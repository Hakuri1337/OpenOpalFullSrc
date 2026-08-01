package wtf.oraculus.client.feature.module.impl.visual.overlay.impl.dynamicisland;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import wtf.oraculus.client.renderer.NVGRenderer;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;

import static org.lwjgl.nanovg.NanoVG.*;
import static wtf.oraculus.client.Constants.VG;

final class SearchSvgIcon {

    static final SearchSvgIcon INSTANCE = load();

    private final float viewWidth;
    private final float viewHeight;
    private final float circleX;
    private final float circleY;
    private final float circleRadius;
    private final float lineX1;
    private final float lineY1;
    private final float lineX2;
    private final float lineY2;
    private final float strokeWidth;
    private final int strokeColor;

    private SearchSvgIcon(
            final float viewWidth,
            final float viewHeight,
            final float circleX,
            final float circleY,
            final float circleRadius,
            final float lineX1,
            final float lineY1,
            final float lineX2,
            final float lineY2,
            final float strokeWidth,
            final int strokeColor
    ) {
        this.viewWidth = viewWidth;
        this.viewHeight = viewHeight;
        this.circleX = circleX;
        this.circleY = circleY;
        this.circleRadius = circleRadius;
        this.lineX1 = lineX1;
        this.lineY1 = lineY1;
        this.lineX2 = lineX2;
        this.lineY2 = lineY2;
        this.strokeWidth = strokeWidth;
        this.strokeColor = strokeColor;
    }

    void render(final float x, final float y, final float size) {
        final float scale = size / Math.max(this.viewWidth, this.viewHeight);
        final float offsetX = x + (size - this.viewWidth * scale) / 2;
        final float offsetY = y + (size - this.viewHeight * scale) / 2;

        nvgSave(VG);
        nvgTranslate(VG, offsetX, offsetY);
        nvgScale(VG, scale, scale);
        nvgBeginPath(VG);
        nvgCircle(VG, this.circleX, this.circleY, this.circleRadius);
        nvgMoveTo(VG, this.lineX1, this.lineY1);
        nvgLineTo(VG, this.lineX2, this.lineY2);
        nvgLineCap(VG, NVG_ROUND);
        nvgStrokeWidth(VG, this.strokeWidth);
        NVGRenderer.applyColor(this.strokeColor, NVGRenderer.NVG_COLOR_1);
        nvgStrokeColor(VG, NVGRenderer.NVG_COLOR_1);
        nvgStroke(VG);
        nvgClosePath(VG);
        nvgRestore(VG);
    }

    private static SearchSvgIcon load() {
        try (InputStream input = SearchSvgIcon.class.getResourceAsStream("/assets/oraculus/icons/search.svg")) {
            if (input == null) {
                throw new IllegalStateException("Missing search.svg");
            }

            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            final Element svg = factory.newDocumentBuilder().parse(input).getDocumentElement();
            final String[] viewBox = svg.getAttribute("viewBox").trim().split("\\s+");
            final NodeList circles = svg.getElementsByTagName("circle");
            final NodeList lines = svg.getElementsByTagName("line");
            final Element circle = (Element) circles.item(0);
            final Element line = (Element) lines.item(0);
            final String stroke = svg.getAttribute("stroke").replace("#", "");

            return new SearchSvgIcon(
                    Float.parseFloat(viewBox[2]),
                    Float.parseFloat(viewBox[3]),
                    Float.parseFloat(circle.getAttribute("cx")),
                    Float.parseFloat(circle.getAttribute("cy")),
                    Float.parseFloat(circle.getAttribute("r")),
                    Float.parseFloat(line.getAttribute("x1")),
                    Float.parseFloat(line.getAttribute("y1")),
                    Float.parseFloat(line.getAttribute("x2")),
                    Float.parseFloat(line.getAttribute("y2")),
                    Float.parseFloat(svg.getAttribute("stroke-width")),
                    0xFF000000 | Integer.parseInt(stroke, 16)
            );
        } catch (Exception exception) {
            return new SearchSvgIcon(24, 24, 10.5F, 10.5F, 6.5F, 15.5F, 15.5F, 21, 21, 1.8F, 0xFF9AA4B8);
        }
    }
}
