package wtf.opal.client.feature.module.impl.visual;

import com.ibm.icu.impl.Pair;
import org.lwjgl.glfw.GLFW;
import wtf.opal.client.OpalClient;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.DeprecatedModule;
import wtf.opal.client.feature.module.property.Property;
import wtf.opal.client.feature.module.property.impl.bool.BooleanProperty;
import wtf.opal.client.feature.module.property.impl.bool.MultipleBooleanProperty;
import wtf.opal.client.feature.module.property.impl.mode.ModeProperty;
import wtf.opal.client.feature.module.property.impl.number.NumberProperty;
import wtf.opal.client.renderer.NVGRenderer;
import wtf.opal.client.renderer.repository.FontRepository;
import wtf.opal.client.renderer.text.NVGTextRenderer;
import wtf.opal.event.impl.press.KeyPressEvent;
import wtf.opal.event.impl.render.RenderBloomEvent;
import wtf.opal.event.impl.render.RenderScreenEvent;
import wtf.opal.event.subscriber.Subscribe;
import wtf.opal.utility.render.ColorUtility;

import java.util.List;
import java.util.stream.Collectors;

import static wtf.opal.client.Constants.mc;

public final class TabGUIModule extends Module {

    private int categoryIndex;

    private boolean categoryExpanded;

    public TabGUIModule() {
        super("Tab GUI", "A display for interacting with client features.", ModuleCategory.VISUAL);
    }

    public void render() {
        NVGTextRenderer font = FontRepository.getFont("productsans-medium");
        Pair<Integer, Integer> colors = ColorUtility.getClientTheme();

        float x = 5, y = 40, width = 75, panelHeight = 16, border = 2;
        renderPanel(x, y, width, panelHeight, ModuleCategory.VALUES.length);

        for (int i = 0; i < ModuleCategory.VALUES.length; i++) {
            float yPos = y + (i * panelHeight);
            boolean isCurrent = (i == categoryIndex);
            renderTab(x, yPos, width, panelHeight, i, ModuleCategory.VALUES.length, isCurrent, colors);
            font.drawString(ModuleCategory.VALUES[i].getName(), x + 5, yPos + 11, 8.25F,
                    isCurrent ? -1 : ColorUtility.brighter(ColorUtility.MUTED_COLOR, 0.3F));
        }

        if (categoryExpanded) {
            renderModules(font, colors, x + width + 7, y, width, panelHeight);
        }

    }

    private void renderPanel(float x, float y, float width, float panelHeight, int itemCount) {
        float height = panelHeight * itemCount;
        float border = 2;
        NVGRenderer.roundedRect(x - border, y - border, width + border * 2, height + border * 2, 5.5F, NVGRenderer.BLUR_PAINT);
        NVGRenderer.roundedRect(x - border, y - border, width + border * 2, height + border * 2, 5.5F, 0x80090909);
    }

    private void renderTab(float x, float y, float width, float height, int index, int total, boolean isCurrent, Pair<Integer, Integer> colors) {
        boolean isFirst = (index == 0), isLast = (index == total - 1);
        float radius = 4;

        if (isFirst || isLast) {
            NVGRenderer.roundedRectVarying(x, y, width, height, isFirst ? radius : 0, isFirst ? radius : 0, isLast ? radius : 0, isLast ? radius : 0, NVGRenderer.BLUR_PAINT);
            NVGRenderer.roundedRectVarying(x, y, width, height, isFirst ? radius : 0, isFirst ? radius : 0, isLast ? radius : 0, isLast ? radius : 0, 0x80090909);
        } else {
            NVGRenderer.rect(x, y, width, height, NVGRenderer.BLUR_PAINT);
            NVGRenderer.rect(x, y, width, height, 0x80090909);
        }

        if (isCurrent) {
            renderGradient(x, y, width, height, isFirst, isLast, colors);
        }
    }

    private void renderGradient(float x, float y, float width, float height, boolean isFirst, boolean isLast, Pair<Integer, Integer> colors) {
        int startColor = ColorUtility.applyOpacity(colors.first, 0.4F);
        int endColor = ColorUtility.applyOpacity(colors.second, 0.4F);

        if (isFirst || isLast) {
            NVGRenderer.roundedRectVaryingGradient(x, y, width, height, isFirst ? 4 : 0, isFirst ? 4 : 0, isLast ? 4 : 0, isLast ? 4 : 0, startColor, endColor, 0);
        } else {
            NVGRenderer.rectGradient(x, y, width, height, startColor, endColor, 0);
        }
    }

    private void renderModules(NVGTextRenderer font, Pair<Integer, Integer> colors, float x, float y, float width, float panelHeight) {
        ModuleCategory category = ModuleCategory.VALUES[categoryIndex];
        List<Module> modules = getVisibleModules(category);
        if (modules.isEmpty()) return;

        normalizeModuleIndex(category, modules);

        renderPanel(x, y, width, panelHeight, modules.size());

        for (int i = 0; i < modules.size(); i++) {
            float yPos = y + (i * panelHeight);
            boolean isCurrent = (i == category.getModuleIndex());
            renderTab(x, yPos, width, panelHeight, i, modules.size(), isCurrent, colors);
            font.drawString(modules.get(i).getName(), x + 5, yPos + 11, 8.25F,
                    isCurrent ? -1 : ColorUtility.brighter(ColorUtility.MUTED_COLOR, 0.3F));

            if (isCurrent && modules.get(i).isExpanded()) {
                renderProperties(font, colors, x + width + 7, y, panelHeight, modules.get(i));
            }
        }
    }

    private void renderProperties(NVGTextRenderer font, Pair<Integer, Integer> colors, float x, float y, float panelHeight, Module module) {
        List<Property<?>> properties = module.getPropertyList();
        if (properties.isEmpty()) return;

        final double maxLength = properties.stream()
                .mapToDouble(p -> font.getStringWidth(p.getName() + ": " + getPropertyValue(p), 8.25F))
                .max().orElse(0);

        renderPanel(x, y, (float) (maxLength + 12.5F), panelHeight, properties.size());

        for (int i = 0; i < properties.size(); i++) {
            float yPos = y + (i * panelHeight);
            boolean isCurrent = (i == module.getPropertyIndex());
            renderTab(x, yPos, (float) (maxLength + 12.5F), panelHeight, i, properties.size(), isCurrent, Pair.of(ColorUtility.darker(colors.first, properties.get(i).isFocused() ? 0.35F : 0), ColorUtility.darker(colors.second, properties.get(i).isFocused() ? 0.35F : 0)));

            String propertyName = properties.get(i).getName() + ": ";
            float textX = x + 5;
            font.drawString(propertyName, textX, yPos + 11, 8.25F, isCurrent ? -1 : ColorUtility.brighter(ColorUtility.MUTED_COLOR, 0.3F));
            font.drawString(getPropertyValue(properties.get(i)), textX + font.getStringWidth(propertyName, 8.25F), yPos + 11, 8.25F,
                    isCurrent ? -1 : ColorUtility.brighter(ColorUtility.MUTED_COLOR, 0.3F));
        }
    }

    private String getPropertyValue(Property<?> property) {
        if (property instanceof BooleanProperty booleanProperty) {
            return String.valueOf(booleanProperty.getValue());
        } else if (property instanceof NumberProperty numberProperty) {
            return String.format("%.3f", numberProperty.getValue()).replaceAll("0+$", "").replaceAll("\\.$", "");
        } else if (property instanceof ModeProperty<?> modeProperty) {
            return String.valueOf(modeProperty.getValue());
        } else if (property instanceof MultipleBooleanProperty multipleBooleanProperty) {
            List<BooleanProperty> subProperties = multipleBooleanProperty.getValue();
            int selectedIndex = multipleBooleanProperty.getSubPropertyIndex();

            return subProperties.stream()
                    .map(p -> (subProperties.indexOf(p) == selectedIndex ? "**" : "") + p.getName() + ": " + p.getValue() + (subProperties.indexOf(p) == selectedIndex ? "**" : ""))
                    .collect(Collectors.joining(", ", "[", "]"));
        }
        return "";
    }

    @Subscribe
    public void onKeyPress(final KeyPressEvent event) {
        if (mc.currentScreen != null) return;

        final ModuleCategory category = ModuleCategory.VALUES[categoryIndex];
        final List<Module> moduleList = getVisibleModules(category);
        final int key = event.getInteractionCode();

        if (!categoryExpanded) {
            if (key == GLFW.GLFW_KEY_UP) {
                categoryIndex = (categoryIndex - 1 + ModuleCategory.VALUES.length) % ModuleCategory.VALUES.length;
            } else if (key == GLFW.GLFW_KEY_DOWN) {
                categoryIndex = (categoryIndex + 1) % ModuleCategory.VALUES.length;
            } else if (key == GLFW.GLFW_KEY_RIGHT && !moduleList.isEmpty()) {
                categoryExpanded = true;
            }
            return;
        }

        if (moduleList.isEmpty()) {
            if (key == GLFW.GLFW_KEY_LEFT) {
                categoryExpanded = false;
            }
            return;
        }

        normalizeModuleIndex(category, moduleList);
        final Module module = moduleList.get(category.getModuleIndex());
        final List<Property<?>> propertyList = module.getPropertyList();

        switch (key) {
            case GLFW.GLFW_KEY_UP -> handleUpKey(category, module, moduleList, propertyList);
            case GLFW.GLFW_KEY_DOWN -> handleDownKey(category, module, moduleList, propertyList);
            case GLFW.GLFW_KEY_RIGHT -> handleRightKey(module, propertyList);
            case GLFW.GLFW_KEY_LEFT -> handleLeftKey(module, propertyList);
            case GLFW.GLFW_KEY_ENTER -> handleEnterKey(module, propertyList);
            case GLFW.GLFW_KEY_TAB -> handleTabKey(module, propertyList);
        }
    }

    private void handleUpKey(ModuleCategory category, Module module, List<Module> moduleList, List<Property<?>> propertyList) {
        if (!categoryExpanded) {
            categoryIndex = (categoryIndex - 1 + ModuleCategory.VALUES.length) % ModuleCategory.VALUES.length;
        } else if (module.isExpanded() && !propertyList.isEmpty()) {
            cyclePropertyIndex(module, propertyList, -1);
        } else {
            cycleModuleIndex(category, moduleList, -1);
        }
    }

    private void handleDownKey(ModuleCategory category, Module module, List<Module> moduleList, List<Property<?>> propertyList) {
        if (!categoryExpanded) {
            categoryIndex = (categoryIndex + 1) % ModuleCategory.VALUES.length;
        } else if (module.isExpanded() && !propertyList.isEmpty()) {
            cyclePropertyIndex(module, propertyList, 1);
        } else {
            cycleModuleIndex(category, moduleList, 1);
        }
    }

    private void handleRightKey(Module module, List<Property<?>> propertyList) {
        if (!categoryExpanded) {
            categoryExpanded = true;
            return;
        }

        if (!propertyList.isEmpty()) {
            Property<?> property = propertyList.get(module.getPropertyIndex());
            if (!property.isFocused()) {
                module.setExpanded(true);
            } else {
                modifyProperty(property, true);
            }
        }
    }

    private void handleLeftKey(Module module, List<Property<?>> propertyList) {
        if (categoryExpanded && module.isExpanded()) {
            if (!propertyList.isEmpty() && !propertyList.get(module.getPropertyIndex()).isFocused()) {
                module.setExpanded(false);
            } else {
                modifyProperty(propertyList.get(module.getPropertyIndex()), false);
            }
        } else {
            categoryExpanded = false;
        }
    }

    private void handleEnterKey(Module module, List<Property<?>> propertyList) {
        if (!categoryExpanded) return;

        if (!module.isExpanded()) {
            module.toggle();
        } else {
            Property<?> property = propertyList.get(module.getPropertyIndex());
            property.setFocused(!property.isFocused());
        }
    }

    private void handleTabKey(Module module, List<Property<?>> propertyList) {
        if (propertyList.isEmpty() || !categoryExpanded) return;

        Property<?> property = propertyList.get(module.getPropertyIndex());
        if (property instanceof MultipleBooleanProperty multipleBooleanProperty) {
            if (property.isFocused()) {
                multipleBooleanProperty.cycleSubPropertyIndex();
            }
        }
    }

    private void cycleModuleIndex(ModuleCategory category, List<Module> moduleList, int direction) {
        category.setModuleIndex((category.getModuleIndex() + direction + moduleList.size()) % moduleList.size());
    }

    private List<Module> getVisibleModules(final ModuleCategory category) {
        return OpalClient.getInstance().getModuleRepository().getModulesInCategory(category).stream()
                .filter(module -> module.isVisible() || module instanceof DeprecatedModule)
                .toList();
    }

    private void normalizeModuleIndex(final ModuleCategory category, final List<Module> moduleList) {
        category.setModuleIndex(Math.floorMod(category.getModuleIndex(), moduleList.size()));
    }

    private void cyclePropertyIndex(Module module, List<Property<?>> propertyList, int direction) {
        module.setPropertyIndex((module.getPropertyIndex() + direction + propertyList.size()) % propertyList.size());
        propertyList.get(module.getPropertyIndex()).setFocused(false);
    }

    private void modifyProperty(Property<?> property, boolean increase) {
        if (property instanceof BooleanProperty booleanProperty) {
            booleanProperty.toggle();
        } else if (property instanceof NumberProperty numberProperty) {
            numberProperty.setValue(numberProperty.getValue() + (increase ? numberProperty.getIncrement() : -numberProperty.getIncrement()));
        } else if (property instanceof ModeProperty<?> modeProperty) {
            modeProperty.cycle(increase);
        } else if (property instanceof MultipleBooleanProperty multipleBooleanProperty) {
            final BooleanProperty selected = multipleBooleanProperty.getSelectedSubProperty();
            if (selected != null) selected.toggle();
        }
    }

    @Subscribe
    public void onRenderScreen(final RenderScreenEvent event) {
        render();
    }

    @Subscribe
    public void onBloomRender(final RenderBloomEvent event) {
        render();
    }

}
