package wtf.oraculus.client.auth;

import com.mojang.logging.LogUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import org.slf4j.Logger;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.edition.EditionBuildInfo;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class AuthService implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long HEARTBEAT_INTERVAL_SECONDS = 5 * 60L;
    private static final long NETWORK_GRACE_SECONDS = 10 * 60L;

    private final OraculusClient oraculus;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "Oraculus-Auth");
        thread.setDaemon(true);
        return thread;
    });
    private final AuthApiClient api = new AuthApiClient(executor);
    private final AuthSessionStore store = new AuthSessionStore();
    private final LauncherAccountStore launcherAccountStore = new LauncherAccountStore();
    private final DeviceFingerprintProvider fingerprintProvider = new DeviceFingerprintProvider();
    private final AtomicReference<AuthSnapshot> snapshot = new AtomicReference<>(AuthSnapshot.initial());
    private final AtomicBoolean requestInFlight = new AtomicBoolean();
    private final AtomicBoolean legalityRequestInFlight = new AtomicBoolean();

    private volatile DeviceFingerprintProvider.Fingerprint fingerprint;
    private volatile String accessToken = "";
    private volatile String refreshToken = "";
    private volatile long accessExpiresAt;
    private volatile long refreshExpiresAt;
    private volatile long nextHeartbeatAt;
    private volatile long networkGraceStartedAt;
    private volatile boolean rememberLogin;
    private volatile RuntimePermit runtimePermit;
    private volatile boolean protectedModuleObserved;
    private volatile long nextLegalityProbeAt;
    private volatile boolean launcherAccountMissing = true;
    private Integer fpsBeforeAccountRestriction;

    AuthService(final OraculusClient oraculus) {
        this.oraculus = oraculus;
    }

    public void initialize() {
        DecoyIntegrityGuard.verify();
        publish(AuthState.CHECKING_SAVED_SESSION, "正在检查已保存的登录会话", "", "", "", null, "");
        executor.execute(() -> {
            try {
                fingerprint = fingerprintProvider.collect();
                final LauncherAccountStore.LoadResult launcherAccount = launcherAccountStore.load();
                launcherAccountMissing = launcherAccount.status() == LauncherAccountStore.Status.ABSENT;
                if (launcherAccount.status() == LauncherAccountStore.Status.READY) {
                    loginWithLauncherToken(launcherAccount.account());
                    return;
                }
                if (launcherAccount.status() == LauncherAccountStore.Status.INVALID) {
                    requireLogin("启动器登录信息无效，请使用账号密码登录");
                    return;
                }
                final Optional<AuthSessionStore.SavedSession> saved = store.loadSession();
                if (saved.isPresent()) {
                    rememberLogin = true;
                    refreshToken = saved.get().refreshToken();
                    refreshExpiresAt = saved.get().refreshExpiresAt();
                    refresh(true, saved.get().username());
                } else {
                    requireLogin("请输入 Oraculus 账号密码");
                }
            } catch (Throwable exception) {
                LOGGER.warn("Unable to initialize Oraculus authentication", exception);
                requireLogin("认证组件初始化失败，请重试");
            }
        });
        executor.scheduleWithFixedDelay(this::maintenance, 30, 30, TimeUnit.SECONDS);
    }

    public AuthSnapshot snapshot() {
        return snapshot.get();
    }

    /**
     * Returns the current short-lived bearer token only while the authenticated
     * runtime is allowed to operate. Callers must never persist or log it.
     */
    public String ircAccessToken() {
        return snapshot.get().allowsRuntime() && RuntimeAccessGate.allowsHotPath()
                && accessExpiresAt > now() ? accessToken : "";
    }

    public void onModuleEnabled(final String moduleName) {
        if (!AccountLegalityPolicy.requiresProbe(moduleName)) {
            return;
        }
        protectedModuleObserved = true;
        requestModuleLegality();
    }

    public CompletableFuture<Boolean> mayConnectToRemoteServer() {
        final String token = accessToken;
        if (token.isBlank() || accessExpiresAt <= now()) {
            return CompletableFuture.completedFuture(false);
        }
        return api.legality(token).handleAsync((result, throwable) -> {
            if (throwable != null) {
                return true;
            }
            applyLegalityResult(result);
            return AccountLegalityPolicy.betaConnectionDecision(result) != AccountLegalityPolicy.DENY;
        }, executor);
    }

    public String savedUsername() {
        return store.loadUsername();
    }

    public String fingerprintStatus() {
        final DeviceFingerprintProvider.Fingerprint current = fingerprint;
        return current == null ? "正在采集设备标识" : current.detail();
    }

    public boolean supportsRememberLogin() {
        return store.supportsRememberLogin();
    }

    public void login(final String username, final String password, final boolean remember) {
        if (!beginRequest()) return;
        rememberLogin = remember && store.supportsRememberLogin();
        store.saveUsername(username);
        publish(AuthState.AUTHENTICATING, "正在登录", "", username, "", null,
                fingerprint == null ? "" : fingerprint.quality());
        ensureFingerprint();
        api.login(username, password, fingerprint).whenComplete((result, throwable) ->
                executor.execute(() -> completeCredentialRequest(result, throwable, username)));
    }

    private void loginWithLauncherToken(final LauncherAccountStore.LauncherAccount account) {
        if (!requestInFlight.compareAndSet(false, true)) {
            requireLogin("启动器自动登录失败，请使用账号密码登录");
            return;
        }
        rememberLogin = false;
        store.saveUsername(account.username());
        publish(AuthState.AUTHENTICATING, "正在验证启动器登录", "", account.username(), "", null,
                fingerprint == null ? "" : fingerprint.quality());
        ensureFingerprint();
        api.launcherLogin(account.username(), account.accessToken(), fingerprint)
                .whenComplete((result, throwable) -> executor.execute(() ->
                        completeLauncherRequest(result, throwable, account.username())));
    }

    private void completeLauncherRequest(final AuthApiClient.ApiResult result, final Throwable throwable,
                                         final String username) {
        requestInFlight.set(false);
        if (throwable != null || result == null) {
            requireLogin("启动器自动登录失败，请使用账号密码登录");
            return;
        }
        final AuthDecisionEvaluator.SessionDecision decision =
                AuthDecisionEvaluator.evaluateSession(result, fingerprint, now());
        if (decision != AuthDecisionEvaluator.SessionDecision.ACCEPT) {
            final String rejectedToken = result.accessToken();
            if (rejectedToken != null && !rejectedToken.isBlank()) {
                api.logout(rejectedToken).exceptionally(ignored -> null);
            }
            publish(AuthState.LOGIN_REQUIRED, "启动器自动登录失败，请使用账号密码登录",
                    result.requestId(), username, "", null,
                    fingerprint == null ? "" : fingerprint.quality());
            return;
        }
        acceptSession(result);
    }

    public void register(final String username, final String password, final boolean remember) {
        if (!beginRequest()) return;
        rememberLogin = remember && store.supportsRememberLogin();
        store.saveUsername(username);
        publish(AuthState.AUTHENTICATING, "正在创建 Free 账号", "", username, "", null,
                fingerprint == null ? "" : fingerprint.quality());
        ensureFingerprint();
        api.register(username, password, fingerprint).whenComplete((result, throwable) ->
                executor.execute(() -> completeCredentialRequest(result, throwable, username)));
    }

    public void logout() {
        final String token = accessToken;
        clearCredentials();
        requireLogin("已退出登录");
        stopRuntimeAndShowLogin();
        if (!token.isBlank()) api.logout(token).exceptionally(throwable -> null);
    }

    private void completeCredentialRequest(final AuthApiClient.ApiResult result, final Throwable throwable,
                                           final String username) {
        requestInFlight.set(false);
        if (throwable != null) {
            requireLogin(networkMessage(throwable));
            return;
        }
        if (result == null) {
            requireLogin("认证服务器返回了空响应");
            return;
        }
        final AuthDecisionEvaluator.SessionDecision decision =
                AuthDecisionEvaluator.evaluateSession(result, fingerprint, now());
        switch (decision) {
            case TEMPORARY_FAILURE, SERVER_REJECTED -> {
                publish(AuthState.LOGIN_REQUIRED, result.message(), result.requestId(), username, "", null,
                        fingerprint == null ? "" : fingerprint.quality());
                return;
            }
            case MALFORMED_RESPONSE -> {
                publish(AuthState.LOGIN_REQUIRED, result.message(), result.requestId(), result.username(),
                        result.tier(), result.betaExpiresAt(), result.hwidQuality());
                return;
            }
            case INVALID_ENTITLEMENT, INVALID_SERVER_PROOF -> {
                rejectInvalidEntitlement(result);
                return;
            }
            case ACCEPT -> {
            }
        }
        acceptSession(result);
    }

    private void acceptSession(final AuthApiClient.ApiResult result) {
        if (launcherAccountMissing && !"BETA".equals(result.tier())) {
            rejectNonBetaLoginWithoutLauncherAccount(result);
            return;
        }
        final RuntimePermit acceptedPermit = RuntimePermit.issue(result, fingerprint, now());
        accessToken = result.accessToken();
        refreshToken = result.refreshToken();
        accessExpiresAt = result.accessExpiresAt();
        refreshExpiresAt = result.refreshExpiresAt();
        runtimePermit = acceptedPermit;
        RuntimeAccessGate.install(acceptedPermit, now());
        nextHeartbeatAt = now() + HEARTBEAT_INTERVAL_SECONDS;
        networkGraceStartedAt = 0L;
        restoreFpsLimit();
        store.save(result.username(), refreshToken, refreshExpiresAt, rememberLogin);
        publish(AuthState.AUTHENTICATED, result.message(), result.requestId(), result.username(),
                result.tier(), result.betaExpiresAt(), result.hwidQuality());
        MinecraftClient.getInstance().execute(() -> {
            publish(AuthState.RUNTIME_STARTING, "正在启动 Oraculus 运行时", result.requestId(),
                    result.username(), result.tier(), result.betaExpiresAt(), result.hwidQuality());
            try {
                oraculus.runAuthenticatedInitializations(acceptedPermit);
                publish(AuthState.READY, "登录成功", result.requestId(), result.username(),
                        result.tier(), result.betaExpiresAt(), result.hwidQuality());
                if (MinecraftClient.getInstance().currentScreen instanceof OraculusLoginScreen screen)
                    MinecraftClient.getInstance().setScreen(screen.parentScreen());
            } catch (Throwable exception) {
                LOGGER.error("Unable to start the authenticated Oraculus runtime", exception);
                clearCredentials();
                publish(AuthState.LOGIN_REQUIRED, "客户端运行时启动失败", result.requestId(),
                        result.username(), result.tier(), result.betaExpiresAt(), result.hwidQuality());
            }
        });
    }

    private void rejectNonBetaLoginWithoutLauncherAccount(final AuthApiClient.ApiResult result) {
        final String token = result.accessToken();
        clearCredentials();
        publish(AuthState.LOGIN_REQUIRED,
                "未检测到启动器登录信息，非 Beta 账号不能登录",
                result.requestId(), result.username(), result.tier(), result.betaExpiresAt(), result.hwidQuality());
        stopRuntimeAndShowLogin();
        if (token != null && !token.isBlank()) {
            api.logout(token).exceptionally(ignored -> null);
        }
    }

    private void refresh(final boolean initial, final String username) {
        if (!requestInFlight.compareAndSet(false, true)) return;
        if (initial)
            publish(AuthState.CHECKING_SAVED_SESSION, "正在恢复登录会话", "", username, "", null,
                    fingerprint == null ? "" : fingerprint.quality());
        api.refresh(refreshToken, fingerprint).whenComplete((result, throwable) -> executor.execute(() -> {
            requestInFlight.set(false);
            if (throwable != null) {
                if (initial) {
                    requireLogin(networkMessage(throwable));
                } else {
                    enterNetworkGrace(networkMessage(throwable));
                }
                return;
            }
            if (result == null) {
                if (initial) requireLogin("认证服务器返回了空响应");
                else enterNetworkGrace("认证服务器返回了空响应");
                return;
            }
            final AuthDecisionEvaluator.SessionDecision decision =
                    AuthDecisionEvaluator.evaluateSession(result, fingerprint, now());
            if (decision != AuthDecisionEvaluator.SessionDecision.ACCEPT) {
                if (decision == AuthDecisionEvaluator.SessionDecision.TEMPORARY_FAILURE) {
                    if (initial) requireLogin(result.message());
                    else enterNetworkGrace(result.message());
                } else if (decision == AuthDecisionEvaluator.SessionDecision.SERVER_REJECTED) {
                    revoke(result.message(), result.requestId());
                } else {
                    rejectInvalidEntitlement(result);
                }
                return;
            }
            acceptSession(result);
        }));
    }

    private void heartbeat() {
        if (!requestInFlight.compareAndSet(false, true)) return;
        api.heartbeat(accessToken).whenComplete((result, throwable) -> executor.execute(() -> {
            requestInFlight.set(false);
            if (throwable != null) {
                enterNetworkGrace(networkMessage(throwable));
                return;
            }
            if (!result.ok()) {
                if (AuthDecisionEvaluator.isTemporaryFailure(result)) enterNetworkGrace(result.message());
                else revoke(result.message(), result.requestId());
                return;
            }
            nextHeartbeatAt = now() + HEARTBEAT_INTERVAL_SECONDS;
            networkGraceStartedAt = 0L;
            final AuthSnapshot current = snapshot.get();
            if (current.state() == AuthState.NETWORK_GRACE)
                publish(AuthState.READY, "认证连接已恢复", result.requestId(), result.username(),
                        result.tier(), result.betaExpiresAt(), result.hwidQuality());
        }));
    }

    private void maintenance() {
        try {
            final AuthState state = snapshot.get().state();
            if (state != AuthState.READY && state != AuthState.NETWORK_GRACE) return;
            final long now = now();
            if (protectedModuleObserved && now >= nextLegalityProbeAt) {
                requestModuleLegality();
            }
            if (state == AuthState.NETWORK_GRACE && networkGraceStartedAt > 0
                    && now - networkGraceStartedAt >= NETWORK_GRACE_SECONDS) {
                revoke("认证服务器连接超时，请重新登录", "");
                return;
            }
            if (refreshToken.isBlank() || refreshExpiresAt <= now) {
                revoke("登录会话已过期", "");
            } else if (accessExpiresAt - now <= 60L) {
                refresh(false, snapshot.get().username());
            } else if (now >= nextHeartbeatAt) {
                heartbeat();
            }
        } catch (Throwable exception) {
            LOGGER.warn("Oraculus authentication maintenance failed", exception);
        }
    }

    private void requestModuleLegality() {
        final String token = accessToken;
        if (token.isBlank() || accessExpiresAt <= now()
                || !legalityRequestInFlight.compareAndSet(false, true)) {
            return;
        }
        nextLegalityProbeAt = now() + 60L;
        api.legality(token).whenComplete((result, throwable) -> executor.execute(() -> {
            legalityRequestInFlight.set(false);
            if (throwable == null) {
                applyLegalityResult(result);
            }
        }));
    }

    private void applyLegalityResult(final AuthApiClient.LegalityResult result) {
        if (AccountLegalityPolicy.shouldLimitFps(result)) {
            MinecraftClient.getInstance().execute(this::applyFpsLimit);
        } else if (result != null && result.ok() && result.exists()) {
            restoreFpsLimit();
        }
    }

    private void applyFpsLimit() {
        final var option = MinecraftClient.getInstance().options.getMaxFps();
        synchronized (this) {
            if (fpsBeforeAccountRestriction == null) {
                fpsBeforeAccountRestriction = option.getValue();
            }
        }
        if (option.getValue() >= 30) {
            option.setValue(29);
        }
    }

    private void restoreFpsLimit() {
        final Integer previous;
        synchronized (this) {
            previous = fpsBeforeAccountRestriction;
            fpsBeforeAccountRestriction = null;
        }
        if (previous != null) {
            MinecraftClient.getInstance().execute(() ->
                    MinecraftClient.getInstance().options.getMaxFps().setValue(previous));
        }
    }

    private synchronized void enterNetworkGrace(final String message) {
        final long currentTime = now();
        if (networkGraceStartedAt == 0L) {
            networkGraceStartedAt = currentTime;
            try {
                RuntimeAccessGate.enterNetworkGrace(runtimePermit,
                        networkGraceStartedAt + NETWORK_GRACE_SECONDS, currentTime);
            } catch (SecurityException invalidPermit) {
                revoke("认证运行时凭据已失效", "");
                return;
            }
        }
        final AuthSnapshot current = snapshot.get();
        publish(AuthState.NETWORK_GRACE, message + "；已进入 10 分钟临时宽限", current.requestId(),
                current.username(), current.tier(), current.betaExpiresAt(), current.hwidQuality());
    }

    private void revoke(final String message, final String requestId) {
        final AuthSnapshot current = snapshot.get();
        clearCredentials();
        publish(AuthState.ACCESS_REVOKED, message == null || message.isBlank() ? "授权已失效" : message,
                requestId, current.username(), current.tier(), current.betaExpiresAt(), current.hwidQuality());
        stopRuntimeAndShowLogin();
    }

    private void stopRuntimeAndShowLogin() {
        MinecraftClient.getInstance().execute(() -> {
            final MinecraftClient client = MinecraftClient.getInstance();
            oraculus.stopAuthenticatedRuntime();
            final OraculusLoginScreen login = new OraculusLoginScreen(new TitleScreen());
            if (client.world != null) {
                client.disconnect(login, false);
            } else {
                client.setScreen(login);
            }
        });
    }

    private void requireLogin(final String message) {
        final AuthSnapshot current = snapshot.get();
        publish(AuthState.LOGIN_REQUIRED, message, "", store.loadUsername(), "", null,
                fingerprint == null ? current.hwidQuality() : fingerprint.quality());
    }

    private void rejectInvalidEntitlement(final AuthApiClient.ApiResult result) {
        final String token = result.accessToken();
        clearCredentials();
        publish(AuthState.ACCESS_REVOKED,
                EditionBuildInfo.isFree() ? "服务器返回的会话无效" : "当前账号没有有效的 Beta 授权",
                result.requestId(), result.username(), result.tier(), result.betaExpiresAt(), result.hwidQuality());
        stopRuntimeAndShowLogin();
        if (token != null && !token.isBlank()) api.logout(token).exceptionally(throwable -> null);
    }

    private boolean beginRequest() {
        if (fingerprint == null) {
            requireLogin("设备标识仍在初始化，请稍候");
            return false;
        }
        return requestInFlight.compareAndSet(false, true);
    }

    private void ensureFingerprint() {
        if (fingerprint == null) throw new IllegalStateException("Device fingerprint is unavailable");
    }

    private void clearCredentials() {
        RuntimeAccessGate.revoke();
        accessToken = "";
        refreshToken = "";
        accessExpiresAt = 0L;
        refreshExpiresAt = 0L;
        networkGraceStartedAt = 0L;
        runtimePermit = null;
        store.clearSession();
    }

    public void resumeAuthenticatedRuntime() {
        final RuntimePermit current = runtimePermit;
        if (current == null || !snapshot.get().allowsRuntime()) {
            throw new SecurityException("Authenticated runtime is unavailable");
        }
        oraculus.runAuthenticatedInitializations(current);
    }

    private void publish(final AuthState state, final String message, final String requestId,
                         final String username, final String tier, final Long betaExpiresAt,
                         final String hwidQuality) {
        snapshot.set(new AuthSnapshot(state, safe(message), safe(requestId), safe(username),
                safe(tier), betaExpiresAt, safe(hwidQuality)));
    }

    private static String networkMessage(final Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) cause = cause.getCause();
        if (cause instanceof HttpTimeoutException) return "连接认证服务器超时";
        if (cause instanceof ConnectException) return "无法连接认证服务器";
        return "认证网络请求失败";
    }

    private static String safe(final String value) {
        return value == null ? "" : value;
    }

    private static long now() {
        return System.currentTimeMillis() / 1000L;
    }

    @Override
    public void close() {
        restoreFpsLimit();
        executor.shutdownNow();
    }
}
