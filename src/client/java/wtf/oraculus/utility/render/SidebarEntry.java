package wtf.oraculus.utility.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public record SidebarEntry(Text name, Text score, int scoreWidth) {
}