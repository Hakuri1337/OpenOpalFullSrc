package wtf.oraculus.client.feature.module.impl.visual.overlay;

import net.minecraft.util.Colors;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.edition.EditionHooks;
import wtf.oraculus.client.feature.helper.impl.render.ScaleProperty;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.ModuleCategory;
import wtf.oraculus.client.feature.module.impl.visual.ClickGUIModule;
import wtf.oraculus.client.feature.module.impl.visual.overlay.impl.client.ClientElements;
import wtf.oraculus.client.feature.module.impl.visual.overlay.impl.balancedtimer.BalancedTimerElement;
import wtf.oraculus.client.feature.module.impl.visual.overlay.impl.dynamicisland.DynamicIslandElement;
import wtf.oraculus.client.feature.module.impl.visual.overlay.impl.lyrics.LyricsElement;
import wtf.oraculus.client.feature.module.impl.visual.overlay.impl.modulelist.ToggledModulesElement;
import wtf.oraculus.client.feature.module.impl.visual.overlay.impl.notifications.NotificationsElement;
import wtf.oraculus.client.feature.module.impl.visual.overlay.impl.targetinfo.TargetInfoElement;
import wtf.oraculus.client.feature.module.impl.visual.PotionModule;
import wtf.oraculus.client.feature.module.impl.visual.TabGUIModule;
import wtf.oraculus.client.feature.module.property.impl.ColorProperty;
import wtf.oraculus.client.feature.module.property.impl.GroupProperty;
import wtf.oraculus.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.oraculus.client.feature.module.property.impl.mode.ModeProperty;
import wtf.oraculus.client.feature.module.property.impl.number.NumberProperty;
import wtf.oraculus.event.impl.client.PostClientInitializationEvent;
import wtf.oraculus.event.impl.client.PropertyUpdateEvent;
import wtf.oraculus.event.impl.game.PostGameTickEvent;
import wtf.oraculus.event.impl.game.packet.ReceivePacketEvent;
import wtf.oraculus.event.impl.render.RenderBloomEvent;
import wtf.oraculus.event.impl.render.RenderScreenEvent;
import wtf.oraculus.event.impl.render.ResolutionChangeEvent;
import wtf.oraculus.event.subscriber.Subscribe;
import wtf.oraculus.utility.render.ClientTheme;

import java.util.ArrayList;
import java.util.List;

public final class
OverlayModule extends Module {

    // Theme
    private final ModeProperty<ClientTheme> themeMode = new ModeProperty<>(
            "Theme", ClientTheme.ORACULUS, EditionHooks.getClientThemes(), true)
            .alias("Opal", ClientTheme.ORACULUS);
    public static final ColorProperty primaryColorProperty = new ColorProperty("Primary color", Colors.BLACK);
    public static final ColorProperty secondaryColorProperty = new ColorProperty("Secondary color", Colors.BLACK);

    // Minecraft elements
    private final BooleanProperty statusEffectOverlayEnabled = new BooleanProperty("Enabled", false);
    private final BooleanProperty scoreboardEnabled = new BooleanProperty("Enabled", true);
    private final BooleanProperty scoreboardTextShadow = new BooleanProperty("Text shadow", true).hideIf(() -> !scoreboardEnabled.getValue());
    private final NumberProperty scoreboardCornerRadius = new NumberProperty("Corner Radius", 1.5, 0, 8, 0.25)
            .hideIf(() -> !scoreboardEnabled.getValue()).id("scoreboard-corner-radius");
    private final LiquidGlassV2Settings scoreboardLiquidGlassV2 = new LiquidGlassV2Settings(
            "scoreboard", "scoreboard-liquid-glass-v2", scoreboardEnabled::getValue
    );
    private final ScaleProperty scoreboardScale = ScaleProperty.newMinecraftElement();
    private final BooleanProperty bossbarEnabled = new BooleanProperty("Enabled", false);

    private final BooleanProperty dynamicIslandLeftAligned = new BooleanProperty("Left-aligned", false);
    private final LiquidGlassV2Settings dynamicIslandLiquidGlassV2 = new LiquidGlassV2Settings(
            "", "liquid-glass-v2", () -> true
    );

    private final List<IOverlayElement> elements = new ArrayList<>();

    private final TargetInfoElement targetInfo;
    private final ToggledModulesElement toggledModules;
    private final NotificationsElement notifications;
    private final LyricsElement lyrics;

    public OverlayModule() {
        super("Overlay", "Renders the clients display.", ModuleCategory.VISUAL);

        primaryColorProperty.hideIf(() -> !themeMode.is(ClientTheme.CUSTOM));
        secondaryColorProperty.hideIf(() -> !themeMode.is(ClientTheme.CUSTOM));

        this.setEnabled(true);
        this.addProperties(
                themeMode, primaryColorProperty, secondaryColorProperty,
                new GroupProperty("Minecraft elements",
                        new GroupProperty(
                                "Status effect overlay",
                                statusEffectOverlayEnabled
                        ),
                        new GroupProperty("Scoreboard", scoreboardLiquidGlassV2.after(
                                scoreboardScale.get(), scoreboardEnabled,
                                scoreboardTextShadow, scoreboardCornerRadius
                        )),
                        new GroupProperty(
                                "Bossbar",
                                bossbarEnabled
                        )
                )
        );

        this.targetInfo = this.register(new TargetInfoElement(this));
        this.toggledModules = this.register(new ToggledModulesElement(this));
        this.register(new ClientElements(this));
        this.register(new BalancedTimerElement());
        this.lyrics = this.register(new LyricsElement(this));
        this.addProperties(new GroupProperty(
                "Dynamic island", dynamicIslandLiquidGlassV2.after(dynamicIslandLeftAligned)
        ));
        this.notifications = this.register(new NotificationsElement(this));

        this.register(new DynamicIslandElement(this));
    }

    private <T extends IOverlayElement> T register(T element) {
        this.elements.add(element);
        return element;
    }

    @Override
    protected void onDisable() {
        this.elements.forEach(IOverlayElement::onDisable);
    }

    @Override
    protected void onEnable() {
        if (OraculusClient.getInstance().isPostInitialization()) {
            this.toggledModules.initialize();
            this.targetInfo.initialize();
        }
    }

    @Subscribe
    public void onPostClientInitialization(PostClientInitializationEvent event) {
        this.toggledModules.initialize();
        this.targetInfo.initialize();
    }

    @Subscribe
    public void onPropertyUpdate(PropertyUpdateEvent event) {
        if (this.toggledModules != null) {
            this.toggledModules.markSortingDirty();
        }

        if (this.targetInfo != null) {
            this.targetInfo.refreshIslandTrigger();
        }
    }

    @Subscribe(priority = -20)
    public void onRenderScreen(RenderScreenEvent event) {
        this.targetInfo.applyScoreboardHealth();

        for (IOverlayElement element : this.elements) {
            if (element.isActive()) {
                element.render(event.drawContext(), event.tickDelta(), false);
            }
        }
    }

    @Subscribe
    public void onReceivePacket(final ReceivePacketEvent event) {
        this.targetInfo.onReceivePacket(event);
    }

    @Subscribe(priority = -20)
    public void onBloomRender(RenderBloomEvent event) {
        for (IOverlayElement element : this.elements) {
            if (element.isActive() && element.isBloom()) {
                element.render(event.drawContext(), event.tickDelta(), true);
            }
        }
    }

    @Subscribe
    public void onResize(ResolutionChangeEvent event) {
        this.elements.forEach(IOverlayElement::onResize);
    }

    @Subscribe
    public void onPostTick(PostGameTickEvent event) {
        for (IOverlayElement element : this.elements) {
            if (element.isActive()) {
                element.tick();
            }
        }
    }

    public ModeProperty<ClientTheme> getThemeMode() {
        return themeMode;
    }

    public ToggledModulesElement getToggledModules() {
        return toggledModules;
    }

    public NotificationsElement getNotifications() {
        return notifications;
    }

    public LyricsElement getLyrics() {
        return this.lyrics;
    }

    public boolean isDynamicIslandLeftAligned() {
        return dynamicIslandLeftAligned.getValue();
    }

    public boolean isDynamicIslandLiquidGlassV2() {
        return this.dynamicIslandLiquidGlassV2.isEnabled();
    }

    public LiquidGlassV2Settings getDynamicIslandLiquidGlassV2Settings() {
        return this.dynamicIslandLiquidGlassV2;
    }

    public boolean isAnyLiquidGlassV2() {
        final PotionModule potion = OraculusClient.getInstance().getModuleRepository().getModule(PotionModule.class);
        final ClickGUIModule clickGUI = OraculusClient.getInstance().getModuleRepository().getModule(ClickGUIModule.class);
        final TabGUIModule tabGUI = OraculusClient.getInstance().getModuleRepository().getModule(TabGUIModule.class);
        return this.isDynamicIslandLiquidGlassV2()
                || this.isScoreboardLiquidGlassV2()
                || (this.notifications != null && this.notifications.isLiquidGlassV2())
                || (this.toggledModules != null && this.toggledModules.getSettings().isLiquidGlassV2())
                || (this.targetInfo != null && this.targetInfo.getSettings().isLiquidGlassV2())
                || (this.lyrics != null && this.lyrics.getSettings().isLiquidGlassV2())
                || (clickGUI != null && clickGUI.isLiquidGlassV2())
                || (tabGUI != null && tabGUI.isLiquidGlassV2())
                || (potion != null && potion.isLiquidGlassV2());
    }

    public boolean isScoreboardTextShadow() {
        return scoreboardEnabled.getValue() && scoreboardTextShadow.getValue();
    }

    public boolean isScoreboardLiquidGlassV2() {
        return this.scoreboardLiquidGlassV2.isEnabled();
    }

    public LiquidGlassV2Settings getScoreboardLiquidGlassV2Settings() {
        return this.scoreboardLiquidGlassV2;
    }

    public float getScoreboardCornerRadius() {
        return this.scoreboardCornerRadius.getValue().floatValue();
    }

    public float getScoreboardScale() {
        return scoreboardEnabled.getValue() ? scoreboardScale.getScale() : 1;
    }

    public boolean isBossbarEnabled() {
        return bossbarEnabled.getValue();
    }

    public boolean isStatusEffectOverlayEnabled() {
        return statusEffectOverlayEnabled.getValue();
    }

}
