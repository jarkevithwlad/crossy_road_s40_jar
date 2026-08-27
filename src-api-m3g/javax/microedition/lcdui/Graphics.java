package javax.microedition.lcdui;
public class Graphics {
    public static final int HCENTER=1,VCENTER=2,LEFT=4,RIGHT=8,TOP=16,BOTTOM=32,BASELINE=64;
    public void setColor(int rgb) { }
    public void fillRect(int x,int y,int width,int height) { }
    public void drawString(String text,int x,int y,int anchor) { }
    public void setFont(Font font) { }
    public void drawRGB(int[] data,int offset,int scanlength,int x,int y,int width,int height,boolean alpha) { }
    public void drawRegion(Image src,int sx,int sy,int width,int height,int transform,int x,int y,int anchor) { }
}
