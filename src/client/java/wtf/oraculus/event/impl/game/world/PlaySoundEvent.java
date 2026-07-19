package wtf.oraculus.event.impl.game.world;

import net.minecraft.sound.SoundEvent;
import wtf.oraculus.event.EventCancellable;

public final class PlaySoundEvent extends EventCancellable {

    private final SoundEvent soundEvent;
    private final double x, y, z;

    public PlaySoundEvent(final SoundEvent soundEvent, final double x, final double y, final double z) {
        this.soundEvent = soundEvent;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public SoundEvent getSoundEvent() {
        return this.soundEvent;
    }

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }

    public double getZ() {
        return this.z;
    }

}
