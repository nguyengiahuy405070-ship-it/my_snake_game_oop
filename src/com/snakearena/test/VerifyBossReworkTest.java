package com.snakearena.test;

import com.snakearena.core.Camera;
import com.snakearena.core.Config;
import com.snakearena.core.GamePanel;
import com.snakearena.entity.*;
import com.snakearena.render.FloatingTextManager;
import com.snakearena.render.LayeredRenderer;
import com.snakearena.render.ParticleSystem;
import com.snakearena.world.Chunk;
import com.snakearena.world.ChunkManager;
import com.snakearena.world.WorldProp;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Kiểm thử toàn diện 3 tính năng mới của hệ thống Boss:
 * 1. Khoảng nghỉ 2.0s (2000ms) đứng yên không tấn công + hồi Mana áp dụng cho TẤT CẢ Boss.
 * 2. Phân tách rõ Boss theo 2 Map: Map 1 (Forest: BEAR, GIANT_PYTHON) và Map 2 (Ocean: OCTOPUS, SWAMP_KING).
 * 3. Bộ kỹ năng mới & hiệu ứng chuyển động thân cho Boss dạng Rắn (GIANT_PYTHON / VOID_DRAGON).
 */
public class VerifyBossReworkTest {

    private static final String ARTIFACT_DIR = "C:/Users/Gia Huy/.gemini/antigravity/brain/654b6c66-ba54-4cb1-9fa5-1e908db71677";

    private static void check(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError("FAILED: " + msg);
        }
    }

    private static void setField(Object target, String name, Object val) throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, val);
                return;
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object getField(Object target, String name) throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void setupSnakeSegments(Boss boss, double startX, double startY, double baseAngle, int segCount, double anim) {
        boss.setX(startX);
        boss.setY(startY);
        boss.setAngle(baseAngle);
        boss.setAnimTimer(anim);
        List<SnakeSegment> segs = boss.getSegments();
        segs.clear();
        double cx = startX;
        double cy = startY;
        double segDist = (boss.getType() == Boss.Type.VOID_DRAGON) ? 24.0 : 22.0;
        for (int i = 0; i < segCount; i++) {
            double ang = baseAngle + Math.sin(i * 0.30 + anim * 1.6) * 0.25;
            segs.add(new SnakeSegment(cx, cy, ang));
            cx -= Math.cos(ang) * segDist;
            cy -= Math.sin(ang) * segDist;
        }
    }

    /**
     * TEST 1: Kiểm tra mọi Boss đều có trạng thái nghỉ đúng 2.0 giây (2000ms) đứng yên, không tấn công, hồi Mana.
     */
    private static void testUniversalTwoSecondRecoveryState() throws Exception {
        Camera cam = new Camera(1024, 720);
        ParticleSystem ps = new ParticleSystem();
        FloatingTextManager ft = new FloatingTextManager();

        for (Boss.Type t : Boss.Type.values()) {
            PlayerSnake player = new PlayerSnake(18.0, 0.0); // Đứng rất gần ngay sát Boss để test "không tấn công"
            setField(player, "evolutionLevel", 1);
            Boss boss = new Boss(t, 0.0, 0.0);

            check(boss.getRestDurationMs() == 2000L, t + " restDurationMs must be 2000");
            check(Math.abs(boss.getRestDuration() - 2.0) < 1e-9, t + " restDuration must be 2.0s");
            check(Math.abs(boss.getMaxMana() - Config.BossConfig.BOSS_MAX_MANA) < 1e-9, t + " maxMana");

            // Hạ Mana xuống 15.0 rồi đưa Boss vào trạng thái nghỉ sau đòn
            boss.setMana(15.0);
            boss.enterRecoveryState();

            check(boss.isRecovering(), t + " must enter recovering state");
            check(boss.getActionState() == Boss.BossActionState.RECOVERING, t + " actionState RECOVERING");
            check(Math.abs(boss.getRecoveryTimer() - 2.0) < 1e-9, t + " recoveryTimer starts at 2.0s");

            double startX = boss.getX();
            double startY = boss.getY();
            int startHp = player.getHealth();
            List<PoisonBolt> bolts = new ArrayList<>();

            // Mô phỏng 1.90 giây (38 x 0.05s): vẫn đứng yên, không gây sát thương, hồi Mana liên tục
            for (int step = 0; step < 38; step++) {
                boss.update(0.05, player, null, bolts, cam, ps, ft);
                check(boss.isRecovering(), t + " must still be recovering before 2.0s at step " + step);
                check(Math.abs(boss.getX() - startX) < 1e-6 && Math.abs(boss.getY() - startY) < 1e-6,
                        t + " must stand completely still during 2s recovery");
                check(player.getHealth() == startHp, t + " must NOT attack player during 2s recovery");
                check(bolts.isEmpty(), t + " must NOT fire projectiles during 2s recovery");
            }

            // 1.90s * 35 MP/s = +66.5 MP -> Mana từ 15.0 lên ~81.5
            check(boss.getMana() > 78.0 && boss.getMana() <= boss.getMaxMana(),
                    t + " Mana should regenerate during recovery, got " + boss.getMana());

            // Vượt mốc 2.0s (thêm 0.15s -> tổng 2.05s): thoát trạng thái nghỉ, hoạt động bình thường
            boss.update(0.15, player, null, bolts, cam, ps, ft);
            check(!boss.isRecovering(), t + " must exit recovery state after 2.0s elapsed");
            check(boss.getActionState() == Boss.BossActionState.ACTIVE, t + " actionState ACTIVE after recovery");
            check(boss.getRecoveryTimer() == 0.0, t + " recoveryTimer reset to 0.0");
        }

        // Kiểm tra thêm Boss Biển (OCTOPUS & SWAMP_KING) tự động vào 2.0s recovery ngay sau khi tung kỹ năng tầm trung
        {
            PlayerSnake p = new PlayerSnake(240.0, 0.0);
            setField(p, "evolutionLevel", 1);
            Boss octo = new Boss(Boss.Type.OCTOPUS, 0.0, 0.0);
            List<PoisonBolt> octoBolts = new ArrayList<>();
            octo.update(0.016, p, null, octoBolts, cam, ps, ft);
            check(octoBolts.size() == 3, "OCTOPUS Ink Volley spawns 3 hostile bolts, got " + octoBolts.size());
            check(octo.isRecovering() && Math.abs(octo.getRecoveryTimer() - 2.0) < 0.05,
                    "OCTOPUS must immediately enter 2.0s recovery after casting Ink Volley");

            Boss swamp = new Boss(Boss.Type.SWAMP_KING, 0.0, 0.0);
            List<PoisonBolt> swampBolts = new ArrayList<>();
            swamp.update(0.016, p, null, swampBolts, cam, ps, ft);
            check(swampBolts.size() == 3, "SWAMP_KING Hydro Volley spawns 3 hostile bolts, got " + swampBolts.size());
            check(swamp.isRecovering() && Math.abs(swamp.getRecoveryTimer() - 2.0) < 0.05,
                    "SWAMP_KING must immediately enter 2.0s recovery after casting Hydro Surge Volley");
        }

        System.out.println("[PASS] Test 1: All 5 Bosses enforce exact 2.0s (2000ms) stationary Mana recovery!");
    }

    /**
     * TEST 2: Kiểm tra phân chia Boss theo Map (Map 1 Forest vs Map 2 Ocean) ở World + GamePanel.
     */
    private static void testMapBossSeparation() throws Exception {
        // 2.1 ChunkManager Map 1 (Forest)
        ChunkManager map1 = new ChunkManager(1);
        check(map1.getBiomeType() == Boss.BiomeType.FOREST, "Map 1 biome must be FOREST");
        Boss.Type[] m1Types = map1.getAllowedBossTypes();
        check(Arrays.asList(m1Types).contains(Boss.Type.BEAR) && Arrays.asList(m1Types).contains(Boss.Type.GIANT_PYTHON),
                "Map 1 roster must contain BEAR & GIANT_PYTHON");
        check(m1Types.length == 2, "Map 1 roster should have exactly 2 forest bosses");
        check(map1.canSpawnBoss(Boss.Type.BEAR) && map1.canSpawnBoss(Boss.Type.GIANT_PYTHON), "Map 1 allows forest bosses");
        check(!map1.canSpawnBoss(Boss.Type.OCTOPUS) && !map1.canSpawnBoss(Boss.Type.SWAMP_KING) && !map1.canSpawnBoss(Boss.Type.VOID_DRAGON),
                "Map 1 must reject ocean and volcano bosses");
        check(map1.createBossForStage(Boss.Type.OCTOPUS, 0, 0) == null, "Map 1 createBoss OCTOPUS must return null");
        check(map1.createBossForStage(Boss.Type.SWAMP_KING, 0, 0) == null, "Map 1 createBoss SWAMP_KING must return null");
        check(map1.createBossForStage(Boss.Type.VOID_DRAGON, 0, 0) == null, "Map 1 createBoss VOID_DRAGON must return null");

        Boss bearSpawn = map1.createBossForStage(Boss.Type.BEAR, 0, 0);
        check(bearSpawn != null && bearSpawn.getType() == Boss.Type.BEAR && bearSpawn.getBiome() == Boss.BiomeType.FOREST,
                "Map 1 spawn BEAR succeeds with FOREST biome");
        check(!map1.checkCollision(bearSpawn.getX(), bearSpawn.getY(), 24.0).collided,
                "Map 1 spawned boss must not overlap solid wall collision");

        // 2.2 ChunkManager Map 2 (Ocean)
        ChunkManager map2 = new ChunkManager(2);
        check(map2.getBiomeType() == Boss.BiomeType.OCEAN, "Map 2 biome must be OCEAN");
        Boss.Type[] m2Types = map2.getAllowedBossTypes();
        check(Arrays.asList(m2Types).contains(Boss.Type.OCTOPUS) && Arrays.asList(m2Types).contains(Boss.Type.SWAMP_KING),
                "Map 2 roster must contain OCTOPUS & SWAMP_KING");
        check(m2Types.length == 2, "Map 2 roster should have exactly 2 ocean bosses");
        check(map2.canSpawnBoss(Boss.Type.OCTOPUS) && map2.canSpawnBoss(Boss.Type.SWAMP_KING), "Map 2 allows ocean bosses");
        check(!map2.canSpawnBoss(Boss.Type.BEAR) && !map2.canSpawnBoss(Boss.Type.GIANT_PYTHON) && !map2.canSpawnBoss(Boss.Type.VOID_DRAGON),
                "Map 2 must reject forest and volcano bosses");
        check(map2.createBossForStage(Boss.Type.BEAR, 0, 0) == null, "Map 2 createBoss BEAR must return null");
        check(map2.createBossForStage(Boss.Type.GIANT_PYTHON, 0, 0) == null, "Map 2 createBoss GIANT_PYTHON must return null");
        check(map2.createBossForStage(Boss.Type.VOID_DRAGON, 0, 0) == null, "Map 2 createBoss VOID_DRAGON must return null");

        Boss octoSpawn = map2.createBossForStage(Boss.Type.OCTOPUS, 0, 0);
        check(octoSpawn != null && octoSpawn.getType() == Boss.Type.OCTOPUS && octoSpawn.getBiome() == Boss.BiomeType.OCEAN,
                "Map 2 spawn OCTOPUS succeeds with OCEAN biome");
        check(!map2.checkCollision(octoSpawn.getX(), octoSpawn.getY(), 24.0).collided,
                "Map 2 spawned boss must not overlap reef core collision");

        // 2.3 GamePanel Integration: Lên cấp 2..5 ở Map 1 và Map 2 chỉ sinh Boss đúng Map
        Method mSpawn = GamePanel.class.getDeclaredMethod("spawnLevelBoss", int.class);
        mSpawn.setAccessible(true);

        GamePanel panel1 = new GamePanel();
        panel1.stopGameLoop();
        panel1.startStage(1);
        for (int lv = 2; lv <= 5; lv++) {
            mSpawn.invoke(panel1, lv);
        }
        @SuppressWarnings("unchecked")
        List<Boss> stage1Bosses = (List<Boss>) getField(panel1, "bosses");
        check(stage1Bosses.size() == 2, "Map 1 levels 2..5 must spawn only 2 forest bosses, got " + stage1Bosses.size());
        for (Boss b : stage1Bosses) {
            check(Boss.isBossAllowedInStage(b.getType(), 1),
                    "Map 1 spawned forbidden boss: " + b.getType());
        }

        GamePanel panel2 = new GamePanel();
        panel2.stopGameLoop();
        panel2.startStage(2);
        for (int lv = 2; lv <= 5; lv++) {
            mSpawn.invoke(panel2, lv);
        }
        @SuppressWarnings("unchecked")
        List<Boss> stage2Bosses = (List<Boss>) getField(panel2, "bosses");
        check(stage2Bosses.size() == 2, "Map 2 levels 2..5 must spawn only 2 ocean bosses, got " + stage2Bosses.size());
        for (Boss b : stage2Bosses) {
            check(Boss.isBossAllowedInStage(b.getType(), 2),
                    "Map 2 spawned forbidden boss: " + b.getType());
        }

        System.out.println("[PASS] Test 2: Map 1 (Forest) & Map 2 (Ocean) Boss rosters cleanly separated across ChunkManager & GamePanel!");
    }

    /**
     * TEST 3: Bộ kỹ năng chuyên biệt của Boss dạng Rắn (GIANT_PYTHON & VOID_DRAGON), đạn độc Boss và choáng ngắt chiêu.
     */
    private static void testSnakeBossSkillsAndProjectiles() throws Exception {
        Camera cam = new Camera(1024, 720);
        ParticleSystem ps = new ParticleSystem();
        FloatingTextManager ft = new FloatingTextManager();

        // 3.1 Chu kỳ kỹ năng 1: ROAR_WINDUP -> VENOM_VOLLEY (bắn 3 tia, enraged -> 5 tia) -> VENOM_SPIT -> RECOVER 2.0s
        PlayerSnake player = new PlayerSnake(250.0, 0.0);
        setField(player, "evolutionLevel", 1);
        Boss python = new Boss(Boss.Type.GIANT_PYTHON, 0.0, 0.0);
        List<PoisonBolt> bolts = new ArrayList<>();

        python.update(0.016, player, null, bolts, cam, ps, ft);
        check(python.getSnakeState() == Boss.SnakeBossState.ROAR_WINDUP,
                "Python at 250px with full mana must enter ROAR_WINDUP");
        check(python.getSnakeSkillType() == Boss.SnakeSkillType.VENOM_VOLLEY,
                "First skill cycle must be VENOM_VOLLEY");

        // Đứng yên gầm tụ lực trong ROAR_WINDUP
        double wx = python.getX(), wy = python.getY();
        python.update(0.20, player, null, bolts, cam, ps, ft);
        check(Math.abs(python.getX() - wx) < 1e-6 && Math.abs(python.getY() - wy) < 1e-6,
                "Python stands still while channeling ROAR_WINDUP");
        check(bolts.isEmpty(), "No bolts fired before roar ends");

        // Hoàn tất roar (tổng ~0.68s >= 0.65s) -> bắn 3 đạn độc tím thù địch
        python.update(0.48, player, null, bolts, cam, ps, ft);
        check(python.getSnakeState() == Boss.SnakeBossState.VENOM_SPIT, "Transitions to VENOM_SPIT");
        check(bolts.size() == 3, "Normal VENOM_VOLLEY shoots 3 bolts, got " + bolts.size());
        for (PoisonBolt b : bolts) {
            check(b.isHostileToPlayer(), "Python poison bolt must be hostileToPlayer=true");
            check(b.getDamage() == Config.BossConfig.PYTHON_VENOM_DAMAGE, "Venom damage matches config");
        }
        check(Math.abs(python.getMana() - (100.0 - Config.BossConfig.PYTHON_VENOM_MANA_COST)) < 1e-5,
                "Mana deducted after venom volley");

        // Kết thúc giật lùi phun độc (0.35s) -> tự động vào 2.0s recovery
        python.update(0.40, player, null, bolts, cam, ps, ft);
        check(python.isRecovering() && Math.abs(python.getRecoveryTimer() - 2.0) < 0.05,
                "After VENOM_SPIT recoil, Python must enter 2.0s Mana recovery");

        // Kết thúc đúng 2.0s recovery trước, sau đó ẩn người chơi tạm thời để Python không cắn cận chiến trong lúc đếm nốt skillCooldown (2.4s)
        for (int i = 0; i < 42; i++) {
            python.update(0.05, player, null, bolts, cam, ps, ft);
        }
        check(!python.isRecovering(), "Python finishes 2.0s recovery");
        setField(player, "hidden", true);
        for (int i = 0; i < 50; i++) {
            python.update(0.05, player, null, bolts, cam, ps, ft);
        }
        setField(player, "hidden", false);
        check(!python.isRecovering() && python.getSkillCooldown() == 0.0, "Ready for skill cycle 2");

        // 3.2 Chu kỳ kỹ năng 2: ROAR_WINDUP -> TAIL_WHIP (nếu gần đuôi) + COIL_DASH kéo theo toàn bộ 20 đốt thân -> 2.0s recovery
        python.setMana(100.0);
        player.teleportTo(python.getX() + 180.0, python.getY());
        python.update(0.016, player, null, bolts, cam, ps, ft);
        check(python.getSnakeState() == Boss.SnakeBossState.ROAR_WINDUP, "Cycle 2 enters ROAR_WINDUP");
        check(python.getSnakeSkillType() == Boss.SnakeSkillType.COIL_DASH_WHIP, "Cycle 2 picks COIL_DASH_WHIP");

        // Đưa người chơi lại gần đuôi ngay trước lúc kích hoạt đòn quẫy đuôi
        SnakeSegment tailSeg = python.getSegments().get(python.getSegments().size() - 1);
        player.teleportTo(tailSeg.x + 25.0, tailSeg.y);
        int hpBeforeWhip = player.getHealth();
        python.update(0.70, player, null, bolts, cam, ps, ft);
        check(python.getSnakeState() == Boss.SnakeBossState.COIL_DASH, "Transitions to COIL_DASH");
        check(python.getTailWhipFxTimer() >= 0.0, "Tail whip FX timer must start");
        check(player.getHealth() == hpBeforeWhip - Config.BossConfig.PYTHON_TAIL_WHIP_DAMAGE,
                "Tail whip must deal " + Config.BossConfig.PYTHON_TAIL_WHIP_DAMAGE + " dmg near tail");

        // Cập nhật COIL_DASH: đầu và đuôi đều được kéo tiến tới mượt mà
        double prevHeadX = python.getX();
        double prevTailX = python.getSegments().get(python.getSegments().size() - 1).x;
        for (int i = 0; i < 8; i++) {
            python.update(0.04, player, null, bolts, cam, ps, ft);
        }
        check(python.getX() > prevHeadX + 40.0, "COIL_DASH moves head fast forward");
        check(python.getSegments().get(python.getSegments().size() - 1).x > prevTailX + 5.0,
                "COIL_DASH pulls body & tail segments via rope physics");

        // Hoàn tất COIL_DASH -> vào 2.0s recovery
        for (int i = 0; i < 10; i++) {
            python.update(0.04, player, null, bolts, cam, ps, ft);
            if (python.isRecovering()) break;
        }
        check(python.isRecovering(), "After COIL_DASH finishes, Python must enter 2.0s recovery");

        // 3.3 Kiểm tra va chạm đạn độc Boss trong GamePanel: bỏ qua người chơi đang ẩn nấp, trúng khi không ẩn
        GamePanel gp = new GamePanel();
        gp.stopGameLoop();
        gp.startStage(1);
        setField(gp, "gameState", GamePanel.GameState.PLAYING);
        PlayerSnake gpPlayer = (PlayerSnake) getField(gp, "player");
        setField(gpPlayer, "evolutionLevel", 1);
        setField(gpPlayer, "hidden", false);
        @SuppressWarnings("unchecked")
        List<PoisonBolt> gpBolts = (List<PoisonBolt>) getField(gp, "poisonBolts");
        @SuppressWarnings("unchecked")
        List<AISnake> gpAis = (List<AISnake>) getField(gp, "aiSnakes");
        gpAis.clear();

        Method mUpdate = GamePanel.class.getDeclaredMethod("updateGame", double.class);
        mUpdate.setAccessible(true);

        // Bắn đạn độc Boss ngay trên người chơi đang lộ diện
        gpBolts.add(new PoisonBolt(gpPlayer.getX(), gpPlayer.getY(), 0.0, 200.0, true, 22, 4, 1.2));
        int hpBeforeBolt = gpPlayer.getHealth();
        mUpdate.invoke(gp, 0.016);
        check(gpPlayer.getHealth() == hpBeforeBolt - 22,
                "Hostile boss PoisonBolt must hit unhidden player for 22 dmg, got diff=" + (hpBeforeBolt - gpPlayer.getHealth()));
        check(gpPlayer.isPoisoned(), "Hostile boss PoisonBolt inflicts poison DoT on player");
        check(gpBolts.isEmpty(), "Hostile boss PoisonBolt consumed after hitting player");

        // Kiểm tra choáng/bẫy ngắt kỹ năng Trăn Rắn + đồng bộ Boss Rồng
        ChunkManager trapWorld = new ChunkManager(1);
        Chunk tc = trapWorld.getOrCreateChunk(0, 0);
        tc.getProps().clear();
        tc.getProps().add(new WorldProp(WorldProp.Type.TRAP, 100.0, 100.0, 18.0, 32, 30));
        Boss trapPython = new Boss(Boss.Type.GIANT_PYTHON, 100.0, 100.0);
        player.teleportTo(300.0, 100.0);
        trapPython.update(0.016, player, trapWorld, bolts, cam, ps, ft);
        check(trapPython.getStunTimer() == 1.8, "Trap must stun Giant Python for 1.8s, got " + trapPython.getStunTimer());
        check(trapPython.getSnakeState() == Boss.SnakeBossState.CHASE, "Trap stun must interrupt skill state");

        System.out.println("[PASS] Test 3: Snake Boss skills (Venom Volley, Coil Dash + Tail Whip), Hostile Bolts & Stun interrupt verified!");
    }

    /**
     * TEST 4: Benchmark hiệu năng render & xuất ảnh preview 6 khung xác nhận hình ảnh trực quan.
     */
    private static void renderVisualPreviewSheetAndBenchmark() throws Exception {
        LayeredRenderer renderer = new LayeredRenderer();
        int panelW = 560;
        int panelH = 380;
        int cols = 3;
        int rows = 2;
        BufferedImage sheet = new BufferedImage(panelW * cols, panelH * rows, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        String[] labels = {
            "1. PYTHON ROAR WINDUP (Telegraph + Charge Pulse + Rattle)",
            "2. PYTHON VENOM VOLLEY (5x Enraged Purple Hostile Bolts)",
            "3. PYTHON COIL DASH + TAIL WHIP (Shockwave + Afterimage)",
            "4. PYTHON 2s MANA RECOVERY + STUN DOTS (Zero Tofu)",
            "5. MAP 2 OCEAN BOSSES (Octopus 2s Aura + Swamp King)",
            "6. BOSS HUD BANNER + MANA BAR + VOID DRAGON SKILL"
        };

        Camera cam = new Camera(panelW, panelH);
        cam.snapTo(0, 0);
        ChunkManager map1 = new ChunkManager(1);
        List<Chunk> m1Chunks = map1.updateActiveChunks(cam, 0.016);
        ChunkManager map2 = new ChunkManager(2);
        List<Chunk> m2Chunks = map2.updateActiveChunks(cam, 0.016);

        PlayerSnake player = new PlayerSnake(165, 25);
        List<AISnake> ais = Collections.emptyList();
        List<Prey> preys = Collections.emptyList();
        List<Crocodile> crocs = Collections.emptyList();
        ParticleSystem ps = new ParticleSystem();
        FloatingTextManager ft = new FloatingTextManager();

        for (int idx = 0; idx < 6; idx++) {
            int col = idx % cols;
            int row = idx / cols;
            int ox = col * panelW;
            int oy = row * panelH;

            BufferedImage sub = new BufferedImage(panelW, panelH, BufferedImage.TYPE_INT_RGB);
            Graphics2D sg = sub.createGraphics();
            sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

            ft.clear();
            ps.clear();
            List<PoisonBolt> bolts = new ArrayList<>();
            List<Boss> bossList = new ArrayList<>();

            if (idx == 0) {
                player.teleportTo(155, 10);
                Boss py = new Boss(Boss.Type.GIANT_PYTHON, 15, 10);
                setupSnakeSegments(py, 15, 10, 0.0, 14, 1.8);
                setField(py, "snakeState", Boss.SnakeBossState.ROAR_WINDUP);
                setField(py, "snakeSkillType", Boss.SnakeSkillType.COIL_DASH_WHIP);
                setField(py, "snakeSkillTime", 0.42);
                setField(py, "lockedTargetX", 175.0);
                setField(py, "lockedTargetY", 10.0);
                bossList.add(py);
                renderer.renderWorldLayers(sg, panelW, panelH, cam, m1Chunks, player, ais, bossList, preys, crocs, null, bolts, ps, ft, 0.016, 1);
            } else if (idx == 1) {
                player.teleportTo(190, 20);
                Boss py = new Boss(Boss.Type.GIANT_PYTHON, -30, 15);
                setupSnakeSegments(py, -30, 15, 0.05, 13, 2.4);
                setField(py, "health", 380); // Enraged
                setField(py, "snakeState", Boss.SnakeBossState.VENOM_SPIT);
                setField(py, "snakeSkillTime", 0.08);
                py.setMana(65.0);
                for (int b = -2; b <= 2; b++) {
                    double ang = 0.05 + b * 0.17;
                    bolts.add(new PoisonBolt(-4 + Math.cos(ang) * 45, 15 + Math.sin(ang) * 45, ang, 360.0, true, 22, 4, 1.3));
                }
                ft.spawn(-30, -76, "PHUN CHUM DOC TO!", new Color(192, 132, 252), 2.0, 15);
                bossList.add(py);
                renderer.renderWorldLayers(sg, panelW, panelH, cam, m1Chunks, player, ais, bossList, preys, crocs, null, bolts, ps, ft, 0.016, 1);
            } else if (idx == 2) {
                player.teleportTo(-175, 40);
                Boss py = new Boss(Boss.Type.GIANT_PYTHON, 60, -10);
                setupSnakeSegments(py, 60, -10, -0.08, 12, 3.1);
                setField(py, "snakeState", Boss.SnakeBossState.COIL_DASH);
                setField(py, "lungeDirX", 1.0);
                setField(py, "lungeDirY", -0.08);
                setField(py, "tailWhipFxX", py.getSegments().get(11).x);
                setField(py, "tailWhipFxY", py.getSegments().get(11).y);
                setField(py, "tailWhipFxTimer", 0.24);
                bossList.add(py);
                renderer.renderWorldLayers(sg, panelW, panelH, cam, m1Chunks, player, ais, bossList, preys, crocs, null, bolts, ps, ft, 0.016, 1);
            } else if (idx == 3) {
                player.teleportTo(190, 90);
                // Trên: Giant Python đang nghỉ 2.0s hồi Mana; Dưới: Giant Python bị choáng với chấm vàng fillOval
                Boss recPy = new Boss(Boss.Type.GIANT_PYTHON, 45, -62);
                setupSnakeSegments(recPy, 45, -62, 0.0, 12, 2.5);
                recPy.setMana(58.0);
                recPy.enterRecoveryState();
                setField(recPy, "recoveryTimer", 1.30);

                Boss stunPy = new Boss(Boss.Type.GIANT_PYTHON, 45, 78);
                setupSnakeSegments(stunPy, 45, 78, -0.1, 12, 4.1);
                stunPy.setAnimTimer(0.65);
                stunPy.stun(1.9);

                bossList.add(recPy);
                bossList.add(stunPy);
                renderer.renderWorldLayers(sg, panelW, panelH, cam, m1Chunks, player, ais, bossList, preys, crocs, null, bolts, ps, ft, 0.016, 1);
            } else if (idx == 4) {
                player.teleportTo(0, 115);
                Boss octo = new Boss(Boss.Type.OCTOPUS, -125, -20);
                octo.setMana(45.0);
                octo.enterRecoveryState();
                setField(octo, "recoveryTimer", 1.1);
                octo.setAnimTimer(2.2);

                Boss swamp = new Boss(Boss.Type.SWAMP_KING, 125, -10);
                swamp.setAngle(Math.PI);
                swamp.setMana(72.0);
                swamp.setAnimTimer(1.7);

                bossList.add(octo);
                bossList.add(swamp);
                renderer.renderWorldLayers(sg, panelW, panelH, cam, m2Chunks, player, ais, bossList, preys, crocs, null, bolts, ps, ft, 0.016, 2);
            } else {
                player.teleportTo(180, 75);
                Boss dragon = new Boss(Boss.Type.VOID_DRAGON, 55, 30);
                setupSnakeSegments(dragon, 55, 30, 0.05, 14, 3.0);
                dragon.setMana(52.0);
                dragon.enterRecoveryState();
                setField(dragon, "recoveryTimer", 1.4);
                bossList.add(dragon);
                renderer.renderWorldLayers(sg, panelW, panelH, cam, m1Chunks, player, ais, bossList, preys, crocs, null, bolts, ps, ft, 0.016, 1);
                renderer.renderHUDLayers(sg, panelW, panelH, cam, player, ais, bossList, preys);
            }

            // Viền & nhãn góc mỗi khung
            sg.setColor(new Color(15, 23, 42, 215));
            sg.fillRect(0, 0, panelW, 26);
            sg.setColor(Color.WHITE);
            sg.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            sg.drawString(labels[idx], 10, 18);
            sg.setColor(new Color(71, 85, 105));
            sg.drawRect(0, 0, panelW - 1, panelH - 1);
            sg.dispose();

            g.drawImage(sub, ox, oy, null);
        }
        g.dispose();

        File outFile = new File(ARTIFACT_DIR, "boss_snake_and_recovery_preview.png");
        ImageIO.write(sheet, "png", outFile);
        System.out.println("Saved preview: " + outFile.getAbsolutePath());

        // Benchmark drawBoss(GIANT_PYTHON) khi đang ROAR_WINDUP + COIL_DASH + RECOVERING
        Boss benchPy = new Boss(Boss.Type.GIANT_PYTHON, 0, 0);
        setupSnakeSegments(benchPy, 0, 0, 0.0, 20, 1.5);
        setField(benchPy, "snakeState", Boss.SnakeBossState.ROAR_WINDUP);
        setField(benchPy, "snakeSkillType", Boss.SnakeSkillType.COIL_DASH_WHIP);
        BufferedImage buf = new BufferedImage(panelW, panelH, BufferedImage.TYPE_INT_RGB);
        Graphics2D bg = buf.createGraphics();
        for (int i = 0; i < 40; i++) renderer.drawBoss(bg, cam, benchPy);
        long t0 = System.nanoTime();
        for (int i = 0; i < 400; i++) renderer.drawBoss(bg, cam, benchPy);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        bg.dispose();
        check(ms < 800, "400x drawBoss(GIANT_PYTHON) took too long: " + ms + " ms");
        System.out.println("[Perf] 400x drawBoss(GIANT_PYTHON skill render): " + ms + " ms ("
                + String.format("%.3f", ms / 400.0) + " ms/frame)");
    }

    public static void main(String[] args) throws Exception {
        testUniversalTwoSecondRecoveryState();
        testMapBossSeparation();
        testSnakeBossSkillsAndProjectiles();
        renderVisualPreviewSheetAndBenchmark();
        System.out.println("ALL BOSS REWORK VERIFICATION TESTS PASSED!");
        System.exit(0);
    }
}
