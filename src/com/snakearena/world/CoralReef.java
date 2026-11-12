package com.snakearena.world;

import com.snakearena.core.Camera;
import com.snakearena.core.Config;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * CoralReef: Đại diện cho một Cụm San Hô Tự Nhiên (Coral Reef Cluster) sinh thái dưới đáy biển Map 2.
 * Thay thế hoàn toàn cách tạo hình đơn điệu cũ bằng hệ thống cụm 3 cấp (Nhỏ, Vừa, Biển Sâu Lớn)
 * bám sát ảnh tham khảo pixel art.
 *
 * Tính năng chính:
 * 1. Ba cấp cụm:
 *    - Cụm nhỏ (160-260px): 2-3 san hô trên một bệ đá nhỏ.
 *    - Cụm vừa (300-520px): Bệ đá xanh teal sẫm có đốm tím/xanh, cành cao ở giữa/sau, khối tròn và não ở trước, hải quỳ ở rìa.
 *    - Cụm lớn "San Hô Biển Sâu" (700-900px, tỷ lệ ~2:1, hiếm 10-15%): 40-70 cây đa sắc tầng lớp, viền gồ ghề, bệ tối ở đáy.
 * 2. Một bóng mềm chung (alpha 0.12 - 0.20) cho cả cụm, không vẽ bóng riêng từng cây.
 * 3. Kích thước thực vật chuẩn trong game:
 *    - San hô cành: 50-110px.
 *    - Não lớn: 40-80px.
 *    - Khối tròn nhỏ: 20-36px.
 *    - Hải quỳ: 20-32px.
 * 4. Va chạm & Ẩn nấp:
 *    - Lõi cứng tối đa 90px (cụm lớn dùng 2-3 lõi nhỏ <= 90px). Nếu rắn spawn trong lõi sẽ được đẩy ra ngoài an toàn.
 *    - Vùng ẩn nấp ngụy trang elip 0.80 cho phép rắn bơi xuyên qua và hồi +4 HP/giây.
 * 5. Hiệu năng tối đa (Zero-Lag):
 *    - Nền bệ đá, bóng mờ, san hô cứng nướng sẵn vào BufferedImage tương thích (Lazy baking, throttle 1 cụm/frame).
 *    - Chỉ các cây mềm (hải quỳ/rong) tối đa 12 sprite đung đưa riêng và Y-sort.
 *    - Giới hạn cá trang trí: tối đa 12 con (cụm vừa), 20 con (cụm lớn).
 */
public class CoralReef {

    // =========================================================================
    // HẰNG SỐ CẤU HÌNH CỤM SAN HÔ MAP 2 (CORAL REEF CLUSTER CONSTANTS)
    // =========================================================================
    // Kích thước các cấp cụm (Cluster Size Tiers)
    public static final int CLUSTER_SMALL_MIN_WIDTH = 160;
    public static final int CLUSTER_SMALL_MAX_WIDTH = 260;
    public static final int CLUSTER_MEDIUM_MIN_WIDTH = 300;
    public static final int CLUSTER_MEDIUM_MAX_WIDTH = 520;
    public static final int CLUSTER_LARGE_MIN_WIDTH = 700;
    public static final int CLUSTER_LARGE_MAX_WIDTH = 900;

    // Tỉ lệ xuất hiện các cấp cụm
    public static final double RATIO_SMALL_CLUSTER = 0.45;
    public static final double RATIO_MEDIUM_CLUSTER = 0.40;
    public static final double RATIO_LARGE_CLUSTER = 0.15; // Cụm San Hô Biển Sâu hiếm 10-15%

    // Kích thước thực vật chuẩn trong game (so với thân rắn 18px)
    public static final int BRANCH_CORAL_MIN_HEIGHT = 50;
    public static final int BRANCH_CORAL_MAX_HEIGHT = 110;
    public static final int BRAIN_CORAL_MIN_SIZE = 40;
    public static final int BRAIN_CORAL_MAX_SIZE = 80;
    public static final int ROUND_MASS_MIN_SIZE = 20;
    public static final int ROUND_MASS_MAX_SIZE = 36;
    public static final int ANEMONE_MIN_SIZE = 20;
    public static final int ANEMONE_MAX_SIZE = 32;

    // Cấu hình va chạm và ẩn nấp
    public static final double MAX_CORE_COLLISION_RADIUS = 90.0; // Bán kính tuyệt đối tối đa của lõi cứng (px, chống kẹt)
    public static final double REEF_HIDING_ZONE_RATIO = 0.80;    // Vùng ẩn nấp ngụy trang tán san hô (80% bán kính)
    public static final float REEF_SHADOW_ALPHA = 0.16f;         // Alpha bóng mềm mờ chung cho toàn cụm (0.12 - 0.20)
    public static final float OCCLUSION_ALPHA = 0.70f;           // Alpha san hô phía trước làm mờ che thân rắn

    // Tối ưu hiệu năng: Giới hạn cây động & throttle nướng ảnh tĩnh
    public static final int MAX_SWAYING_PLANTS_PER_CLUSTER = 12; // Tối đa 12 sprite hải quỳ/rong mềm đung đưa riêng
    public static final int MAX_STATIC_BAKES_PER_FRAME = 1;      // Tối đa 1 cụm nướng ảnh tĩnh mỗi frame

    // Giới hạn đàn cá trang trí bơi quanh cụm
    public static final int MAX_FISH_MEDIUM_CLUSTER = 12;
    public static final int MAX_FISH_LARGE_CLUSTER = 20;

    // Tương thích ngược với các test cũ
    public static final int REEF_MIN_WIDTH = CLUSTER_SMALL_MIN_WIDTH;
    public static final int REEF_MAX_WIDTH = CLUSTER_LARGE_MAX_WIDTH;
    public static final int REEF_MIN_HEIGHT = 110;
    public static final int REEF_MAX_HEIGHT = 580;
    public static final int MIN_CORALS_PER_REEF = 2;
    public static final int MAX_CORALS_PER_REEF = 70;
    public static final double REEF_CORE_COLLISION_RATIO = 0.36;
    public static final int MAX_REEF_FISH = MAX_FISH_MEDIUM_CLUSTER;
    public static final int CORAL_MIN_HEIGHT = ROUND_MASS_MIN_SIZE;
    public static final int CORAL_MAX_HEIGHT = BRANCH_CORAL_MAX_HEIGHT;

    // Biến static điều phối throttle nướng ảnh tĩnh trong frame hiện tại
    private static int staticBakesThisFrame = 0;

    public static void resetFrameBakeCount() {
        staticBakesThisFrame = 0;
    }

    public static int getFrameBakeCount() {
        return staticBakesThisFrame;
    }

    // 3 Cấp Cụm
    public enum ClusterTier {
        SMALL,              // Cụm nhỏ 160-260px (2-3 san hô trên bệ đá nhỏ)
        MEDIUM,             // Cụm vừa 300-520px (bệ đá teal, cành cao sau, khối tròn/não trước, hải quỳ rìa)
        LARGE_DEEP_SEA      // Cụm lớn Biển Sâu 700-900px (40-70 cây đa sắc, bệ tối)
    }

    static {
        // Kiểm tra không bị nhảy hình khi cuộn màn hình: nửa kích thước lớn nhất <= CHUNK_SIZE * BUFFER_CHUNKS - 60
        double maxHalfDim = CLUSTER_LARGE_MAX_WIDTH / 2.0;
        double maxAllowed = Config.World.CHUNK_SIZE * Config.World.BUFFER_CHUNKS - 60.0;
        if (maxHalfDim > maxAllowed) {
            System.err.println("[WARNING] CoralReef dimension violation: maxHalfDim (" + maxHalfDim + ") > maxAllowed (" + maxAllowed + ")");
        }
        assert maxHalfDim <= maxAllowed : "CoralReef max half-dimension exceeds chunk buffer limit!";
        assert maxHalfDim <= 450.0 : "CoralReef half-dimension exceeds 450px limit!";
    }

    // =========================================================================
    // CẤU TRÚC PHẦN TỬ THỰC VẬT BIỂN TRONG CỤM (CORAL PLANT)
    // =========================================================================
    public static class CoralPlant {
        public final double relX;
        public final double relY;
        public final double worldX;
        public final double worldY;
        public final double footY;
        public final int spriteId;
        public final int width;
        public final int height;
        public final boolean isFlipped;
        public final double swayPhase;
        public final boolean isSwaying;

        public CoralPlant(double relX, double relY, double worldX, double worldY, double footY,
                          int spriteId, int width, int height, boolean isFlipped,
                          double swayPhase, boolean isSwaying) {
            this.relX = relX;
            this.relY = relY;
            this.worldX = worldX;
            this.worldY = worldY;
            this.footY = footY;
            this.spriteId = spriteId;
            this.width = width;
            this.height = height;
            this.isFlipped = isFlipped;
            this.swayPhase = swayPhase;
            this.isSwaying = isSwaying;
        }

        // Tương thích ngược
        public int getVariant() { return spriteId; }
        public int getColorVariant() { return 0; }
        public double getTilt() { return 0.0; }
        public double getSwayAmp() { return isSwaying ? 2.0 : 0.4; }
    }

    // =========================================================================
    // CẤU TRÚC DỮ LIỆU ĐÀN CÁ NHỎ TRANG TRÍ QUANH CỤM (REEF FISH)
    // =========================================================================
    public static class ReefFish {
        public final double orbitRadiusX;
        public final double orbitRadiusY;
        public final double orbitSpeed;
        public final double phaseOffset;
        public final Color color;
        public final int size;

        public ReefFish(double rx, double ry, double speed, double phase, Color color, int size) {
            this.orbitRadiusX = rx;
            this.orbitRadiusY = ry;
            this.orbitSpeed = speed;
            this.phaseOffset = phase;
            this.color = color;
            this.size = size;
        }
    }

    // =========================================================================
    // CẤU TRÚC LÕI CỨNG VA CHẠM (CORE NODE) - CHỐNG KẸT RẮN
    // =========================================================================
    public static class CoreNode {
        public final double worldX;
        public final double worldY;
        public final double radius;

        public CoreNode(double worldX, double worldY, double radius) {
            this.worldX = worldX;
            this.worldY = worldY;
            this.radius = radius;
        }
    }

    // =========================================================================
    // THUỘC TÍNH CỤM SAN HÔ
    // =========================================================================
    public final double centerX;
    public final double centerY;
    public final double radiusX;
    public final double radiusY;
    public final double hideRadiusX;
    public final double hideRadiusY;
    public final double rotation;
    public final double arcCurvature;
    public final ClusterTier tier;
    public final long seed;

    // Bounding Box cho Frustum Culling
    public final double minX;
    public final double maxX;
    public final double minY;
    public final double maxY;

    private final List<CoreNode> coreNodes = new ArrayList<>();
    private final List<CoralPlant> allCorals = new ArrayList<>();
    private final List<CoralPlant> swayingCorals = new ArrayList<>();
    private final List<ReefFish> fishSchool = new ArrayList<>();
    private final List<Point2D.Double> bubbleCrevices = new ArrayList<>();

    // Cache ảnh tĩnh (Bóng mềm, bệ đá teal, cát, san hô cứng)
    private BufferedImage cachedStaticBase = null;

    public CoralReef(double centerX, double centerY, Random rng) {
        this(centerX, centerY, rng, selectRandomTier(rng));
    }

    public CoralReef(double centerX, double centerY, Random rng, ClusterTier tier) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.tier = tier;
        this.seed = rng.nextLong();
        this.rotation = (rng.nextFloat() - 0.5) * 0.40;
        this.arcCurvature = (rng.nextFloat() - 0.5) * 0.25;

        // Tính kích thước theo cấp cụm
        double w, h;
        switch (tier) {
            case SMALL -> {
                w = CLUSTER_SMALL_MIN_WIDTH + rng.nextFloat() * (CLUSTER_SMALL_MAX_WIDTH - CLUSTER_SMALL_MIN_WIDTH);
                h = w * (0.65 + rng.nextFloat() * 0.15);
            }
            case LARGE_DEEP_SEA -> {
                // Tỷ lệ ~ 2:1
                w = CLUSTER_LARGE_MIN_WIDTH + rng.nextFloat() * (CLUSTER_LARGE_MAX_WIDTH - CLUSTER_LARGE_MIN_WIDTH);
                h = w * (0.50 + rng.nextFloat() * 0.08); // tỷ lệ ~ 2:1
            }
            default -> { // MEDIUM
                w = CLUSTER_MEDIUM_MIN_WIDTH + rng.nextFloat() * (CLUSTER_MEDIUM_MAX_WIDTH - CLUSTER_MEDIUM_MIN_WIDTH);
                h = w * (0.64 + rng.nextFloat() * 0.12);
            }
        }
        this.radiusX = w / 2.0;
        this.radiusY = h / 2.0;

        // Vùng ẩn nấp/ngụy trang trong tán san hô: 80% bán kính
        this.hideRadiusX = this.radiusX * REEF_HIDING_ZONE_RATIO;
        this.hideRadiusY = this.radiusY * REEF_HIDING_ZONE_RATIO;

        // Bounding box frustum culling
        double pad = 90.0;
        this.minX = centerX - radiusX - pad;
        this.maxX = centerX + radiusX + pad;
        this.minY = centerY - radiusY - pad;
        this.maxY = centerY + radiusY + pad;

        // Tạo lõi cứng va chạm (khống chế tuyệt đối <= 90px; cụm lớn chia 2-3 lõi nhỏ)
        setupCoreNodes(rng);

        // Sinh danh sách thực vật biển theo mẫu
        generateClusterPlants(rng);

        // Sinh sinh vật rạn: đàn cá nhỏ và khe bọt khí
        generateReefLife(rng);
    }

    // Constructor tương thích ngược
    public CoralReef(double centerX, double centerY, double radiusX, double radiusY,
                     double rotation, double arcCurvature, Random rng) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.radiusX = radiusX;
        this.radiusY = radiusY;
        this.rotation = rotation;
        this.arcCurvature = arcCurvature;
        this.seed = rng.nextLong();

        double w = radiusX * 2.0;
        if (w >= CLUSTER_LARGE_MIN_WIDTH - 20) {
            this.tier = ClusterTier.LARGE_DEEP_SEA;
        } else if (w >= CLUSTER_MEDIUM_MIN_WIDTH - 20) {
            this.tier = ClusterTier.MEDIUM;
        } else {
            this.tier = ClusterTier.SMALL;
        }

        this.hideRadiusX = this.radiusX * REEF_HIDING_ZONE_RATIO;
        this.hideRadiusY = this.radiusY * REEF_HIDING_ZONE_RATIO;

        double pad = 90.0;
        this.minX = centerX - radiusX - pad;
        this.maxX = centerX + radiusX + pad;
        this.minY = centerY - radiusY - pad;
        this.maxY = centerY + radiusY + pad;

        setupCoreNodes(rng);
        generateClusterPlants(rng);
        generateReefLife(rng);
    }

    private static ClusterTier selectRandomTier(Random rng) {
        double roll = rng.nextFloat();
        if (roll < RATIO_LARGE_CLUSTER) {
            return ClusterTier.LARGE_DEEP_SEA;
        } else if (roll < RATIO_LARGE_CLUSTER + RATIO_SMALL_CLUSTER) {
            return ClusterTier.SMALL;
        } else {
            return ClusterTier.MEDIUM;
        }
    }

    /**
     * Thiết lập các lõi cứng va chạm:
     * - Bán kính tuyệt đối tối đa mỗi lõi: 90px.
     * - Cụm nhỏ: 1 lõi nhỏ (30-45px).
     * - Cụm vừa: 1 lõi (50-75px).
     * - Cụm lớn (Biển Sâu): 2-3 lõi nhỏ (mỗi lõi <= 75px) rải đều dọc theo chiều dài rạn.
     */
    private void setupCoreNodes(Random rng) {
        coreNodes.clear();
        double cosR = Math.cos(rotation);
        double sinR = Math.sin(rotation);

        if (tier == ClusterTier.LARGE_DEEP_SEA) {
            // Cụm lớn: bố trí 3 lõi nhỏ cách nhau, mỗi lõi bán kính 60-75px
            double[] fractions = { -0.50, 0.0, 0.50 };
            for (double frac : fractions) {
                double lx = radiusX * frac;
                double ly = arcCurvature * (lx * lx / radiusX);
                double rx = lx * cosR - ly * sinR;
                double ry = lx * sinR + ly * cosR;
                double r = (frac == 0.0) ? 75.0 : 60.0;
                coreNodes.add(new CoreNode(centerX + rx, centerY + ry, r));
            }
        } else if (tier == ClusterTier.MEDIUM) {
            double coreR = Math.min(MAX_CORE_COLLISION_RADIUS, Math.min(radiusX, radiusY) * 0.40);
            coreNodes.add(new CoreNode(centerX, centerY, Math.min(coreR, 70.0)));
        } else {
            // Small
            double coreR = Math.min(MAX_CORE_COLLISION_RADIUS, Math.min(radiusX, radiusY) * 0.35);
            coreNodes.add(new CoreNode(centerX, centerY, Math.min(coreR, 40.0)));
        }
    }

    /**
     * Sinh các thực vật trong cụm theo bố cục mỹ thuật của ảnh mẫu:
     * - Cụm nhỏ: 2-3 san hô (cành/khối tròn/hải quỳ)
     * - Cụm vừa: Bệ đá teal sẫm, cành cao ở giữa/sau, khối tròn & não ở trước, hải quỳ ở rìa
     * - Cụm lớn: 40-70 cây dày nhiều màu, tầng lớp phong phú
     */
    private void generateClusterPlants(Random rng) {
        allCorals.clear();
        swayingCorals.clear();

        double cosR = Math.cos(rotation);
        double sinR = Math.sin(rotation);

        if (tier == ClusterTier.SMALL) {
            // 2-3 cây san hô trên bệ nhỏ
            int count = 2 + rng.nextInt(2);
            int[] smallSprites = {
                CoralManager.ID_BRANCH_GOLD_1,
                CoralManager.ID_BRANCH_CYAN,
                CoralManager.ID_ROUND_PINK,
                CoralManager.ID_ANEMONE_YELLOW_ORANGE
            };
            for (int i = 0; i < count; i++) {
                double frac = (count == 2) ? (i == 0 ? -0.3 : 0.3) : ((i - 1) * 0.35);
                double lx = radiusX * frac;
                double ly = (rng.nextFloat() - 0.5) * radiusY * 0.4;
                double rx = lx * cosR - ly * sinR;
                double ry = lx * sinR + ly * cosR;

                int spId = smallSprites[rng.nextInt(smallSprites.length)];
                int h = CoralManager.isLargeBranch(spId) ? (50 + rng.nextInt(20)) : (24 + rng.nextInt(12));
                int w = (int) Math.round(h * 0.95);
                boolean flip = rng.nextBoolean();
                double swayPhase = (centerX + rx) * 0.05 + (centerY + ry) * 0.05 + i;
                boolean isSwaying = CoralManager.isAnemone(spId);

                CoralPlant plant = new CoralPlant(lx, ly, centerX + rx, centerY + ry, centerY + ry + h * 0.35,
                                                  spId, w, h, flip, swayPhase, isSwaying);
                allCorals.add(plant);
                if (isSwaying && swayingCorals.size() < MAX_SWAYING_PLANTS_PER_CLUSTER) {
                    swayingCorals.add(plant);
                }
            }
        } else if (tier == ClusterTier.MEDIUM) {
            // Cụm vừa (300-520px, giống 3 cụm giữa trong ảnh):
            // - 2-3 San hô cành cao ở giữa và sau (Y âm)
            // - 1-2 Khối tròn hoặc não ở trước (Y dương)
            // - 2-4 Hải quỳ ở rìa
            int branchCount = 2 + rng.nextInt(2);
            int[] branchPool = {
                CoralManager.ID_BRANCH_GOLD_1, CoralManager.ID_BRANCH_GOLD_2,
                CoralManager.ID_BRANCH_PINK_MAGENTA, CoralManager.ID_BRANCH_PINK_PURPLE,
                CoralManager.ID_BRANCH_CYAN
            };
            // Hoán đổi palette màu theo seed
            int colorOffset = rng.nextInt(branchPool.length);

            // 1. San hô cành ở phía sau/giữa (localY < 0)
            for (int i = 0; i < branchCount; i++) {
                double frac = (branchCount == 1) ? 0.0 : ((double) i / (branchCount - 1) - 0.5) * 1.1;
                double lx = radiusX * frac * 0.65;
                double ly = -radiusY * (0.20 + rng.nextFloat() * 0.35);
                double rx = lx * cosR - ly * sinR;
                double ry = lx * sinR + ly * cosR;

                int spId = branchPool[(i + colorOffset) % branchPool.length];
                int h = 60 + rng.nextInt(35);
                int w = (int) Math.round(h * 0.95);
                boolean flip = rng.nextBoolean();
                double swayPhase = (centerX + rx) * 0.04 + (centerY + ry) * 0.04 + i * 0.8;

                CoralPlant plant = new CoralPlant(lx, ly, centerX + rx, centerY + ry, centerY + ry + h * 0.35,
                                                  spId, w, h, flip, swayPhase, false);
                allCorals.add(plant);
            }

            // 2. Não và khối tròn ở phía trước (localY >= 0)
            int roundCount = 1 + rng.nextInt(2);
            for (int i = 0; i < roundCount; i++) {
                double frac = (roundCount == 1) ? 0.0 : ((double) i / (roundCount - 1) - 0.5) * 0.9;
                double lx = radiusX * frac * 0.55;
                double ly = radiusY * (0.10 + rng.nextFloat() * 0.30);
                double rx = lx * cosR - ly * sinR;
                double ry = lx * sinR + ly * cosR;

                int spId = (i == 0 && rng.nextFloat() < 0.6) ? CoralManager.ID_BRAIN_BLUE : (CoralManager.ID_ROUND_YELLOW + rng.nextInt(4));
                int h = (spId == CoralManager.ID_BRAIN_BLUE) ? (45 + rng.nextInt(20)) : (24 + rng.nextInt(12));
                int w = (int) Math.round(h * 1.05);
                boolean flip = rng.nextBoolean();
                double swayPhase = (centerX + rx) * 0.04 + (centerY + ry) * 0.04;

                CoralPlant plant = new CoralPlant(lx, ly, centerX + rx, centerY + ry, centerY + ry + h * 0.35,
                                                  spId, w, h, flip, swayPhase, false);
                allCorals.add(plant);
            }

            // 3. Hải quỳ ở rìa trái/phải
            int anemoneCount = 2 + rng.nextInt(3);
            for (int i = 0; i < anemoneCount; i++) {
                double side = (i % 2 == 0) ? -1.0 : 1.0;
                double lx = radiusX * side * (0.45 + rng.nextFloat() * 0.35);
                double ly = radiusY * (rng.nextFloat() - 0.5) * 0.70;
                double rx = lx * cosR - ly * sinR;
                double ry = lx * sinR + ly * cosR;

                int spId = CoralManager.ID_ANEMONE_RED_SMALL + rng.nextInt(3);
                int h = 20 + rng.nextInt(12);
                int w = (int) Math.round(h * 0.95);
                boolean flip = rng.nextBoolean();
                double swayPhase = (centerX + rx) * 0.05 + (centerY + ry) * 0.05 + i * 1.1;

                CoralPlant plant = new CoralPlant(lx, ly, centerX + rx, centerY + ry, centerY + ry + h * 0.35,
                                                  spId, w, h, flip, swayPhase, true);
                allCorals.add(plant);
                if (swayingCorals.size() < MAX_SWAYING_PLANTS_PER_CLUSTER) {
                    swayingCorals.add(plant);
                }
            }
        } else {
            // Cụm Lớn "San Hô Biển Sâu" (700-900px, 40-70 cây dày đặc đa sắc)
            int targetCount = 42 + rng.nextInt(28);
            for (int i = 0; i < targetCount; i++) {
                double normR = Math.pow(rng.nextFloat(), 1.35);
                double theta = rng.nextFloat() * Math.PI * 2.0;

                double lx = radiusX * normR * Math.cos(theta);
                double ly = radiusY * normR * Math.sin(theta);
                ly += arcCurvature * (lx * lx / radiusX);

                double rx = lx * cosR - ly * sinR;
                double ry = lx * sinR + ly * cosR;

                // Chọn sprite theo vị trí Y tương đối
                int spId;
                double normY = ly / radiusY;
                if (normY < -0.15) {
                    // Phía sau: San hô cành cao
                    spId = CoralManager.ID_BRANCH_GOLD_1 + rng.nextInt(5);
                } else if (normY > 0.25) {
                    // Phía trước: Não, khối tròn, hải quỳ
                    double roll = rng.nextFloat();
                    if (roll < 0.35) {
                        spId = CoralManager.ID_BRAIN_BLUE;
                    } else if (roll < 0.75) {
                        spId = CoralManager.ID_ROUND_YELLOW + rng.nextInt(4);
                    } else {
                        spId = CoralManager.ID_ANEMONE_RED_SMALL + rng.nextInt(3);
                    }
                } else {
                    // Trung tâm: Đa dạng mọi loài
                    spId = rng.nextInt(CoralManager.TOTAL_SPRITES);
                }

                int h;
                if (CoralManager.isLargeBranch(spId)) {
                    h = 65 + rng.nextInt(45);
                } else if (CoralManager.isBrain(spId)) {
                    h = 45 + rng.nextInt(25);
                } else if (CoralManager.isAnemone(spId)) {
                    h = 22 + rng.nextInt(10);
                } else {
                    h = 24 + rng.nextInt(12);
                }
                int w = (int) Math.round(h * 0.95);
                boolean flip = rng.nextBoolean();
                double swayPhase = (centerX + rx) * 0.04 + (centerY + ry) * 0.04 + i * 0.5;
                boolean isSwaying = CoralManager.isAnemone(spId);

                CoralPlant plant = new CoralPlant(lx, ly, centerX + rx, centerY + ry, centerY + ry + h * 0.35,
                                                  spId, w, h, flip, swayPhase, isSwaying);
                allCorals.add(plant);
                if (isSwaying && swayingCorals.size() < MAX_SWAYING_PLANTS_PER_CLUSTER) {
                    swayingCorals.add(plant);
                }
            }
        }

        // Sắp xếp Y-sort cho toàn bộ cây trong cụm (dùng khi nướng ảnh tĩnh)
        allCorals.sort(Comparator.comparingDouble(p -> p.footY));
    }

    /**
     * Sinh đàn cá nhỏ bơi lượn và các khe đá nhả bọt khí.
     */
    private void generateReefLife(Random rng) {
        fishSchool.clear();
        bubbleCrevices.clear();

        // 1. Đàn cá trang trí (tối đa 12 con cho vừa, 20 con cho lớn)
        int maxFish = (tier == ClusterTier.LARGE_DEEP_SEA) ? MAX_FISH_LARGE_CLUSTER : ((tier == ClusterTier.MEDIUM) ? MAX_FISH_MEDIUM_CLUSTER : 4);
        int fishCount = Math.max(3, (int) Math.round(maxFish * (0.6 + rng.nextFloat() * 0.4)));

        Color[] fishPalettes = {
            new Color(251, 146, 60),  // Cam cá hề
            new Color(56, 189, 248),  // Xanh dương neon
            new Color(250, 204, 21),  // Vàng chanh
            new Color(244, 114, 182), // Hồng tím
            new Color(45, 212, 191)   // Xanh ngọc
        };

        for (int i = 0; i < fishCount; i++) {
            double rx = radiusX * (0.55 + rng.nextFloat() * 0.40);
            double ry = radiusY * (0.55 + rng.nextFloat() * 0.40);
            double speed = (rng.nextFloat() < 0.5 ? 1.0 : -1.0) * (0.35 + rng.nextFloat() * 0.45);
            double phase = rng.nextFloat() * Math.PI * 2.0;
            Color col = fishPalettes[i % fishPalettes.length];
            int sz = 7 + rng.nextInt(4);
            fishSchool.add(new ReefFish(rx, ry, speed, phase, col, sz));
        }

        // 2. Khe đá nhả bọt khí
        int creviceCount = (tier == ClusterTier.LARGE_DEEP_SEA) ? (3 + rng.nextInt(3)) : (2 + rng.nextInt(2));
        for (int i = 0; i < creviceCount; i++) {
            double bx = centerX + (rng.nextFloat() - 0.5) * radiusX * 0.7;
            double by = centerY + (rng.nextFloat() - 0.5) * radiusY * 0.7;
            bubbleCrevices.add(new Point2D.Double(bx, by));
        }
    }

    /**
     * Nướng ảnh tĩnh lười (Lazy Baking) vào BufferedImage tương thích.
     * Bao gồm:
     * - Một bóng mờ mềm chung duy nhất cho cả cụm (alpha 0.12 - 0.20)
     * - Bệ đá xanh teal sẫm có đốm tím/xanh
     * - Các cây san hô cứng (cành, não, khối tròn, và cây nền)
     */
    public synchronized void buildStaticBaseCache() {
        if (cachedStaticBase != null) return;
        CoralManager.init();

        Random rng = new Random(seed);
        int pad = 100;
        int imgW = (int) Math.ceil(radiusX * 2 + pad * 2);
        int imgH = (int) Math.ceil(radiusY * 2 + pad * 2);

        GraphicsConfiguration gc = null;
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            if (!ge.isHeadless()) {
                gc = ge.getDefaultScreenDevice().getDefaultConfiguration();
            }
        } catch (Throwable ignored) {}

        if (gc != null) {
            cachedStaticBase = gc.createCompatibleImage(imgW, imgH, Transparency.TRANSLUCENT);
        } else {
            cachedStaticBase = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
        }

        Graphics2D g = cachedStaticBase.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        float cx = imgW / 2.0f;
        float cy = imgH / 2.0f;

        AffineTransform oldXf = g.getTransform();

        // 1. BÓNG MỀM CHUNG DUY NHẤT (Single Soft Ocean Shadow, alpha 0.16)
        int shadowW = (int) Math.round(radiusX * 2.15);
        int shadowH = (int) Math.round(radiusY * 2.05);
        float shadowCenterY = cy + 12.0f;

        float[] shadowFracs = { 0.0f, 0.45f, 0.80f, 1.0f };
        Color[] shadowCols = {
            new Color(8, 28, 42, (int) (255 * REEF_SHADOW_ALPHA)),
            new Color(10, 32, 48, (int) (255 * REEF_SHADOW_ALPHA * 0.75f)),
            new Color(12, 38, 54, (int) (255 * REEF_SHADOW_ALPHA * 0.25f)),
            new Color(12, 38, 54, 0)
        };

        AffineTransform sat = AffineTransform.getTranslateInstance(cx, shadowCenterY);
        sat.scale(shadowW / 2.0, shadowH / 2.0);
        RadialGradientPaint rgp = new RadialGradientPaint(
            new Point2D.Float(0, 0), 1.0f, new Point2D.Float(0, 0),
            shadowFracs, shadowCols, MultipleGradientPaint.CycleMethod.NO_CYCLE,
            MultipleGradientPaint.ColorSpaceType.SRGB, sat
        );
        g.setPaint(rgp);
        g.fillOval((int) (cx - shadowW / 2.0), (int) (shadowCenterY - shadowH / 2.0), shadowW, shadowH);

        // 2. BỆ ĐÁ XANH TEAL SẪM VỚI ĐỐM TÍM/XANH
        drawTealRockPedestal(g, cx, cy + 6.0f, rng);

        // 3. VẼ CÁC CÂY TĨNH (San hô cành, não, khối tròn, cây nền không đung đưa riêng)
        for (CoralPlant plant : allCorals) {
            if (swayingCorals.contains(plant)) {
                // Cây mềm đung đưa riêng sẽ được vẽ qua LayeredRenderer Y-sort động
                continue;
            }
            BufferedImage sprite = CoralManager.getSprite(plant.spriteId, plant.isFlipped);
            if (sprite != null) {
                int px = (int) Math.round(cx + plant.relX - plant.width / 2.0);
                int py = (int) Math.round(cy + plant.relY - plant.height * 0.80);
                g.drawImage(sprite, px, py, plant.width, plant.height, null);
            }
        }

        g.setTransform(oldXf);
        g.dispose();
    }

    /**
     * Vẽ bệ đá xanh teal sẫm có đốm tím/xanh theo đúng mô tả ảnh mẫu.
     */
    private void drawTealRockPedestal(Graphics2D g, float cx, float cy, Random rng) {
        int moundCount = (tier == ClusterTier.LARGE_DEEP_SEA) ? (14 + rng.nextInt(8)) : ((tier == ClusterTier.MEDIUM) ? (7 + rng.nextInt(4)) : (3 + rng.nextInt(2)));

        Color colDarkTeal = new Color(16, 48, 62);
        Color colMidTeal  = new Color(28, 72, 88);
        Color colHighTeal = new Color(42, 104, 122);

        // Đốm tím/xanh đặc trưng như trong ảnh
        Color[] speckleColors = {
            new Color(192, 75, 128), // Tím hồng
            new Color(91, 134, 212), // Xanh dương
            new Color(66, 181, 178), // Xanh ngọc biển
            new Color(236, 72, 153)  // Hồng sen
        };

        for (int i = 0; i < moundCount; i++) {
            double angle = (i * 2.0 * Math.PI) / moundCount + (rng.nextFloat() - 0.5) * 0.6;
            double dist = rng.nextFloat() * radiusX * 0.55;
            int mx = (int) Math.round(cx + Math.cos(angle) * dist);
            int my = (int) Math.round(cy + Math.sin(angle) * dist * 0.65);

            int mw = (int) (40 + rng.nextFloat() * 50);
            int mh = (int) (24 + rng.nextFloat() * 26);

            // Khối đá viền tối
            g.setColor(colDarkTeal);
            g.fillOval(mx - mw / 2, my - mh / 2, mw, mh);

            // Thân đá teal
            g.setColor(colMidTeal);
            g.fillOval(mx - mw / 2 + 2, my - mh / 2 + 2, mw - 4, mh - 4);

            // Vệt sáng đón nước
            g.setColor(colHighTeal);
            g.fillOval(mx - mw / 2 + 4, my - mh / 2 + 2, mw / 2, mh / 2);

            // Đốm tím/xanh trên bệ đá
            int speckleCount = 2 + rng.nextInt(3);
            for (int s = 0; s < speckleCount; s++) {
                int sx = mx - mw / 3 + rng.nextInt(Math.max(2, (mw * 2) / 3));
                int sy = my - mh / 3 + rng.nextInt(Math.max(2, (mh * 2) / 3));
                g.setColor(speckleColors[(i + s) % speckleColors.length]);
                g.fillRect(sx, sy, 2, 2);
            }
        }
    }

    /**
     * Vẽ phần nền tĩnh (Bóng mềm + Bệ đá teal + San hô cứng) đã nướng.
     * Khởi tạo LAZY khi rạn lần đầu xuất hiện trong tầm nhìn camera với throttle 1 cụm/frame.
     */
    public void renderStaticBase(Graphics2D g, Camera camera) {
        if (cachedStaticBase == null) {
            if (staticBakesThisFrame >= MAX_STATIC_BAKES_PER_FRAME) {
                return; // Throttle: Chờ frame sau nướng để tránh spike time
            }
            buildStaticBaseCache();
            staticBakesThisFrame++;
        }
        if (cachedStaticBase != null) {
            Point2D.Double screenPos = camera.worldToScreen(centerX, centerY);
            int drawX = (int) Math.round(screenPos.x - cachedStaticBase.getWidth() / 2.0);
            int drawY = (int) Math.round(screenPos.y - cachedStaticBase.getHeight() / 2.0);
            g.drawImage(cachedStaticBase, drawX, drawY, null);
        }
    }

    /**
     * Kiểm tra va chạm với lõi rạn cứng (Bán kính tối đa 90px hoặc chia 2-3 lõi nhỏ).
     */
    public boolean isCollidingWithCore(double x, double y, double radius) {
        for (CoreNode node : coreNodes) {
            if (Math.hypot(x - node.worldX, y - node.worldY) < node.radius + radius) {
                return true;
            }
        }
        return false;
    }

    /**
     * Nếu rắn hoặc thực thể bị lọt vào trong lõi cứng (ví dụ khi vừa spawn),
     * đẩy nhẹ ra vị trí rìa lõi an toàn gần nhất để không bao giờ bị kẹt.
     */
    public Point2D.Double resolveCorePenetration(double x, double y, double radius) {
        double curX = x;
        double curY = y;
        boolean pushed = false;

        for (CoreNode node : coreNodes) {
            double reqDist = node.radius + radius + 2.0;
            double dx = curX - node.worldX;
            double dy = curY - node.worldY;
            double dist = Math.hypot(dx, dy);

            if (dist < reqDist) {
                pushed = true;
                if (dist < 1e-4) {
                    dx = 1.0;
                    dy = 0.0;
                    dist = 1.0;
                }
                curX = node.worldX + (dx / dist) * reqDist;
                curY = node.worldY + (dy / dist) * reqDist;
            }
        }

        return pushed ? new Point2D.Double(curX, curY) : new Point2D.Double(x, y);
    }

    /**
     * Vùng ẩn nấp ngụy trang: 80% elip bao ngoài, cho phép rắn bơi xuyên qua và hồi +4 HP/giây.
     */
    public boolean isInsideHidingZone(double x, double y) {
        double dx = x - centerX;
        double dy = y - centerY;

        double cos = Math.cos(-rotation);
        double sin = Math.sin(-rotation);

        double localX = dx * cos - dy * sin;
        double localY = dx * sin + dy * cos;

        localY -= arcCurvature * (localX * localX / radiusX);

        double nx = localX / hideRadiusX;
        double ny = localY / hideRadiusY;

        return (nx * nx + ny * ny) <= 1.0;
    }

    public boolean contains(double x, double y) {
        double dx = x - centerX;
        double dy = y - centerY;

        double cos = Math.cos(-rotation);
        double sin = Math.sin(-rotation);

        double localX = dx * cos - dy * sin;
        double localY = dx * sin + dy * cos;

        localY -= arcCurvature * (localX * localX / radiusX);

        double nx = localX / radiusX;
        double ny = localY / radiusY;

        return (nx * nx + ny * ny) <= 1.0;
    }

    public boolean isVisible(Camera camera) {
        return camera.isVisible(centerX, centerY, (int) Math.max(radiusX, radiusY) + 90);
    }

    public void dispose() {
        if (cachedStaticBase != null) {
            cachedStaticBase.flush();
            cachedStaticBase = null;
        }
        allCorals.clear();
        swayingCorals.clear();
        fishSchool.clear();
        bubbleCrevices.clear();
    }

    // Getters
    public List<CoralPlant> getCorals() { return allCorals; }
    public List<CoralPlant> getSwayingCorals() { return swayingCorals; }
    public List<ReefFish> getFishSchool() { return fishSchool; }
    public List<Point2D.Double> getBubbleCrevices() { return bubbleCrevices; }
    public List<CoreNode> getCoreNodes() { return coreNodes; }
    public ClusterTier getTier() { return tier; }

    public double getCenterX() { return centerX; }
    public double getCenterY() { return centerY; }
    public double getRadiusX() { return radiusX; }
    public double getRadiusY() { return radiusY; }
    public double getWidth() { return radiusX * 2.0; }
    public double getHeight() { return radiusY * 2.0; }
    public int getPlantsCount() { return allCorals.size(); }
    public boolean isInsideReef(double x, double y) { return isInsideHidingZone(x, y); }
    public double getRotation() { return rotation; }
    public double getArcCurvature() { return arcCurvature; }
    public double getHideRadiusX() { return hideRadiusX; }
    public double getHideRadiusY() { return hideRadiusY; }
}
