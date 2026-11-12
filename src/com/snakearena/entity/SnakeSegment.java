package com.snakearena.entity;

/**
 * Đại diện cho một đốt trên thân rắn trong mô phỏng vật lý dây mềm (Rope Physics).
 */
public class SnakeSegment {
    public double x;
    public double y;
    public double angle;

    public SnakeSegment(double x, double y, double angle) {
        this.x = x;
        this.y = y;
        this.angle = angle;
    }
}
