package com.snakearena.entity;

import com.snakearena.core.Camera;
import com.snakearena.render.ParticleSystem;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;

/**
 * Đạn tia độc (Venom Spit) bắn ra theo đường thẳng từ miệng rắn (người chơi hoặc Boss).
 * Gây sát thương va chạm và gây sát thương theo thời gian (Poison DoT).
 */
public class PoisonBolt {
    private static final Color COLOR_PLAYER_GLOW = new Color(34, 197, 94, 95);
    private static final Color COLOR_PLAYER_BODY = new Color(74, 222, 128);
    private static final Color COLOR_PLAYER_CORE = new Color(220, 252, 231);

    private static final Color COLOR_HOSTILE_GLOW = new Color(168, 85, 247, 115);
    private static final Color COLOR_HOSTILE_RING = new Color(239, 68, 68, 175);
    private static final Color COLOR_HOSTILE_BODY = new Color(217, 70, 239);
    private static final Color COLOR_HOSTILE_CORE = new Color(253, 244, 255);

    private double x;
    private double y;
    private final double vx;
    private final double vy;
    private double lifetime = 1.05; // Tầm bắn khoảng 450px
    private boolean alive = true;
    private double trailTimer = 0.0;
    private final boolean hostileToPlayer;
    private final int damage;
    private final int dotDamage;

    public PoisonBolt(double x, double y, double angle, double speed) {
        this(x, y, angle, speed, false, 0, 0, 1.05);
    }

    public PoisonBolt(double x, double y, double angle, double speed,
                      boolean hostileToPlayer, int damage, int dotDamage) {
        this(x, y, angle, speed, hostileToPlayer, damage, dotDamage, 1.25);
    }

    public PoisonBolt(double x, double y, double angle, double speed,
                      boolean hostileToPlayer, int damage, int dotDamage, double lifetime) {
        this.x = x;
        this.y = y;
        this.vx = Math.cos(angle) * speed;
        this.vy = Math.sin(angle) * speed;
        this.hostileToPlayer = hostileToPlayer;
        this.damage = damage;
        this.dotDamage = dotDamage;
        this.lifetime = lifetime;
    }

    public void update(double dt, ParticleSystem particles) {
        if (!alive) return;
        x += vx * dt;
        y += vy * dt;
        lifetime -= dt;
        if (lifetime <= 0) {
            alive = false;
        }

        trailTimer += dt;
        if (particles != null && trailTimer > 0.04) {
            trailTimer = 0.0;
            particles.spawnPoisonBubble(x, y);
        }
    }

    public void render(Graphics2D g, Camera camera) {
        if (!alive) return;
        Point2D.Double screen = camera.worldToScreen(x, y);
        int sx = (int) screen.x;
        int sy = (int) screen.y;

        if (hostileToPlayer) {
            // Tia đạn độc Boss tím-hồng neon cảnh báo nguy hiểm cao
            g.setColor(COLOR_HOSTILE_GLOW);
            g.fillOval(sx - 17, sy - 17, 34, 34);
            g.setColor(COLOR_HOSTILE_RING);
            g.drawOval(sx - 12, sy - 12, 24, 24);
            g.setColor(COLOR_HOSTILE_BODY);
            g.fillOval(sx - 10, sy - 10, 20, 20);
            g.setColor(COLOR_HOSTILE_CORE);
            g.fillOval(sx - 5, sy - 5, 10, 10);
        } else {
            // Vầng hào quang độc tố lục quang mở rộng (Poison Glow)
            g.setColor(COLOR_PLAYER_GLOW);
            g.fillOval(sx - 15, sy - 15, 30, 30);

            // Thân phi đạn tia độc to lớn, bắt mắt hơn
            g.setColor(COLOR_PLAYER_BODY);
            g.fillOval(sx - 9, sy - 9, 18, 18);
            g.setColor(COLOR_PLAYER_CORE);
            g.fillOval(sx - 5, sy - 5, 10, 10);
        }
    }

    public boolean isAlive() { return alive; }
    public void setAlive(boolean alive) { this.alive = alive; }
    public double getX() { return x; }
    public double getY() { return y; }
    public boolean isHostileToPlayer() { return hostileToPlayer; }
    public int getDamage() { return damage; }
    public int getDotDamage() { return dotDamage; }
}
