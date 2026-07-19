package wtf.oraculus.scripting.impl.proxy;

import wtf.oraculus.client.renderer.NVGRenderer;

public class RenderProxy {

    public void rect(float x, float y, float width, float height, int color) {
        NVGRenderer.rect(x, y, width, height, color);
    }

    public void roundedRect(float x, float y, float width, float height, float radius, int color) {
        NVGRenderer.roundedRect(x, y, width, height, radius, color);
    }

}
