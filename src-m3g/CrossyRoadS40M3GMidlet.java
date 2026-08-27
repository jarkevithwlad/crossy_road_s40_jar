import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.game.GameCanvas;
import javax.microedition.m3g.Appearance;
import javax.microedition.m3g.Background;
import javax.microedition.m3g.Camera;
import javax.microedition.m3g.CompositingMode;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.Image2D;
import javax.microedition.m3g.PolygonMode;
import javax.microedition.m3g.Texture2D;
import javax.microedition.m3g.Transform;
import javax.microedition.m3g.TriangleStripArray;
import javax.microedition.m3g.VertexArray;
import javax.microedition.m3g.VertexBuffer;
import javax.microedition.midlet.MIDlet;

/** M3G immediate-mode performance port for Nokia 6303 classic. */
public final class CrossyRoadS40M3GMidlet extends MIDlet {
    private SceneCanvas canvas;
    protected void startApp() { if (canvas == null) canvas = new SceneCanvas(); Display.getDisplay(this).setCurrent(canvas); canvas.start(); }
    protected void pauseApp() { if (canvas != null) canvas.stop(); }
    protected void destroyApp(boolean unconditional) { if (canvas != null) canvas.stop(); }

    private static final class SceneCanvas extends GameCanvas implements Runnable {
        private static final int DEFAULT_W = 240, DEFAULT_H = 320, TARGET_MS = 16;
        private static final int ORIENTATION_PORTRAIT = 0;
        private static final int ORIENTATION_LEFT = 1;
        private static final int ORIENTATION_RIGHT = 2;
        private static final int ORIENTATION_NATIVE_LANDSCAPE = 3;
        private static final int ORIENTATION_COUNT = 4;
        private static final int ORIENTATION_NOTICE_MS = 1400;
        private static final int RENDER_SCALE_STEP = 10, RENDER_SCALE_MIN = 20;
        private static final int LOGIC_MS = 100, HUD_MS = 250;
        private static final int MOVE_MS = 200, CELL_X = 256, CELL_Z = 1;
        private static final int PLAYER_MAX_X = CELL_X * 4;
        private static final int LOG_SPEED = 144, LOG_SUPPORT_HALF_WIDTH = 320;
        private static final int HERO_GROUND_Y = 90, HERO_SUNK_Y = -80, HERO_JUMP_HEIGHT = 55;
        private static final int FIRST_LINE_Z = -7, LAST_LINE_Z = 12;
        private static final int GENERATED_FIRST_Z = -7, GENERATED_LAST_Z = -5;
        private static final int ROUTE_ARRAY_OFFSET = 2048, ROUTE_ARRAY_SIZE = 4096;
        private static final int ROUTE_AHEAD_LINES = 9;
        private static final float VIEW_HALF_X = 1100.0f, VIEW_HALF_Y = 1467.0f;
        private static final int HUD_W = 120, HUD_H = 64, FPS_W = 96, FPS_H = 32;
        private static final int GAME_OVER_SRC_W = 120, GAME_OVER_SRC_H = 24;
        private static final int GAME_OVER_W = 240, GAME_OVER_H = 48;
        private static final int NOTICE_SRC_W = 160, NOTICE_SRC_H = 24;
        private static final int TRANS_ROT90 = 5, TRANS_ROT270 = 6;
        private static final int CAMERA_COS = 985, CAMERA_SIN = 174;
        private static final int CAMERA_FOLLOW_PER_CELL = 297;
        private static final int CAMERA_LEAD_CELLS = 2;
        private static final float CAMERA_NORMAL_PARALLEL = 2930.0f;
        private static final float X_SCREEN_OFFSET = -457.0f;
        private static final float Z_BASE = -20000.0f;
        private final Graphics3D g3d = Graphics3D.getInstance();
        private final Camera camera = new Camera();
        private final Transform cameraTransform = new Transform();
        private final Transform identity = new Transform();
        private final Transform objectTransform = new Transform();
        private final Background background = new Background();
        private Image rotatedFrame;
        private Graphics rotatedGraphics;
        private int rotatedFrameW, rotatedFrameH;
        private Image lowResolutionFrame;
        private Graphics lowResolutionGraphics;
        private int lowResolutionW, lowResolutionH;
        private int[] scaledFramePixels;
        private int[] scaleSourceX, scaleSourceRow;
        private int canvasW = DEFAULT_W, canvasH = DEFAULT_H;
        private volatile int orientationMode = ORIENTATION_PORTRAIT;
        private volatile int renderScalePercent = 100;
        private int preparedOrientation = -1;
        private long noticeChangedAt;
        private final Image hudImage;
        private final Graphics hudGraphics;
        private final Font overlayFont;
        private Image fpsImage;
        private Graphics fpsGraphics;
        private int[] fpsPixels;
        private Image progressImage;
        private Graphics progressGraphics;
        private int[] progressPixels;
        private final Image gameOverImage;
        private final Graphics gameOverGraphics;
        private int[] gameOverPixels;
        private int[] noticePixels;
        private final Appearance[] appearances = new Appearance[18];
        private final M3GMesh grassBatch, roadBatch, riverBatch, railBatch;
        private final M3GMesh grassRow, roadRow, riverRow, railRow, treeRow, boulderRow, lilyRow;
        private final M3GMesh treeBatch, boulderBatch, lilyBatch;
        private final M3GMesh carLeft, carRight, log;
        private final M3GMesh heroUp, heroDown, heroLeft, heroRight;
        private final M3GMesh crushedHeroUp, crushedHeroDown, crushedHeroLeft, crushedHeroRight;
        private final M3GMesh signal1, signal2, signalOff, trainFrontLeft, trainMiddleLeft, trainBackLeft;
        private final M3GMesh trainFrontRight, trainMiddleRight, trainBackRight;
        private final int[] carX = {-1200, 0, 1200, 300};
        private final int[] carDirection = {1, 1, -1, -1};
        private final int[] logX = {-3000, 0};
        private final int[] generatedTemplates = new int[3];
        private final int[] routeTypes = new int[ROUTE_ARRAY_SIZE];
        private final int[] routeLilyX1 = new int[ROUTE_ARRAY_SIZE];
        private final int[] routeLilyX2 = new int[ROUTE_ARRAY_SIZE];
        private final int[] routeBoulderX = new int[ROUTE_ARRAY_SIZE];
        private int routeMinZ = GENERATED_FIRST_Z;
        private int routeRandomState = 0x4D3A;
        private final int[] lineBounds;
        private final int[] lineRxNumerator = new int[LAST_LINE_Z - FIRST_LINE_Z + 1];
        private final int[] lineRzNumerator = new int[LAST_LINE_Z - FIRST_LINE_Z + 1];
        private final boolean[] lineVisible = new boolean[LAST_LINE_Z - FIRST_LINE_Z + 1];
        private volatile boolean running;
        private Thread worker;
        private long fpsClock, frameClock, logicTime, logicAccumulator, lastHudUpdate;
        private int fps, frames, renderMs, triangles;
        private int trainX = -18000, trainDirection = 1;
        private int playerX, playerZ = 7;
        private int playerFromX, playerFromZ = 7, playerToX, playerToZ = 7;
        private int playerFromY = HERO_GROUND_Y, playerToY = HERO_GROUND_Y;
        private int playerDirection = 2, playerMoveElapsed;
        private int furthestPlayerZ = 7;
        private int cameraFollowZ;
        private int ridingLog = -1;
        private boolean playerMoving, playerSunk, gameOver;

        SceneCanvas() {
            super(false);
            setFullScreenMode(true);
            if (getWidth() > 0 && getHeight() > 0) {
                canvasW = getWidth();
                canvasH = getHeight();
            }
            background.setColor(0x8ED5E8);
            background.setColorClearEnable(true);
            background.setDepthClearEnable(true);
            camera.setParallel(CAMERA_NORMAL_PARALLEL, 0.75f, 1.0f, 50000.0f);
            setCameraTransform();
            identity.setIdentity();
            hudImage = Image.createImage(HUD_W, HUD_H);
            hudGraphics = hudImage.getGraphics();
            overlayFont = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_LARGE);
            updateFpsImage();
            updateProgressImage();
            gameOverImage = Image.createImage(GAME_OVER_SRC_W, GAME_OVER_SRC_H);
            gameOverGraphics = gameOverImage.getGraphics();
            gameOverGraphics.setColor(0x000000);
            gameOverGraphics.setFont(overlayFont);
            gameOverGraphics.drawString("Game Over", GAME_OVER_SRC_W / 2, 2, Graphics.TOP | Graphics.HCENTER);
            int[] gameOverSource = new int[GAME_OVER_SRC_W * GAME_OVER_SRC_H];
            gameOverImage.getRGB(gameOverSource, 0, GAME_OVER_SRC_W, 0, 0, GAME_OVER_SRC_W, GAME_OVER_SRC_H);
            gameOverPixels = new int[GAME_OVER_W * GAME_OVER_H];
            int gameOverX, gameOverY, scaledX, scaledY;
            for (gameOverY = 0; gameOverY < GAME_OVER_SRC_H; gameOverY++) for (gameOverX = 0; gameOverX < GAME_OVER_SRC_W; gameOverX++) {
                int pixel = gameOverSource[gameOverY * GAME_OVER_SRC_W + gameOverX];
                if ((pixel & 0x00ffffff) == 0x00ffffff) continue;
                for (scaledY = 0; scaledY < 2; scaledY++) for (scaledX = 0; scaledX < 2; scaledX++) {
                int rotatedX = GAME_OVER_H - 1 - gameOverY * 2 - scaledY;
                int rotatedY = gameOverX * 2 + scaledX;
                gameOverPixels[rotatedY * GAME_OVER_H + rotatedX] = 0xffffffff;
                }
            }
            loadAppearances();
            lineBounds = M3GMesh.projectedBounds(AssetMeshes.ROW, 0);
            initializeLineCache();
            grassRow = new M3GMesh(AssetMeshes.ROW, 0, appearances[0]);
            roadRow = new M3GMesh(AssetMeshes.ROAD, 0, appearances[1]);
            riverRow = new M3GMesh(AssetMeshes.RIVER, 0, appearances[2]);
            railRow = new M3GMesh(AssetMeshes.RAIL, 0, appearances[3]);
            treeRow = new M3GMesh(AssetMeshes.TREE, 0, appearances[7]);
            boulderRow = new M3GMesh(AssetMeshes.BOULDER, 0, appearances[8]);
            lilyRow = new M3GMesh(AssetMeshes.LILY, 0, appearances[6]);
            generateForwardRows();
            initializeRouteTypes();
            grassBatch = new M3GMesh(AssetMeshes.ROW, 0, appearances[0], visiblePlacements(new int[] {
                    0, 0, -4, 0, 0, 0, -2, 0, 0, 0, 1, 0, 0, 0, 3, 0,
                    0, 0, 4, 0, 0, 0, 6, 0, 0, 0, 7, 0, 0, 0, 8, 0, 0, 0, 9, 0,
                    0, 0, 10, 0, 0, 0, 11, 0, 0, 0, 12, 0 }));
            roadBatch = new M3GMesh(AssetMeshes.ROAD, 0, appearances[1], visiblePlacements(new int[] {
                    0, 0, -3, 0, 0, 0, 2, 0 }));
            riverBatch = new M3GMesh(AssetMeshes.RIVER, 0, appearances[2], visiblePlacements(new int[] {
                    0, 0, -1, 0, 0, 0, 5, 0 }));
            railBatch = new M3GMesh(AssetMeshes.RAIL, 0, appearances[3], visiblePlacements(new int[] {
                    0, 0, 0, 0 }));
            treeBatch = new M3GMesh(AssetMeshes.TREE, 0, appearances[7], visiblePlacements(new int[] {
                    -243, 0, -4, 0, -243, 0, -2, 0, -243, 0, 4, 0, -243, 0, 6, 0 }));
            boulderBatch = new M3GMesh(AssetMeshes.BOULDER, 0, appearances[8], visiblePlacements(new int[] {
                    230, 0, -4, 0, 230, 0, -2, 0, 230, 0, 4, 0, 230, 0, 6, 0 }));
            lilyBatch = new M3GMesh(AssetMeshes.LILY, 0, appearances[6], visiblePlacements(new int[] {
                    -256, 41, 5, 0, 256, 41, 5, 0 }));
            carLeft = new M3GMesh(AssetMeshes.CAR, 1, appearances[4]);
            carRight = new M3GMesh(AssetMeshes.CAR, 3, appearances[4]);
            // The log mesh is already authored horizontally. Turn 5 mirrors
            // its winding and makes it disappear with back-face culling.
            log = new M3GMesh(AssetMeshes.LOG, 0, appearances[5]);
            heroUp = new M3GMesh(AssetMeshes.HERO, 0, appearances[9]);
            heroDown = new M3GMesh(AssetMeshes.HERO, 2, appearances[9]);
            heroLeft = new M3GMesh(AssetMeshes.HERO, 1, appearances[9]);
            heroRight = new M3GMesh(AssetMeshes.HERO, 3, appearances[9]);
            crushedHeroUp = new M3GMesh(AssetMeshes.HERO, 0, appearances[9], 20);
            crushedHeroDown = new M3GMesh(AssetMeshes.HERO, 2, appearances[9], 20);
            crushedHeroLeft = new M3GMesh(AssetMeshes.HERO, 1, appearances[9], 20);
            crushedHeroRight = new M3GMesh(AssetMeshes.HERO, 3, appearances[9], 20);
            signal1 = new M3GMesh(AssetMeshes.TRAIN_LIGHT_ON1, 0, appearances[15]);
            signal2 = new M3GMesh(AssetMeshes.TRAIN_LIGHT_ON2, 0, appearances[16]);
            signalOff = new M3GMesh(AssetMeshes.TRAIN_LIGHT_ON1, 0, appearances[17]);
            trainFrontLeft = new M3GMesh(TrainFrontMesh.DATA, 0, appearances[11], true);
            trainMiddleLeft = new M3GMesh(TrainMesh.DATA, 0, appearances[10], true);
            trainBackLeft = new M3GMesh(TrainBackMesh.DATA, 0, appearances[12], true);
            trainFrontRight = new M3GMesh(TrainFrontMesh.DATA, 2, appearances[11], true);
            trainMiddleRight = new M3GMesh(TrainMesh.DATA, 2, appearances[10], true);
            trainBackRight = new M3GMesh(TrainBackMesh.DATA, 2, appearances[12], true);
            triangles = grassBatch.triangles + roadBatch.triangles + riverBatch.triangles + railBatch.triangles
                    + treeBatch.triangles + boulderBatch.triangles + lilyBatch.triangles
                    + carLeft.triangles * 4 + log.triangles * 2 + heroUp.triangles;
            updateHudImage();
        }

        synchronized void start() { if (running) return; running = true; worker = new Thread(this); worker.start(); }
        synchronized void stop() { running = false; worker = null; }
        public void run() {
            Thread current = Thread.currentThread(); frameClock = System.currentTimeMillis(); fpsClock = frameClock;
            while (running && worker == current) {
                long started = System.currentTimeMillis(); long elapsed = started - frameClock; frameClock = started;
                if (elapsed < 0) elapsed = 0; if (elapsed > 250) elapsed = 250;
                logicAccumulator += elapsed;
                while (logicAccumulator >= LOGIC_MS) { update(LOGIC_MS); logicAccumulator -= LOGIC_MS; }
                render(started, logicTime + logicAccumulator, (int)logicAccumulator); frames++;
                long finished = System.currentTimeMillis();
                if (finished - fpsClock >= 1000) { fps = frames; frames = 0; fpsClock = finished; }
                long delay = TARGET_MS - (finished - started); if (delay > 0) sleep(delay);
            }
        }

        private void update(int delta) {
            logicTime += delta;
            updateCamera(delta);
            if (playerSunk) {
                // Positive world X projects to the right on the actual rotated display.
                playerX += (int)(LOG_SPEED * (long)delta / 1000L);
            } else if (ridingLog >= 0 && !playerMoving && !gameOver) {
                playerX += (int)(LOG_SPEED * (long)delta / 1000L);
            }
            if (playerMoving) {
                playerMoveElapsed += delta;
                if (playerMoveElapsed >= MOVE_MS) {
                    playerMoveElapsed = MOVE_MS;
                    playerX = playerToX;
                    playerZ = playerToZ;
                    playerMoving = false;
                    if (playerSunk) gameOver = true;
                }
            }
            int i; for (i = 0; i < carX.length; i++) { carX[i] += (int)(carDirection[i] * 690L * delta / 1000L); carX[i] = wrap(carX[i], -1500, 1500); }
            int logPhase = (int)((logicTime * (long)LOG_SPEED / 1000L) % 6001L);
            logX[0] = -3000 + logPhase; logX[1] = -3000 + ((logPhase + 3000) % 6001);
            trainX += (int)(trainDirection * 3500L * delta / 1000L);
            if (trainX >= 18000) { trainX = 18000; trainDirection = -1; }
            else if (trainX <= -18000) { trainX = -18000; trainDirection = 1; }
            if (!gameOver && (trainHitsPlayer() || carHitsPlayer())) {
                playerMoving = false;
                playerToY = HERO_GROUND_Y;
                playerSunk = false;
                gameOver = true;
            }
        }

        private void render(long frameStarted, long animationTime, int interpolationMs) {
            Graphics screenGraphics = getGraphics();
            int mode = orientationMode;
            prepareOrientation(mode);
            Graphics frameGraphics = isRotated(mode) ? rotatedGraphics : screenGraphics;
            int frameWidth = isRotated(mode) ? rotatedFrameW : canvasW;
            int frameHeight = isRotated(mode) ? rotatedFrameH : canvasH;
            int scalePercent = renderScalePercent;
            prepareRenderResolution(frameWidth, frameHeight, scalePercent);
            boolean reducedResolution = scalePercent < 100;
            Graphics sceneGraphics = reducedResolution ? lowResolutionGraphics : frameGraphics;
            int sceneWidth = reducedResolution ? lowResolutionW : frameWidth;
            int sceneHeight = reducedResolution ? lowResolutionH : frameHeight;
            boolean bound = false;
            try {
                g3d.bindTarget(sceneGraphics, true, 0); bound = true;
                g3d.setViewport(0, 0, sceneWidth, sceneHeight);
                g3d.clear(background); g3d.setCamera(camera, cameraTransform);
                drawScene(animationTime, interpolationMs);
            } finally { if (bound) g3d.releaseTarget(); }
            if (reducedResolution) upscaleRender(frameGraphics, frameWidth, frameHeight);
            renderMs = (int)(System.currentTimeMillis() - frameStarted);
            drawHud(frameGraphics, frameStarted, frameWidth, frameHeight);
            if (isRotated(mode)) {
                int transform = mode == ORIENTATION_LEFT ? TRANS_ROT270 : TRANS_ROT90;
                screenGraphics.drawRegion(rotatedFrame, 0, 0, rotatedFrameW, rotatedFrameH,
                        transform, 0, 0, Graphics.TOP | Graphics.LEFT);
            }
            flushGraphics();
        }

        private boolean isRotated(int mode) {
            return mode == ORIENTATION_LEFT || mode == ORIENTATION_RIGHT;
        }

        private void prepareOrientation(int mode) {
            int frameWidth = isRotated(mode) ? canvasH : canvasW;
            int frameHeight = isRotated(mode) ? canvasW : canvasH;
            if (isRotated(mode) && (rotatedFrame == null
                    || rotatedFrameW != frameWidth || rotatedFrameH != frameHeight)) {
                rotatedFrame = Image.createImage(frameWidth, frameHeight);
                rotatedGraphics = rotatedFrame.getGraphics();
                rotatedFrameW = frameWidth;
                rotatedFrameH = frameHeight;
            }
            if (preparedOrientation != mode) {
                camera.setParallel(CAMERA_NORMAL_PARALLEL,
                        (float)frameWidth / (float)frameHeight, 1.0f, 50000.0f);
                preparedOrientation = mode;
            }
        }

        private void prepareRenderResolution(int frameWidth, int frameHeight, int scalePercent) {
            if (scalePercent >= 100) {
                if (lowResolutionFrame != null) {
                    lowResolutionFrame = null;
                    lowResolutionGraphics = null;
                    scaledFramePixels = null;
                    scaleSourceX = null;
                    scaleSourceRow = null;
                    System.gc();
                }
                return;
            }
            int width = frameWidth * scalePercent / 100;
            int height = frameHeight * scalePercent / 100;
            if (width < 1) width = 1;
            if (height < 1) height = 1;
            if (lowResolutionFrame != null && lowResolutionW == width
                    && lowResolutionH == height && scaledFramePixels.length == frameWidth * frameHeight)
                return;
            lowResolutionFrame = null;
            lowResolutionGraphics = null;
            scaledFramePixels = null;
            scaleSourceX = null;
            scaleSourceRow = null;
            System.gc();
            lowResolutionFrame = Image.createImage(width, height);
            lowResolutionGraphics = lowResolutionFrame.getGraphics();
            lowResolutionW = width;
            lowResolutionH = height;
            scaledFramePixels = new int[frameWidth * frameHeight];
            scaleSourceX = new int[frameWidth];
            scaleSourceRow = new int[frameHeight];
            int i;
            for (i = 0; i < frameWidth; i++) scaleSourceX[i] = i * width / frameWidth;
            for (i = 0; i < frameHeight; i++) scaleSourceRow[i] = i * height / frameHeight * width;
        }

        private void upscaleRender(Graphics target, int frameWidth, int frameHeight) {
            lowResolutionFrame.getRGB(scaledFramePixels, 0, lowResolutionW,
                    0, 0, lowResolutionW, lowResolutionH);
            int x, y;
            // Expand bottom-to-top and right-to-left so the compact source at
            // the beginning of this same array is never overwritten too early.
            for (y = frameHeight - 1; y >= 0; y--) {
                int sourceRow = scaleSourceRow[y];
                int destinationRow = y * frameWidth;
                for (x = frameWidth - 1; x >= 0; x--)
                    scaledFramePixels[destinationRow + x]
                            = scaledFramePixels[sourceRow + scaleSourceX[x]];
            }
            target.drawRGB(scaledFramePixels, 0, frameWidth,
                    0, 0, frameWidth, frameHeight, false);
        }

        private void drawScene(long animationTime, int interpolationMs) {
            drawTerrainRows(animationTime, interpolationMs);
            if (playerMoving) {
                int elapsed = playerMoveElapsed + interpolationMs;
                if (elapsed > MOVE_MS) elapsed = MOVE_MS;
                int progress = elapsed * 1000 / MOVE_MS;
                int x = playerFromX + (playerToX - playerFromX) * progress / 1000;
                int playerZQ = playerFromZ * CELL_Z * 256
                        + (playerToZ - playerFromZ) * CELL_Z * 256 * progress / 1000;
                int playerZPos = playerZQ / 256;
                int playerZOffset = playerZQ - playerZPos * 256;
                int landingY = playerFromY + (playerToY - playerFromY) * progress / 1000;
                int jump = HERO_JUMP_HEIGHT * progress * (1000 - progress) / 250000;
                draw(heroMesh(playerDirection), x, landingY + jump, playerZPos, playerZOffset);
            } else {
                draw(heroMesh(playerDirection), playerX, playerToY, playerZ, 0);
            }
        }

        private static int interpolated(int position, int speed, int elapsed, int min, int max) {
            return wrap(position + (int)(speed * (long)elapsed / 1000L), min, max);
        }

        private M3GMesh heroMesh(int direction) {
            if (gameOver) {
                if (direction == 0) return crushedHeroUp;
                if (direction == 1) return crushedHeroRight;
                if (direction == 3) return crushedHeroLeft;
                return crushedHeroDown;
            }
            if (direction == 0) return heroUp;
            if (direction == 1) return heroRight;
            if (direction == 3) return heroLeft;
            return heroDown;
        }

        private void movePlayer(int direction) {
            if (playerMoving || playerSunk || gameOver) return;
            playerFromX = playerX;
            playerFromZ = playerZ;
            playerFromY = playerToY;
            playerToX = playerX;
            playerToZ = playerZ;
            playerToY = HERO_GROUND_Y;
            playerDirection = direction;
            if (direction == 0) playerToZ += CELL_Z;
            else if (direction == 1) playerToX += CELL_X;
            else if (direction == 2) playerToZ -= CELL_Z;
            else playerToX -= CELL_X;
            boolean leavingWater = isWaterRow(playerZ) && !isWaterRow(playerToZ);
            if (leavingWater) playerToX = nearestGrid(playerX);
            if (playerToX < -PLAYER_MAX_X || playerToX > PLAYER_MAX_X) return;
            if (direction == 0 && playerToZ > maximumBackwardZ()) return;
            extendRouteTo(playerToZ - ROUTE_AHEAD_LINES);
            if (isVegetationBlocked(playerToX, playerToZ)) return;
            if (isCarBlocked(playerToX, playerToZ)) return;
            if (isTrainBlocked(playerToX, playerToZ)) return;
            if (isWaterRow(playerToZ)) {
                int logIndex = logAt(playerToX, playerToZ);
                if (isLilyPad(playerToX, playerToZ)) {
                    ridingLog = -1;
                } else if (logIndex >= 0) {
                    ridingLog = logIndex;
                } else {
                    ridingLog = -1;
                    playerToY = HERO_SUNK_Y;
                    playerSunk = true;
                }
            } else {
                ridingLog = -1;
            }
            if (playerToZ < furthestPlayerZ) furthestPlayerZ = playerToZ;
            playerMoveElapsed = 0;
            playerMoving = true;
        }

        private int maximumBackwardZ() {
            int rollback = furthestPlayerZ + 2;
            return rollback < 7 ? rollback : 7;
        }

        private void updateCamera(int delta) {
            // Forward is decreasing Z. The camera follows only the furthest
            // forward cell and never moves back when the player retreats.
            int targetZ = -(7 - furthestPlayerZ - CAMERA_LEAD_CELLS) * CAMERA_FOLLOW_PER_CELL;
            int difference = targetZ - cameraFollowZ;
            if (difference != 0) {
                int step = difference * delta / 300;
                if (step == 0) step = difference > 0 ? 1 : -1;
                if (difference > 0 && step > difference) step = difference;
                if (difference < 0 && step < difference) step = difference;
                cameraFollowZ += step;
                setCameraTransform();
            }
        }

        private void setCameraTransform() {
            cameraTransform.setIdentity();
            cameraTransform.postRotate(180.0f, 0.0f, 0.0f, 1.0f);
            // One game Z axis is projected into all three camera-space
            // coordinates. Moving only camera Z has no visible effect with
            // the orthographic projection.
            int cameraX = -cameraFollowZ * 142 / CAMERA_FOLLOW_PER_CELL;
            int cameraY = cameraFollowZ * 208 / CAMERA_FOLLOW_PER_CELL;
            // Follow the route in depth as well. Otherwise every forward row
            // moves the scene farther from the camera until individual
            // triangles, and eventually whole models, cross the far plane.
            cameraTransform.postTranslate(cameraX, cameraY, cameraFollowZ);
        }

        private boolean isVegetationBlocked(int x, int z) {
            if (!hasGeneratedDecoration(z)) return false;
            // Only the tall tree blocks the player's grid cell; the low boulder
            // remains passable, as in the intended gameplay.
            return Math.abs(x + 243) < 150;
        }

        private boolean isWaterRow(int z) {
            int type = routeType(z);
            return type == 2 || type == 4;
        }

        private boolean isLilyPad(int x, int z) {
            if (z != 5 && !(z <= GENERATED_LAST_Z && routeType(z) == 2)) return false;
            int index = z + ROUTE_ARRAY_OFFSET;
            return Math.abs(x - routeLilyX1[index]) < 128
                    || Math.abs(x - routeLilyX2[index]) < 128;
        }

        private int generatedTemplate(int z) {
            if (z < GENERATED_FIRST_Z || z > GENERATED_LAST_Z) return -1;
            return generatedTemplates[z - GENERATED_FIRST_Z];
        }

        private void initializeRouteTypes() {
            int z;
            for (z = GENERATED_FIRST_Z; z <= 12; z++) {
                int index = z + ROUTE_ARRAY_OFFSET;
                routeTypes[index] = 0;
                routeBoulderX[index] = 230;
            }
            routeTypes[-3 + ROUTE_ARRAY_OFFSET] = 1;
            routeTypes[2 + ROUTE_ARRAY_OFFSET] = 1;
            routeTypes[0 + ROUTE_ARRAY_OFFSET] = 3;
            routeTypes[-1 + ROUTE_ARRAY_OFFSET] = 4;
            routeTypes[5 + ROUTE_ARRAY_OFFSET] = 2;
            routeTypes[-7 + ROUTE_ARRAY_OFFSET] = 0;
            routeTypes[-6 + ROUTE_ARRAY_OFFSET] = 3;
            routeTypes[-5 + ROUTE_ARRAY_OFFSET] = 4;
            routeLilyX1[5 + ROUTE_ARRAY_OFFSET] = -256;
            routeLilyX2[5 + ROUTE_ARRAY_OFFSET] = 256;
            configureRouteRow(-7, 0);
            configureRouteRow(-6, 3);
            configureRouteRow(-5, 4);
            configureRouteRow(-4, 0);
            configureRouteRow(-2, 0);
            configureRouteRow(4, 0);
            configureRouteRow(6, 0);
        }

        private void generateForwardRows() {
            // The first reusable gameplay chunk is the last three normal
            // rows: grass, road, grass. Shuffle their positions as a chunk.
            generatedTemplates[0] = 4;
            generatedTemplates[1] = 5;
            generatedTemplates[2] = 4;
            int state = 0x4D3A;
            state = state * 1103515245 + 12345;
            if ((state & 1) != 0) {
                int swap = generatedTemplates[0]; generatedTemplates[0] = generatedTemplates[1]; generatedTemplates[1] = swap;
            }
            state = state * 1103515245 + 12345;
            if ((state & 1) != 0) {
                int swap = generatedTemplates[1]; generatedTemplates[1] = generatedTemplates[2]; generatedTemplates[2] = swap;
            }
        }

        private void drawTerrainRows(long animationTime, int interpolationMs) {
            int z, top = playerZ + 6;
            if (top > 12) top = 12;
            for (z = routeMinZ; z <= top; z++) {
                int type = routeType(z);
                if (type == 1) {
                    draw(roadRow, 0, 0, z, 0);
                    int slot;
                    for (slot = 0; slot < 2; slot++) {
                        int direction = carDirectionForRow(z, slot);
                        M3GMesh car = direction > 0 ? carRight : carLeft;
                        draw(car, carPositionForRow(z, slot, animationTime), 97, z, 0);
                    }
                }
                else if (type == 2) {
                    draw(riverRow, 0, 0, z, 0);
                    if (z == 5 || z <= GENERATED_LAST_Z) {
                        int index = z + ROUTE_ARRAY_OFFSET;
                        draw(lilyRow, routeLilyX1[index], 41, z, 0);
                        draw(lilyRow, routeLilyX2[index], 41, z, 0);
                    }
                }
                else if (type == 3) {
                    draw(railRow, 0, 0, z, 0);
                    drawTrainRow(z, animationTime, interpolationMs);
                }
                else if (type == 4) {
                    draw(riverRow, 0, 0, z, 0);
                    draw(log, interpolated(logX[0], LOG_SPEED, interpolationMs, -3000, 3000), -64, z, 0);
                    draw(log, interpolated(logX[1], LOG_SPEED, interpolationMs, -3000, 3000), -64, z, 0);
                }
                else {
                    draw(grassRow, 0, 0, z, 0);
                    if (hasGeneratedDecoration(z)) {
                        draw(treeRow, -243, 0, z, 0);
                        draw(boulderRow, routeBoulderX[z + ROUTE_ARRAY_OFFSET], 0, z, 0);
                    }
                }
            }
        }

        private void drawTrainRow(int z, long animationTime, int interpolationMs) {
            int visibleTrainX = trainPositionForRow(z, animationTime);
            int direction = trainDirectionForRow(z, animationTime);
            if (isTrainApproachingCenter(visibleTrainX, direction)) {
                int blink = (int)((animationTime / 250L) & 1L);
                draw(blink == 0 ? signal1 : signal2, -397, 0, z, 192);
            } else {
                draw(signalOff, -397, 0, z, 192);
            }
            if (visibleTrainX > -10000 && visibleTrainX < 10000) {
                boolean right = direction > 0;
                int front = right ? visibleTrainX + 2500 : visibleTrainX - 2500;
                int middle = right ? visibleTrainX + 1250 : visibleTrainX - 1250;
                draw(right ? trainFrontRight : trainFrontLeft, front, 26, z, 0);
                draw(right ? trainMiddleRight : trainMiddleLeft, middle, 26, z, 0);
                draw(right ? trainBackRight : trainBackLeft, visibleTrainX, 26, z, 0);
            }
        }

        private int rowHash(int z, int salt) {
            return (z * 1103515245 + salt * 12345 + 0x4D3A) >>> 1;
        }

        private int speedPercentForRow(int z, int salt) {
            return 90 + rowHash(z, salt) % 21;
        }

        private int carDirectionForRow(int z, int slot) {
            return (rowHash(z, 20) & 1) == 0 ? 1 : -1;
        }

        private int carPositionForRow(int z, int slot, long time) {
            int direction = carDirectionForRow(z, slot);
            int speed = 690 * speedPercentForRow(z, 30) / 100;
            int phase = (rowHash(z, 40) % 3001 + slot * 1500) % 3001;
            int travel = (int)((time * speed / 1000L + phase) % 3001L);
            return direction > 0 ? -1500 + travel : 1500 - travel;
        }

        private int trainSpeedForRow(int z) {
            return 3500 * speedPercentForRow(z, 50) / 100;
        }

        private long trainPhaseForRow(int z, long time) {
            long phase = rowHash(z, 60) % 72001;
            return (time * trainSpeedForRow(z) / 1000L + phase) % 72001L;
        }

        private int trainPositionForRow(int z, long time) {
            long phase = trainPhaseForRow(z, time);
            return phase <= 36000L ? -18000 + (int)phase : 18000 - (int)(phase - 36000L);
        }

        private int trainDirectionForRow(int z, long time) {
            return trainPhaseForRow(z, time) <= 36000L ? 1 : -1;
        }

        private int routeType(int z) {
            if (z < routeMinZ || z > 12) return 0;
            return routeTypes[z + ROUTE_ARRAY_OFFSET];
        }

        private boolean hasGeneratedDecoration(int z) {
            return z <= GENERATED_LAST_Z || z == -4 || z == -2 || z == 4 || z == 6;
        }

        private void extendRouteTo(int z) {
            while (z < routeMinZ) {
                // Generate from the old front toward the new front so every
                // neighboring pair, including the chunk boundary, differs.
                int third = nextRouteType(routeType(routeMinZ));
                int second = nextRouteType(third);
                int first = nextRouteType(second);
                routeMinZ -= 3;
                routeTypes[routeMinZ + ROUTE_ARRAY_OFFSET] = first;
                routeTypes[routeMinZ + 1 + ROUTE_ARRAY_OFFSET] = second;
                routeTypes[routeMinZ + 2 + ROUTE_ARRAY_OFFSET] = third;
                configureRouteRow(routeMinZ, first);
                configureRouteRow(routeMinZ + 1, second);
                configureRouteRow(routeMinZ + 2, third);
            }
        }

        private int nextRouteType(int excluded) {
            routeRandomState = routeRandomState * 1103515245 + 12345;
            int type = (routeRandomState >>> 16) % 5;
            if (type == excluded) type = (type + 1) % 5;
            return type;
        }

        private int randomGridX() {
            routeRandomState = routeRandomState * 1103515245 + 12345;
            return (((routeRandomState >>> 16) % 5) - 2) * CELL_X;
        }

        private void configureRouteRow(int z, int type) {
            int index = z + ROUTE_ARRAY_OFFSET;
            if (type == 2) {
                int first = randomGridX();
                int second = randomGridX();
                if (second == first) {
                    second += CELL_X;
                    if (second > CELL_X * 2) second = -CELL_X * 2;
                }
                routeLilyX1[index] = first;
                routeLilyX2[index] = second;
            }
            if (type == 0) routeBoulderX[index] = randomGridX();
        }

        private int logAt(int x, int z) {
            if (routeType(z) != 4) return -1;
            int arrivalOffset = LOG_SPEED * MOVE_MS / 1000;
            int i;
            for (i = 0; i < logX.length; i++) {
                int logPosition = wrap(logX[i] + arrivalOffset, -3000, 3000);
                if (Math.abs(x - logPosition) <= LOG_SUPPORT_HALF_WIDTH) return i;
            }
            return -1;
        }

        private int nearestGrid(int x) {
            int grid = x >= 0 ? ((x + CELL_X / 2) / CELL_X) * CELL_X
                    : -(((-x + CELL_X / 2) / CELL_X) * CELL_X);
            if (grid > PLAYER_MAX_X) return PLAYER_MAX_X;
            if (grid < -PLAYER_MAX_X) return -PLAYER_MAX_X;
            return grid;
        }

        private boolean trainHitsPlayer() {
            if (playerSunk) return false;
            int x = playerMoving ? playerToX : playerX;
            int firstRow = playerMoving ? playerFromZ : playerZ;
            int secondRow = playerMoving ? playerToZ : playerZ;
            if (trainHitsAtRow(x, firstRow)) return true;
            return secondRow != firstRow && trainHitsAtRow(x, secondRow);
        }

        private boolean trainHitsAtRow(int x, int rowZ) {
            if (routeType(rowZ) != 3) return false;
            int position = trainPositionForRow(rowZ, logicTime);
            int direction = trainDirectionForRow(rowZ, logicTime);
            int frontX = position + (direction > 0 ? 2500 : -2500);
            return Math.abs(x - frontX) < 500;
        }

        private boolean carHitsPlayer() {
            int x = playerX;
            int zQ = playerZ * 256;
            if (playerMoving) {
                int progress = playerMoveElapsed * 1000 / MOVE_MS;
                if (progress > 1000) progress = 1000;
                x = playerFromX + (playerToX - playerFromX) * progress / 1000;
                zQ = playerFromZ * 256 + (playerToZ - playerFromZ) * 256 * progress / 1000;
            }
            int firstRow = playerMoving ? playerFromZ : playerZ;
            int secondRow = playerMoving ? playerToZ : playerZ;
            if (carHitsAtRow(x, zQ, firstRow)) return true;
            return secondRow != firstRow && carHitsAtRow(x, zQ, secondRow);
        }

        private boolean carHitsAtRow(int x, int zQ, int rowZ) {
            if (routeType(rowZ) != 1 || Math.abs(zQ - rowZ * 256) >= 180) return false;
            int slot;
            for (slot = 0; slot < 2; slot++)
                if (Math.abs(x - carPositionForRow(rowZ, slot, logicTime)) < 280) return true;
            return false;
        }

        private boolean isCarBlocked(int x, int z) {
            if (routeType(z) != 1) return false;
            int slot;
            for (slot = 0; slot < 2; slot++)
                if (Math.abs(x - carPositionForRow(z, slot, logicTime)) < 280) return true;
            return false;
        }

        private boolean isTrainBlocked(int x, int z) {
            if (routeType(z) != 3) return false;
            return Math.abs(x - trainPositionForRow(z, logicTime)) < 2800;
        }

        private void resetGame() {
            playerX = 0; playerZ = 7;
            playerFromX = 0; playerFromZ = 7; playerToX = 0; playerToZ = 7;
            playerFromY = HERO_GROUND_Y; playerToY = HERO_GROUND_Y;
            playerDirection = 2; playerMoveElapsed = 0;
            furthestPlayerZ = 7;
            routeMinZ = GENERATED_FIRST_Z;
            routeRandomState = 0x4D3A;
            initializeRouteTypes();
            cameraFollowZ = 0;
            setCameraTransform();
            ridingLog = -1;
            playerMoving = false; playerSunk = false; gameOver = false;
            logicTime = 0; logicAccumulator = 0;
            carX[0] = -1200; carX[1] = 0; carX[2] = 1200; carX[3] = 300;
            logX[0] = -3000; logX[1] = 0;
            trainX = -18000; trainDirection = 1;
        }

        protected void keyPressed(int keyCode) {
            if (keyCode == 35) {
                orientationMode = (orientationMode + 1) % ORIENTATION_COUNT;
                preparedOrientation = -1;
                showNotice(orientationName(orientationMode));
                return;
            }
            if (keyCode == 42) {
                renderScalePercent -= RENDER_SCALE_STEP;
                if (renderScalePercent < RENDER_SCALE_MIN) renderScalePercent = 100;
                showNotice("Render " + renderScalePercent + "%");
                return;
            }
            if (gameOver) { resetGame(); return; }
            int action = getGameAction(keyCode);
            int physicalDirection = -1;
            if (action == UP || keyCode == KEY_NUM2) physicalDirection = 0;
            else if (action == RIGHT || keyCode == 54) physicalDirection = 1;
            else if (action == DOWN || keyCode == 56) physicalDirection = 2;
            else if (action == LEFT || keyCode == 52) physicalDirection = 3;
            if (physicalDirection >= 0) movePlayer(directionForOrientation(physicalDirection));
        }

        private int directionForOrientation(int physicalDirection) {
            if (orientationMode == ORIENTATION_LEFT) return (2 - physicalDirection) & 3;
            if (orientationMode == ORIENTATION_RIGHT) return -physicalDirection & 3;
            return (3 - physicalDirection) & 3;
        }

        protected void keyRepeated(int keyCode) {
            // A held key must not queue extra jumps during the current jump.
            if (gameOver) resetGame();
        }

        private int[] visiblePlacements(int[] source) {
            int count = 0, i;
            for (i = 0; i < source.length; i += 4) if (isLineVisible(source[i + 2])) count += 4;
            int[] result = new int[count]; int out = 0;
            for (i = 0; i < source.length; i += 4) if (isLineVisible(source[i + 2])) {
                result[out++] = source[i]; result[out++] = source[i + 1];
                result[out++] = source[i + 2]; result[out++] = source[i + 3];
            }
            return result;
        }

        private void initializeLineCache() {
            int z;
            for (z = FIRST_LINE_Z; z <= LAST_LINE_Z; z++) {
                int index = z - FIRST_LINE_Z, dz = z * 256;
                lineRxNumerator[index] = 397 * CAMERA_COS + dz * CAMERA_SIN;
                lineRzNumerator[index] = dz * CAMERA_COS - 397 * CAMERA_SIN;
                int rx = lineRxNumerator[index] / 1000, rz = lineRzNumerator[index] / 1000;
                float drawX = (rx + rz) * 0.48f + X_SCREEN_OFFSET;
                float drawY = rx - rz;
                lineVisible[index] = drawX + lineBounds[1] + 192 >= -VIEW_HALF_X
                        && drawX + lineBounds[0] - 192 <= VIEW_HALF_X
                        && drawY + lineBounds[3] + 192 >= -VIEW_HALF_Y
                        && drawY + lineBounds[2] - 192 <= VIEW_HALF_Y;
            }
        }

        private boolean isLineVisible(int z) {
            if (z >= FIRST_LINE_Z && z <= LAST_LINE_Z) return lineVisible[z - FIRST_LINE_Z];
            int dz = z * 256;
            int rx = dz * CAMERA_SIN / 1000, rz = dz * CAMERA_COS / 1000;
            float drawX = (rx + rz) * 0.48f + X_SCREEN_OFFSET;
            float drawY = rx - rz;
            return drawX + lineBounds[1] + 192 >= -VIEW_HALF_X
                    && drawX + lineBounds[0] - 192 <= VIEW_HALF_X
                    && drawY + lineBounds[3] + 192 >= -VIEW_HALF_Y
                    && drawY + lineBounds[2] - 192 <= VIEW_HALF_Y;
        }

        private void drawBatch(M3GMesh mesh) {
            if (mesh.triangles != 0) g3d.render(mesh.vertices, mesh.indices, mesh.appearance, identity);
        }

        private boolean isTrainApproachingCenter(int position, int direction) {
            int center = position + (direction > 0 ? 1250 : -1250);
            int distance = direction > 0 ? -397 - center : center + 397;
            return distance >= 0 && distance <= 10500;
        }

        private void draw(M3GMesh mesh, int ox, int oy, int oz, int ozOffset) {
            int rx, rz;
            if (oz >= FIRST_LINE_Z && oz <= LAST_LINE_Z) {
                int index = oz - FIRST_LINE_Z;
                rx = (lineRxNumerator[index] + ox * CAMERA_COS + ozOffset * CAMERA_SIN) / 1000;
                rz = (lineRzNumerator[index] + ozOffset * CAMERA_COS - ox * CAMERA_SIN) / 1000;
            } else {
                int dx = ox + 397, dz = oz * 256 + ozOffset;
                rx = (dx * CAMERA_COS + dz * CAMERA_SIN) / 1000;
                rz = (dz * CAMERA_COS - dx * CAMERA_SIN) / 1000;
            }
            float drawX = (rx + rz) * 0.48f - oy + X_SCREEN_OFFSET;
            float drawY = rx - rz;
            objectTransform.setIdentity();
            objectTransform.postTranslate(drawX, drawY, rx + rz + oy * 0.5f + Z_BASE);
            g3d.render(mesh.vertices, mesh.indices, mesh.appearance, objectTransform);
        }

        private void loadAppearances() {
            String[] names = {"grass", "road", "river", "rail", "car", "log", "lily", "tree", "boulder", "hero", "train", "train-front", "train-back", "train-light", "unused", "train-light-on1", "train-light-on2", "train-light-off"};
            int i; for (i = 0; i < names.length; i++) appearances[i] = appearance("/textures/" + names[i] + ".png");
        }

        private Appearance appearance(String path) {
            Appearance result = new Appearance(); PolygonMode polygon = new PolygonMode(); polygon.setCulling(PolygonMode.CULL_BACK); polygon.setShading(PolygonMode.SHADE_FLAT);
            CompositingMode composite = new CompositingMode(); composite.setDepthTestEnable(true); composite.setDepthWriteEnable(true);
            result.setPolygonMode(polygon); result.setCompositingMode(composite);
            try { Texture2D texture = new Texture2D(new Image2D(Image2D.RGB, Image.createImage(path))); texture.setBlending(Texture2D.FUNC_REPLACE); texture.setFiltering(Texture2D.FILTER_BASE_LEVEL, Texture2D.FILTER_NEAREST); texture.setWrapping(Texture2D.WRAP_CLAMP, Texture2D.WRAP_CLAMP); result.setTexture(0, texture); } catch (Throwable ignored) { }
            return result;
        }

        private void updateHudImage() {
            updateFpsImage();
        }

        private void updateFpsImage() {
            fpsImage = Image.createImage(FPS_W, FPS_H);
            fpsGraphics = fpsImage.getGraphics();
            fpsGraphics.setColor(0x000000);
            fpsGraphics.setFont(overlayFont);
            String fpsText = "FPS " + fps;
            fpsGraphics.drawString(fpsText, 2, 2, Graphics.TOP | Graphics.LEFT);
            int[] source = new int[FPS_W * FPS_H];
            fpsImage.getRGB(source, 0, FPS_W, 0, 0, FPS_W, FPS_H);
            fpsPixels = new int[FPS_W * FPS_H];
            int x, y;
            for (y = 0; y < FPS_H; y++) for (x = 0; x < FPS_W; x++) {
                int pixel = source[y * FPS_W + x];
                int argb = (pixel & 0x00ffffff) == 0x00ffffff ? 0x00000000 : 0xffffffff;
                int rotatedX = FPS_H - 1 - y;
                int rotatedY = x;
                fpsPixels[rotatedY * FPS_H + rotatedX] = argb;
            }
        }

        private void updateProgressImage() {
            progressImage = Image.createImage(FPS_W, FPS_H);
            progressGraphics = progressImage.getGraphics();
            progressGraphics.setColor(0x000000);
            progressGraphics.setFont(overlayFont);
            progressGraphics.drawString("" + (7 - furthestPlayerZ), FPS_W - 2, 2,
                    Graphics.TOP | Graphics.RIGHT);
            int[] source = new int[FPS_W * FPS_H];
            progressImage.getRGB(source, 0, FPS_W, 0, 0, FPS_W, FPS_H);
            progressPixels = new int[FPS_W * FPS_H];
            int x, y;
            for (y = 0; y < FPS_H; y++) for (x = 0; x < FPS_W; x++) {
                int pixel = source[y * FPS_W + x];
                int argb = (pixel & 0x00ffffff) == 0x00ffffff ? 0x00000000 : 0xffffffff;
                int rotatedX = FPS_H - 1 - y;
                int rotatedY = x;
                progressPixels[rotatedY * FPS_H + rotatedX] = argb;
            }
        }

        private void showNotice(String text) {
            Image image = Image.createImage(NOTICE_SRC_W, NOTICE_SRC_H);
            Graphics graphics = image.getGraphics();
            graphics.setColor(0x000000);
            graphics.setFont(overlayFont);
            graphics.drawString(text, NOTICE_SRC_W / 2, 2,
                    Graphics.TOP | Graphics.HCENTER);
            int[] source = new int[NOTICE_SRC_W * NOTICE_SRC_H];
            image.getRGB(source, 0, NOTICE_SRC_W, 0, 0, NOTICE_SRC_W, NOTICE_SRC_H);
            noticePixels = new int[NOTICE_SRC_W * NOTICE_SRC_H];
            int x, y;
            for (y = 0; y < NOTICE_SRC_H; y++) for (x = 0; x < NOTICE_SRC_W; x++) {
                int pixel = source[y * NOTICE_SRC_W + x];
                int argb = (pixel & 0x00ffffff) == 0x00ffffff ? 0x00000000 : 0xffffffff;
                int rotatedX = NOTICE_SRC_H - 1 - y;
                int rotatedY = x;
                noticePixels[rotatedY * NOTICE_SRC_H + rotatedX] = argb;
            }
            noticeChangedAt = System.currentTimeMillis();
        }
        private void drawHud(Graphics g, long now, int width, int height) {
            if (now - lastHudUpdate >= HUD_MS) {
                lastHudUpdate = now;
                updateFpsImage();
                updateProgressImage();
            }
            drawOverlay(g, width, height, now);
        }

        private void drawOverlay(Graphics g, int width, int height, long now) {
            g.drawRGB(fpsPixels, 0, FPS_H,
                    width - FPS_H, 0, FPS_H, FPS_W, true);
            g.drawRGB(progressPixels, 0, FPS_H,
                    width - FPS_H, height - FPS_W, FPS_H, FPS_W, true);
            if (gameOver) {
                g.drawRGB(gameOverPixels, 0, GAME_OVER_H,
                        (width - GAME_OVER_H) / 2, (height - GAME_OVER_W) / 2,
                        GAME_OVER_H, GAME_OVER_W, true);
            }
            if (noticePixels != null && noticeChangedAt != 0L
                    && now - noticeChangedAt < ORIENTATION_NOTICE_MS) {
                g.drawRGB(noticePixels, 0, NOTICE_SRC_H,
                        (width - NOTICE_SRC_H) / 2, (height - NOTICE_SRC_W) / 2,
                        NOTICE_SRC_H, NOTICE_SRC_W, true);
            }
        }
        private String orientationName(int mode) {
            if (mode == ORIENTATION_LEFT) return "# Rotate left";
            if (mode == ORIENTATION_RIGHT) return "# Rotate right";
            if (mode == ORIENTATION_NATIVE_LANDSCAPE) return "# Native landscape";
            return "# Portrait";
        }
        protected void paint(Graphics g) {
            drawOverlay(g, canvasW, canvasH, System.currentTimeMillis());
        }
        protected void sizeChanged(int width, int height) {
            if (width <= 0 || height <= 0) return;
            canvasW = width;
            canvasH = height;
            preparedOrientation = -1;
            rotatedFrame = null;
            rotatedGraphics = null;
            lowResolutionFrame = null;
            lowResolutionGraphics = null;
        }
        private static int wrap(int value, int min, int max) { int span = max - min + 1; while (value < min) value += span; while (value > max) value -= span; return value; }
        private static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) { } }
    }

    private static final class M3GMesh {
        final VertexBuffer vertices;
        final TriangleStripArray indices;
        final Appearance appearance;
        final int triangles;
        final int minX, maxX, minY, maxY;
        M3GMesh(int[][] parts, int turn, Appearance appearance) {
            this(parts, turn, appearance, null, false);
        }
        M3GMesh(int[][] parts, int turn, Appearance appearance, boolean removeFarTrainSide) {
            this(parts, turn, appearance, null, removeFarTrainSide, 100);
        }
        M3GMesh(int[][] parts, int turn, Appearance appearance, int[] placements) {
            this(parts, turn, appearance, placements, false, 100);
        }
        M3GMesh(int[][] parts, int turn, Appearance appearance, int[] placements, boolean removeFarTrainSide) {
            this(parts, turn, appearance, placements, removeFarTrainSide, 100);
        }
        M3GMesh(int[][] parts, int turn, Appearance appearance, int scaleYPercent) {
            this(parts, turn, appearance, null, false, scaleYPercent);
        }
        M3GMesh(int[][] parts, int turn, Appearance appearance, int[] placements, boolean removeFarTrainSide, int scaleYPercent) {
            int baseTotal = 0, p, i;
            for (p = 0; p < parts.length; p++) baseTotal += retainedCorners(parts[p], turn, removeFarTrainSide);
            int instanceCount = placements == null ? 1 : placements.length / 4;
            int total = baseTotal * instanceCount;
            triangles = total / 3; short[] positions = new short[total * 3]; short[] uv = new short[total * 2]; int[] ix = new int[total]; int[] strips = new int[triangles];
            int tableSize = 1; while (tableSize < total * 2) tableSize <<= 1;
            int[] table = new int[tableSize]; int corner = 0, unique = 0;
            int boundsMinX = 32767, boundsMaxX = -32768, boundsMinY = 32767, boundsMaxY = -32768;
            int instance;
            for (instance = 0; instance < instanceCount; instance++) for (p = 0; p < parts.length; p++) for (i = 0; i < parts[p].length; i += 4) {
                if (removeFarTrainSide && isFarTrainSide(parts[p], i - i % 12, turn)) continue;
                int x = parts[p][i], y = parts[p][i + 1], z = parts[p][i + 2], tx, ty, tz;
                if (turn == 1) { tx = -z; ty = y; tz = x; } else if (turn == 2) { tx = -x; ty = y; tz = -z; }
                else if (turn == 3) { tx = z; ty = y; tz = -x; } else if (turn == 4) { tx = x; ty = -z; tz = y; }
                else if (turn == 5) { tx = -x; ty = -z; tz = -y; } else { tx = x; ty = y; tz = z; }
                ty = ty * scaleYPercent / 100;
                int rx = (tx * SceneCanvas.CAMERA_COS + tz * SceneCanvas.CAMERA_SIN) / 1000;
                int rz = (tz * SceneCanvas.CAMERA_COS - tx * SceneCanvas.CAMERA_SIN) / 1000;
                int px = (rx + rz) * 48 / 100 - ty;
                int py = rx - rz;
                int pz = rx + rz + ty / 2;
                if (placements != null) {
                    int offset = instance * 4;
                    int dx = placements[offset] + 397;
                    int dz = placements[offset + 2] * 256 + placements[offset + 3];
                    int prx = (dx * SceneCanvas.CAMERA_COS + dz * SceneCanvas.CAMERA_SIN) / 1000;
                    int prz = (dz * SceneCanvas.CAMERA_COS - dx * SceneCanvas.CAMERA_SIN) / 1000;
                    int oy = placements[offset + 1];
                    px += (int)((prx + prz) * 0.48f - oy + SceneCanvas.X_SCREEN_OFFSET);
                    py += prx - prz;
                    pz += prx + prz + oy / 2 + (int)SceneCanvas.Z_BASE;
                }
                int packed = parts[p][i + 3];
                short pu = (short)(packed >> 8);
                // M3G Image2D uses the opposite vertical texture origin from
                // the OBJ UV convention used by the source assets.
                short pv = (short)(255 - (packed & 255));
                int slot = (px * 73856093 ^ py * 19349663 ^ pz * 83492791
                        ^ pu * 31 ^ pv) & (tableSize - 1);
                int index;
                while (true) {
                    int stored = table[slot];
                    if (stored == 0) {
                        index = unique++;
                        positions[index * 3] = (short)px; positions[index * 3 + 1] = (short)py; positions[index * 3 + 2] = (short)pz;
                        uv[index * 2] = pu; uv[index * 2 + 1] = pv;
                        if (px < boundsMinX) boundsMinX = px; if (px > boundsMaxX) boundsMaxX = px;
                        if (py < boundsMinY) boundsMinY = py; if (py > boundsMaxY) boundsMaxY = py;
                        table[slot] = index + 1;
                        break;
                    }
                    index = stored - 1;
                    if (positions[index * 3] == px && positions[index * 3 + 1] == py
                            && positions[index * 3 + 2] == pz && uv[index * 2] == pu
                            && uv[index * 2 + 1] == pv) break;
                    slot = (slot + 1) & (tableSize - 1);
                }
                ix[corner++] = index;
            }
            for (i = 0; i < strips.length; i++) strips[i] = 3;
            VertexArray pa = new VertexArray(unique, 3, 2); pa.set(0, unique, positions); VertexArray ua = new VertexArray(unique, 2, 2); ua.set(0, unique, uv);
            vertices = new VertexBuffer(); vertices.setPositions(pa, 1.0f, null); vertices.setTexCoords(0, ua, 1.0f / 255.0f, null);
            indices = new TriangleStripArray(ix, strips); this.appearance = appearance;
            minX = boundsMinX; maxX = boundsMaxX; minY = boundsMinY; maxY = boundsMaxY;
        }

        private static int retainedCorners(int[] part, int turn, boolean removeFarTrainSide) {
            if (!removeFarTrainSide) return part.length / 4;
            int corners = 0, i;
            for (i = 0; i < part.length; i += 12) if (!isFarTrainSide(part, i, turn)) corners += 3;
            return corners;
        }

        private static boolean isFarTrainSide(int[] part, int offset, int turn) {
            int ax = part[offset], ay = part[offset + 1];
            int bx = part[offset + 4], by = part[offset + 5];
            int cx = part[offset + 8], cy = part[offset + 9];
            int normalZ = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
            return turn == 0 ? normalZ < 0 : turn == 2 && normalZ > 0;
        }

        static int[] projectedBounds(int[][] parts, int turn) {
            int minX = 32767, maxX = -32768, minY = 32767, maxY = -32768;
            int p, i;
            for (p = 0; p < parts.length; p++) for (i = 0; i < parts[p].length; i += 4) {
                int x = parts[p][i], y = parts[p][i + 1], z = parts[p][i + 2], tx, ty, tz;
                if (turn == 1) { tx = -z; ty = y; tz = x; } else if (turn == 2) { tx = -x; ty = y; tz = -z; }
                else if (turn == 3) { tx = z; ty = y; tz = -x; } else if (turn == 4) { tx = x; ty = -z; tz = y; }
                else if (turn == 5) { tx = -x; ty = -z; tz = -y; } else { tx = x; ty = y; tz = z; }
                int rx = (tx * SceneCanvas.CAMERA_COS + tz * SceneCanvas.CAMERA_SIN) / 1000;
                int rz = (tz * SceneCanvas.CAMERA_COS - tx * SceneCanvas.CAMERA_SIN) / 1000;
                int px = (rx + rz) * 48 / 100 - ty;
                int py = rx - rz;
                if (px < minX) minX = px; if (px > maxX) maxX = px;
                if (py < minY) minY = py; if (py > maxY) maxY = py;
            }
            return new int[] {minX, maxX, minY, maxY};
        }
    }
}
