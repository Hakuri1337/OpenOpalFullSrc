package wtf.oraculus.client.notification;

import wtf.oraculus.utility.misc.time.Stopwatch;

public final class Notification {

    private final Stopwatch timer = new Stopwatch();
    private final NotificationType type;
    private final String title, description;
    private final int duration;

    Notification(final NotificationType type, final String title, final String description, final int duration) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.duration = duration;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public int getDuration() {
        return duration;
    }

    public boolean hasExpired() {
        return timer.hasTimeElapsed(duration, false);
    }

    public long getTime() {
        return timer.getTime();
    }

}
