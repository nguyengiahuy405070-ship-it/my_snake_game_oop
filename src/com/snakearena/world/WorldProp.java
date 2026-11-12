package com.snakearena.world;

/**
 * Đại diện cho một vật cản hoặc đối tượng môi trường trên bản đồ (Cây, Đá, Bụi rậm, Bẫy thú).
 */
public class WorldProp {
    public enum Type {
        TREE,
        ROCK,
        MOUNTAIN,
        BUSH,
        CAVE,
        TRAP,
        WALL
    }

    private final Type type;
    private final double x;
    private final double y;
    private final double radius;
    private final int width;
    private final int height;
    private final int variant;

    // Riêng cho bẫy thú (TRAP)
    private boolean armed = true;
    private double triggerTimer = 0.0;

    public WorldProp(Type type, double x, double y, double radius, int width, int height, int variant) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.width = width;
        this.height = height;
        this.variant = variant;
    }

    public WorldProp(Type type, double x, double y, double radius, int width, int height) {
        this(type, x, y, radius, width, height, 0);
    }

    public void update(double dt) {
        if (type == Type.TRAP && !armed) {
            triggerTimer -= dt;
            if (triggerTimer <= 0) {
                armed = true; // Bẫy tự gài lại sau thời gian nguội (8s)
            }
        }
    }

    public Type getType() { return type; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getRadius() { return radius; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getVariant() { return variant; }
    public boolean isArmed() { return armed; }
    public void setArmed(boolean armed) { this.armed = armed; }
    public double getTriggerTimer() { return triggerTimer; }
    public void setTriggerTimer(double triggerTimer) { this.triggerTimer = triggerTimer; }
}
