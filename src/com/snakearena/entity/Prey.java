package com.snakearena.entity;

import com.snakearena.world.ChunkManager;
import java.util.List;

/**
 * Lớp đại diện cho các loài con mồi trong chuỗi thức ăn (Thỏ, Ếch, Chuột, Bướm).
 * Hỗ trợ thuật toán né tránh kẻ săn mồi (Fleeing) và tăng tốc độ bơi lội khi ở dưới nước.
 */
public class Prey {
    public enum Type {
        RABBIT,
        FROG,
        MOUSE,
        BUTTERFLY
    }

    private double x;
    private double y;
    private final Type type;
    private final int foodValue;
    private final int energyValue;
    private final double baseSpeed;
    private final double fleeRadius;

    private double angle;
    private boolean alive = true;
    private boolean hidden = false;
    private boolean inWater = false;
    private double animTimer = 0.0;
    private double stunTimer = 0.0;

    private boolean waterOnly = false;

    public Prey(double x, double y, Type type, int foodValue, int energyValue, double baseSpeed, double fleeRadius) {
        this.x = x;
        this.y = y;
        this.type = type;
        this.foodValue = foodValue;
        this.energyValue = energyValue;
        this.baseSpeed = baseSpeed;
        this.fleeRadius = fleeRadius;
        this.angle = Math.random() * Math.PI * 2.0;
    }

    public void setWaterOnly(boolean waterOnly) {
        this.waterOnly = waterOnly;
    }

    public boolean isWaterOnly() {
        return waterOnly;
    }

    public void stun(double duration) {
        this.stunTimer = Math.max(this.stunTimer, duration);
    }

    public void update(double dt, ChunkManager world, List<? extends Snake> snakes) {
        if (!alive) return;
        animTimer += dt;
        if (stunTimer > 0) {
            stunTimer -= dt;
            return; // Đang bị choáng bởi Rung Đuôi
        }
        hidden = world.isInsideBush(x, y);
        inWater = world.isInWater(x, y);

        // Quét tìm kẻ săn mồi (Rắn) gần nhất trong bán kính hoảng sợ
        Snake nearestPredator = null;
        double minDist = fleeRadius;
        for (Snake snake : snakes) {
            if (!snake.isAlive()) continue;
            double d = Math.hypot(snake.getX() - x, snake.getY() - y);
            if (d < minDist) {
                minDist = d;
                nearestPredator = snake;
            }
        }

        double moveSpeed = baseSpeed;

        // Nếu là loài Ếch khi ở trong hồ nước -> Tăng vọt 50% tốc độ bơi
        if (type == Type.FROG && inWater && !waterOnly) {
            moveSpeed *= 1.5;
        }

        if (nearestPredator != null) {
            // Hoảng loạn tháo chạy khỏi kẻ săn mồi
            angle = Math.atan2(y - nearestPredator.getY(), x - nearestPredator.getX());
            moveSpeed *= 1.45;
        } else {
            // Thong dong gặm cỏ hoặc lượn lờ bơi lội
            if (Math.random() < 0.02) {
                angle += (Math.random() - 0.5) * 1.2;
            }
        }

        if (!waterOnly) {
            if (type == Type.FROG) {
                double hopCycle = animTimer % (inWater ? 1.0 : 1.6);
                if (hopCycle < 0.45) {
                    moveSpeed *= 2.6; // Nhảy bật lên
                } else {
                    moveSpeed = 0;   // Ngồi nghỉ
                }
            } else if (type == Type.BUTTERFLY) {
                angle += Math.sin(animTimer * 4.0) * 0.08;
                x += Math.cos(angle) * moveSpeed * dt;
                y += Math.sin(angle) * moveSpeed * dt;
                return;
            }
        } else {
            // Cá biển bơi lượn uốn lượn mềm mại theo dòng nước
            angle += Math.sin(animTimer * 3.5) * 0.05;
        }

        double nx = x + Math.cos(angle) * moveSpeed * dt;
        double ny = y + Math.sin(angle) * moveSpeed * dt;

        if (!world.checkCollision(nx, ny, 8.0).collided && (!waterOnly || world.isInWater(nx, ny))) {
            x = nx;
            y = ny;
        } else {
            angle += Math.PI * 0.75 + (Math.random() - 0.5) * 0.5; // Đổi hướng khi gặp bờ cát hoặc vật cản
        }
    }

    // Getters / Setters
    public double getX() { return x; }
    public double getY() { return y; }
    public Type getType() { return type; }
    public int getFoodValue() { return foodValue; }
    public int getEnergyValue() { return energyValue; }
    public double getAngle() { return angle; }
    public boolean isAlive() { return alive; }
    public void setAlive(boolean alive) { this.alive = alive; }
    public boolean isHidden() { return hidden; }
    public boolean isInWater() { return inWater; }
    public double getAnimTimer() { return animTimer; }
}
