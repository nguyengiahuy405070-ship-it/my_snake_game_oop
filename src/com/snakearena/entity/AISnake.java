package com.snakearena.entity;

import com.snakearena.core.Config;
import com.snakearena.render.ParticleSystem;
import com.snakearena.world.ChunkManager;
import java.util.List;

/**
 * Rắn do AI điều khiển với 2 vai trò:
 * 1. FORAGER: Săn tìm con mồi tự nhiên (Chuột, Ếch, Thỏ).
 * 2. HUNTER: Rắn sát thủ chủ động rình rập và săn đuổi Người Chơi.
 */
public class AISnake extends Snake {
    public enum Role {
        HUNTER,
        FORAGER
    }

    private final Role role;
    private boolean isHuntingPlayer = false;
    private double targetLockTimer = 0.0;
    private Prey lockedPrey = null;

    public AISnake(double x, double y) {
        this(x, y, (Math.random() < 0.60) ? Role.HUNTER : Role.FORAGER);
    }

    public AISnake(double x, double y, Role role) {
        super(x, y, false);
        this.role = role;
        this.speed = Config.SnakeConfig.BASE_SPEED * 0.85;
    }

    public void updateAI(double dt, ChunkManager world, List<Prey> preys, PlayerSnake player, ParticleSystem particles) {
        targetLockTimer -= dt;

        // CƠ CHẾ SĂN NGƯỜI CHƠI (PREDATOR HUNTING PLAYER)
        if (role == Role.HUNTER && player != null && player.isAlive()) {
            double distToPlayer = Math.hypot(player.getX() - x, player.getY() - y);

            // NẾU NGƯỜI CHƠI ẨN NẤP TRONG BỤI RẬM -> RẮN AI MẤT DẤU VẾT!
            if (player.isHidden()) {
                isHuntingPlayer = false;
            } else if (distToPlayer < Config.Ecosystem.AI_HUNTER_AGGRO_RADIUS) {
                // Người chơi lộ diện trong tầm săn -> Rắn AI lao vào truy đuổi!
                isHuntingPlayer = true;
                targetAngle = Math.atan2(player.getY() - y, player.getX() - x);
                speed = Config.Ecosystem.AI_HUNTER_SPEED;
            } else {
                isHuntingPlayer = false;
            }
        } else {
            isHuntingPlayer = false;
        }

        // Nếu không săn người chơi thì săn các con mồi xung quanh hoặc đi tuần tra
        if (!isHuntingPlayer) {
            speed = Config.SnakeConfig.BASE_SPEED * 0.80;

            if (targetLockTimer <= 0 || lockedPrey == null || !lockedPrey.isAlive()) {
                Prey nearest = null;
                double minDist = 380.0;
                for (Prey p : preys) {
                    if (!p.isAlive()) continue;
                    double d = Math.hypot(p.getX() - x, p.getY() - y);
                    if (d < minDist) {
                        minDist = d;
                        nearest = p;
                    }
                }
                lockedPrey = nearest;
                targetLockTimer = 2.5; // Khóa mục tiêu trong 2.5 giây chống rung giật
            }

            if (lockedPrey != null && lockedPrey.isAlive()) {
                targetAngle = Math.atan2(lockedPrey.getY() - y, lockedPrey.getX() - x);
            } else {
                if (Math.random() < 0.03) {
                    targetAngle += (Math.random() - 0.5) * 1.4;
                }
            }
        }

        // Cập nhật di chuyển vật lý
        super.update(dt, world, particles);
    }

    public Role getRole() { return role; }
    public boolean isHuntingPlayer() { return isHuntingPlayer; }
}
