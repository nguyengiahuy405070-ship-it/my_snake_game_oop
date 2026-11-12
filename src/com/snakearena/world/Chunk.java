package com.snakearena.world;

import com.snakearena.core.Config;
import com.snakearena.core.FastNoise;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Quản lý một vùng không gian (Chunk 512x512) trong thế giới vô hạn.
 * Sử dụng kỹ thuật Offscreen BufferedImage Cache để vẽ địa hình và decals với hiệu năng tối đa.
 * Hỗ trợ làm mịn mặt nạ nước loại bỏ răng cưa, bờ hồ 3 lớp (bùn ướt, cát nhạt, cỏ),
 * lòng hồ 3 độ sâu, chuyển màu nền mượt mà bằng Multi-Octave FBM Noise và Dithering,
 * phân bố vật thể theo cụm sinh thái (Rừng rậm, Bãi đá, Đồng trống).
 */
public class Chunk {
    // ==========================================
    // CÁC HẰNG SỐ ĐỊA HÌNH, NOISE, HỒ NƯỚC & CỤM VẬT THỂ
    // ==========================================
    public static final int TILE_SIZE = 8;                  // Kích thước ô vẽ (giảm xuống 8px để nền mịn và đường cong mượt)
    public static final int NOISE_OCTAVES = 3;              // Số tầng octave sinh màu địa hình tự nhiên
    public static final double NOISE_SCALE_BIOME = 0.0018;  // Tỉ lệ scale noise vùng sinh thái lớn
    public static final double NOISE_SCALE_DETAIL = 0.018;  // Tỉ lệ scale noise chi tiết địa hình
    public static final double NOISE_SCALE_COLOR = 0.045;   // Tỉ lệ scale noise biến thiên sắc độ màu

    // Phân bố vật thể theo cụm sinh thái bằng Noise (Clustering)
    public static final double PROP_CLUSTER_SCALE = 0.0035;          // Tần số phân bố cụm sinh thái
    public static final double PROP_CLUSTER_FOREST_THRESH = 0.55;    // Ngưỡng khu vực Rừng Rậm (> 0.55)
    public static final double PROP_CLUSTER_ROCK_THRESH = 0.38;      // Ngưỡng khu vực Bãi Đá (< 0.38)

    // =========================================================================
    // HẰNG SỐ PHÂN BỐ CỤM CỎ VÀ HOA MAP 1 (TWO-TIER GRID CLUSTER SYSTEM)
    // =========================================================================
    public static final double COARSE_CELL_SIZE = 1600.0;            // Lưới thô cho cụm lớn (560-800px)
    public static final double COARSE_JITTER_MAX = 120.0;            // Jitter lưới thô (px)
    public static final double FINE_CELL_SIZE = 800.0;               // Lưới mịn cho cụm vừa và nhỏ (260-560px)
    public static final double FINE_JITTER_MAX = 60.0;               // Jitter lưới mịn (px)
    public static final double MIN_CLUSTER_GAP = 100.0;              // Khoảng đệm an toàn giữa các cụm (px)
    public static final double CLUSTER_MAX_SHIFT = 150.0;            // Khoảng cách dời tâm tối đa khi va chạm (px)
    public static final int CLUSTER_MAX_SHIFT_ATTEMPTS = 3;          // Số lần thử dời tâm tối đa (3 lần)
    public static final double CAVE_CLUSTER_CLEARANCE = 40.0;        // Khoảng cách tối thiểu từ mép cụm tới hang động (px)
    public static final double CLUSTER_LAND_CHANCE = 0.90;           // Tỉ lệ sinh cụm trên ô đất liền (>= 88% phủ)
    public static final double STANDALONE_VEG_AREA = 40000.0;        // Mật độ cỏ/hoa lẻ: 1 cây mỗi 40.000px²
    public static final boolean DEBUG_GRASS_PATCHES = false;

    // Tương thích ngược
    public static final double PATCH_CELL_SIZE = FINE_CELL_SIZE;
    public static final double PATCH_JITTER_MAX = FINE_JITTER_MAX;
    public static final double MIN_PATCH_GAP = MIN_CLUSTER_GAP;
    public static final double CAVE_GRASS_CLEARANCE = CAVE_CLUSTER_CLEARANCE;
    public static final double PATCH_BIOME_THRESHOLD = -0.40;

    // =========================================================================
    // HẰNG SỐ PHÂN BỐ CỤM SAN HÔ MAP 2 (TWO-TIER REEF CLUSTER SYSTEM)
    // =========================================================================
    public static final double REEF_COARSE_CELL_SIZE = 1800.0;           // Lưới thô cho Cụm Lớn Biển Sâu (700-900px)
    public static final double REEF_COARSE_JITTER_MAX = 140.0;           // Jitter lưới thô (px)
    public static final double REEF_FINE_CELL_SIZE = 900.0;              // Lưới mịn cho Cụm Vừa và Nhỏ (160-520px)
    public static final double REEF_FINE_JITTER_MAX = 70.0;              // Jitter lưới mịn (px)
    public static final double MIN_REEF_GAP = 120.0;                     // Khoảng đệm an toàn giữa các rạn (px)
    public static final double REEF_MAX_SHIFT = 150.0;                   // Khoảng cách dời tâm tối đa khi va chạm (px)
    public static final int REEF_MAX_SHIFT_ATTEMPTS = 3;                 // Số lần thử dời tâm tối đa
    public static final double CAVE_REEF_CLEARANCE = 40.0;               // Khoảng cách tối thiểu từ mép cụm tới hang ngầm (px)
    public static final double REEF_DEEP_WATER_CHANCE = 0.90;            // Tỉ lệ sinh cụm trên ô nước sâu (>= 85% phủ)
    public static final double STANDALONE_REEF_AREA = 60000.0;           // Mật độ san hô/hải quỳ lẻ: 1 cây mỗi 60.000px²

    // Tương thích ngược
    public static final double REEF_CELL_SIZE = REEF_FINE_CELL_SIZE;
    public static final double REEF_JITTER_MAX = REEF_FINE_JITTER_MAX;
    public static final double REEF_MIN_CLEARANCE_OBSTACLE = MIN_REEF_GAP;

    static {
        // 1. Kiểm tra công thức khoảng cách lưới mịn: FINE_CELL_SIZE - 2*FINE_JITTER >= CLUSTER_MEDIUM_MAX_WIDTH + 100
        double fineClearance = FINE_CELL_SIZE - 2.0 * FINE_JITTER_MAX;
        double fineMinReq = GrassPatch.CLUSTER_MEDIUM_MAX_WIDTH + MIN_CLUSTER_GAP;
        if (fineClearance < fineMinReq) {
            System.err.println("[WARNING] Fine grid clearance violation: " + fineClearance + " < " + fineMinReq);
        }
        assert fineClearance >= fineMinReq : "Fine grid spacing formula violated!";

        // 2. Kiểm tra công thức khoảng cách lưới thô: COARSE_CELL_SIZE - 2*COARSE_JITTER >= CLUSTER_LARGE_MAX_WIDTH + 100
        double coarseClearance = COARSE_CELL_SIZE - 2.0 * COARSE_JITTER_MAX;
        double coarseMinReq = GrassPatch.CLUSTER_LARGE_MAX_WIDTH + MIN_CLUSTER_GAP;
        if (coarseClearance < coarseMinReq) {
            System.err.println("[WARNING] Coarse grid clearance violation: " + coarseClearance + " < " + coarseMinReq);
        }
        assert coarseClearance >= coarseMinReq : "Coarse grid spacing formula violated!";

        // 3. Kiểm tra công thức khoảng cách lưới mịn san hô: REEF_FINE_CELL_SIZE - 2*REEF_FINE_JITTER_MAX >= CLUSTER_MEDIUM_MAX_WIDTH + 120
        double reefFineClearance = REEF_FINE_CELL_SIZE - 2.0 * REEF_FINE_JITTER_MAX;
        double reefFineMinReq = CoralReef.CLUSTER_MEDIUM_MAX_WIDTH + MIN_REEF_GAP;
        if (reefFineClearance < reefFineMinReq) {
            System.err.println("[WARNING] Reef fine grid clearance violation: " + reefFineClearance + " < " + reefFineMinReq);
        }
        assert reefFineClearance >= reefFineMinReq : "Reef fine grid spacing formula violated!";

        // 4. Kiểm tra công thức khoảng cách lưới thô san hô: REEF_COARSE_CELL_SIZE - 2*REEF_COARSE_JITTER_MAX >= CLUSTER_LARGE_MAX_WIDTH + 120
        double reefCoarseClearance = REEF_COARSE_CELL_SIZE - 2.0 * REEF_COARSE_JITTER_MAX;
        double reefCoarseMinReq = CoralReef.CLUSTER_LARGE_MAX_WIDTH + MIN_REEF_GAP;
        if (reefCoarseClearance < reefCoarseMinReq) {
            System.err.println("[WARNING] Reef coarse grid clearance violation: " + reefCoarseClearance + " < " + reefCoarseMinReq);
        }
        assert reefCoarseClearance >= reefCoarseMinReq : "Reef coarse grid spacing formula violated!";
    }

    // Kích thước Bụi Lá Stage 1 (Cao 2-3 lần thân rắn ~18px, tỷ lệ: rắn < bụi < cây)
    public static final int BUSH_MIN_WIDTH = 46;
    public static final int BUSH_MAX_WIDTH = 56;
    public static final int BUSH_MIN_HEIGHT = 36;
    public static final int BUSH_MAX_HEIGHT = 44;

    // Palette Màn 1: Thảo nguyên (Tươi sáng, sống động, đan xen mảng ấm vàng úa và mảng lạnh rêu xanh)
    public static final Color COLOR_GRASS_VIBRANT = new Color(64, 142, 82);   // Cỏ tươi sáng sống động
    public static final Color COLOR_GRASS_MID     = new Color(48, 114, 66);   // Cỏ xanh chuẩn đậm đà
    public static final Color COLOR_GRASS_MOSS    = new Color(32, 82, 54);    // Mảng lạnh: Xanh rêu râm mát
    public static final Color COLOR_GRASS_WARM    = new Color(138, 122, 68);  // Mảng ấm: Vàng úa đón nắng
    public static final Color COLOR_DIRT_WARM     = new Color(106, 84, 58);   // Đất mùn ấm mịn
    public static final Color COLOR_DIRT_DRY      = new Color(128, 106, 76);  // Đất khô phù sa

    // Palette Bờ Hồ 3 Lớp Màn 1 (Loại bỏ viền be sáng sticker, chuyển tiếp mượt mà)
    public static final Color COLOR_SHORE_WET_MUD = new Color(38, 30, 22);    // Lớp 1: Bùn ướt tối sát mép nước (2-3px)
    public static final Color COLOR_SHORE_SAND    = new Color(132, 114, 82);  // Lớp 2: Cát nhạt không đều
    public static final Color COLOR_SHORE_GRASS_TRANS = new Color(74, 98, 56);// Lớp 3: Dải chuyển tiếp giáp cỏ

    // Palette Nước Hồ Màn 1 (3 độ sâu chuyển dần, trong trẻo)
    public static final Color COLOR_WATER_SHALLOW = new Color(36, 112, 126); // Nước nông ven bờ
    public static final Color COLOR_WATER_MID     = new Color(22, 82, 96);   // Nước vừa
    public static final Color COLOR_WATER_DEEP    = new Color(14, 54, 68);   // Lòng hồ sâu thẳm

    // Palette Màn 2: Đại dương & Bờ biển (Map 2 Overhaul)
    public static final Color COLOR_OCEAN_SHALLOW = new Color(6, 182, 212);  // Biển ngọc bích ven đảo (#06b6d4)
    public static final Color COLOR_OCEAN_MID     = new Color(2, 132, 199);  // Đại dương xanh rực rỡ (#0284c7)
    public static final Color COLOR_OCEAN_DEEP    = new Color(7, 89, 133);   // Rãnh biển sâu thẳm (#075985)
    public static final Color COLOR_ISLAND_GRASS  = new Color(22, 163, 74);  // Cây cỏ lòng đảo xanh biếc (#16a34a)

    // Bờ Cát 5 Tầng Chuyển Tiếp Tự Nhiên (Bỏ lưới lặp, hạt cát bằng noise, vệt gợn cát)
    public static final Color COLOR_BEACH_WET_MUD = new Color(110, 84, 48);   // Lớp 1: Cát ướt sẫm / phù sa sát mép nước (#6e5430)
    public static final Color COLOR_BEACH_WET     = new Color(172, 142, 88);  // Lớp 2: Cát ướt đậm màu (#ac8e58)
    public static final Color COLOR_BEACH_DRY     = new Color(237, 213, 138); // Lớp 3: Cát khô vàng nắng ấm (#edd58a)
    public static final Color COLOR_BEACH_GRAIN   = new Color(218, 184, 104); // Hạt cát đón bóng (#dab868)
    public static final Color COLOR_BEACH_DUNES   = new Color(250, 227, 156); // Gờ cồn cát khô sáng màu (#fae39c)
    public static final Color COLOR_SAND_BEACH    = COLOR_BEACH_DRY;          // Tương thích ngược

    // Đáy Biển Đại Dương Màn 2 (Tạo chiều sâu 3D: Rãnh hố sâu, gò cạn, mảng phù sa, gợn sóng cát)
    public static final Color COLOR_SEABED_TRENCH = new Color(6, 32, 54);     // #062036: Hố sâu thẳm đáy biển
    public static final Color COLOR_SEABED_SHOAL  = new Color(28, 148, 168);  // #1c94a8: Gò cạn đón nắng
    public static final Color COLOR_SEABED_SILT   = new Color(19, 88, 109);   // #13586d: Mảng cát phù sa mịn
    public static final Color COLOR_SEABED_RIPPLE = new Color(50, 162, 184);  // #32a2b8: Gờ vân gợn cát song song
    public static final Color COLOR_SEABED_GRAIN  = new Color(14, 72, 92);    // Hạt cát biển vi mô

    // Decals Đáy Biển Màn 2 (Sao biển, vỏ sò quạt, sỏi đáy biển)
    public static final Color COLOR_SEABED_STARFISH_ORANGE = new Color(249, 115, 22); // Sao biển cam rực (#f97316)
    public static final Color COLOR_SEABED_STARFISH_RED    = new Color(225, 29, 72);  // Sao biển đỏ son (#e11d48)
    public static final Color COLOR_SEABED_SHELL_IVORY     = new Color(248, 250, 252);// Vỏ sò trắng ngà (#f8fafc)
    public static final Color COLOR_SEABED_SHELL_PINK      = new Color(251, 207, 232);// Vỏ sò hồng phấn (#fbcfe8)
    public static final Color COLOR_SEABED_PEBBLE          = new Color(40, 58, 72);   // Sỏi biển xám đá phiến

    // Mật độ decals chi tiết nền (mật độ thấp, điểm xuyết thanh lịch)
    public static final int DECAL_MIN_COUNT = 10;
    public static final int DECAL_MAX_COUNT = 18;

    // ==========================================
    // BẢNG MÀU INT RGB (0xRRGGBB) PHỤC VỤ DIRECT PIXEL BUFFER WRITE
    // ==========================================
    public static final int RGB_GRASS_VIBRANT     = 0x408E52;
    public static final int RGB_GRASS_MID         = 0x307242;
    public static final int RGB_GRASS_MOSS        = 0x205236;
    public static final int RGB_GRASS_WARM        = 0x8A7A44;
    public static final int RGB_DIRT_WARM         = 0x6A543A;
    public static final int RGB_DIRT_DRY          = 0x806A4C;

    public static final int RGB_SHORE_WET_MUD     = 0x261E16;
    public static final int RGB_SHORE_SAND        = 0x847252;
    public static final int RGB_SHORE_GRASS_TRANS = 0x4A6238;

    public static final int RGB_WATER_SHALLOW     = 0x24707E;
    public static final int RGB_WATER_MID         = 0x165260;
    public static final int RGB_WATER_DEEP        = 0x0E3644;
    public static final int RGB_WATER_WAVE        = 0x4A9CAC; // new Color(74, 156, 172)
    public static final int RGB_WATER_LILY        = 0x228A4C; // new Color(34, 138, 76)

    public static final int RGB_OCEAN_SHALLOW     = 0x06B6D4;
    public static final int RGB_OCEAN_MID         = 0x0284C7;
    public static final int RGB_OCEAN_DEEP        = 0x075985;
    public static final int RGB_ISLAND_GRASS      = 0x16A34A;
    public static final int RGB_ISLAND_INTERIOR   = 0x22C55E; // new Color(34, 197, 94)

    public static final int RGB_BEACH_WET_MUD     = 0x6E5430;
    public static final int RGB_BEACH_WET         = 0xAC8E58;
    public static final int RGB_BEACH_DRY         = 0xEDD58A;
    public static final int RGB_BEACH_GRAIN       = 0xDAB868;
    public static final int RGB_BEACH_DUNES       = 0xFAE39C;

    public static final int RGB_SEABED_TRENCH     = 0x062036;
    public static final int RGB_SEABED_SHOAL      = 0x1C94A8;
    public static final int RGB_SEABED_SILT       = 0x13586D;
    public static final int RGB_SEABED_RIPPLE     = 0x32A2B8;
    public static final int RGB_SEABED_GRAIN      = 0x0E485C;

    private static final double COS_RIP_SCALE = Math.cos(0.62) * 0.18;
    private static final double SIN_RIP_SCALE = Math.sin(0.62) * 0.18;

    // ==========================================
    // CACHED GRAPHICS CONFIGURATION (KHỞI TẠO 1 LẦN DUY NHẤT)
    // ==========================================
    private static final GraphicsConfiguration GRAPHICS_CONFIG;
    static {
        GraphicsConfiguration gc = null;
        try {
            if (!GraphicsEnvironment.isHeadless()) {
                gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
            }
        } catch (Throwable t) {
            gc = null;
        }
        GRAPHICS_CONFIG = gc;
    }

    public static BufferedImage createCompatibleImage(int width, int height) {
        if (GRAPHICS_CONFIG != null) {
            try {
                BufferedImage img = GRAPHICS_CONFIG.createCompatibleImage(width, height, Transparency.OPAQUE);
                if (img.getType() == BufferedImage.TYPE_INT_RGB && img.getRaster().getDataBuffer() instanceof DataBufferInt) {
                    return img;
                }
            } catch (Throwable ignored) {}
        }
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    }

    // ==========================================
    // CẤU TRÚC DỮ LIỆU ĐỊA HÌNH NƯỚC & BỜ PHỤC VỤ HIỆU ỨNG ĐỘNG
    // ==========================================
    public static class WaterTileRun {
        public final int rx, ry, rw, rh;
        public WaterTileRun(int rx, int ry, int rw, int rh) {
            this.rx = rx; this.ry = ry; this.rw = rw; this.rh = rh;
        }
    }

    public static class ShoreSegment {
        public final int x1, y1;
        public final int x2, y2;
        public final int normX, normY; // Hướng pháp tuyến (-1, 0, 1)
        public final double phase;    // Pha lệch sóng sin liên tục theo tọa độ thế giới
        public ShoreSegment(int x1, int y1, int x2, int y2, int normX, int normY, double phase) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
            this.normX = normX; this.normY = normY; this.phase = phase;
        }
    }

    private final int cx;
    private final int cy;
    private final int size;
    private final double worldX;
    private final double worldY;
    private final int seed;
    private final int stageId;

    private BufferedImage cachedImage;
    private final List<WorldProp> props = new ArrayList<>();
    private final List<GrassPatch> grassPatches = new ArrayList<>();
    private final List<GrassPatch.ClusterPlant> standalonePlants = new ArrayList<>();
    private final List<CoralReef> coralReefs = new ArrayList<>();
    private final List<CoralReef.CoralPlant> standaloneCorals = new ArrayList<>();
    private final List<WaterTileRun> waterRuns = new ArrayList<>();
    private final List<ShoreSegment> shoreSegments = new ArrayList<>();

    public Chunk(int cx, int cy, FastNoise worldNoise, int stageId) {
        this.cx = cx;
        this.cy = cy;
        this.size = Config.World.CHUNK_SIZE;
        this.stageId = stageId;
        this.worldX = (double) cx * this.size;
        this.worldY = (double) cy * this.size;
        this.seed = FastNoise.hashCoords(cx, cy, Config.World.SEED + (stageId - 1) * 10007);

        generate(worldNoise);
    }

    public Chunk(int cx, int cy, FastNoise worldNoise) {
        this(cx, cy, worldNoise, 1);
    }

    /**
     * Xác định tọa độ (wx, wy) có phải là nước hay không.
     * Logic giữ nguyên 100% để đảm bảo va chạm và dữ liệu bản đồ hoàn toàn nhất quán.
     */
    public static boolean isWaterAt(double wx, double wy, FastNoise worldNoise, int stageId) {
        if (stageId == 2) {
            double nBiome = worldNoise.noise2D(wx * 0.0012, wy * 0.0012);
            double nDetail = worldNoise.noise2D(wx * 0.012, wy * 0.012);
            boolean isIsland = (nBiome > 0.65 && nDetail > 0.45);
            return !isIsland;
        }
        double nBiome = worldNoise.noise2D(wx * 0.0015, wy * 0.0015);
        double nDetail = worldNoise.noise2D(wx * 0.015, wy * 0.015);
        return nBiome > Config.World.WATER_THRESHOLD && nDetail > 0.40;
    }

    /**
     * Thế năng mặt nước liên tục (Continuous Water Potential field):
     * Giá trị > 0: Nước hồ / biển (càng lớn càng sâu).
     * Giá trị <= 0: Đất liền (càng âm càng xa mép nước).
     * Giá trị = 0: Đường bờ hồ chuẩn xác không răng cưa.
     */
    public static double getWaterPotential(double wx, double wy, FastNoise worldNoise, int stageId) {
        if (stageId == 2) {
            double nBiome = worldNoise.noise2D(wx * 0.0012, wy * 0.0012);
            double nDetail = worldNoise.noise2D(wx * 0.012, wy * 0.012);
            double islandPotential = Math.min((nBiome - 0.65) * 3.5, (nDetail - 0.45) * 4.0);
            return -islandPotential;
        }
        double nBiome = worldNoise.noise2D(wx * 0.0015, wy * 0.0015);
        double nDetail = worldNoise.noise2D(wx * 0.015, wy * 0.015);
        return Math.min((nBiome - Config.World.WATER_THRESHOLD) * 4.0, (nDetail - 0.40) * 3.0);
    }

    /**
     * Tính toán Fractal Brownian Motion (FBM) đa tầng octave từ FastNoise.
     */
    public static double fbm(FastNoise noise, double x, double y, int octaves) {
        double total = 0.0;
        double frequency = 1.0;
        double amplitude = 1.0;
        double maxAmp = 0.0;
        for (int i = 0; i < octaves; i++) {
            total += noise.noise2D(x * frequency, y * frequency) * amplitude;
            maxAmp += amplitude;
            amplitude *= 0.5;
            frequency *= 2.0;
        }
        return total / maxAmp;
    }

    private static double clamp01(double val) {
        return Math.max(0.0, Math.min(1.0, val));
    }

    private static Color lerpColor(Color c1, Color c2, double t) {
        if (t <= 0.0) return c1;
        if (t >= 1.0) return c2;
        int r = (int) Math.round(c1.getRed() + (c2.getRed() - c1.getRed()) * t);
        int g = (int) Math.round(c1.getGreen() + (c2.getGreen() - c1.getGreen()) * t);
        int b = (int) Math.round(c1.getBlue() + (c2.getBlue() - c1.getBlue()) * t);
        return new Color(
            Math.max(0, Math.min(255, r)),
            Math.max(0, Math.min(255, g)),
            Math.max(0, Math.min(255, b))
        );
    }

    public static int lerpRgb(int c1, int c2, double t) {
        if (t <= 0.0) return c1;
        if (t >= 1.0) return c2;
        int r1 = (c1 >> 16) & 0xFF;
        int g1 = (c1 >> 8) & 0xFF;
        int b1 = c1 & 0xFF;
        int r = (int) (r1 + (((c2 >> 16) & 0xFF) - r1) * t + 0.5);
        int g = (int) (g1 + (((c2 >> 8) & 0xFF) - g1) * t + 0.5);
        int b = (int) (b1 + ((c2 & 0xFF) - b1) * t + 0.5);
        return (r << 16) | (g << 8) | b;
    }

    private void generate(FastNoise worldNoise) {
        cachedImage = createCompatibleImage(size, size);
        int[] pixels = ((DataBufferInt) cachedImage.getRaster().getDataBuffer()).getData();

        FastNoise.Mulberry32 rng = new FastNoise.Mulberry32(seed);
        int tilesPerChunk = size / TILE_SIZE; // 512 / 8 = 64 ô

        // 1. Tạo lưới thế năng nước (Water Potential Grid) có padding 3 ô để làm mượt viền
        int pad = 3;
        int padGridSize = tilesPerChunk + pad * 2; // 70
        double[] rawWater = new double[padGridSize * padGridSize];
        for (int gx = 0; gx < padGridSize; gx++) {
            int tx = gx - pad;
            double wx = worldX + (tx + 0.5) * TILE_SIZE;
            int colOffset = gx * padGridSize;
            for (int gy = 0; gy < padGridSize; gy++) {
                int ty = gy - pad;
                double wy = worldY + (ty + 0.5) * TILE_SIZE;
                rawWater[colOffset + gy] = getWaterPotential(wx, wy, worldNoise, stageId);
            }
        }

        // Làm mịn lưới bằng bộ lọc 3x3 Gaussian/Box Blur để triệt tiêu hoàn toàn hình răng cưa
        double[] smoothWater = new double[padGridSize * padGridSize];
        for (int gx = 1; gx < padGridSize - 1; gx++) {
            int colPrev = (gx - 1) * padGridSize;
            int colCurr = gx * padGridSize;
            int colNext = (gx + 1) * padGridSize;
            for (int gy = 1; gy < padGridSize - 1; gy++) {
                double center = rawWater[colCurr + gy] * 4.0;
                double cross = rawWater[colPrev + gy] + rawWater[colNext + gy] + rawWater[colCurr + gy - 1] + rawWater[colCurr + gy + 1];
                smoothWater[colCurr + gy] = (center + cross) * 0.125;
            }
        }

        int colShallow = (stageId == 2) ? RGB_OCEAN_SHALLOW : RGB_WATER_SHALLOW;
        int colMid     = (stageId == 2) ? RGB_OCEAN_MID     : RGB_WATER_MID;
        int colDeep    = (stageId == 2) ? RGB_OCEAN_DEEP    : RGB_WATER_DEEP;

        // 2. TIỀN TÍNH TOÁN NOISE TẦN SỐ THẤP (<= 0.02) THEO LƯỚI 4x4 PIXEL (129x129 mẫu)
        int gridDim = 129;
        double[] gridMacro = null;
        double[] gridDetail = null;
        double[] gridDepth = null;
        double[] gridSilt = null;
        double[] gridRip = null;
        double[] gridIsland = null;
        double minWater = Double.MAX_VALUE;
        for (int i = 0; i < padGridSize * padGridSize; i++) {
            if (smoothWater[i] < minWater) minWater = smoothWater[i];
        }

        if (stageId == 1) {
            gridMacro = new double[gridDim * gridDim];
            gridDetail = new double[gridDim * gridDim];
            int idx = 0;
            for (int gy = 0; gy < gridDim; gy++) {
                double wy = worldY + gy * 4;
                for (int gx = 0; gx < gridDim; gx++) {
                    double wx = worldX + gx * 4;
                    gridMacro[idx] = fbm(worldNoise, wx * NOISE_SCALE_BIOME, wy * NOISE_SCALE_BIOME, 2);
                    gridDetail[idx] = fbm(worldNoise, wx * NOISE_SCALE_DETAIL, wy * NOISE_SCALE_DETAIL, 2);
                    idx++;
                }
            }
        } else {
            gridDepth = new double[gridDim * gridDim];
            gridSilt = new double[gridDim * gridDim];
            gridRip = new double[gridDim * gridDim];
            boolean needIsland = (minWater < -0.18);
            if (needIsland) {
                gridIsland = new double[gridDim * gridDim];
            }
            int idx = 0;
            for (int gy = 0; gy < gridDim; gy++) {
                double wy = worldY + gy * 4;
                for (int gx = 0; gx < gridDim; gx++) {
                    double wx = worldX + gx * 4;
                    gridDepth[idx] = fbm(worldNoise, wx * 0.0035, wy * 0.0035, 2);
                    gridSilt[idx] = worldNoise.noise2D(wx * 0.018, wy * 0.018);
                    gridRip[idx] = worldNoise.noise2D(wx * 0.012, wy * 0.012) * (16.0 * 0.18);
                    if (needIsland) {
                        gridIsland[idx] = fbm(worldNoise, wx * 0.02, wy * 0.02, 2);
                    }
                    idx++;
                }
            }
        }

        // Buffer nội suy hàng (Row Buffers) để tối ưu hóa truy cập bộ nhớ tuần tự L1/L2 Cache
        double[] rowWater = new double[size];
        double[] rowMacro = (stageId == 1) ? new double[size] : null;
        double[] rowDetail = (stageId == 1) ? new double[size] : null;
        double[] rowDepth = (stageId == 2) ? new double[size] : null;
        double[] rowSilt = (stageId == 2) ? new double[size] : null;
        double[] rowRip = (stageId == 2) ? new double[size] : null;
        double[] rowIsland = (gridIsland != null) ? new double[size] : null;

        // 2. KẾT XUẤT NỀN ĐỊA HÌNH & HỒ NƯỚC (Ghi trực tiếp vào int[] pixels, song tuyến tính 4x4)
        for (int y = 0; y < size; y++) {
            int ty = y >> 3;
            int ly = y & 7;
            double vWater = ly * 0.125;
            int gyWater = ty + pad;

            // Nội suy thế năng nước dọc theo hàng
            for (int tx = 0; tx < tilesPerChunk; tx++) {
                int gxWater = tx + pad;
                int colCurr = gxWater * padGridSize;
                int colNext = (gxWater + 1) * padGridSize;
                double w00 = smoothWater[colCurr + gyWater];
                double w10 = smoothWater[colNext + gyWater];
                double w01 = smoothWater[colCurr + gyWater + 1];
                double w11 = smoothWater[colNext + gyWater + 1];
                double wLeft = w00 + (w01 - w00) * vWater;
                double wRight = w10 + (w11 - w10) * vWater;
                double wStep = (wRight - wLeft) * 0.125;
                int px = tx << 3;
                for (int lx = 0; lx < 8; lx++) {
                    rowWater[px + lx] = wLeft + lx * wStep;
                }
            }

            // Nội suy song tuyến tính noise lưới 4x4 dọc theo hàng
            int by = y >> 2;
            int dy = y & 3;
            double fyNoise = dy * 0.25;
            int gridRowOffset = by * gridDim;

            if (stageId == 1) {
                for (int bx = 0; bx < 128; bx++) {
                    int g00 = gridRowOffset + bx;
                    int px = bx << 2;

                    double m00 = gridMacro[g00], m10 = gridMacro[g00 + 1], m01 = gridMacro[g00 + gridDim], m11 = gridMacro[g00 + gridDim + 1];
                    double mLeft = m00 + (m01 - m00) * fyNoise, mRight = m10 + (m11 - m10) * fyNoise, mStep = (mRight - mLeft) * 0.25;

                    double d00 = gridDetail[g00], d10 = gridDetail[g00 + 1], d01 = gridDetail[g00 + gridDim], d11 = gridDetail[g00 + gridDim + 1];
                    double dLeft = d00 + (d01 - d00) * fyNoise, dRight = d10 + (d11 - d10) * fyNoise, dStep = (dRight - dLeft) * 0.25;

                    rowMacro[px] = mLeft; rowMacro[px + 1] = mLeft + mStep; rowMacro[px + 2] = mLeft + 2 * mStep; rowMacro[px + 3] = mLeft + 3 * mStep;
                    rowDetail[px] = dLeft; rowDetail[px + 1] = dLeft + dStep; rowDetail[px + 2] = dLeft + 2 * dStep; rowDetail[px + 3] = dLeft + 3 * dStep;
                }
            } else {
                for (int bx = 0; bx < 128; bx++) {
                    int g00 = gridRowOffset + bx;
                    int px = bx << 2;

                    double dp00 = gridDepth[g00], dp10 = gridDepth[g00 + 1], dp01 = gridDepth[g00 + gridDim], dp11 = gridDepth[g00 + gridDim + 1];
                    double dpLeft = dp00 + (dp01 - dp00) * fyNoise, dpRight = dp10 + (dp11 - dp10) * fyNoise, dpStep = (dpRight - dpLeft) * 0.25;

                    double s00 = gridSilt[g00], s10 = gridSilt[g00 + 1], s01 = gridSilt[g00 + gridDim], s11 = gridSilt[g00 + gridDim + 1];
                    double sLeft = s00 + (s01 - s00) * fyNoise, sRight = s10 + (s11 - s10) * fyNoise, sStep = (sRight - sLeft) * 0.25;

                    double r00 = gridRip[g00], r10 = gridRip[g00 + 1], r01 = gridRip[g00 + gridDim], r11 = gridRip[g00 + gridDim + 1];
                    double rLeft = r00 + (r01 - r00) * fyNoise, rRight = r10 + (r11 - r10) * fyNoise, rStep = (rRight - rLeft) * 0.25;

                    rowDepth[px] = dpLeft; rowDepth[px + 1] = dpLeft + dpStep; rowDepth[px + 2] = dpLeft + 2 * dpStep; rowDepth[px + 3] = dpLeft + 3 * dpStep;
                    rowSilt[px] = sLeft; rowSilt[px + 1] = sLeft + sStep; rowSilt[px + 2] = sLeft + 2 * sStep; rowSilt[px + 3] = sLeft + 3 * sStep;
                    rowRip[px] = rLeft; rowRip[px + 1] = rLeft + rStep; rowRip[px + 2] = rLeft + 2 * rStep; rowRip[px + 3] = rLeft + 3 * rStep;

                    if (rowIsland != null) {
                        double is00 = gridIsland[g00], is10 = gridIsland[g00 + 1], is01 = gridIsland[g00 + gridDim], is11 = gridIsland[g00 + gridDim + 1];
                        double isLeft = is00 + (is01 - is00) * fyNoise, isRight = is10 + (is11 - is10) * fyNoise, isStep = (isRight - isLeft) * 0.25;
                        rowIsland[px] = isLeft; rowIsland[px + 1] = isLeft + isStep; rowIsland[px + 2] = isLeft + 2 * isStep; rowIsland[px + 3] = isLeft + 3 * isStep;
                    }
                }
            }

            int rowOffset = y * size;
            double wy = worldY + y;
            double wyWaveTerm = wy * 0.08;
            double wyRipTerm = wy * SIN_RIP_SCALE;
            int lModY = (int) Math.abs(wy) % 6;

            for (int x = 0; x < size; x++) {
                double waterVal = rowWater[x];
                double wx = worldX + x;
                int pixelCol;

                if (waterVal > 0.0) {
                    if (stageId == 2) {
                        // --- ĐÁY BIỂN ĐẠI DƯƠNG MÀN 2 ---
                        double depthRatio = clamp01(waterVal * 4.0); // waterVal / 0.25
                        int oceanBase;
                        if (depthRatio < 0.35) {
                            oceanBase = lerpRgb(RGB_OCEAN_SHALLOW, RGB_OCEAN_MID, depthRatio / 0.35);
                        } else {
                            oceanBase = lerpRgb(RGB_OCEAN_MID, RGB_OCEAN_DEEP, (depthRatio - 0.35) / 0.65);
                        }

                        // 2. Độ sâu 3D: Rãnh hố sâu & Gò cạn (Noise đa tầng 4x4)
                        double nDepth = rowDepth[x];
                        if (nDepth < 0.32) {
                            double trenchF = clamp01((0.32 - nDepth) / 0.16) * 0.50;
                            oceanBase = lerpRgb(oceanBase, RGB_SEABED_TRENCH, trenchF);
                        } else if (nDepth > 0.65) {
                            double shoalF = clamp01((nDepth - 0.65) * 5.0) * 0.40;
                            oceanBase = lerpRgb(oceanBase, RGB_SEABED_SHOAL, shoalF);
                        }

                        // 3. Mảng cát phù sa mịn (Silty Sand Patches 4x4)
                        double nSilt = rowSilt[x];
                        if (nSilt > 0.54) {
                            oceanBase = lerpRgb(oceanBase, RGB_SEABED_SILT, (nSilt - 0.54) * 0.55);
                        }

                        // 4. Vân gợn cát song song (Precomputed sin/cos & pre-scaled 0.18)
                        double ripWave = Math.sin(wx * COS_RIP_SCALE + wyRipTerm + rowRip[x]);
                        if (ripWave > 0.80) {
                            oceanBase = lerpRgb(oceanBase, RGB_SEABED_RIPPLE, 0.26);
                        }

                        // 5. Hạt cát vi mô bằng noise
                        double nGrain = worldNoise.noise2D(wx * 0.14, wy * 0.14);
                        if (nGrain > 0.62) {
                            oceanBase = lerpRgb(oceanBase, RGB_SEABED_GRAIN, 0.12);
                        }

                        pixelCol = oceanBase;
                    } else {
                        // --- VÙNG NƯỚC HỒ MÀN 1 ---
                        double depthRatio = clamp01(waterVal / 0.22);
                        if (depthRatio < 0.45) {
                            pixelCol = lerpRgb(colShallow, colMid, depthRatio / 0.45);
                        } else {
                            pixelCol = lerpRgb(colMid, colDeep, (depthRatio - 0.45) / 0.55);
                        }

                        // Gợn sóng & ánh sáng phản chiếu nhẹ
                        double waveNoise = Math.sin(wx * 0.12 + wyWaveTerm);
                        if (waveNoise > 0.78) {
                            pixelCol = lerpRgb(pixelCol, RGB_WATER_WAVE, 0.35);
                        }

                        // Cụm lá sen / bèo nhỏ ở rìa nước hồ (Stage 1)
                        if (waterVal < 0.045) {
                            double lilyNoise = worldNoise.noise2D(wx * 0.08, wy * 0.08);
                            if (lilyNoise > 0.68) {
                                int lModX = (int) Math.abs(wx) % 6;
                                if (lModX >= 1 && lModX <= 4 && lModY >= 1 && lModY <= 4 && (lModX != 4 || lModY != 2)) {
                                    pixelCol = RGB_WATER_LILY;
                                }
                            }
                        }
                    }
                } else {
                    // --- VÙNG ĐẤT LIỀN & BỜ BIỂN / BỜ HỒ ---
                    double shoreWobble = (waterVal >= -0.190) ? (worldNoise.noise2D(wx * 0.04, wy * 0.04) - 0.5) * 0.035 : 0.0;

                    if (stageId == 2) {
                        // MÀN 2: BỜ CÁT 5 TẦNG TỰ NHIÊN
                        double beachDist = -waterVal;
                        double sandNoise = worldNoise.noise2D(wx * 0.12, wy * 0.12);

                        if (beachDist < 0.030) {
                            double t = clamp01(beachDist / 0.030);
                            pixelCol = lerpRgb(RGB_BEACH_WET_MUD, RGB_BEACH_WET, t);
                        } else if (beachDist < 0.080 + shoreWobble * 0.5) {
                            double t = clamp01((beachDist - 0.030) / (0.050 + shoreWobble * 0.5));
                            pixelCol = lerpRgb(RGB_BEACH_WET, RGB_BEACH_DRY, t);
                        } else if (beachDist < 0.140 + shoreWobble) {
                            int baseSand = RGB_BEACH_DRY;
                            if (sandNoise > 0.58) {
                                baseSand = lerpRgb(baseSand, RGB_BEACH_GRAIN, (sandNoise - 0.58) * 0.55);
                            } else if (sandNoise < 0.38) {
                                baseSand = lerpRgb(baseSand, RGB_BEACH_DUNES, (0.38 - sandNoise) * 0.50);
                            }
                            double beachRipple = Math.sin(wx * 0.09 + wy * 0.15 + worldNoise.noise2D(wx * 0.03, wy * 0.03) * 6.0);
                            if (beachRipple > 0.82) {
                                baseSand = lerpRgb(baseSand, RGB_BEACH_GRAIN, 0.25);
                            }
                            pixelCol = baseSand;
                        } else if (beachDist < 0.190 + shoreWobble) {
                            double t = clamp01((beachDist - 0.140) / 0.050);
                            double grassNoise = worldNoise.noise2D(wx * 0.06, wy * 0.06) + (sandNoise - 0.5) * 0.35;
                            double blendFactor = clamp01(t + grassNoise * 0.40);
                            pixelCol = lerpRgb(RGB_BEACH_DRY, RGB_ISLAND_GRASS, blendFactor);
                        } else {
                            double nIsland = rowIsland[x];
                            pixelCol = lerpRgb(RGB_ISLAND_GRASS, RGB_ISLAND_INTERIOR, (nIsland - 0.5) * 0.30);
                        }
                    } else {
                        // MÀN 1: BỜ HỒ 3 LỚP & NỀN CỎ THẢO NGUYÊN
                        if (waterVal >= -0.032) {
                            pixelCol = RGB_SHORE_WET_MUD;
                        } else if (waterVal >= -0.095 + shoreWobble) {
                            pixelCol = RGB_SHORE_SAND;
                        } else if (waterVal >= -0.155 + shoreWobble) {
                            boolean dither = ((x + y) & 1) == 0;
                            pixelCol = dither ? RGB_SHORE_SAND : RGB_SHORE_GRASS_TRANS;
                        } else {
                            double nMacro = rowMacro[x];
                            double nDetail = rowDetail[x];

                            int grassBase;
                            if (nMacro > 0.58) {
                                grassBase = lerpRgb(RGB_GRASS_MID, RGB_GRASS_WARM, clamp01((nMacro - 0.58) / 0.22));
                            } else if (nMacro < 0.42) {
                                grassBase = lerpRgb(RGB_GRASS_MOSS, RGB_GRASS_MID, clamp01(nMacro / 0.42));
                            } else {
                                grassBase = lerpRgb(RGB_GRASS_MID, RGB_GRASS_VIBRANT, clamp01((nMacro - 0.42) / 0.16));
                            }

                            int dirtBase = lerpRgb(RGB_DIRT_WARM, RGB_DIRT_DRY, nDetail);

                            double dirtBlend = clamp01((nDetail - 0.52) / 0.18);
                            if (dirtBlend > 0.0) {
                                boolean dither = ((x + y) & 1) == 0;
                                pixelCol = dither ? lerpRgb(grassBase, dirtBase, dirtBlend) : grassBase;
                            } else {
                                double nColor = fbm(worldNoise, wx * NOISE_SCALE_COLOR, wy * NOISE_SCALE_COLOR, NOISE_OCTAVES);
                                pixelCol = lerpRgb(grassBase, RGB_GRASS_VIBRANT, (nColor - 0.5) * 0.25);
                            }
                        }
                    }
                }

                pixels[rowOffset + x] = pixelCol;
            }
        }

        // ==========================================
        // 2.5 TIỀN TÍNH TOÁN DẢI Ô NƯỚC (WATER RUNS) & BIÊN BỜ NƯỚC (SHORE SEGMENTS)
        // Dùng kích thước TILE_SIZE = 8 để gợn sóng và bọt bám khít đường cong
        // ==========================================
        boolean[][] isWaterGrid = new boolean[tilesPerChunk][tilesPerChunk];
        for (int tx = 0; tx < tilesPerChunk; tx++) {
            int colCurr = (tx + pad) * padGridSize;
            for (int ty = 0; ty < tilesPerChunk; ty++) {
                isWaterGrid[tx][ty] = smoothWater[colCurr + ty + pad] > 0.0;
            }
        }

        boolean allWater = true;
        for (int tx = 0; tx < tilesPerChunk && allWater; tx++) {
            for (int ty = 0; ty < tilesPerChunk && allWater; ty++) {
                if (!isWaterGrid[tx][ty]) {
                    allWater = false;
                }
            }
        }

        if (allWater) {
            waterRuns.add(new WaterTileRun(0, 0, size, size));
        } else {
            for (int ty = 0; ty < tilesPerChunk; ty++) {
                int startX = -1;
                for (int tx = 0; tx < tilesPerChunk; tx++) {
                    if (isWaterGrid[tx][ty]) {
                        if (startX == -1) startX = tx;
                    } else {
                        if (startX != -1) {
                            waterRuns.add(new WaterTileRun(startX * TILE_SIZE, ty * TILE_SIZE, (tx - startX) * TILE_SIZE, TILE_SIZE));
                            startX = -1;
                        }
                    }
                }
                if (startX != -1) {
                    waterRuns.add(new WaterTileRun(startX * TILE_SIZE, ty * TILE_SIZE, (tilesPerChunk - startX) * TILE_SIZE, TILE_SIZE));
                }
            }

            // Shore segments phục vụ Shoreline Foam động (Chỉ sinh khi có đảo / đất liền)
            for (int ty = 0; ty < tilesPerChunk; ty++) {
                for (int tx = 0; tx < tilesPerChunk; tx++) {
                    if (!isWaterGrid[tx][ty]) continue;
                    int wx = (int) (worldX + tx * TILE_SIZE);
                    int wy = (int) (worldY + ty * TILE_SIZE);
                    double phase = (wx * 0.05 + wy * 0.08);

                    // Giáp đất phía Bắc
                    if (ty > 0 && !isWaterGrid[tx][ty - 1]) {
                        shoreSegments.add(new ShoreSegment(tx * TILE_SIZE, ty * TILE_SIZE, (tx + 1) * TILE_SIZE, ty * TILE_SIZE, 0, 1, phase));
                    }
                    // Giáp đất phía Nam
                    if (ty < tilesPerChunk - 1 && !isWaterGrid[tx][ty + 1]) {
                        shoreSegments.add(new ShoreSegment(tx * TILE_SIZE, (ty + 1) * TILE_SIZE, (tx + 1) * TILE_SIZE, (ty + 1) * TILE_SIZE, 0, -1, phase));
                    }
                    // Giáp đất phía Tây
                    if (tx > 0 && !isWaterGrid[tx - 1][ty]) {
                        shoreSegments.add(new ShoreSegment(tx * TILE_SIZE, ty * TILE_SIZE, tx * TILE_SIZE, (ty + 1) * TILE_SIZE, 1, 0, phase));
                    }
                    // Giáp đất phía Đông
                    if (tx < tilesPerChunk - 1 && !isWaterGrid[tx + 1][ty]) {
                        shoreSegments.add(new ShoreSegment((tx + 1) * TILE_SIZE, ty * TILE_SIZE, (tx + 1) * TILE_SIZE, (ty + 1) * TILE_SIZE, -1, 0, phase));
                    }
                }
            }
        }

        // ==========================================
        // 3. DECALS MẬT ĐỘ THẤP (Cỏ con, hoa nhỏ, sỏi, lá rụng)
        // ==========================================
        Graphics2D g2d = cachedImage.createGraphics();
        int decalCount = DECAL_MIN_COUNT + (int) (rng.nextFloat() * (DECAL_MAX_COUNT - DECAL_MIN_COUNT + 1));
        for (int i = 0; i < decalCount; i++) {
            double dx = 12 + rng.nextFloat() * (size - 24);
            double dy = 12 + rng.nextFloat() * (size - 24);
            double dwx = worldX + dx;
            double dwy = worldY + dy;

            if (isWaterAt(dwx, dwy, worldNoise, stageId)) {
                if (stageId == 2) {
                    // Decals Đáy Biển Màn 2: Sao biển, Vỏ sò quạt, Sỏi đáy biển
                    int idxX = (int) Math.round(dx);
                    int idxY = (int) Math.round(dy);
                    double dType = rng.nextFloat();
                    if (dType < 0.28) {
                        drawStarfishDecal(g2d, idxX, idxY, rng.nextFloat() < 0.5);
                    } else if (dType < 0.62) {
                        drawSeashellDecal(g2d, idxX, idxY, rng.nextFloat() < 0.5);
                    } else {
                        drawSeabedPebbleDecal(g2d, idxX, idxY, rng.nextFloat());
                    }
                }
                continue;
            }

            int idxX = (int) Math.round(dx);
            int idxY = (int) Math.round(dy);
            double dType = rng.nextFloat();

            if (dType < 0.32) {
                // 1. Cỏ con (2-3 sợi cỏ nhỏ xanh non)
                g2d.setColor(new Color(94, 186, 114));
                g2d.fillRect(idxX, idxY, 1, 3);
                g2d.fillRect(idxX + 1, idxY - 1, 1, 4);
                g2d.fillRect(idxX + 2, idxY + 1, 1, 2);
            } else if (dType < 0.56) {
                // 2. Hoa nhỏ dại (3px pastel vàng nhạt hoặc trắng ngà)
                Color flowerCol = rng.nextFloat() < 0.5 ? new Color(254, 240, 138) : new Color(241, 245, 249);
                g2d.setColor(flowerCol);
                g2d.fillRect(idxX, idxY - 1, 1, 3);
                g2d.fillRect(idxX - 1, idxY, 3, 1);
                g2d.setColor(new Color(245, 158, 11));
                g2d.fillRect(idxX, idxY, 1, 1);
            } else if (dType < 0.78) {
                // 3. Sỏi dẹt (1-2 viên nhỏ xám đá)
                g2d.setColor(new Color(71, 85, 105));
                g2d.fillRect(idxX, idxY, 3, 2);
                g2d.setColor(new Color(148, 163, 184));
                g2d.fillRect(idxX, idxY, 1, 1);
            } else {
                // 4. Lá rụng (chấm vàng hổ phách)
                g2d.setColor(new Color(180, 130, 60));
                g2d.fillRect(idxX, idxY, 2, 2);
                g2d.setColor(new Color(146, 64, 14));
                g2d.fillRect(idxX + 1, idxY + 1, 1, 1);
            }
        }

        g2d.dispose();

        // ==========================================
        // 4. PHÂN BỐ VẬT THỂ THEO CỤM BẰNG NOISE (CLUSTERING)
        // (Rừng cây rậm rạp, Bãi đá tảng, Đồng cỏ trống trải)
        // ==========================================
        int gridStep = 44;
        int propCells = size / gridStep;
        for (int gx = 0; gx < propCells; gx++) {
            for (int gy = 0; gy < propCells; gy++) {
                double px = worldX + gx * gridStep + (rng.nextFloat() * 26);
                double py = worldY + gy * gridStep + (rng.nextFloat() * 26);

                boolean inWaterTile = isWaterAt(px, py, worldNoise, stageId);

                if (stageId == 2) {
                    // MÀN 2: ĐẠI DƯƠNG
                    double roll = rng.nextFloat();
                    double clusterNoise = worldNoise.noise2D(px * 0.008, py * 0.008);

                    if (inWaterTile) {
                        boolean hasNearby = false;
                        for (WorldProp ep : props) {
                            if (Math.hypot(ep.getX() - px, ep.getY() - py) < 100.0) {
                                hasNearby = true;
                                break;
                            }
                        }
                        if (!hasNearby) {
                            // Chỉ giữ đá ngầm nhỏ điểm xuyết trong luồng nước trống (san hô và rong được sinh theo rạn lớn)
                            if (roll > 0.975 && clusterNoise < 0.52) {
                                int rockVar = (int) (rng.nextFloat() * 3);
                                if (rockVar > 2) rockVar = 2;
                                props.add(new WorldProp(WorldProp.Type.ROCK, px, py, Config.World.PROP_ROCK_RADIUS, 54, 46, rockVar));
                            }
                        }
                    } else {
                        if (roll < 0.032 && clusterNoise > 0.45) {
                            props.add(new WorldProp(WorldProp.Type.TREE, px, py, 30.0, 76, 102, 0));
                        }
                    }
                } else {
                    // MÀN 1: THẢO NGUYÊN (Chỉ sinh trên đất liền)
                    if (inWaterTile) continue;

                    // Phân loại khu vực sinh thái lớn theo Macro Cluster Noise
                    double zoneNoise = worldNoise.noise2D(px * PROP_CLUSTER_SCALE, py * PROP_CLUSTER_SCALE);
                    double roll = rng.nextFloat();

                    if (zoneNoise > PROP_CLUSTER_FOREST_THRESH) {
                        // --- KHU VỰC 1: RỪNG CÂY RẬM RẠP (Dense Forest Grove) ---
                        // Ưu tiên sinh cây cối nhiều kích cỡ (Cây lớn tạo tán, cây vừa, cây nhỏ)
                        if (roll < 0.28) {
                            if (!hasNearbyProp(px, py, 68.0)) {
                                double treeTier = rng.nextFloat();
                                int treeW, treeH;
                                double treeR;
                                if (treeTier < 0.22) {
                                    treeW = 96; treeH = 128; treeR = 38.0;
                                } else if (treeTier < 0.65) {
                                    treeW = 68; treeH = 92; treeR = 28.0;
                                } else {
                                    treeW = 52; treeH = 70; treeR = 22.0;
                                }
                                props.add(new WorldProp(WorldProp.Type.TREE, px, py, treeR, treeW, treeH, 0));
                            }
                        }
                    } else if (zoneNoise < PROP_CLUSTER_ROCK_THRESH) {
                        // --- KHU VỰC 2: BÃI ĐÁ TẢNG & VÁCH NÚI (Boulder Field) ---
                        // Ưu tiên đá tảng bất định hình 4 biến thể, tường và bẫy
                        if (roll < 0.32) {
                            if (!hasNearbyProp(px, py, 48.0)) {
                                int rVar = (int) (rng.nextFloat() * 4);
                                int rW = 46 + (rVar % 2) * 8;
                                int rH = 36 + (rVar / 2) * 6;
                                props.add(new WorldProp(WorldProp.Type.ROCK, px, py, Config.World.PROP_ROCK_RADIUS, rW, rH, rVar));

                                // Mọc thành bãi đá cụm (2 tảng đá gần nhau)
                                if (rng.nextFloat() < 0.40 && !hasNearbyProp(px + 24, py + 8, 28.0)) {
                                    int rVar2 = (rVar + 1) % 4;
                                    props.add(new WorldProp(WorldProp.Type.ROCK, px + 24, py + 8, Config.World.PROP_ROCK_RADIUS, rW - 6, rH - 4, rVar2));
                                }
                            }
                        } else if (roll < 0.36) {
                            if (!hasNearbyProp(px, py, 68.0)) {
                                props.add(new WorldProp(WorldProp.Type.WALL, px, py, Config.World.PROP_WALL_RADIUS, 54, 38));
                            }
                        } else if (roll < 0.40) {
                            if (!hasNearbyProp(px, py, 52.0)) {
                                props.add(new WorldProp(WorldProp.Type.TRAP, px, py, Config.World.PROP_TRAP_RADIUS, 32, 30));
                            }
                        }
                    } else {
                        // --- KHU VỰC 3: ĐỒNG TRỐNG / THẢO NGUYÊN MỞ (Open Clearing) ---
                        // Không gian mở thoáng đãng cho rắn lướt dash, săn mồi, tỷ lệ vật cản thưa
                        if (roll < 0.025) {
                            if (!hasNearbyProp(px, py, 90.0)) {
                                props.add(new WorldProp(WorldProp.Type.CAVE, px, py, Config.World.PROP_CAVE_RADIUS, 96, 76));
                            }
                        } else if (roll < 0.06) {
                            if (!hasNearbyProp(px, py, 75.0)) {
                                // Cây cô đơn giữa đồng
                                props.add(new WorldProp(WorldProp.Type.TREE, px, py, 26.0, 64, 88, 0));
                            }
                        } else if (roll < 0.09) {
                            if (!hasNearbyProp(px, py, 55.0)) {
                                props.add(new WorldProp(WorldProp.Type.TRAP, px, py, Config.World.PROP_TRAP_RADIUS, 32, 30));
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 5. SINH CỤM CỎ VÀ HOA MAP 1 (KHU CỎ XUM XUÊ & CỎ LẺ)
        // ==========================================
        generateGrassPatches(worldNoise);
        generateStandaloneVegetation(worldNoise);

        // ==========================================
        // 6. SINH CỤM SAN HÔ MAP 2 (KHU SAN HÔ XUM XUÊ & SAN HÔ LẺ)
        // ==========================================
        generateCoralReefs(worldNoise);
        generateStandaloneCorals(worldNoise);
    }

    /**
     * Kiểm tra xem elip cụm cỏ tại (cx, cy) có va chạm với các vật cản (hang động, đá tảng, tường) hay không.
     * Khoảng cách tính tới biên elip (không phải tới tâm).
     * Cây thường (TREE) được phép nằm trong cụm cỏ và y-sort chuẩn.
     * Hang động (CAVE) luôn phải cách mép cụm cỏ >= 40px để lối vào hang không bị che khuất.
     */
    private WorldProp findCollidingObstacle(double cx, double cy, double rx, double ry, double rot) {
        double cosR = Math.cos(-rot);
        double sinR = Math.sin(-rot);

        for (WorldProp p : props) {
            // Cây thường được phép nằm trong cụm cỏ (y-sort chuẩn)
            if (p.getType() == WorldProp.Type.TREE) {
                continue;
            }

            // Hang động bắt buộc cách mép cụm >= 40px; đá tảng / tường / bẫy có khoảng đệm prop.getRadius() + 8px
            double extra = (p.getType() == WorldProp.Type.CAVE)
                    ? (p.getRadius() + CAVE_CLUSTER_CLEARANCE)
                    : (p.getRadius() + 8.0);

            double dx = p.getX() - cx;
            double dy = p.getY() - cy;

            // Xoay về hệ tọa độ chuẩn của elip
            double nx = (dx * cosR - dy * sinR) / (rx + extra);
            double ny = (dx * sinR + dy * cosR) / (ry + extra);

            if (nx * nx + ny * ny <= 1.0) {
                return p;
            }
        }
        return null;
    }

    /**
     * Dời tâm cụm cỏ tối đa 150px (tối đa 3 lần thử) nếu va chạm với vật cản.
     */
    private Point2D.Double resolveObstacleCollision(double candX, double candY, double rx, double ry, Random rng, FastNoise worldNoise) {
        double currX = candX;
        double currY = candY;
        WorldProp obstacle = findCollidingObstacle(currX, currY, rx, ry, 0.0);

        int shifts = 0;
        while (obstacle != null && shifts < CLUSTER_MAX_SHIFT_ATTEMPTS) {
            shifts++;
            double vx = currX - obstacle.getX();
            double vy = currY - obstacle.getY();
            double vlen = Math.hypot(vx, vy);
            if (vlen < 1e-4) {
                double a = rng.nextDouble() * Math.PI * 2.0;
                vx = Math.cos(a);
                vy = Math.sin(a);
                vlen = 1.0;
            }

            double shiftDist = (CLUSTER_MAX_SHIFT / CLUSTER_MAX_SHIFT_ATTEMPTS) * shifts;
            double testX = candX + (vx / vlen) * shiftDist;
            double testY = candY + (vy / vlen) * shiftDist;

            // Tâm sau khi dời phải trong khoảng dịch chuyển cho phép và nằm trên cạn
            if (Math.hypot(testX - candX, testY - candY) > CLUSTER_MAX_SHIFT) {
                return null;
            }
            if (isWaterAt(testX, testY, worldNoise, stageId) || getWaterPotential(testX, testY, worldNoise, stageId) >= -0.06) {
                return null;
            }

            currX = testX;
            currY = testY;
            obstacle = findCollidingObstacle(currX, currY, rx, ry, 0.0);
        }

        if (obstacle != null) {
            return null; // Vẫn va chạm sau tối đa 3 lần dời tâm -> bỏ cụm
        }
        return new Point2D.Double(currX, currY);
    }

    /**
     * Kiểm tra xem điểm (x, y) của ô mịn có giao thoa với bất kỳ Cụm Lớn nào
     * (thuộc các ô lưới thô lân cận) cộng thêm đệm an toàn 100px hay không.
     */
    private boolean intersectsLargeCluster(double x, double y, FastNoise worldNoise) {
        int baseCgx = (int) Math.floor(x / COARSE_CELL_SIZE);
        int baseCgy = (int) Math.floor(y / COARSE_CELL_SIZE);

        for (int dcx = -1; dcx <= 1; dcx++) {
            for (int dcy = -1; dcy <= 1; dcy++) {
                int cgx = baseCgx + dcx;
                int cgy = baseCgy + dcy;
                long cSeed = ((long) cgx * 82736111L) ^ ((long) cgy * 39827113L) ^ (long) Config.World.SEED;
                Random cRng = new Random(cSeed);

                double lx = cgx * COARSE_CELL_SIZE + (COARSE_CELL_SIZE / 2.0) + (cRng.nextFloat() - 0.5) * (COARSE_JITTER_MAX * 2.0);
                double ly = cgy * COARSE_CELL_SIZE + (COARSE_CELL_SIZE / 2.0) + (cRng.nextFloat() - 0.5) * (COARSE_JITTER_MAX * 2.0);

                if (isWaterAt(lx, ly, worldNoise, 1) || getWaterPotential(lx, ly, worldNoise, 1) >= -0.06) continue;
                if (cRng.nextFloat() > CLUSTER_LAND_CHANCE) continue;

                double lw = GrassPatch.CLUSTER_LARGE_MIN_WIDTH + cRng.nextFloat() * (GrassPatch.CLUSTER_LARGE_MAX_WIDTH - GrassPatch.CLUSTER_LARGE_MIN_WIDTH);
                double lh = lw * (0.64 + cRng.nextFloat() * 0.14);
                double bufferRx = (lw / 2.0) + MIN_CLUSTER_GAP;
                double bufferRy = (lh / 2.0) + MIN_CLUSTER_GAP;

                double dx = x - lx;
                double dy = y - ly;
                if ((dx * dx) / (bufferRx * bufferRx) + (dy * dy) / (bufferRy * bufferRy) <= 1.0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Sinh các cụm cỏ và hoa tự nhiên (Grass & Flower Clusters) trên Map 1.
     * Hệ thống Lưới 2 Tầng (Two-Tier Grid):
     * 1. Lưới Thô (Coarse Grid 1600px, Jitter 120px) cho Cụm Lớn (560-800px, tỉ lệ 15%).
     * 2. Lưới Mịn (Fine Grid 800px, Jitter 60px) cho Cụm Nhỏ (260-380px, 40%) & Cụm Vừa (380-560px, 45%).
     * Đảm bảo:
     * - Độ phủ đất liền >= 88%, không để vùng trống rộng hơn ~1200px không có cụm cỏ.
     * - Ô mịn giao thoa với elip cụm lớn (+ 100px đệm) sẽ tự động bị bỏ qua.
     * - CELL - 2*JITTER >= MAX_WIDTH + 100 thỏa mãn cho cả 2 lưới.
     */
    private void generateGrassPatches(FastNoise worldNoise) {
        if (stageId != 1) return;

        // 1. SINH CỤM LỚN THEO LƯỚI THÔ (COARSE GRID 1600px, JITTER 120px)
        int minCgx = (int) Math.floor(worldX / COARSE_CELL_SIZE);
        int maxCgx = (int) Math.floor((worldX + size) / COARSE_CELL_SIZE);
        int minCgy = (int) Math.floor(worldY / COARSE_CELL_SIZE);
        int maxCgy = (int) Math.floor((worldY + size) / COARSE_CELL_SIZE);

        for (int cgx = minCgx; cgx <= maxCgx; cgx++) {
            for (int cgy = minCgy; cgy <= maxCgy; cgy++) {
                long cSeed = ((long) cgx * 82736111L) ^ ((long) cgy * 39827113L) ^ (long) Config.World.SEED;
                Random cRng = new Random(cSeed);

                double candX = cgx * COARSE_CELL_SIZE + (COARSE_CELL_SIZE / 2.0) + (cRng.nextFloat() - 0.5) * (COARSE_JITTER_MAX * 2.0);
                double candY = cgy * COARSE_CELL_SIZE + (COARSE_CELL_SIZE / 2.0) + (cRng.nextFloat() - 0.5) * (COARSE_JITTER_MAX * 2.0);

                if (candX < worldX || candX >= worldX + size || candY < worldY || candY >= worldY + size) {
                    continue;
                }

                if (isWaterAt(candX, candY, worldNoise, stageId)) continue;
                if (getWaterPotential(candX, candY, worldNoise, stageId) >= -0.06) continue;

                if (cRng.nextFloat() > CLUSTER_LAND_CHANCE) continue;

                // Cỡ cụm lớn: 560 - 800px
                double w = GrassPatch.CLUSTER_LARGE_MIN_WIDTH + cRng.nextFloat() * (GrassPatch.CLUSTER_LARGE_MAX_WIDTH - GrassPatch.CLUSTER_LARGE_MIN_WIDTH);
                double h = w * (0.64 + cRng.nextFloat() * 0.14);
                double rx = w / 2.0;
                double ry = h / 2.0;

                int pIdx = Math.abs((int) Math.round(worldNoise.noise2D(candX * 0.002, candY * 0.002) * 4.0)) % 4;
                GrassPatch.Pattern pat = GrassPatch.Pattern.values()[pIdx];
                double pScale = 1.1 + cRng.nextFloat() * 0.25;
                boolean pFlip = cRng.nextBoolean();

                Point2D.Double placed = resolveObstacleCollision(candX, candY, rx, ry, cRng, worldNoise);
                if (placed != null) {
                    grassPatches.add(new GrassPatch(placed.x, placed.y, rx, ry, pat, pScale, pFlip, cRng));
                }
            }
        }

        // 2. SINH CỤM NHỎ VÀ VỪA THEO LƯỚI MỊN (FINE GRID 800px, JITTER 60px)
        int minFgx = (int) Math.floor(worldX / FINE_CELL_SIZE);
        int maxFgx = (int) Math.floor((worldX + size) / FINE_CELL_SIZE);
        int minFgy = (int) Math.floor(worldY / FINE_CELL_SIZE);
        int maxFgy = (int) Math.floor((worldY + size) / FINE_CELL_SIZE);

        for (int fgx = minFgx; fgx <= maxFgx; fgx++) {
            for (int fgy = minFgy; fgy <= maxFgy; fgy++) {
                long fSeed = ((long) fgx * 73856093L) ^ ((long) fgy * 19349663L) ^ (long) Config.World.SEED;
                Random fRng = new Random(fSeed);

                double candX = fgx * FINE_CELL_SIZE + (FINE_CELL_SIZE / 2.0) + (fRng.nextFloat() - 0.5) * (FINE_JITTER_MAX * 2.0);
                double candY = fgy * FINE_CELL_SIZE + (FINE_CELL_SIZE / 2.0) + (fRng.nextFloat() - 0.5) * (FINE_JITTER_MAX * 2.0);

                if (candX < worldX || candX >= worldX + size || candY < worldY || candY >= worldY + size) {
                    continue;
                }

                if (isWaterAt(candX, candY, worldNoise, stageId)) continue;
                if (getWaterPotential(candX, candY, worldNoise, stageId) >= -0.06) continue;

                // Bỏ ô mịn giao thoa với elip của cụm lớn (cộng đệm 100px)
                if (intersectsLargeCluster(candX, candY, worldNoise)) {
                    continue;
                }

                if (fRng.nextFloat() > CLUSTER_LAND_CHANCE) continue;

                // Tỉ lệ: Nhỏ 40%, Vừa 45%
                boolean isSmall = fRng.nextFloat() < (GrassPatch.RATIO_SMALL_CLUSTER / (GrassPatch.RATIO_SMALL_CLUSTER + GrassPatch.RATIO_MEDIUM_CLUSTER));
                double w, h;
                if (isSmall) {
                    w = GrassPatch.CLUSTER_SMALL_MIN_WIDTH + fRng.nextFloat() * (GrassPatch.CLUSTER_SMALL_MAX_WIDTH - GrassPatch.CLUSTER_SMALL_MIN_WIDTH);
                    h = w * (0.65 + fRng.nextFloat() * 0.15);
                } else {
                    w = GrassPatch.CLUSTER_MEDIUM_MIN_WIDTH + fRng.nextFloat() * (GrassPatch.CLUSTER_MEDIUM_MAX_WIDTH - GrassPatch.CLUSTER_MEDIUM_MIN_WIDTH);
                    h = w * (0.64 + fRng.nextFloat() * 0.14);
                }
                double rx = w / 2.0;
                double ry = h / 2.0;

                int pIdx = Math.abs((int) Math.round(worldNoise.noise2D(candX * 0.0035, candY * 0.0035) * 4.0)) % 4;
                GrassPatch.Pattern pat = GrassPatch.Pattern.values()[pIdx];
                double pScale = isSmall ? (0.85 + fRng.nextFloat() * 0.20) : (0.95 + fRng.nextFloat() * 0.25);
                boolean pFlip = fRng.nextBoolean();

                Point2D.Double placed = resolveObstacleCollision(candX, candY, rx, ry, fRng, worldNoise);
                if (placed != null) {
                    grassPatches.add(new GrassPatch(placed.x, placed.y, rx, ry, pat, pScale, pFlip, fRng));
                }
            }
        }
    }

    /**
     * Sinh cỏ lẻ và hoa lẻ ngoài cụm (Standalone Decorative Vegetation).
     * Mật độ khoảng 1 cây mỗi 40.000px², không tạo vùng ẩn nấp, chỉ trang trí nền tự nhiên.
     */
    private void generateStandaloneVegetation(FastNoise worldNoise) {
        if (stageId != 1) return;
        standalonePlants.clear();

        int targetCount = (int) Math.round(((double) size * size) / STANDALONE_VEG_AREA);
        Random rng = new Random(seed ^ 98452171L);

        int attempts = 0;
        int maxAttempts = targetCount * 5;
        while (standalonePlants.size() < targetCount && attempts < maxAttempts) {
            attempts++;
            double wx = worldX + 16.0 + rng.nextFloat() * (size - 32.0);
            double wy = worldY + 16.0 + rng.nextFloat() * (size - 32.0);

            if (isWaterAt(wx, wy, worldNoise, stageId)) continue;
            if (getWaterPotential(wx, wy, worldNoise, stageId) >= -0.06) continue;

            // Đảm bảo nằm ngoài các cụm cỏ
            boolean insideCluster = false;
            for (GrassPatch gp : grassPatches) {
                if (gp.contains(wx, wy)) {
                    insideCluster = true;
                    break;
                }
            }
            if (insideCluster) continue;

            // 60% cỏ đơn lẻ (grass_1..3), 40% hoa đơn lẻ (flower_1..6)
            int spriteId;
            int plantW, plantH;
            if (rng.nextFloat() < 0.60) {
                spriteId = VegetationManager.SPRITE_GRASS_1 + rng.nextInt(3);
                plantH = 20 + rng.nextInt(10);
                plantW = (int) Math.round(plantH * 0.95);
            } else {
                spriteId = VegetationManager.SPRITE_FLOWER_1 + rng.nextInt(6);
                plantH = 16 + rng.nextInt(8);
                plantW = (int) Math.round(plantH * 0.90);
            }

            boolean isFlipped = rng.nextBoolean();
            double swayPhase = wx * 0.05 + wy * 0.05 + standalonePlants.size() * 0.9;
            double footY = wy + plantH * 0.35;

            standalonePlants.add(new GrassPatch.ClusterPlant(0, 0, wx, wy, footY, spriteId, plantW, plantH, isFlipped, swayPhase, true));
        }
    }

    /**
     * Kiểm tra xem elip cụm san hô có va chạm với các vật cản (hang ngầm, đá tảng, tường, đảo đá) hay không.
     * Hang ngầm (CAVE) bắt buộc cách mép cụm san hô >= 40px để cửa hang thông thoáng.
     */
    private WorldProp findCollidingReefObstacle(double cx, double cy, double rx, double ry, double rot) {
        double cosR = Math.cos(-rot);
        double sinR = Math.sin(-rot);

        for (WorldProp p : props) {
            double extra = (p.getType() == WorldProp.Type.CAVE)
                    ? (p.getRadius() + CAVE_REEF_CLEARANCE)
                    : (p.getRadius() + 10.0);

            double dx = p.getX() - cx;
            double dy = p.getY() - cy;

            double nx = (dx * cosR - dy * sinR) / (rx + extra);
            double ny = (dx * sinR + dy * cosR) / (ry + extra);

            if (nx * nx + ny * ny <= 1.0) {
                return p;
            }
        }
        return null;
    }

    /**
     * Dời tâm cụm san hô tối đa 150px (tối đa 3 lần thử) nếu va chạm với vật cản.
     */
    private Point2D.Double resolveReefObstacleCollision(double candX, double candY, double rx, double ry, Random rng, FastNoise worldNoise) {
        double currX = candX;
        double currY = candY;
        WorldProp obstacle = findCollidingReefObstacle(currX, currY, rx, ry, 0.0);

        int shifts = 0;
        while (obstacle != null && shifts < REEF_MAX_SHIFT_ATTEMPTS) {
            shifts++;
            double vx = currX - obstacle.getX();
            double vy = currY - obstacle.getY();
            double vlen = Math.hypot(vx, vy);
            if (vlen < 1e-4) {
                double a = rng.nextDouble() * Math.PI * 2.0;
                vx = Math.cos(a);
                vy = Math.sin(a);
                vlen = 1.0;
            }

            double shiftDist = (REEF_MAX_SHIFT / REEF_MAX_SHIFT_ATTEMPTS) * shifts;
            double testX = candX + (vx / vlen) * shiftDist;
            double testY = candY + (vy / vlen) * shiftDist;

            // Tâm sau khi dời phải trong khoảng dịch chuyển cho phép và nằm dưới biển
            if (Math.hypot(testX - candX, testY - candY) > REEF_MAX_SHIFT) {
                return null;
            }
            if (!isWaterAt(testX, testY, worldNoise, stageId) || getWaterPotential(testX, testY, worldNoise, stageId) <= 0.05) {
                return null;
            }

            currX = testX;
            currY = testY;
            obstacle = findCollidingReefObstacle(currX, currY, rx, ry, 0.0);
        }

        if (obstacle != null) {
            return null; // Vẫn va chạm sau tối đa 3 lần dời tâm -> bỏ cụm
        }
        return new Point2D.Double(currX, currY);
    }

    /**
     * Kiểm tra xem điểm (x, y) của ô mịn có giao thoa với bất kỳ Cụm Lớn nào
     * (thuộc các ô lưới thô lân cận) cộng thêm đệm an toàn 120px hay không.
     */
    private boolean intersectsLargeReef(double x, double y, FastNoise worldNoise) {
        int baseCgx = (int) Math.floor(x / REEF_COARSE_CELL_SIZE);
        int baseCgy = (int) Math.floor(y / REEF_COARSE_CELL_SIZE);

        for (int dcx = -1; dcx <= 1; dcx++) {
            for (int dcy = -1; dcy <= 1; dcy++) {
                int cgx = baseCgx + dcx;
                int cgy = baseCgy + dcy;
                long cSeed = ((long) cgx * 61728391L) ^ ((long) cgy * 48291047L) ^ (long) Config.World.SEED;
                Random cRng = new Random(cSeed);

                double lx = cgx * REEF_COARSE_CELL_SIZE + (REEF_COARSE_CELL_SIZE / 2.0) + (cRng.nextFloat() - 0.5) * (REEF_COARSE_JITTER_MAX * 2.0);
                double ly = cgy * REEF_COARSE_CELL_SIZE + (REEF_COARSE_CELL_SIZE / 2.0) + (cRng.nextFloat() - 0.5) * (REEF_COARSE_JITTER_MAX * 2.0);

                if (!isWaterAt(lx, ly, worldNoise, 2) || getWaterPotential(lx, ly, worldNoise, 2) <= 0.15) continue;
                if (cRng.nextFloat() > REEF_DEEP_WATER_CHANCE) continue;

                double lw = CoralReef.CLUSTER_LARGE_MIN_WIDTH + cRng.nextFloat() * (CoralReef.CLUSTER_LARGE_MAX_WIDTH - CoralReef.CLUSTER_LARGE_MIN_WIDTH);
                double lh = lw * (0.50 + cRng.nextFloat() * 0.08);
                double bufferRx = (lw / 2.0) + MIN_REEF_GAP;
                double bufferRy = (lh / 2.0) + MIN_REEF_GAP;

                double dx = x - lx;
                double dy = y - ly;
                if ((dx * dx) / (bufferRx * bufferRx) + (dy * dy) / (bufferRy * bufferRy) <= 1.0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Sinh các cụm san hô tự nhiên (Coral Reef Clusters) trên Map 2.
     * Hệ thống Lưới 2 Cấp (Two-Tier Grid):
     * 1. Lưới Thô (Coarse Grid 1800px, Jitter 140px) cho Cụm Lớn Biển Sâu (700-900px, hiếm 10-15%).
     * 2. Lưới Mịn (Fine Grid 900px, Jitter 70px) cho Cụm Nhỏ (160-260px) & Cụm Vừa (300-520px).
     * Đảm bảo:
     * - Cụm vừa và lớn chỉ sinh ở nước sâu (potential > 0.15).
     * - Cụm nhỏ cho phép từ nước vừa (potential > 0.05).
     * - Ô mịn giao thoa với elip cụm lớn (+ 120px đệm) sẽ tự động bị bỏ qua.
     * - CELL - 2*JITTER >= MAX_WIDTH + 120 thỏa mãn cho cả 2 lưới.
     */
    private void generateCoralReefs(FastNoise worldNoise) {
        if (stageId != 2) return;

        // 1. SINH CỤM LỚN THEO LƯỚI THÔ (COARSE GRID 1800px, JITTER 140px)
        int minCgx = (int) Math.floor(worldX / REEF_COARSE_CELL_SIZE);
        int maxCgx = (int) Math.floor((worldX + size) / REEF_COARSE_CELL_SIZE);
        int minCgy = (int) Math.floor(worldY / REEF_COARSE_CELL_SIZE);
        int maxCgy = (int) Math.floor((worldY + size) / REEF_COARSE_CELL_SIZE);

        for (int cgx = minCgx; cgx <= maxCgx; cgx++) {
            for (int cgy = minCgy; cgy <= maxCgy; cgy++) {
                long cSeed = ((long) cgx * 61728391L) ^ ((long) cgy * 48291047L) ^ (long) Config.World.SEED;
                Random cRng = new Random(cSeed);

                double candX = cgx * REEF_COARSE_CELL_SIZE + (REEF_COARSE_CELL_SIZE / 2.0) + (cRng.nextFloat() - 0.5) * (REEF_COARSE_JITTER_MAX * 2.0);
                double candY = cgy * REEF_COARSE_CELL_SIZE + (REEF_COARSE_CELL_SIZE / 2.0) + (cRng.nextFloat() - 0.5) * (REEF_COARSE_JITTER_MAX * 2.0);

                if (candX < worldX || candX >= worldX + size || candY < worldY || candY >= worldY + size) {
                    continue;
                }

                // Cụm lớn chỉ sinh trong lòng biển sâu (> 0.15)
                if (!isWaterAt(candX, candY, worldNoise, stageId)) continue;
                if (getWaterPotential(candX, candY, worldNoise, stageId) <= 0.15) continue;

                if (cRng.nextFloat() > REEF_DEEP_WATER_CHANCE) continue;

                double w = CoralReef.CLUSTER_LARGE_MIN_WIDTH + cRng.nextFloat() * (CoralReef.CLUSTER_LARGE_MAX_WIDTH - CoralReef.CLUSTER_LARGE_MIN_WIDTH);
                double h = w * (0.50 + cRng.nextFloat() * 0.08);
                double rx = w / 2.0;
                double ry = h / 2.0;

                Point2D.Double placed = resolveReefObstacleCollision(candX, candY, rx, ry, cRng, worldNoise);
                if (placed != null) {
                    coralReefs.add(new CoralReef(placed.x, placed.y, cRng, CoralReef.ClusterTier.LARGE_DEEP_SEA));
                }
            }
        }

        // 2. SINH CỤM VỪA VÀ NHỎ THEO LƯỚI MỊN (FINE GRID 900px, JITTER 70px)
        int minFgx = (int) Math.floor(worldX / REEF_FINE_CELL_SIZE);
        int maxFgx = (int) Math.floor((worldX + size) / REEF_FINE_CELL_SIZE);
        int minFgy = (int) Math.floor(worldY / REEF_FINE_CELL_SIZE);
        int maxFgy = (int) Math.floor((worldY + size) / REEF_FINE_CELL_SIZE);

        for (int fgx = minFgx; fgx <= maxFgx; fgx++) {
            for (int fgy = minFgy; fgy <= maxFgy; fgy++) {
                long fSeed = ((long) fgx * 58498429L) ^ ((long) fgy * 29482847L) ^ (long) Config.World.SEED;
                Random fRng = new Random(fSeed);

                double candX = fgx * REEF_FINE_CELL_SIZE + (REEF_FINE_CELL_SIZE / 2.0) + (fRng.nextFloat() - 0.5) * (REEF_FINE_JITTER_MAX * 2.0);
                double candY = fgy * REEF_FINE_CELL_SIZE + (REEF_FINE_CELL_SIZE / 2.0) + (fRng.nextFloat() - 0.5) * (REEF_FINE_JITTER_MAX * 2.0);

                if (candX < worldX || candX >= worldX + size || candY < worldY || candY >= worldY + size) {
                    continue;
                }

                if (!isWaterAt(candX, candY, worldNoise, stageId)) continue;
                double pot = getWaterPotential(candX, candY, worldNoise, stageId);
                if (pot <= 0.05) continue; // Không sinh trên cạn/đảo/cát

                // Bỏ ô mịn giao thoa với elip của cụm lớn (cộng đệm 120px)
                if (intersectsLargeReef(candX, candY, worldNoise)) {
                    continue;
                }

                if (fRng.nextFloat() > REEF_DEEP_WATER_CHANCE) continue;

                // Cụm vừa chỉ sinh ở nước sâu (> 0.15), nước vừa (0.05 - 0.15) chỉ sinh cụm nhỏ
                CoralReef.ClusterTier tier;
                if (pot <= 0.15) {
                    tier = CoralReef.ClusterTier.SMALL;
                } else {
                    tier = (fRng.nextFloat() < 0.45) ? CoralReef.ClusterTier.SMALL : CoralReef.ClusterTier.MEDIUM;
                }

                double w, h;
                if (tier == CoralReef.ClusterTier.SMALL) {
                    w = CoralReef.CLUSTER_SMALL_MIN_WIDTH + fRng.nextFloat() * (CoralReef.CLUSTER_SMALL_MAX_WIDTH - CoralReef.CLUSTER_SMALL_MIN_WIDTH);
                    h = w * (0.65 + fRng.nextFloat() * 0.15);
                } else {
                    w = CoralReef.CLUSTER_MEDIUM_MIN_WIDTH + fRng.nextFloat() * (CoralReef.CLUSTER_MEDIUM_MAX_WIDTH - CoralReef.CLUSTER_MEDIUM_MIN_WIDTH);
                    h = w * (0.64 + fRng.nextFloat() * 0.12);
                }
                double rx = w / 2.0;
                double ry = h / 2.0;

                Point2D.Double placed = resolveReefObstacleCollision(candX, candY, rx, ry, fRng, worldNoise);
                if (placed != null) {
                    coralReefs.add(new CoralReef(placed.x, placed.y, fRng, tier));
                }
            }
        }
    }

    /**
     * Sinh san hô khối tròn và hải quỳ lẻ tẻ ngoài cụm (Standalone Ocean Vegetation).
     * Mật độ khoảng 1 cây mỗi 60.000px², chỉ sinh dưới nước sâu/vừa.
     */
    private void generateStandaloneCorals(FastNoise worldNoise) {
        if (stageId != 2) return;
        standaloneCorals.clear();

        int targetCount = (int) Math.round(((double) size * size) / STANDALONE_REEF_AREA);
        Random rng = new Random(seed ^ 47192837L);

        int attempts = 0;
        int maxAttempts = targetCount * 5;
        while (standaloneCorals.size() < targetCount && attempts < maxAttempts) {
            attempts++;
            double wx = worldX + 16.0 + rng.nextFloat() * (size - 32.0);
            double wy = worldY + 16.0 + rng.nextFloat() * (size - 32.0);

            if (!isWaterAt(wx, wy, worldNoise, stageId)) continue;
            if (getWaterPotential(wx, wy, worldNoise, stageId) <= 0.05) continue;

            // Đảm bảo nằm ngoài các rạn san hô
            boolean insideCluster = false;
            for (CoralReef reef : coralReefs) {
                if (reef.contains(wx, wy)) {
                    insideCluster = true;
                    break;
                }
            }
            if (insideCluster) continue;

            // Xen khối tròn (IDs 6..9) và hải quỳ lẻ (IDs 10..12)
            int spriteId;
            int plantW, plantH;
            if (rng.nextFloat() < 0.50) {
                spriteId = CoralManager.ID_ROUND_YELLOW + rng.nextInt(4);
                plantH = 20 + rng.nextInt(12);
                plantW = (int) Math.round(plantH * 1.05);
            } else {
                spriteId = CoralManager.ID_ANEMONE_RED_SMALL + rng.nextInt(3);
                plantH = 20 + rng.nextInt(12);
                plantW = (int) Math.round(plantH * 0.95);
            }

            boolean isFlipped = rng.nextBoolean();
            double swayPhase = wx * 0.05 + wy * 0.05 + standaloneCorals.size() * 0.9;
            double footY = wy + plantH * 0.35;
            boolean isSwaying = CoralManager.isAnemone(spriteId);

            standaloneCorals.add(new CoralReef.CoralPlant(0, 0, wx, wy, footY, spriteId, plantW, plantH, isFlipped, swayPhase, isSwaying));
        }
    }

    private boolean hasNearbyProp(double px, double py, double minDist) {
        for (WorldProp p : props) {
            double reqDist = minDist;
            if (p.getType() == WorldProp.Type.CAVE) {
                reqDist = Math.max(minDist, 180.0);
            } else if (p.getType() == WorldProp.Type.TREE) {
                reqDist = Math.max(minDist, 150.0);
            } else if (p.getType() == WorldProp.Type.ROCK) {
                reqDist = Math.max(minDist, 120.0);
            }
            if (Math.hypot(p.getX() - px, p.getY() - py) < reqDist) {
                return true;
            }
        }
        return false;
    }

    public void update(double dt) {
        for (WorldProp prop : props) {
            prop.update(dt);
        }
    }

    public int getCx() { return cx; }
    public int getCy() { return cy; }
    public int getSize() { return size; }
    public double getWorldX() { return worldX; }
    public double getWorldY() { return worldY; }
    public BufferedImage getCachedImage() { return cachedImage; }
    public List<WorldProp> getProps() { return props; }
    public List<GrassPatch> getGrassPatches() { return grassPatches; }
    public List<GrassPatch.ClusterPlant> getStandalonePlants() { return standalonePlants; }
    public List<CoralReef> getCoralReefs() { return coralReefs; }
    public List<CoralReef.CoralPlant> getStandaloneCorals() { return standaloneCorals; }
    public List<WaterTileRun> getWaterRuns() { return waterRuns; }
    public List<ShoreSegment> getShoreSegments() { return shoreSegments; }
    public boolean hasWater() { return !waterRuns.isEmpty(); }

    /**
     * Giải phóng tài nguyên đồ họa (BufferedImage) của Chunk và các mảng cỏ / rạn san hô khi Chunk bị dỡ khỏi bộ nhớ.
     */
    public void dispose() {
        if (cachedImage != null) {
            cachedImage.flush();
            cachedImage = null;
        }
        if (grassPatches != null) {
            for (GrassPatch gp : grassPatches) {
                gp.dispose();
            }
            grassPatches.clear();
        }
        if (standalonePlants != null) {
            standalonePlants.clear();
        }
        if (coralReefs != null) {
            for (CoralReef cr : coralReefs) {
                cr.dispose();
            }
            coralReefs.clear();
        }
        if (standaloneCorals != null) {
            standaloneCorals.clear();
        }
    }

    /**
     * Ước tính tỉ lệ diện tích mảng cỏ phủ trên đất liền trong vùng mẫu (sampleGrid x sampleGrid pixels).
     * Phục vụ kiểm tra chỉ số coverage ratio ~18-25% theo thiết kế.
     */
    public static double estimateCoverageRatio(FastNoise worldNoise, int sampleGrid) {
        if (sampleGrid <= 0) sampleGrid = 4000;
        int step = 25; // bước lưới lấy mẫu 25px
        int totalLandSamples = 0;
        int coveredLandSamples = 0;

        // Sinh danh sách mảng cỏ trong vùng lấy mẫu
        List<GrassPatch> testPatches = new ArrayList<>();
        int minGx = 0;
        int maxGx = (int) Math.ceil((double) sampleGrid / PATCH_CELL_SIZE);
        int minGy = 0;
        int maxGy = (int) Math.ceil((double) sampleGrid / PATCH_CELL_SIZE);

        for (int gx = minGx; gx <= maxGx; gx++) {
            for (int gy = minGy; gy <= maxGy; gy++) {
                long cellSeed = ((long) gx * 73856093L) ^ ((long) gy * 19349663L) ^ 1337L;
                Random cellRng = new Random(cellSeed);

                double candX = gx * PATCH_CELL_SIZE + (PATCH_CELL_SIZE / 2.0) + (cellRng.nextFloat() - 0.5) * (PATCH_JITTER_MAX * 2.0);
                double candY = gy * PATCH_CELL_SIZE + (PATCH_CELL_SIZE / 2.0) + (cellRng.nextFloat() - 0.5) * (PATCH_JITTER_MAX * 2.0);

                if (isWaterAt(candX, candY, worldNoise, 1)) continue;
                double pot = getWaterPotential(candX, candY, worldNoise, 1);
                if (pot >= -0.06) continue;

                double biomeRoll = worldNoise.noise2D(candX * 0.0035, candY * 0.0035);
                if (biomeRoll < PATCH_BIOME_THRESHOLD) continue;

                double w = GrassPatch.PATCH_MIN_WIDTH + cellRng.nextFloat() * (GrassPatch.PATCH_MAX_WIDTH - GrassPatch.PATCH_MIN_WIDTH);
                double h = GrassPatch.PATCH_MIN_HEIGHT + cellRng.nextFloat() * (GrassPatch.PATCH_MAX_HEIGHT - GrassPatch.PATCH_MIN_HEIGHT);
                double radiusX = w / 2.0;
                double radiusY = h / 2.0;
                double rotation = (cellRng.nextFloat() - 0.5) * 0.50;

                testPatches.add(new GrassPatch(candX, candY, radiusX, radiusY, rotation, cellRng));
            }
        }

        for (int x = 0; x < sampleGrid; x += step) {
            for (int y = 0; y < sampleGrid; y += step) {
                if (isWaterAt(x, y, worldNoise, 1)) continue;
                totalLandSamples++;

                for (GrassPatch gp : testPatches) {
                    if (gp.contains(x, y)) {
                        coveredLandSamples++;
                        break;
                    }
                }
            }
        }

        double ratio = (totalLandSamples > 0) ? ((double) coveredLandSamples / totalLandSamples) : 0.0;
        if (DEBUG_GRASS_PATCHES) {
            System.out.printf("[DEBUG] GrassPatch coverage ratio: %.2f%% (%d / %d land points sampled in %dx%d area)\n",
                ratio * 100.0, coveredLandSamples, totalLandSamples, sampleGrid, sampleGrid);
        }
        return ratio;
    }
 
    // ==========================================
    // CÁC HÀM VẼ DECALS ĐÁY BIỂN MÀN 2 (PIXEL ART)
    // ==========================================
    private void drawStarfishDecal(Graphics2D g, int x, int y, boolean isOrange) {
        Color col = isOrange ? COLOR_SEABED_STARFISH_ORANGE : COLOR_SEABED_STARFISH_RED;
        g.setColor(col);
        g.fillRect(x, y, 1, 1);       // Tâm sao
        g.fillRect(x, y - 2, 1, 1);   // Cánh trên
        g.fillRect(x + 2, y - 1, 1, 1); // Cánh trên-phải
        g.fillRect(x + 1, y + 2, 1, 1); // Cánh dưới-phải
        g.fillRect(x - 1, y + 2, 1, 1); // Cánh dưới-trái
        g.fillRect(x - 2, y - 1, 1, 1); // Cánh trên-trái
        // Điểm sáng nhẹ ở tâm
        g.setColor(new Color(254, 215, 170));
        g.fillRect(x, y, 1, 1);
    }

    private void drawSeashellDecal(Graphics2D g, int x, int y, boolean isPink) {
        Color mainCol = isPink ? COLOR_SEABED_SHELL_PINK : COLOR_SEABED_SHELL_IVORY;
        Color rimCol  = isPink ? new Color(244, 114, 182) : new Color(203, 213, 225);
        g.setColor(mainCol);
        g.fillRect(x - 1, y - 1, 3, 2);
        g.fillRect(x, y + 1, 1, 1);
        g.setColor(rimCol);
        g.drawLine(x - 1, y - 2, x + 1, y - 2);
    }

    private void drawSeabedPebbleDecal(Graphics2D g, int x, int y, double rand) {
        g.setColor(COLOR_SEABED_PEBBLE);
        int pw = (rand > 0.5) ? 3 : 2;
        int ph = 2;
        g.fillRect(x, y, pw, ph);
        g.setColor(new Color(74, 102, 120));
        g.fillRect(x, y, 1, 1); // Điểm sáng hạt sỏi
    }
}
