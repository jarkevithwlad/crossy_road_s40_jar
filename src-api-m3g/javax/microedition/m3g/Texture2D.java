package javax.microedition.m3g;
public class Texture2D extends Object3D {
    public static final int FILTER_BASE_LEVEL=208,FILTER_NEAREST=210,FUNC_REPLACE=228,WRAP_CLAMP=240,WRAP_REPEAT=241;
    public Texture2D(Image2D image) { }
    public void setBlending(int function) { }
    public void setFiltering(int levelFilter, int imageFilter) { }
    public void setWrapping(int s, int t) { }
}
