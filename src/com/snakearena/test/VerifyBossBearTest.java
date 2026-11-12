package com.snakearena.test;

import com.snakearena.core.Camera;
import com.snakearena.core.Config;
import com.snakearena.core.GamePanel;
import com.snakearena.entity.AISnake;
import com.snakearena.entity.Boss;
import com.snakearena.entity.Crocodile;
import com.snakearena.entity.PlayerSnake;
import com.snakearena.entity.Prey;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Kiểm thử toàn diện Boss Gấu (Boss.Type.BEAR):
 * - Hằng số Config.BossConfig & LayeredRenderer
 * - Máy trạng thái PATROL -> CHASE -> WINDUP -> LUNGE -> BITE -> RETREAT -> RECOVER
 * - Hủy khi ẩn nấp, lệch hướng không bám đuôi, góc táp 70 độ, chỉ gây sát thương 1 lần
 * - Sát thương nhận x1.25 khi RETREAT/RECOVER, ngắt trạng thái khi choáng/sập bẫy
 * - Nội tại nổi điên (<=40% HP) và cơ hội lao kép
 * - Hiển thị sprite x2 NEAREST_NEIGHBOR 16 hướng, FX & render preview ảnh kiểm chứng
 */
public class VerifyBossBearTest {

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

    private static void testConstants() {
        check(Config.BossConfig.BEAR_MAX_HP == 650, "BEAR_MAX_HP must stay 650");
        check(Config.BossConfig.BEAR_BASE_SPEED == 105.0, "BEAR_BASE_SPEED must stay 105.0");
        check(Config.BossConfig.BEAR_CHARGE_SPEED == 185.0, "BEAR_CHARGE_SPEED must stay 185.0");
        check(Config.BossConfig.BEAR_DAMAGE == 35, "BEAR_DAMAGE must stay 35");
        check(Config.BossConfig.BEAR_TRIGGER_DIST == 230.0, "BEAR_TRIGGER_DIST");
        check(Config.BossConfig.BEAR_WINDUP_TIME == 0.55, "BEAR_WINDUP_TIME");
        check(Config.BossConfig.BEAR_LUNGE_SPEED == 520.0, "BEAR_LUNGE_SPEED");
        check(Config.BossConfig.BEAR_LUNGE_MAX_DIST == 280.0, "BEAR_LUNGE_MAX_DIST");
        check(Config.BossConfig.BEAR_BITE_RADIUS == 58.0, "BEAR_BITE_RADIUS");
        check(Config.BossConfig.BEAR_BITE_ARC_DEG == 70.0, "BEAR_BITE_ARC_DEG");
        check(Config.BossConfig.BEAR_BITE_TIME == 0.25, "BEAR_BITE_TIME");
        check(Config.BossConfig.BEAR_RETREAT_TIME == 0.9, "BEAR_RETREAT_TIME");
        check(Config.BossConfig.BEAR_RETREAT_SPEED == 170.0, "BEAR_RETREAT_SPEED");
        check(Config.BossConfig.BEAR_RETREAT_DAMAGE_MULT == 1.25, "BEAR_RETREAT_DAMAGE_MULT");
        check(Config.BossConfig.BEAR_RECOVER_TIME == Config.BossConfig.BOSS_RECOVERY_DURATION,
                "BEAR_RECOVER_TIME must equal BOSS_RECOVERY_DURATION (2.0s)");
        check(Config.BossConfig.BOSS_REST_DURATION_MS == 2000L, "BOSS_REST_DURATION_MS must be 2000ms");
        check(Config.BossConfig.BEAR_ATTACK_COOLDOWN == 0.8, "BEAR_ATTACK_COOLDOWN");
        check(Config.BossConfig.BEAR_STUN_RECOVERY_COOLDOWN == 1.0, "BEAR_STUN_RECOVERY_COOLDOWN");
        check(Config.BossConfig.BEAR_HIT_STOP_TIME == 0.06, "BEAR_HIT_STOP_TIME");
        check(Config.BossConfig.BEAR_ENRAGE_HP_RATIO == 0.4, "BEAR_ENRAGE_HP_RATIO");
        check(Config.BossConfig.BEAR_ENRAGE_WINDUP_MULT == 0.65, "BEAR_ENRAGE_WINDUP_MULT");
        check(Config.BossConfig.BEAR_ENRAGE_LUNGE_SPEED_MULT == 1.2, "BEAR_ENRAGE_LUNGE_SPEED_MULT");
        check(Config.BossConfig.BEAR_ENRAGE_RETREAT_MULT == 0.7, "BEAR_ENRAGE_RETREAT_MULT");
        check(Config.BossConfig.BEAR_ENRAGE_DOUBLE_LUNGE_CHANCE == 0.35, "BEAR_ENRAGE_DOUBLE_LUNGE_CHANCE");
        check(LayeredRenderer.BEAR_SPRITE_SCALE == 2, "BEAR_SPRITE_SCALE");
        check(LayeredRenderer.BEAR_DIRECTIONS == 16, "BEAR_DIRECTIONS");
    }

    private static void testFullAttackCycleAndHitStop() throws Exception {
        GamePanel.resetHitStop();
        PlayerSnake player = new PlayerSnake(180, 0);
        setField(player, "evolutionLevel", 1); // Avoid Lv3 thick skin 20% mitigation so HP loss is exact 35
        int initialHp = player.getHealth();

        Boss bear = new Boss(Boss.Type.BEAR, 0, 0);
        Camera camera = new Camera(1024, 720);
        ParticleSystem particles = new ParticleSystem();
        FloatingTextManager texts = new FloatingTextManager();

        check(bear.getBearState() == Boss.BearState.PATROL, "Initial state should be PATROL");

        // Distance 180 < BEAR_TRIGGER_DIST (230) -> enters WINDUP
        bear.update(1.0 / 60.0, player, null, camera, particles, texts);
        check(bear.getBearState() == Boss.BearState.WINDUP, "Should transition to WINDUP within trigger dist, got " + bear.getBearState());

        // Advance partway through windup: no damage yet, still standing still
        for (int i = 0; i < 20; i++) {
            bear.update(0.016, player, null, camera, particles, texts);
        }
        check(bear.getBearState() == Boss.BearState.WINDUP, "Should remain in WINDUP around 0.33s");
        check(Math.abs(bear.getX()) < 1e-6 && Math.abs(bear.getY()) < 1e-6, "Bear should stand still in WINDUP");
        check(player.getHealth() == initialHp, "WINDUP must deal no damage");
        check(bear.getBearStateProgress() > 0.5 && bear.getBearStateProgress() < 1.0, "WINDUP progress in (0.5,1)");

        // Complete WINDUP -> enters LUNGE
        for (int i = 0; i < 20; i++) {
            bear.update(0.016, player, null, camera, particles, texts);
            if (bear.getBearState() == Boss.BearState.LUNGE) break;
        }
        check(bear.getBearState() == Boss.BearState.LUNGE, "Should transition WINDUP -> LUNGE");
        check(bear.getLockedTargetX() == 180.0 && bear.getLockedTargetY() == 0.0, "Locked target matches player pos");
        check(bear.getBearDustFxTimer() >= 0.0, "Dust FX should start on LUNGE");

        // Advance LUNGE until BITE triggers
        for (int i = 0; i < 60; i++) {
            bear.update(1.0 / 60.0, player, null, camera, particles, texts);
            if (bear.getBearState() == Boss.BearState.BITE) break;
        }
        check(bear.getBearState() == Boss.BearState.BITE, "Should reach BITE");
        check(bear.isBiteDamageDone(), "biteDamageDone should be set immediately on BITE entry");
        check(player.getHealth() == initialHp - Config.BossConfig.BEAR_DAMAGE,
                "Expected HP " + (initialHp - 35) + " got " + player.getHealth());
        check(Math.abs(GamePanel.getHitStopTimer() - 0.06) < 1e-6, "Hit-stop should be triggered for 0.06s");
        check(bear.getBearBiteFxTimer() >= 0.0, "Bite FX should start on BITE");

        @SuppressWarnings("unchecked")
        List<?> spawnedTexts = (List<?>) getField(texts, "texts");
        boolean foundText = false;
        for (Object ft : spawnedTexts) {
            String str = (String) getField(ft, "text");
            if ("GAU TAP (-35 HP)".equals(str)) {
                foundText = true;
                break;
            }
        }
        check(foundText, "Must spawn exact floating text 'GAU TAP (-35 HP)' without tofu symbols");

        // Further updates during BITE must NOT deal damage twice (even if invuln reset)
        setField(player, "invulnerableTimer", 0.0);
        int hpAfterBite = player.getHealth();
        for (int i = 0; i < 20; i++) {
            bear.update(0.016, player, null, camera, particles, texts);
            if (bear.getBearState() == Boss.BearState.RETREAT) break;
        }
        check(player.getHealth() == hpAfterBite, "BITE must only apply damage once per BITE state");
        check(bear.getBearState() == Boss.BearState.RETREAT, "Should transition BITE -> RETREAT");

        // During RETREAT: bear moves backward (-cos(angle), -sin(angle)), keeps facing player, speed slows down
        double xRetreatStart = bear.getX();
        bear.update(0.1, player, null, camera, particles, texts);
        double step1 = bear.getX() - xRetreatStart; // should be negative (moving left away from player at x=180)
        check(step1 < -5.0, "RETREAT must move opposite facing direction (-X)");

        double xMid = bear.getX();
        for (int i = 0; i < 4; i++) bear.update(0.1, player, null, camera, particles, texts);
        double xLateStart = bear.getX();
        bear.update(0.1, player, null, camera, particles, texts);
        double stepLate = bear.getX() - xLateStart;
        check(Math.abs(stepLate) < Math.abs(step1), "RETREAT speed should ease-out (slow down over time)");
        check(xMid != bear.getX(), "RETREAT continues moving smoothly");

        // Advance RETREAT -> RECOVER
        for (int i = 0; i < 30; i++) {
            bear.update(0.05, player, null, camera, particles, texts);
            if (bear.getBearState() == Boss.BearState.RECOVER) break;
        }
        check(bear.getBearState() == Boss.BearState.RECOVER, "Should transition RETREAT -> RECOVER");

        // Damage taken during RETREAT/RECOVER is x1.25 (40 -> 50)
        int hpBeforeHit = bear.getHealth();
        bear.takeDamage(40, null, null);
        check(hpBeforeHit - bear.getHealth() == 50,
                "Damage during RECOVER must multiply by 1.25 (expected 50, got " + (hpBeforeHit - bear.getHealth()) + ")");

        // Complete 2.0s (2000ms) RECOVER -> CHASE with attackCooldown = 0.8
        check(bear.isRecovering(), "Bear should be in universal 2s recovery state");
        for (int i = 0; i < 45; i++) {
            bear.update(0.05, player, null, camera, particles, texts);
            if (bear.getBearState() == Boss.BearState.CHASE) break;
        }
        check(bear.getBearState() == Boss.BearState.CHASE, "Should transition RECOVER -> CHASE");
        check(!bear.isRecovering(), "Should exit recovery state after 2.0s");
        check(bear.getAttackCooldown() > 0.6 && bear.getAttackCooldown() <= 0.8,
                "Cooldown after RECOVER should start around 0.8s, got " + bear.getAttackCooldown());
    }

    private static void testStealthAndNoHoming() throws Exception {
        PlayerSnake player = new PlayerSnake(180, 0);
        Boss bear = new Boss(Boss.Type.BEAR, 0, 0);
        FloatingTextManager texts = new FloatingTextManager();

        // 1. Player hides during WINDUP -> cancels to CHASE
        bear.update(0.016, player, null, null, null, texts);
        check(bear.getBearState() == Boss.BearState.WINDUP, "Should enter WINDUP");
        setField(player, "hidden", true);
        bear.update(0.016, player, null, null, null, texts);
        check(bear.getBearState() == Boss.BearState.CHASE, "Hiding in WINDUP must cancel to CHASE");
        bear.update(0.016, player, null, null, null, texts);
        check(bear.getBearState() == Boss.BearState.PATROL, "While still hidden, CHASE loses sight -> PATROL");

        // 2. Player dodges sideways during LUNGE (straight-line no homing)
        setField(player, "hidden", false);
        bear = new Boss(Boss.Type.BEAR, 0, 0);
        player.teleportTo(180, 0);
        for (int i = 0; i < 40; i++) {
            bear.update(0.016, player, null, null, null, texts);
            if (bear.getBearState() == Boss.BearState.LUNGE) break;
        }
        check(bear.getBearState() == Boss.BearState.LUNGE, "Entered LUNGE toward (180,0)");
        // Player dodges far aside to (0, 200) -> bear must NOT steer toward Y=200!
        player.teleportTo(0, 200);
        int hpBefore = player.getHealth();
        for (int i = 0; i < 40; i++) {
            bear.update(0.016, player, null, null, null, texts);
            if (bear.getBearState() == Boss.BearState.BITE) break;
        }
        check(bear.getBearState() == Boss.BearState.BITE, "Reached BITE near locked (180,0)");
        check(Math.abs(bear.getY()) < 5.0 && bear.getX() > 150.0, "LUNGE must move straight along locked dir, no homing!");
        check(player.getHealth() == hpBefore, "Dodged player outside BITE radius takes 0 damage");

        // 3. Player hides mid-LUNGE -> bear still finishes lunging to lockedTarget, then whiffs BITE
        bear = new Boss(Boss.Type.BEAR, 0, 0);
        player.teleportTo(180, 0);
        for (int i = 0; i < 40; i++) {
            bear.update(0.016, player, null, null, null, texts);
            if (bear.getBearState() == Boss.BearState.LUNGE) break;
        }
        setField(player, "hidden", true);
        hpBefore = player.getHealth();
        for (int i = 0; i < 40; i++) {
            bear.update(0.016, player, null, null, null, texts);
            if (bear.getBearState() == Boss.BearState.BITE) break;
        }
        check(bear.getBearState() == Boss.BearState.BITE, "Mid-lunge stealth: bear finishes lunging to locked target");
        check(bear.getX() >= 175.0, "Finished lunging to locked point (180,0)");
        check(player.getHealth() == hpBefore, "BITE against hidden player deals 0 damage");

        // 4. Player behind bear within BITE radius (outside 70 deg half-arc) -> 0 damage
        setField(player, "hidden", false);
        bear = new Boss(Boss.Type.BEAR, 0, 0);
        bear.setAngle(0.0); // Facing +X
        player.teleportTo(-30, 0); // Behind bear, distance = 30 <= 58, angle = 180 deg > 70 deg!
        setField(bear, "bearState", Boss.BearState.BITE);
        setField(bear, "biteDamageDone", false);
        hpBefore = player.getHealth();
        bear.update(0.016, player, null, null, null, texts);
        check(player.getHealth() == hpBefore, "Player behind bear (>70 deg half-arc) takes 0 bite damage");
    }

    private static void testStunTrapAndObstacleCancel() throws Exception {
        // 1. Stun interrupts LUNGE and transitions to RECOVER with 1.0s cooldown when stun expires
        PlayerSnake player = new PlayerSnake(180, 0);
        Boss bear = new Boss(Boss.Type.BEAR, 0, 0);
        for (int i = 0; i < 40; i++) {
            bear.update(0.016, player, null, null, null, null);
            if (bear.getBearState() == Boss.BearState.LUNGE) break;
        }
        check(bear.getBearState() == Boss.BearState.LUNGE, "Bear in LUNGE");

        bear.stun(0.5);
        check(bear.getBearState() != Boss.BearState.LUNGE, "Stun must interrupt LUNGE immediately");
        bear.update(0.25, player, null, null, null, null);
        check(bear.getStunTimer() > 0.0, "Still stunned");
        bear.update(0.25, player, null, null, null, null);
        check(bear.getStunTimer() == 0.0, "Stun finished");
        check(bear.getBearState() == Boss.BearState.RECOVER, "Must enter RECOVER right after stun ends");
        check(Math.abs(bear.getAttackCooldown() - 1.0) < 1e-6, "Cooldown after stun must be 1.0");

        // 2. Trap trigger during WINDUP deals 120, stuns 2.5s, interrupts state
        ChunkManager world = new ChunkManager(1);
        Chunk c0 = world.getOrCreateChunk(0, 0);
        c0.getProps().clear();
        c0.getProps().add(new WorldProp(WorldProp.Type.TRAP, 100.0, 100.0, 16.0, 32, 30));
        Boss trapBear = new Boss(Boss.Type.BEAR, 100.0, 100.0);
        player.teleportTo(260.0, 100.0);
        trapBear.update(0.016, player, world, null, null, null); // Enters WINDUP & hits trap
        check(trapBear.getStunTimer() == 2.5, "Trap should stun bear for 2.5s");
        check(trapBear.getHealth() == Config.BossConfig.BEAR_MAX_HP - 120, "Trap deals 120 dmg");
        check(trapBear.getBearState() != Boss.BearState.WINDUP, "Trap must interrupt WINDUP");

        // 3. Wall collision during LUNGE cancels to CHASE (no hang)
        Chunk wallChunk = world.getOrCreateChunk(2, 0); // x in [1024..1536]
        wallChunk.getProps().clear();
        wallChunk.getProps().add(new WorldProp(WorldProp.Type.WALL, 1200.0, 0.0, 40.0, 54, 38));
        Boss wallBear = new Boss(Boss.Type.BEAR, 1100.0, 0.0);
        player.teleportTo(1280.0, 0.0);
        for (int i = 0; i < 40; i++) {
            wallBear.update(0.016, player, world, null, null, null);
            if (wallBear.getBearState() == Boss.BearState.LUNGE) break;
        }
        check(wallBear.getBearState() == Boss.BearState.LUNGE, "WallBear entered LUNGE");
        boolean hitWallCancel = false;
        for (int i = 0; i < 30; i++) {
            wallBear.update(0.016, player, world, null, null, null);
            if (wallBear.getBearState() == Boss.BearState.CHASE) {
                hitWallCancel = true;
                break;
            }
        }
        check(hitWallCancel, "LUNGE hitting obstacle must cancel to CHASE");
    }

    private static void testEnragedMultipliersAndPerformance() throws Exception {
        Boss enragedBear = new Boss(Boss.Type.BEAR, 0, 0);
        setField(enragedBear, "health", (int) (Config.BossConfig.BEAR_MAX_HP * 0.35)); // <= 40%
        check(enragedBear.isBearEnraged(), "Bear at 35% HP should be enraged");

        PlayerSnake player = new PlayerSnake(180, 0);
        setField(player, "evolutionLevel", 1);
        enragedBear.update(0.016, player, null, null, null, null); // Enters WINDUP
        check(enragedBear.getBearState() == Boss.BearState.WINDUP, "Enraged WINDUP");
        // Enraged windup = 0.55 * 0.65 = 0.3575s -> by 0.38s must already be in LUNGE!
        enragedBear.update(0.38, player, null, null, null, null);
        check(enragedBear.getBearState() == Boss.BearState.LUNGE, "Enraged windup duration must be x0.65");

        // Enraged lunge speed = 520 * 1.2 = 624 px/s -> verify enraged retreat duration = 0.9 * 0.7 = 0.63s
        setField(enragedBear, "bearState", Boss.BearState.RETREAT);
        setField(enragedBear, "stateTime", 0.0);
        enragedBear.update(0.60, player, null, null, null, null);
        check(enragedBear.getBearState() == Boss.BearState.RETREAT, "Enraged retreat still active at 0.60s");
        enragedBear.update(0.05, player, null, null, null, null);
        check(enragedBear.getBearState() == Boss.BearState.RECOVER, "Enraged retreat (0.63s) finishes by 0.65s");

        // Sprite & Render Performance Test (1000 frames of world + bear draw)
        LayeredRenderer renderer = new LayeredRenderer();
        Camera cam = new Camera(1024, 720);
        ChunkManager cm = new ChunkManager(1);
        List<Chunk> chunks = cm.updateActiveChunks(cam, 0.016);
        List<Boss> bosses = Collections.singletonList(enragedBear);
        List<AISnake> ais = Collections.emptyList();
        List<Prey> preys = Collections.emptyList();
        List<Crocodile> crocs = Collections.emptyList();
        ParticleSystem ps = new ParticleSystem();
        FloatingTextManager ft = new FloatingTextManager();

        BufferedImage buf = new BufferedImage(1024, 720, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = buf.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        // Warmup
        for (int i = 0; i < 20; i++) {
            renderer.renderWorldLayers(g, 1024, 720, cam, chunks, player, ais, bosses, preys, crocs, null, null, ps, ft, 0.016, 1);
        }

        long t0 = System.nanoTime();
        for (int i = 0; i < 400; i++) {
            renderer.drawBoss(g, cam, enragedBear);
        }
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        g.dispose();
        check(elapsedMs < 800, "400x drawBoss BEAR took too long: " + elapsedMs + " ms");
        System.out.println("[Perf] 400x drawBoss(BEAR): " + elapsedMs + " ms (" + String.format("%.3f", elapsedMs / 400.0) + " ms/frame)");
    }

    private static void renderVisualPreviewSheet() throws Exception {
        String artifactDir = "C:/Users/Gia Huy/.gemini/antigravity/brain/654b6c66-ba54-4cb1-9fa5-1e908db71677";
        LayeredRenderer renderer = new LayeredRenderer();
        int panelW = 500;
        int panelH = 380;
        int cols = 3;
        int rows = 2;
        BufferedImage sheet = new BufferedImage(panelW * cols, panelH * rows, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        String[] labels = {
            "1. WINDUP (50px Red Ground Telegraph)",
            "2. LUNGE (Dust FX + 0.7 Alpha Wind Streak)",
            "3. BITE (Bite Slash FX + Floating Text)",
            "4. RETREAT (Enraged <=40% HP Backing Off)",
            "5. STUNNED (fillOval Dots + Head Bar)",
            "6. Y-SORT & SHADOWS (Full Scene Map 1)"
        };

        ChunkManager world = new ChunkManager(1);
        Camera cam = new Camera(panelW, panelH);
        cam.snapTo(0, 0);
        List<Chunk> chunks = world.updateActiveChunks(cam, 0.016);
        PlayerSnake player = new PlayerSnake(110, 15);
        List<AISnake> ais = new ArrayList<>();
        List<Prey> preys = new ArrayList<>();
        List<Crocodile> crocs = new ArrayList<>();
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

            Boss bear = new Boss(Boss.Type.BEAR, -70, 0);
            ft.clear();
            player.teleportTo(110, 15);

            if (idx == 0) {
                bear.setAngle(0.08);
                setField(bear, "bearState", Boss.BearState.WINDUP);
                setField(bear, "stateTime", 0.44); // ~80% -> bright blink phase
                setField(bear, "lockedTargetX", 110.0);
                setField(bear, "lockedTargetY", 15.0);
            } else if (idx == 1) {
                bear.setX(20);
                bear.setAngle(0.0);
                setField(bear, "bearState", Boss.BearState.LUNGE);
                setField(bear, "lungeTraveled", 110.0);
                setField(bear, "lungeExpectedDist", 180.0);
                setField(bear, "bearDustFxX", -70.0);
                setField(bear, "bearDustFxY", 0.0);
                setField(bear, "bearDustFxTimer", 0.09);
            } else if (idx == 2) {
                bear.setX(60);
                bear.setAngle(0.0);
                setField(bear, "bearState", Boss.BearState.BITE);
                setField(bear, "stateTime", 0.08);
                setField(bear, "bearBiteFxX", 116.0);
                setField(bear, "bearBiteFxY", 0.0);
                setField(bear, "bearBiteFxAngle", 0.0);
                setField(bear, "bearBiteFxTimer", 0.08);
                ft.spawn(110, -24, "GAU TAP (-35 HP)", new Color(239, 68, 68), 2.0, 16);
            } else if (idx == 3) {
                bear.setAngle(-0.15);
                setField(bear, "health", 220); // Enraged
                setField(bear, "bearState", Boss.BearState.RETREAT);
                setField(bear, "stateTime", 0.25);
            } else if (idx == 4) {
                bear.setAngle(0.5);
                bear.setAnimTimer(0.9);
                setField(bear, "stunTimer", 1.8);
                ft.spawn(bear.getX(), bear.getY() - 82, "GAU SAP BAY KEP (-120 HP & CHOANG)!", new Color(250, 204, 21), 2.0, 15);
            } else {
                bear.setX(0);
                bear.setY(20);
                bear.setAngle(0.6);
                setField(bear, "bearState", Boss.BearState.CHASE);
                setField(bear, "bearMoving", true);
                setField(bear, "walkAnimTimer", 0.24);
                player.teleportTo(-15, -20); // Player behind/above bear footY -> behind bear in Y-sort
            }

            List<Boss> list = Collections.singletonList(bear);
            renderer.renderWorldLayers(sg, panelW, panelH, cam, chunks, player, ais, list, preys, crocs, null, null, ps, ft, 0.016, 1);

            // Panel border + label banner
            sg.setColor(new Color(15, 23, 42, 200));
            sg.fillRect(0, 0, panelW, 28);
            sg.setColor(Color.WHITE);
            sg.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            sg.drawString(labels[idx], 10, 19);
            sg.setColor(new Color(71, 85, 105));
            sg.drawRect(0, 0, panelW - 1, panelH - 1);
            sg.dispose();

            g.drawImage(sub, ox, oy, null);
        }
        g.dispose();

        File outFile = new File(artifactDir, "boss_bear_overhaul_preview.png");
        ImageIO.write(sheet, "png", outFile);
        System.out.println("Saved preview: " + outFile.getAbsolutePath());
    }

    public static void main(String[] args) throws Exception {
        testConstants();
        testFullAttackCycleAndHitStop();
        testStealthAndNoHoming();
        testStunTrapAndObstacleCancel();
        testEnragedMultipliersAndPerformance();
        renderVisualPreviewSheet();
        System.out.println("ALL BOSS BEAR VERIFICATION TESTS PASSED!");
    }
}
