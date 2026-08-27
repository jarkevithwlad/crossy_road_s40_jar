package javax.microedition.lcdui;

/** Compile-time subset of the MIDP Font API used by the M3G overlay. */
public class Font {
    public static final int FACE_SYSTEM = 0;
    public static final int STYLE_PLAIN = 0;
    public static final int STYLE_BOLD = 1;
    public static final int STYLE_ITALIC = 2;
    public static final int SIZE_SMALL = 8;
    public static final int SIZE_MEDIUM = 0;
    public static final int SIZE_LARGE = 16;

    public static Font getFont(int face, int style, int size) { return null; }
}
