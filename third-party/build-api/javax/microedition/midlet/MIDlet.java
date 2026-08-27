package javax.microedition.midlet;

/** Compile-time MIDP API stub. It is deliberately excluded from the JAR. */
public abstract class MIDlet {
    protected MIDlet() {
    }

    protected abstract void startApp();
    protected abstract void pauseApp();
    protected abstract void destroyApp(boolean unconditional);
    public final void notifyDestroyed() {
    }
}
