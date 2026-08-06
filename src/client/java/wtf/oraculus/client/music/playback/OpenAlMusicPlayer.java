package wtf.oraculus.client.music.playback;

import com.mojang.logging.LogUtils;
import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundEngine;
import org.slf4j.Logger;
import mixin.ChannelAccessor;
import mixin.SoundManagerAccessor;
import mixin.SoundSystemAccessor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static wtf.oraculus.client.Constants.mc;

public final class OpenAlMusicPlayer implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final AtomicLong generation = new AtomicLong();
    private final AtomicReference<ActiveSource> activeSource = new AtomicReference<>();

    private volatile long basePositionMillis;
    private volatile long startedAtNanos;
    private volatile float volume = 0.7F;
    private volatile boolean playing;
    private volatile boolean paused;

    public CompletableFuture<Void> play(final Path path, final long seekMillis, final float requestedVolume) {
        final long currentGeneration = generation.incrementAndGet();
        return releaseCurrentSource()
                .thenCompose(ignored -> openStream(path, seekMillis))
                .thenCompose(stream -> startStream(stream, seekMillis, requestedVolume, currentGeneration));
    }

    public void pause() {
        final ActiveSource active = activeSource.get();
        if (active == null || !playing || paused) return;
        basePositionMillis = getPositionMillis();
        paused = true;
        active.manager().run(source -> source.pause());
    }

    public void resume() {
        final ActiveSource active = activeSource.get();
        if (active == null || !playing || !paused) return;
        startedAtNanos = System.nanoTime();
        paused = false;
        active.manager().run(source -> source.resume());
    }

    public void setVolume(final float value) {
        volume = Math.clamp(value, 0.0F, 1.0F);
        final ActiveSource active = activeSource.get();
        if (active != null) active.manager().run(source -> source.setVolume(volume));
    }

    public long getPositionMillis() {
        if (!playing || paused) return basePositionMillis;
        return basePositionMillis + Math.max(0, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    public boolean hasEnded() {
        final ActiveSource active = activeSource.get();
        return playing && active != null && active.manager().isStopped();
    }

    public boolean isPlaying() {
        return playing;
    }

    public void stop() {
        generation.incrementAndGet();
        releaseCurrentSource().exceptionally(throwable -> {
            LOGGER.warn("Failed to release the Oraculus music source", throwable);
            return null;
        });
        basePositionMillis = 0;
    }

    private CompletableFuture<JLayerMp3AudioStream> openStream(final Path path, final long seekMillis) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return new JLayerMp3AudioStream(path, seekMillis);
            } catch (final IOException exception) {
                throw new CompletionException(exception);
            }
        });
    }

    private CompletableFuture<Void> startStream(final JLayerMp3AudioStream stream, final long seekMillis,
                                                final float requestedVolume, final long currentGeneration) {
        if (generation.get() != currentGeneration) {
            closeQuietly(stream);
            return CompletableFuture.completedFuture(null);
        }

        final Channel channel;
        try {
            channel = ((SoundSystemAccessor) ((SoundManagerAccessor) mc.getSoundManager())
                    .oraculus$getSoundSystem()).oraculus$getChannel();
        } catch (final RuntimeException exception) {
            closeQuietly(stream);
            return CompletableFuture.failedFuture(exception);
        }

        final CompletableFuture<Void> started = new CompletableFuture<>();
        try {
            channel.createSource(SoundEngine.RunMode.STREAMING).whenComplete((manager, throwable) -> {
                if (throwable != null) {
                    closeQuietly(stream);
                    started.completeExceptionally(throwable);
                    return;
                }
                if (manager == null) {
                    closeQuietly(stream);
                    started.completeExceptionally(new IllegalStateException("No OpenAL streaming source is available"));
                    return;
                }

                final ActiveSource active = new ActiveSource(channel, manager, started);
                if (generation.get() != currentGeneration) {
                    closeQuietly(stream);
                    releaseSource(active);
                    started.complete(null);
                    return;
                }

                activeSource.set(active);
                volume = Math.clamp(requestedVolume, 0.0F, 1.0F);
                basePositionMillis = Math.max(0, seekMillis);
                try {
                    manager.run(source -> configureAndPlay(source, stream, active, currentGeneration, started));
                } catch (final RuntimeException exception) {
                    activeSource.compareAndSet(active, null);
                    closeQuietly(stream);
                    releaseSource(active);
                    started.completeExceptionally(exception);
                }
            });
        } catch (final RuntimeException exception) {
            closeQuietly(stream);
            started.completeExceptionally(exception);
        }
        return started;
    }

    private void configureAndPlay(final net.minecraft.client.sound.Source source,
                                  final JLayerMp3AudioStream stream, final ActiveSource active,
                                  final long currentGeneration, final CompletableFuture<Void> started) {
        if (generation.get() != currentGeneration || activeSource.get() != active) {
            closeQuietly(stream);
            releaseSource(active);
            started.complete(null);
            return;
        }

        boolean attached = false;
        try {
            source.setRelative(true);
            source.disableAttenuation();
            source.setLooping(false);
            source.setPitch(1.0F);
            source.setVolume(volume);
            source.setStream(stream);
            attached = true;
            source.play();
            startedAtNanos = System.nanoTime();
            playing = true;
            paused = false;
            started.complete(null);
        } catch (final RuntimeException exception) {
            activeSource.compareAndSet(active, null);
            playing = false;
            paused = false;
            if (!attached) closeQuietly(stream);
            releaseSource(active);
            started.completeExceptionally(exception);
        }
    }

    private CompletableFuture<Void> releaseCurrentSource() {
        final ActiveSource active = activeSource.getAndSet(null);
        playing = false;
        paused = false;
        if (active == null) return CompletableFuture.completedFuture(null);
        active.startup().complete(null);
        return releaseSource(active);
    }

    private CompletableFuture<Void> releaseSource(final ActiveSource active) {
        final CompletableFuture<Void> released = new CompletableFuture<>();
        try {
            final ChannelAccessor channel = (ChannelAccessor) active.channel();
            channel.oraculus$getExecutor().execute(() -> {
                try {
                    if (!active.manager().isStopped()) active.manager().close();
                    channel.oraculus$getSources().remove(active.manager());
                    released.complete(null);
                } catch (final RuntimeException exception) {
                    channel.oraculus$getSources().remove(active.manager());
                    released.completeExceptionally(exception);
                }
            });
        } catch (final RuntimeException exception) {
            released.completeExceptionally(exception);
        }
        return released;
    }

    @Override
    public void close() {
        stop();
    }

    private static void closeQuietly(final JLayerMp3AudioStream stream) {
        try {
            stream.close();
        } catch (final IOException ignored) {
        }
    }

    private record ActiveSource(Channel channel, Channel.SourceManager manager,
                                CompletableFuture<Void> startup) {
    }
}
