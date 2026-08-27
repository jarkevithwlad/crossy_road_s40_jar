package javax.microedition.lcdui;

/** Compile-time subset of the MIDP Image API used by the software texture sampler. */
public class Image {
    public static Image createImage(String name) { return null; }
    public static Image createImage(int width, int height) { return null; }
    public Graphics getGraphics() { return null; }
    public int getWidth() { return 0; }
    public int getHeight() { return 0; }
    public void getRGB(int[] rgbData, int offset, int scanlength, int x, int y,
            int width, int height) { }
}
