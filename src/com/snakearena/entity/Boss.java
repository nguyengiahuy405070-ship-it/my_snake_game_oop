package com.snakearena.entity;

import com.snakearena.core.Camera;
import com.snakearena.core.Config;
import com.snakearena.core.GamePanel;
import com.snakearena.core.SoundManager;
import com.snakearena.render.FloatingTextManager;
import com.snakearena.render.ParticleSystem;
import com.snakearena.world.ChunkManager;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Lớp đại diện cho các Boss Khổng Lồ trong hệ sinh thái:
 * - Map 1 (Rừng / Thảo Nguyên - BiomeType.FOREST):
 *   1. BEAR (Bạch Mao Hùng)
 *   2. GIANT_PYTHON (Thái Cổ Cự Trăn)
 * - Map 2 (Đại Dương / Rạn San Hô - BiomeType.OCEAN):
 *   3. OCTOPUS (Bạch Tuộc Cự Đại)
 *   4. SWAMP_KING (Hải Long Cự Thần)
 * - Map 3 / Đặc biệt (BiomeType.VOLCANO):
 *   5. VOID_DRAGON (Hư Không Thần Long)
 *
 * Tất cả Boss dùng chung hệ thống hồi phục sau đòn (2.0s = 2000ms đứng yên hồi Mana).
 */
public class Boss {
    public enum Type {
        BEAR,
        GIANT_PYTHON,
        OCTOPUS,
        SWAMP_KING,
        VOID_DRAGON
    }

    public enum BiomeType {
        FOREST,
        OCEAN,
        VOLCANO
    }

    public enum BossActionState {
        ACTIVE,
        RECOVERING
    }

    public enum BearState {
        PATROL,
        CHASE,
        WINDUP,
        LUNGE,
        BITE,
        RETREAT,
        RECOVER
    }

    public enum SnakeBossState {
        CHASE,
        ROAR_WINDUP,
        VENOM_SPIT,
        COIL_DASH
    }

    public enum SnakeSkillType {
        NONE,
        VENOM_VOLLEY,
        COIL_DASH_WHIP
    }

    public static final Type[] STAGE_1_FOREST_BOSSES = { Type.BEAR, Type.GIANT_PYTHON };
    public static final Type[] STAGE_2_OCEAN_BOSSES  = { Type.OCTOPUS, Type.SWAMP_KING };
    public static final Type[] STAGE_3_VOLCANO_BOSSES = { Type.VOID_DRAGON };

    private static final double BEAR_COLLISION_RADIUS = 22.0;
    private static final double SNAKE_COLLISION_RADIUS = 24.0;
    private static final double BEAR_DUST_FX_DURATION = 0.28; // 4 khung x 0.07s
    private static final double BEAR_BITE_FX_DURATION = 0.25; // 5 khung x 0.05s
    private static final double TAIL_WHIP_FX_DURATION = 0.45;

    private final Type type;
    private final BiomeType biome;
    private double x;
    private double y;
    private double angle = 0.0;
    private int health;
    private final int maxHealth;
    private boolean alive = true;
    private double stunTimer = 0.0;
    private double attackCooldown = 0.0;
    private double skillCooldown = 0.0;
    private double animTimer = 0.0;
    private boolean isCharging = false;
    private double enrageThreshold;

    // Trạng thái & Bộ đếm khoảng nghỉ 2 giây (2000ms) hồi Mana chung cho toàn bộ Boss
    private final long restDurationMs = Config.BossConfig.BOSS_REST_DURATION_MS;
    private final double restDuration = Config.BossConfig.BOSS_RECOVERY_DURATION;
    private double recoveryTimer = 0.0;
    private BossActionState actionState = BossActionState.ACTIVE;
    private double mana = Config.BossConfig.BOSS_MAX_MANA;
    private final double maxMana = Config.BossConfig.BOSS_MAX_MANA;

    // Máy trạng thái riêng cho Boss Gấu (BEAR)
    private BearState bearState = BearState.PATROL;
    private double stateTime = 0.0;
    private double lockedTargetX = 0.0;
    private double lockedTargetY = 0.0;
    private double lungeDirX = 1.0;
    private double lungeDirY = 0.0;
    private boolean biteDamageDone = false;
    private boolean biteHitPlayer = false;
    private double walkAnimTimer = 0.0;
    private boolean bearMoving = false;
    private double lungeTraveled = 0.0;
    private double lungeExpectedDist = Config.BossConfig.BEAR_LUNGE_MAX_DIST;
    private int bearLungeComboCount = 0;
    private boolean wasStunned = false;
    private boolean postStunRecover = false;

    // Máy trạng thái kỹ năng riêng cho Boss Rắn (GIANT_PYTHON & VOID_DRAGON)
    private SnakeBossState snakeState = SnakeBossState.CHASE;
    private SnakeSkillType snakeSkillType = SnakeSkillType.NONE;
    private double snakeSkillTime = 0.0;
    private double snakeDashTraveled = 0.0;
    private boolean snakeDashHitPlayer = false;
    private boolean snakeWhipDamageDone = false;
    private boolean venomShotFired = false;
    private int snakeSkillCycle = 0;
    private double tailWhipFxTimer = -1.0;
    private double tailWhipFxX = 0.0;
    private double tailWhipFxY = 0.0;

    // Bộ đếm FX sprite cho Boss Gấu (bụi chân & vết táp)
    private double bearDustFxTimer = -1.0;
    private double bearDustFxX = 0.0;
    private double bearDustFxY = 0.0;
    private double bearBiteFxTimer = -1.0;
    private double bearBiteFxX = 0.0;
    private double bearBiteFxY = 0.0;
    private double bearBiteFxAngle = 0.0;

    // Trạng thái Trúng Độc (Poison DoT)
    private double poisonTimer = 0.0;
    private double poisonTickTimer = 0.0;
    private int poisonDmgPerTick = 0;

    // Riêng cho Boss Trăn Khổng Lồ & Thần Long: Hệ thống đốt thân Rope Physics cỡ lớn
    private final List<SnakeSegment> segments = new ArrayList<>();

    public Boss(Type type, double x, double y) {
        this.type = type;
        this.biome = getBiomeForType(type);
        this.x = x;
        this.y = y;
        if (type == Type.BEAR) {
            this.maxHealth = Config.BossConfig.BEAR_MAX_HP;
            this.enrageThreshold = this.maxHealth * Config.BossConfig.BEAR_ENRAGE_HP_RATIO;
            this.bearState = BearState.PATROL;
        } else if (type == Type.GIANT_PYTHON) {
            this.maxHealth = Config.BossConfig.PYTHON_MAX_HP;
            this.enrageThreshold = this.maxHealth * 0.5;
            for (int i = 0; i < Config.BossConfig.PYTHON_SEGMENTS; i++) {
                segments.add(new SnakeSegment(x - i * 22.0, y, 0.0));
            }
        } else if (type == Type.OCTOPUS) {
            this.maxHealth = Config.BossConfig.OCTOPUS_MAX_HP;
            this.enrageThreshold = this.maxHealth * 0.45;
        } else if (type == Type.SWAMP_KING) {
            this.maxHealth = Config.BossConfig.SWAMP_KING_MAX_HP;
            this.enrageThreshold = this.maxHealth * 0.45;
        } else {
            this.maxHealth = Config.BossConfig.VOID_DRAGON_MAX_HP;
            this.enrageThreshold = this.maxHealth * 0.5;
            for (int i = 0; i < 24; i++) {
                segments.add(new SnakeSegment(x - i * 24.0, y, 0.0));
            }
        }
        this.health = this.maxHealth;
    }

    public static BiomeType getBiomeForType(Type type) {
        if (type == null) return BiomeType.FOREST;
        return switch (type) {
            case BEAR, GIANT_PYTHON -> BiomeType.FOREST;
            case OCTOPUS, SWAMP_KING -> BiomeType.OCEAN;
            case VOID_DRAGON -> BiomeType.VOLCANO;
        };
    }

    public static BiomeType getBiomeForStage(int stageId) {
        return switch (stageId) {
            case 2 -> BiomeType.OCEAN;
            case 3 -> BiomeType.VOLCANO;
            default -> BiomeType.FOREST;
        };
    }

    public static Type[] getBossesForStage(int stageId) {
        return switch (stageId) {
            case 2 -> STAGE_2_OCEAN_BOSSES.clone();
            case 3 -> STAGE_3_VOLCANO_BOSSES.clone();
            default -> STAGE_1_FOREST_BOSSES.clone();
        };
    }

    public static boolean isBossAllowedInStage(Type type, int stageId) {
        return type != null && getBiomeForType(type) == getBiomeForStage(stageId);
    }

    public static Type getBossForStageLevel(int stageId, int level) {
        if (stageId == 2) {
            if (level == 2) return Type.OCTOPUS;
            if (level >= 3) return Type.SWAMP_KING;
            return null;
        }
        if (stageId == 3) {
            return (level >= 2) ? Type.VOID_DRAGON : null;
        }
        // Map 1 (Forest): CHỈ có Boss hệ Rừng (Bạch Mao Hùng Lv.2, Thái Cổ Cự Trăn Lv.3)
        if (level == 2) return Type.BEAR;
        if (level >= 3) return Type.GIANT_PYTHON;
        return null;
    }

    private static final Color COLOR_BEAR_BITE_TEXT = new Color(239, 68, 68);
    private static final Color COLOR_BEAR_TRAP_TEXT = new Color(250, 204, 21);
    private static final Color COLOR_PYTHON_ROAR_TEXT = new Color(217, 70, 239);
    private static final Color COLOR_PYTHON_VENOM_TEXT = new Color(192, 132, 252);
    private static final Color COLOR_PYTHON_WHIP_TEXT = new Color(250, 204, 21);
    private static final Color COLOR_BOSS_POISON_DOT_TEXT = new Color(74, 222, 128);
    private static final Color COLOR_OCEAN_SKILL_TEXT = new Color(56, 189, 248);
    private static final Color COLOR_OCTO_SKILL_TEXT = new Color(244, 63, 94);

    /**
     * Đưa Boss vào trạng thái nghỉ 2.0 giây (2000ms) đứng yên tại chỗ để hồi Mana.
     */
    public void enterRecoveryState() {
        this.recoveryTimer = this.restDuration;
        this.actionState = BossActionState.RECOVERING;
        this.isCharging = false;
        this.bearMoving = false;
        this.stateTime = 0.0;
        if (this.type == Type.BEAR) {
            this.bearState = BearState.RECOVER;
        } else if (this.type == Type.GIANT_PYTHON || this.type == Type.VOID_DRAGON) {
            this.snakeState = SnakeBossState.CHASE;
            this.snakeSkillType = SnakeSkillType.NONE;
            this.snakeSkillTime = 0.0;
        }
    }

    public void update(double dt, PlayerSnake player, ChunkManager world,
                       Camera camera, ParticleSystem particles, FloatingTextManager texts) {
        update(dt, player, world, null, camera, particles, texts);
    }

    public void update(double dt, PlayerSnake player, ChunkManager world, List<PoisonBolt> projectiles,
                       Camera camera, ParticleSystem particles, FloatingTextManager texts) {
        if (!alive) return;
        animTimer += dt;

        // Cập nhật timer FX của Boss Gấu (bụi chân & vết táp) và Quẫy đuôi Boss Rắn
        if (type == Type.BEAR) {
            if (bearDustFxTimer >= 0.0) {
                bearDustFxTimer += dt;
                if (bearDustFxTimer >= BEAR_DUST_FX_DURATION) {
                    bearDustFxTimer = -1.0;
                }
            }
            if (bearBiteFxTimer >= 0.0) {
                bearBiteFxTimer += dt;
                if (bearBiteFxTimer >= BEAR_BITE_FX_DURATION) {
                    bearBiteFxTimer = -1.0;
                }
            }
        }
        if (tailWhipFxTimer >= 0.0) {
            tailWhipFxTimer += dt;
            if (tailWhipFxTimer >= TAIL_WHIP_FX_DURATION) {
                tailWhipFxTimer = -1.0;
            }
        }

        // Xử lý Trúng Độc DoT từ chiêu Phun Độc
        if (poisonTimer > 0) {
            poisonTimer -= dt;
            poisonTickTimer -= dt;
            if (poisonTickTimer <= 0) {
                poisonTickTimer = 0.5;
                takeDamage(poisonDmgPerTick, texts, particles);
                if (texts != null) {
                    texts.spawn(x, y - 32, "DOC RUT (-" + poisonDmgPerTick + " HP)!", COLOR_BOSS_POISON_DOT_TEXT, 1.0, 14);
                }
            }
        }

        // Xử lý bị choáng (Sập bẫy hoặc Rung đuôi)
        if (stunTimer > 0) {
            stunTimer -= dt;
            if (stunTimer <= 0.0) {
                stunTimer = 0.0;
                if (wasStunned) {
                    wasStunned = false;
                    enterRecoveryState();
                    if (type == Type.BEAR) {
                        postStunRecover = true;
                        attackCooldown = Config.BossConfig.BEAR_STUN_RECOVERY_COOLDOWN;
                    }
                }
            }
            return;
        }

        // Khoảng nghỉ 2.0 giây (2000ms) đứng yên hồi Mana áp dụng cho MỌI Boss sau khi ra đòn/kỹ năng
        if (recoveryTimer > 0.0) {
            actionState = BossActionState.RECOVERING;
            isCharging = false;
            bearMoving = false;
            stateTime += dt;
            mana = Math.min(maxMana, mana + Config.BossConfig.BOSS_MANA_REGEN_RECOVERY * dt);

            if (!segments.isEmpty()) {
                segments.get(0).x = x;
                segments.get(0).y = y;
                segments.get(0).angle = angle;
            }

            recoveryTimer -= dt;
            if (recoveryTimer <= 0.0) {
                recoveryTimer = 0.0;
                actionState = BossActionState.ACTIVE;
                if (type == Type.BEAR && bearState == BearState.RECOVER) {
                    if (!postStunRecover) {
                        attackCooldown = Config.BossConfig.BEAR_ATTACK_COOLDOWN;
                    }
                    postStunRecover = false;
                    bearState = BearState.CHASE;
                    stateTime = 0.0;
                }
            }
            return;
        }

        actionState = BossActionState.ACTIVE;
        mana = Math.min(maxMana, mana + Config.BossConfig.BOSS_MANA_REGEN_ACTIVE * dt);
        if (attackCooldown > 0.0) {
            attackCooldown = Math.max(0.0, attackCooldown - dt);
        }
        if (skillCooldown > 0.0) {
            skillCooldown = Math.max(0.0, skillCooldown - dt);
        }

        boolean enraged = health <= enrageThreshold;

        if (type == Type.BEAR) {
            updateBear(dt, player, world, camera, particles, texts, enraged);
        } else if (type == Type.GIANT_PYTHON) {
            updatePython(dt, player, world, projectiles, camera, particles, texts, enraged);
        } else if (type == Type.OCTOPUS) {
            updateOctopus(dt, player, world, projectiles, camera, particles, texts, enraged);
        } else if (type == Type.SWAMP_KING) {
            updateSwampKing(dt, player, world, projectiles, camera, particles, texts, enraged);
        } else {
            updateVoidDragon(dt, player, world, projectiles, camera, particles, texts, enraged);
        }
    }

    private void updateBear(double dt, PlayerSnake player, ChunkManager world,
                            Camera camera, ParticleSystem particles, FloatingTextManager texts, boolean enraged) {
        bearMoving = false;
        double distToPlayer = Math.hypot(player.getX() - x, player.getY() - y);

        switch (bearState) {
            case PATROL -> {
                if (!player.isHidden() && distToPlayer < Config.BossConfig.BEAR_DETECT_RADIUS) {
                    bearState = BearState.CHASE;
                    stateTime = 0.0;
                    bearLungeComboCount = 0;
                    handleBearChase(dt, player, world, distToPlayer, enraged);
                } else {
                    handleBearPatrol(dt, player, world);
                }
            }
            case CHASE -> {
                if (player.isHidden() || distToPlayer >= Config.BossConfig.BEAR_DETECT_RADIUS) {
                    isCharging = false;
                    bearState = BearState.PATROL;
                    stateTime = 0.0;
                    handleBearPatrol(dt, player, world);
                } else {
                    handleBearChase(dt, player, world, distToPlayer, enraged);
                }
            }
            case WINDUP -> {
                if (player.isHidden() || distToPlayer > Config.BossConfig.BEAR_LOST_SIGHT_MAX_DIST) {
                    bearState = BearState.CHASE;
                    stateTime = 0.0;
                    isCharging = false;
                    break;
                }
                isCharging = false;
                angle = Math.atan2(player.getY() - y, player.getX() - x);
                lockedTargetX = player.getX();
                lockedTargetY = player.getY();
                stateTime += dt;
                double windupDur = Config.BossConfig.BEAR_WINDUP_TIME
                        * (enraged ? Config.BossConfig.BEAR_ENRAGE_WINDUP_MULT : 1.0);
                if (stateTime >= windupDur) {
                    startBearLunge(camera);
                }
            }
            case LUNGE -> {
                if (distToPlayer > Config.BossConfig.BEAR_LOST_SIGHT_MAX_DIST) {
                    bearState = BearState.CHASE;
                    stateTime = 0.0;
                    isCharging = false;
                    bearLungeComboCount = 0;
                    break;
                }
                stateTime += dt;
                isCharging = true;
                double lungeSpd = Config.BossConfig.BEAR_LUNGE_SPEED
                        * (enraged ? Config.BossConfig.BEAR_ENRAGE_LUNGE_SPEED_MULT : 1.0);
                double step = lungeSpd * dt;
                double remainingMax = Math.max(0.0, Config.BossConfig.BEAR_LUNGE_MAX_DIST - lungeTraveled);
                double dxLock = lockedTargetX - x;
                double dyLock = lockedTargetY - y;
                double dotToLock = dxLock * lungeDirX + dyLock * lungeDirY;
                double moveStep = (dotToLock > 0.0) ? Math.min(Math.min(step, remainingMax), dotToLock) : 0.0;

                double nextX = x + lungeDirX * moveStep;
                double nextY = y + lungeDirY * moveStep;
                if (world != null && world.checkCollision(nextX, nextY, BEAR_COLLISION_RADIUS).collided) {
                    bearState = BearState.CHASE;
                    stateTime = 0.0;
                    isCharging = false;
                    bearLungeComboCount = 0;
                    break;
                }

                double prevX = x;
                double prevY = y;
                x = nextX;
                y = nextY;
                lungeTraveled += moveStep;
                recordBearStepDist(prevX, prevY);

                double newDistToPlayer = Math.hypot(player.getX() - x, player.getY() - y);
                double newDotToLock = (lockedTargetX - x) * lungeDirX + (lockedTargetY - y) * lungeDirY;
                boolean reachedLock = newDotToLock <= 1.0;
                boolean reachedMaxDist = lungeTraveled >= Config.BossConfig.BEAR_LUNGE_MAX_DIST - 0.5;
                boolean reachedBiteRange = !player.isHidden() && newDistToPlayer <= Config.BossConfig.BEAR_BITE_RADIUS;

                if (reachedLock || reachedMaxDist || reachedBiteRange) {
                    enterBearBite(player, camera, texts);
                }
            }
            case BITE -> {
                if (!biteDamageDone) {
                    performBearBiteHitCheck(player, camera, texts);
                }
                isCharging = false;
                stateTime += dt;
                if (stateTime >= Config.BossConfig.BEAR_BITE_TIME) {
                    if (enraged && biteHitPlayer && !player.isHidden()
                            && bearLungeComboCount < 2
                            && distToPlayer < Config.BossConfig.BEAR_DETECT_RADIUS
                            && Config.BossConfig.BEAR_ENRAGE_DOUBLE_LUNGE_CHANCE > 0.0
                            && Math.random() < Config.BossConfig.BEAR_ENRAGE_DOUBLE_LUNGE_CHANCE) {
                        lockedTargetX = player.getX();
                        lockedTargetY = player.getY();
                        startBearLunge(camera);
                    } else {
                        bearLungeComboCount = 0;
                        bearState = BearState.RETREAT;
                        stateTime = 0.0;
                    }
                }
            }
            case RETREAT -> {
                isCharging = false;
                stateTime += dt;
                double retreatDur = Config.BossConfig.BEAR_RETREAT_TIME
                        * (enraged ? Config.BossConfig.BEAR_ENRAGE_RETREAT_MULT : 1.0);
                double progress = Math.max(0.0, Math.min(1.0, stateTime / Math.max(0.01, retreatDur)));
                if (!player.isHidden()) {
                    angle = Math.atan2(player.getY() - y, player.getX() - x);
                }
                double retreatSpd = Config.BossConfig.BEAR_RETREAT_SPEED * (1.0 - progress);
                double prevX = x;
                double prevY = y;
                moveBearWithCollision(world, -Math.cos(angle) * retreatSpd * dt, -Math.sin(angle) * retreatSpd * dt);
                recordBearStepDist(prevX, prevY);

                if (stateTime >= retreatDur) {
                    postStunRecover = false;
                    enterRecoveryState();
                }
            }
            case RECOVER -> {
                // Nếu vì lý do đặc biệt test đặt bearState = RECOVER trực tiếp, tự đồng bộ hồi phục
                if (recoveryTimer <= 0.0) {
                    enterRecoveryState();
                }
            }
        }

        // Kiểm tra sập bẫy thú (Điểm yếu chí mạng của Boss Gấu — ngắt mọi trạng thái!)
        checkBearTrap(world, texts, particles, camera);
    }

    private void handleBearPatrol(double dt, PlayerSnake player, ChunkManager world) {
        stateTime += dt;
        isCharging = false;
        if (Math.random() < 0.02) {
            angle += (Math.random() - 0.5) * 1.2;
        }
        double spd = Config.BossConfig.BEAR_BASE_SPEED * (player.isHidden() ? 0.7 : 0.6);
        double prevX = x;
        double prevY = y;
        if (!moveBearWithCollision(world, Math.cos(angle) * spd * dt, Math.sin(angle) * spd * dt)) {
            angle += Math.PI * 0.65;
        }
        recordBearStepDist(prevX, prevY);
    }

    private void handleBearChase(double dt, PlayerSnake player, ChunkManager world, double distToPlayer, boolean enraged) {
        angle = Math.atan2(player.getY() - y, player.getX() - x);
        if (distToPlayer < Config.BossConfig.BEAR_TRIGGER_DIST && attackCooldown <= 0.0) {
            bearState = BearState.WINDUP;
            stateTime = 0.0;
            isCharging = false;
            bearLungeComboCount = 0;
            lockedTargetX = player.getX();
            lockedTargetY = player.getY();
            return;
        }
        stateTime += dt;
        isCharging = distToPlayer < 240.0;
        double spd = isCharging
                ? (enraged ? Config.BossConfig.BEAR_CHARGE_SPEED * 1.25 : Config.BossConfig.BEAR_CHARGE_SPEED)
                : Config.BossConfig.BEAR_BASE_SPEED;
        double prevX = x;
        double prevY = y;
        if (!moveBearWithCollision(world, Math.cos(angle) * spd * dt, Math.sin(angle) * spd * dt)) {
            double alt1 = angle + Math.PI * 0.45;
            if (!moveBearWithCollision(world, Math.cos(alt1) * spd * dt, Math.sin(alt1) * spd * dt)) {
                double alt2 = angle - Math.PI * 0.45;
                moveBearWithCollision(world, Math.cos(alt2) * spd * dt, Math.sin(alt2) * spd * dt);
            }
        }
        recordBearStepDist(prevX, prevY);
    }

    private void startBearLunge(Camera camera) {
        double dx = lockedTargetX - x;
        double dy = lockedTargetY - y;
        double dist = Math.hypot(dx, dy);
        if (dist > 1e-4) {
            lungeDirX = dx / dist;
            lungeDirY = dy / dist;
            angle = Math.atan2(dy, dx);
        } else {
            lungeDirX = Math.cos(angle);
            lungeDirY = Math.sin(angle);
        }
        if (bearLungeComboCount == 0) {
            mana = Math.max(0.0, mana - Config.BossConfig.BEAR_LUNGE_MANA_COST);
        }
        lungeExpectedDist = Math.max(1.0, Math.min(Config.BossConfig.BEAR_LUNGE_MAX_DIST, dist));
        lungeTraveled = 0.0;
        bearState = BearState.LUNGE;
        stateTime = 0.0;
        isCharging = true;
        biteDamageDone = false;
        biteHitPlayer = false;
        bearLungeComboCount++;
        bearDustFxX = x;
        bearDustFxY = y;
        bearDustFxTimer = 0.0;
        if (camera != null) {
            camera.shake(4.0);
        }
    }

    private void enterBearBite(PlayerSnake player, Camera camera, FloatingTextManager texts) {
        bearState = BearState.BITE;
        stateTime = 0.0;
        isCharging = false;
        biteDamageDone = false;
        biteHitPlayer = false;
        performBearBiteHitCheck(player, camera, texts);
    }

    private void performBearBiteHitCheck(PlayerSnake player, Camera camera, FloatingTextManager texts) {
        if (biteDamageDone) return;

        bearBiteFxAngle = angle;
        bearBiteFxX = x + Math.cos(angle) * 56.0;
        bearBiteFxY = y + Math.sin(angle) * 56.0;
        bearBiteFxTimer = 0.0;

        double dx = player.getX() - x;
        double dy = player.getY() - y;
        double dist = Math.hypot(dx, dy);
        double toPlayerAngle = (dist > 1e-4) ? Math.atan2(dy, dx) : angle;
        double angleDiff = normalizeAngleDiff(toPlayerAngle - angle);
        double maxHalfArcRad = Math.toRadians(Config.BossConfig.BEAR_BITE_ARC_DEG);

        if (!player.isHidden() && dist <= Config.BossConfig.BEAR_BITE_RADIUS && Math.abs(angleDiff) <= maxHalfArcRad) {
            if (player.takeDamage(Config.BossConfig.BEAR_DAMAGE, camera)) {
                if (texts != null) {
                    texts.spawn(player.getX(), player.getY() - 36,
                            "GAU TAP (-" + Config.BossConfig.BEAR_DAMAGE + " HP)",
                            COLOR_BEAR_BITE_TEXT, 1.8, 16);
                }
                if (camera != null) {
                    camera.shake(10.0);
                }
                GamePanel.triggerHitStop(Config.BossConfig.BEAR_HIT_STOP_TIME);
                SoundManager.playHit();
                biteHitPlayer = true;
            }
        }
        biteDamageDone = true;
    }

    private static double normalizeAngleDiff(double diff) {
        while (diff < -Math.PI) diff += Math.PI * 2.0;
        while (diff > Math.PI) diff -= Math.PI * 2.0;
        return diff;
    }

    private boolean moveBearWithCollision(ChunkManager world, double stepX, double stepY) {
        return moveWithCollision(world, stepX, stepY, BEAR_COLLISION_RADIUS);
    }

    private boolean moveWithCollision(ChunkManager world, double stepX, double stepY, double colRadius) {
        if (world == null) {
            x += stepX;
            y += stepY;
            return true;
        }
        if (!world.checkCollision(x + stepX, y + stepY, colRadius).collided) {
            x += stepX;
            y += stepY;
            return true;
        }
        if (Math.abs(stepX) > 1e-4 && !world.checkCollision(x + stepX, y, colRadius).collided) {
            x += stepX;
            return true;
        }
        if (Math.abs(stepY) > 1e-4 && !world.checkCollision(x, y + stepY, colRadius).collided) {
            y += stepY;
            return true;
        }
        return false;
    }

    private void recordBearStepDist(double prevX, double prevY) {
        double moved = Math.hypot(x - prevX, y - prevY);
        if (moved > 1e-4) {
            bearMoving = true;
            walkAnimTimer += moved / Math.max(1.0, Config.BossConfig.BEAR_BASE_SPEED);
        }
    }

    private void checkBearTrap(ChunkManager world, FloatingTextManager texts, ParticleSystem particles, Camera camera) {
        if (world == null) return;
        ChunkManager.TrapResult trap = world.checkTrapTrigger(x, y, BEAR_COLLISION_RADIUS);
        if (trap != null && trap.triggered) {
            interruptBearStateForStun();
            stunTimer = 2.5; // Choáng 2.5 giây
            takeDamage(120, texts, particles);
            if (texts != null) {
                texts.spawn(x, y - 78, "GAU SAP BAY KEP (-120 HP & CHOANG)!", COLOR_BEAR_TRAP_TEXT, 2.5, 18);
            }
            if (particles != null) {
                particles.spawnTrapSparks(x, y);
            }
            if (camera != null) camera.shake(18.0);
        }
    }

    private void interruptBearStateForStun() {
        this.wasStunned = true;
        this.recoveryTimer = 0.0;
        this.actionState = BossActionState.ACTIVE;
        this.bearState = BearState.PATROL;
        this.stateTime = 0.0;
        this.isCharging = false;
        this.bearMoving = false;
        this.biteDamageDone = false;
        this.biteHitPlayer = false;
        this.bearLungeComboCount = 0;
        this.postStunRecover = false;
    }

    private void interruptSnakeStateForStun() {
        this.wasStunned = true;
        this.recoveryTimer = 0.0;
        this.actionState = BossActionState.ACTIVE;
        this.snakeState = SnakeBossState.CHASE;
        this.snakeSkillType = SnakeSkillType.NONE;
        this.snakeSkillTime = 0.0;
        this.isCharging = false;
        this.venomShotFired = false;
        this.snakeDashHitPlayer = false;
        this.snakeWhipDamageDone = false;
    }

    private void updatePython(double dt, PlayerSnake player, ChunkManager world, List<PoisonBolt> projectiles,
                              Camera camera, ParticleSystem particles, FloatingTextManager texts, boolean enraged) {
        updateSnakeBoss(
                dt, player, world, projectiles, camera, particles, texts, enraged,
                Config.BossConfig.PYTHON_SPEED,
                Config.BossConfig.PYTHON_DAMAGE,
                22.0,
                26.0,
                "CU TRAN CAN DOC",
                Config.BossConfig.PYTHON_VENOM_DAMAGE,
                Config.BossConfig.PYTHON_VENOM_DOT_DAMAGE,
                Config.BossConfig.PYTHON_TAIL_WHIP_DAMAGE
        );

        // Trăn khổng lồ giẫm phải bẫy thú (Chỉ check khi đang hoạt động & chưa chết)
        if (world != null && alive && stunTimer <= 0.0) {
            ChunkManager.TrapResult trap = world.checkTrapTrigger(x, y, 18.0);
            if (trap != null && trap.triggered) {
                interruptSnakeStateForStun();
                takeDamage(85, texts, particles);
                stunTimer = 1.8;
                if (texts != null) {
                    texts.spawn(x, y - 82, "CU TRAN DINH BAY (-85 HP)!", COLOR_BEAR_TRAP_TEXT, 2.2, 18);
                }
                if (particles != null) {
                    particles.spawnTrapSparks(x, y);
                }
                if (camera != null) camera.shake(16.0);
            }
        }
    }

    private void updateVoidDragon(double dt, PlayerSnake player, ChunkManager world, List<PoisonBolt> projectiles,
                                  Camera camera, ParticleSystem particles, FloatingTextManager texts, boolean enraged) {
        updateSnakeBoss(
                dt, player, world, projectiles, camera, particles, texts, enraged,
                Config.BossConfig.VOID_DRAGON_SPEED,
                Config.BossConfig.VOID_DRAGON_DAMAGE,
                24.0,
                36.0,
                "THAN LONG CAN QUET",
                Config.BossConfig.PYTHON_VENOM_DAMAGE + 8,
                Config.BossConfig.PYTHON_VENOM_DOT_DAMAGE + 2,
                Config.BossConfig.PYTHON_TAIL_WHIP_DAMAGE + 10
        );
    }

    private void updateSnakeBoss(double dt, PlayerSnake player, ChunkManager world, List<PoisonBolt> projectiles,
                                 Camera camera, ParticleSystem particles, FloatingTextManager texts, boolean enraged,
                                 double baseSpeed, int meleeDamage, double segDist, double meleeRange,
                                 String biteLabel, int venomDmg, int dotDmg, int whipDmg) {
        if (segments.isEmpty()) return;

        double distToPlayer = Math.hypot(player.getX() - x, player.getY() - y);

        switch (snakeState) {
            case CHASE -> {
                isCharging = false;
                double targetAngle = angle;
                if (player.isHidden()) {
                    if (Math.random() < 0.03) targetAngle += (Math.random() - 0.5) * 1.2;
                } else {
                    targetAngle = Math.atan2(player.getY() - y, player.getX() - x);
                }

                // Kiểm tra kích hoạt chiêu thức đặc biệt (Gầm -> Bắn chùm độc HOẶC Quẫy đuôi + Lướt)
                if (!player.isHidden()
                        && skillCooldown <= 0.0
                        && distToPlayer <= Config.BossConfig.PYTHON_SKILL_RANGE) {
                    boolean canVenom = mana >= Config.BossConfig.PYTHON_VENOM_MANA_COST;
                    boolean canDash = mana >= Config.BossConfig.PYTHON_DASH_MANA_COST;
                    SnakeSkillType chosen = SnakeSkillType.NONE;

                    if (canVenom && canDash) {
                        if (distToPlayer > 290.0) {
                            chosen = SnakeSkillType.VENOM_VOLLEY;
                        } else if (distToPlayer < 165.0) {
                            chosen = SnakeSkillType.COIL_DASH_WHIP;
                        } else {
                            chosen = (snakeSkillCycle % 2 == 0)
                                    ? SnakeSkillType.VENOM_VOLLEY
                                    : SnakeSkillType.COIL_DASH_WHIP;
                        }
                        snakeSkillCycle++;
                    } else if (canVenom) {
                        chosen = SnakeSkillType.VENOM_VOLLEY;
                    } else if (canDash) {
                        chosen = SnakeSkillType.COIL_DASH_WHIP;
                    }

                    if (chosen != SnakeSkillType.NONE) {
                        snakeState = SnakeBossState.ROAR_WINDUP;
                        snakeSkillType = chosen;
                        snakeSkillTime = 0.0;
                        stateTime = 0.0;
                        venomShotFired = false;
                        snakeDashHitPlayer = false;
                        snakeWhipDamageDone = false;
                        angle = targetAngle;
                        lockedTargetX = player.getX();
                        lockedTargetY = player.getY();
                        if (texts != null) {
                            String roarTxt = (chosen == SnakeSkillType.VENOM_VOLLEY)
                                    ? "CU TRAN GAM RU - PHUN CHUM DOC!"
                                    : "CU TRAN GAM RU - QUAY DUOI LUOT!";
                            texts.spawn(x, y - 84, roarTxt, COLOR_PYTHON_ROAR_TEXT, 1.3, 15);
                        }
                        SoundManager.playEagle();
                        return;
                    }
                }

                // Angular Lerp mượt mà
                double diff = normalizeAngleDiff(targetAngle - angle);
                angle += Math.signum(diff) * Math.min(Math.abs(diff), 2.6 * dt);

                double spd = enraged ? baseSpeed * 1.3 : baseSpeed;
                moveWithCollision(world, Math.cos(angle) * spd * dt, Math.sin(angle) * spd * dt, SNAKE_COLLISION_RADIUS);
                updateSnakeSegments(segDist);

                // Tấn công cắn cận chiến -> Chuyển sang 2s nghỉ hồi Mana
                if (!player.isHidden() && distToPlayer < meleeRange && attackCooldown <= 0.0) {
                    if (player.takeDamage(meleeDamage, camera)) {
                        attackCooldown = 1.6;
                        skillCooldown = Math.max(skillCooldown, 0.8);
                        if (texts != null) {
                            texts.spawn(player.getX(), player.getY() - 36,
                                    biteLabel + " (-" + meleeDamage + " HP)!",
                                    COLOR_BEAR_BITE_TEXT, 2.0, 16);
                        }
                        if (particles != null) {
                            particles.spawnEvolutionAura(x, y, COLOR_PYTHON_VENOM_TEXT);
                        }
                        SoundManager.playHit();
                        if (camera != null) camera.shake(14.0);
                        enterRecoveryState();
                    }
                }
            }
            case ROAR_WINDUP -> {
                if (player.isHidden()) {
                    snakeState = SnakeBossState.CHASE;
                    snakeSkillType = SnakeSkillType.NONE;
                    snakeSkillTime = 0.0;
                    stateTime = 0.0;
                    isCharging = false;
                    return;
                }
                isCharging = false;
                snakeSkillTime += dt;
                stateTime = snakeSkillTime;
                double roarDur = Config.BossConfig.PYTHON_ROAR_TIME * (enraged ? 0.78 : 1.0);
                double prog = Math.min(1.0, snakeSkillTime / Math.max(0.01, roarDur));

                // Giai đoạn đầu xoay theo mục tiêu, 28% cuối khóa chặt hướng để tạo đường báo
                if (prog < 0.72) {
                    double targetAngle = Math.atan2(player.getY() - y, player.getX() - x);
                    double diff = normalizeAngleDiff(targetAngle - angle);
                    angle += Math.signum(diff) * Math.min(Math.abs(diff), 4.0 * dt);
                    lockedTargetX = player.getX();
                    lockedTargetY = player.getY();
                }

                // Neo đầu rắn đứng yên tụ lực
                segments.get(0).x = x;
                segments.get(0).y = y;
                segments.get(0).angle = angle;

                if (snakeSkillTime >= roarDur) {
                    if (snakeSkillType == SnakeSkillType.VENOM_VOLLEY) {
                        mana = Math.max(0.0, mana - Config.BossConfig.PYTHON_VENOM_MANA_COST);
                        fireSnakeVenomVolley(projectiles, enraged, venomDmg, dotDmg,
                                Config.BossConfig.PYTHON_VENOM_SPEED, particles, texts);
                        snakeState = SnakeBossState.VENOM_SPIT;
                        snakeSkillTime = 0.0;
                        stateTime = 0.0;
                    } else {
                        mana = Math.max(0.0, mana - Config.BossConfig.PYTHON_DASH_MANA_COST);
                        performSnakeTailWhip(player, whipDmg, Config.BossConfig.PYTHON_TAIL_WHIP_RADIUS,
                                camera, particles, texts);
                        double dx = lockedTargetX - x;
                        double dy = lockedTargetY - y;
                        double d = Math.hypot(dx, dy);
                        if (d > 1e-4) {
                            lungeDirX = dx / d;
                            lungeDirY = dy / d;
                            angle = Math.atan2(dy, dx);
                        } else {
                            lungeDirX = Math.cos(angle);
                            lungeDirY = Math.sin(angle);
                        }
                        snakeDashTraveled = 0.0;
                        snakeDashHitPlayer = false;
                        isCharging = true;
                        snakeState = SnakeBossState.COIL_DASH;
                        snakeSkillTime = 0.0;
                        stateTime = 0.0;
                        SoundManager.playDash();
                        if (camera != null) camera.shake(8.0);
                    }
                }
            }
            case VENOM_SPIT -> {
                isCharging = false;
                snakeSkillTime += dt;
                stateTime = snakeSkillTime;
                if (snakeSkillTime >= Config.BossConfig.PYTHON_VENOM_RECOIL_TIME) {
                    skillCooldown = Config.BossConfig.PYTHON_SKILL_COOLDOWN;
                    enterRecoveryState();
                }
            }
            case COIL_DASH -> {
                isCharging = true;
                snakeSkillTime += dt;
                stateTime = snakeSkillTime;

                double dashSpd = Config.BossConfig.PYTHON_DASH_SPEED * (enraged ? 1.18 : 1.0);
                double step = dashSpd * dt;
                double nextX = x + lungeDirX * step;
                double nextY = y + lungeDirY * step;

                if (world != null && world.checkCollision(nextX, nextY, SNAKE_COLLISION_RADIUS).collided) {
                    skillCooldown = Config.BossConfig.PYTHON_SKILL_COOLDOWN;
                    enterRecoveryState();
                    return;
                }

                x = nextX;
                y = nextY;
                snakeDashTraveled += step;
                updateSnakeSegments(segDist);

                double newDist = Math.hypot(player.getX() - x, player.getY() - y);
                if (!player.isHidden() && !snakeDashHitPlayer && newDist < meleeRange + 10.0) {
                    if (player.takeDamage(meleeDamage, camera)) {
                        snakeDashHitPlayer = true;
                        GamePanel.triggerHitStop(0.05);
                        SoundManager.playHit();
                        if (camera != null) camera.shake(15.0);
                        if (texts != null) {
                            texts.spawn(player.getX(), player.getY() - 36,
                                    "CU TRAN LUOT CAN (-" + meleeDamage + " HP)!",
                                    COLOR_BEAR_BITE_TEXT, 2.0, 16);
                        }
                    }
                }

                if (snakeSkillTime >= Config.BossConfig.PYTHON_DASH_TIME) {
                    skillCooldown = Config.BossConfig.PYTHON_SKILL_COOLDOWN;
                    enterRecoveryState();
                }
            }
        }
    }

    private void updateSnakeSegments(double segDist) {
        if (segments.isEmpty()) return;
        segments.get(0).x = x;
        segments.get(0).y = y;
        segments.get(0).angle = angle;

        for (int i = 1; i < segments.size(); i++) {
            SnakeSegment prev = segments.get(i - 1);
            SnakeSegment curr = segments.get(i);

            double dx = curr.x - prev.x;
            double dy = curr.y - prev.y;
            double d = Math.hypot(dx, dy);

            if (d > segDist) {
                double ratio = segDist / d;
                curr.x = prev.x + dx * ratio;
                curr.y = prev.y + dy * ratio;
                curr.angle = Math.atan2(-dy, -dx);
            }
        }
    }

    private void fireSnakeVenomVolley(List<PoisonBolt> projectiles, boolean enraged,
                                       int impactDmg, int dotDmg, double speed,
                                       ParticleSystem particles, FloatingTextManager texts) {
        venomShotFired = true;
        double mouthX = x + Math.cos(angle) * 26.0;
        double mouthY = y + Math.sin(angle) * 26.0;
        double aimAngle = Math.atan2(lockedTargetY - y, lockedTargetX - x);
        angle = aimAngle;

        if (projectiles != null) {
            int count = enraged ? 5 : 3;
            double spreadStep = enraged ? 0.17 : 0.22;
            int half = count / 2;
            for (int i = -half; i <= half; i++) {
                double shotAngle = aimAngle + i * spreadStep;
                projectiles.add(new PoisonBolt(mouthX, mouthY, shotAngle, speed, true, impactDmg, dotDmg, 1.35));
            }
        }
        if (particles != null) {
            particles.spawnPoisonSplash(mouthX, mouthY);
        }
        if (texts != null) {
            texts.spawn(x, y - 82, "PHUN CHUM DOC TO!", COLOR_PYTHON_VENOM_TEXT, 1.4, 15);
        }
        SoundManager.playVenomSpit();
    }

    private void performSnakeTailWhip(PlayerSnake player, int dmg, double radius,
                                      Camera camera, ParticleSystem particles, FloatingTextManager texts) {
        if (snakeWhipDamageDone) return;
        snakeWhipDamageDone = true;

        double whipX = x;
        double whipY = y;
        if (segments.size() >= 4) {
            int idxStart = segments.size() - 4;
            double sx = 0.0, sy = 0.0;
            for (int i = idxStart; i < segments.size(); i++) {
                sx += segments.get(i).x;
                sy += segments.get(i).y;
            }
            whipX = sx / 4.0;
            whipY = sy / 4.0;
        }
        tailWhipFxX = whipX;
        tailWhipFxY = whipY;
        tailWhipFxTimer = 0.0;

        if (particles != null) {
            particles.spawnTrapSparks(whipX, whipY);
        }
        SoundManager.playRattleTail();

        double dist = Math.hypot(player.getX() - whipX, player.getY() - whipY);
        if (!player.isHidden() && dist <= radius) {
            if (player.takeDamage(dmg, camera)) {
                double pushAng = Math.atan2(player.getY() - whipY, player.getX() - whipX);
                player.teleportTo(player.getX() + Math.cos(pushAng) * 38.0,
                        player.getY() + Math.sin(pushAng) * 38.0);
                if (texts != null) {
                    texts.spawn(player.getX(), player.getY() - 36,
                            "QUAY DUOI CHAN DONG (-" + dmg + " HP)!",
                            COLOR_PYTHON_WHIP_TEXT, 1.8, 16);
                }
                if (camera != null) camera.shake(12.0);
                SoundManager.playHit();
            }
        }
    }

    private static final Color COLOR_BOSS_DMG_TEXT = new Color(251, 146, 60);

    public void takeDamage(int dmg, FloatingTextManager texts, ParticleSystem particles) {
        if (type == Type.BEAR && (bearState == BearState.RETREAT || bearState == BearState.RECOVER)) {
            dmg = Math.max(1, (int) Math.round(dmg * Config.BossConfig.BEAR_RETREAT_DAMAGE_MULT));
        }
        health = Math.max(0, health - dmg);
        if (texts != null) {
            texts.spawn(x, y - 24, "-" + dmg, COLOR_BOSS_DMG_TEXT, 1.2, 16);
        }
        if (particles != null) {
            particles.spawnTrapSparks(x, y);
        }
        if (health <= 0) {
            alive = false;
        }
    }

    public void inflictPoison(double duration, int dmgPerTick) {
        this.poisonTimer = Math.max(this.poisonTimer, duration);
        this.poisonDmgPerTick = dmgPerTick;
        this.poisonTickTimer = 0.5;
    }

    public void stun(double duration) {
        this.stunTimer = Math.max(this.stunTimer, duration);
        if (duration > 0.0) {
            if (type == Type.BEAR) {
                interruptBearStateForStun();
            } else if (type == Type.GIANT_PYTHON || type == Type.VOID_DRAGON) {
                interruptSnakeStateForStun();
            } else {
                this.wasStunned = true;
                this.recoveryTimer = 0.0;
                this.actionState = BossActionState.ACTIVE;
                this.isCharging = false;
            }
        }
    }

    public void knockback(double pushX, double pushY) {
        this.x += pushX;
        this.y += pushY;
        if (!segments.isEmpty()) {
            segments.get(0).x = this.x;
            segments.get(0).y = this.y;
        }
    }

    private void updateSwampKing(double dt, PlayerSnake player, ChunkManager world, List<PoisonBolt> projectiles,
                                 Camera camera, ParticleSystem particles, FloatingTextManager texts, boolean enraged) {
        double distToPlayer = Math.hypot(player.getX() - x, player.getY() - y);

        if (player.isHidden()) {
            isCharging = false;
            if (Math.random() < 0.02) {
                angle += (Math.random() - 0.5) * 1.5;
            }
            double spd = Config.BossConfig.SWAMP_KING_SPEED * 0.6;
            moveWithCollision(world, Math.cos(angle) * spd * dt, Math.sin(angle) * spd * dt, 26.0);
            return;
        }

        if (distToPlayer < 480.0) {
            angle = Math.atan2(player.getY() - y, player.getX() - x);

            // Kỹ năng tầm trung: Sóng Thủy Triều Độc -> nghỉ 2.0s hồi Mana
            if (distToPlayer > 115.0 && distToPlayer < 380.0
                    && skillCooldown <= 0.0 && mana >= Config.BossConfig.SWAMP_KING_WAVE_MANA_COST) {
                mana = Math.max(0.0, mana - Config.BossConfig.SWAMP_KING_WAVE_MANA_COST);
                skillCooldown = Config.BossConfig.SWAMP_KING_SKILL_COOLDOWN;
                if (projectiles != null) {
                    int count = enraged ? 4 : 3;
                    for (int i = 0; i < count; i++) {
                        double offset = (i - (count - 1) * 0.5) * 0.24;
                        projectiles.add(new PoisonBolt(
                                x + Math.cos(angle) * 30.0,
                                y + Math.sin(angle) * 30.0,
                                angle + offset, 330.0, true,
                                Config.BossConfig.SWAMP_KING_WAVE_DAMAGE, 4, 1.25));
                    }
                }
                if (particles != null) particles.spawnWaterBubble(x, y);
                if (texts != null) {
                    texts.spawn(x, y - 42, "HAI LONG SONG TRIEU!", COLOR_OCEAN_SKILL_TEXT, 1.5, 16);
                }
                SoundManager.playVenomSpit();
                enterRecoveryState();
                return;
            }

            isCharging = distToPlayer < 280.0;
            double spd = isCharging ? (enraged ? Config.BossConfig.SWAMP_KING_SPEED * 1.4 : Config.BossConfig.SWAMP_KING_SPEED * 1.15)
                                    : Config.BossConfig.SWAMP_KING_SPEED;

            moveWithCollision(world, Math.cos(angle) * spd * dt, Math.sin(angle) * spd * dt, 26.0);

            // Đòn ngoạm xé càn quét (Death Roll Snap) -> nghỉ 2.0s hồi Mana
            if (distToPlayer < 46.0 && attackCooldown <= 0) {
                if (player.takeDamage(Config.BossConfig.SWAMP_KING_DAMAGE, camera)) {
                    attackCooldown = 1.6;
                    mana = Math.max(0.0, mana - 20.0);
                    if (texts != null) {
                        texts.spawn(player.getX(), player.getY() - 36,
                                "HAI LONG QUET SONG (-" + Config.BossConfig.SWAMP_KING_DAMAGE + " HP)!",
                                COLOR_OCEAN_SKILL_TEXT, 2.2, 17);
                    }
                    if (particles != null) {
                        particles.spawnWaterBubble(x, y);
                        particles.spawnTrapSparks(x, y);
                    }
                    SoundManager.playHit();
                    if (camera != null) camera.shake(16.0);
                    enterRecoveryState();
                }
            }
        }
    }

    private void updateOctopus(double dt, PlayerSnake player, ChunkManager world, List<PoisonBolt> projectiles,
                               Camera camera, ParticleSystem particles, FloatingTextManager texts, boolean enraged) {
        double distToPlayer = Math.hypot(player.getX() - x, player.getY() - y);

        if (player.isHidden()) {
            isCharging = false;
            if (Math.random() < 0.02) {
                angle += (Math.random() - 0.5) * 1.5;
            }
            double spd = Config.BossConfig.OCTOPUS_SPEED * 0.6;
            moveWithCollision(world, Math.cos(angle) * spd * dt, Math.sin(angle) * spd * dt, 26.0);
            return;
        }

        if (distToPlayer < 520.0) {
            angle = Math.atan2(player.getY() - y, player.getX() - x);

            // Kỹ năng Phun Mực Độc 4 hướng -> nghỉ 2.0s hồi Mana
            if (distToPlayer > 110.0 && distToPlayer < 360.0
                    && skillCooldown <= 0.0 && mana >= Config.BossConfig.OCTOPUS_INK_MANA_COST) {
                mana = Math.max(0.0, mana - Config.BossConfig.OCTOPUS_INK_MANA_COST);
                skillCooldown = Config.BossConfig.OCTOPUS_SKILL_COOLDOWN;
                if (projectiles != null) {
                    int bolts = enraged ? 5 : 3;
                    for (int i = 0; i < bolts; i++) {
                        double offset = (i - (bolts - 1) * 0.5) * 0.28;
                        projectiles.add(new PoisonBolt(
                                x + Math.cos(angle) * 28.0,
                                y + Math.sin(angle) * 28.0,
                                angle + offset, 310.0, true,
                                Config.BossConfig.OCTOPUS_INK_DAMAGE, 3, 1.25));
                    }
                }
                if (particles != null) {
                    particles.spawnPoisonSplash(x, y);
                    particles.spawnWaterBubble(x, y);
                }
                if (texts != null) {
                    texts.spawn(x, y - 42, "BACH TUOC PHUN MUC DOC!", COLOR_OCTO_SKILL_TEXT, 1.5, 16);
                }
                SoundManager.playVenomSpit();
                enterRecoveryState();
                return;
            }

            isCharging = distToPlayer < 260.0;
            double spd = isCharging ? (enraged ? Config.BossConfig.OCTOPUS_SPEED * 1.45 : Config.BossConfig.OCTOPUS_SPEED * 1.2)
                                    : Config.BossConfig.OCTOPUS_SPEED;

            moveWithCollision(world, Math.cos(angle) * spd * dt, Math.sin(angle) * spd * dt, 26.0);

            // Đòn quất xúc tu -> nghỉ 2.0s hồi Mana
            if (distToPlayer < 48.0 && attackCooldown <= 0) {
                if (player.takeDamage(Config.BossConfig.OCTOPUS_DAMAGE, camera)) {
                    attackCooldown = 1.6;
                    mana = Math.max(0.0, mana - 20.0);
                    if (texts != null) {
                        texts.spawn(player.getX(), player.getY() - 36,
                                "BACH TUOC QUAT XUC TU (-" + Config.BossConfig.OCTOPUS_DAMAGE + " HP)!",
                                COLOR_OCTO_SKILL_TEXT, 2.2, 17);
                    }
                    if (particles != null) {
                        particles.spawnPoisonSplash(x, y);
                        particles.spawnWaterBubble(x, y);
                    }
                    SoundManager.playHit();
                    if (camera != null) camera.shake(14.0);
                    enterRecoveryState();
                }
            }
        }
    }

    public String getName() {
        return switch (type) {
            case BEAR -> "BẠCH MAO HÙNG (BOSS LV.2)";
            case GIANT_PYTHON -> "THÁI CỔ CỰ TRĂN (BOSS LV.3)";
            case OCTOPUS -> "BẠCH TUỘC CỰ ĐẠI (BOSS ĐẠI DƯƠNG)";
            case SWAMP_KING -> "HẢI LONG CỰ THẦN (BOSS ĐẠI DƯƠNG)";
            case VOID_DRAGON -> "HƯ KHÔNG THẦN LONG (BOSS LV.5)";
        };
    }

    public Type getType() { return type; }
    public BiomeType getBiome() { return biome; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getAngle() { return angle; }
    public int getHealth() { return health; }
    public int getMaxHealth() { return maxHealth; }
    public boolean isAlive() { return alive; }
    public boolean isCharging() { return isCharging; }
    public double getStunTimer() { return stunTimer; }
    public double getAttackCooldown() { return attackCooldown; }
    public double getSkillCooldown() { return skillCooldown; }
    public double getAnimTimer() { return animTimer; }
    public List<SnakeSegment> getSegments() { return segments; }

    // Trạng thái nghỉ 2.0s (2000ms) & Mana chung
    public BossActionState getActionState() { return actionState; }
    public boolean isRecovering() { return recoveryTimer > 0.0 || actionState == BossActionState.RECOVERING; }
    public double getRecoveryTimer() { return recoveryTimer; }
    public double getRestDuration() { return restDuration; }
    public long getRestDurationMs() { return restDurationMs; }
    public double getRecoveryProgress() {
        if (restDuration <= 1e-6) return 1.0;
        return Math.max(0.0, Math.min(1.0, 1.0 - (recoveryTimer / restDuration)));
    }
    public double getMana() { return mana; }
    public double getMaxMana() { return maxMana; }
    public double getManaRatio() { return maxMana > 1e-6 ? Math.max(0.0, Math.min(1.0, mana / maxMana)) : 1.0; }

    // Getter trạng thái Boss Gấu
    public BearState getBearState() { return bearState; }
    public double getBearStateTime() { return stateTime; }

    public double getBearStateProgress() {
        boolean enraged = health <= enrageThreshold;
        double dur;
        switch (bearState) {
            case WINDUP -> dur = Config.BossConfig.BEAR_WINDUP_TIME
                    * (enraged ? Config.BossConfig.BEAR_ENRAGE_WINDUP_MULT : 1.0);
            case LUNGE -> {
                return Math.max(0.0, Math.min(1.0, lungeTraveled / Math.max(1.0, lungeExpectedDist)));
            }
            case BITE -> dur = Config.BossConfig.BEAR_BITE_TIME;
            case RETREAT -> dur = Config.BossConfig.BEAR_RETREAT_TIME
                    * (enraged ? Config.BossConfig.BEAR_ENRAGE_RETREAT_MULT : 1.0);
            case RECOVER -> dur = Config.BossConfig.BEAR_RECOVER_TIME;
            default -> {
                return 0.0;
            }
        }
        if (dur <= 1e-6) return 1.0;
        return Math.max(0.0, Math.min(1.0, stateTime / dur));
    }

    // Getter trạng thái kỹ năng Boss Rắn
    public SnakeBossState getSnakeState() { return snakeState; }
    public SnakeSkillType getSnakeSkillType() { return snakeSkillType; }
    public double getSnakeSkillTime() { return snakeSkillTime; }
    public double getSnakeStateProgress() {
        boolean enraged = health <= enrageThreshold;
        double dur;
        switch (snakeState) {
            case ROAR_WINDUP -> dur = Config.BossConfig.PYTHON_ROAR_TIME * (enraged ? 0.78 : 1.0);
            case VENOM_SPIT -> dur = Config.BossConfig.PYTHON_VENOM_RECOIL_TIME;
            case COIL_DASH -> dur = Config.BossConfig.PYTHON_DASH_TIME;
            default -> {
                return 0.0;
            }
        }
        if (dur <= 1e-6) return 1.0;
        return Math.max(0.0, Math.min(1.0, snakeSkillTime / dur));
    }
    public double getTailWhipFxTimer() { return tailWhipFxTimer; }
    public double getTailWhipFxX() { return tailWhipFxX; }
    public double getTailWhipFxY() { return tailWhipFxY; }
    public boolean isVenomShotFired() { return venomShotFired; }

    public double getLockedTargetX() { return lockedTargetX; }
    public double getLockedTargetY() { return lockedTargetY; }
    public double getLungeDirX() { return lungeDirX; }
    public double getLungeDirY() { return lungeDirY; }
    public boolean isBiteDamageDone() { return biteDamageDone; }
    public double getWalkAnimTimer() { return walkAnimTimer; }
    public boolean isBearMoving() { return bearMoving; }
    public boolean isBearEnraged() { return health <= enrageThreshold; }

    public double getBearDustFxTimer() { return bearDustFxTimer; }
    public double getBearDustFxX() { return bearDustFxX; }
    public double getBearDustFxY() { return bearDustFxY; }
    public double getBearBiteFxTimer() { return bearBiteFxTimer; }
    public double getBearBiteFxX() { return bearBiteFxX; }
    public double getBearBiteFxY() { return bearBiteFxY; }
    public double getBearBiteFxAngle() { return bearBiteFxAngle; }

    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }
    public void setAngle(double angle) { this.angle = angle; }
    public void setAnimTimer(double animTimer) { this.animTimer = animTimer; }
    public void setMana(double mana) { this.mana = Math.max(0.0, Math.min(maxMana, mana)); }
}
