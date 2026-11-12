package com.snakearena.core;

/**
 * Enum đại diện cho các hướng di chuyển trên lưới 2D.
 */
public enum Direction {
    UP(0, -1),
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0),
    NONE(0, 0);

    private final int dx;
    private final int dy;

    Direction(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public int getDx() {
        return dx;
    }

    public int getDy() {
        return dy;
    }

    public boolean isOpposite(Direction other) {
        if (other == null || other == NONE || this == NONE) return false;
        return this.dx + other.dx == 0 && this.dy + other.dy == 0;
    }

    public static Direction fromVector(int vx, int vy) {
        if (Math.abs(vx) > Math.abs(vy)) {
            return vx > 0 ? RIGHT : LEFT;
        } else if (vy != 0) {
            return vy > 0 ? DOWN : UP;
        }
        return NONE;
    }
}
