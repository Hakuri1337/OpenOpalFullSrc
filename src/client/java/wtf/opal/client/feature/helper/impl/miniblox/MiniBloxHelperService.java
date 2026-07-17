package wtf.opal.client.feature.helper.impl.miniblox;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.CookieStorage;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import org.slf4j.Logger;
import wtf.opal.client.feature.helper.IHelper;
import wtf.opal.event.EventDispatcher;
import wtf.opal.event.impl.game.server.ServerDisconnectEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.data.SaveUtility;
import wtf.opal.utility.player.protocol.ViaFabricPlusSupport;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static wtf.opal.client.Constants.DIRECTORY;

public final class MiniBloxHelperService implements IHelper, AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String REPOSITORY_URL = "https://github.com/7GrandDadPGN/MinibloxTranslationLayer.git";
    private static final URI PRESET_URI = URI.create(
            "https://raw.githubusercontent.com/Hakuri1337/OpenOpal/main/miniblox.json");
    private static final String LOCAL_ADDRESS = "127.0.0.1:25565";
    private static final int MAX_PRESET_BYTES = 8 * 1024 * 1024;

    private static final Path ROOT = DIRECTORY.toPath().resolve("miniblox");
    private static final Path REPOSITORY = ROOT.resolve("translation-layer");
    private static final Path PRESET_CACHE = ROOT.resolve("official-preset.json");
    private static final Path SESSION_BACKUP = ROOT.resolve("session-backup.json");
    private static final Path SETTINGS = ROOT.resolve("helper-settings.json");
    private static final Path LOG_DIRECTORY = ROOT.resolve("logs");

    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("OpenOpal MiniBlox Helper").factory());
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final AtomicBoolean busy = new AtomicBoolean();
    private final AtomicBoolean stopRequested = new AtomicBoolean();
    private final Object sessionLock = new Object();

    private volatile State state = State.IDLE;
    private volatile String detail = "Ready";
    private volatile String lastLog = "";
    private volatile boolean useOfficialParameters;
    private volatile Process managedProcess;
    private volatile boolean closed;

    private String sessionConfig;
    private Object sessionViaTarget;
    private SaveUtility.AutoSaveScope sessionAutoSaveScope;
    private boolean sessionArmed;

    private MiniBloxHelperService() {
        this.useOfficialParameters = loadUseOfficialParameters();
    }

    public void recoverInterruptedSession() {
        synchronized (sessionLock) {
            if (sessionArmed || !Files.isRegularFile(SESSION_BACKUP)) {
                return;
            }
            try {
                final String backup = Files.readString(SESSION_BACKUP, StandardCharsets.UTF_8);
                validatePreset(backup);
                if (!SaveUtility.applyConfigJson(backup)) {
                    throw new HelperException("The interrupted MiniBlox config backup could not be applied.");
                }
                Files.deleteIfExists(SESSION_BACKUP);
                setState(State.IDLE, "Recovered config from an interrupted MiniBlox session");
            } catch (Exception exception) {
                LOGGER.error("Unable to recover interrupted MiniBloxHelper session", exception);
                setState(State.ERROR, "Interrupted-session recovery failed: " + exception.getMessage());
            }
        }
    }

    public void installOrUpdate() {
        if (!beginOperation(State.CHECKING, "Checking Git, Node.js and npm")) {
            return;
        }

        executor.execute(() -> {
            try {
                prepareRuntime(true);
                setState(State.READY, "Translation layer is installed and up to date");
            } catch (Exception exception) {
                fail(exception);
            } finally {
                busy.set(false);
            }
        });
    }

    public void startAndJoin(final Screen parent) {
        if (!beginOperation(State.CHECKING, "Preparing MiniBlox session")) {
            return;
        }

        executor.execute(() -> {
            try {
                if (!ViaFabricPlusSupport.isLoaded()) {
                    throw new HelperException("ViaFabricPlus is required. Install it before joining MiniBlox.");
                }
                prepareRuntime(false);
                if (isPortOpen()) {
                    throw new HelperException("Port 25565 is already in use. Stop the existing local server first.");
                }

                final String preset = useOfficialParameters ? downloadOfficialPreset() : null;
                startTranslationLayer();
                waitForPort();

                MinecraftClient.getInstance().execute(() -> armSessionAndConnect(parent, preset));
            } catch (Exception exception) {
                stopManagedProcess();
                MinecraftClient.getInstance().execute(this::restoreSession);
                if (stopRequested.get()) {
                    setState(State.IDLE, "Stopped; previous config and Via target restored");
                } else {
                    fail(exception);
                }
                busy.set(false);
            }
        });
    }

    public void stop() {
        if (closed) {
            return;
        }
        stopRequested.set(true);
        setState(State.STOPPING, "Stopping translation layer and restoring session");
        Thread.ofPlatform().daemon().name("OpenOpal MiniBlox Stop").start(() -> {
            stopManagedProcess();
            MinecraftClient.getInstance().execute(() -> {
                restoreSession();
                busy.set(false);
                setState(State.IDLE, "Stopped; previous config and Via target restored");
            });
        });
    }

    public Snapshot getSnapshot() {
        final Process process = this.managedProcess;
        return new Snapshot(
                this.state,
                this.detail,
                this.lastLog,
                this.busy.get(),
                Files.isDirectory(REPOSITORY.resolve(".git")),
                process != null && process.isAlive(),
                ViaFabricPlusSupport.isLoaded(),
                ViaFabricPlusSupport.getTargetVersionName(),
                this.useOfficialParameters
        );
    }

    public boolean isUseOfficialParameters() {
        return useOfficialParameters;
    }

    public void setUseOfficialParameters(final boolean useOfficialParameters) {
        this.useOfficialParameters = useOfficialParameters;
        try {
            final JsonObject settings = new JsonObject();
            settings.addProperty("useOfficialParameters", useOfficialParameters);
            writeAtomically(SETTINGS, settings.toString());
        } catch (IOException exception) {
            setState(State.ERROR, "Could not save MiniBloxHelper settings: " + exception.getMessage());
        }
    }

    @Subscribe
    public void onServerDisconnect(final ServerDisconnectEvent event) {
        if (this.sessionArmed) {
            stopRequested.set(true);
            setState(State.IDLE, "Disconnected; previous config and Via target restored");
            Thread.ofPlatform().daemon().name("OpenOpal MiniBlox Disconnect Stop").start(this::stopManagedProcess);
        }
        restoreSession();
    }

    private boolean beginOperation(final State newState, final String newDetail) {
        if (closed || !busy.compareAndSet(false, true)) {
            return false;
        }
        stopRequested.set(false);
        setState(newState, newDetail);
        return true;
    }

    private void prepareRuntime(final boolean updateExisting) throws Exception {
        Files.createDirectories(ROOT);
        Files.createDirectories(LOG_DIRECTORY);
        checkCommand("git", "--version");
        checkCommand("node", "--version");
        checkCommand(npmCommand(), "--version");

        if (!Files.exists(REPOSITORY)) {
            setState(State.INSTALLING, "Cloning MiniBlox Translation Layer");
            runCommand(ROOT, Duration.ofMinutes(5), "git", "clone", REPOSITORY_URL,
                    REPOSITORY.getFileName().toString());
        } else {
            if (!Files.isDirectory(REPOSITORY.resolve(".git"))) {
                throw new HelperException("The translation-layer directory exists but is not a Git repository.");
            }
            final String origin = runCommand(REPOSITORY, Duration.ofSeconds(20),
                    "git", "remote", "get-url", "origin").trim();
            if (!normalizeRepositoryUrl(origin).equals(normalizeRepositoryUrl(REPOSITORY_URL))) {
                throw new HelperException("The translation-layer directory points to an unexpected Git origin.");
            }
            // npm creates package-lock.json for this repository although the upstream project does not track it.
            // Only tracked/staged edits can be user source changes that an update must protect.
            final String dirty = runCommand(REPOSITORY, Duration.ofSeconds(20),
                    "git", "status", "--porcelain", "--untracked-files=no").trim();
            if (!dirty.isEmpty()) {
                throw new HelperException("The translation-layer checkout has local changes; update was stopped.");
            }
            if (updateExisting) {
                setState(State.UPDATING, "Updating MiniBlox Translation Layer");
                runCommand(REPOSITORY, Duration.ofMinutes(3), "git", "pull", "--ff-only");
            }
        }

        if (updateExisting || !Files.isDirectory(REPOSITORY.resolve("node_modules"))) {
            setState(State.INSTALLING, "Installing translation-layer npm dependencies");
            runCommand(REPOSITORY, Duration.ofMinutes(10), npmCommand(), "install", "--package-lock=false");
        }
    }

    private void checkCommand(final String... command) throws Exception {
        try {
            runCommand(ROOT, Duration.ofSeconds(15), command);
        } catch (IOException exception) {
            throw new HelperException(command[0] + " is not installed or is not available in PATH.", exception);
        }
    }

    private String downloadOfficialPreset() throws Exception {
        setState(State.DOWNLOADING, "Downloading official OpenOpal MiniBlox parameters");
        final HttpRequest request = HttpRequest.newBuilder(PRESET_URI)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "OpenOpal-MiniBloxHelper")
                .GET()
                .build();
        final HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new HelperException("Official parameter download failed with HTTP " + response.statusCode() + '.');
        }
        if (response.body().length == 0 || response.body().length > MAX_PRESET_BYTES) {
            throw new HelperException("Official parameter file is empty or exceeds the 8 MiB limit.");
        }

        final String json = new String(response.body(), StandardCharsets.UTF_8);
        validatePreset(json);
        writeAtomically(PRESET_CACHE, json);
        return json;
    }

    private void validatePreset(final String json) throws HelperException {
        try {
            final JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonArray() || root.getAsJsonArray().isEmpty()) {
                throw new HelperException("Official parameter file is not a non-empty config array.");
            }
            final boolean hasModule = root.getAsJsonArray().asList().stream()
                    .anyMatch(element -> element.isJsonObject() && element.getAsJsonObject().has("name"));
            if (!hasModule) {
                throw new HelperException("Official parameter file contains no recognizable modules.");
            }
        } catch (HelperException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new HelperException("Official parameter file is invalid JSON.", exception);
        }
    }

    private void startTranslationLayer() throws IOException {
        setState(State.STARTING, "Starting local translation layer on 127.0.0.1:25565");
        final Path logPath = LOG_DIRECTORY.resolve("translation-layer-"
                + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now()) + ".log");
        final ProcessBuilder builder = new ProcessBuilder("node", "index.js")
                .directory(REPOSITORY.toFile())
                .redirectErrorStream(true);
        final Process process = builder.start();
        this.managedProcess = process;

        Thread.ofPlatform().daemon().name("OpenOpal MiniBlox Output").start(() -> streamProcessOutput(process, logPath));
        Thread.ofPlatform().daemon().name("OpenOpal MiniBlox Watcher").start(() -> watchManagedProcess(process));
    }

    private void streamProcessOutput(final Process process, final Path logPath) {
        try (BufferedReader reader = process.inputReader(StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(logPath, StandardCharsets.UTF_8,
                     StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            String line;
            while ((line = reader.readLine()) != null) {
                this.lastLog = line.length() > 220 ? line.substring(0, 220) : line;
                writer.write(line);
                writer.newLine();
                writer.flush();
            }
        } catch (IOException exception) {
            LOGGER.warn("Unable to capture MiniBlox translation-layer output", exception);
        }
    }

    private void watchManagedProcess(final Process process) {
        try {
            final int exitCode = process.waitFor();
            if (this.managedProcess != process) {
                return;
            }
            this.managedProcess = null;
            MinecraftClient.getInstance().execute(() -> {
                restoreSession();
                busy.set(false);
                if (!closed && state != State.STOPPING && state != State.IDLE) {
                    setState(State.ERROR, "Translation layer exited unexpectedly with code " + exitCode + '.');
                }
            });
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void waitForPort() throws Exception {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        while (System.nanoTime() < deadline) {
            final Process process = this.managedProcess;
            if (process == null || !process.isAlive()) {
                throw new HelperException("Translation layer exited before opening port 25565. Check its log.");
            }
            if (isPortOpen()) {
                return;
            }
            Thread.sleep(250L);
        }
        throw new HelperException("Translation layer did not open port 25565 within 45 seconds.");
    }

    private boolean isPortOpen() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 25565), 250);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private void armSessionAndConnect(final Screen parent, final String preset) {
        try {
            synchronized (sessionLock) {
                if (sessionArmed) {
                    throw new HelperException("A MiniBloxHelper session is already active.");
                }
                this.sessionConfig = SaveUtility.captureConfigJson();
                writeAtomically(SESSION_BACKUP, this.sessionConfig);
                this.sessionViaTarget = ViaFabricPlusSupport.getTargetVersion();
                this.sessionAutoSaveScope = SaveUtility.suppressAutoSave();
                this.sessionArmed = true;

                if (!ViaFabricPlusSupport.setTargetVersion1_8() || !ViaFabricPlusSupport.isTargeting1_8()) {
                    throw new HelperException("ViaFabricPlus could not switch its target protocol to 1.8.x.");
                }
                if (preset != null && !SaveUtility.applyConfigJson(preset)) {
                    throw new HelperException("Official MiniBlox parameters could not be applied.");
                }
            }

            final MinecraftClient client = MinecraftClient.getInstance();
            final ServerInfo info = new ServerInfo("MiniBlox (OpenOpal Helper)", LOCAL_ADDRESS,
                    ServerInfo.ServerType.OTHER);
            setState(State.RUNNING, "Translation layer is running; connecting with Via 1.8.x");
            busy.set(false);
            ConnectScreen.connect(parent, client, ServerAddress.parse(LOCAL_ADDRESS), info, false,
                    new CookieStorage(Map.of(), Map.of(), false));
        } catch (Exception exception) {
            restoreSession();
            stopManagedProcess();
            fail(exception);
            busy.set(false);
        }
    }

    private void restoreSession() {
        synchronized (sessionLock) {
            if (!sessionArmed) {
                return;
            }

            try {
                if (sessionConfig != null && !SaveUtility.applyConfigJson(sessionConfig)) {
                    LOGGER.error("Unable to restore the OpenOpal config after MiniBloxHelper session");
                }
                if (sessionViaTarget != null && !ViaFabricPlusSupport.restoreTargetVersion(sessionViaTarget)) {
                    LOGGER.error("Unable to restore the ViaFabricPlus target after MiniBloxHelper session");
                }
            } finally {
                if (sessionAutoSaveScope != null) {
                    sessionAutoSaveScope.close();
                }
                sessionAutoSaveScope = null;
                sessionConfig = null;
                sessionViaTarget = null;
                sessionArmed = false;
                try {
                    Files.deleteIfExists(SESSION_BACKUP);
                } catch (IOException exception) {
                    LOGGER.warn("Unable to remove MiniBlox session backup", exception);
                }
            }
        }
    }

    private String runCommand(final Path directory, final Duration timeout, final String... command) throws Exception {
        Files.createDirectories(directory);
        final Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .start();
        final StringBuilder output = new StringBuilder();
        final Thread readerThread = Thread.ofPlatform().daemon().name("OpenOpal MiniBlox Command Output").start(() -> {
            try (BufferedReader reader = process.inputReader(StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (output) {
                        if (output.length() < 64 * 1024) {
                            output.append(line).append('\n');
                        }
                    }
                    lastLog = line.length() > 220 ? line.substring(0, 220) : line;
                }
            } catch (IOException ignored) {
            }
        });

        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroy();
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
            throw new HelperException(command[0] + " timed out after " + timeout.toSeconds() + " seconds.");
        }
        readerThread.join(2_000L);
        final String result;
        synchronized (output) {
            result = output.toString();
        }
        if (process.exitValue() != 0) {
            final String tail = result.length() > 500 ? result.substring(result.length() - 500) : result;
            throw new HelperException(command[0] + " exited with code " + process.exitValue() + ": " + tail.trim());
        }
        return result;
    }

    private void stopManagedProcess() {
        final Process process = this.managedProcess;
        this.managedProcess = null;
        if (process == null || !process.isAlive()) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private boolean loadUseOfficialParameters() {
        try {
            if (!Files.isRegularFile(SETTINGS)) {
                return false;
            }
            final JsonObject object = JsonParser.parseString(Files.readString(SETTINGS)).getAsJsonObject();
            return object.has("useOfficialParameters") && object.get("useOfficialParameters").getAsBoolean();
        } catch (Exception exception) {
            LOGGER.warn("Unable to load MiniBloxHelper settings", exception);
            return false;
        }
    }

    private static void writeAtomically(final Path target, final String content) throws IOException {
        Files.createDirectories(target.getParent());
        final Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String normalizeRepositoryUrl(final String url) {
        String normalized = url.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".git")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        return normalized;
    }

    private static String npmCommand() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "npm.cmd" : "npm";
    }

    private void setState(final State state, final String detail) {
        this.state = state;
        this.detail = detail == null ? "" : detail;
    }

    private void fail(final Exception exception) {
        final String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        LOGGER.error("MiniBloxHelper operation failed", exception);
        setState(State.ERROR, message);
    }

    @Override
    public void close() {
        this.closed = true;
        this.stopRequested.set(true);
        stopManagedProcess();
        restoreSession();
        executor.shutdownNow();
    }

    public enum State {
        IDLE,
        CHECKING,
        INSTALLING,
        UPDATING,
        DOWNLOADING,
        READY,
        STARTING,
        RUNNING,
        STOPPING,
        ERROR
    }

    public record Snapshot(State state, String detail, String lastLog, boolean busy, boolean repositoryInstalled,
                           boolean processRunning, boolean viaInstalled, String viaTarget,
                           boolean useOfficialParameters) {
    }

    private static final class HelperException extends Exception {
        private HelperException(final String message) {
            super(message);
        }

        private HelperException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }

    private static MiniBloxHelperService instance;

    public static MiniBloxHelperService getInstance() {
        if (instance == null) {
            instance = new MiniBloxHelperService();
            EventDispatcher.subscribe(instance);
        }
        return instance;
    }

    public static void setInstance() {
        getInstance();
    }
}
