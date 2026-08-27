package javax.microedition.m3g;
public class Graphics3D {
    public static final int DITHER = 2;
    public static Graphics3D getInstance() { return null; }
    /* JSR-184 declares target as Object, not lcdui.Graphics. */
    public void bindTarget(Object target) { }
    public void bindTarget(Object target, boolean depthBuffer, int hints) { }
    public void releaseTarget() { }
    public void setViewport(int x, int y, int width, int height) { }
    public void clear(Background background) { }
    public void setCamera(Camera camera, Transform transform) { }
    public int addLight(Light light, Transform transform) { return 0; }
    public void resetLights() { }
    public void render(VertexBuffer vertices, IndexBuffer triangles, Appearance appearance, Transform transform) { }
}
