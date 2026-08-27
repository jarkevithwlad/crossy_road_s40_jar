package javax.microedition.lcdui;

/** Compile-time MIDP Canvas subset for the M3G build. It is not packaged in the JAR. */
public abstract class Canvas extends Displayable {
    public static final int KEY_NUM2 = 50;
    public static final int FIRE = 8, LEFT = 2, RIGHT = 5, UP = 1, DOWN = 6;

    protected Canvas() { }
    protected void setFullScreenMode(boolean fullScreen) { }
    protected abstract void paint(Graphics graphics);
    protected void keyPressed(int keyCode) { }
    protected void keyRepeated(int keyCode) { }
    protected void showNotify() { }
    protected void sizeChanged(int width, int height) { }
    public int getGameAction(int keyCode) { return 0; }
    public int getWidth() { return 0; }
    public int getHeight() { return 0; }
}
