package com.snakearena.entity;

import com.snakearena.core.Camera;
import com.snakearena.core.Config;
import com.snakearena.core.SoundManager;
import com.snakearena.render.FloatingTextManager;
import com.snakearena.render.ParticleSystem;
import com.snakearena.world.ChunkManager;
import java.awt.Color;

/**
 * Cá Sấu Đầm Nước (Swamp Crocodile):
 * Sinh sống và rình rập trong các đầm hồ nước.
 * Khi người chơi xuống nước (player.isInWater()), cá sấu sẽ đánh hơi thấy chấn động,
 * lướt tới rình rập và tung cú đớp chớp nhoáng (25 HP).
 */
public class Crocodile {
    private double x;
    private double y;
    private double angle;
    private double targetAngle;
    private double speed;

    private int health;
    private final int maxHealth;
    private boolean alive = true;

    private double attackCooldown = 0.0;
    private double lungeTimer = 0.0;
    private double stunTimer = 0.0;
    private double animTimer = 0.0;
    private double swimPhase = 0.0;

    // Trạng thái độc tố (DoT)
    private double poisonTimer = 0.0;
    private double poisonTickTimer = 0.0;
    private int poisonDmgPerTick = 0;

    // Trạng thái hành vi
    private boolean isStalking = false;
    private boolean isLunging = false;
    private boolean onLand = false;

    // Tham số hoạt ảnh chuyển động mềm mại & linh hoạt
    private double walkPhase = 0.0;
    private double jawOpen = 0.0;
    private double spineCurvature = 0.0;

    public Crocodile(double x, double y) {
        this.x = x;
        this.y = y;
        this.angle = Math.random() * Math.PI * 2.0;
        this.targetAngle = this.angle;
        this.maxHealth = Config.Ecosystem.CROCODILE_MAX_HP;
        this.health = this.maxHealth;
        this.speed = Config.Ecosystem.CROCODILE_SPEED;
    }

    public void update(double dt, PlayerSnake player, ChunkManager world,
                       Camera camera, ParticleSystem particles, FloatingTextManager texts) {
        if (!alive) return;
        animTimer += dt;
        onLand = !world.isInWater(x, y);

        // Chu kỳ bước chân & bơi lượn uốn khúc theo tốc độ thực
        walkPhase += dt * (speed / 16.0);
        swimPhase += dt * (isLunging ? 16.0 : (isStalking ? 11.0 : 6.0));

        // Xử lý Trúng Độc DoT
        if (poisonTimer > 0) {
            poisonTimer -= dt;
            poisonTickTimer -= dt;
            if (poisonTickTimer <= 0) {
                poisonTickTimer = 0.5;
                takeDamage(poisonDmgPerTick, texts, particles);
            }
        }

        if (stunTimer > 0) {
            stunTimer -= dt;
            return;
        }

        if (attackCooldown > 0) {
            attackCooldown -= dt;
        }

        double distToPlayer = Math.hypot(player.getX() - x, player.getY() - y);
        boolean playerInWater = player.isAlive() && player.isInWater() && !player.isHidden();
        boolean playerNearShore = player.isAlive() && !player.isHidden() && distToPlayer < 240.0;

        // 1. Phán đoán hành vi: Rình rập nếu người chơi ở dưới nước hoặc ven bờ
        if ((playerInWater || playerNearShore) && distToPlayer < Config.Ecosystem.CROCODILE_AGGRO_RADIUS) {
            isStalking = true;
            targetAngle = Math.atan2(player.getY() - y, player.getX() - x);

            // Mở rộng miệng chuẩn bị táp khi tiếp cận con mồi
            if (distToPlayer < 90.0) {
                jawOpen = Math.min(1.0, jawOpen + dt * 6.0);
            } else {
                jawOpen = Math.max(0.0, jawOpen - dt * 3.0);
            }

            // Tấn công cắn xé (Lunge Snap) khi tiếp cận rất gần
            if (distToPlayer < 52.0 && attackCooldown <= 0) {
                isLunging = true;
                lungeTimer = 0.45;
                attackCooldown = 1.7;
                speed = Config.Ecosystem.CROCODILE_LUNGE_SPEED;
                jawOpen = 1.0;

                // Gây sát thương lên người chơi
                if (player.takeDamage(Config.Ecosystem.CROCODILE_DAMAGE, camera)) {
                    jawOpen = 0.1; // Cắn sập hàm cực mạnh!
                    if (texts != null) {
                        texts.spawn(player.getX(), player.getY() - 32, "⚔ CÁ SẤU ĐỚP CẮN (-" + Config.Ecosystem.CROCODILE_DAMAGE + " HP)!", new Color(239, 68, 68), 2.0, 16);
                    }
                    if (particles != null) {
                        if (!onLand) {
                            particles.spawnWaterBubble(x, y);
                            particles.spawnWaterBubble(player.getX(), player.getY());
                        }
                        particles.spawnTrapSparks(x, y);
                    }
                    SoundManager.playHit();
                    if (camera != null) camera.shake(9.0);
                }
            } else if (lungeTimer > 0) {
                lungeTimer -= dt;
                speed = Config.Ecosystem.CROCODILE_LUNGE_SPEED;
            } else {
                isLunging = false;
                speed = Config.Ecosystem.CROCODILE_SPEED;
            }
        } else {
            isStalking = false;
            isLunging = false;
            jawOpen = Math.max(0.0, jawOpen - dt * 4.0);
            speed = Config.Ecosystem.CROCODILE_SPEED * 0.55; // Tuần tra bơi lượn linh hoạt
            if (Math.random() < 0.035) {
                targetAngle += (Math.random() - 0.5) * 1.8;
            }
        }

        // 2. Quay đầu cực kỳ mượt mà và linh hoạt (Agile Turn Rate)
        double diff = targetAngle - angle;
        while (diff < -Math.PI) diff += Math.PI * 2.0;
        while (diff > Math.PI) diff -= Math.PI * 2.0;
        double turnSpeed = isLunging ? 8.5 : (isStalking ? 5.5 : 3.2);
        if (Math.abs(diff) < turnSpeed * dt) {
            angle = targetAngle;
        } else {
            angle += Math.signum(diff) * turnSpeed * dt;
        }

        // Độ cong cột sống theo độ bẻ lái (Curvature)
        double targetCurvature = Math.max(-0.6, Math.min(0.6, diff));
        spineCurvature += (targetCurvature - spineCurvature) * Math.min(1.0, dt * 7.0);

        // 3. Di chuyển lưỡng cư linh hoạt (Amphibious Locomotion)
        // Khi ở trên cạn hoặc bò lên bờ, tốc độ đạt 75% so với bơi, không bị dừng hay kẹt cứng
        double moveSpeed = onLand ? (speed * 0.78) : speed;
        double nextX = x + Math.cos(angle) * moveSpeed * dt;
        double nextY = y + Math.sin(angle) * moveSpeed * dt;

        boolean nextInWater = world.isInWater(nextX, nextY);

        if (nextInWater || isStalking) {
            // Cho phép đuổi theo người chơi hoặc bơi tự do
            x = nextX;
            y = nextY;
        } else {
            // Khi đi dạo mà gặp cạn xa, tìm hướng nước gần nhất để quay về
            double bestAngle = angle;
            boolean foundWater = false;
            for (int k = 1; k <= 6; k++) {
                double a1 = angle + k * (Math.PI / 6.0);
                if (world.isInWater(x + Math.cos(a1) * 45.0, y + Math.sin(a1) * 45.0)) {
                    bestAngle = a1;
                    foundWater = true;
                    break;
                }
                double a2 = angle - k * (Math.PI / 6.0);
                if (world.isInWater(x + Math.cos(a2) * 45.0, y + Math.sin(a2) * 45.0)) {
                    bestAngle = a2;
                    foundWater = true;
                    break;
                }
            }
            if (foundWater) {
                targetAngle = bestAngle;
            } else {
                targetAngle += Math.PI * 0.5;
            }
            x += Math.cos(angle) * moveSpeed * dt * 0.7;
            y += Math.sin(angle) * moveSpeed * dt * 0.7;
        }

        // Hiệu ứng bọt nước khi ở dưới nước
        if (!onLand && particles != null && Math.random() < (isLunging ? 0.50 : (isStalking ? 0.30 : 0.15))) {
            particles.spawnWaterBubble(x - Math.cos(angle) * 32.0, y - Math.sin(angle) * 32.0);
        }
    }

    public void takeDamage(int dmg, FloatingTextManager texts, ParticleSystem particles) {
        if (!alive) return;
        health -= dmg;
        if (texts != null) {
            texts.spawn(x, y - 24, "-" + dmg + " HP", new Color(248, 113, 113), 1.2, 14);
        }
        if (particles != null) {
            particles.spawnPoisonSplash(x, y);
        }
        if (health <= 0) {
            health = 0;
            alive = false;
        }
    }

    public void inflictPoison(double duration, int dmgPerTick) {
        this.poisonTimer = duration;
        this.poisonTickTimer = 0.5;
        this.poisonDmgPerTick = dmgPerTick;
    }

    public void stun(double duration) {
        this.stunTimer = Math.max(this.stunTimer, duration);
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getAngle() { return angle; }
    public int getHealth() { return health; }
    public int getMaxHealth() { return maxHealth; }
    public boolean isAlive() { return alive; }
    public void setAlive(boolean alive) { this.alive = alive; }
    public boolean isStalking() { return isStalking; }
    public boolean isLunging() { return isLunging; }
    public double getAnimTimer() { return animTimer; }
    public double getSwimPhase() { return swimPhase; }
    public double getWalkPhase() { return walkPhase; }
    public double getJawOpen() { return jawOpen; }
    public double getSpineCurvature() { return spineCurvature; }
    public boolean isOnLand() { return onLand; }
}
