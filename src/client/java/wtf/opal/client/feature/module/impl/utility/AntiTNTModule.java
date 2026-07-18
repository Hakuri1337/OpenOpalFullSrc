package wtf.opal.client.feature.module.impl.utility;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.TntEntity;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.impl.visual.overlay.OverlayModule;
import wtf.opal.client.feature.module.impl.visual.overlay.impl.dynamicisland.DynamicIslandElement;
import wtf.opal.client.feature.module.impl.visual.overlay.impl.dynamicisland.IslandTrigger;
import wtf.opal.client.renderer.NVGRenderer;
import wtf.opal.client.renderer.repository.FontRepository;
import wtf.opal.client.renderer.text.NVGTextRenderer;
import wtf.opal.event.impl.game.PreGameTickEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.misc.chat.ChatUtility;
import wtf.opal.utility.render.ColorUtility;

import java.util.Comparator;
import java.util.Locale;

import static wtf.opal.client.Constants.mc;

/**
 * Detection-only TNT warning. It never rotates, switches items, or places
 * blocks; the module is intentionally limited to local awareness.
 */
public final class AntiTNTModule extends Module {

    private static final double WARNING_RADIUS = 6.0D;
    private static final double WARNING_RADIUS_SQUARED = WARNING_RADIUS * WARNING_RADIUS;

    private final TntWarningIsland island = new TntWarningIsland();
    private TntEntity nearestTnt;
    private int warnedTntId = Integer.MIN_VALUE;

    public AntiTNTModule() {
        super("AntiTNT", "Warns when ignited TNT is within six blocks.", ModuleCategory.UTILITY);
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null || mc.world == null) {
            this.clearWarning();
            return;
        }

        this.nearestTnt = mc.world.getEntitiesByClass(
                        TntEntity.class,
                        mc.player.getBoundingBox().expand(WARNING_RADIUS),
                        tnt -> tnt.isAlive()
                                && tnt.getFuse() > 0
                                && mc.player.squaredDistanceTo(tnt) <= WARNING_RADIUS_SQUARED
                ).stream()
                .min(Comparator.comparingDouble(mc.player::squaredDistanceTo))
                .orElse(null);

        if (this.nearestTnt == null) {
            this.clearWarning();
            return;
        }

        this.island.setTarget(this.nearestTnt);
        if (this.warnedTntId != this.nearestTnt.getId()) {
            this.warnedTntId = this.nearestTnt.getId();
            final double distance = Math.sqrt(mc.player.squaredDistanceTo(this.nearestTnt));
            ChatUtility.error(String.format(Locale.ROOT, "TNT nearby: %.1fm", distance));
        }

        if (this.isDynamicIslandEnabled()) {
            DynamicIslandElement.addTrigger(this.island);
        } else {
            DynamicIslandElement.removeTrigger(this.island);
        }
    }

    @Override
    protected void onDisable() {
        this.clearWarning();
        super.onDisable();
    }

    private boolean isDynamicIslandEnabled() {
        final OverlayModule overlay = OpalClient.getInstance().getModuleRepository().getModule(OverlayModule.class);
        return overlay != null && overlay.isEnabled();
    }

    private void clearWarning() {
        this.nearestTnt = null;
        this.warnedTntId = Integer.MIN_VALUE;
        this.island.clear();
        DynamicIslandElement.removeTrigger(this.island);
    }

    private static final class TntWarningIsland implements IslandTrigger {
        private static final int WARNING_COLOR = 0xFFE65A5A;

        private TntEntity target;

        private void setTarget(final TntEntity target) {
            this.target = target;
        }

        private void clear() {
            this.target = null;
        }

        @Override
        public void renderIsland(final DrawContext context, final float posX, final float posY,
                                 final float width, final float height, final float progress) {
            if (this.target == null || !this.target.isAlive() || mc.player == null) {
                DynamicIslandElement.removeTrigger(this);
                return;
            }

            final NVGTextRenderer titleFont = FontRepository.getFont("productsans-bold");
            final NVGTextRenderer footerFont = FontRepository.getFont("productsans-medium");
            final float distance = (float) Math.sqrt(mc.player.squaredDistanceTo(this.target));
            final float fuseSeconds = this.target.getFuse() / 20.0F;

            NVGRenderer.roundedRect(posX + 6.0F, posY + 4.0F, 17.0F, 17.0F, 8.5F,
                    ColorUtility.applyOpacity(WARNING_COLOR, 150));
            titleFont.drawString("!", posX + 12.0F, posY + 17.0F, 11.0F, -1);
            titleFont.drawString("TNT ALERT", posX + 30.0F, posY + 12.5F, 8.0F, WARNING_COLOR);
            footerFont.drawString(String.format(Locale.ROOT, "%.1fm  %.1fs", distance, fuseSeconds),
                    posX + 30.0F, posY + 19.0F, 6.0F, ColorUtility.MUTED_COLOR);
        }

        @Override
        public float getIslandWidth() {
            return 130.0F;
        }

        @Override
        public float getIslandHeight() {
            return 25.0F;
        }

        @Override
        public int getIslandPriority() {
            return 12;
        }
    }
}
