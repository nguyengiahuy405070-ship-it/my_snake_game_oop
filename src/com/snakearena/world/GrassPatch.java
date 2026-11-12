package com.snakearena.world;

import com.snakearena.core.Camera;

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
 * GrassPatch: Đại diện cho một "Khu Cỏ Xum Xuê" (Grass & Flower Cluster) sinh thái trên Map 1.
 * Thay thế hoàn toàn cách tạo hình đơn điệu cũ bằng hệ thống cụm cỏ và hoa tự nhiên theo 4 mẫu (A, B, C, D)
 * lấy cảm hứng từ ảnh tham khảo.
 *
 * Tính năng chính:
 * 1. Bốn mẫu cụm A/B/C/D sinh theo công thức hình học:
 *    - A: "Vành hoa quanh bụi lớn" (Bụi tròn lớn ở tâm, vành hoa dày bao quanh theo elip).
 *    - B: "Vòng cỏ cao, giữa trống" (Cỏ cao nhiều cỡ xếp thành vành bầu dục, giữa là bãi cỏ ngắn sáng).
 *    - C: "Vành hoa thấp quanh bãi trống" (Vành hoa nhiều màu, tâm trống, một bụi hoa vàng phía sau).
 *    - D: "Cỏ rậm có bụi tròn" (Cỏ dày, bụi tròn ở giữa, hoa vàng chạy dọc mép dưới).
 * 2. Cỡ cụm: Nhỏ 260-380px (40%), Vừa 380-560px (45%), Lớn 560-800px (15%).
 * 3. Nền chung: Mảng nền cỏ nhạt/đậm (alpha ~0.35, mép dither) và một bóng mềm chung (alpha 0.15-0.25).
 * 4. Vùng ẩn nấp ngụy trang: Elip bao cụm thu nhỏ 0.8 (bao gồm cả bãi trống giữa) kết nối hồi máu +4 HP/s.
 * 5. Hiệu năng tối đa (Zero-Lag):
 *    - Gom phần tĩnh (nền dither, bóng mềm, bãi trống, bụi tròn lớn) vào 1 BufferedImage tương thích.
 *    - Nướng ảnh tĩnh lười (Lazy baking) khi lần đầu vào tầm nhìn, throttle tối đa 1 cụm mỗi frame.
 *    - Chỉ tối đa 14 sprite đung đưa động (swaying) được y-sort riêng; phần còn lại nướng trong ảnh tĩnh.
 */
public class GrassPatch {

    // =========================================================================
    // HẰNG SỐ CẤU HÌNH CỤM CỎ VÀ HOA MAP 1 (GRASS & FLOWER CLUSTER CONSTANTS)
    // =========================================================================
    // Kích thước các cấp cụm (Cluster Size Tiers)
    public static final int CLUSTER_SMALL_MIN_WIDTH = 260;
    public static final int CLUSTER_SMALL_MAX_WIDTH = 380;
    public static final int CLUSTER_MEDIUM_MIN_WIDTH = 380;
    public static final int CLUSTER_MEDIUM_MAX_WIDTH = 560;
    public static final int CLUSTER_LARGE_MIN_WIDTH = 560;
    public static final int CLUSTER_LARGE_MAX_WIDTH = 800;

    // Tỉ lệ xác suất các cỡ cụm (40% nhỏ, 45% vừa, 15% lớn)
    public static final double RATIO_SMALL_CLUSTER = 0.40;
    public static final double RATIO_MEDIUM_CLUSTER = 0.45;
    public static final double RATIO_LARGE_CLUSTER = 0.15;

    // Kích thước thực vật chuẩn trong game (so với thân rắn 18px)
    public static final int GRASS_MIN_HEIGHT = 20;
    public static final int GRASS_MAX_HEIGHT = 56;
    public static final int FLOWER_MIN_HEIGHT = 14;
    public static final int FLOWER_MAX_HEIGHT = 26;
    public static final int BUSH_DOME_MIN_SIZE = 70;
    public static final int BUSH_DOME_MAX_SIZE = 130;
    public static final int BUSH_YELLOW_MIN_SIZE = 40;
    public static final int BUSH_YELLOW_MAX_SIZE = 60;

    // Cấu hình vùng ẩn nấp & bóng đổ
    public static final double HIDING_ZONE_RATIO = 0.80;       // Vùng ẩn nấp thu nhỏ 0.8 elip bao
    public static final float CLUSTER_SHADOW_ALPHA = 0.20f;    // Alpha bóng mềm duy nhất (0.15 - 0.25)
    public static final float CLUSTER_BASE_ALPHA = 0.35f;      // Alpha mảng nền cỏ (0.35)
    public static final float OCCLUSION_ALPHA = 0.70f;         // Alpha cỏ phía trước làm mờ thấy rắn

    // Tương thích ngược với các test cũ
    public static final int PATCH_MIN_WIDTH = CLUSTER_SMALL_MIN_WIDTH;
    public static final int PATCH_MAX_WIDTH = CLUSTER_LARGE_MAX_WIDTH;
    public static final int PATCH_MIN_HEIGHT = 170;
    public static final int PATCH_MAX_HEIGHT = 560;
    public static final int MIN_PLANTS_PER_PATCH = 10;
    public static final int MAX_PLANTS_PER_PATCH = 60;

    // Tối ưu hiệu năng: Giới hạn cây động & throttle nướng ảnh tĩnh
    public static final int MAX_SWAYING_PLANTS_PER_CLUSTER = 14; // Tối đa 14 sprite đung đưa riêng
    public static final int MAX_STATIC_BAKES_PER_FRAME = 1;       // Tối đa 1 cụm nướng ảnh tĩnh mỗi frame

    // Biến static điều phối throttle nướng ảnh tĩnh trong frame hiện tại
    private static int staticBakesThisFrame = 0;

    public static void resetFrameBakeCount() {
        staticBakesThisFrame = 0;
    }

    // 4 Mẫu Cụm (Pattern A, B, C, D)
    public enum Pattern {
        PATTERN_A_DOME_FLOWER_RING,     // A: Vành hoa quanh bụi lớn
        PATTERN_B_TALL_GRASS_CLEARING,  // B: Vòng cỏ cao, giữa trống
        PATTERN_C_LOW_FLOWERS_CLEARING, // C: Vành hoa thấp quanh bãi trống
        PATTERN_D_DENSE_GRASS_BUSHES    // D: Cỏ rậm có bụi tròn
    }

    static {
        // Kiểm tra chống nhảy hình (popping) khi cuộn: nửa kích thước lớn nhất <= CHUNK_SIZE * BUFFER_CHUNKS - 60
        double maxHalfDim = CLUSTER_LARGE_MAX_WIDTH / 2.0;
        double maxAllowed = com.snakearena.core.Config.World.CHUNK_SIZE * com.snakearena.core.Config.World.BUFFER_CHUNKS - 60.0;
        if (maxHalfDim > maxAllowed) {
            System.err.println("[WARNING] GrassPatch half-dimension (" + maxHalfDim + "px) exceeds buffer limit (" + maxAllowed + "px)!");
        }
        assert maxHalfDim <= maxAllowed : "GrassPatch max half-dimension exceeds chunk buffer limit!";
    }

    // =========================================================================
    // CẤU TRÚC PHẦN TỬ THỰC VẬT TRONG CỤM (CLUSTER PLANT)
    // =========================================================================
    public static class ClusterPlant {
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

        public ClusterPlant(double relX, double relY, double worldX, double worldY, double footY,
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
    }

    // =========================================================================
    // THUỘC TÍNH CỤM CỎ
    // =========================================================================
    public final double centerX;
    public final double centerY;
    public final double radiusX;
    public final double radiusY;
    public final double hideRadiusX;
    public final double hideRadiusY;
    public final double rotation;
    public final Pattern pattern;
    public final double scale;
    public final boolean isFlippedCluster;
    public final long seed;

    // Bounding Box cho Frustum Culling
    public final double minX;
    public final double maxX;
    public final double minY;
    public final double maxY;

    // Danh sách thực vật đung đưa riêng (Tối đa 14 cây nổi bật)
    private final List<ClusterPlant> swayingPlants = new ArrayList<>();

    // Danh sách toàn bộ thực vật (dùng khi nướng ảnh tĩnh)
    private final List<ClusterPlant> allPlants = new ArrayList<>();

    // Cache ảnh tĩnh (Nền dither, bóng mềm, bãi trống, bụi tròn, cây nền)
    private BufferedImage cachedStaticBase = null;

    public GrassPatch(double centerX, double centerY, double radiusX, double radiusY,
                      Pattern pattern, double scale, boolean isFlippedCluster, Random rng) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.radiusX = radiusX;
        this.radiusY = radiusY;
        this.pattern = pattern;
        this.scale = scale;
        this.isFlippedCluster = isFlippedCluster;
        this.rotation = (rng.nextFloat() - 0.5) * 0.40;
        this.seed = rng.nextLong();

        // Vùng ẩn nấp ngụy trang: elip thu nhỏ 0.8
        this.hideRadiusX = this.radiusX * HIDING_ZONE_RATIO;
        this.hideRadiusY = this.radiusY * HIDING_ZONE_RATIO;

        // Bounding Box Culling
        double pad = 80.0;
        this.minX = centerX - radiusX - pad;
        this.maxX = centerX + radiusX + pad;
        this.minY = centerY - radiusY - pad;
        this.maxY = centerY + radiusY + pad;

        // Sinh danh sách thực vật theo mẫu đã chọn
        generateClusterPlants(rng);
    }

    // Constructor tương thích ngược
    public GrassPatch(double centerX, double centerY, double radiusX, double radiusY, double rotation, Random rng) {
        this(centerX, centerY, radiusX, radiusY,
             Pattern.values()[rng.nextInt(Pattern.values().length)],
             0.9 + rng.nextFloat() * 0.3,
             rng.nextBoolean(),
             rng);
    }

    /**
     * Sinh các thực vật theo đúng 4 công thức mẫu hình học (A, B, C, D)
     */
    private void generateClusterPlants(Random rng) {
        allPlants.clear();
        swayingPlants.clear();

        double cosR = Math.cos(rotation);
        double sinR = Math.sin(rotation);

        switch (pattern) {
            case PATTERN_A_DOME_FLOWER_RING -> {
                // MẪU A: "VÀNH HOA QUANH BỤI LỚN"
                // 1. Bụi tròn lớn ở tâm/phía sau
                int domeW = (int) Math.round(97 * scale * 1.15);
                int domeH = (int) Math.round(91 * scale * 1.15);
                domeW = Math.max(BUSH_DOME_MIN_SIZE, Math.min(BUSH_DOME_MAX_SIZE, domeW));
                domeH = Math.max(BUSH_DOME_MIN_SIZE, Math.min(BUSH_DOME_MAX_SIZE, domeH));
                addPlant(0, -radiusY * 0.15, VegetationManager.SPRITE_BUSH_DOME, domeW, domeH,
                        isFlippedCluster, 0.0, false, cosR, sinR);

                // 2. Vành hoa dày (tím, hồng, đỏ: flower_1..5) xếp theo elip bao quanh bụi tròn
                int flowerCount = 14 + rng.nextInt(7);
                for (int i = 0; i < flowerCount; i++) {
                    double angle = (i * 2.0 * Math.PI) / flowerCount + (rng.nextFloat() - 0.5) * 0.25;
                    // Bán kính vành hoa ôm sát quanh bụi
                    double rRatio = 0.55 + rng.nextFloat() * 0.38;
                    double px = Math.cos(angle) * (radiusX * rRatio);
                    double py = Math.sin(angle) * (radiusY * rRatio) + radiusY * 0.10;

                    int flId = VegetationManager.SPRITE_FLOWER_1 + rng.nextInt(5); // tím, hồng, đỏ
                    int flH = (int) Math.round((FLOWER_MIN_HEIGHT + rng.nextInt(FLOWER_MAX_HEIGHT - FLOWER_MIN_HEIGHT + 1)) * 1.05);
                    int flW = (int) Math.round(flH * 0.90);
                    boolean flFlip = rng.nextBoolean();
                    double phase = px * 0.04 + py * 0.04 + i * 0.6;
                    addPlant(px, py, flId, flW, flH, flFlip, phase, true, cosR, sinR);
                }

                // 3. Vài cọng cỏ cao chen vào mép sau và bên hông
                int grassCount = 6 + rng.nextInt(5);
                for (int i = 0; i < grassCount; i++) {
                    double angle = (rng.nextFloat() * Math.PI * 1.2) - Math.PI * 0.1; // nửa trên
                    double rRatio = 0.82 + rng.nextFloat() * 0.18;
                    double px = Math.cos(angle) * (radiusX * rRatio);
                    double py = Math.sin(angle) * (radiusY * rRatio);

                    int gId = VegetationManager.SPRITE_GRASS_4 + rng.nextInt(4); // cỏ vừa đến lớn
                    int gH = (int) Math.round((GRASS_MIN_HEIGHT + 18 + rng.nextInt(18)) * scale);
                    int gW = (int) Math.round(gH * 0.92);
                    boolean gFlip = rng.nextBoolean();
                    double phase = px * 0.03 + py * 0.03 + i * 0.8;
                    addPlant(px, py, gId, gW, gH, gFlip, phase, true, cosR, sinR);
                }
            }

            case PATTERN_B_TALL_GRASS_CLEARING -> {
                // MẪU B: "VÒNG CỎ CAO, GIỮA TRỐNG"
                // 1. Cỏ cao nhiều cỡ xếp thành vành bầu dục, giữa là bãi cỏ trống sáng
                int grassCount = 18 + rng.nextInt(8);
                for (int i = 0; i < grassCount; i++) {
                    double angle = (i * 2.0 * Math.PI) / grassCount + (rng.nextFloat() - 0.5) * 0.20;
                    double rRatio = 0.72 + rng.nextFloat() * 0.26; // ngoài vành
                    double px = Math.cos(angle) * (radiusX * rRatio);
                    double py = Math.sin(angle) * (radiusY * rRatio);

                    int gId = (rng.nextFloat() < 0.25)
                            ? VegetationManager.SPRITE_GRASS_CLUMP
                            : (VegetationManager.SPRITE_GRASS_3 + rng.nextInt(5));

                    int gH = (int) Math.round((GRASS_MIN_HEIGHT + 16 + rng.nextInt(20)) * scale);
                    int gW = (int) Math.round(gH * 0.90);
                    boolean gFlip = rng.nextBoolean();
                    double phase = px * 0.04 + py * 0.04 + i * 0.7;
                    addPlant(px, py, gId, gW, gH, gFlip, phase, true, cosR, sinR);
                }

                // 2. Hoa vàng & tím rải một nửa vành (phía trước & mép hông)
                int flowerCount = 8 + rng.nextInt(6);
                for (int i = 0; i < flowerCount; i++) {
                    double angle = (rng.nextFloat() * Math.PI * 0.95) + 0.1; // nửa dưới
                    double rRatio = 0.65 + rng.nextFloat() * 0.28;
                    double px = Math.cos(angle) * (radiusX * rRatio);
                    double py = Math.sin(angle) * (radiusY * rRatio);

                    int flId = (rng.nextFloat() < 0.65)
                            ? (VegetationManager.SPRITE_FLOWER_6 + rng.nextInt(3)) // vàng / cụm vàng / tím-vàng
                            : VegetationManager.SPRITE_FLOWER_1;                 // tím

                    int flH = (int) Math.round((FLOWER_MIN_HEIGHT + rng.nextInt(FLOWER_MAX_HEIGHT - FLOWER_MIN_HEIGHT + 1)) * 1.05);
                    int flW = (int) Math.round(flH * 0.90);
                    boolean flFlip = rng.nextBoolean();
                    double phase = px * 0.04 + py * 0.04 + i * 0.6;
                    addPlant(px, py, flId, flW, flH, flFlip, phase, true, cosR, sinR);
                }
            }

            case PATTERN_C_LOW_FLOWERS_CLEARING -> {
                // MẪU C: "VÀNH HOA THẤP QUANH BÃI TRỐNG"
                // 1. Một bụi hoa vàng phía sau
                int ybW = (int) Math.round(60 * scale);
                int ybH = (int) Math.round(48 * scale);
                ybW = Math.max(BUSH_YELLOW_MIN_SIZE, Math.min(BUSH_YELLOW_MAX_SIZE, ybW));
                ybH = Math.max(BUSH_YELLOW_MIN_SIZE, Math.min(BUSH_YELLOW_MAX_SIZE, ybH));
                addPlant(0, -radiusY * 0.35, VegetationManager.SPRITE_BUSH_YELLOW, ybW, ybH,
                        isFlippedCluster, 0.0, false, cosR, sinR);

                // 2. Vành hoa đa sắc bao quanh bãi trống tâm
                int flowerCount = 18 + rng.nextInt(8);
                for (int i = 0; i < flowerCount; i++) {
                    double angle = (i * 2.0 * Math.PI) / flowerCount + (rng.nextFloat() - 0.5) * 0.25;
                    double rRatio = 0.60 + rng.nextFloat() * 0.36;
                    double px = Math.cos(angle) * (radiusX * rRatio);
                    double py = Math.sin(angle) * (radiusY * rRatio);

                    // Phân bổ màu sắc: tím, hồng, vàng xen kẽ rực rỡ
                    int flId = VegetationManager.SPRITE_FLOWER_1 + rng.nextInt(8);
                    int flH = (int) Math.round((FLOWER_MIN_HEIGHT + rng.nextInt(FLOWER_MAX_HEIGHT - FLOWER_MIN_HEIGHT + 1)) * 1.05);
                    int flW = (int) Math.round(flH * 0.90);
                    boolean flFlip = rng.nextBoolean();
                    double phase = px * 0.04 + py * 0.04 + i * 0.6;
                    addPlant(px, py, flId, flW, flH, flFlip, phase, true, cosR, sinR);
                }

                // 3. Vài nhánh cỏ con nhỏ viền ngoài
                int smallGrass = 6 + rng.nextInt(5);
                for (int i = 0; i < smallGrass; i++) {
                    double angle = rng.nextFloat() * Math.PI * 2.0;
                    double rRatio = 0.88 + rng.nextFloat() * 0.12;
                    double px = Math.cos(angle) * (radiusX * rRatio);
                    double py = Math.sin(angle) * (radiusY * rRatio);

                    int gId = VegetationManager.SPRITE_GRASS_1 + rng.nextInt(3); // cỏ nhỏ
                    int gH = (int) Math.round((GRASS_MIN_HEIGHT + rng.nextInt(10)) * scale);
                    int gW = (int) Math.round(gH * 0.95);
                    boolean gFlip = rng.nextBoolean();
                    double phase = px * 0.04 + py * 0.04 + i * 0.7;
                    addPlant(px, py, gId, gW, gH, gFlip, phase, true, cosR, sinR);
                }
            }

            case PATTERN_D_DENSE_GRASS_BUSHES -> {
                // MẪU D: "CỎ RẬM CÓ BỤI TRÒN"
                // 1. Vài bụi tròn ở giữa (1 bụi dome nhỏ hoặc 1-2 bụi vàng)
                if (rng.nextBoolean()) {
                    int domeW = (int) Math.round(85 * scale);
                    int domeH = (int) Math.round(80 * scale);
                    addPlant(-radiusX * 0.10, -radiusY * 0.08, VegetationManager.SPRITE_BUSH_DOME,
                            domeW, domeH, isFlippedCluster, 0.0, false, cosR, sinR);
                }
                int ybW = (int) Math.round(52 * scale);
                int ybH = (int) Math.round(44 * scale);
                addPlant(radiusX * 0.22, radiusY * 0.05, VegetationManager.SPRITE_BUSH_YELLOW,
                        ybW, ybH, !isFlippedCluster, 0.0, false, cosR, sinR);

                // 2. Cỏ dày rậm rạp bao quanh
                int grassCount = 16 + rng.nextInt(8);
                for (int i = 0; i < grassCount; i++) {
                    double angle = (i * 2.0 * Math.PI) / grassCount + (rng.nextFloat() - 0.5) * 0.25;
                    double rRatio = 0.50 + rng.nextFloat() * 0.48;
                    double px = Math.cos(angle) * (radiusX * rRatio);
                    double py = Math.sin(angle) * (radiusY * rRatio);

                    int gId = (rng.nextFloat() < 0.30)
                            ? VegetationManager.SPRITE_GRASS_CLUMP
                            : (VegetationManager.SPRITE_GRASS_4 + rng.nextInt(4));

                    int gH = (int) Math.round((GRASS_MIN_HEIGHT + 16 + rng.nextInt(20)) * scale);
                    int gW = (int) Math.round(gH * 0.90);
                    boolean gFlip = rng.nextBoolean();
                    double phase = px * 0.04 + py * 0.04 + i * 0.7;
                    addPlant(px, py, gId, gW, gH, gFlip, phase, true, cosR, sinR);
                }

                // 3. Hoa vàng chạy dọc mép dưới
                int flowerCount = 7 + rng.nextInt(5);
                for (int i = 0; i < flowerCount; i++) {
                    double angle = (rng.nextFloat() * Math.PI * 0.7) + Math.PI * 0.15; // mép dưới
                    double rRatio = 0.70 + rng.nextFloat() * 0.28;
                    double px = Math.cos(angle) * (radiusX * rRatio);
                    double py = Math.sin(angle) * (radiusY * rRatio);

                    int flId = (rng.nextFloat() < 0.75)
                            ? VegetationManager.SPRITE_FLOWER_6  // hoa vàng
                            : VegetationManager.SPRITE_FLOWER_7; // cụm hoa vàng

                    int flH = (int) Math.round((FLOWER_MIN_HEIGHT + rng.nextInt(FLOWER_MAX_HEIGHT - FLOWER_MIN_HEIGHT + 1)) * 1.05);
                    int flW = (int) Math.round(flH * 0.90);
                    boolean flFlip = rng.nextBoolean();
                    double phase = px * 0.04 + py * 0.04 + i * 0.6;
                    addPlant(px, py, flId, flW, flH, flFlip, phase, true, cosR, sinR);
                }
            }
        }

        // Sắp xếp allPlants theo footY
        allPlants.sort(Comparator.comparingDouble(p -> p.footY));

        // Phân loại tối đa 14 cây đung đưa riêng (ưu tiên các cây ở phía trước/mép nổi bật)
        List<ClusterPlant> candidates = new ArrayList<>();
        for (ClusterPlant p : allPlants) {
            if (p.isSwaying && VegetationManager.isSwayable(p.spriteId)) {
                candidates.add(p);
            }
        }

        if (candidates.size() <= MAX_SWAYING_PLANTS_PER_CLUSTER) {
            swayingPlants.addAll(candidates);
        } else {
            // Lấy đều các cây cách đều nhau để cả cụm đều có điểm xao động tự nhiên
            double step = (double) candidates.size() / MAX_SWAYING_PLANTS_PER_CLUSTER;
            for (int k = 0; k < MAX_SWAYING_PLANTS_PER_CLUSTER; k++) {
                int idx = Math.min(candidates.size() - 1, (int) Math.floor(k * step));
                swayingPlants.add(candidates.get(idx));
            }
        }
    }

    private void addPlant(double relX, double relY, int spriteId, int w, int h,
                          boolean isFlipped, double swayPhase, boolean canSway,
                          double cosR, double sinR) {
        if (isFlippedCluster) {
            relX = -relX;
            isFlipped = !isFlipped;
        }

        // Xoay tọa độ tương đối theo góc elip cụm
        double rotX = relX * cosR - relY * sinR;
        double rotY = relX * sinR + relY * cosR;

        double wx = centerX + rotX;
        double wy = centerY + rotY;
        double footY = wy + h * 0.35;

        allPlants.add(new ClusterPlant(relX, relY, wx, wy, footY, spriteId, w, h, isFlipped, swayPhase, canSway));
    }

    /**
     * Nướng ảnh tĩnh lười (Lazy baking):
     * - Nền dither hạt cỏ/sỏi (alpha ~0.35).
     * - Bóng mềm mờ duy nhất (alpha 0.15 - 0.25).
     * - Bãi cỏ trống sáng tâm (Mẫu B và C).
     * - Các bụi tròn tĩnh (`bush_dome`, `bush_yellow`) và các cây nền không đung đưa.
     */
    public synchronized void buildStaticBaseCache() {
        if (cachedStaticBase != null) return;

        int imgW = (int) Math.ceil(radiusX * 2.0 + 120);
        int imgH = (int) Math.ceil(radiusY * 2.0 + 110);
        float cx = imgW / 2.0f;
        float cy = imgH / 2.0f;

        cachedStaticBase = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = cachedStaticBase.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        AffineTransform oldXf = g.getTransform();
        g.rotate(rotation, cx, cy);

        // 1. MỘT bóng mềm chung duy nhất cho toàn bộ cụm (Soft Ellipse Shadow)
        float shadowY = cy + 12.0f;
        int shadowW = (int) (radiusX * 2.08);
        int shadowH = (int) (radiusY * 1.95);

        float[] fractions = {0.0f, 0.50f, 0.85f, 1.0f};
        Color[] shadowColors = {
            new Color(20, 24, 16, (int) (255 * CLUSTER_SHADOW_ALPHA)),
            new Color(25, 30, 20, (int) (255 * CLUSTER_SHADOW_ALPHA * 0.70f)),
            new Color(30, 36, 24, (int) (255 * CLUSTER_SHADOW_ALPHA * 0.22f)),
            new Color(30, 36, 24, 0)
        };

        AffineTransform at = AffineTransform.getTranslateInstance(cx, shadowY);
        at.scale(shadowW / 2.0, shadowH / 2.0);

        RadialGradientPaint rgp = new RadialGradientPaint(
            new Point2D.Float(0, 0), 1.0f, new Point2D.Float(0, 0),
            fractions, shadowColors, MultipleGradientPaint.CycleMethod.NO_CYCLE,
            MultipleGradientPaint.ColorSpaceType.SRGB, at
        );
        g.setPaint(rgp);
        g.fillOval((int) (cx - shadowW / 2.0), (int) (shadowY - shadowH / 2.0), shadowW, shadowH);

        // 2. Mảng nền cỏ bo theo hình cụm (alpha ~0.35, mép dither)
        int baseW = (int) (radiusX * 1.82);
        int baseH = (int) (radiusY * 1.76);
        float[] baseFractions = {0.0f, 0.65f, 1.0f};
        Color[] baseColors = {
            new Color(38, 92, 48, (int) (255 * CLUSTER_BASE_ALPHA)),
            new Color(48, 114, 58, (int) (255 * CLUSTER_BASE_ALPHA * 0.65f)),
            new Color(48, 114, 58, 0)
        };
        AffineTransform baseAt = AffineTransform.getTranslateInstance(cx, cy + 2.0f);
        baseAt.scale(baseW / 2.0, baseH / 2.0);

        RadialGradientPaint baseRgp = new RadialGradientPaint(
            new Point2D.Float(0, 0), 1.0f, new Point2D.Float(0, 0),
            baseFractions, baseColors, MultipleGradientPaint.CycleMethod.NO_CYCLE,
            MultipleGradientPaint.ColorSpaceType.SRGB, baseAt
        );
        g.setPaint(baseRgp);
        g.fillOval((int) (cx - baseW / 2.0), (int) (cy + 2.0f - baseH / 2.0), baseW, baseH);

        // 3. Bãi cỏ trống sáng màu mịn ở tâm (Cho mẫu B và C)
        if (pattern == Pattern.PATTERN_B_TALL_GRASS_CLEARING || pattern == Pattern.PATTERN_C_LOW_FLOWERS_CLEARING) {
            int clearW = (int) (radiusX * 0.95);
            int clearH = (int) (radiusY * 0.88);
            float[] clearFractions = {0.0f, 0.70f, 1.0f};
            Color[] clearColors = {
                new Color(110, 185, 75, (int) (255 * 0.38f)),
                new Color(90, 165, 65, (int) (255 * 0.22f)),
                new Color(90, 165, 65, 0)
            };
            AffineTransform clearAt = AffineTransform.getTranslateInstance(cx, cy + 4.0f);
            clearAt.scale(clearW / 2.0, clearH / 2.0);
            RadialGradientPaint clearRgp = new RadialGradientPaint(
                new Point2D.Float(0, 0), 1.0f, new Point2D.Float(0, 0),
                clearFractions, clearColors, MultipleGradientPaint.CycleMethod.NO_CYCLE,
                MultipleGradientPaint.ColorSpaceType.SRGB, clearAt
            );
            g.setPaint(clearRgp);
            g.fillOval((int) (cx - clearW / 2.0), (int) (cy + 4.0f - clearH / 2.0), clearW, clearH);
        }

        // 4. Viền dither hạt cỏ và sỏi li ti hòa vào nền
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        Random baseRng = new Random(seed);
        int ditherCount = 65 + baseRng.nextInt(30);
        for (int i = 0; i < ditherCount; i++) {
            double ang = baseRng.nextFloat() * Math.PI * 2;
            double rFrac = 0.80 + baseRng.nextFloat() * 0.25;
            int px = (int) Math.round(cx + Math.cos(ang) * radiusX * rFrac);
            int py = (int) Math.round(cy + Math.sin(ang) * radiusY * rFrac);

            double dType = baseRng.nextFloat();
            if (dType < 0.60) {
                g.setColor(new Color(55, 125, 62, 160));
                g.fillRect(px, py, 1, 2);
                g.fillRect(px + 1, py - 1, 1, 3);
            } else if (dType < 0.85) {
                g.setColor(new Color(42, 98, 48, 140));
                g.fillRect(px, py, 2, 1);
            } else {
                g.setColor(new Color(138, 122, 68, 130)); // hạt cỏ khô
                g.fillRect(px, py, 1, 1);
            }
        }

        // 5. Nướng các thực vật tĩnh (bụi tròn dome, bụi hoa vàng, cây nền không đung đưa)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for (ClusterPlant p : allPlants) {
            // Cây đung đưa đã được chọn vào swayingPlants sẽ được vẽ động theo nhịp gió, bỏ qua ở nền tĩnh
            if (swayingPlants.contains(p)) continue;

            BufferedImage sprite = VegetationManager.getSprite(p.spriteId, p.isFlipped);
            if (sprite != null) {
                // Tọa độ trên ảnh tĩnh cache
                int drawX = (int) Math.round(cx + p.relX - p.width / 2.0);
                int drawY = (int) Math.round(cy + p.relY - p.height * 0.82);
                g.drawImage(sprite, drawX, drawY, p.width, p.height, null);
            }
        }

        g.setTransform(oldXf);
        g.dispose();
    }

    /**
     * Kết xuất nền tĩnh của cụm (bao gồm bóng mềm, nền dither, bãi trống, bụi tròn tĩnh).
     * Áp dụng Throttle: tối đa 1 cụm nướng ảnh tĩnh mỗi frame để game mượt mà tuyệt đối.
     */
    public void renderStaticBase(Graphics2D g, Camera camera) {
        if (cachedStaticBase == null) {
            if (staticBakesThisFrame < MAX_STATIC_BAKES_PER_FRAME) {
                buildStaticBaseCache();
                staticBakesThisFrame++;
            } else {
                // Nếu frame này đã nướng đủ 1 cụm, vẽ bóng mờ tạm thời để không bị pop
                drawTemporaryBase(g, camera);
                return;
            }
        }

        if (cachedStaticBase != null) {
            Point2D.Double pos = camera.worldToScreen(centerX, centerY);
            int drawX = (int) Math.round(pos.x - cachedStaticBase.getWidth() / 2.0);
            int drawY = (int) Math.round(pos.y - cachedStaticBase.getHeight() / 2.0);
            g.drawImage(cachedStaticBase, drawX, drawY, null);
        }
    }

    private void drawTemporaryBase(Graphics2D g, Camera camera) {
        Point2D.Double pos = camera.worldToScreen(centerX, centerY);
        int sw = (int) (radiusX * 2.0);
        int sh = (int) (radiusY * 1.8);
        g.setColor(new Color(25, 30, 20, (int) (255 * CLUSTER_SHADOW_ALPHA * 0.7f)));
        g.fillOval((int) Math.round(pos.x - sw / 2.0), (int) Math.round(pos.y - sh / 2.0 + 8), sw, sh);
    }

    /**
     * Kiểm tra xem điểm (wx, wy) có nằm trong vùng ẩn nấp ngụy trang (elip thu nhỏ 0.8) không.
     */
    public boolean isInsideHidingZone(double wx, double wy) {
        double dx = wx - centerX;
        double dy = wy - centerY;

        double cosR = Math.cos(-rotation);
        double sinR = Math.sin(-rotation);

        double localX = dx * cosR - dy * sinR;
        double localY = dx * sinR + dy * cosR;

        double nx = localX / hideRadiusX;
        double ny = localY / hideRadiusY;

        return (nx * nx + ny * ny) <= 1.0;
    }

    public boolean contains(double wx, double wy) {
        double dx = wx - centerX;
        double dy = wy - centerY;

        double cosR = Math.cos(-rotation);
        double sinR = Math.sin(-rotation);

        double localX = dx * cosR - dy * sinR;
        double localY = dx * sinR + dy * cosR;

        double nx = localX / radiusX;
        double ny = localY / radiusY;

        return (nx * nx + ny * ny) <= 1.0;
    }

    public boolean isVisible(Camera camera) {
        return camera.isVisible(centerX, centerY, Math.max(radiusX, radiusY) + 70);
    }

    public List<ClusterPlant> getSwayingPlants() {
        return swayingPlants;
    }

    public List<ClusterPlant> getAllPlants() {
        return allPlants;
    }

    // Tương thích ngược
    public List<ClusterPlant> getPlants() {
        return allPlants;
    }

    public double getCenterX() { return centerX; }
    public double getCenterY() { return centerY; }
    public double getRadiusX() { return radiusX; }
    public double getRadiusY() { return radiusY; }

    public BufferedImage getCachedStaticBase() {
        return cachedStaticBase;
    }

    public void dispose() {
        if (cachedStaticBase != null) {
            cachedStaticBase.flush();
            cachedStaticBase = null;
        }
    }
}
