package wtf.oraculus.client.screen.settings;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import wtf.oraculus.client.feature.helper.impl.miniblox.MiniBloxHelperService;
import wtf.oraculus.client.renderer.liquidglass.reglass.ReGlassApi;
import wtf.oraculus.client.renderer.liquidglass.reglass.WidgetStyle;

public final class OraculusSettingsScreen extends Screen {
    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 250;

    private final Screen parent;
    private final MiniBloxHelperService helper = MiniBloxHelperService.getInstance();

    private ButtonWidget installButton;
    private ButtonWidget startButton;
    private ButtonWidget stopButton;

    public OraculusSettingsScreen(final Screen parent) {
        super(Text.literal("Oraculus Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        final int panelX = (this.width - PANEL_WIDTH) / 2;
        final int panelY = Math.max(24, (this.height - PANEL_HEIGHT) / 2);
        final int contentX = panelX + 24;
        final int controlsY = panelY + 112;
        final int buttonWidth = 116;

        this.addDrawableChild(CheckboxWidget.builder(
                        Text.literal("Use official MiniBlox parameters"), this.textRenderer)
                .pos(contentX, panelY + 77)
                .checked(this.helper.isUseOfficialParameters())
                .callback((checkbox, checked) -> this.helper.setUseOfficialParameters(checked))
                .maxWidth(PANEL_WIDTH - 48)
                .build());

        this.installButton = this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("Install / Update"), button -> this.helper.installOrUpdate())
                .dimensions(contentX, controlsY, buttonWidth, 20)
                .build());
        this.startButton = this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("Start & Join"), button -> this.helper.startAndJoin(this))
                .dimensions(contentX + buttonWidth + 12, controlsY, buttonWidth, 20)
                .build());
        this.stopButton = this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("Stop"), button -> this.helper.stop())
                .dimensions(contentX + (buttonWidth + 12) * 2, controlsY, buttonWidth, 20)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> this.close())
                .dimensions(panelX + PANEL_WIDTH - 84, panelY + PANEL_HEIGHT - 32, 60, 20)
                .build());
        updateButtonStates();
    }

    @Override
    public void tick() {
        updateButtonStates();
    }

    private void updateButtonStates() {
        if (this.installButton == null) {
            return;
        }
        final MiniBloxHelperService.Snapshot snapshot = this.helper.getSnapshot();
        this.installButton.active = !snapshot.busy() && !snapshot.processRunning();
        this.startButton.active = !snapshot.busy() && !snapshot.processRunning() && snapshot.viaInstalled();
        this.stopButton.active = snapshot.processRunning();
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public void render(final DrawContext context, final int mouseX, final int mouseY, final float delta) {
        // Screen.renderBackground() already consumes Minecraft's once-per-frame blur pass.
        // ReGlass owns that pass for this screen's panel instead.
        context.fill(0, 0, this.width, this.height, 0xB6101216);

        final int panelX = (this.width - PANEL_WIDTH) / 2;
        final int panelY = Math.max(24, (this.height - PANEL_HEIGHT) / 2);
        ReGlassApi.create(context)
                .dimensions(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT)
                .cornerRadius(8.0F)
                .style(WidgetStyle.create()
                        .tint(0x101216, 0.72F)
                        .blurRadius(12)
                        .shadow(5.0F, 0.55F, 0.0F, 2.0F)
                        .shadowColor(0x000000, 0.55F))
                .render();

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, panelY + 17, 0xFFFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("MiniBloxHelper").formatted(Formatting.BOLD),
                panelX + 24, panelY + 43, 0xFFF3F4F6);
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("Managed translation layer, Via 1.8.x and temporary Oraculus preset"),
                panelX + 24, panelY + 58, 0xFFB7BBC3);

        final MiniBloxHelperService.Snapshot snapshot = this.helper.getSnapshot();
        final int statusY = panelY + 148;
        context.drawTextWithShadow(this.textRenderer,
                this.textRenderer.trimToWidth("State: " + snapshot.state() + " - " + snapshot.detail(), PANEL_WIDTH - 48),
                panelX + 24, statusY, snapshot.state() == MiniBloxHelperService.State.ERROR ? 0xFFFF7777 : 0xFFE3E5E8);
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("Repository: " + present(snapshot.repositoryInstalled())
                        + "    Process: " + (snapshot.processRunning() ? "running" : "stopped")),
                panelX + 24, statusY + 16, 0xFFB7BBC3);
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("ViaFabricPlus: " + (snapshot.viaInstalled() ? snapshot.viaTarget() : "missing (join blocked)")),
                panelX + 24, statusY + 32, snapshot.viaInstalled() ? 0xFFB7BBC3 : 0xFFFFA1A1);
        if (!snapshot.lastLog().isBlank()) {
            context.drawTextWithShadow(this.textRenderer,
                    this.textRenderer.trimToWidth("Last log: " + snapshot.lastLog(), PANEL_WIDTH - 48),
                    panelX + 24, statusY + 48, 0xFF8F949D);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private static String present(final boolean value) {
        return value ? "installed" : "not installed";
    }
}
