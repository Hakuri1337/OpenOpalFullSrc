package wtf.oraculus.client.edition;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerAddress;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.auth.AuthBootstrap;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.impl.movement.TargetStrafeModule;
import wtf.oraculus.client.feature.module.impl.utility.AutoRodModule;
import wtf.oraculus.client.feature.module.impl.combat.FakeLagModule;
import wtf.oraculus.client.feature.module.repository.ModuleRepository;
import wtf.oraculus.utility.render.ClientTheme;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class EditionHooks {
    private static final String ALWAYS_ALLOWED_SERVER = "mc.loyisa.cn";
    private static final AtomicReference<String> APPROVED_CONNECTION = new AtomicReference<>();
    private static final Set<String> CONNECTION_CHECKS = ConcurrentHashMap.newKeySet();

    private EditionHooks() {
    }

    public static boolean shouldCancelMouseButton(final int button) {
        final var repository = OraculusClient.getInstance().getModuleRepository();
        if (repository == null) {
            return false;
        }

        final AutoRodModule autoRod = repository.getModule(AutoRodModule.class);
        return autoRod != null && autoRod.isEnabled() && autoRod.shouldInterceptButton(button);
    }

    public static boolean isTargetStrafing() {
        final var repository = OraculusClient.getInstance().getModuleRepository();
        if (repository == null) {
            return false;
        }

        final TargetStrafeModule targetStrafe = repository.getModule(TargetStrafeModule.class);
        return targetStrafe != null && targetStrafe.isActive();
    }

    public static boolean shouldDeferServerConnection(final ServerAddress address, final Runnable resume) {
        final String host = normalizeHost(address.getAddress());
        if (ALWAYS_ALLOWED_SERVER.equals(host)) {
            return false;
        }
        final String key = host + ':' + address.getPort();
        if (APPROVED_CONNECTION.compareAndSet(key, null)) {
            return false;
        }
        final var authService = AuthBootstrap.getService();
        if (authService == null || !CONNECTION_CHECKS.add(key)) {
            return true;
        }
        authService.mayConnectToRemoteServer().whenComplete((allowed, throwable) -> {
            CONNECTION_CHECKS.remove(key);
            if (throwable == null && Boolean.TRUE.equals(allowed)) {
                APPROVED_CONNECTION.set(key);
                MinecraftClient.getInstance().execute(resume);
            }
        });
        return true;
    }

    private static String normalizeHost(final String value) {
        String host = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        return host;
    }

    public static void disableEditionPacketBuffers(final ModuleRepository repository, final List<String> disabledModules) {
        final Module fakeLag = repository.getModule(FakeLagModule.class);
        if (fakeLag != null && fakeLag.isEnabled()) {
            fakeLag.setEnabled(false);
            disabledModules.add("FakeLag");
        }
    }

    public static void enforceEditionDefaults(final ModuleRepository repository) {
        // Beta keeps Streamer Mode fully user-configurable.
    }

    public static ClientTheme[] getClientThemes() {
        return ClientTheme.values();
    }
}
