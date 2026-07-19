package wtf.oraculus.client.feature.module.impl.visual.overlay.impl.notifications;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.Window;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.module.impl.visual.overlay.IOverlayElement;
import wtf.oraculus.client.feature.module.impl.visual.overlay.OverlayModule;
import wtf.oraculus.client.notification.Notification;
import wtf.oraculus.client.renderer.NVGRenderer;
import wtf.oraculus.client.renderer.repository.FontRepository;
import wtf.oraculus.client.renderer.text.NVGTextRenderer;
import wtf.oraculus.utility.render.ColorUtility;
import wtf.oraculus.utility.render.animation.Animation;
import wtf.oraculus.utility.render.animation.Easing;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.nanovg.NanoVG.nvgShapeAntiAlias;
import static wtf.oraculus.client.Constants.VG;
import static wtf.oraculus.client.Constants.mc;

public final class NotificationsElement implements IOverlayElement {

    private static final NVGTextRenderer ICON_FONT = FontRepository.getFont("materialicons-regular");
    private static final NVGTextRenderer TITLE_FONT = FontRepository.getFont("productsans-bold");
    private static final NVGTextRenderer DESCRIPTION_FONT = FontRepository.getFont("productsans-medium");

    private final Map<Notification, Animation> animations = new HashMap<>();
    private final NotificationSettings settings;

    public NotificationsElement(final OverlayModule module) {
        this.settings = new NotificationSettings(module);
    }

    public NotificationSettings getSettings() {
        return this.settings;
    }

    @Override
    public void render(final DrawContext context, final float delta, boolean isBloom) {
        final List<Notification> notifications = OraculusClient.getInstance().getNotificationManager().getNotifications();

        final float padding = 3;
        final float height = 21;
        final float iconSize = 14;
        final float iconOffset = iconSize + padding;

        final Window window = mc.getWindow();
        final float scaledWidth = window.getScaledWidth();
        final float scaledHeight = window.getScaledHeight();

        for (int i = 0; i < notifications.size(); i++) {
            final Notification notification = notifications.get(i);
            final Animation animation = animations.computeIfAbsent(notification, n -> new Animation(Easing.EASE_OUT_EXPO, 400));

            final float width = Math.max(
                    100,
                    iconOffset + Math.max(
                            TITLE_FONT.getStringWidth(notification.getTitle(), 7) + (padding * 4),
                            DESCRIPTION_FONT.getStringWidth(notification.getDescription(), 7.5F)
                    )
            );

            final float endX = scaledWidth - width - padding;

            if (!notification.hasExpired()) {
                animation.setStartValue(scaledWidth);
            }
            animation.run(notification.hasExpired() ? scaledWidth : endX);

            final float x = animation.getValue();
            final float y = scaledHeight - (padding * 2) - ((i + 1) * (height + padding));

            final float progress = (float) notification.getTime() / notification.getDuration();
            final int iconColor = notification.getType().getIconColor();

            NVGRenderer.roundedRect(x, y, width, height, 4, NVGRenderer.BLUR_PAINT);
            NVGRenderer.roundedRect(x, y, width, height, 4, 0x80090909);

            nvgShapeAntiAlias(VG, false);
            NVGRenderer.roundedRectVaryingGradient(x + 0.5F, y + height - 4, (width - 0.5F) * progress, 4, 0, 0, progress > 0.95F ? 4 : 0, 4, Color.BITMASK, ColorUtility.applyOpacity(iconColor, 0.25F), 90);
            nvgShapeAntiAlias(VG, true);

            NVGRenderer.roundedRect(x + padding - 0.5F, y + padding / 2 + 0.5F, iconOffset, iconOffset, 2.75F, ColorUtility.applyOpacity(ColorUtility.darker(iconColor, 0.6F), 0.5F));
            ICON_FONT.drawString(notification.getType().getIcon(), x + padding + 1.25F, y + (padding * 3) + iconOffset / 2, iconSize, iconColor);

            TITLE_FONT.drawString(notification.getTitle(), x + (padding * 2) + iconOffset, y + (padding * 3), 7, -1);
            DESCRIPTION_FONT.drawString(notification.getDescription(), x + (padding * 2) + iconOffset, y + (padding * 3) + 7.5F, 6.5F, 0xFFAAAAAA);

            if (notification.hasExpired() && animation.getValue() == scaledWidth) {
                notifications.remove(notification);
                animations.remove(notification);
            }
        }
    }

    @Override
    public boolean isActive() {
        return !mc.getDebugHud().shouldShowDebugHud() && this.settings.isEnabled();
    }

    @Override
    public boolean isBloom() {
        return true;
    }
}
