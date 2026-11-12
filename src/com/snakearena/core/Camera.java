package com.snakearena.core;

import java.awt.geom.Point2D;

/**
 * Quản lý camera bám theo đầu rắn với Lerp, Screen Shake và Frustum Culling.
 */
public class Camera {
    private int viewportWidth;
    private int viewportHeight;

    private double x = 0; // Tọa độ tâm camera trong thế giới
    private double y = 0;
    private double targetX = 0;
    private double targetY = 0;

    private double shakeIntensity = 0;
    private double shakeOffsetX = 0;
    private double shakeOffsetY = 0;

    public Camera(int viewportWidth, int viewportHeight) {
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
    }

    public void resize(int w, int h) {
        this.viewportWidth = w;
        this.viewportHeight = h;
    }

    public void follow(double targetX, double targetY, double targetAngle, double speedFactor) {
        // Đón đầu một chút theo hướng chuyển động của rắn
        double leadDist = Config.CameraConfig.LEAD_DISTANCE * speedFactor;
        this.targetX = targetX + Math.cos(targetAngle) * leadDist;
        this.targetY = targetY + Math.sin(targetAngle) * leadDist;
    }

    public void update(double dt) {
        // Làm mượt chuyển động bằng Lerp (hệ số 0.10)
        double lerpFactor = Config.CameraConfig.LERP_SPEED;
        this.x += (this.targetX - this.x) * lerpFactor;
        this.y += (this.targetY - this.y) * lerpFactor;

        // Xử lý rung chấn màn hình
        if (this.shakeIntensity > 0.1) {
            this.shakeOffsetX = (Math.random() - 0.5) * this.shakeIntensity * 2.0;
            this.shakeOffsetY = (Math.random() - 0.5) * this.shakeIntensity * 2.0;
            this.shakeIntensity *= Config.CameraConfig.SHAKE_DECAY;
        } else {
            this.shakeIntensity = 0;
            this.shakeOffsetX = 0;
            this.shakeOffsetY = 0;
        }
    }

    public void shake(double amount) {
        this.shakeIntensity = Math.min(30.0, this.shakeIntensity + amount);
    }

    public void snapTo(double x, double y) {
        this.x = x;
        this.y = y;
        this.targetX = x;
        this.targetY = y;
    }

    // Chuyển đổi tọa độ thế giới sang tọa độ màn hình
    public Point2D.Double worldToScreen(double wx, double wy) {
        return new Point2D.Double(
            wx - (this.x - this.viewportWidth / 2.0) + this.shakeOffsetX,
            wy - (this.y - this.viewportHeight / 2.0) + this.shakeOffsetY
        );
    }

    // Chuyển đổi tọa độ màn hình sang tọa độ thế giới
    public Point2D.Double screenToWorld(double sx, double sy) {
        return new Point2D.Double(
            sx + (this.x - this.viewportWidth / 2.0) - this.shakeOffsetX,
            sy + (this.y - this.viewportHeight / 2.0) - this.shakeOffsetY
        );
    }

    // Khung nhìn bao quanh có lề an toàn
    public static class ViewBounds {
        public double left, right, top, bottom;
        public ViewBounds(double l, double r, double t, double b) {
            this.left = l; this.right = r; this.top = t; this.bottom = b;
        }
    }

    public ViewBounds getViewBounds(double padding) {
        double halfW = this.viewportWidth / 2.0 + padding;
        double halfH = this.viewportHeight / 2.0 + padding;
        return new ViewBounds(
            this.x - halfW,
            this.x + halfW,
            this.y - halfH,
            this.y + halfH
        );
    }

    public boolean isVisible(double wx, double wy, double radius) {
        ViewBounds b = getViewBounds(radius);
        return wx >= b.left && wx <= b.right && wy >= b.top && wy <= b.bottom;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public int getViewportWidth() { return viewportWidth; }
    public int getViewportHeight() { return viewportHeight; }
}
