package wtf.oraculus.client.feature.module.impl.visual.overlay.impl.modulelist;

import net.minecraft.client.gui.DrawContext;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.feature.module.Module;
import wtf.oraculus.client.feature.module.impl.visual.overlay.IOverlayElement;
import wtf.oraculus.client.feature.module.impl.visual.overlay.OverlayModule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static wtf.oraculus.client.Constants.mc;

public final class ToggledModulesElement implements IOverlayElement {

    private final ToggledSettings settings;

    public ToggledModulesElement(final OverlayModule module) {
        this.settings = new ToggledSettings(module);
    }

    private List<ModuleElement> moduleList, visibleList;

    public void initialize() {
        Collection<Module> moduleList = OraculusClient.getInstance().getModuleRepository().getModules();
        this.moduleList = new ArrayList<>(moduleList.size());
        this.visibleList = new ArrayList<>(moduleList.size());
        moduleList.forEach(m -> this.moduleList.add(new ModuleElement(this.settings, m)));
        this.markSortingDirty();
    }

    public float getTotalHeight() {
        float height = 0;
        for (final ModuleElement element : this.visibleList) {
            height += ModuleElement.OFFSET * element.getHeightAnimation().getValue();
        }
        return height * this.settings.getScale();
    }

    public ToggledSettings getSettings() {
        return this.settings;
    }

    private boolean sortingDirty;

    public void markSortingDirty() {
        this.sortingDirty = true;
    }

    private void sort() {
        Collections.sort(this.moduleList);
        this.sortingDirty = false;
    }

    @Override
    public void render(DrawContext context, float delta, boolean isBloom) {
        this.renderPass(context, isBloom);
    }

    @Override
    public void renderBlur(DrawContext context, float delta) {
//        this.renderPass(true);
    }

    private void renderPass(final DrawContext context, final boolean isBloom) {
        if (this.sortingDirty) {
            this.tick();
            this.sort();
        }

        final int size = this.visibleList.size();
        for (int i = 0; i < size; i++) {
            final ModuleElement element = this.visibleList.get(i);
            final float nextWidth = i + 1 < size ? this.visibleList.get(i + 1).getWidth() : Float.NaN;
            element.render(context, i, nextWidth, isBloom);
        }
    }

    @Override
    public void tick() {
        this.visibleList.clear();

        int index = 0;
        for (final ModuleElement element : this.moduleList) {
            final boolean visible = element.isModuleVisible();
            element.tick(index, visible);
            if (element.isVisible()) {
                this.visibleList.add(element);
                if (visible) {
                    index++;
                }
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
