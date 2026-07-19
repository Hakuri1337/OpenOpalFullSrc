package wtf.oraculus.client.feature.module.impl.visual.overlay.impl.dynamicisland.preset;

import com.ibm.icu.impl.Pair;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;
import wtf.oraculus.client.Constants;
import wtf.oraculus.client.OraculusClient;
import wtf.oraculus.client.ReleaseInfo;
import wtf.oraculus.client.feature.helper.impl.LocalDataWatch;
import wtf.oraculus.client.feature.helper.impl.server.KnownServer;
import wtf.oraculus.client.feature.module.impl.visual.overlay.OverlayModule;
import wtf.oraculus.client.feature.module.impl.visual.overlay.impl.dynamicisland.IslandTrigger;
import wtf.oraculus.client.renderer.NVGRenderer;
import wtf.oraculus.client.renderer.image.NVGImageRenderer;
import wtf.oraculus.client.renderer.repository.FontRepository;
import wtf.oraculus.client.renderer.repository.ImageRepository;
import wtf.oraculus.client.renderer.text.NVGTextRenderer;
import wtf.oraculus.utility.render.ClientTheme;
import wtf.oraculus.utility.render.ColorUtility;

import java.util.Locale;

import static wtf.oraculus.client.Constants.mc;

public class DefaultIsland implements IslandTrigger {
    private float width;

    @Override
    public void renderIsland(DrawContext context, float posX, float posY, float width, float height, float progress) {
        final NVGTextRenderer brandFont = FontRepository.getFont("borel-regular");
        final NVGTextRenderer titleFont = FontRepository.getFont("productsans-bold");
        final NVGTextRenderer footerFont = FontRepository.getFont("productsans-medium");

        final String brandText = ReleaseInfo.NAME;
        final String releaseType = ReleaseInfo.getEditionLabel();
        final String releaseVersion = ReleaseInfo.VERSION;

        String serverAddress = "singleplayer";
        String serverPing = "0 ms";

        if (mc.getNetworkHandler() != null) {
            final ServerInfo serverInfo = mc.getNetworkHandler().getServerInfo();
            if (serverInfo != null) {
                final KnownServer currentKnownServer = LocalDataWatch.get().getKnownServerManager().getCurrentServer();
                final String mappedAddress = this.getMappedServerAddress(serverInfo.address);

                serverAddress = mappedAddress != null
                        ? mappedAddress
                        : currentKnownServer != null && currentKnownServer.getProxyServer() != null
                                ? currentKnownServer.getProxyServer().getName().toLowerCase(Locale.ROOT)
                                : serverInfo.address.toLowerCase(Locale.ROOT);

                serverAddress = serverAddress.length() > 20
                        ? serverAddress.substring(0, 20 - 3) + "..."
                        : serverAddress;

                long latency = 0;

                final PlayerListEntry playerListEntry = mc.getNetworkHandler().getPlayerListEntry(mc.getSession().getUuidOrNull());
                if (playerListEntry != null) {
                    latency = playerListEntry.getLatency();
                }

                if (latency < 2) {
                    latency = serverInfo.ping;
                }

                serverPing = latency + " ms";
            }
        }

        final float titleTextSize = 11.5f;
        final float secondaryTextSize = 7;
        final float footerTextSize = 6;

        final float releaseInfoWidth = Math.max(
                titleFont.getStringWidth(releaseType, secondaryTextSize),
                footerFont.getStringWidth(releaseVersion, footerTextSize)
        );

        this.width = 14 + brandFont.getStringWidth(brandText, titleTextSize) + releaseInfoWidth + titleFont.getStringWidth(serverAddress, secondaryTextSize) + 35;

        final ClientTheme theme = OraculusClient.getInstance().getModuleRepository().getModule(OverlayModule.class).getThemeMode().getValue();
        final Pair<Integer, Integer> colors = theme.getColors();

        final boolean grayscale = theme != ClientTheme.ORACULUS;
        final NVGImageRenderer iconRenderer = this.getAppropriateImage(mc.getWindow().getScaleFactor(), grayscale);

        final int xOffset = 10;
        final int yOffset = 5;
        final String version = ReleaseInfo.VERSION;

        final int baseYOffset = 5;
        final int baseXOffset = 10;
        final float dividerHeight = 17.5f;
        final float textSpacing = 1.5f;
        final int colorIndex = 5;

        if (grayscale) {
            final int interpolatedColor = ColorUtility.interpolateColorsBackAndForth(colorIndex, 1, colors.second, colors.first);
            iconRenderer.drawImage(posX + baseXOffset - 4, posY + baseYOffset + 1.5F, 16, 16, ColorUtility.brighter(interpolatedColor, 0.2F));
        } else {
            iconRenderer.drawImage(posX + baseXOffset - 4, posY + baseYOffset + 1.5F, 16, 16);
        }

        final float textStart = posX + 26.5f - 2;
        brandFont.drawGradientString(brandText, textStart, posY + baseYOffset + 2.5F + baseXOffset, titleTextSize, colors.second, colors.first);

        final float releaseTypeStart = textStart + brandFont.getStringWidth(brandText, titleTextSize) + 3.3f + 1;
        NVGRenderer.rect(releaseTypeStart, posY + dividerHeight / 1.5f - 2F, 0.75F, 10, ColorUtility.MUTED_COLOR);

        titleFont.drawString(releaseType, releaseTypeStart + textSpacing + 2, posY + dividerHeight / 1.3f + 1.0F, secondaryTextSize, -1);
        footerFont.drawString(releaseVersion, releaseTypeStart + textSpacing + 2, posY + 19.5f + 1.0F, footerTextSize, ColorUtility.MUTED_COLOR);

        final float serverIPStart = releaseTypeStart + textSpacing + releaseInfoWidth + 3.3f;
        NVGRenderer.rect(serverIPStart + 1, posY + dividerHeight / 1.5f - 2F, 0.75F, 10, ColorUtility.MUTED_COLOR);

        titleFont.drawString(serverAddress, serverIPStart + textSpacing + 3, posY + dividerHeight / 1.3f + 1F, secondaryTextSize, -1);
        footerFont.drawString(serverPing, serverIPStart + textSpacing + 3, posY + 19.5f + 1F, footerTextSize, ColorUtility.MUTED_COLOR);
    }

//    private NVGImageRenderer getAppropriateImage(double scaleFactor, boolean grayscale) {
//        final int size = scaleFactor > 2 ? 128 : 32;
//        final String suffix = grayscale ? "Gray Scaled Suffix" : "";
//
//        return ImageRepository.getImage(String.format("window-icons/icon_%dx%d%s.png", size, size, suffix));
//    }

    private NVGImageRenderer getAppropriateImage(double scaleFactor, boolean grayscale) {
        final int size = scaleFactor > 2 ? 128 : 32;

        return ImageRepository.getImage(String.format("window-icons/icon_%dx%d.png", size, size));
    }

    private String getMappedServerAddress(final String address) {
        final String normalizedAddress = address.toLowerCase(Locale.ROOT);
        final int portSeparator = normalizedAddress.indexOf(':');
        final String host = portSeparator == -1 ? normalizedAddress : normalizedAddress.substring(0, portSeparator);

        if (host.equals("127.0.0.1") || host.equals("localhost") || host.equals("loaclhost")) {
            return "LocalServer";
        }

        if (host.contains("fis")) {
            return "FisProxy";
        }

        return null;
    }

    @Override
    public float getIslandWidth() {
        return width;
    }

    @Override
    public float getIslandHeight() {
        return 28;
    }

    @Override
    public int getIslandPriority() {
        return -5;
    }
}
