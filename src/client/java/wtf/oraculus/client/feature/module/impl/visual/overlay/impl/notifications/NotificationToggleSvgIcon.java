package wtf.oraculus.client.feature.module.impl.visual.overlay.impl.notifications;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import wtf.oraculus.client.renderer.NVGRenderer;
import wtf.oraculus.utility.render.ColorUtility;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;

import static org.lwjgl.nanovg.NanoVG.nvgRestore;
import static org.lwjgl.nanovg.NanoVG.nvgSave;
import static org.lwjgl.nanovg.NanoVG.nvgScale;
import static org.lwjgl.nanovg.NanoVG.nvgTranslate;
import static wtf.oraculus.client.Constants.VG;

final class NotificationToggleSvgIcon {

    static final NotificationToggleSvgIcon INSTANCE = load();

    private static final int OFF_TRACK_COLOR = 0xFF777B7E;
    private static final int OFF_KNOB_COLOR = 0xFF555A5E;
    private static final int ON_KNOB_COLOR = 0xFF69747A;

    private final float viewWidth;
    private final float viewHeight;
    private final float trackX;
    private final float trackY;
    private final float trackWidth;
    private final float trackHeight;
    private final float trackRadius;
    private final float knobOffX;
    private final float knobY;
    private final float knobRadius;

    private NotificationToggleSvgIcon(
            final float viewWidth,
            final float viewHeight,
            final float trackX,
            final float trackY,
            final float trackWidth,
            final float trackHeight,
            final float trackRadius,
            final float knobOffX,
            final float knobY,
            final float knobRadius
    ) {
        this.viewWidth = viewWidth;
        this.viewHeight = viewHeight;
        this.trackX = trackX;
        this.trackY = trackY;
        this.trackWidth = trackWidth;
        this.trackHeight = trackHeight;
        this.trackRadius = trackRadius;
        this.knobOffX = knobOffX;
        this.knobY = knobY;
        this.knobRadius = knobRadius;
    }

    void render(
            final float x,
            final float y,
            final float width,
            final float height,
            final float progress,
            final int enabledColor
    ) {
        final float scale = Math.min(width / this.viewWidth, height / this.viewHeight);
        final float offsetX = x + (width - this.viewWidth * scale) / 2;
        final float offsetY = y + (height - this.viewHeight * scale) / 2;
        final float knobOnX = this.trackX + this.trackWidth - (this.knobOffX - this.trackX);
        final float knobX = this.knobOffX + (knobOnX - this.knobOffX) * progress;
        final int trackColor = ColorUtility.interpolateColors(OFF_TRACK_COLOR, enabledColor, progress);
        final int knobColor = ColorUtility.interpolateColors(OFF_KNOB_COLOR, ON_KNOB_COLOR, progress);

        nvgSave(VG);
        nvgTranslate(VG, offsetX, offsetY);
        nvgScale(VG, scale, scale);
        NVGRenderer.roundedRect(
                this.trackX,
                this.trackY,
                this.trackWidth,
                this.trackHeight,
                this.trackRadius,
                trackColor
        );
        NVGRenderer.roundedRectOutline(
                this.trackX,
                this.trackY,
                this.trackWidth,
                this.trackHeight,
                this.trackRadius,
                0.7F,
                0x50000000
        );
        NVGRenderer.roundedRect(
                knobX - this.knobRadius,
                this.knobY - this.knobRadius,
                this.knobRadius * 2,
                this.knobRadius * 2,
                this.knobRadius,
                knobColor
        );
        nvgRestore(VG);
    }

    private static NotificationToggleSvgIcon load() {
        try (InputStream input = NotificationToggleSvgIcon.class.getResourceAsStream("/assets/oraculus/icons/notification-toggle.svg")) {
            if (input == null) {
                throw new IllegalStateException("Missing notification-toggle.svg");
            }

            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            final Element svg = factory.newDocumentBuilder().parse(input).getDocumentElement();
            final String[] viewBox = svg.getAttribute("viewBox").trim().split("\\s+");
            final NodeList rects = svg.getElementsByTagName("rect");
            final NodeList circles = svg.getElementsByTagName("circle");
            final Element track = (Element) rects.item(0);
            final Element knob = (Element) circles.item(0);

            return new NotificationToggleSvgIcon(
                    Float.parseFloat(viewBox[2]),
                    Float.parseFloat(viewBox[3]),
                    Float.parseFloat(track.getAttribute("x")),
                    Float.parseFloat(track.getAttribute("y")),
                    Float.parseFloat(track.getAttribute("width")),
                    Float.parseFloat(track.getAttribute("height")),
                    Float.parseFloat(track.getAttribute("rx")),
                    Float.parseFloat(knob.getAttribute("cx")),
                    Float.parseFloat(knob.getAttribute("cy")),
                    Float.parseFloat(knob.getAttribute("r"))
            );
        } catch (Exception exception) {
            return new NotificationToggleSvgIcon(36, 20, 1, 3, 34, 14, 7, 9, 10, 6);
        }
    }
}
