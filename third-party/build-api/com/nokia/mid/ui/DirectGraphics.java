package com.nokia.mid.ui;

/** Compile-time Nokia UI API stub. Not included in the MIDlet JAR. */
public interface DirectGraphics {
    int TYPE_USHORT_565_RGB = 565;

    void drawPixels(short[] pixels, boolean transparency, int offset,
            int scanlength, int x, int y, int width, int height,
            int manipulation, int format);
}
