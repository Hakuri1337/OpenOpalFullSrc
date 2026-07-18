package wtf.opal.client.screen.serverpack;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.ClientConnection;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import wtf.opal.client.feature.module.impl.utility.ServerPackSpoofModule;
import wtf.opal.client.renderer.liquidglass.reglass.ReGlassApi;
import wtf.opal.client.renderer.liquidglass.reglass.WidgetStyle;

import java.util.List;

public final class ServerPackSpoofScreen extends Screen {
    private static final int PANEL_WIDTH = 440;
    private static final int PANEL_HEIGHT = 240;

    private final ServerPackSpoofModule module;
    private final ClientConnection connection;
    private final Screen parent;
    private boolean resolved;

    public ServerPackSpoofScreen(final ServerPackSpoofModule module, final ClientConnection connection,
                                 final Screen parent) {
        super(Text.literal("Server Resource Pack"));
        this.module = module;
        this.connection = connection;
        this.parent = parent;
    }

    @Override
    protected void init() {
        final int panelX = (this.width - PANEL_WIDTH) / 2;
        final int panelY = Math.max(24, (this.height - PANEL_HEIGHT) / 2);
        final int buttonWidth = 120;
        final int gap = 10;
        final int buttonsWidth = buttonWidth * 3 + gap * 2;
        final int buttonX = panelX + (PANEL_WIDTH - buttonsWidth) / 2;
        final int buttonY = panelY + PANEL_HEIGHT - 48;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Load"), button -> this.module.loadPending(this))
                .dimensions(buttonX, buttonY, buttonWidth, 20)
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Spoof"), button -> this.module.spoofPending(this))
                .dimensions(buttonX + buttonWidth + gap, buttonY, buttonWidth, 20)
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Decline"), button -> this.module.declinePending(this))
                .dimensions(buttonX + (buttonWidth + gap) * 2, buttonY, buttonWidth, 20)
                .build());
    }

    @Override
    public void close() {
        if (!this.resolved) {
            this.module.declinePending(this);
        }
    }

    @Override
    public void removed() {
        if (!this.resolved) {
            this.module.declinePending(this);
        }
    }

    public boolean isFor(final ServerPackSpoofModule module, final ClientConnection connection) {
        return this.module == module && this.connection == connection;
    }

    public void markResolved() {
        this.resolved = true;
    }

    @Override
    public void render(final DrawContext context, final int mouseX, final int mouseY, final float delta) {
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

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, panelY + 18, 0xFFFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Required server resource pack"),
                panelX + 24, panelY + 48, 0xFFF3F4F6);
        context.drawTextWithShadow(this.textRenderer,
                Text.literal(this.module.getSourceHost(this.connection)),
                panelX + 24, panelY + 66, 0xFFB7BBC3);
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("Requests: " + this.module.getPendingCount(this.connection)),
                panelX + 24, panelY + 82, 0xFF8F949D);

        final List<OrderedText> promptLines = this.textRenderer.wrapLines(
                this.module.getPrompt(this.connection), PANEL_WIDTH - 48
        );
        int promptY = panelY + 108;
        for (int index = 0; index < Math.min(promptLines.size(), 4); index++) {
            context.drawTextWithShadow(this.textRenderer, promptLines.get(index), panelX + 24, promptY, 0xFFE3E5E8);
            promptY += this.textRenderer.fontHeight + 2;
        }

        super.render(context, mouseX, mouseY, delta);
    }
}
