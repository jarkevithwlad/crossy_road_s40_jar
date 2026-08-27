import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.GameCanvas;
import javax.microedition.midlet.MIDlet;

/** Visual-only Crossy Road scene used for the first S40 3D/FPS spike. */
public final class CrossyRoadS40Midlet extends MIDlet {
    private SceneCanvas canvas;

    protected void startApp() {
        if (canvas == null) canvas = new SceneCanvas();
        Display.getDisplay(this).setCurrent(canvas);
        canvas.start();
    }

    protected void pauseApp() {
        if (canvas != null) canvas.stop();
    }

    protected void destroyApp(boolean unconditional) {
        if (canvas != null) canvas.stop();
    }

    private static final class SceneCanvas extends GameCanvas implements Runnable {
        private static final int TARGET_FRAME_MS = 16;
        private static final int INTERPOLATION_SHIFT = 12;
        private static final int HUD_SCALE = 4;
        private static final int SKY = 0x8ED5E8;
        private static final int HUD = 0x19344A;
        private static final int WHITE = 0xFFFFFF;
        private static final int LOGICAL_W = 320;
        private static final int LOGICAL_H = 240;
        private static final int PHYSICAL_W = 240;
        private static final int PHYSICAL_H = 320;
        private static final int CENTER_X = 160;
        // BASE CAMERA: accepted current framing, kept stable for the next stages.
        private static final int BASE_HORIZON = 70;
        private static final int BASE_SCALE = 28;
        private static final int HORIZON = BASE_HORIZON;
        private static final int SCALE = BASE_SCALE;
        // Same target/framing as the accepted base camera.
        private static final int CAMERA_TARGET_X_Q = -397;
        private static final int CAMERA_TARGET_Z_Q = 0;
        // Perspective distance is internal; it does not move the camera target.
        private static final int CAMERA_DISTANCE_Q = 4096;
        private static final int CAMERA_YAW_COS_Q = 985;
        private static final int CAMERA_YAW_SIN_Q = 174;
        private static final int FOCAL_LENGTH = 240;
        private static final boolean USE_ORTHOGRAPHIC_CAMERA = true;
        private static final int RAILROAD_ROW_Z = 0;
        private static final int LOG_RIVER_ROW_Z = -1;
        private static final int LILY_RIVER_ROW_Z = 5;
        // Keep the signal on the original side of the railroad, only slightly farther out.
        private static final int SIGNAL_ROW_Z = RAILROAD_ROW_Z;
        private static final int SIGNAL_Z_OFFSET_Q = 192;
        // One more 180-degree turn relative to the previous signal orientation.
        private static final int SIGNAL_Y_ROTATION = 0;
        private static final int TRAIN_WARNING_MS = 3000;
        private static final int TRAIN_PASS_MS = 18000;
        private static final int TRAIN_CYCLE_MS = TRAIN_WARNING_MS + TRAIN_PASS_MS + 3000;
        // Large hidden margins let the train shuttle instead of teleporting in view.
        private static final int TRAIN_MIN_X = -18000;
        private static final int TRAIN_MAX_X = 18000;
        private static final int TRAIN_DRAW_MIN_X = -10000;
        private static final int TRAIN_DRAW_MAX_X = 10000;
        private static final int LOG_MIN_X = -3000;
        private static final int LOG_MAX_X = 3000;
        private static final int LOG_SPAN_X = LOG_MAX_X - LOG_MIN_X + 1;
        // 5x the previous speed (4200 / 6000 world units per millisecond).
        private static final int TRAIN_SPEED_NUMERATOR = 3500;

        private volatile boolean running;
        private Thread worker;
        private long fpsWindow;
        private int fpsFrames;
        private int fps;
        private int frameTriangles;
        private int sceneTriangles;
        private int clearMs;
        private int rasterMs;
        private int drawRgbMs;
        private int flushMs;
        private final int[] pixels = new int[PHYSICAL_W * PHYSICAL_H];
        private final int[] depth = new int[PHYSICAL_W * PHYSICAL_H];
        private final Texture[] textures = new Texture[18];
        private long animationEpoch;
        private int trainDirection;
        private long lastAnimationTime;
        private int lastTrainCycle = -1;
        // Fixed reusable slots: objects wrap around instead of being spawned.
        private final int[] carX = new int[] {-1200, 0, 1200, 300};
        private final int[] carDirection = new int[] {1, 1, -1, -1};
        private final int[] logX = new int[] {-3000, -800};
        private final int[] logDirection = new int[] {1, 1};
        private final int[] lilyX = new int[] {0, 0};
        private boolean mapGenerated;
        private int mapRandomState;
        private int trainHeadX = TRAIN_MIN_X;
        private boolean trainVisible;
        // Reused projection result. Keeping it on the canvas avoids allocating
        // temporary arrays/objects for every vertex on CLDC devices.
        private int projectedX;
        private int projectedY;
        private int projectedDepth;

        SceneCanvas() {
            super(false);
            setFullScreenMode(true);
            loadTextures();
        }

        synchronized void start() {
            if (running) return;
            if (animationEpoch == 0L) {
                animationEpoch = System.currentTimeMillis();
                trainDirection = (int)(animationEpoch & 1L) == 0 ? 1 : -1;
                lastAnimationTime = 0L;
            }
            if (!mapGenerated) generateMap();
            running = true;
            worker = new Thread(this);
            worker.start();
        }

        synchronized void stop() {
            running = false;
            worker = null;
        }

        public void run() {
            Thread current = Thread.currentThread();
            fpsWindow = System.currentTimeMillis();
            while (running && worker == current) {
                long started = System.currentTimeMillis();
                drawFrame();
                ++fpsFrames;
                long now = System.currentTimeMillis();
                if (now - fpsWindow >= 1000L) {
                    fps = fpsFrames;
                    fpsFrames = 0;
                    fpsWindow = now;
                }
                long delay = TARGET_FRAME_MS - (System.currentTimeMillis() - started);
                if (delay > 0L) sleep(delay);
            }
        }

        protected void paint(Graphics graphics) {
            drawFrame();
        }

        protected void showNotify() {
            fpsWindow = System.currentTimeMillis();
        }

        private void drawFrame() {
            Graphics g = getGraphics();
            long stageStarted = System.currentTimeMillis();
            long animationTime = System.currentTimeMillis() - animationEpoch;
            updateAnimation(animationTime);
            int p;
            for (p = 0; p < pixels.length; ++p) {
                pixels[p] = SKY;
                depth[p] = -2147483647;
            }
            clearMs = (int)(System.currentTimeMillis() - stageStarted);
            stageStarted = System.currentTimeMillis();
            frameTriangles = 0;
            drawRows(g, animationTime);
            drawProps(g, animationTime);
            rasterMs = (int)(System.currentTimeMillis() - stageStarted);
            drawHudPixels();
            stageStarted = System.currentTimeMillis();
            g.drawRGB(pixels, 0, PHYSICAL_W, 0, 0, PHYSICAL_W, PHYSICAL_H, false);
            drawRgbMs = (int)(System.currentTimeMillis() - stageStarted);
            stageStarted = System.currentTimeMillis();
            flushGraphics();
            flushMs = (int)(System.currentTimeMillis() - stageStarted);
            sceneTriangles = frameTriangles;
        }

        private void drawRows(Graphics g, long animationTime) {
            int z;
            for (z = -4; z <= 7; ++z) {
                int kind = z == -3 || z == 2 ? 1 : (z == -1 || z == 5 ? 2 : (z == 0 ? 3 : 0));
                int color = kind == 1 ? 0xD6A85D : (kind == 2 ? 0x4AAED0 : (kind == 3 ? 0x646B78 : 0x72B84E));
                int[][] rowMesh = kind == 1 ? AssetMeshes.ROAD : (kind == 2 ? AssetMeshes.RIVER : (kind == 3 ? AssetMeshes.RAIL : AssetMeshes.ROW));
                drawMesh(g, rowMesh, 0, 0, z, color, 0, 0, kind);
                if (kind == 1) {
                    int firstSlot = z == -3 ? 0 : 2;
                    int secondSlot = firstSlot + 1;
                    int turn = carDirection[firstSlot] > 0 ? 3 : 1;
                    drawMesh(g, AssetMeshes.CAR, carX[firstSlot], 97, z, 0x2D69C4, 2, turn, 4);
                    drawMesh(g, AssetMeshes.CAR, carX[secondSlot], 97, z, 0xE84D42, 1, turn, 4);
                } else if (kind == 2) {
                    if (z == LOG_RIVER_ROW_Z) {
                        drawMesh(g, AssetMeshes.LOG, logX[0], -64, z, 0x91572E, 1, 0, 5);
                        drawMesh(g, AssetMeshes.LOG, logX[1], -64, z, 0x91572E, 2, 0, 5);
                    } else if (z == LILY_RIVER_ROW_Z) {
                        // Lily pads are generated near the map center and then reused.
                        drawMesh(g, AssetMeshes.LILY, lilyX[0], 41, z, 0x54B85A, 2, 0, 6);
                        drawMesh(g, AssetMeshes.LILY, lilyX[1], 41, z, 0x54B85A, 1, 0, 6);
                    }
                } else if (kind == 0 && (z & 1) == 0) {
                    drawMesh(g, AssetMeshes.TREE, -243, 0, z, 0x3C9E4A, 2, 0, 7);
                    drawMesh(g, AssetMeshes.BOULDER, 230, 0, z, 0x777B76, 1, 0, 8);
                }
            }
            drawMesh(g, AssetMeshes.HERO, 0, 100, 1, 0xF2D34F, 3, 2, 9);
        }

        private void drawProps(Graphics g, long animationTime) {
            if (isTrainApproachingCenter()) {
                int blinkFrame = (int)((animationTime / 250L) & 1L);
                int[][] signalMesh = blinkFrame == 0 ? AssetMeshes.TRAIN_LIGHT_ON1 : AssetMeshes.TRAIN_LIGHT_ON2;
                int signalTexture = blinkFrame == 0 ? 15 : 16;
                drawMesh(g, signalMesh, -397, 0, SIGNAL_ROW_Z, SIGNAL_Z_OFFSET_Q, 0xE43C45, 1, SIGNAL_Y_ROTATION, signalTexture);
            } else {
                // After the warning cycle all signal lights are off, using the same valid UV layout.
                drawMesh(g, AssetMeshes.TRAIN_LIGHT_ON1, -397, 0, SIGNAL_ROW_Z, SIGNAL_Z_OFFSET_Q, 0xE43C45, 1, SIGNAL_Y_ROTATION, 17);
            }
            if (trainVisible) {
                // The train asset is authored across the track; rotate it 90 degrees around Y.
                int turn = trainDirection > 0 ? 2 : 0;
                int frontX = trainDirection > 0 ? trainHeadX + 2500 : trainHeadX - 2500;
                int middleX = trainDirection > 0 ? trainHeadX + 1250 : trainHeadX - 1250;
                drawMesh(g, TrainFrontMesh.DATA, frontX, 26, RAILROAD_ROW_Z, 0, 0xE1E5E6, 2, turn, 11);
                drawMesh(g, TrainMesh.DATA, middleX, 26, RAILROAD_ROW_Z, 0, 0xE1E5E6, 2, turn, 10);
                drawMesh(g, TrainBackMesh.DATA, trainHeadX, 26, RAILROAD_ROW_Z, 0, 0xE1E5E6, 2, turn, 12);
            }
        }

        private void updateAnimation(long animationTime) {
            long delta = animationTime - lastAnimationTime;
            if (delta < 0L) delta = 0L;
            if (delta > 100L) delta = 100L;
            lastAnimationTime = animationTime;
            int i;
            for (i = 0; i < carX.length; ++i) {
                carX[i] += (int)(carDirection[i] * 690L * delta / 1000L);
                carX[i] = wrap(carX[i], -1500, 1500);
            }
            // Both logs share one deterministic clock and a fixed half-route phase offset.
            int logPhase = (int)((animationTime * 144L / 1000L) % (long)LOG_SPAN_X);
            logX[0] = LOG_MIN_X + logPhase;
            logX[1] = LOG_MIN_X + ((logPhase + LOG_SPAN_X / 2) % LOG_SPAN_X);
            trainHeadX += (int)(trainDirection * (long)TRAIN_SPEED_NUMERATOR * delta / 1000L);
            if (trainHeadX >= TRAIN_MAX_X) {
                trainHeadX = TRAIN_MAX_X;
                trainDirection = -1;
            } else if (trainHeadX <= TRAIN_MIN_X) {
                trainHeadX = TRAIN_MIN_X;
                trainDirection = 1;
            }
            trainVisible = trainHeadX > TRAIN_DRAW_MIN_X && trainHeadX < TRAIN_DRAW_MAX_X;
        }

        private boolean isTrainApproachingCenter() {
            int trainCenter = trainHeadX + (trainDirection > 0 ? 1250 : -1250);
            int railroadCenter = CAMERA_TARGET_X_Q;
            int distance = trainDirection > 0 ? railroadCenter - trainCenter : trainCenter - railroadCenter;
            return distance >= 0 && distance <= TRAIN_SPEED_NUMERATOR * TRAIN_WARNING_MS / 1000;
        }

        private int wrap(int value, int minimum, int maximum) {
            int span = maximum - minimum + 1;
            while (value < minimum) value += span;
            while (value > maximum) value -= span;
            return value;
        }

        private void generateMap() {
            mapRandomState = (int)animationEpoch;
            lilyX[0] = -320 + nextMapRandom(641);
            lilyX[1] = -320 + nextMapRandom(641);
            if (absolute(lilyX[1] - lilyX[0]) < 96) lilyX[1] += 144;
            lilyX[1] = wrap(lilyX[1], -320, 320);
            mapGenerated = true;
        }

        private int nextMapRandom(int bound) {
            mapRandomState = mapRandomState * 1103515245 + 12345;
            int value = (mapRandomState & 0x7FFFFFFF) % bound;
            return value;
        }

        private void drawTrainNormals() {
            int i;
            int[] mesh = TrainMesh.DATA[0];
            for (i = 0; i < mesh.length; i += 12) {
                if ((i / 12) % 12 != 0) continue;
                int ax = mesh[i];
                int ay = mesh[i + 1];
                int az = mesh[i + 2];
                int bx = mesh[i + 4];
                int by = mesh[i + 5];
                int bz = mesh[i + 6];
                int cx = mesh[i + 8];
                int cy = mesh[i + 9];
                int cz = mesh[i + 10];
                int ux = bx - ax;
                int uy = by - ay;
                int uz = bz - az;
                int vx = cx - ax;
                int vy = cy - ay;
                int vz = cz - az;
                int nx = uy * vz - uz * vy;
                int ny = uz * vx - ux * vz;
                int nz = ux * vy - uy * vx;
                int length = absolute(nx);
                if (absolute(ny) > length) length = absolute(ny);
                if (absolute(nz) > length) length = absolute(nz);
                if (length < 1) continue;
                nx = nx * 28 / length;
                ny = ny * 28 / length;
                nz = nz * 28 / length;
                int centerX = (ax + bx + cx) / 3 + 371;
                int centerY = (ay + by + cy) / 3 + 26;
                int centerZ = (az + bz + cz) / 3 + 5 * 256;
                drawNormalLine(centerX, centerY, centerZ, nx, ny, nz, 0xFF3030);
                drawNormalLine(centerX, centerY, centerZ, -nx, -ny, -nz, 0x3080FF);
            }
        }

        private void drawNormalLine(int x, int y, int z, int nx, int ny, int nz, int color) {
            int x0 = projectX(x, y, z, 0, 0, 0);
            int y0 = projectY(x, y, z, 0, 0, 0);
            int x1 = projectX(x + nx, y + ny, z + nz, 0, 0, 0);
            int y1 = projectY(x + nx, y + ny, z + nz, 0, 0, 0);
            rasterLine(y0, PHYSICAL_H - 1 - x0, y1, PHYSICAL_H - 1 - x1, color);
        }

        private void rasterLine(int x0, int y0, int x1, int y1, int color) {
            int dx = absolute(x1 - x0);
            int sx = x0 < x1 ? 1 : -1;
            int dy = -absolute(y1 - y0);
            int sy = y0 < y1 ? 1 : -1;
            int error = dx + dy;
            while (true) {
                if (x0 >= 0 && x0 < PHYSICAL_W && y0 >= 0 && y0 < PHYSICAL_H) pixels[y0 * PHYSICAL_W + x0] = color;
                if (x0 == x1 && y0 == y1) return;
                int e2 = error * 2;
                if (e2 >= dy) { error += dy; x0 += sx; }
                if (e2 <= dx) { error += dx; y0 += sy; }
            }
        }

        private void drawMesh(Graphics g, int[] mesh, int ox, int oy, int oz, int color, int shadePhase, int turn, int textureId) {
            drawMesh(g, mesh, ox, oy, oz, 0, color, shadePhase, turn, textureId);
        }

        private void drawMesh(Graphics g, int[][] meshParts, int ox, int oy, int oz, int color, int shadePhase, int turn, int textureId) {
            int part;
            for (part = 0; part < meshParts.length; ++part) {
                drawMesh(g, meshParts[part], ox, oy, oz, 0, color, shadePhase, turn, textureId);
            }
        }

        private void drawMesh(Graphics g, int[][] meshParts, int ox, int oy, int oz, int ozOffsetQ, int color, int shadePhase, int turn, int textureId) {
            int part;
            for (part = 0; part < meshParts.length; ++part) {
                drawMesh(g, meshParts[part], ox, oy, oz, ozOffsetQ, color, shadePhase, turn, textureId);
            }
        }

        private void drawMesh(Graphics g, int[] mesh, int ox, int oy, int oz, int ozOffsetQ, int color, int shadePhase, int turn, int textureId) {
            int i;
            int worldZQ = oz * 256 + ozOffsetQ;
            for (i = 0; i < mesh.length; i += 12) {
                int ax3;
                int ay3;
                int az3;
                int bx3;
                int by3;
                int bz3;
                int cx3;
                int cy3;
                int cz3;
                if (turn == 1) {
                    ax3 = -mesh[i + 2]; ay3 = mesh[i + 1]; az3 = mesh[i];
                    bx3 = -mesh[i + 6]; by3 = mesh[i + 5]; bz3 = mesh[i + 4];
                    cx3 = -mesh[i + 10]; cy3 = mesh[i + 9]; cz3 = mesh[i + 8];
                } else if (turn == 2) {
                    ax3 = -mesh[i]; ay3 = mesh[i + 1]; az3 = -mesh[i + 2];
                    bx3 = -mesh[i + 4]; by3 = mesh[i + 5]; bz3 = -mesh[i + 6];
                    cx3 = -mesh[i + 8]; cy3 = mesh[i + 9]; cz3 = -mesh[i + 10];
                } else if (turn == 3) {
                    ax3 = mesh[i + 2]; ay3 = mesh[i + 1]; az3 = -mesh[i];
                    bx3 = mesh[i + 6]; by3 = mesh[i + 5]; bz3 = -mesh[i + 4];
                    cx3 = mesh[i + 10]; cy3 = mesh[i + 9]; cz3 = -mesh[i + 8];
                } else if (turn == 4) {
                    ax3 = mesh[i]; ay3 = -mesh[i + 2]; az3 = mesh[i + 1];
                    bx3 = mesh[i + 4]; by3 = -mesh[i + 6]; bz3 = mesh[i + 5];
                    cx3 = mesh[i + 8]; cy3 = -mesh[i + 10]; cz3 = mesh[i + 9];
                } else if (turn == 5) {
                    ax3 = -mesh[i]; ay3 = -mesh[i + 2]; az3 = -mesh[i + 1];
                    bx3 = -mesh[i + 4]; by3 = -mesh[i + 6]; bz3 = -mesh[i + 5];
                    cx3 = -mesh[i + 8]; cy3 = -mesh[i + 10]; cz3 = -mesh[i + 9];
                } else {
                    ax3 = mesh[i]; ay3 = mesh[i + 1]; az3 = mesh[i + 2];
                    bx3 = mesh[i + 4]; by3 = mesh[i + 5]; bz3 = mesh[i + 6];
                    cx3 = mesh[i + 8]; cy3 = mesh[i + 9]; cz3 = mesh[i + 10];
                }
                projectVertex(ax3, ay3, az3, ox, oy, worldZQ);
                int ax = projectedX;
                int ay = projectedY;
                int da = projectedDepth;
                projectVertex(bx3, by3, bz3, ox, oy, worldZQ);
                int bx = projectedX;
                int by = projectedY;
                int db = projectedDepth;
                projectVertex(cx3, cy3, cz3, ox, oy, worldZQ);
                int cx = projectedX;
                int cy = projectedY;
                int dc = projectedDepth;
                int rax = ay;
                int ray = PHYSICAL_H - 1 - ax;
                int rbx = by;
                int rby = PHYSICAL_H - 1 - bx;
                int rcx = cy;
                int rcy = PHYSICAL_H - 1 - cx;
                int uv0 = mesh[i + 3];
                int uv1 = mesh[i + 7];
                int uv2 = mesh[i + 11];
                if (textureId == 13 && turn == SIGNAL_Y_ROTATION) {
                    uv0 ^= 0xFF00;
                    uv1 ^= 0xFF00;
                    uv2 ^= 0xFF00;
                }
                rasterTriangle(rax, ray, rbx, rby, rcx, rcy, da, db, dc,
                        uv0, uv1, uv2, textureId, color);
                ++frameTriangles;
            }
        }

        private int rotateX(int x, int z, int turn) {
            if (turn == 5) return -x;
            if (turn == 1) return -z;
            if (turn == 2) return -x;
            if (turn == 3) return z;
            return x;
        }

        private int rotateZ(int x, int z, int turn) {
            if (turn == 4) return z;
            if (turn == 1) return x;
            if (turn == 2) return -z;
            if (turn == 3) return -x;
            return z;
        }

        private int rotateY(int x, int y, int z, int turn) {
            if (turn == 4) return -z;
            if (turn == 5) return -z;
            return y;
        }

        private void loadTextures() {
            String[] names = {"grass", "road", "river", "rail", "car", "log", "lily", "tree", "boulder", "hero", "train", "train-front", "train-back", "train-light", "unused", "train-light-on1", "train-light-on2", "train-light-off"};
            int i;
            for (i = 0; i < names.length; ++i) textures[i] = Texture.load("/textures/" + names[i] + ".png");
        }

        private void projectVertex(int x, int y, int z, int ox, int oy, int ozQ) {
            int dx = x + ox - CAMERA_TARGET_X_Q;
            int dy = y + oy;
            int dz = z + ozQ - CAMERA_TARGET_Z_Q;
            int rotatedX = (dx * CAMERA_YAW_COS_Q + dz * CAMERA_YAW_SIN_Q) / 1000;
            int rotatedZ = (dz * CAMERA_YAW_COS_Q - dx * CAMERA_YAW_SIN_Q) / 1000;
            if (USE_ORTHOGRAPHIC_CAMERA) {
                projectedX = CENTER_X + (rotatedX - rotatedZ) * SCALE / 256;
                projectedY = HORIZON + (rotatedX + rotatedZ) * SCALE * 48 / (256 * 100) - dy * SCALE / 256;
                projectedDepth = (rotatedX + rotatedZ) + dy / 2;
            } else {
                int depthQ = CAMERA_DISTANCE_Q - rotatedX - rotatedZ - dy * 48 / 50;
                if (depthQ < 64) depthQ = 64;
                projectedX = CENTER_X + (rotatedX - rotatedZ) * FOCAL_LENGTH / depthQ;
                projectedY = HORIZON - (dy - (rotatedX + rotatedZ) * 48 / 100) * FOCAL_LENGTH / depthQ;
                projectedDepth = -depthQ;
            }
        }

        private int projectX(int x, int y, int z, int ox, int oy, int ozQ) {
            int dx = x + ox - CAMERA_TARGET_X_Q;
            int dz = z + ozQ - CAMERA_TARGET_Z_Q;
            int rotatedX = (dx * CAMERA_YAW_COS_Q + dz * CAMERA_YAW_SIN_Q) / 1000;
            int rotatedZ = (dz * CAMERA_YAW_COS_Q - dx * CAMERA_YAW_SIN_Q) / 1000;
            if (USE_ORTHOGRAPHIC_CAMERA) return CENTER_X + (rotatedX - rotatedZ) * SCALE / 256;
            int depthQ = viewDepth(x, y, z, ox, oy, ozQ);
            return CENTER_X + (rotatedX - rotatedZ) * FOCAL_LENGTH / depthQ;
        }

        private int projectY(int x, int y, int z, int ox, int oy, int ozQ) {
            int dx = x + ox - CAMERA_TARGET_X_Q;
            int dy = y + oy;
            int dz = z + ozQ - CAMERA_TARGET_Z_Q;
            int rotatedX = (dx * CAMERA_YAW_COS_Q + dz * CAMERA_YAW_SIN_Q) / 1000;
            int rotatedZ = (dz * CAMERA_YAW_COS_Q - dx * CAMERA_YAW_SIN_Q) / 1000;
            if (USE_ORTHOGRAPHIC_CAMERA) return HORIZON + (rotatedX + rotatedZ) * SCALE * 48 / (256 * 100) - dy * SCALE / 256;
            int depthQ = viewDepth(x, y, z, ox, oy, ozQ);
            return HORIZON - (dy - (rotatedX + rotatedZ) * 48 / 100) * FOCAL_LENGTH / depthQ;
        }

        private int viewDepth(int x, int y, int z, int ox, int oy, int ozQ) {
            int dx = x + ox - CAMERA_TARGET_X_Q;
            int dy = y + oy;
            int dz = z + ozQ - CAMERA_TARGET_Z_Q;
            int rotatedX = (dx * CAMERA_YAW_COS_Q + dz * CAMERA_YAW_SIN_Q) / 1000;
            int rotatedZ = (dz * CAMERA_YAW_COS_Q - dx * CAMERA_YAW_SIN_Q) / 1000;
            if (USE_ORTHOGRAPHIC_CAMERA) return -(rotatedX + rotatedZ) - dy / 2;
            int depthQ = CAMERA_DISTANCE_Q - rotatedX - rotatedZ - dy * 48 / 50;
            return depthQ < 64 ? 64 : depthQ;
        }

        private void drawHudPixels() {
            drawLogicalText("CROSSY ROAD S40", 4, 4, WHITE);
            drawLogicalText("FPS " + fps, 4, 28, 0xFFF070);
            drawLogicalText("TRI " + sceneTriangles, 4, 52, 0xFFF070);
            drawLogicalText("C " + clearMs, 4, 76, 0xFFFFFF);
            drawLogicalText("R " + rasterMs, 4, 100, 0xFFFFFF);
            drawLogicalText("D " + drawRgbMs, 4, 124, 0xFFFFFF);
            drawLogicalText("F " + flushMs, 4, 148, 0xFFFFFF);
        }

        private void drawLogicalText(String text, int x, int y, int color) {
            int i;
            for (i = 0; i < text.length(); ++i) {
                drawGlyph(text.charAt(i), x + i * 4 * HUD_SCALE, y, color);
            }
        }

        private void drawGlyph(char ch, int x, int y, int color) {
            int pattern = glyph(ch);
            int row;
            int col;
            for (row = 0; row < 5; ++row) {
                int bits = (pattern >> ((4 - row) * 4)) & 7;
                for (col = 0; col < 3; ++col) {
                    if ((bits & (4 >> col)) != 0) {
                        int px;
                        int py;
                        for (py = 0; py < HUD_SCALE; ++py) {
                            for (px = 0; px < HUD_SCALE; ++px) {
                                putLogicalPixel(x + col * HUD_SCALE + px,
                                        y + row * HUD_SCALE + py, color);
                            }
                        }
                    }
                }
            }
        }

        private void putLogicalPixel(int x, int y, int color) {
            int px = y;
            int py = PHYSICAL_H - 1 - x;
            if (px >= 0 && px < PHYSICAL_W && py >= 0 && py < PHYSICAL_H) pixels[py * PHYSICAL_W + px] = color;
        }

        private int glyph(char ch) {
            switch (ch) {
                case 'A': return 0x25755; case 'C': return 0x74447; case 'D': return 0x64446;
                case 'E': return 0x74647; case 'F': return 0x74644; case 'I': return 0x72227;
                case 'O': return 0x74447; case 'P': return 0x74644; case 'R': return 0x64655;
                case 'S': return 0x71217; case 'T': return 0x72222; case 'Y': return 0x55222;
                case '0': return 0x75557; case '1': return 0x26227; case '2': return 0x61247;
                case '3': return 0x61216; case '4': return 0x55711; case '5': return 0x74616;
                case '6': return 0x34652; case '7': return 0x71222; case '8': return 0x25252;
                case '9': return 0x25216; default: return 0;
            }
        }

        private void rasterTriangle(int x0, int y0, int x1, int y1, int x2, int y2,
                int d0, int d1, int d2, int uv0, int uv1, int uv2, int textureId, int color) {
            int area = (x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0);
            if (area == 0) return;
            if ((x0 < 0 && x1 < 0 && x2 < 0)
                    || (x0 >= PHYSICAL_W && x1 >= PHYSICAL_W && x2 >= PHYSICAL_W)
                    || (y0 < 0 && y1 < 0 && y2 < 0)
                    || (y0 >= PHYSICAL_H && y1 >= PHYSICAL_H && y2 >= PHYSICAL_H)) return;

            // Sort a copy of the screen coordinates by Y. Attributes keep
            // their original order because their gradients are planar.
            int sx0 = x0;
            int sy0 = y0;
            int sx1 = x1;
            int sy1 = y1;
            int sx2 = x2;
            int sy2 = y2;
            int swap;
            if (sy0 > sy1) {
                swap = sy0; sy0 = sy1; sy1 = swap;
                swap = sx0; sx0 = sx1; sx1 = swap;
            }
            if (sy1 > sy2) {
                swap = sy1; sy1 = sy2; sy2 = swap;
                swap = sx1; sx1 = sx2; sx2 = swap;
            }
            if (sy0 > sy1) {
                swap = sy0; sy0 = sy1; sy1 = swap;
                swap = sx0; sx0 = sx1; sx1 = swap;
            }
            if (sy2 < 0 || sy0 >= PHYSICAL_H) return;

            int yStart = sy0 < 0 ? 0 : sy0;
            int yEnd = sy2 >= PHYSICAL_H ? PHYSICAL_H - 1 : sy2;
            int longStep = ((sx2 - sx0) << 16) / (sy2 - sy0);
            int longX = (sx0 << 16) + longStep * (yStart - sy0);
            boolean usingTop = yStart <= sy1 && sy1 > sy0;
            int shortStep;
            int shortX;
            if (usingTop) {
                shortStep = ((sx1 - sx0) << 16) / (sy1 - sy0);
                shortX = (sx0 << 16) + shortStep * (yStart - sy0);
            } else {
                if (sy2 == sy1) return;
                shortStep = ((sx2 - sx1) << 16) / (sy2 - sy1);
                shortX = (sx1 << 16) + shortStep * (yStart - sy1);
            }

            Texture texture = textures[textureId];
            int u0 = uv0 >> 8;
            int u1 = uv1 >> 8;
            int u2 = uv2 >> 8;
            int v0 = uv0 & 255;
            int v1 = uv1 & 255;
            int v2 = uv2 & 255;
            int zStepX = fixedGradientX(d0, d1, d2, y0, y1, y2, area);
            int zStepY = fixedGradientY(d0, d1, d2, x0, x1, x2, area);
            int uStepX = fixedTextureGradientX(u0, u1, u2, y0, y1, y2, area);
            int uStepY = fixedTextureGradientY(u0, u1, u2, x0, x1, x2, area);
            int vStepX = fixedTextureGradientX(v0, v1, v2, y0, y1, y2, area);
            int vStepY = fixedTextureGradientY(v0, v1, v2, x0, x1, x2, area);

            int y;
            for (y = yStart; y <= yEnd; ++y) {
                if (usingTop && y > sy1) {
                    usingTop = false;
                    if (sy2 == sy1) break;
                    shortStep = ((sx2 - sx1) << 16) / (sy2 - sy1);
                    shortX = (sx1 << 16) + shortStep * (y - sy1);
                }
                int left = longX < shortX ? longX : shortX;
                int right = longX > shortX ? longX : shortX;
                int xStart = (left + 65535) >> 16;
                int xEnd = right >> 16;
                if (xStart < 0) xStart = 0;
                if (xEnd >= PHYSICAL_W) xEnd = PHYSICAL_W - 1;
                if (xStart <= xEnd) {
                    int z = (d0 << INTERPOLATION_SHIFT)
                            + zStepX * (xStart - x0) + zStepY * (y - y0);
                    int u = (u0 << INTERPOLATION_SHIFT)
                            + uStepX * (xStart - x0) + uStepY * (y - y0);
                    int v = (v0 << INTERPOLATION_SHIFT)
                            + vStepX * (xStart - x0) + vStepY * (y - y0);
                    int at = y * PHYSICAL_W + xStart;
                    int x;
                    for (x = xStart; x <= xEnd; ++x) {
                        if (z > depth[at]) {
                            depth[at] = z;
                            if (texture == null) {
                                pixels[at] = color;
                            } else {
                                int tu = u >> INTERPOLATION_SHIFT;
                                int tv = v >> INTERPOLATION_SHIFT;
                                if (tu < 0) tu = 0;
                                else if (tu > 255) tu = 255;
                                if (tv < 0) tv = 0;
                                else if (tv > 255) tv = 255;
                                pixels[at] = texture.pixels[
                                        texture.rowFromV[tv] + texture.xFromU[tu]];
                            }
                        }
                    z += zStepX;
                    u += uStepX;
                    v += vStepX;
                    ++at;
                    }
                }
                longX += longStep;
                shortX += shortStep;
            }
        }

        private static int fixedGradientX(int value0, int value1, int value2,
                int y0, int y1, int y2, int area) {
            long numerator = (long)value0 * (y1 - y2)
                    + (long)value1 * (y2 - y0) + (long)value2 * (y0 - y1);
            return (int)((numerator << INTERPOLATION_SHIFT) / area);
        }

        private static int fixedGradientY(int value0, int value1, int value2,
                int x0, int x1, int x2, int area) {
            long numerator = (long)value0 * (x2 - x1)
                    + (long)value1 * (x0 - x2) + (long)value2 * (x1 - x0);
            return (int)((numerator << INTERPOLATION_SHIFT) / area);
        }

        private static int fixedTextureGradientX(int value0, int value1, int value2,
                int y0, int y1, int y2, int area) {
            int numerator = value0 * (y1 - y2)
                    + value1 * (y2 - y0) + value2 * (y0 - y1);
            return (numerator << INTERPOLATION_SHIFT) / area;
        }

        private static int fixedTextureGradientY(int value0, int value1, int value2,
                int x0, int x1, int x2, int area) {
            int numerator = value0 * (x2 - x1)
                    + value1 * (x0 - x2) + value2 * (x1 - x0);
            return (numerator << INTERPOLATION_SHIFT) / area;
        }

        private static int shade(int rgb, int percent) {
            int r = ((rgb >> 16) & 255) * percent / 100;
            int g = ((rgb >> 8) & 255) * percent / 100;
            int b = (rgb & 255) * percent / 100;
            return (r << 16) | (g << 8) | b;
        }

        private static int absolute(int value) {
            return value < 0 ? -value : value;
        }

        private static final class Texture {
            final int width;
            final int height;
            final int[] pixels;
            final int[] xFromU = new int[256];
            final int[] rowFromV = new int[256];

            private Texture(Image image) {
                width = image.getWidth();
                height = image.getHeight();
                pixels = new int[width * height];
                image.getRGB(pixels, 0, width, 0, 0, width, height);
                int i;
                for (i = 0; i < pixels.length; ++i) pixels[i] &= 0xFFFFFF;
                for (i = 0; i < 256; ++i) {
                    xFromU[i] = i * (width - 1) / 255;
                    rowFromV[i] = (255 - i) * (height - 1) / 255 * width;
                }
            }

            static Texture load(String name) {
                try { return new Texture(Image.createImage(name)); }
                catch (Throwable ignored) { return null; }
            }

        }

        private static void sleep(long millis) {
            try { Thread.sleep(millis); } catch (InterruptedException ignored) { }
        }
    }
}
