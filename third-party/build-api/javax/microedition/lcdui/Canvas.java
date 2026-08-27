package javax.microedition.lcdui;

/** Compile-time MIDP 2.0 API stub. */
public abstract class Canvas extends Displayable {
    public static final int KEY_NUM0 = 48;
    public static final int KEY_NUM1 = 49;
    public static final int KEY_NUM2 = 50;
    public static final int KEY_NUM3 = 51;
    public static final int KEY_STAR = 42;
    public static final int FIRE = 8;
    public static final int LEFT = 2;
    public static final int RIGHT = 5;
    public static final int UP = 1;
    public static final int DOWN = 6;

    protected Canvas() {
    }

    protected void setFullScreenMode(boolean fullScreen) {
    }

    protected abstract void paint(Graphics graphics);
    protected void keyPressed(int keyCode) {
    }

    protected void keyRepeated(int keyCode) {
    }

    protected void showNotify() {
    }

    public void repaint() {
    }

    public void serviceRepaints() {
    }

    public int getGameAction(int keyCode) {
        return 0;
    }
}
