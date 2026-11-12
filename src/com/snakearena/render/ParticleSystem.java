package com.snakearena.render;

import com.snakearena.core.Camera;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * ParticleSystem quản lý các hạt hiệu ứng thế giới thực:
 * Lấp lánh khi ăn mồi, bọt nước khi bơi, bụi khi sprint, tia lửa khi sập bẫy, hào quang tiến hóa.
 */
public class ParticleSystem {
    private static class Particle {
        double x, y;
        double vx, vy;
        Color color;
        double life;
        double maxLife;
        int size;

        Particle(double x, double y, double vx, double vy, Color color, double duration, int size) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.color = color;
            this.maxLife = duration;
            this.life = duration;
            this.size = size;
        }

        boolean update(double dt) {
            x += vx * dt;
            y += vy * dt;
            life -= dt;
            return life <= 0;
        }

        void render(Graphics2D g, Camera camera) {
            Point2D.Double screen = camera.worldToScreen(x, y);
            float alpha = (float) Math.max(0.0, Math.min(1.0, life / maxLife));
            Color c = new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (alpha * 255));
            g.setColor(c);
            g.fillRect((int) screen.x - size / 2, (int) screen.y - size / 2, size, size);
        }
    }

    private final List<Particle> particles = new ArrayList<>();

    public void update(double dt) {
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            if (it.next().update(dt)) {
                it.remove();
            }
        }
    }

    public void render(Graphics2D g, Camera camera) {
        int size = particles.size();
        for (int i = 0; i < size; i++) {
            if (i >= particles.size()) break;
            Particle p = particles.get(i);
            if (p != null && camera.isVisible(p.x, p.y, 32)) {
                p.render(g, camera);
            }
        }
    }

    public void spawnEat(double wx, double wy, Color color) {
        for (int i = 0; i < 14; i++) {
            double angle = Math.random() * Math.PI * 2;
            double speed = 30.0 + Math.random() * 60.0;
            double vx = Math.cos(angle) * speed;
            double vy = Math.sin(angle) * speed;
            double life = 0.4 + Math.random() * 0.4;
            particles.add(new Particle(wx, wy, vx, vy, color, life, 3));
        }
    }

    public void spawnSprintTrail(double wx, double wy, boolean inWater) {
        Color col = inWater ? new Color(186, 230, 253, 180) : new Color(250, 204, 21, 180);
        double vx = (Math.random() - 0.5) * 20.0;
        double vy = (Math.random() - 0.5) * 20.0;
        particles.add(new Particle(wx, wy, vx, vy, col, 0.4, 3));
    }

    public void spawnDashTrail(double wx, double wy) {
        for (int i = 0; i < 6; i++) {
            double vx = (Math.random() - 0.5) * 40.0;
            double vy = (Math.random() - 0.5) * 40.0;
            particles.add(new Particle(wx, wy, vx, vy, new Color(34, 211, 238), 0.5, 4));
        }
    }

    public void spawnWaterBubble(double wx, double wy) {
        double vx = (Math.random() - 0.5) * 10.0;
        double vy = (Math.random() - 0.5) * 10.0;
        particles.add(new Particle(wx, wy, vx, vy, new Color(147, 197, 253, 160), 0.6, 2));
    }

    public void spawnTrapSparks(double wx, double wy) {
        for (int i = 0; i < 16; i++) {
            double angle = Math.random() * Math.PI * 2;
            double speed = 40.0 + Math.random() * 80.0;
            particles.add(new Particle(wx, wy, Math.cos(angle) * speed, Math.sin(angle) * speed, new Color(248, 113, 113), 0.5, 3));
        }
    }

    public void spawnEvolutionAura(double wx, double wy, Color color) {
        for (int i = 0; i < 24; i++) {
            double angle = Math.random() * Math.PI * 2;
            double speed = 20.0 + Math.random() * 60.0;
            particles.add(new Particle(wx, wy, Math.cos(angle) * speed, Math.sin(angle) * speed, color, 0.8, 4));
        }
    }

    public void spawnPoisonBubble(double wx, double wy) {
        double vx = (Math.random() - 0.5) * 16.0;
        double vy = (Math.random() - 0.5) * 16.0;
        particles.add(new Particle(wx, wy, vx, vy, new Color(74, 222, 128, 200), 0.45, 3));
    }

    public void spawnPoisonSplash(double wx, double wy) {
        for (int i = 0; i < 18; i++) {
            double angle = Math.random() * Math.PI * 2;
            double speed = 35.0 + Math.random() * 85.0;
            particles.add(new Particle(wx, wy, Math.cos(angle) * speed, Math.sin(angle) * speed,
                    new Color(34, 197, 94), 0.6, 4));
        }
    }

    public void spawnShockwaveRing(double wx, double wy) {
        for (int i = 0; i < 32; i++) {
            double angle = i * (Math.PI * 2.0 / 32.0);
            double speed = 140.0 + Math.random() * 50.0;
            particles.add(new Particle(wx, wy, Math.cos(angle) * speed, Math.sin(angle) * speed,
                    new Color(250, 204, 21), 0.5, 4));
        }
    }

    public void clear() {
        particles.clear();
    }
}
