package com.snakearena.entity;

import com.snakearena.core.Camera;
import com.snakearena.core.Config;
import com.snakearena.core.SoundManager;
import com.snakearena.render.FloatingTextManager;
import com.snakearena.render.ParticleSystem;
import com.snakearena.world.ChunkManager;
import java.awt.Color;
import java.util.List;

/**
 * Con rắn do Người Chơi điều khiển.
 * Hỗ trợ các kỹ năng theo cấp độ:
 * - Cấp 1: Phun Độc [Q / Chuột Phải] - Bắn tia độc theo đường thẳng, gây sát thương theo thời gian (DoT)
 * - Cấp 2: Rung Đuôi [E / Phím F] - Sóng xung kích làm choáng và đẩy lùi kẻ thù xung quanh
 * - Cấp 3: Da Dày [Nội Tại] - Giảm 20% mọi sát thương nhận vào
 */
public class PlayerSnake extends Snake {
    private double venomCooldownTimer = 0.0;
    private double rattleCooldownTimer = 0.0;

    public PlayerSnake(double x, double y) {
        super(x, y, true);
    }

    public void directTo(double targetX, double targetY) {
        this.targetAngle = Math.atan2(targetY - this.y, targetX - this.x);
    }

    @Override
    public void update(double dt, ChunkManager world, ParticleSystem particles) {
        super.update(dt, world, particles);
        if (venomCooldownTimer > 0) venomCooldownTimer -= dt;
        if (rattleCooldownTimer > 0) rattleCooldownTimer -= dt;
    }

    /**
     * Chiêu 1: PHUN ĐỘC (Venom Spit) [Q / Chuột Phải]
     * Bắn ra một phi đạn tia độc theo đường thẳng, gây sát thương tức thời và DoT.
     */
    public boolean shootVenomSpit(List<PoisonBolt> bolts, FloatingTextManager texts, ParticleSystem particles) {
        if (!alive || stunTimer > 0) return false;
        if (venomCooldownTimer > 0) return false;

        if (mana < Config.SnakeConfig.VENOM_SPIT_MANA) {
            if (texts != null) {
                texts.spawn(x, y - 24, "KHÔNG ĐỦ MANA!", new Color(248, 113, 113), 0.8, 13);
            }
            return false;
        }

        useMana(Config.SnakeConfig.VENOM_SPIT_MANA);
        venomCooldownTimer = 0.45; // Cooldown 0.45s

        double spawnX = x + Math.cos(angle) * 18.0;
        double spawnY = y + Math.sin(angle) * 18.0;
        bolts.add(new PoisonBolt(spawnX, spawnY, angle, Config.SnakeConfig.VENOM_SPIT_SPEED));

        SoundManager.playVenomSpit();
        if (particles != null) {
            particles.spawnPoisonBubble(spawnX, spawnY);
        }
        return true;
    }

    /**
     * Chiêu 2: RUNG ĐUÔI (Rattle Tail Shockwave) [E / Phím F] - Yêu cầu Cấp 2 trở lên
     * Phát ra sóng âm thanh chấn động từ đuôi rắn, làm choáng và đẩy lùi toàn bộ kẻ thù trong bán kính 180px.
     */
    public boolean rattleTail(List<AISnake> aiSnakes, List<Boss> bosses, List<Prey> preys,
                              List<Crocodile> crocodiles,
                              ParticleSystem particles, FloatingTextManager texts, Camera camera) {
        if (!alive || stunTimer > 0) return false;
        if (evolutionLevel < 2) {
            if (texts != null) {
                texts.spawn(x, y - 24, "YÊU CẦU CẤP 2 (SHADOW VIPER)!", new Color(251, 146, 60), 1.0, 13);
            }
            return false;
        }
        if (rattleCooldownTimer > 0) return false;

        if (mana < Config.SnakeConfig.RATTLE_TAIL_MANA) {
            if (texts != null) {
                texts.spawn(x, y - 24, "KHÔNG ĐỦ MANA!", new Color(248, 113, 113), 0.8, 13);
            }
            return false;
        }

        useMana(Config.SnakeConfig.RATTLE_TAIL_MANA);
        rattleCooldownTimer = Config.SnakeConfig.RATTLE_TAIL_COOLDOWN;

        // Vị trí phát sóng từ đuôi rắn (hoặc tâm thân rắn)
        SnakeSegment tail = segments.get(segments.size() - 1);
        double originX = tail.x;
        double originY = tail.y;

        SoundManager.playRattleTail();
        if (particles != null) {
            particles.spawnShockwaveRing(originX, originY);
        }
        if (texts != null) {
            texts.spawn(originX, originY - 30, "⚡ RUNG ĐUÔI CHẤN ĐỘNG! ⚡", new Color(250, 204, 21), 1.5, 16);
        }
        if (camera != null) {
            camera.shake(8.0);
        }

        double radius = Config.SnakeConfig.RATTLE_TAIL_RADIUS;

        // Tác động lên Rắn AI
        for (AISnake ai : aiSnakes) {
            if (!ai.isAlive()) continue;
            double d = Math.hypot(ai.getX() - originX, ai.getY() - originY);
            if (d < radius) {
                double pushAngle = Math.atan2(ai.getY() - originY, ai.getX() - originX);
                double pushDist = Config.SnakeConfig.RATTLE_TAIL_PUSH;
                ai.knockback(Math.cos(pushAngle) * pushDist, Math.sin(pushAngle) * pushDist);
                ai.stun(Config.SnakeConfig.RATTLE_TAIL_STUN);
                ai.takeDamage(15 + skillDamageBonus, null);
                if (texts != null) {
                    texts.spawn(ai.getX(), ai.getY() - 22, "★ CHOÁNG (1.8s)!", new Color(250, 204, 21), 1.5, 14);
                }
            }
        }

        // Tác động lên Boss Khổng Lồ
        for (Boss boss : bosses) {
            if (!boss.isAlive()) continue;
            double d = Math.hypot(boss.getX() - originX, boss.getY() - originY);
            if (d < radius + 30.0) {
                double pushAngle = Math.atan2(boss.getY() - originY, boss.getX() - originX);
                boss.knockback(Math.cos(pushAngle) * 45.0, Math.sin(pushAngle) * 45.0);
                boss.stun(1.5);
                boss.takeDamage(20 + skillDamageBonus, texts, particles);
                if (texts != null) {
                    texts.spawn(boss.getX(), boss.getY() - 36, "★ BOSS BỊ CHOÁNG (1.5s)!", new Color(250, 204, 21), 1.8, 16);
                }
            }
        }

        // Tác động lên Cá Sấu Đầm Nước
        if (crocodiles != null) {
            for (Crocodile croc : crocodiles) {
                if (!croc.isAlive()) continue;
                double d = Math.hypot(croc.getX() - originX, croc.getY() - originY);
                if (d < radius) {
                    croc.stun(2.0);
                    croc.takeDamage(20 + skillDamageBonus, texts, particles);
                    if (texts != null) {
                        texts.spawn(croc.getX(), croc.getY() - 20, "★ CHOÁNG!", new Color(250, 204, 21), 1.2, 13);
                    }
                }
            }
        }

        // Tác động lên Con mồi (làm choáng con mồi dễ săn bắt)
        for (Prey prey : preys) {
            if (!prey.isAlive()) continue;
            double d = Math.hypot(prey.getX() - originX, prey.getY() - originY);
            if (d < radius) {
                prey.stun(2.0);
            }
        }

        return true;
    }

    public boolean rattleTail(List<AISnake> aiSnakes, List<Boss> bosses, List<Prey> preys,
                              ParticleSystem particles, FloatingTextManager texts, Camera camera) {
        return rattleTail(aiSnakes, bosses, preys, null, particles, texts, camera);
    }

    public double getVenomCooldownTimer() { return venomCooldownTimer; }
    public double getRattleCooldownTimer() { return rattleCooldownTimer; }
}
