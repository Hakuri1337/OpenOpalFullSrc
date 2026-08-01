package wtf.oraculus.client.feature.module.impl.visual.overlay.impl.notifications;

import com.ibm.icu.impl.Pair;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.Window;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.module.impl.visual.overlay.IOverlayElement;
import wtf.oraculus.client.feature.module.impl.visual.overlay.OverlayModule;
import wtf.oraculus.client.feature.module.impl.visual.overlay.impl.dynamicisland.DynamicIslandElement;
import wtf.oraculus.client.feature.module.impl.visual.overlay.impl.dynamicisland.IslandTrigger;
import wtf.oraculus.client.notification.Notification;
import wtf.oraculus.client.renderer.NVGRenderer;
import wtf.oraculus.client.renderer.repository.FontRepository;
import wtf.oraculus.client.renderer.text.NVGTextRenderer;
import wtf.oraculus.utility.render.ColorUtility;
import wtf.oraculus.utility.render.animation.Animation;
import wtf.oraculus.utility.render.animation.Easing;

import java.awt.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.lwjgl.nanovg.NanoVG.nvgShapeAntiAlias;
import static wtf.oraculus.client.Constants.VG;
import static wtf.oraculus.client.Constants.mc;

public final class NotificationsElement implements IOverlayElement, IslandTrigger {

    private static final NVGTextRenderer ICON_FONT = FontRepository.getFont("materialicons-regular");
    private static final NVGTextRenderer TITLE_FONT = FontRepository.getFont("productsans-bold");
    private static final NVGTextRenderer DESCRIPTION_FONT = FontRepository.getFont("productsans-medium");

    private static final float ISLAND_HORIZONTAL_PADDING = 7;
    private static final float ISLAND_VERTICAL_PADDING = 4;
    private static final float ISLAND_ITEM_HEIGHT = 28;
    private static final float ISLAND_ITEM_GAP = 3;
    private static final float ISLAND_ICON_SIZE = 22;
    private static final float ISLAND_ICON_RADIUS = 7;
    private static final float ISLAND_ICON_GAP = 5;
    private static final float ISLAND_PLAIN_SWITCH_WIDTH = 25.5F;
    private static final float ISLAND_PLAIN_SWITCH_HEIGHT = 19;
    private static final float ISLAND_TITLE_SIZE = 7.5F;
    private static final float ISLAND_DESCRIPTION_SIZE = 6.5F;
    private static final float ISLAND_PROGRESS_HEIGHT = 5;

    private final Map<Notification, Animation> animations = new HashMap<>();
    private final Map<Notification, Animation> toggleAnimations = new HashMap<>();
    private final NotificationSettings settings;
    private boolean islandRegistered;

    public NotificationsElement(final OverlayModule module) {
        this.settings = new NotificationSettings(module);
    }

    public NotificationSettings getSettings() {
        return this.settings;
    }

    @Override
    public void render(final DrawContext context, final float delta, boolean isBloom) {
        final List<Notification> notifications = OraculusClient.getInstance().getNotificationManager().getNotifications();
        this.toggleAnimations.keySet().removeIf(notification -> !notifications.contains(notification) || notification.hasExpired());

        if (!this.settings.isEnabled()) {
            this.setIslandRegistered(false);
            return;
        }

        if (this.settings.isIsland()) {
            notifications.removeIf(Notification::hasExpired);
            this.animations.keySet().removeIf(notification -> !notifications.contains(notification));
            this.setIslandRegistered(!mc.getDebugHud().shouldShowDebugHud() && !notifications.isEmpty());
            return;
        }

        this.setIslandRegistered(false);
        if (mc.getDebugHud().shouldShowDebugHud()) {
            return;
        }

        this.renderLegacy(notifications);
    }

    private void renderLegacy(final List<Notification> notifications) {

        final float padding = 3;
        final float height = 21;
        final float iconSize = 14;
        final float iconOffset = iconSize + padding;

        final Window window = mc.getWindow();
        final float scaledWidth = window.getScaledWidth();
        final float scaledHeight = window.getScaledHeight();

        int index = 0;
        final Iterator<Notification> iterator = notifications.iterator();
        while (iterator.hasNext()) {
            final Notification notification = iterator.next();
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
            final float y = scaledHeight - (padding * 2) - ((index + 1) * (height + padding));

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
                iterator.remove();
                animations.remove(notification);
                continue;
            }

            index++;
        }
    }

    @Override
    public void renderIsland(
            final DrawContext context,
            final float posX,
            final float posY,
            final float width,
            final float height,
            final float progress
    ) {
        final List<Notification> notifications = this.getIslandNotifications();
        final Pair<Integer, Integer> theme = ColorUtility.getClientTheme();
        final int iconColor = ColorUtility.brighter(theme.first, 0.2F);
        final boolean showIconBackground = this.settings.showIslandIconBackground();
        final float iconSlotWidth = this.getIslandIconSlotWidth();

        for (int index = 0; index < notifications.size(); index++) {
            final Notification notification = notifications.get(index);
            final float itemY = posY + ISLAND_VERTICAL_PADDING + index * (ISLAND_ITEM_HEIGHT + ISLAND_ITEM_GAP);
            final float iconX = posX + ISLAND_HORIZONTAL_PADDING;
            final float iconY = itemY + (ISLAND_ITEM_HEIGHT - ISLAND_ICON_SIZE) / 2;
            final float contentX = iconX + iconSlotWidth + ISLAND_ICON_GAP;
            final float contentWidth = Math.max(1, width - (contentX - posX) - ISLAND_HORIZONTAL_PADDING);
            final String title = this.getIslandTitle(notification);
            final String description = notification.getDescription() == null ? "" : notification.getDescription();
            final boolean moduleToggle = this.isModuleToggle(notification);

            if (showIconBackground) {
                NVGRenderer.roundedRectGradient(
                        iconX,
                        iconY,
                        ISLAND_ICON_SIZE,
                        ISLAND_ICON_SIZE,
                        ISLAND_ICON_RADIUS,
                        theme.first,
                        theme.second,
                        45
                );
            }

            if (moduleToggle) {
                final boolean enabled = this.isModuleEnabled(notification);
                final Animation toggleAnimation = this.toggleAnimations.computeIfAbsent(notification, ignored -> {
                    final Animation animation = new Animation(Easing.DECELERATE, 220);
                    animation.setStartValue(enabled ? 0 : 1);
                    return animation;
                });
                toggleAnimation.run(enabled ? 1 : 0);
                if (showIconBackground) {
                    NotificationToggleSvgIcon.INSTANCE.render(
                            iconX + 2.5F,
                            iconY + 2.5F,
                            ISLAND_ICON_SIZE - 5,
                            ISLAND_ICON_SIZE - 5,
                            toggleAnimation.getValue(),
                            theme.first
                    );
                } else {
                    NotificationToggleSvgIcon.INSTANCE.render(
                            iconX,
                            itemY + (ISLAND_ITEM_HEIGHT - ISLAND_PLAIN_SWITCH_HEIGHT) / 2,
                            ISLAND_PLAIN_SWITCH_WIDTH,
                            ISLAND_PLAIN_SWITCH_HEIGHT,
                            toggleAnimation.getValue(),
                            theme.first
                    );
                }
            } else {
                final float iconOffset = showIconBackground ? 0 : (iconSlotWidth - ISLAND_ICON_SIZE) / 2;
                ICON_FONT.drawString(notification.getType().getIcon(), iconX + iconOffset + 4, iconY + 15, 14, iconColor);
            }

            TITLE_FONT.drawString(title, contentX, itemY + 9, ISLAND_TITLE_SIZE, -1);
            DESCRIPTION_FONT.drawString(description, contentX, itemY + 16.5F, ISLAND_DESCRIPTION_SIZE, 0xFFAAAAAA);

            final float barY = itemY + 25 - ISLAND_PROGRESS_HEIGHT;
            final float barWidth = Math.min(
                    contentWidth,
                    TITLE_FONT.getStringWidth(title, ISLAND_TITLE_SIZE)
            );
            final float remaining = Math.max(0, Math.min(1,
                    1F - (float) notification.getTime() / Math.max(1, notification.getDuration())));
            NVGRenderer.roundedRect(contentX, barY, barWidth, ISLAND_PROGRESS_HEIGHT, ISLAND_PROGRESS_HEIGHT / 2, 0xD0101114);
            if (remaining > 0) {
                NVGRenderer.roundedRectGradient(
                        contentX,
                        barY,
                        barWidth * remaining,
                        ISLAND_PROGRESS_HEIGHT,
                        ISLAND_PROGRESS_HEIGHT / 2,
                        theme.first,
                        theme.second,
                        0
                );
            }
        }
    }

    @Override
    public float getIslandWidth() {
        float contentWidth = 1;
        for (final Notification notification : this.getIslandNotifications()) {
            final String description = notification.getDescription() == null ? "" : notification.getDescription();
            contentWidth = Math.max(contentWidth, Math.max(
                    TITLE_FONT.getStringWidth(this.getIslandTitle(notification), ISLAND_TITLE_SIZE),
                    DESCRIPTION_FONT.getStringWidth(description, ISLAND_DESCRIPTION_SIZE)
            ));
        }

        return ISLAND_HORIZONTAL_PADDING * 2 + this.getIslandIconSlotWidth() + ISLAND_ICON_GAP + contentWidth;
    }

    @Override
    public float getIslandHeight() {
        final int count = this.getIslandNotifications().size();
        return ISLAND_VERTICAL_PADDING * 2
                + count * ISLAND_ITEM_HEIGHT
                + Math.max(0, count - 1) * ISLAND_ITEM_GAP;
    }

    @Override
    public int getIslandPriority() {
        return 20;
    }

    @Override
    public void onDisable() {
        this.setIslandRegistered(false);
    }

    private List<Notification> getIslandNotifications() {
        return OraculusClient.getInstance().getNotificationManager().getNotifications().stream()
                .filter(notification -> !notification.hasExpired())
                .toList();
    }

    private String getIslandTitle(final Notification notification) {
        final String title = notification.getTitle() == null ? "Notification" : notification.getTitle();
        return this.isModuleToggle(notification) && !title.startsWith("Module Toggle: ") ? "Module Toggle: " + title : title;
    }

    private boolean isModuleToggle(final Notification notification) {
        final String description = notification.getDescription() == null ? "" : notification.getDescription();
        return description.equalsIgnoreCase("Module enabled.") || description.equalsIgnoreCase("Module disabled.");
    }

    private boolean isModuleEnabled(final Notification notification) {
        return "Module enabled.".equalsIgnoreCase(notification.getDescription());
    }

    private float getIslandIconSlotWidth() {
        return this.settings.showIslandIconBackground() ? ISLAND_ICON_SIZE : ISLAND_PLAIN_SWITCH_WIDTH;
    }

    private void setIslandRegistered(final boolean registered) {
        if (registered == this.islandRegistered) {
            return;
        }

        if (registered) {
            DynamicIslandElement.addTrigger(this);
        } else {
            DynamicIslandElement.removeTrigger(this);
        }
        this.islandRegistered = registered;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public boolean isBloom() {
        return true;
    }
}
