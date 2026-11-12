package com.snakearena.entity;

import com.snakearena.core.Camera;
import com.snakearena.core.Config;
import com.snakearena.core.SoundManager;
import com.snakearena.render.FloatingTextManager;
import com.snakearena.render.ParticleSystem;
import com.snakearena.world.ChunkManager;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Lớp cơ sở đại diện cho Rắn (Người chơi & Rắn AI) với mô phỏng vật lý dây mềm (Rope Physics)
 * và sóng uốn lượn hình sin.
 */
public class Snake {
    protected double x;
    protected double y;
    protected double angle;
    protected double targetAngle;
    protected boolean isPlayer;

    protected List<SnakeSegment> segments = new ArrayList<>();
    protected int length;

    protected int health;
    protected int maxHealth;
    protected int energy = 0;
    protected int score = 0;
    protected int evolutionLevel = 1;

    protected double healBuffer = 0.0;
    protected double healDelayTimer = 0.0;
    protected boolean usesNaturalRegen = false;

    private static final Color COLOR_HEAL = new Color(74, 222, 128);

    protected double speed = Config.SnakeConfig.BASE_SPEED;
    protected boolean isDashing = false;
    protected double dashTimer = 0.0;
    protected double dashCooldownTimer = 0.0;

    // Cơ chế Thể lực Tăng tốc (Stamina / Sprint)
    protected double stamina = Config.SnakeConfig.MAX_STAMINA;
    protected double maxStamina = Config.SnakeConfig.MAX_STAMINA;
    protected boolean isSprinting = false;

    // Cơ chế Thanh Mana & Hồi Phục
    protected double mana = Config.SnakeConfig.MAX_MANA;
    protected double maxMana = Config.SnakeConfig.MAX_MANA;

    // Hiệu ứng khống chế & Trúng Độc
    protected double stunTimer = 0.0;
    protected double poisonTimer = 0.0;
    protected double poisonTickTimer = 0.0;
    protected int poisonDmgPerTick = 0;

    protected double slitherTime = 0.0;
    protected double invulnerableTimer = 0.0;
    protected boolean hidden = false;
    protected boolean inWater = false;
    protected boolean alive = true;
    protected boolean expAwarded = false;

    // Các thuộc tính nâng cấp khi thăng cấp (Agility, Damage, Max HP)
    protected double baseSpeedBonus = 0.0;
    protected int skillDamageBonus = 0;
    protected int maxHealthBonus = 0;
    protected double caveCooldown = 0.0;
    protected boolean pendingLevelUpChoice = false;

    public Snake(double x, double y, boolean isPlayer) {
        this.x = x;
        this.y = y;
        this.angle = 0;
        this.targetAngle = 0;
        this.isPlayer = isPlayer;
        this.usesNaturalRegen = isPlayer;
        this.length = Config.SnakeConfig.INITIAL_LENGTH;
        this.maxHealth = isPlayer ? Config.SnakeConfig.HP_LV1 : Config.SnakeConfig.AI_SNAKE_MAX_HP;
        this.health = this.maxHealth;
        this.maxMana = isPlayer ? Config.SnakeConfig.MANA_LV1 : 50.0;
        this.mana = this.maxMana;

        for (int i = 0; i < this.length; i++) {
            segments.add(new SnakeSegment(x - i * Config.SnakeConfig.SEGMENT_DIST, y, 0));
        }
    }

    public void update(double dt, ChunkManager world, ParticleSystem particles) {
        if (!alive) return;

        slitherTime += dt;
        if (invulnerableTimer > 0) {
            invulnerableTimer -= dt;
        }
        if (healDelayTimer > 0) {
            healDelayTimer -= dt;
        }

        // 1. Kiểm tra trạng thái môi trường (Bụi rậm / Hang đá ngụy trang & Hồ nước)
        hidden = world.isInsideBush(x, y);
        inWater = world.isInWater(x, y);

        // Hồi phục HP liên tục theo dt (bộ đệm healBuffer mượt, không nhảy giật theo giây)
        if (alive && health < maxHealth) {
            if (usesNaturalRegen) {
                if (healDelayTimer <= 0) {
                    double rate = hidden ? Config.SnakeConfig.HIDING_HEAL_PER_SEC : Config.SnakeConfig.BASE_HEAL_PER_SEC;
                    healBuffer += rate * dt;
                    int step = (int) (healBuffer + 1e-9);
                    if (step > 0) {
                        health = Math.min(maxHealth, health + step);
                        healBuffer = Math.max(0.0, healBuffer - step);
                        if (hidden && particles != null) {
                            particles.spawnWaterBubble(x + (Math.random() - 0.5) * 16, y + (Math.random() - 0.5) * 16);
                        }
                    }
                }
            } else if (hidden) {
                // Rắn AI chỉ hồi khi ẩn nấp trong bụi
                healBuffer += Config.SnakeConfig.HIDING_HEAL_PER_SEC * dt;
                int step = (int) (healBuffer + 1e-9);
                if (step > 0) {
                    health = Math.min(maxHealth, health + step);
                    healBuffer = Math.max(0.0, healBuffer - step);
                }
            }
        } else if (health >= maxHealth) {
            healBuffer = 0.0;
        }

        // Hồi phục Mana
        if (hidden) {
            mana = Math.min(maxMana, mana + Config.SnakeConfig.HIDING_MANA_RECOVERY * dt);
        } else {
            mana = Math.min(maxMana, mana + Config.SnakeConfig.BASE_MANA_RECOVERY * dt);
        }

        // Xử lý Trúng Độc (Poison DoT)
        if (poisonTimer > 0) {
            poisonTimer -= dt;
            poisonTickTimer -= dt;
            if (poisonTickTimer <= 0) {
                poisonTickTimer = 0.5;
                health = Math.max(0, health - poisonDmgPerTick);
                healDelayTimer = Config.SnakeConfig.HEAL_DELAY_AFTER_HIT;
                if (particles != null) {
                    particles.spawnPoisonBubble(x, y);
                }
                if (health <= 0) {
                    alive = false;
                }
            }
        }

        // Nếu đang bị Choáng (Stun): Bất động hoàn toàn
        if (stunTimer > 0) {
            stunTimer -= dt;
            return;
        }

        // 2. Xử lý Thể lực & Tăng tốc (Sprint / Dash)
        if (isDashing) {
            dashTimer -= dt;
            if (dashTimer <= 0) {
                isDashing = false;
            }
        }
        if (dashCooldownTimer > 0) {
            dashCooldownTimer -= dt;
        }

        if (caveCooldown > 0) {
            caveCooldown -= dt;
        }

        if (isPlayer) {
            double curBaseSpeed = Config.SnakeConfig.BASE_SPEED + baseSpeedBonus;
            double curSprintSpeed = Config.SnakeConfig.SPRINT_SPEED + baseSpeedBonus * 1.25;
            double curDashSpeed = Config.SnakeConfig.DASH_SPEED + baseSpeedBonus * 1.25;

            if (isSprinting && stamina > 0) {
                stamina = Math.max(0.0, stamina - Config.SnakeConfig.STAMINA_DRAIN * dt);
                speed = isDashing ? curDashSpeed : curSprintSpeed;

                // Tạo hạt bụi hoặc bọt nước khi tăng tốc
                if (particles != null && Math.random() < 0.6) {
                    SnakeSegment tail = segments.get(segments.size() - 1);
                    particles.spawnSprintTrail(tail.x, tail.y, inWater);
                }
            } else {
                isSprinting = false;
                stamina = Math.min(maxStamina, stamina + Config.SnakeConfig.STAMINA_RECOVERY * dt);
                speed = isDashing ? curDashSpeed : curBaseSpeed;
            }
        }

        // Bọt nước sinh ra khi bơi trong hồ
        if (inWater && particles != null && Math.random() < 0.25) {
            particles.spawnWaterBubble(x + (Math.random() - 0.5) * 10, y + (Math.random() - 0.5) * 10);
        }

        // 3. Xoay góc từ từ theo targetAngle (Angular Lerp)
        double diff = targetAngle - angle;
        while (diff < -Math.PI) diff += Math.PI * 2.0;
        while (diff > Math.PI) diff -= Math.PI * 2.0;
        double turnStep = Config.SnakeConfig.TURN_SPEED * dt;
        if (Math.abs(diff) < turnStep) {
            angle = targetAngle;
        } else {
            angle += Math.signum(diff) * turnStep;
        }

        // 4. Di chuyển đầu rắn
        double moveDist = speed * dt;
        double nextX = x + Math.cos(angle) * moveDist;
        double nextY = y + Math.sin(angle) * moveDist;

        // Kiểm tra va chạm với cây cối hoặc khối đá
        ChunkManager.CollisionResult hit = world.checkCollision(nextX, nextY, 10.0);
        if (!hit.collided) {
            x = nextX;
            y = nextY;
        } else {
            angle += 0.8 * dt; // Trượt theo cạnh vật cản
        }

        // 5. Cập nhật các đốt thân theo nguyên lý Rope Physics
        segments.get(0).x = x;
        segments.get(0).y = y;
        segments.get(0).angle = angle;

        double segDist = Config.SnakeConfig.SEGMENT_DIST;
        for (int i = 1; i < segments.size(); i++) {
            SnakeSegment prev = segments.get(i - 1);
            SnakeSegment curr = segments.get(i);

            double dx = curr.x - prev.x;
            double dy = curr.y - prev.y;
            double dist = Math.hypot(dx, dy);

            if (dist > segDist) {
                double ratio = segDist / dist;
                curr.x = prev.x + dx * ratio;
                curr.y = prev.y + dy * ratio;
                curr.angle = Math.atan2(-dy, -dx);
            }
        }
    }

    public boolean activateDash() {
        if (evolutionLevel >= 2 && dashCooldownTimer <= 0 && !isDashing) {
            isDashing = true;
            dashTimer = Config.SnakeConfig.DASH_DURATION;
            dashCooldownTimer = Config.SnakeConfig.DASH_COOLDOWN;
            SoundManager.playDash();
            return true;
        }
        return false;
    }

    public void grow(int count) {
        int maxAllowed = Math.min(Config.SnakeConfig.MAX_SNAKE_LENGTH, 14 + evolutionLevel * 6);
        for (int i = 0; i < count; i++) {
            if (segments.size() >= maxAllowed) break;
            SnakeSegment tail = segments.get(segments.size() - 1);
            segments.add(new SnakeSegment(tail.x, tail.y, tail.angle));
        }
        length = segments.size();
    }

    public void feed(int foodVal, int energyVal, ParticleSystem particles, FloatingTextManager texts) {
        score += foodVal;
        energy += energyVal;

        // Kéo dài thân rắn mỗi khi ăn thêm quái
        grow(1);

        SoundManager.playEat();

        if (texts != null) {
            texts.spawn(x, y - 16, "+" + foodVal + " (+" + energyVal + " EXP)", new Color(250, 204, 21), 1.0, 14);
        }

        if (particles != null) {
            particles.spawnEat(x, y, new Color(100, 255, 120));
        }

        checkEvolution(particles, texts);
    }

    /**
     * Nhận Điểm Kinh Nghiệm (EXP) và Điểm Số trực tiếp từ việc diệt Quái hoặc Boss.
     * Tự động kiểm tra và nâng cấp tiến hóa khi đủ mốc.
     */
    public void addExp(int expVal, int scoreVal, ParticleSystem particles, FloatingTextManager texts) {
        this.energy += expVal;
        this.score += scoreVal;

        // Mỗi lần bắn hạ hoặc tiêu diệt quái/boss cũng tăng thêm chiều dài thân rắn
        grow(Math.max(1, expVal / 10));

        if (texts != null && expVal > 0) {
            texts.spawn(x, y - 26, "+" + expVal + " EXP (" + scoreVal + " PTS)!", new Color(192, 132, 252), 1.5, 15);
        }

        if (particles != null && expVal > 0) {
            particles.spawnEvolutionAura(x, y, new Color(192, 132, 252));
        }

        checkEvolution(particles, texts);
    }

    protected void checkEvolution(ParticleSystem particles, FloatingTextManager texts) {
        if (evolutionLevel == 1 && energy >= Config.SnakeConfig.EXP_TO_LV2) {
            evolutionLevel = 2;
            maxHealth = Config.SnakeConfig.HP_LV2 + maxHealthBonus;
            maxMana = Config.SnakeConfig.MANA_LV2;
            health = Math.min(maxHealth, health + 25); // Hồi phục nhẹ 25 HP
            mana = maxMana;
            if (isPlayer) {
                pendingLevelUpChoice = true;
            }
            SoundManager.playEvolve();
            int hpBonus = Config.SnakeConfig.HP_LV2 - Config.SnakeConfig.HP_LV1;
            int mpBonus = (int) (Config.SnakeConfig.MANA_LV2 - Config.SnakeConfig.MANA_LV1);
            if (texts != null) texts.spawn(x, y - 28, "TIẾN HÓA CẤP 2: SHADOW VIPER (+" + hpBonus + " MAX HP | +" + mpBonus + " MAX MP)!", new Color(192, 132, 252), 2.2, 16);
            if (particles != null) particles.spawnEvolutionAura(x, y, new Color(192, 132, 252));
        } else if (evolutionLevel == 2 && energy >= Config.SnakeConfig.EXP_TO_LV3) {
            evolutionLevel = 3;
            maxHealth = Config.SnakeConfig.HP_LV3 + maxHealthBonus;
            maxMana = Config.SnakeConfig.MANA_LV3;
            health = Math.min(maxHealth, health + 30);
            mana = maxMana;
            if (isPlayer) {
                pendingLevelUpChoice = true;
            }
            SoundManager.playEvolve();
            int hpBonus = Config.SnakeConfig.HP_LV3 - Config.SnakeConfig.HP_LV2;
            int mpBonus = (int) (Config.SnakeConfig.MANA_LV3 - Config.SnakeConfig.MANA_LV2);
            if (texts != null) texts.spawn(x, y - 28, "TIẾN HÓA CẤP 3: TITAN BOA (+" + hpBonus + " MAX HP | +" + mpBonus + " MAX MP)!", new Color(250, 204, 21), 2.4, 18);
            if (particles != null) particles.spawnEvolutionAura(x, y, new Color(250, 204, 21));
        } else if (evolutionLevel == 3 && energy >= Config.SnakeConfig.EXP_TO_LV4) {
            evolutionLevel = 4;
            maxHealth = Config.SnakeConfig.HP_LV4 + maxHealthBonus;
            maxMana = Config.SnakeConfig.MANA_LV4;
            health = Math.min(maxHealth, health + 35);
            mana = maxMana;
            if (isPlayer) {
                pendingLevelUpChoice = true;
            }
            SoundManager.playEvolve();
            int hpBonus = Config.SnakeConfig.HP_LV4 - Config.SnakeConfig.HP_LV3;
            int mpBonus = (int) (Config.SnakeConfig.MANA_LV4 - Config.SnakeConfig.MANA_LV3);
            if (texts != null) texts.spawn(x, y - 28, "TIẾN HÓA CẤP 4: CRIMSON FIRE DRAKE (+" + hpBonus + " MAX HP | +" + mpBonus + " MAX MP)!", new Color(239, 68, 68), 2.6, 18);
            if (particles != null) particles.spawnEvolutionAura(x, y, new Color(239, 68, 68));
        } else if (evolutionLevel == 4 && energy >= Config.SnakeConfig.EXP_TO_LV5) {
            evolutionLevel = 5;
            maxHealth = Config.SnakeConfig.HP_LV5 + maxHealthBonus;
            maxMana = Config.SnakeConfig.MANA_LV5;
            health = Math.min(maxHealth, health + 40);
            mana = maxMana;
            if (isPlayer) {
                pendingLevelUpChoice = true;
            }
            SoundManager.playEvolve();
            int hpBonus = Config.SnakeConfig.HP_LV5 - Config.SnakeConfig.HP_LV4;
            int mpBonus = (int) (Config.SnakeConfig.MANA_LV5 - Config.SnakeConfig.MANA_LV4);
            if (texts != null) texts.spawn(x, y - 28, "TIẾN HÓA TỐI THƯỢNG CẤP 5: CELESTIAL LEVIATHAN (+" + hpBonus + " MAX HP)!", new Color(250, 204, 21), 3.0, 20);
            if (particles != null) particles.spawnEvolutionAura(x, y, new Color(250, 204, 21));
        }
    }

    public boolean takeDamage(int dmg, Camera camera) {
        if (invulnerableTimer > 0) return false;

        // [KỸ NĂNG CẤP 3: DA DÀY] Giảm 20% sát thương nhận vào
        if (isPlayer && evolutionLevel >= 3) {
            dmg = Math.max(1, (int) Math.round(dmg * (1.0 - Config.SnakeConfig.THICK_SKIN_REDUCTION)));
        }

        health = Math.max(0, health - dmg);
        healDelayTimer = Config.SnakeConfig.HEAL_DELAY_AFTER_HIT;
        invulnerableTimer = 1.2; // Miễn nhiễm sát thương trong 1.2 giây
        if (camera != null) camera.shake(14.0);
        if (health <= 0) {
            alive = false;
        }
        return true;
    }

    public boolean useMana(double amount) {
        if (mana >= amount) {
            mana -= amount;
            return true;
        }
        return false;
    }

    public void inflictPoison(double duration, int dmgPerTick) {
        this.poisonTimer = Math.max(this.poisonTimer, duration);
        this.poisonDmgPerTick = dmgPerTick;
        this.poisonTickTimer = 0.5;
    }

    public void stun(double duration) {
        this.stunTimer = Math.max(this.stunTimer, duration);
    }

    public void knockback(double pushX, double pushY) {
        this.x += pushX;
        this.y += pushY;
        if (!segments.isEmpty()) {
            segments.get(0).x = this.x;
            segments.get(0).y = this.y;
        }
    }

    // Getters / Setters
    public double getMana() { return mana; }
    public double getMaxMana() { return maxMana; }
    public double getStunTimer() { return stunTimer; }
    public double getPoisonTimer() { return poisonTimer; }
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    public double getAngle() { return angle; }
    public void setAngle(double angle) { this.angle = angle; }
    public void setTargetAngle(double targetAngle) { this.targetAngle = targetAngle; }
    public List<SnakeSegment> getSegments() { return segments; }
    public int getLength() { return length; }
    public int getHealth() { return health; }
    public int getMaxHealth() { return maxHealth; }
    public int getEnergy() { return energy; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public int getEvolutionLevel() { return evolutionLevel; }
    public void setEvolutionLevel(int level) { this.evolutionLevel = level; }
    public double getSpeed() { return speed; }
    public boolean isDashing() { return isDashing; }
    public double getDashCooldownTimer() { return dashCooldownTimer; }
    public double getStamina() { return stamina; }
    public double getMaxStamina() { return maxStamina; }
    public boolean isSprinting() { return isSprinting; }
    public void setSprinting(boolean sprinting) { this.isSprinting = sprinting; }
    public double getSlitherTime() { return slitherTime; }
    public double getInvulnerableTimer() { return invulnerableTimer; }
    public boolean isHidden() { return hidden; }
    public boolean isInWater() { return inWater; }
    public void setInWater(boolean inWater) { this.inWater = inWater; }
    public void setSlitherTime(double t) { this.slitherTime = t; }
    public void setDashing(boolean dashing) { this.isDashing = dashing; }
    public boolean isPoisoned() { return poisonTimer > 0; }
    public boolean isExpAwarded() { return expAwarded; }
    public void setExpAwarded(boolean expAwarded) { this.expAwarded = expAwarded; }
    public boolean isAlive() { return alive; }
    public boolean isPlayer() { return isPlayer; }

    // Các hàm nâng cấp thuộc tính khi lên cấp
    public void upgradeBaseSpeed(double bonus) {
        this.baseSpeedBonus += bonus;
    }

    public void upgradeSkillDamage(int bonus) {
        this.skillDamageBonus += bonus;
    }

    public void upgradeMaxHealth(int bonus) {
        this.maxHealthBonus += bonus;
        this.maxHealth += bonus;
        this.health = Math.min(this.maxHealth, this.health + 35); // Tăng trần Max HP và hồi nhẹ +35 HP
        this.mana = this.maxMana;
    }

    public boolean hasPendingLevelUpChoice() { return pendingLevelUpChoice; }
    public void setPendingLevelUpChoice(boolean b) { this.pendingLevelUpChoice = b; }

    public double getBaseSpeedBonus() { return baseSpeedBonus; }
    public int getSkillDamageBonus() { return skillDamageBonus; }
    public int getMaxHealthBonus() { return maxHealthBonus; }

    public double getCaveCooldown() { return caveCooldown; }
    public void setCaveCooldown(double cd) { this.caveCooldown = cd; }

    /**
     * Hồi máu ngay lập tức khi tiêu diệt mục tiêu (Heal On Kill).
     * Hiển thị chữ nổi màu xanh lá sáng ("+N HP") và bắn hiệu ứng hạt xung quanh đầu rắn.
     */
    public void healOnKill(int amount, ParticleSystem particles, FloatingTextManager texts) {
        if (!alive || amount <= 0) return;
        this.health = Math.min(this.maxHealth, this.health + amount);
        if (texts != null) {
            texts.spawn(x, y - 24, "+" + amount + " HP", COLOR_HEAL, 1.2, 14);
        }
        if (particles != null) {
            particles.spawnEat(x, y, COLOR_HEAL);
        }
    }

    public boolean isUsesNaturalRegen() { return usesNaturalRegen; }
    public void setUsesNaturalRegen(boolean usesNaturalRegen) { this.usesNaturalRegen = usesNaturalRegen; }
    public double getHealDelayTimer() { return healDelayTimer; }

    // Dịch chuyển tức thời cả đầu và toàn bộ thân rắn
    public void teleportTo(double targetX, double targetY) {
        double dx = targetX - this.x;
        double dy = targetY - this.y;
        this.x = targetX;
        this.y = targetY;
        for (SnakeSegment seg : segments) {
            seg.x += dx;
            seg.y += dy;
        }
    }
}
