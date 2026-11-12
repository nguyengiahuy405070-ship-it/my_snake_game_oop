package com.snakearena.entity;

import com.snakearena.core.Camera;
import com.snakearena.core.Config;
import com.snakearena.core.SoundManager;
import com.snakearena.render.FloatingTextManager;
import com.snakearena.render.ParticleSystem;
import java.awt.Color;
import java.util.List;

/**
 * Đại bàng săn mồi trên không (Apex Aerial Predator).
 * Cơ chế Săn mồi Bổ nhào (Swoop Attack):
 * 1. TELEGRAPH: Bóng đen báo hiệu xuất hiện trên mặt đất vài giây, rình rập và khóa tọa độ.
 * 2. SWOOP: Bổ nhào cực nhanh từ trên cao xuống điểm đã khóa.
 * 3. IMPACT: Đập xuống mặt đất gây sát thương diện rộng (trừ khi mục tiêu trốn trong bụi rậm).
 * 4. ASCEND: Bay vút lên trời và rời đi.
 */
public class Eagle {
    public enum State {
        TELEGRAPH,  // Bóng báo hiệu trên mặt đất vài giây trước khi tấn công
        SWOOP,      // Lao xuống từ trên cao
        ASCEND      // Bay vút lên trời và biến mất
    }

    private State state = State.TELEGRAPH;
    private double timer;
    private boolean locked = false;

    // Tọa độ mục tiêu trên mặt đất (nơi bóng đổ và điểm dậm chân)
    private double groundX;
    private double groundY;

    // Tọa độ đại bàng trên không gian 3D
    private double x;
    private double y;
    private double altitude = 360.0; // Độ cao (360: bầu trời, 0: mặt đất)
    private double exitVx = 0.0;
    private double exitVy = 0.0;

    private boolean alive = true;
    private double wingTimer = 0.0;
    private boolean impactDone = false;
    private int stageId = 1;

    public Eagle(double targetStartX, double targetStartY, int stageId) {
        this.groundX = targetStartX;
        this.groundY = targetStartY;
        this.stageId = stageId;
        this.timer = Config.Ecosystem.EAGLE_TELEGRAPH_TIME;
        this.altitude = 360.0;
        this.x = groundX;
        this.y = groundY - 180.0;
        this.state = State.TELEGRAPH;
    }

    public Eagle(double targetStartX, double targetStartY) {
        this(targetStartX, targetStartY, 1);
    }

    public void update(double dt, List<? extends Snake> snakes, List<Prey> preys,
                       PlayerSnake player, Camera camera, ParticleSystem particles, FloatingTextManager texts) {
        wingTimer += dt;

        switch (state) {
            case TELEGRAPH -> {
                timer -= dt;

                // Trong giai đoạn rình rập chưa khóa (còn hơn 0.9s), bóng bám theo Người chơi
                if (timer > Config.Ecosystem.EAGLE_LOCK_TIME) {
                    if (player != null && player.isAlive()) {
                        // Lerp nhẹ nhàng bóng theo người chơi
                        groundX += (player.getX() - groundX) * 0.08;
                        groundY += (player.getY() - groundY) * 0.08;
                    }
                    locked = false;
                } else {
                    // Trong 0.9s cuối: KHÓA TỌA ĐỘ MẶT ĐẤT!
                    // Bóng dừng lại cố định để người chơi kịp dùng Sprint/Dash hoặc chui vào bụi cây né đòn
                    if (!locked) {
                        locked = true;
                        if (texts != null) {
                            String lockMsg = (stageId == 2) ? "⚠ HẢI ÂU KHÓA TỌA ĐỘ VỒ MỒI!" : "⚠ KHÓA TỌA ĐỘ VỒ MỒI!";
                            texts.spawn(groundX, groundY - 32, lockMsg, new Color(239, 68, 68), 0.9, 14);
                        }
                    }
                }

                // Vị trí đại bàng trên cao lượn quanh điểm ngắm
                x = groundX + Math.sin(wingTimer * 3.0) * 40.0;
                y = groundY - 180.0 + Math.cos(wingTimer * 2.0) * 20.0;

                if (timer <= 0) {
                    // Chuyển sang giai đoạn LAO XUỐNG (SWOOP)
                    state = State.SWOOP;
                    timer = Config.Ecosystem.EAGLE_DIVE_TIME;
                    SoundManager.playEagleDive();
                }
            }

            case SWOOP -> {
                timer -= dt;
                double progress = Math.max(0.0, Math.min(1.0, 1.0 - (timer / Config.Ecosystem.EAGLE_DIVE_TIME)));

                // Độ cao rơi thẳng đứng xuống mặt đất
                altitude = 360.0 * (1.0 - progress);
                x = groundX;
                y = groundY - altitude * 0.5;

                if (timer <= 0 && !impactDone) {
                    impactDone = true;
                    altitude = 0.0;

                    // ĐẬP XUỐNG MẶT ĐẤT (IMPACT)
                    if (camera != null) camera.shake(22.0);
                    if (particles != null) particles.spawnTrapSparks(groundX, groundY);

                    // 1. Kiểm tra sát thương lên Rắn (Người chơi & AI)
                    boolean hitAny = false;
                    for (Snake snake : snakes) {
                        if (!snake.isAlive()) continue;
                        double dist = Math.hypot(snake.getX() - groundX, snake.getY() - groundY);

                        if (dist < Config.Ecosystem.EAGLE_STRIKE_RADIUS) {
                            if (snake.isHidden()) {
                                // Người chơi/Rắn ẩn nấp trong bụi cây -> Đại bàng vồ hụt!
                                if (texts != null && snake.isPlayer()) {
                                    texts.spawn(snake.getX(), snake.getY() - 36, "[ẨN NẤP] TRỐN THOÁT THÀNH CÔNG!", new Color(134, 239, 172), 1.8, 16);
                                }
                            } else {
                                // Vồ trúng mục tiêu lộ diện!
                                if (snake.takeDamage(Config.Ecosystem.EAGLE_DAMAGE, camera)) {
                                    hitAny = true;
                                    if (texts != null) {
                                        if (snake.isPlayer()) {
                                            String pMsg = (stageId == 2)
                                                    ? "⚡ HẢI ÂU VỒ TRÚNG (-" + Config.Ecosystem.EAGLE_DAMAGE + " HP)!"
                                                    : "⚡ ĐẠI BÀNG VỒ TRÚNG (-" + Config.Ecosystem.EAGLE_DAMAGE + " HP)!";
                                            texts.spawn(snake.getX(), snake.getY() - 36, pMsg, new Color(239, 68, 68), 2.2, 16);
                                        } else {
                                            String aiMsg = (stageId == 2)
                                                    ? "⚡ HẢI ÂU BẮT CÁ MẬP (-" + Config.Ecosystem.EAGLE_DAMAGE + " HP)!"
                                                    : "⚡ ĐẠI BÀNG BẮT RẮN AI (-" + Config.Ecosystem.EAGLE_DAMAGE + " HP)!";
                                            texts.spawn(snake.getX(), snake.getY() - 28, aiMsg, new Color(248, 113, 113), 1.8, 14);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Nếu người chơi ở ngoài bán kính -> Né đòn thành công
                    if (!hitAny && player != null && player.isAlive() && !player.isHidden()) {
                        double dPlayer = Math.hypot(player.getX() - groundX, player.getY() - groundY);
                        if (dPlayer < 150.0 && texts != null) {
                            texts.spawn(groundX, groundY - 36, "✦ NÉ ĐÒN THÀNH CÔNG!", new Color(56, 189, 248), 1.8, 16);
                        }
                    }

                    // 2. Kiểm tra bắt con mồi xung quanh
                    for (Prey prey : preys) {
                        if (!prey.isAlive() || prey.isHidden()) continue;
                        double dist = Math.hypot(prey.getX() - groundX, prey.getY() - groundY);
                        if (dist < Config.Ecosystem.EAGLE_STRIKE_RADIUS) {
                            prey.setAlive(false);
                            if (texts != null) {
                                String mMsg = (stageId == 2) ? "🕊 HẢI ÂU BẮT CÁ!" : "🦅 ĐẠI BÀNG BẮT MỒI!";
                                texts.spawn(prey.getX(), prey.getY() - 20, mMsg, new Color(251, 191, 36), 1.6, 14);
                            }
                            break;
                        }
                    }

                    // Chuyển sang giai đoạn BAY LÊN (ASCEND)
                    state = State.ASCEND;
                    timer = Config.Ecosystem.EAGLE_ASCEND_TIME;

                    // Hướng bay vút ra xa
                    double exitAngle = -Math.PI * 0.4 + (Math.random() - 0.5) * 0.6;
                    exitVx = Math.cos(exitAngle) * 320.0;
                    exitVy = Math.sin(exitAngle) * 320.0;
                }
            }

            case ASCEND -> {
                timer -= dt;
                double progress = Math.max(0.0, Math.min(1.0, 1.0 - (timer / Config.Ecosystem.EAGLE_ASCEND_TIME)));

                // Bay vút lên cao
                altitude = 480.0 * progress;
                x += exitVx * dt;
                y += exitVy * dt;

                if (timer <= 0) {
                    alive = false; // Rời khỏi màn hình
                }
            }
        }
    }

    // Getters
    public State getState() { return state; }
    public double getTimer() { return timer; }
    public boolean isLocked() { return locked; }
    public double getGroundX() { return groundX; }
    public double getGroundY() { return groundY; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getAltitude() { return altitude; }
    public boolean isAlive() { return alive; }
    public double getWingTimer() { return wingTimer; }
    public int getStageId() { return stageId; }
}
