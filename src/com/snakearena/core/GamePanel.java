package com.snakearena.core;

import com.snakearena.entity.*;
import com.snakearena.render.FloatingTextManager;
import com.snakearena.render.LayeredRenderer;
import com.snakearena.render.ParticleSystem;
import com.snakearena.world.Chunk;
import com.snakearena.world.ChunkManager;
import com.snakearena.world.WorldProp;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.awt.image.BufferedImage;
import java.util.Iterator;
import java.util.List;

/**
 * GamePanel quản lý toàn bộ vòng lặp 60 FPS, hệ thống camera, thế giới vô hạn,
 * tương tác người chơi (Bàn phím + Chuột) và các thực thể sinh thái.
 * Render qua low-resolution buffer (480x270) và phóng to nguyên số (Nearest Neighbor).
 */
public class GamePanel extends JPanel implements Runnable, KeyListener, MouseListener, MouseMotionListener {
    // ==========================================
    // CÁC HẰNG SỐ ĐỘ PHÂN GIẢI LOGIC & CẤU HÌNH HỆ THỐNG
    // ==========================================
    // Hệ số zoom góc nhìn camera:
    // 1.0: Góc nhìn chuẩn 1:1 của game gốc (không phóng to, góc nhìn bao quát nguyên bản).
    // > 1.0: Phóng to cận cảnh hơn (ví dụ 1.25, 1.5, 2.0).
    // < 1.0: Thu nhỏ mở rộng góc nhìn bao quát hơn (ví dụ 0.8, 0.75).
    public static final double CAMERA_ZOOM = 1.0;
    public static final int CHUNK_SIZE = 512;        // Kích thước mỗi chunk thế giới (px)
    public static final int DEFAULT_WIDTH = 1024;    // Chiều rộng cửa sổ panel mặc định
    public static final int DEFAULT_HEIGHT = 720;    // Chiều cao cửa sổ panel mặc định
    public static final double TARGET_FPS = 60.0;    // Tốc độ khung hình mục tiêu
    public static final double FIXED_TIME_STEP = 1.0 / TARGET_FPS; // Bước nhảy thời gian cố định (~0.01667s)

    private Camera camera;
    private ChunkManager world;
    private LayeredRenderer renderer;

    // Buffer thế giới độ phân giải logic động (tự động khớp tỉ lệ cửa sổ, 100% không viền đen)
    private int logicWidth = (int) Math.ceil(DEFAULT_WIDTH / CAMERA_ZOOM);
    private int logicHeight = (int) Math.ceil(DEFAULT_HEIGHT / CAMERA_ZOOM);
    private BufferedImage logicBuffer;
    private Graphics2D logicGraphics;

    // Cache các chunk đang hoạt động (chỉ tải lại khi camera bước sang chunk mới)
    private int lastCamChunkX = Integer.MIN_VALUE;
    private int lastCamChunkY = Integer.MIN_VALUE;
    private List<Chunk> cachedActiveChunks = new ArrayList<>();

    // Game loop 60 FPS tách biệt luồng update và render
    private Thread gameThread;
    private volatile boolean running = false;

    private PlayerSnake player;
    private final List<AISnake> aiSnakes = new ArrayList<>();
    private final List<Boss> bosses = new ArrayList<>();
    private final List<Crocodile> crocodiles = new ArrayList<>();
    private int lastKnownLevel = 1;
    private final List<Prey> preys = new ArrayList<>();
    private final List<PoisonBolt> poisonBolts = new ArrayList<>();
    private final List<Snake> allSnakesCache = new ArrayList<>();
    private Eagle eagle = null;
    private double eagleTimer = Config.Ecosystem.EAGLE_INTERVAL;

    private final ParticleSystem particles = new ParticleSystem();
    private final FloatingTextManager texts = new FloatingTextManager();

    public enum GameState {
        MAIN_MENU,
        STAGE_SELECT,
        HOW_TO_PLAY,
        PLAYING,
        PAUSED,
        VICTORY,
        DEFEAT
    }

    private GameState gameState = GameState.MAIN_MENU;
    private int currentStage = 1;
    private boolean bearDefeated = false;
    private boolean pythonDefeated = false;
    private boolean octopusDefeated = false;
    private boolean swampKingDefeated = false;
    private double stagePlayTime = 0.0;
    private int enemiesKilledCount = 0;
    private double menuAnimTime = 0.0;

    private boolean isPaused = false;
    private boolean isGameOver = false;
    private boolean isLevelUpSelecting = false;
    private int pauseMenuIndex = 0; // 0: Tiếp tục, 1: Chơi lại, 2: Đổi map, 3: Thoát

    // Dừng khựng (Hit-stop) toàn cục khi Boss Gấu táp trúng hoặc đòn nặng va chạm
    private static volatile double hitStopTimer = 0.0;

    public static void triggerHitStop(double duration) {
        if (duration > hitStopTimer) {
            hitStopTimer = duration;
        }
    }

    public static void resetHitStop() {
        hitStopTimer = 0.0;
    }

    public static double getHitStopTimer() {
        return hitStopTimer;
    }

    private Point mouseScreenPos = new Point(DEFAULT_WIDTH / 2, DEFAULT_HEIGHT / 2);
    private boolean useMouse = true;

    public GamePanel() {
        setPreferredSize(new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));
        setBackground(Color.BLACK);
        setFocusable(true);

        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);

        initGameWorld();
        gameState = GameState.MAIN_MENU;

        initLogicBuffer();
        startGameLoop();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        initLogicBuffer();
        if (!running) {
            startGameLoop();
        }
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        stopGameLoop();
    }

    public synchronized void startGameLoop() {
        if (running) return;
        running = true;
        gameThread = new Thread(this, "GameLoop-Thread");
        gameThread.setDaemon(true);
        gameThread.start();
    }

    public synchronized void stopGameLoop() {
        running = false;
        if (gameThread != null) {
            gameThread.interrupt();
            gameThread = null;
        }
    }

    @Override
    public void run() {
        final long nsPerTick = (long) (1_000_000_000.0 / TARGET_FPS);
        long lastTime = System.nanoTime();
        double delta = 0;

        while (running) {
            long now = System.nanoTime();
            delta += (now - lastTime) / (double) nsPerTick;
            lastTime = now;

            boolean shouldRender = false;
            while (delta >= 1.0) {
                updateGame(FIXED_TIME_STEP);
                delta -= 1.0;
                shouldRender = true;
            }

            if (shouldRender) {
                repaint();
            }

            try {
                long sleepMs = (lastTime + nsPerTick - System.nanoTime()) / 1_000_000;
                if (sleepMs > 1) {
                    Thread.sleep(sleepMs);
                } else {
                    Thread.sleep(1);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void initLogicBuffer() {
        checkAndResizeBuffer(logicWidth, logicHeight);
    }

    private void checkAndResizeBuffer(int reqW, int reqH) {
        if (logicBuffer != null && logicBuffer.getWidth() == reqW && logicBuffer.getHeight() == reqH && logicGraphics != null) {
            return;
        }
        logicWidth = Math.max(1, reqW);
        logicHeight = Math.max(1, reqH);

        GraphicsConfiguration gc = getGraphicsConfiguration();
        if (gc == null && !GraphicsEnvironment.isHeadless()) {
            try {
                GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
                GraphicsDevice gd = ge.getDefaultScreenDevice();
                gc = gd.getDefaultConfiguration();
            } catch (Exception ignored) {}
        }
        if (logicGraphics != null) {
            logicGraphics.dispose();
        }
        if (gc != null) {
            logicBuffer = gc.createCompatibleImage(logicWidth, logicHeight, Transparency.OPAQUE);
        } else {
            logicBuffer = new BufferedImage(logicWidth, logicHeight, BufferedImage.TYPE_INT_RGB);
        }
        logicGraphics = logicBuffer.createGraphics();
        logicGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        logicGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    }

    public Point getLogicMousePos() {
        if (mouseScreenPos == null) {
            return new Point(logicWidth / 2, logicHeight / 2);
        }
        int lx = (int) Math.round(mouseScreenPos.x / CAMERA_ZOOM);
        int ly = (int) Math.round(mouseScreenPos.y / CAMERA_ZOOM);
        lx = Math.max(0, Math.min(logicWidth, lx));
        ly = Math.max(0, Math.min(logicHeight, ly));
        return new Point(lx, ly);
    }

    public void startStage(int stageId) {
        currentStage = stageId;
        bearDefeated = false;
        pythonDefeated = false;
        octopusDefeated = false;
        swampKingDefeated = false;
        stagePlayTime = 0.0;
        enemiesKilledCount = 0;
        isPaused = false;
        isGameOver = false;
        isLevelUpSelecting = false;
        initGameWorld();
        gameState = GameState.PLAYING;
    }

    private void initGame() {
        startStage(currentStage);
    }

    private void initGameWorld() {
        resetHitStop();
        camera = new Camera(logicWidth, logicHeight);
        world = new ChunkManager(currentStage);
        renderer = new LayeredRenderer();
        player = new PlayerSnake(0, 0);

        lastCamChunkX = Integer.MIN_VALUE;
        lastCamChunkY = Integer.MIN_VALUE;
        cachedActiveChunks.clear();

        aiSnakes.clear();
        bosses.clear();
        crocodiles.clear();
        lastKnownLevel = 1;
        preys.clear();
        poisonBolts.clear();
        particles.clear();
        texts.clear();
        eagle = null;
        eagleTimer = Config.Ecosystem.EAGLE_INTERVAL;

        isGameOver = false;
        isPaused = false;
        isLevelUpSelecting = false;

        // Khởi tạo đàn mồi ban đầu
        for (int i = 0; i < Config.Ecosystem.TARGET_PREY; i++) {
            spawnPrey(true);
        }

        // Khởi tạo các con rắn AI cạnh tranh
        for (int i = 0; i < Config.Ecosystem.TARGET_AI; i++) {
            double angle = Math.random() * Math.PI * 2.0;
            double dist = 600.0 + Math.random() * 400.0;
            aiSnakes.add(new AISnake(player.getX() + Math.cos(angle) * dist, player.getY() + Math.sin(angle) * dist));
        }

        // Khởi tạo cá sấu trong các đầm nước xung quanh (Màn 2 nhiều cá sấu hơn do đầm lầy mở rộng)
        int initialCrocs = (currentStage == 2) ? 8 : Config.Ecosystem.TARGET_CROCODILES;
        for (int i = 0; i < initialCrocs; i++) {
            spawnCrocodile(true);
        }
    }

    private void spawnCrocodile(boolean near) {
        for (int attempts = 0; attempts < 35; attempts++) {
            double angle = Math.random() * Math.PI * 2.0;
            double dist = near ? (200.0 + Math.random() * 500.0) : (450.0 + Math.random() * 950.0);
            double cx = player.getX() + Math.cos(angle) * dist;
            double cy = player.getY() + Math.sin(angle) * dist;
            if (world.isInWater(cx, cy)) {
                crocodiles.add(new Crocodile(cx, cy));
                break;
            }
        }
    }

    private boolean isBossDefeatedOrActive(Boss.Type type) {
        if (type == Boss.Type.BEAR && bearDefeated) return true;
        if (type == Boss.Type.GIANT_PYTHON && pythonDefeated) return true;
        if (type == Boss.Type.OCTOPUS && octopusDefeated) return true;
        if (type == Boss.Type.SWAMP_KING && swampKingDefeated) return true;
        for (Boss b : bosses) {
            if (b.isAlive() && b.getType() == type) {
                return true;
            }
        }
        return false;
    }

    private void spawnLevelBoss(int level) {
        if (world == null || player == null) return;
        boolean spawnedAny = false;
        int maxTier = Math.min(level, 3);
        for (int lv = 2; lv <= maxTier; lv++) {
            Boss.Type bType = world.getBossTypeForLevel(lv);
            if (bType == null || !world.canSpawnBoss(bType) || isBossDefeatedOrActive(bType)) {
                continue;
            }
            Boss newBoss = world.createBossForStage(bType, player.getX(), player.getY());
            if (newBoss != null) {
                bosses.add(newBoss);
                texts.spawn(player.getX(), player.getY() - 52,
                        "THÁCH THỨC BOSS CẤP " + lv + ": " + newBoss.getName() + " XUẤT HIỆN!",
                        new Color(250, 204, 21), 4.0, 20);
                spawnedAny = true;
            }
        }
        if (spawnedAny) {
            SoundManager.playHit();
        }
    }

    private void spawnPrey(boolean near) {
        double angle = Math.random() * Math.PI * 2.0;
        double dist = near ? (150.0 + Math.random() * 450.0)
                : (Config.Ecosystem.SPAWN_MIN_DIST + Math.random() * (Config.Ecosystem.SPAWN_MAX_DIST - Config.Ecosystem.SPAWN_MIN_DIST));
        double px = player.getX() + Math.cos(angle) * dist;
        double py = player.getY() + Math.sin(angle) * dist;

        double roll = Math.random();
        Prey.Type type;
        int foodVal, energyVal;
        double speed, fleeR;

        // Động vật nhỏ cho từ 3 EXP trở lên (chuột, ếch, bướm, cá = 3 EXP; thỏ/cá lớn = 4 EXP)
        if (roll < 0.35) {
            type = Prey.Type.MOUSE; foodVal = 10; energyVal = 3; speed = 55.0; fleeR = 140.0;
        } else if (roll < 0.65) {
            type = Prey.Type.FROG; foodVal = 15; energyVal = 3; speed = 40.0; fleeR = 120.0;
        } else if (roll < 0.85) {
            type = Prey.Type.BUTTERFLY; foodVal = 12; energyVal = 3; speed = 45.0; fleeR = 100.0;
        } else {
            type = Prey.Type.RABBIT; foodVal = 20; energyVal = 4; speed = 75.0; fleeR = 180.0;
        }

        Prey prey = new Prey(px, py, type, foodVal, energyVal, speed, fleeR);
        if (currentStage == 2) {
            prey.setWaterOnly(true);
            // Bơi hoàn toàn dưới nước: nếu chạm đất đảo, tìm tọa độ nước gần nhất
            if (!world.isInWater(px, py)) {
                for (int step = 1; step <= 8; step++) {
                    double testX = px + Math.cos(angle) * step * 32.0;
                    double testY = py + Math.sin(angle) * step * 32.0;
                    if (world.isInWater(testX, testY)) {
                        prey = new Prey(testX, testY, type, foodVal, energyVal, speed, fleeR);
                        prey.setWaterOnly(true);
                        break;
                    }
                }
            }
        }

        preys.add(prey);
    }

    private void updateGame(double dt) {
        if (gameState == GameState.MAIN_MENU || gameState == GameState.STAGE_SELECT || gameState == GameState.HOW_TO_PLAY) {
            menuAnimTime += dt;
            return;
        }
        if (gameState == GameState.VICTORY || gameState == GameState.DEFEAT) {
            menuAnimTime += dt;
            return;
        }
        if (gameState == GameState.PAUSED) {
            return;
        }

        menuAnimTime += dt;
        stagePlayTime += dt;

        if (player.hasPendingLevelUpChoice()) {
            isLevelUpSelecting = true;
        }
        if (isLevelUpSelecting) return;

        if (player.getHealth() <= 0) {
            gameState = GameState.DEFEAT;
            SoundManager.playGameOver();
            return;
        }

        // Dừng khựng (Hit-stop) cho đòn nặng: tạm dừng mô phỏng thực thể nhưng vẫn giữ camera shake & chữ bay
        if (hitStopTimer > 0.0) {
            hitStopTimer = Math.max(0.0, hitStopTimer - dt);
            if (camera != null) {
                camera.update(dt);
            }
            texts.update(dt);
            return;
        }

        // 1. Điều khiển hướng bằng chuột nếu đang dùng chuột (Ánh xạ tọa độ chuột sang không gian logic)
        if (useMouse && mouseScreenPos != null) {
            Point logicMouse = getLogicMousePos();
            Point2D.Double worldMouse = camera.screenToWorld(logicMouse.x, logicMouse.y);
            player.directTo(worldMouse.x, worldMouse.y);
        }

        // 2. Cập nhật rắn người chơi
        player.update(dt, world, particles);

        // 2.1. Kiểm tra dịch chuyển xuyên hang đá (Ngẫu nhiên xuyên qua 1 hang khác)
        if (player.getCaveCooldown() <= 0) {
            WorldProp cave = world.getNearbyCaveEntrance(player.getX(), player.getY(), 32.0);
            if (cave != null) {
                WorldProp dest = world.getRandomDestinationCave(cave, player.getX(), player.getY());
                if (dest != null) {
                    particles.spawnEvolutionAura(cave.getX(), cave.getY(), new Color(168, 85, 247));
                    player.teleportTo(dest.getX(), dest.getY() + 14.0);
                    player.setCaveCooldown(3.5); // Cooldown 3.5s chống kẹt vòng lặp dịch chuyển
                    camera.snapTo(player.getX(), player.getY());
                    particles.spawnEvolutionAura(dest.getX(), dest.getY(), new Color(56, 189, 248));
                    texts.spawn(player.getX(), player.getY() - 40, "✦ DỊCH CHUYỂN XUYÊN HANG!", new Color(192, 132, 252), 2.5, 18);
                    SoundManager.playDash();
                    camera.shake(10.0);
                }
            }
        }

        // 3. Camera bám theo đầu rắn với Lerp và Lead distance
        camera.follow(player.getX(), player.getY(), player.getAngle(), player.getSpeed() / Config.SnakeConfig.BASE_SPEED);
        camera.update(dt);

        // 4. Cập nhật các chunk xung quanh camera (Chỉ tải lại khi camera bước sang chunk mới)
        int currentCamChunkX = (int) Math.floor(camera.getX() / CHUNK_SIZE);
        int currentCamChunkY = (int) Math.floor(camera.getY() / CHUNK_SIZE);
        if (currentCamChunkX != lastCamChunkX || currentCamChunkY != lastCamChunkY || cachedActiveChunks.isEmpty()) {
            lastCamChunkX = currentCamChunkX;
            lastCamChunkY = currentCamChunkY;
            cachedActiveChunks = world.updateActiveChunks(camera, dt);
        } else {
            for (Chunk chunk : cachedActiveChunks) {
                chunk.update(dt);
            }
        }

        // 5. Cập nhật rắn AI
        for (Iterator<AISnake> it = aiSnakes.iterator(); it.hasNext(); ) {
            AISnake ai = it.next();
            double dist = Math.hypot(ai.getX() - player.getX(), ai.getY() - player.getY());
            if (!ai.isAlive()) {
                if (!ai.isExpAwarded() && (ai.isPoisoned() || dist < 350.0)) {
                    ai.setExpAwarded(true);
                    player.addExp(8, 50, particles, texts);
                    player.healOnKill(Config.SnakeConfig.calcKillHeal(Config.SnakeConfig.KILL_HEAL_AI_SNAKE_FIXED, Config.SnakeConfig.KILL_HEAL_AI_SNAKE_RATIO, player.getMaxHealth()), particles, texts);
                    texts.spawn(ai.getX(), ai.getY() - 24, "☠ RẮN SĂN BỊ DIỆT (+8 EXP | +50 PTS)!", new Color(74, 222, 128), 2.0, 16);
                }
                it.remove();
            } else if (dist > Config.Ecosystem.DESPAWN_DIST) {
                it.remove();
            } else {
                ai.updateAI(dt, world, preys, player, particles);
            }
        }
        while (aiSnakes.size() < Config.Ecosystem.TARGET_AI) {
            double angle = Math.random() * Math.PI * 2.0;
            aiSnakes.add(new AISnake(player.getX() + Math.cos(angle) * 900.0, player.getY() + Math.sin(angle) * 900.0));
        }

        // 6. Kiểm tra sập bẫy thú (Người chơi giẫm phải bẫy kẹp)
        ChunkManager.TrapResult pTrap = world.checkTrapTrigger(player.getX(), player.getY(), 12.0);
        if (pTrap.triggered) {
            if (player.takeDamage(Config.SnakeConfig.TRAP_DAMAGE, camera)) {
                texts.spawn(player.getX(), player.getY() - 32, "⚡ SẬP BẪY KẸP (-" + Config.SnakeConfig.TRAP_DAMAGE + " HP)!", new Color(239, 68, 68), 1.8, 16);
                particles.spawnTrapSparks(player.getX(), player.getY());
            }
        }

        // Kiểm tra sập bẫy thú (Rắn AI giẫm phải bẫy)
        for (AISnake ai : aiSnakes) {
            if (!ai.isAlive()) continue;
            ChunkManager.TrapResult aiTrap = world.checkTrapTrigger(ai.getX(), ai.getY(), 12.0);
            if (aiTrap.triggered) {
                ai.takeDamage(Config.SnakeConfig.TRAP_DAMAGE, null);
                texts.spawn(ai.getX(), ai.getY() - 24, "⚡ RẮN AI SẬP BẪY!", new Color(248, 113, 113), 1.5, 14);
            }
        }

        // 7. Cập nhật Boss Khổng Lồ (Chỉ xuất hiện khi lên cấp, cấp càng cao Boss càng mạnh)
        if (player.getEvolutionLevel() > lastKnownLevel) {
            lastKnownLevel = player.getEvolutionLevel();
            spawnLevelBoss(lastKnownLevel);
        }

        for (Iterator<Boss> it = bosses.iterator(); it.hasNext(); ) {
            Boss boss = it.next();
            if (!boss.isAlive()) {
                enemiesKilledCount++;
                if (boss.getType() == Boss.Type.BEAR) {
                    bearDefeated = true;
                } else if (boss.getType() == Boss.Type.OCTOPUS) {
                    octopusDefeated = true;
                } else if (boss.getType() == Boss.Type.GIANT_PYTHON) {
                    pythonDefeated = true;
                } else if (boss.getType() == Boss.Type.SWAMP_KING) {
                    swampKingDefeated = true;
                }

                // Diệt Boss nhận lượng lớn EXP bứt phá lên cấp và điểm thưởng khổng lồ
                int rewardScore = switch (boss.getType()) {
                    case BEAR -> 500;
                    case OCTOPUS -> 800;
                    case GIANT_PYTHON -> 1000;
                    case SWAMP_KING -> 1800;
                    case VOID_DRAGON -> 3000;
                };
                int rewardExp = switch (boss.getType()) {
                    case BEAR -> 60;
                    case OCTOPUS -> 85;
                    case GIANT_PYTHON -> 100;
                    case SWAMP_KING -> 150;
                    case VOID_DRAGON -> 250;
                };
                player.addExp(rewardExp, rewardScore, particles, texts);
                player.healOnKill(Config.SnakeConfig.calcKillHeal(Config.SnakeConfig.KILL_HEAL_BOSS_FIXED, Config.SnakeConfig.KILL_HEAL_BOSS_RATIO, player.getMaxHealth()), particles, texts);
                texts.spawn(boss.getX(), boss.getY() - 40, "★ HẠ GỤC " + boss.getName() + " (+" + rewardExp + " EXP | +" + rewardScore + " ĐIỂM)!", new Color(250, 204, 21), 3.5, 18);
                particles.spawnEvolutionAura(boss.getX(), boss.getY(), new Color(250, 204, 21));
                SoundManager.playEvolve();
                it.remove();

                // Kiểm tra điều kiện Chiến Thắng Màn 1: Cả Gấu và Cự Trăn đều bị hạ
                if (currentStage == 1 && bearDefeated && pythonDefeated) {
                    gameState = GameState.VICTORY;
                    SoundManager.playVictory();
                    texts.spawn(player.getX(), player.getY() - 80, "★ CHIẾN THẮNG MÀN 1! BÁ CHỦ THẢO NGUYÊN! ★", new Color(250, 204, 21), 5.0, 24);
                    for (int k = 0; k < 60; k++) {
                        particles.spawnEvolutionAura(player.getX() + (Math.random() - 0.5) * 160, player.getY() + (Math.random() - 0.5) * 160, new Color(250, 204, 21));
                    }
                    return;
                } else if (currentStage == 2 && octopusDefeated && swampKingDefeated) {
                    gameState = GameState.VICTORY;
                    SoundManager.playVictory();
                    texts.spawn(player.getX(), player.getY() - 80, "★ CHIẾN THẮNG MÀN 2! BÁ CHỦ ĐẠI DƯƠNG XANH THẲM! ★", new Color(56, 189, 248), 5.0, 24);
                    for (int k = 0; k < 60; k++) {
                        particles.spawnEvolutionAura(player.getX() + (Math.random() - 0.5) * 160, player.getY() + (Math.random() - 0.5) * 160, new Color(56, 189, 248));
                    }
                    return;
                }
                continue;
            }

            boss.update(dt, player, world, poisonBolts, camera, particles, texts);

            // Chiến đấu với Boss: Nếu người chơi DASH (Lướt nhanh Lv.2+) đâm trúng Boss
            if (player.isDashing()) {
                double d = Math.hypot(player.getX() - boss.getX(), player.getY() - boss.getY());
                double hitR = (boss.getType() == Boss.Type.BEAR ? 42.0 : (boss.getType() == Boss.Type.SWAMP_KING ? 48.0 : 38.0));
                if (d < hitR) {
                    int dashDmg = 40 + player.getSkillDamageBonus();
                    boss.takeDamage(dashDmg, texts, particles);
                    camera.shake(14.0);
                    texts.spawn(player.getX(), player.getY() - 36, "⚔ ĐÒN LƯỚT XUYÊN PHÁ (-" + dashDmg + " HP BOSS)!", new Color(56, 189, 248), 1.8, 16);
                    // Đẩy dội nhẹ người chơi ra xa sau va chạm
                    player.directTo(player.getX() + (player.getX() - boss.getX()) * 10, player.getY() + (player.getY() - boss.getY()) * 10);
                }
            }
        }

        // 7.1. Cập nhật Cá Sấu Đầm Nước (Lurking & Stalking in water)
        for (Iterator<Crocodile> it = crocodiles.iterator(); it.hasNext(); ) {
            Crocodile croc = it.next();
            double dist = Math.hypot(croc.getX() - player.getX(), croc.getY() - player.getY());
            if (!croc.isAlive() || dist > Config.Ecosystem.DESPAWN_DIST) {
                it.remove();
            } else {
                croc.update(dt, player, world, camera, particles, texts);

                // Đòn lướt Dash của người chơi húc cá sấu
                if (player.isDashing() && Math.hypot(player.getX() - croc.getX(), player.getY() - croc.getY()) < 32.0) {
                    int dashDmg = 45 + player.getSkillDamageBonus();
                    croc.takeDamage(dashDmg, texts, particles);
                    camera.shake(8.0);
                    texts.spawn(croc.getX(), croc.getY() - 24, "⚔ LƯỚT HÚC CÁ SẤU (-" + dashDmg + " HP)!", new Color(56, 189, 248), 1.8, 14);
                    if (!croc.isAlive()) {
                        player.addExp(12, 80, particles, texts);
                        player.healOnKill(Config.SnakeConfig.calcKillHeal(Config.SnakeConfig.KILL_HEAL_CROCODILE_FIXED, Config.SnakeConfig.KILL_HEAL_CROCODILE_RATIO, player.getMaxHealth()), particles, texts);
                        texts.spawn(croc.getX(), croc.getY() - 34, "★ DIỆT CÁ SẤU (+12 EXP)!", new Color(250, 204, 21), 2.5, 16);
                    }
                }
            }
        }
        int targetCrocs = (currentStage == 2) ? 9 : Config.Ecosystem.TARGET_CROCODILES;
        while (crocodiles.size() < targetCrocs) {
            spawnCrocodile(false);
        }

        // 8. Cập nhật con mồi
        allSnakesCache.clear();
        allSnakesCache.add(player);
        allSnakesCache.addAll(aiSnakes);

        for (Iterator<Prey> it = preys.iterator(); it.hasNext(); ) {
            Prey prey = it.next();
            double dist = Math.hypot(prey.getX() - player.getX(), prey.getY() - player.getY());
            if (!prey.isAlive() || dist > Config.Ecosystem.DESPAWN_DIST) {
                it.remove();
            } else {
                prey.update(dt, world, allSnakesCache);
            }
        }
        while (preys.size() < Config.Ecosystem.TARGET_PREY) {
            spawnPrey(false);
        }

        // 9. Cập nhật Kẻ săn mồi trên không: Đại bàng (Màn 1) / Chim hải âu (Màn 2)
        eagleTimer -= dt;
        if (eagleTimer <= 0 && (eagle == null || !eagle.isAlive())) {
            double targetX = player.getX() + (Math.random() - 0.5) * 60.0;
            double targetY = player.getY() + (Math.random() - 0.5) * 60.0;
            eagle = new Eagle(targetX, targetY, currentStage);
            eagleTimer = Config.Ecosystem.EAGLE_INTERVAL;
            if (currentStage == 2) {
                texts.spawn(player.getX(), player.getY() - 48, "⚠ HẢI ÂU ĐANG KHÓA TỌA ĐỘ VỒ MỒI!", new Color(56, 189, 248), 2.8, 16);
            } else {
                texts.spawn(player.getX(), player.getY() - 48, "⚠ ĐẠI BÀNG ĐANG KHÓA TỌA ĐỘ VỒ MỒI!", new Color(239, 68, 68), 2.8, 16);
            }
            SoundManager.playEagle();
        }

        if (eagle != null && eagle.isAlive()) {
            eagle.update(dt, allSnakesCache, preys, player, camera, particles, texts);
        }

        // 10. Ăn mồi (Người chơi chỉ ăn được khi KHÔNG ẩn nấp!)
        if (!player.isHidden()) {
            for (Prey prey : preys) {
                if (!prey.isAlive()) continue;
                double d = Math.hypot(prey.getX() - player.getX(), prey.getY() - player.getY());
                if (d < 20.0) {
                    prey.setAlive(false);
                    player.feed(prey.getFoodValue(), prey.getEnergyValue(), particles, texts);
                    break;
                }
            }
        }

        // Ăn mồi (Rắn AI)
        for (AISnake ai : aiSnakes) {
            if (!ai.isAlive()) continue;
            for (Prey prey : preys) {
                if (!prey.isAlive()) continue;
                double d = Math.hypot(prey.getX() - ai.getX(), prey.getY() - ai.getY());
                if (d < 20.0) {
                    prey.setAlive(false);
                    ai.feed(prey.getFoodValue(), prey.getEnergyValue(), null, null);
                    break;
                }
            }
        }

        // 11. Xung đột chiến đấu giữa Người chơi và Rắn AI
        for (AISnake ai : aiSnakes) {
            if (!ai.isAlive()) continue;

            // Rắn AI săn người chơi: cắn trực tiếp vào đầu nếu bắt kịp và người chơi không ẩn nấp
            double distHead = Math.hypot(player.getX() - ai.getX(), player.getY() - ai.getY());
            if (distHead < 20.0 && !player.isHidden()) {
                if (player.takeDamage(Config.SnakeConfig.AI_BITE_DAMAGE, camera)) {
                    texts.spawn(player.getX(), player.getY() - 30, "⚔ BỊ RẮN SĂN CẮN TRÚNG (-" + Config.SnakeConfig.AI_BITE_DAMAGE + " HP)!", new Color(239, 68, 68), 1.8, 16);
                    ai.setTargetAngle(ai.getAngle() + Math.PI * 0.75); // Bật lùi sau khi cắn
                }
            }

            // Người chơi đâm đầu vào thân AI
            for (int i = 2; i < ai.getSegments().size(); i++) {
                SnakeSegment seg = ai.getSegments().get(i);
                if (Math.hypot(player.getX() - seg.x, player.getY() - seg.y) < 14.0) {
                    player.takeDamage(Config.SnakeConfig.AI_BITE_DAMAGE, camera);
                    break;
                }
            }

            // AI đâm đầu vào thân Người chơi -> AI bị hạ gục!
            for (int i = 2; i < player.getSegments().size(); i++) {
                SnakeSegment seg = player.getSegments().get(i);
                if (Math.hypot(ai.getX() - seg.x, ai.getY() - seg.y) < 14.0) {
                    ai.takeDamage(999, null); // Hạ gục AI
                    if (!ai.isExpAwarded()) {
                        ai.setExpAwarded(true);
                        player.addExp(8, 50, particles, texts);
                        player.healOnKill(Config.SnakeConfig.calcKillHeal(Config.SnakeConfig.KILL_HEAL_AI_SNAKE_FIXED, Config.SnakeConfig.KILL_HEAL_AI_SNAKE_RATIO, player.getMaxHealth()), particles, texts);
                        texts.spawn(ai.getX(), ai.getY() - 20, "⚔ DIỆT RẮN SĂN (+8 EXP | +50 ĐIỂM)!", new Color(56, 189, 248), 2.0, 16);
                        SoundManager.playEat();
                    }
                    break;
                }
            }
        }

        // 12. Cập nhật các tia đạn độc (Poison Bolts) và va chạm
        for (Iterator<PoisonBolt> it = poisonBolts.iterator(); it.hasNext(); ) {
            PoisonBolt bolt = it.next();
            bolt.update(dt, particles);
            if (!bolt.isAlive()) {
                it.remove();
                continue;
            }

            // Đạn độc do Boss bắn ra chỉ gây sát thương lên người chơi (không đánh trúng phe AI/Boss khác)
            if (bolt.isHostileToPlayer()) {
                if (player.isAlive() && !player.isHidden()) {
                    boolean pHit = Math.hypot(bolt.getX() - player.getX(), bolt.getY() - player.getY()) < 22.0;
                    if (!pHit) {
                        List<SnakeSegment> pSegs = player.getSegments();
                        for (int i = 1; i < pSegs.size(); i++) {
                            SnakeSegment seg = pSegs.get(i);
                            if (Math.hypot(bolt.getX() - seg.x, bolt.getY() - seg.y) < 18.0) {
                                pHit = true;
                                break;
                            }
                        }
                    }
                    if (pHit) {
                        if (player.takeDamage(bolt.getDamage(), camera)) {
                            if (bolt.getDotDamage() > 0) {
                                player.inflictPoison(2.5, bolt.getDotDamage());
                            }
                            particles.spawnPoisonSplash(bolt.getX(), bolt.getY());
                            texts.spawn(player.getX(), player.getY() - 32,
                                    "TRÚNG ĐỘC BOSS (-" + bolt.getDamage() + " HP)!",
                                    new Color(217, 70, 239), 1.6, 15);
                            SoundManager.playHit();
                        }
                        it.remove();
                    }
                }
                continue;
            }

            boolean hit = false;

            // Đạn trúng Rắn AI (Mở rộng phạm vi va chạm lên 38px)
            for (AISnake ai : aiSnakes) {
                if (!ai.isAlive()) continue;
                boolean aiHit = false;
                for (SnakeSegment seg : ai.getSegments()) {
                    if (Math.hypot(bolt.getX() - seg.x, bolt.getY() - seg.y) < 38.0) {
                        aiHit = true;
                        break;
                    }
                }
                if (aiHit) {
                    int impactDmg = Config.SnakeConfig.VENOM_IMPACT_DMG + player.getSkillDamageBonus();
                    int dotDmg = Config.SnakeConfig.VENOM_DOT_DMG + (player.getSkillDamageBonus() / 3);
                    ai.takeDamage(impactDmg, null);
                    ai.inflictPoison(Config.SnakeConfig.VENOM_DOT_DURATION, dotDmg);
                    particles.spawnPoisonSplash(bolt.getX(), bolt.getY());
                    if (!ai.isAlive()) {
                        if (!ai.isExpAwarded()) {
                            ai.setExpAwarded(true);
                            player.addExp(10, 60, particles, texts);
                            player.healOnKill(Config.SnakeConfig.calcKillHeal(Config.SnakeConfig.KILL_HEAL_AI_SNAKE_FIXED, Config.SnakeConfig.KILL_HEAL_AI_SNAKE_RATIO, player.getMaxHealth()), particles, texts);
                            texts.spawn(ai.getX(), ai.getY() - 28, "☠ BẮN HẠ RẮN AI (+10 EXP)!", new Color(74, 222, 128), 2.0, 16);
                        }
                    } else {
                        texts.spawn(ai.getX(), ai.getY() - 26, "☠ TRÚNG ĐỘC (-" + dotDmg + " HP/s)!", new Color(74, 222, 128), 1.5, 14);
                    }
                    hit = true;
                    break;
                }
            }

            if (!hit) {
                // Đạn trúng Boss Khổng Lồ (Mở rộng phạm vi va chạm lên 58px thân / 64px đầu)
                for (Boss boss : bosses) {
                    if (!boss.isAlive()) continue;
                    boolean bHit = false;
                    if (!boss.getSegments().isEmpty()) {
                        for (SnakeSegment bSeg : boss.getSegments()) {
                            if (Math.hypot(bolt.getX() - bSeg.x, bolt.getY() - bSeg.y) < 58.0) {
                                bHit = true;
                                break;
                            }
                        }
                    } else {
                        if (Math.hypot(bolt.getX() - boss.getX(), bolt.getY() - boss.getY()) < 64.0) {
                            bHit = true;
                        }
                    }
                    if (bHit) {
                        int bImpact = Config.SnakeConfig.VENOM_IMPACT_DMG + 10 + player.getSkillDamageBonus();
                        int bDot = 8 + (player.getSkillDamageBonus() / 3);
                        boss.takeDamage(bImpact, texts, particles);
                        boss.inflictPoison(4.0, bDot);
                        texts.spawn(boss.getX(), boss.getY() - 36, "☠ BOSS TRÚNG ĐỘC (-" + bDot + " HP/s)!", new Color(74, 222, 128), 1.8, 16);
                        particles.spawnPoisonSplash(bolt.getX(), bolt.getY());
                        hit = true;
                        break;
                    }
                }
            }

            if (!hit) {
                // Đạn trúng Cá Sấu Đầm Nước (Mở rộng phạm vi va chạm lên 46px)
                for (Crocodile croc : crocodiles) {
                    if (!croc.isAlive()) continue;
                    if (Math.hypot(bolt.getX() - croc.getX(), bolt.getY() - croc.getY()) < 46.0) {
                        int cImpact = Config.SnakeConfig.VENOM_IMPACT_DMG + player.getSkillDamageBonus();
                        int cDot = Config.SnakeConfig.VENOM_DOT_DMG + (player.getSkillDamageBonus() / 3);
                        croc.takeDamage(cImpact, texts, particles);
                        croc.inflictPoison(Config.SnakeConfig.VENOM_DOT_DURATION, cDot);
                        texts.spawn(croc.getX(), croc.getY() - 20, "☠ CÁ SẤU TRÚNG ĐỘC (-" + cImpact + " HP)!", new Color(74, 222, 128), 1.5, 14);
                        particles.spawnPoisonSplash(bolt.getX(), bolt.getY());
                        if (!croc.isAlive()) {
                            player.addExp(12, 80, particles, texts);
                            player.healOnKill(Config.SnakeConfig.calcKillHeal(Config.SnakeConfig.KILL_HEAL_CROCODILE_FIXED, Config.SnakeConfig.KILL_HEAL_CROCODILE_RATIO, player.getMaxHealth()), particles, texts);
                            texts.spawn(croc.getX(), croc.getY() - 32, "★ DIỆT CÁ SẤU (+12 EXP)!", new Color(250, 204, 21), 2.5, 16);
                        }
                        hit = true;
                        break;
                    }
                }
            }

            if (!hit) {
                // Đạn trúng con mồi (Mở rộng phạm vi trúng mồi lên 36px)
                for (Prey prey : preys) {
                    if (!prey.isAlive()) continue;
                    if (Math.hypot(bolt.getX() - prey.getX(), bolt.getY() - prey.getY()) < 36.0) {
                        prey.setAlive(false);
                        int preyExp = prey.getEnergyValue();
                        int preyFood = prey.getFoodValue();
                        player.addExp(preyExp, preyFood, particles, texts);
                        int preyHeal = (prey.getType() == Prey.Type.BUTTERFLY)
                                ? Config.SnakeConfig.calcKillHeal(Config.SnakeConfig.KILL_HEAL_PREY_BUTTERFLY_FIXED, Config.SnakeConfig.KILL_HEAL_PREY_BUTTERFLY_RATIO, player.getMaxHealth())
                                : Config.SnakeConfig.calcKillHeal(Config.SnakeConfig.KILL_HEAL_PREY_NORMAL_FIXED, Config.SnakeConfig.KILL_HEAL_PREY_NORMAL_RATIO, player.getMaxHealth());
                        player.healOnKill(preyHeal, particles, texts);
                        texts.spawn(prey.getX(), prey.getY() - 16, "☠ BẮN HẠ MỒI (+" + preyExp + " EXP)!", new Color(74, 222, 128), 1.4, 14);
                        hit = true;
                        break;
                    }
                }
            }

            if (hit) {
                // Hiệu ứng Nổ Lan Độc Tố Diện Rộng (Venom Splash AoE bán kính 68px)
                particles.spawnPoisonSplash(bolt.getX(), bolt.getY());
                double splashR = 68.0;

                // Quét nổ lan: Tiêu diệt toàn bộ con mồi/đàn cá xung quanh trong bán kính nổ
                for (Prey extraPrey : preys) {
                    if (!extraPrey.isAlive()) continue;
                    if (Math.hypot(bolt.getX() - extraPrey.getX(), bolt.getY() - extraPrey.getY()) <= splashR) {
                        extraPrey.setAlive(false);
                        int pExp = extraPrey.getEnergyValue();
                        int pFood = extraPrey.getFoodValue();
                        player.addExp(pExp, pFood, particles, texts);
                        int extraPreyHeal = (extraPrey.getType() == Prey.Type.BUTTERFLY)
                                ? Config.SnakeConfig.calcKillHeal(Config.SnakeConfig.KILL_HEAL_PREY_BUTTERFLY_FIXED, Config.SnakeConfig.KILL_HEAL_PREY_BUTTERFLY_RATIO, player.getMaxHealth())
                                : Config.SnakeConfig.calcKillHeal(Config.SnakeConfig.KILL_HEAL_PREY_NORMAL_FIXED, Config.SnakeConfig.KILL_HEAL_PREY_NORMAL_RATIO, player.getMaxHealth());
                        player.healOnKill(extraPreyHeal, particles, texts);
                        texts.spawn(extraPrey.getX(), extraPrey.getY() - 16, "☠ TRÚNG ĐỘC LAN (+" + pExp + " EXP)!", new Color(74, 222, 128), 1.3, 14);
                    }
                }

                // Gây hiệu ứng trúng độc lan lên Rắn AI lân cận
                for (AISnake extraAI : aiSnakes) {
                    if (!extraAI.isAlive()) continue;
                    if (Math.hypot(bolt.getX() - extraAI.getX(), bolt.getY() - extraAI.getY()) <= splashR) {
                        extraAI.inflictPoison(Config.SnakeConfig.VENOM_DOT_DURATION, Config.SnakeConfig.VENOM_DOT_DMG);
                    }
                }

                // Gây hiệu ứng trúng độc lan lên Cá sấu lân cận
                for (Crocodile extraCroc : crocodiles) {
                    if (!extraCroc.isAlive()) continue;
                    if (Math.hypot(bolt.getX() - extraCroc.getX(), bolt.getY() - extraCroc.getY()) <= splashR) {
                        extraCroc.inflictPoison(Config.SnakeConfig.VENOM_DOT_DURATION, Config.SnakeConfig.VENOM_DOT_DMG);
                    }
                }

                it.remove();
            }
        }

        // 13. Cập nhật hạt & chữ bay
        particles.update(dt);
        texts.update(dt);

        // Kiểm tra Game Over / Defeat
        if (player.getHealth() <= 0) {
            gameState = GameState.DEFEAT;
            SoundManager.playGameOver();
        }
    }

    private void applyLevelUpUpgrade(int option) {
        if (option < 1 || option > 3) return;

        if (option == 1) {
            player.upgradeBaseSpeed(25.0);
            texts.spawn(player.getX(), player.getY() - 40, "⚡ TỐC ĐỘ CƠ BẢN +25 PX/S!", new Color(56, 189, 248), 2.5, 18);
        } else if (option == 2) {
            player.upgradeSkillDamage(15);
            texts.spawn(player.getX(), player.getY() - 40, "⚔ DAME CHIÊU THỨC +15 DAME!", new Color(192, 132, 252), 2.5, 18);
        } else if (option == 3) {
            player.upgradeMaxHealth(60);
            texts.spawn(player.getX(), player.getY() - 40, "♥ MAX MÁU +60 (HỒI +35 HP)!", new Color(74, 222, 128), 2.5, 18);
        }

        particles.spawnEvolutionAura(player.getX(), player.getY(), new Color(250, 204, 21));
        SoundManager.playEvolve();
        player.setPendingLevelUpChoice(false);
        isLevelUpSelecting = false;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        int panelW = getWidth();
        int panelH = getHeight();
        if (panelW <= 0 || panelH <= 0) return;

        int reqLogicW = (int) Math.ceil(panelW / CAMERA_ZOOM);
        int reqLogicH = (int) Math.ceil(panelH / CAMERA_ZOOM);
        checkAndResizeBuffer(reqLogicW, reqLogicH);

        if (camera != null && (camera.getViewportWidth() != logicWidth || camera.getViewportHeight() != logicHeight)) {
            camera.resize(logicWidth, logicHeight);
        }

        Color naturalBgColor = (currentStage == 2)
            ? new Color(2, 132, 199)
            : new Color(47, 98, 62);

        switch (gameState) {
            case MAIN_MENU -> {
                g2d.setColor(Color.BLACK);
                g2d.fillRect(0, 0, panelW, panelH);
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                renderer.renderMainMenu(g2d, panelW, panelH, mouseScreenPos, menuAnimTime);
            }
            case STAGE_SELECT -> {
                g2d.setColor(Color.BLACK);
                g2d.fillRect(0, 0, panelW, panelH);
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                renderer.renderStageSelect(g2d, panelW, panelH, mouseScreenPos, menuAnimTime);
            }
            case HOW_TO_PLAY -> {
                g2d.setColor(Color.BLACK);
                g2d.fillRect(0, 0, panelW, panelH);
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                renderer.renderMainMenu(g2d, panelW, panelH, mouseScreenPos, menuAnimTime);
                renderer.renderHowToPlayModal(g2d, panelW, panelH, mouseScreenPos);
            }
            case PLAYING, PAUSED, VICTORY, DEFEAT -> {
                if (logicBuffer == null || logicGraphics == null) {
                    checkAndResizeBuffer(reqLogicW, reqLogicH);
                }

                if (cachedActiveChunks.isEmpty()) {
                    cachedActiveChunks = world.updateActiveChunks(camera, 0);
                }

                logicGraphics.setColor(naturalBgColor);
                logicGraphics.fillRect(0, 0, logicWidth, logicHeight);

                renderer.renderWorldLayers(
                    logicGraphics, logicWidth, logicHeight, camera,
                    cachedActiveChunks, player, aiSnakes, bosses, preys,
                    crocodiles, eagle, poisonBolts, particles, texts,
                    FIXED_TIME_STEP, currentStage
                );

                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

                if (panelW == logicWidth && panelH == logicHeight) {
                    g2d.drawImage(logicBuffer, 0, 0, null);
                } else {
                    g2d.drawImage(
                        logicBuffer,
                        0, 0, panelW, panelH,
                        0, 0, logicWidth, logicHeight,
                        null
                    );
                }

                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                renderer.renderHUDLayers(g2d, panelW, panelH, camera, player, aiSnakes, bosses, preys);
                renderer.renderStageObjectivesHUD(g2d, panelW, panelH, currentStage, bearDefeated, pythonDefeated, octopusDefeated, swampKingDefeated, bosses, player.getEvolutionLevel());

                if (isLevelUpSelecting) {
                    renderer.renderLevelUpModal(g2d, panelW, panelH, mouseScreenPos);
                } else if (gameState == GameState.PAUSED) {
                    renderer.renderPauseMenu(g2d, panelW, panelH, mouseScreenPos, pauseMenuIndex, currentStage, stagePlayTime, player.getScore(), player.getEvolutionLevel());
                } else if (gameState == GameState.VICTORY) {
                    renderer.renderVictoryScreen(g2d, panelW, panelH, mouseScreenPos, stagePlayTime, player.getScore(), player.getEvolutionLevel(), enemiesKilledCount, menuAnimTime, currentStage);
                } else if (gameState == GameState.DEFEAT) {
                    renderer.renderDefeatScreen(g2d, panelW, panelH, mouseScreenPos, stagePlayTime, player.getScore(), player.getEvolutionLevel(), bearDefeated, pythonDefeated, octopusDefeated, swampKingDefeated, menuAnimTime, currentStage);
                }
            }
        }

        Toolkit.getDefaultToolkit().sync();
    }

    // ==========================================
    // XỬ LÝ SỰ KIỆN PHÍM BẤM & CHUỘT
    // ==========================================
    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();

        // Thoát hoặc quay về Menu bằng phím ESC
        if (code == KeyEvent.VK_ESCAPE) {
            if (gameState == GameState.HOW_TO_PLAY || gameState == GameState.STAGE_SELECT) {
                SoundManager.playButtonClick();
                gameState = GameState.MAIN_MENU;
                return;
            } else if (gameState == GameState.PLAYING) {
                SoundManager.playButtonClick();
                gameState = GameState.PAUSED;
                pauseMenuIndex = 0;
                return;
            } else if (gameState == GameState.PAUSED) {
                SoundManager.playButtonClick();
                gameState = GameState.PLAYING;
                return;
            } else if (gameState == GameState.VICTORY || gameState == GameState.DEFEAT) {
                SoundManager.playButtonClick();
                gameState = GameState.MAIN_MENU;
                return;
            }
        }

        if (gameState == GameState.PLAYING) {
            // Xử lý lựa chọn thẻ bài nâng cấp khi lên cấp [1], [2], [3]
            if (isLevelUpSelecting) {
                if (code == KeyEvent.VK_1 || code == KeyEvent.VK_NUMPAD1) {
                    applyLevelUpUpgrade(1);
                } else if (code == KeyEvent.VK_2 || code == KeyEvent.VK_NUMPAD2) {
                    applyLevelUpUpgrade(2);
                } else if (code == KeyEvent.VK_3 || code == KeyEvent.VK_NUMPAD3) {
                    applyLevelUpUpgrade(3);
                }
                return;
            }

            if (code == KeyEvent.VK_SPACE) {
                player.activateDash();
            } else if (code == KeyEvent.VK_SHIFT) {
                player.setSprinting(true);
            } else if (code == KeyEvent.VK_Q) {
                // Chiêu 1: PHUN ĐỘC (Venom Spit)
                player.shootVenomSpit(poisonBolts, texts, particles);
            } else if (code == KeyEvent.VK_E || code == KeyEvent.VK_F) {
                // Chiêu 2: RUNG ĐUÔI (Rattle Tail Shockwave)
                player.rattleTail(aiSnakes, bosses, preys, crocodiles, particles, texts, camera);
            } else if (code == KeyEvent.VK_P) {
                SoundManager.playButtonClick();
                gameState = GameState.PAUSED;
                pauseMenuIndex = 0;
            }

            // Hỗ trợ điều khiển bằng phím mũi tên / WASD
            if (code == KeyEvent.VK_UP || code == KeyEvent.VK_W) {
                player.setTargetAngle(-Math.PI / 2);
                useMouse = false;
            } else if (code == KeyEvent.VK_DOWN || code == KeyEvent.VK_S) {
                player.setTargetAngle(Math.PI / 2);
                useMouse = false;
            } else if (code == KeyEvent.VK_LEFT || code == KeyEvent.VK_A) {
                player.setTargetAngle(Math.PI);
                useMouse = false;
            } else if (code == KeyEvent.VK_RIGHT || code == KeyEvent.VK_D) {
                player.setTargetAngle(0);
                useMouse = false;
            }
        } else if (gameState == GameState.PAUSED) {
            // Điều hướng menu bằng phím mũi tên hoặc W/S
            if (code == KeyEvent.VK_UP || code == KeyEvent.VK_W) {
                pauseMenuIndex = (pauseMenuIndex - 1 + 4) % 4;
                SoundManager.playButtonClick();
            } else if (code == KeyEvent.VK_DOWN || code == KeyEvent.VK_S) {
                pauseMenuIndex = (pauseMenuIndex + 1) % 4;
                SoundManager.playButtonClick();
            } else if (code == KeyEvent.VK_ENTER) {
                triggerPauseMenuAction(pauseMenuIndex + 1);
            } else if (code == KeyEvent.VK_P || code == KeyEvent.VK_SPACE) {
                triggerPauseMenuAction(LayeredRenderer.PAUSE_BTN_RESUME);
            } else if (code == KeyEvent.VK_R) {
                triggerPauseMenuAction(LayeredRenderer.PAUSE_BTN_RESTART);
            } else if (code == KeyEvent.VK_M) {
                triggerPauseMenuAction(LayeredRenderer.PAUSE_BTN_CHANGE_MAP);
            } else if (code == KeyEvent.VK_Q) {
                triggerPauseMenuAction(LayeredRenderer.PAUSE_BTN_EXIT);
            }
        } else if (gameState == GameState.VICTORY || gameState == GameState.DEFEAT) {
            if (code == KeyEvent.VK_R || code == KeyEvent.VK_SPACE) {
                startStage(currentStage);
            } else if (code == KeyEvent.VK_M) {
                gameState = GameState.MAIN_MENU;
            }
        }
    }

    private void triggerPauseMenuAction(int action) {
        switch (action) {
            case LayeredRenderer.PAUSE_BTN_RESUME -> {
                SoundManager.playButtonClick();
                gameState = GameState.PLAYING;
            }
            case LayeredRenderer.PAUSE_BTN_RESTART -> {
                SoundManager.playButtonClick();
                startStage(currentStage);
            }
            case LayeredRenderer.PAUSE_BTN_CHANGE_MAP -> {
                SoundManager.playButtonClick();
                gameState = GameState.STAGE_SELECT;
            }
            case LayeredRenderer.PAUSE_BTN_EXIT -> {
                SoundManager.playButtonClick();
                gameState = GameState.MAIN_MENU;
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
            player.setSprinting(false);
        }
    }

    @Override public void keyTyped(KeyEvent e) {}

    @Override
    public void mouseMoved(MouseEvent e) {
        mouseScreenPos = e.getPoint();
        useMouse = true;
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        mouseScreenPos = e.getPoint();
        useMouse = true;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        int w = getWidth();
        int h = getHeight();
        Point pt = e.getPoint();

        if (e.getButton() == MouseEvent.BUTTON1) {
            switch (gameState) {
                case MAIN_MENU -> {
                    int btn = renderer.getHoveredMainMenuButton(w, h, pt);
                    if (btn == LayeredRenderer.MENU_BTN_PLAY) {
                        SoundManager.playButtonClick();
                        startStage(1);
                    } else if (btn == LayeredRenderer.MENU_BTN_STAGE) {
                        SoundManager.playButtonClick();
                        gameState = GameState.STAGE_SELECT;
                    } else if (btn == LayeredRenderer.MENU_BTN_GUIDE) {
                        SoundManager.playButtonClick();
                        gameState = GameState.HOW_TO_PLAY;
                    } else if (btn == LayeredRenderer.MENU_BTN_EXIT) {
                        SoundManager.playButtonClick();
                        System.exit(0);
                    }
                    return;
                }
                case STAGE_SELECT -> {
                    int card = renderer.getHoveredStageCard(w, h, pt);
                    if (card == 1) {
                        SoundManager.playButtonClick();
                        startStage(1);
                    } else if (card == 2) {
                        SoundManager.playButtonClick();
                        startStage(2);
                    } else if (card == 99) {
                        SoundManager.playButtonClick();
                        gameState = GameState.MAIN_MENU;
                    }
                    return;
                }
                case HOW_TO_PLAY -> {
                    if (renderer.isHoveredHowToPlayClose(w, h, pt)) {
                        SoundManager.playButtonClick();
                        gameState = GameState.MAIN_MENU;
                    }
                    return;
                }
                case VICTORY -> {
                    int btn = renderer.getHoveredVictoryButton(w, h, pt);
                    if (btn == 1) {
                        SoundManager.playButtonClick();
                        startStage(currentStage);
                    } else if (btn == 2) {
                        SoundManager.playButtonClick();
                        gameState = GameState.STAGE_SELECT;
                    } else if (btn == 3) {
                        SoundManager.playButtonClick();
                        gameState = GameState.MAIN_MENU;
                    }
                    return;
                }
                case DEFEAT -> {
                    int btn = renderer.getHoveredDefeatButton(w, h, pt);
                    if (btn == 1) {
                        SoundManager.playButtonClick();
                        startStage(currentStage);
                    } else if (btn == 2) {
                        SoundManager.playButtonClick();
                        gameState = GameState.STAGE_SELECT;
                    } else if (btn == 3) {
                        SoundManager.playButtonClick();
                        gameState = GameState.MAIN_MENU;
                    }
                    return;
                }
                case PLAYING -> {
                    if (isLevelUpSelecting) {
                        int opt = renderer.getHoveredCardOption(w, h, pt);
                        if (opt >= 1 && opt <= 3) {
                            applyLevelUpUpgrade(opt);
                        }
                        return;
                    }
                    player.setSprinting(true); // Giữ chuột trái để tăng tốc
                    useMouse = true;
                }
                case PAUSED -> {
                    int btn = renderer.getHoveredPauseButton(w, h, pt);
                    if (btn != 0) {
                        triggerPauseMenuAction(btn);
                    }
                }
            }
        } else if (e.getButton() == MouseEvent.BUTTON3 && gameState == GameState.PLAYING && !isLevelUpSelecting) {
            // Chuột phải: Kích hoạt PHUN ĐỘC
            player.shootVenomSpit(poisonBolts, texts, particles);
            useMouse = true;
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            player.setSprinting(false);
        }
    }

    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}
}
