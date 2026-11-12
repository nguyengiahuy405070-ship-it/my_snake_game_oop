package com.snakearena.render;

import com.snakearena.core.Camera;
import com.snakearena.core.Config;
import com.snakearena.entity.*;
import com.snakearena.world.Chunk;
import com.snakearena.world.CoralManager;
import com.snakearena.world.CoralReef;
import com.snakearena.world.GrassPatch;
import com.snakearena.world.VegetationManager;
import com.snakearena.world.WorldProp;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pipeline tách lớp môi trường (Layered Rendering) chuẩn 7 lớp đồ họa:
 * 1. Ground (Offscreen chunk cache)
 * 2. Decals
 * 3. Shadows (Bóng đổ elip định hướng)
 * 4 & 5. Y-Sorted Entities & Props (Cây, Đá, Bụi rậm, Bẫy, Rắn, Con mồi)
 * 6. Canopy / Air (Mây trôi Parallax, Bướm, Đại bàng, Hạt)
 * 7. Post-FX, Vignette, Minimap Radar & HUD Cố định.
 */
public class LayeredRenderer {
    // =========================================================================
    // HẰNG SỐ CẤU HÌNH THỰC VẬT MẢNG CỎ RỘNG (WIDE GRASS PATCHES - MAP 1)
    // =========================================================================
    public static final double PLANT_SWAY_FREQUENCY     = 2.4;        // Tần số lay động gió tự nhiên (rad/s)
    public static final double PLANT_SWAY_MAX_AMPLITUDE = 3.5;        // Biên độ lắc tối đa ở ngọn cây cỏ (px)
    public static final int    PLANT_FRAME_COUNT        = 8;          // Số frame lay động pre-rendered (Zero-GC)
    public static final float  PLANT_OCCLUSION_ALPHA    = 0.70f;      // Alpha cỏ phía trước khi che thân rắn (~0.70)
    public static final AlphaComposite COMPOSITE_PLANT_OCCLUSION = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, PLANT_OCCLUSION_ALPHA);

    // =========================================================================
    // HẰNG SỐ CẤU HÌNH THỰC VẬT RẶNG SAN HÔ (WIDE CORAL REEFS - MAP 2)
    // =========================================================================
    public static final double REEF_SWAY_FREQUENCY       = 1.8;        // Tần số dao động sóng ngầm (rad/s)
    public static final double REEF_SWAY_MAX_AMPLITUDE   = 5.0;        // Biên độ lắc tối đa cho rong biển mềm (px)
    public static final int    REEF_PLANT_FRAME_COUNT    = 8;          // Số frame lay động pre-rendered (Zero-GC)
    public static final float  REEF_OCCLUSION_ALPHA      = 0.70f;      // Alpha san hô phía trước khi che thân rắn (~0.70)
    public static final AlphaComposite COMPOSITE_REEF_OCCLUSION = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, REEF_OCCLUSION_ALPHA);

    // =========================================================================
    // HẰNG SỐ & BỘ ĐỆM SPRITE BOSS GẤU (BEAR SPRITES & FX PRE-BAKED 16 HƯỚNG)
    // =========================================================================
    public static final int    BEAR_SPRITE_SCALE        = 2;          // Phóng x2 pixel-art NEAREST_NEIGHBOR
    public static final int    BEAR_FRAME_W             = 72;
    public static final int    BEAR_FRAME_H             = 48;
    public static final int    BEAR_GRID_COLS           = 4;
    public static final int    BEAR_GRID_ROWS           = 4;
    public static final int    BEAR_PIVOT_X             = 26;
    public static final int    BEAR_PIVOT_Y             = 24;
    public static final int    BEAR_DIRECTIONS          = 16;         // 16 hướng xoay (mỗi 22.5 độ)
    public static final int    BEAR_UNSCALED_ROT_SIZE   = 88;         // Kích thước trước khi phóng đảm bảo không cắt viền khi xoay
    public static final int    BEAR_BAKED_SIZE          = BEAR_UNSCALED_ROT_SIZE * BEAR_SPRITE_SCALE; // 176
    public static final int    BEAR_BAKED_HALF          = BEAR_BAKED_SIZE / 2;                       // 88

    public static final int    BEAR_STREAK_UNSCALED     = 72;
    public static final int    BEAR_STREAK_BAKED_SIZE   = BEAR_STREAK_UNSCALED * BEAR_SPRITE_SCALE;   // 144
    public static final int    BEAR_STREAK_BAKED_HALF   = BEAR_STREAK_BAKED_SIZE / 2;                 // 72
    public static final int    BEAR_BITE_UNSCALED       = 70;
    public static final int    BEAR_BITE_BAKED_SIZE     = BEAR_BITE_UNSCALED * BEAR_SPRITE_SCALE;     // 140
    public static final int    BEAR_BITE_BAKED_HALF     = BEAR_BITE_BAKED_SIZE / 2;                   // 70

    public static final double BEAR_WALK_FRAME_TIME     = 0.12;
    public static final double BEAR_WINDUP_SHAKE_TIME   = 0.06;
    public static final double BEAR_RETREAT_FRAME_TIME  = 0.10;
    public static final double BEAR_DUST_FRAME_TIME     = 0.07;
    public static final double BEAR_BITE_FRAME_TIME     = 0.05;
    public static final double BEAR_FOOT_Y_OFFSET       = 22.0;       // Offset chân gấu để Y-sort đúng thứ tự trước/sau

    public static final int    BEAR_TELEGRAPH_WIDTH     = 50;
    public static final float  BEAR_SHADOW_ALPHA        = 0.25f;
    public static final int    BEAR_SHADOW_W            = 112;
    public static final int    BEAR_SHADOW_H            = 44;

    public static final AlphaComposite COMPOSITE_BEAR_STREAK = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.70f);
    public static final Color  COLOR_BEAR_STUN_DOT           = new Color(250, 204, 21);
    public static final Color  COLOR_BEAR_TELEGRAPH_BORDER   = new Color(248, 113, 113, 150);
    private static final Color[] BEAR_TELEGRAPH_FILL_COLORS  = new Color[16];
    private static final Color[] BEAR_TELEGRAPH_BLINK_COLORS = new Color[16];

    private static final Color COLOR_BOSS_BAR_BG      = new Color(15, 23, 42, 220);
    private static final Color COLOR_BOSS_BAR_FILL    = new Color(239, 68, 68);
    private static final Color COLOR_BOSS_BAR_BORDER  = new Color(203, 213, 225);
    private static final Color COLOR_BOSS_NAME_BEAR   = new Color(251, 146, 60);
    private static final Color COLOR_BOSS_NAME_OCTO   = new Color(244, 63, 94);
    private static final Color COLOR_BOSS_NAME_PYTHON = new Color(192, 132, 252);
    private static final Color COLOR_BOSS_NAME_SWAMP  = new Color(56, 189, 248);
    private static final Color COLOR_BOSS_NAME_VOID   = new Color(250, 204, 21);

    private static final Color COLOR_BOSS_MANA_FILL           = new Color(56, 189, 248);
    private static final Color COLOR_BOSS_MANA_GLOW           = new Color(186, 230, 253, 185);
    private static final Color COLOR_BOSS_RECOVERY_RING       = new Color(56, 189, 248, 125);
    private static final Color COLOR_BOSS_RECOVERY_PROGRESS   = new Color(125, 211, 252, 205);
    private static final Color COLOR_BOSS_RECOVERY_CORE       = new Color(224, 242, 254, 220);
    private static final Color COLOR_BOSS_RECOVERY_TEXT       = new Color(125, 211, 252);
    private static final Color COLOR_PYTHON_TELE_DASH_FILL    = new Color(168, 85, 247, 68);
    private static final Color COLOR_PYTHON_TELE_DASH_BORDER  = new Color(217, 70, 239, 175);
    private static final Color COLOR_PYTHON_TELE_VENOM_RAY    = new Color(217, 70, 239, 155);
    private static final Color COLOR_PYTHON_TELE_WHIP_FILL    = new Color(250, 204, 21, 32);
    private static final Color COLOR_PYTHON_TELE_WHIP_RING    = new Color(250, 204, 21, 155);
    private static final Color COLOR_PYTHON_ROAR_WAVE         = new Color(232, 121, 249, 185);
    private static final Color COLOR_PYTHON_ROAR_GLOW         = new Color(217, 70, 239, 135);
    private static final Color COLOR_PYTHON_CHARGE_PULSE      = new Color(250, 204, 21, 195);
    private static final Color COLOR_PYTHON_CHARGE_CORE       = new Color(240, 171, 252, 225);
    private static final Color COLOR_PYTHON_VENOM_FLARE       = new Color(192, 132, 252, 215);
    private static final Color COLOR_PYTHON_DASH_GHOST        = new Color(168, 85, 247);
    private static final Color COLOR_VOID_DASH_GHOST          = new Color(56, 189, 248);
    private static final Color COLOR_PYTHON_JOINT_RIDGE       = new Color(107, 33, 168, 210);
    private static final Color COLOR_VOID_JOINT_RIDGE         = new Color(56, 189, 248, 170);
    private static final Color COLOR_PYTHON_RATTLE_GOLD       = new Color(250, 204, 21);
    private static final Color COLOR_PYTHON_RATTLE_DARK       = new Color(161, 98, 7);
    private static final Color COLOR_PYTHON_SKILL_BADGE       = new Color(232, 121, 249);
    private static final AlphaComposite COMPOSITE_SNAKE_AFTERIMAGE_1 = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.36f);
    private static final AlphaComposite COMPOSITE_SNAKE_AFTERIMAGE_2 = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.18f);
    private static final String[] RECOVERY_LABEL_CACHE = new String[21];

    static {
        for (int i = 0; i < RECOVERY_LABEL_CACHE.length; i++) {
            RECOVERY_LABEL_CACHE[i] = String.format("HOI MANA %.1fs", i * 0.1);
        }
        for (int i = 0; i < BEAR_TELEGRAPH_FILL_COLORS.length; i++) {
            float t = i / 15.0f;
            float alphaFill = 0.15f + t * 0.25f; // 0.15 -> 0.40
            float alphaBlink = Math.min(0.65f, alphaFill + 0.16f);
            BEAR_TELEGRAPH_FILL_COLORS[i] = new Color(220, 38, 38, Math.round(alphaFill * 255.0f));
            BEAR_TELEGRAPH_BLINK_COLORS[i] = new Color(239, 68, 68, Math.round(alphaBlink * 255.0f));
        }
    }

    // =========================================================================
    // CÁC HẰNG SỐ ĐIỀU CHỈNH: NƯỚC ĐỘNG, BỌT SÓNG, ĐUNG ĐƯA VÀ GỢN SÓNG KHI BƠI
    // =========================================================================
    // CÁC HẰNG SỐ ĐIỀU CHỈNH: NƯỚC ĐỘNG, BỌT SÓNG, ĐUNG ĐƯA VÀ GỢN SÓNG KHI BƠI
    // =========================================================================
    // 1. Mặt nước chuyển động (Dual-Scale Non-Repeating Water Caustics - Map 2 Overhaul)
    public static final int WATER_CAUSTICS_TEX1_SIZE = 160;        // Kích thước caustics lớp 1 (160x160, tỉ lệ 1.0x)
    public static final int WATER_CAUSTICS_TEX2_SIZE = 260;        // Kích thước caustics lớp 2 (260x260, tỉ lệ 1.625x phi chu kỳ)
    public static final int WATER_CAUSTICS_TEX_SIZE  = 160;        // Tương thích ngược
    public static final double WATER_SCROLL_SPEED_1_X = 14.0;      // Tốc độ cuộn lớp 1 trục X (px/s)
    public static final double WATER_SCROLL_SPEED_1_Y = 9.0;       // Tốc độ cuộn lớp 1 trục Y (px/s)
    public static final double WATER_SCROLL_SPEED_2_X = -18.0;     // Tốc độ cuộn lớp 2 trục X (ngược chiều, px/s)
    public static final double WATER_SCROLL_SPEED_2_Y = -12.0;     // Tốc độ cuộn lớp 2 trục Y (ngược chiều, px/s)
    public static final float WATER_CAUSTICS_ALPHA_1 = 0.18f;      // Độ mờ vân sáng lớp 1 (0.15 - 0.25)
    public static final float WATER_CAUSTICS_ALPHA_2 = 0.14f;      // Độ mờ vân sáng lớp 2 (0.12 - 0.20)

    // Tia sáng mặt trời nghiêng (Tilted Soft Sunbeams / God Rays - Màn 2)
    public static final double SUNRAY_TILT_DEG       = 20.0;       // Góc nghiêng 20 độ theo một hướng chiếu xuống
    public static final double SUNRAY_TILT_SHEAR     = Math.tan(Math.toRadians(SUNRAY_TILT_DEG)); // ~0.364
    public static final float  SUNRAY_BASE_ALPHA     = 0.08f;      // Alpha trung bình dịu nhẹ (0.05 - 0.12)
    public static final Color  COLOR_SUNBEAM_BEAM    = new Color(186, 230, 253);

    // Nhuộm màu theo độ sâu (Underwater Color Depth Tint)
    public static final Color COLOR_UNDERWATER_TINT  = new Color(6, 44, 62); // #062c3e: Sắc xanh teal đại dương sâu thẳm
    public static final float TINT_ALPHA_FLORA       = 0.18f;      // Nhuộm san hô, rong rêu
    public static final float TINT_ALPHA_CREATURE    = 0.16f;      // Nhuộm cá mập, cá biển, cá sấu
    public static final float TINT_ALPHA_SNAKE       = 0.12f;      // Nhuộm rắn khi bơi dưới nước

    // =========================================================================
    // =========================================================================
    // HẰNG SỐ CẤU HÌNH THÂN RẮN ĐỘ RỘNG ĐỀU, THUÔN DÀI (UNIFORM SLENDER SNAKE)
    // =========================================================================
    // 1. Độ rộng thân đều, tỷ lệ đầu, khoảng cách đốt & hệ số boss dạng rắn
    public static final float  SNAKE_BODY_WIDTH       = 18.0f;      // Thân đều ~62% bản cũ, thuôn dài mảnh
    public static final float  SNAKE_BORDER_WIDTH     = 20.0f;      // Lớp viền rộng hơn thân đúng 2px (mỗi bên 1px)
    public static final float  SNAKE_HIGHLIGHT_WIDTH  = 2.5f;       // Highlight mảnh chạy dọc lưng phía trên-trái
    public static final float  SNAKE_HEAD_RATIO       = 1.12f;      // Đầu nhỉnh hơn thân tối đa 10-15% (chiều rộng ~20.2px)

    // Boss dạng rắn: Thái Cổ Cự Trăn (Python) & Hư Không Long Vương (Void Dragon)
    public static final float  BOSS_SNAKE_SCALE       = 1.6f;       // Hệ số boss rắn x1.6 cố định
    public static final float  BOSS_BODY_WIDTH        = SNAKE_BODY_WIDTH * BOSS_SNAKE_SCALE; // 28.8f
    public static final float  BOSS_BORDER_WIDTH      = BOSS_BODY_WIDTH + 2.0f;              // 30.8f
    public static final float  BOSS_HIGHLIGHT_WIDTH   = 3.5f;

    // 2. Chuyển động sóng sin vuông góc hướng di chuyển (biên độ đồng nhất suốt thân)
    public static final double SNAKE_WAVE_AMP_NORMAL  = 2.6;        // Biên độ sóng đều suốt thân khi bò thường (px)
    public static final double SNAKE_WAVE_AMP_FAST    = 4.2;        // Biên độ sóng khi chạy nhanh (Sprint/Dash, px)
    public static final double SNAKE_WAVE_FREQ_NORMAL = 6.5;        // Tần số uốn lượn cơ bản (rad/s)
    public static final double SNAKE_WAVE_FREQ_FAST   = 11.0;       // Tần số uốn lượn khi tăng tốc (rad/s)
    public static final double SNAKE_WATER_TAIL_SWAY_FREQ = 4.0;    // Đuôi vẫy chậm uyển chuyển dưới nước (rad/s)
    public static final double SNAKE_WATER_TAIL_SWAY_AMP  = 3.8;    // Biên độ vẫy đuôi dưới nước (px)

    // BasicStroke tiền khởi tạo tái sử dụng (CAP_ROUND cho đuôi bo tròn tự nhiên cùng bán kính thân)
    public static final BasicStroke STROKE_NORMAL_BORDER    = new BasicStroke(SNAKE_BORDER_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    public static final BasicStroke STROKE_NORMAL_BODY      = new BasicStroke(SNAKE_BODY_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    public static final BasicStroke STROKE_NORMAL_HIGHLIGHT = new BasicStroke(SNAKE_HIGHLIGHT_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    public static final BasicStroke STROKE_BOSS_BORDER      = new BasicStroke(BOSS_BORDER_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    public static final BasicStroke STROKE_BOSS_BODY        = new BasicStroke(BOSS_BODY_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    public static final BasicStroke STROKE_BOSS_HIGHLIGHT   = new BasicStroke(BOSS_HIGHLIGHT_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    // 3. Màu sắc & Palette riêng cho từng loài (Màu thân, Viền tối hơn 35-40%, Highlight trên-trái, Hoa văn)
    // Cấp 1: Garter Snake (Thảo Xà Lục Bảo - Đầu tròn mềm)
    public static final Color SNAKE_LV1_BODY          = new Color(34, 197, 94);     // #22c55e Xanh ngọc lục bảo tươi
    public static final Color SNAKE_LV1_OUTLINE       = new Color(15, 56, 30);      // #0f381e Viền lục sẫm (-38%)
    public static final Color SNAKE_LV1_HIGHLIGHT     = new Color(134, 239, 172);   // #86efac Xanh mạ sáng
    public static final Color SNAKE_LV1_SPOT          = new Color(22, 101, 52, 220); // #166534 Đốm xanh đậm lặp đều
    public static final Color SNAKE_LV1_STRIPE        = new Color(254, 240, 138, 230); // #fef08a Chấm vàng chanh

    // Cấp 2: Shadow Viper (Độc Xà Bóng Đêm - Đầu tam giác sắc nhọn)
    public static final Color SNAKE_LV2_BODY          = new Color(147, 51, 234);    // #9333ea Tím huyền bí
    public static final Color SNAKE_LV2_OUTLINE       = new Color(46, 16, 101);     // #2e1065 Tím chàm sẫm (-38%)
    public static final Color SNAKE_LV2_HIGHLIGHT     = new Color(192, 132, 252);   // #c084fc Tím phấn sáng
    public static final Color SNAKE_LV2_BAND          = new Color(232, 121, 249, 220); // #e879f9 Khoanh tím neon
    public static final Color SNAKE_LV2_GLINT         = new Color(34, 211, 238, 230); // #22d3ee Điểm lam dạ quang

    // Cấp 3: Thái Cổ Hoàng Kim Cự Long (Titan Boa - Đầu có vương miện rồng)
    public static final Color SNAKE_LV3_BODY          = new Color(234, 179, 8);     // #eab308 Hoàng kim rực rỡ
    public static final Color SNAKE_LV3_OUTLINE       = new Color(113, 63, 18);     // #713f12 Nâu hổ phách sẫm (-38%)
    public static final Color SNAKE_LV3_HIGHLIGHT     = new Color(254, 240, 138);   // #fef08a Vàng kim sáng
    public static final Color SNAKE_LV3_CHEVRON       = new Color(220, 38, 38, 230); // #dc2626 Vảy chữ V ruby đỏ

    // Cấp 4: Crimson Fire Drake (Huyết Diệm Hỏa Xà - Đầu có sừng hỏa xà)
    public static final Color SNAKE_LV4_BODY          = new Color(239, 68, 68);     // #ef4444 Đỏ huyết rồng
    public static final Color SNAKE_LV4_OUTLINE       = new Color(69, 10, 10);      // #450a0a Đỏ đen thạch anh (-38%)
    public static final Color SNAKE_LV4_HIGHLIGHT     = new Color(253, 186, 116);   // #fdba74 Cam lửa sáng
    public static final Color SNAKE_LV4_FLAME         = new Color(251, 191, 36, 230); // #fbbf24 Vằn ngọn lửa rực cháy

    // Cấp 5: Celestial Leviathan (Kim Long Thần Xà - Đầu có mào thần long & ngọc vũ trụ)
    public static final Color SNAKE_LV5_BODY          = new Color(250, 204, 21);    // #facc15 Hoàng kim thần thánh
    public static final Color SNAKE_LV5_OUTLINE       = new Color(120, 53, 15);     // #78350f Nâu đồng hoàng gia (-38%)
    public static final Color SNAKE_LV5_HIGHLIGHT     = new Color(254, 249, 195);   // #fef9c3 Trắng kim cương sáng
    public static final Color SNAKE_LV5_RING          = new Color(56, 189, 248, 230); // #38bdf8 Khoanh tinh vân lam ngọc

    // Rắn AI: Hunter (Cọp Lửa Săn Mồi - Đầu vát tam giác hung hãn)
    public static final Color SNAKE_AI_HUNTER_BODY    = new Color(220, 38, 38);     // #dc2626 Đỏ cam săn mồi
    public static final Color SNAKE_AI_HUNTER_OUTLINE = new Color(69, 10, 10);      // #450a0a Viền đỏ thẫm (-38%)
    public static final Color SNAKE_AI_HUNTER_HIGHLIGHT = new Color(252, 165, 165); // #fca5a5 Hồng phấn sáng
    public static final Color SNAKE_AI_HUNTER_STRIPE  = new Color(42, 8, 8, 230);    // Vằn hổ đen đỏ xiên chéo

    // Rắn AI: Forager (Báo Đốm Kiếm Ăn - Đầu tròn mềm thanh thoát)
    public static final Color SNAKE_AI_FORAGER_BODY   = new Color(161, 98, 7);      // #a16207 Nâu đồng ngụy trang
    public static final Color SNAKE_AI_FORAGER_OUTLINE = new Color(59, 24, 4);      // #3b1804 Nâu đen viền (-38%)
    public static final Color SNAKE_AI_FORAGER_HIGHLIGHT = new Color(253, 230, 138);// #fde68a Be cát sáng
    public static final Color SNAKE_AI_FORAGER_ROSETTE = new Color(35, 15, 5, 230); // Đốm hoa mai nâu sẫm
    public static final Color SNAKE_AI_FORAGER_CORE    = new Color(250, 204, 21);   // Tâm vàng đốm hoa mai

    // Boss 1: Thái Cổ Cự Trăn (Giant Python)
    public static final Color SNAKE_BOSS_PYTHON_BODY   = new Color(59, 7, 100);     // #3b0764 Tím thẫm hắc thạch
    public static final Color SNAKE_BOSS_PYTHON_OUTLINE = new Color(20, 3, 35);     // #140323 Viền hắc ám
    public static final Color SNAKE_BOSS_PYTHON_HIGHLIGHT = new Color(192, 132, 252);
    public static final Color SNAKE_BOSS_PYTHON_PATTERN = new Color(234, 179, 8);   // Vảy hoàng kim / đỏ khi enrage

    // Boss 2: Hư Không Long Vương (Void Dragon)
    public static final Color SNAKE_BOSS_VOID_BODY     = new Color(30, 9, 56);      // Tím hư không vũ trụ
    public static final Color SNAKE_BOSS_VOID_OUTLINE  = new Color(12, 3, 24);
    public static final Color SNAKE_BOSS_VOID_HIGHLIGHT = new Color(216, 180, 254);
    public static final Color SNAKE_BOSS_VOID_PATTERN  = new Color(56, 189, 248);   // Vảy lam tinh vũ trụ

    // Bảng màu mảng tra cứu nhanh (LUT) cho 7 loài rắn (0..4: Player Lv1..Lv5, 5: AI Hunter, 6: AI Forager)
    public static final Color[] SNAKE_BASE_COLOR = {
        SNAKE_LV1_BODY, SNAKE_LV2_BODY, SNAKE_LV3_BODY, SNAKE_LV4_BODY, SNAKE_LV5_BODY,
        SNAKE_AI_HUNTER_BODY, SNAKE_AI_FORAGER_BODY
    };
    public static final Color[] SNAKE_OUTLINE_LAND = {
        SNAKE_LV1_OUTLINE, SNAKE_LV2_OUTLINE, SNAKE_LV3_OUTLINE, SNAKE_LV4_OUTLINE, SNAKE_LV5_OUTLINE,
        SNAKE_AI_HUNTER_OUTLINE, SNAKE_AI_FORAGER_OUTLINE
    };
    public static final Color[] SNAKE_HIGHLIGHT_COLOR = {
        SNAKE_LV1_HIGHLIGHT, SNAKE_LV2_HIGHLIGHT, SNAKE_LV3_HIGHLIGHT, SNAKE_LV4_HIGHLIGHT, SNAKE_LV5_HIGHLIGHT,
        SNAKE_AI_HUNTER_HIGHLIGHT, SNAKE_AI_FORAGER_HIGHLIGHT
    };

    public static final Color[] SNAKE_BASE_COLOR_WATER = new Color[7];
    public static final Color[] SNAKE_OUTLINE_WATER = new Color[7];
    public static final Color[] SNAKE_HIGHLIGHT_COLOR_WATER = new Color[7];
    private static boolean snakePalettesInitialized = false;

    public static synchronized void initSnakePalettes() {
        if (snakePalettesInitialized) return;
        for (int a = 0; a < 7; a++) {
            SNAKE_BASE_COLOR_WATER[a]      = lerpColor(SNAKE_BASE_COLOR[a], COLOR_UNDERWATER_TINT, TINT_ALPHA_SNAKE);
            SNAKE_OUTLINE_WATER[a]         = lerpColor(SNAKE_OUTLINE_LAND[a], COLOR_UNDERWATER_TINT, TINT_ALPHA_SNAKE * 1.35f);
            SNAKE_HIGHLIGHT_COLOR_WATER[a] = lerpColor(SNAKE_HIGHLIGHT_COLOR[a], COLOR_UNDERWATER_TINT, TINT_ALPHA_SNAKE * 0.9f);
        }
        snakePalettesInitialized = true;
    }

    static {
        initSnakePalettes();
    }


    // 2. Bờ nước có bọt (Shoreline Foam)
    public static final float FOAM_LINE_WIDTH = 1.0f;              // Độ dày đường bọt nước (1px mảnh)
    public static final double FOAM_FREQUENCY = 3.2;               // Tần số sóng bọt nhấp nhô (rad/s)
    public static final double FOAM_AMPLITUDE = 1.4;               // Biên độ sóng bọt dạt vào bờ (1-2px)
    public static final int FOAM_ALPHA = 135;                      // Độ sáng bọt trắng mờ dịu tự nhiên (0-255)
    public static final Color COLOR_WATER_FOAM = new Color(205, 240, 250, FOAM_ALPHA);
    public static final Color COLOR_OCEAN_FOAM = new Color(230, 252, 255, FOAM_ALPHA);

    // 3. Cỏ, bụi và cây đung đưa (Swaying Vegetation)
    public static final int SWAY_STRIP_COUNT = 5;                  // Số dải ngang cắt sprite (4-6 dải)
    public static final double SWAY_FREQUENCY = 2.4;               // Tần số đung đưa theo gió (rad/s)
    public static final double SWAY_MAX_AMPLITUDE = 2.2;           // Biên độ dịch ngang tối đa ở ngọn (1-3px logic)

    // 4. Gợn nước quanh rắn khi bơi (Swimming Water Ripples)
    public static final double RIPPLE_SPAWN_INTERVAL = 0.15;       // Chu kỳ sinh vòng sóng khi bơi (0.15s một vòng)
    public static final int RIPPLE_POOL_SIZE = 40;                 // Kích thước mảng tái sử dụng vòng sóng (tránh tạo object)
    public static final double RIPPLE_LIFETIME = 0.85;             // Thời gian tồn tại của mỗi vòng gợn sóng (giây)
    public static final double RIPPLE_START_RADIUS = 5.0;          // Bán kính ban đầu ở đầu rắn (px)
    public static final double RIPPLE_MAX_RADIUS = 28.0;           // Bán kính lan rộng cực đại (px)
    public static final float RIPPLE_MAX_ALPHA = 0.55f;            // Độ mờ ban đầu của gợn sóng
    public static final Color COLOR_WATER_RIPPLE = new Color(195, 240, 255);
    public static final Color COLOR_OCEAN_RIPPLE = new Color(225, 252, 255);

    // Stroke và Màu sắc dựng sẵn (Zero-allocation)
    public static final Stroke STROKE_FOAM = new BasicStroke(FOAM_LINE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    public static final Stroke STROKE_RIPPLE = new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    public static final Stroke STROKE_CAP_ROUND_1_0 = new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    public static final Stroke STROKE_CAP_ROUND_1_2 = new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    public static final Stroke STROKE_CAP_ROUND_1_4 = new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    public static final Stroke STROKE_CAP_ROUND_1_6 = new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    public static final Stroke STROKE_CAP_ROUND_1_8 = new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    public static final Stroke STROKE_CAP_ROUND_2_0 = new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    public static final Stroke STROKE_CAP_ROUND_2_2 = new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    public static final Stroke STROKE_CAP_ROUND_2_4 = new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    public static final Stroke STROKE_CAP_ROUND_2_6 = new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    public static final Stroke STROKE_CAP_ROUND_2_8 = new BasicStroke(2.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    public static final Stroke STROKE_CAP_ROUND_3_0 = new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    public static final Stroke STROKE_CAP_ROUND_3_2 = new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    public static final Stroke STROKE_CAP_ROUND_3_4 = new BasicStroke(3.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    public static final Stroke STROKE_CAP_ROUND_3_6 = new BasicStroke(3.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    public static final Stroke STROKE_CAP_ROUND_3_8 = new BasicStroke(3.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    public static final Stroke STROKE_CAP_ROUND_4_8 = new BasicStroke(4.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    public static final Stroke STROKE_CAP_ROUND_5_0 = new BasicStroke(5.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    public static final Stroke STROKE_CAP_ROUND_5_2 = new BasicStroke(5.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    public static final Stroke STROKE_CAP_ROUND_6_8 = new BasicStroke(6.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    public static final Stroke STROKE_CAP_ROUND_7_2 = new BasicStroke(7.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    public static final Stroke STROKE_PLAIN_1_0 = new BasicStroke(1.0f);
    public static final Stroke STROKE_PLAIN_1_5 = new BasicStroke(1.5f);
    public static final Stroke STROKE_PLAIN_1_8 = new BasicStroke(1.8f);
    public static final Stroke STROKE_PLAIN_2_0 = new BasicStroke(2.0f);
    public static final Stroke STROKE_PLAIN_2_5 = new BasicStroke(2.5f);
    public static final Stroke STROKE_PLAIN_3_0 = new BasicStroke(3.0f);

    private static final Stroke[] STROKE_CACHE_ROUND = new Stroke[40];
    static {
        for (int i = 0; i < STROKE_CACHE_ROUND.length; i++) {
            STROKE_CACHE_ROUND[i] = new BasicStroke(Math.max(0.5f, i * 0.5f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        }
    }

    public static Stroke getRoundStroke(float width) {
        int idx = Math.round(width * 2.0f);
        if (idx >= 0 && idx < STROKE_CACHE_ROUND.length) {
            return STROKE_CACHE_ROUND[idx];
        }
        return new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }

    // ==========================================
    // CÁC HẰNG SỐ ĐỒ HỌA, ÁNH SÁNG & BÓNG ĐỔ (SHADOWS & LIGHTING)
    // ==========================================
    public static final float SHADOW_WIDTH_RATIO = 0.62f;       // Tỉ lệ chiều rộng bóng = 55% - 70% chiều rộng chân sprite (0.62)
    public static final float SHADOW_HEIGHT_RATIO = 0.32f;      // Tỉ lệ chiều cao bóng = 30% - 35% chiều rộng bóng (0.32)
    public static final int SHADOW_OFFSET_X = 4;                // Độ lệch bóng trục X (lệch nhẹ sang phải đồng nhất)
    public static final int SHADOW_OFFSET_Y = 5;                // Độ lệch bóng trục Y (lệch nhẹ xuống dưới đồng nhất)
    public static final float SHADOW_LAND_MAX_ALPHA = 0.22f;    // Alpha tối đa trên cạn (0.18 - 0.28, dịu mắt)
    public static final float SHADOW_WATER_MAX_ALPHA = 0.12f;   // Alpha tối đa dưới nước (0.10 - 0.15, mờ nhẹ)
    public static final Color SHADOW_LAND_COLOR = new Color(10, 18, 14);     // Màu bóng trên cạn (đen pha rêu đất)
    public static final Color SHADOW_WATER_COLOR = new Color(6, 32, 42);     // Màu bóng dưới nước (xanh teal đậm)
    public static final float SHADOW_ALPHA = SHADOW_LAND_MAX_ALPHA;          // Tương thích ngược
    public static final Color SHADOW_COLOR = new Color(10, 18, 14, (int) (SHADOW_LAND_MAX_ALPHA * 255)); // Tương thích ngược

    public static final float LIGHT_RADIUS = 180.0f;      // Bán kính ánh sáng mềm quanh đầu rắn (px)
    public static final int AMBIENT_DARKNESS_ALPHA = 35;   // Cường độ tối môi trường (0-255, hằng số gắn với chu kỳ ngày/đêm)
    public static final int VIGNETTE_MAX_ALPHA = 90;       // Độ tối tối đa ở viền màn hình (0-255, tối dần ra viền)
    public static final float TREE_OCCLUSION_ALPHA = 0.60f;// Độ mờ cây khi rắn đi sau cây lớn (0.6)

    // Màu sắc và Composite Zero-Allocation tối ưu hóa hiệu năng render loop
    public static final Color COLOR_AMBIENT_DARKNESS = new Color(6, 12, 22, AMBIENT_DARKNESS_ALPHA);
    public static final Color COLOR_SUNLIGHT_RAY = new Color(186, 230, 253);
    public static final Color COLOR_BUBBLE_OUTLINE = new Color(125, 211, 252, 170);
    public static final Color COLOR_BUBBLE_FILL = new Color(224, 242, 254, 80);
    public static final Color COLOR_PLANKTON_1 = new Color(56, 189, 248);
    public static final Color COLOR_PLANKTON_2 = new Color(165, 243, 252);
    public static final Color COLOR_REEF_BUBBLE_1 = new Color(186, 230, 253, 140);
    public static final Color COLOR_REEF_BUBBLE_2 = new Color(240, 249, 255, 170);
    public static final Color COLOR_REEF_CAUSTICS = new Color(210, 248, 255);
    public static final Color COLOR_REEF_CAUSTICS_GLINT = new Color(255, 255, 255, 210);

    public static final Composite COMPOSITE_SUNLIGHT = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.12f);
    public static final Composite COMPOSITE_BUBBLE = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.38f);
    public static final Composite COMPOSITE_PLANKTON = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f);
    public static final Composite COMPOSITE_TREE_OCCLUSION = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, TREE_OCCLUSION_ALPHA);
    public static final Composite COMPOSITE_CAUSTICS_1 = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, WATER_CAUSTICS_ALPHA_1);
    public static final Composite COMPOSITE_CAUSTICS_2 = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, WATER_CAUSTICS_ALPHA_2);

    public enum TimeOfDay {
        DAY,        // Ban ngày: trong trẻo (alpha = 0)
        AFTERNOON, // Chiều tà: ấm áp vàng cam (#f59e0b)
        NIGHT       // Ban đêm: xanh lạnh thẳm (#0f173e)
    }

    public static TimeOfDay currentTimeOfDay = TimeOfDay.DAY;

    // =========================================================================
    // HẰNG SỐ CẤU HÌNH HUD, FONT BITMAP & HIỆU ỨNG GIAO DIỆN PIXEL-ART
    // =========================================================================
    public static final float HUD_BG_ALPHA = 0.88f;
    public static final float OBJECTIVE_BASE_ALPHA = 0.50f;
    public static final float OBJECTIVE_IDLE_ALPHA = 0.30f;
    public static final double OBJECTIVE_FADE_DELAY = 4.0; // Giây trước khi tự mờ dần
    public static final double PROXIMITY_LABEL_RADIUS = 150.0; // Bán kính hiển thị nhãn thế giới (px)

    public static final Color HUD_HP_FILL_HIGH = new Color(34, 197, 94);
    public static final Color HUD_HP_FILL_MID = new Color(234, 179, 8);
    public static final Color HUD_HP_FILL_LOW = new Color(239, 68, 68);
    public static final Color HUD_HP_LAG_COLOR = new Color(254, 202, 202, 220); // Thanh tụt máu sáng chậm
    public static final Color HUD_MP_FILL = new Color(56, 189, 248);
    public static final Color HUD_STM_FILL = new Color(250, 204, 21);
    public static final Color HUD_EXP_FILL = new Color(168, 85, 247);
    public static final Color HUD_EXP_GLOW = new Color(243, 232, 255);
    public static final Color HUD_TEXT_OUTLINE = new Color(15, 23, 42, 235);
    public static final Color HUD_BAR_BG = new Color(15, 23, 42, 190);
    public static final Color HUD_BAR_BORDER = new Color(71, 85, 105, 200);

    // Font Bitmap Pixel-Art (Press Start 2P / Silkscreen / Fallback)
    public static final Font BASE_PIXEL_FONT;
    public static final boolean HAS_PIXEL_FONT;

    public static final Font FONT_PIXEL_TITLE;
    public static final Font FONT_PIXEL_HUD;
    public static final Font FONT_PIXEL_LABEL;
    public static final Font FONT_PIXEL_NUMBER;
    public static final Font FONT_PIXEL_SMALL;

    static {
        Font loaded = null;
        String[] possibleFiles = {
            "resources/fonts/PressStart2P-Regular.ttf",
            "resources/PressStart2P-Regular.ttf",
            "assets/fonts/PressStart2P-Regular.ttf",
            "assets/PressStart2P-Regular.ttf",
            "resources/fonts/Silkscreen-Regular.ttf",
            "resources/Silkscreen-Regular.ttf"
        };
        for (String path : possibleFiles) {
            try {
                File f = new File(path);
                if (f.exists() && f.length() > 0) {
                    loaded = Font.createFont(Font.TRUETYPE_FONT, f);
                    GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(loaded);
                    break;
                }
            } catch (Exception ignored) {}
        }
        if (loaded == null) {
            String[] resPaths = {
                "/resources/fonts/PressStart2P-Regular.ttf",
                "/resources/PressStart2P-Regular.ttf",
                "/assets/fonts/PressStart2P-Regular.ttf",
                "/assets/PressStart2P-Regular.ttf"
            };
            for (String rp : resPaths) {
                try (InputStream is = LayeredRenderer.class.getResourceAsStream(rp)) {
                    if (is != null) {
                        loaded = Font.createFont(Font.TRUETYPE_FONT, is);
                        GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(loaded);
                        break;
                    }
                } catch (Exception ignored) {}
            }
        }

        if (loaded != null) {
            BASE_PIXEL_FONT = loaded;
            HAS_PIXEL_FONT = true;
            FONT_PIXEL_TITLE = BASE_PIXEL_FONT.deriveFont(Font.BOLD, 12f);
            FONT_PIXEL_HUD = BASE_PIXEL_FONT.deriveFont(Font.BOLD, 9f);
            FONT_PIXEL_LABEL = BASE_PIXEL_FONT.deriveFont(Font.BOLD, 8f);
            FONT_PIXEL_NUMBER = BASE_PIXEL_FONT.deriveFont(Font.BOLD, 7f);
            FONT_PIXEL_SMALL = BASE_PIXEL_FONT.deriveFont(Font.PLAIN, 7f);
        } else {
            BASE_PIXEL_FONT = new Font(Font.MONOSPACED, Font.BOLD, 12);
            HAS_PIXEL_FONT = false;
            FONT_PIXEL_TITLE = new Font(Font.MONOSPACED, Font.BOLD, 14);
            FONT_PIXEL_HUD = new Font(Font.MONOSPACED, Font.BOLD, 12);
            FONT_PIXEL_LABEL = new Font(Font.MONOSPACED, Font.BOLD, 11);
            FONT_PIXEL_NUMBER = new Font(Font.MONOSPACED, Font.BOLD, 10);
            FONT_PIXEL_SMALL = new Font(Font.MONOSPACED, Font.PLAIN, 10);
        }
    }

    // =========================================================================
    // HỆ THỐNG FONT CHỮ UI CHUẨN (HỖ TRỢ 100% TIẾNG VIỆT & BIỂU TƯỢNG, KHÔNG LỖI TOFU)
    // =========================================================================
    public static final String FONT_FAMILY = Font.SANS_SERIF;
    public static final Font FONT_TITLE_HUGE = new Font(FONT_FAMILY, Font.BOLD, 42);
    public static final Font FONT_TITLE_LARGE = new Font(FONT_FAMILY, Font.BOLD, 30);
    public static final Font FONT_TITLE_MODAL = new Font(FONT_FAMILY, Font.BOLD, 21);
    public static final Font FONT_TITLE_SECTION = new Font(FONT_FAMILY, Font.BOLD, 16);
    public static final Font FONT_TITLE_CARD = new Font(FONT_FAMILY, Font.BOLD, 14);
    public static final Font FONT_BUTTON = new Font(FONT_FAMILY, Font.BOLD, 14);
    public static final Font FONT_HUD_BOLD = new Font(FONT_FAMILY, Font.BOLD, 13);
    public static final Font FONT_HUD_LABEL = new Font(FONT_FAMILY, Font.BOLD, 12);
    public static final Font FONT_BODY_BOLD = new Font(FONT_FAMILY, Font.BOLD, 12);
    public static final Font FONT_BODY = new Font(FONT_FAMILY, Font.PLAIN, 12);
    public static final Font FONT_BADGE = new Font(FONT_FAMILY, Font.BOLD, 11);
    public static final Font FONT_BAR_NUMBER = new Font(FONT_FAMILY, Font.BOLD, 10);
    public static final Font FONT_SMALL_BOLD = new Font(FONT_FAMILY, Font.BOLD, 10);
    public static final Font FONT_SMALL_REGULAR = new Font(FONT_FAMILY, Font.PLAIN, 11);

    /**
     * Viền chữ 1px: vẽ chữ màu tối lệch 4 hướng trước (trên, dưới, trái, phải), rồi vẽ chữ chính.
     */
    public static void drawOutlinedString(Graphics2D g, String text, int x, int y, Color textColor, Color outlineColor) {
        if (text == null || text.isEmpty()) return;
        g.setColor(outlineColor);
        g.drawString(text, x - 1, y);
        g.drawString(text, x + 1, y);
        g.drawString(text, x, y - 1);
        g.drawString(text, x, y + 1);
        g.setColor(textColor);
        g.drawString(text, x, y);
    }

    /**
     * Vẽ chữ pixel kèm viền 1px, tự động dùng fallback nếu có ký tự có dấu tiếng Việt
     * mà font pixel không hỗ trợ, đảm bảo không bao giờ lỗi tofu.
     */
    public static void drawOutlinedPixelString(Graphics2D g, String text, int x, int y, Color textColor, Color outlineColor, Font pixelFont, Font fallbackFont) {
        if (text == null || text.isEmpty()) return;
        Font prev = g.getFont();
        Font chosen = (pixelFont != null && pixelFont.canDisplayUpTo(text) == -1) ? pixelFont : fallbackFont;
        g.setFont(chosen);
        drawOutlinedString(g, text, x, y, textColor, outlineColor);
        g.setFont(prev);
    }

    // State cho hiệu ứng động của HUD
    private double hudLagHp = -1;
    private double hudLastExp = -1;
    private double hudExpGlowTimer = 0.0;
    private int hudLastBossesKilled = -1;
    private double hudObjectiveFadeTimer = OBJECTIVE_FADE_DELAY;
    private float hudObjectiveAlpha = OBJECTIVE_BASE_ALPHA;
    private long hudLastTimeNs = 0;

    // Bộ nhớ đệm Vignette & Lớp ánh sáng môi trường tương thích VRAM
    private BufferedImage cachedVignette = null;
    private int cachedVignetteW = 0;
    private int cachedVignetteH = 0;

    // Mặt nạ ánh sáng tròn mềm pre-bake tương thích VRAM
    private static BufferedImage cachedLightMask = null;

    // Sprite bong bóng khí biển sâu pre-bake (5 kích cỡ)
    private static final BufferedImage[] cachedOceanBubbles = new BufferedImage[5];

    // Mảng tọa độ đa giác vệt nắng tái sử dụng (0 allocation)
    private final int[] sunlightX = new int[4];
    private final int[] sunlightY = new int[4];

    // Bộ nhớ đệm Sprite thực vật & Caustics mặt nước
    private static BufferedImage causticsTexture = null;
    private static BufferedImage causticsTexture1 = null;
    private static BufferedImage causticsTexture2 = null;
    private static BufferedImage cachedSunbeamTexture = null;
    private final AffineTransform sunbeamTransform = new AffineTransform();
    private static final BufferedImage[] cachedBushVariants = new BufferedImage[4];
    private static final BufferedImage[][] cachedPlantFrames = new BufferedImage[4][PLANT_FRAME_COUNT];
    private static final BufferedImage[][] cachedReefPlantFrames = new BufferedImage[5][REEF_PLANT_FRAME_COUNT];

    // Mảng tọa độ tái sử dụng cho hệ thống hiển thị thân rắn Catmull-Rom liền mạch (Zero-allocation)
    private static final int MAX_SNAKE_SPLINE_PTS = 512;
    private final double[] snakeCtrlX = new double[128];
    private final double[] snakeCtrlY = new double[128];
    private final double[] snakeSplineX = new double[MAX_SNAKE_SPLINE_PTS];
    private final double[] snakeSplineY = new double[MAX_SNAKE_SPLINE_PTS];
    private final double[] snakeSplineW = new double[MAX_SNAKE_SPLINE_PTS];
    private final double[] snakeNormX = new double[MAX_SNAKE_SPLINE_PTS];
    private final double[] snakeNormY = new double[MAX_SNAKE_SPLINE_PTS];
    private final double[] snakeLeftX = new double[MAX_SNAKE_SPLINE_PTS];
    private final double[] snakeLeftY = new double[MAX_SNAKE_SPLINE_PTS];
    private final double[] snakeRightX = new double[MAX_SNAKE_SPLINE_PTS];
    private final double[] snakeRightY = new double[MAX_SNAKE_SPLINE_PTS];
    private final int[] quadPolyX = new int[4];
    private final int[] quadPolyY = new int[4];
    private final int[] headBorderX = new int[8];
    private final int[] headBorderY = new int[8];
    private final int[] headInnerX = new int[8];
    private final int[] headInnerY = new int[8];

    // Đối tượng Path2D tái sử dụng cho hệ thống vẽ thân rắn 3 lớp nét tròn (Zero-allocation)
    private final Path2D.Double snakeBodyPath = new Path2D.Double();
    private final Path2D.Double snakeHighlightPath = new Path2D.Double();

    // Bộ nhớ đệm bóng elip mềm tạo sẵn bằng RadialGradientPaint (Zero-allocation khi render loop chạy)
    private static final int[] SHADOW_BUCKET_WIDTHS = { 16, 24, 36, 52, 76, 110, 160 };
    private static BufferedImage[] cachedLandShadows = null;
    private static BufferedImage[] cachedWaterShadows = null;

    public static synchronized void initShadowCache() {
        if (cachedLandShadows != null) return;
        cachedLandShadows = new BufferedImage[SHADOW_BUCKET_WIDTHS.length];
        cachedWaterShadows = new BufferedImage[SHADOW_BUCKET_WIDTHS.length];

        float[] fractions = { 0.0f, 0.45f, 0.80f, 1.0f };

        // Bảng màu bóng trên cạn (Land)
        Color[] landColors = {
            new Color(SHADOW_LAND_COLOR.getRed(), SHADOW_LAND_COLOR.getGreen(), SHADOW_LAND_COLOR.getBlue(), (int) (255 * SHADOW_LAND_MAX_ALPHA)),
            new Color(SHADOW_LAND_COLOR.getRed(), SHADOW_LAND_COLOR.getGreen(), SHADOW_LAND_COLOR.getBlue(), (int) (255 * SHADOW_LAND_MAX_ALPHA * 0.80f)),
            new Color(SHADOW_LAND_COLOR.getRed(), SHADOW_LAND_COLOR.getGreen(), SHADOW_LAND_COLOR.getBlue(), (int) (255 * SHADOW_LAND_MAX_ALPHA * 0.35f)),
            new Color(SHADOW_LAND_COLOR.getRed(), SHADOW_LAND_COLOR.getGreen(), SHADOW_LAND_COLOR.getBlue(), 0)
        };

        // Bảng màu bóng dưới nước (Water teal dịu)
        Color[] waterColors = {
            new Color(SHADOW_WATER_COLOR.getRed(), SHADOW_WATER_COLOR.getGreen(), SHADOW_WATER_COLOR.getBlue(), (int) (255 * SHADOW_WATER_MAX_ALPHA)),
            new Color(SHADOW_WATER_COLOR.getRed(), SHADOW_WATER_COLOR.getGreen(), SHADOW_WATER_COLOR.getBlue(), (int) (255 * SHADOW_WATER_MAX_ALPHA * 0.75f)),
            new Color(SHADOW_WATER_COLOR.getRed(), SHADOW_WATER_COLOR.getGreen(), SHADOW_WATER_COLOR.getBlue(), (int) (255 * SHADOW_WATER_MAX_ALPHA * 0.25f)),
            new Color(SHADOW_WATER_COLOR.getRed(), SHADOW_WATER_COLOR.getGreen(), SHADOW_WATER_COLOR.getBlue(), 0)
        };

        for (int i = 0; i < SHADOW_BUCKET_WIDTHS.length; i++) {
            int w = SHADOW_BUCKET_WIDTHS[i];
            int h = Math.max(4, (int) Math.round(w * SHADOW_HEIGHT_RATIO));

            cachedLandShadows[i] = createRadialShadowImage(w, h, fractions, landColors);
            cachedWaterShadows[i] = createRadialShadowImage(w, h, fractions, waterColors);
        }
    }

    private static BufferedImage createRadialShadowImage(int w, int h, float[] fractions, Color[] colors) {
        BufferedImage raw = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = raw.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        AffineTransform at = AffineTransform.getTranslateInstance(w / 2.0, h / 2.0);
        at.scale(w / 2.0, h / 2.0);

        RadialGradientPaint rgp = new RadialGradientPaint(
            new Point2D.Float(0, 0),
            1.0f,
            new Point2D.Float(0, 0),
            fractions,
            colors,
            MultipleGradientPaint.CycleMethod.NO_CYCLE,
            MultipleGradientPaint.ColorSpaceType.SRGB,
            at
        );
        g2.setPaint(rgp);
        g2.fillOval(0, 0, w, h);
        g2.dispose();

        BufferedImage comp = createCompatible(w, h, Transparency.TRANSLUCENT);
        Graphics2D cg = comp.createGraphics();
        cg.setComposite(AlphaComposite.Src);
        cg.drawImage(raw, 0, 0, null);
        cg.dispose();
        return comp;
    }

    public static void drawCachedShadow(Graphics2D g, double centerX, double centerY, int shadowW, int shadowH, boolean isWater) {
        if (cachedLandShadows == null) {
            initShadowCache();
        }
        BufferedImage[] cache = isWater ? cachedWaterShadows : cachedLandShadows;
        int bestIdx = 0;
        int minDiff = Integer.MAX_VALUE;
        for (int i = 0; i < SHADOW_BUCKET_WIDTHS.length; i++) {
            int diff = Math.abs(SHADOW_BUCKET_WIDTHS[i] - shadowW);
            if (diff < minDiff) {
                minDiff = diff;
                bestIdx = i;
            }
        }
        BufferedImage img = cache[bestIdx];
        int drawX = (int) Math.round(centerX - shadowW / 2.0);
        int drawY = (int) Math.round(centerY - shadowH / 2.0);
        g.drawImage(img, drawX, drawY, shadowW, shadowH, null);
    }

    // Bộ nhớ đệm Sprite & FX Boss Gấu (Khởi tạo 1 lần duy nhất)
    private static boolean bearSpritesInitialized = false;
    private static BufferedImage[][][] bearNormalFrames = null;    // [row][col][dir]
    private static BufferedImage[][][] bearEnragedFrames = null;   // [row][col][dir]
    private static BufferedImage[] bearDustFrames = null;          // [4]
    private static BufferedImage[] bearStreakFrames = null;        // [16]
    private static BufferedImage[][] bearBiteFrames = null;        // [5][16]
    private static BufferedImage cachedBearShadow = null;          // Elip mềm alpha 0.25 dưới gấu

    private final int[] bearTeleX = new int[16];
    private final int[] bearTeleY = new int[16];
    private final int[] snakeTeleX = new int[16];
    private final int[] snakeTeleY = new int[16];
    private boolean bearWindupTelegraphDrawn = false;
    private boolean snakeWindupTelegraphDrawn = false;

    public static synchronized void initBearSprites() {
        if (bearSpritesInitialized) return;

        BufferedImage normalSheet = loadBearAsset("bear_sheet.png");
        BufferedImage enragedSheet = loadBearAsset("bear_sheet_enraged.png");
        if (enragedSheet == null) enragedSheet = normalSheet;

        bearNormalFrames = preBakeBearSheet(normalSheet);
        bearEnragedFrames = preBakeBearSheet(enragedSheet);

        // 1. Dust FX (128x32 = 4 frames x 32x32 -> scale x2 = 64x64)
        BufferedImage dustSheet = loadBearAsset("bear_fx_dust.png");
        bearDustFrames = new BufferedImage[4];
        if (dustSheet != null) {
            int dw = 32 * BEAR_SPRITE_SCALE;
            int dh = 32 * BEAR_SPRITE_SCALE;
            for (int f = 0; f < 4; f++) {
                BufferedImage sub = dustSheet.getSubimage(f * 32, 0, 32, 32);
                BufferedImage scaled = createCompatible(dw, dh, Transparency.TRANSLUCENT);
                Graphics2D gs = scaled.createGraphics();
                gs.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                gs.drawImage(sub, 0, 0, dw, dh, null);
                gs.dispose();
                bearDustFrames[f] = scaled;
            }
        }

        // 2. Wind Streak FX (64x24 = 1 frame -> rotate 16 dirs around center (32,12) then scale x2)
        BufferedImage streakRaw = loadBearAsset("bear_fx_streak.png");
        bearStreakFrames = new BufferedImage[BEAR_DIRECTIONS];
        if (streakRaw != null) {
            for (int d = 0; d < BEAR_DIRECTIONS; d++) {
                bearStreakFrames[d] = rotateAndScaleSprite(streakRaw, 32, 12, d, BEAR_STREAK_UNSCALED);
            }
        }

        // 3. Bite Slash FX (240x48 = 5 frames x 48x48 -> rotate 16 dirs around center (24,24) then scale x2)
        BufferedImage biteSheet = loadBearAsset("bear_fx_bite.png");
        bearBiteFrames = new BufferedImage[5][BEAR_DIRECTIONS];
        if (biteSheet != null) {
            for (int f = 0; f < 5; f++) {
                BufferedImage sub = biteSheet.getSubimage(f * 48, 0, 48, 48);
                for (int d = 0; d < BEAR_DIRECTIONS; d++) {
                    bearBiteFrames[f][d] = rotateAndScaleSprite(sub, 24, 24, d, BEAR_BITE_UNSCALED);
                }
            }
        }

        // 4. Bóng elip mềm alpha 0.25 kích thước theo sprite gấu
        float[] bearShadowFrac = { 0.0f, 0.50f, 0.85f, 1.0f };
        Color[] bearShadowCols = {
            new Color(SHADOW_LAND_COLOR.getRed(), SHADOW_LAND_COLOR.getGreen(), SHADOW_LAND_COLOR.getBlue(), Math.round(255 * BEAR_SHADOW_ALPHA)),
            new Color(SHADOW_LAND_COLOR.getRed(), SHADOW_LAND_COLOR.getGreen(), SHADOW_LAND_COLOR.getBlue(), Math.round(255 * BEAR_SHADOW_ALPHA * 0.78f)),
            new Color(SHADOW_LAND_COLOR.getRed(), SHADOW_LAND_COLOR.getGreen(), SHADOW_LAND_COLOR.getBlue(), Math.round(255 * BEAR_SHADOW_ALPHA * 0.30f)),
            new Color(SHADOW_LAND_COLOR.getRed(), SHADOW_LAND_COLOR.getGreen(), SHADOW_LAND_COLOR.getBlue(), 0)
        };
        cachedBearShadow = createRadialShadowImage(BEAR_SHADOW_W, BEAR_SHADOW_H, bearShadowFrac, bearShadowCols);

        bearSpritesInitialized = true;
    }

    private static BufferedImage loadBearAsset(String fileName) {
        String[] paths = { "src/assets/bear/" + fileName, "assets/bear/" + fileName };
        for (String p : paths) {
            File f = new File(p);
            if (f.exists()) {
                try {
                    return ImageIO.read(f);
                } catch (Exception ignored) {}
            }
        }
        String[] resPaths = { "/assets/bear/" + fileName, "/src/assets/bear/" + fileName };
        for (String rp : resPaths) {
            try (InputStream is = LayeredRenderer.class.getResourceAsStream(rp)) {
                if (is != null) {
                    return ImageIO.read(is);
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static BufferedImage[][][] preBakeBearSheet(BufferedImage sheet) {
        BufferedImage[][][] out = new BufferedImage[BEAR_GRID_ROWS][BEAR_GRID_COLS][BEAR_DIRECTIONS];
        if (sheet == null) return out;
        for (int row = 0; row < BEAR_GRID_ROWS; row++) {
            for (int col = 0; col < BEAR_GRID_COLS; col++) {
                BufferedImage frame = sheet.getSubimage(col * BEAR_FRAME_W, row * BEAR_FRAME_H, BEAR_FRAME_W, BEAR_FRAME_H);
                for (int d = 0; d < BEAR_DIRECTIONS; d++) {
                    out[row][col][d] = rotateAndScaleSprite(frame, BEAR_PIVOT_X, BEAR_PIVOT_Y, d, BEAR_UNSCALED_ROT_SIZE);
                }
            }
        }
        return out;
    }

    private static BufferedImage rotateAndScaleSprite(BufferedImage src, int pivotX, int pivotY, int dirIdx, int unscaledCanvas) {
        double theta = dirIdx * (Math.PI * 2.0 / BEAR_DIRECTIONS);
        double halfUnscaled = unscaledCanvas / 2.0;

        BufferedImage rotBuf = new BufferedImage(unscaledCanvas, unscaledCanvas, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gr = rotBuf.createGraphics();
        gr.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        AffineTransform at = new AffineTransform();
        at.translate(halfUnscaled, halfUnscaled);
        at.rotate(theta);
        at.translate(-pivotX, -pivotY);
        gr.drawImage(src, at, null);
        gr.dispose();

        int scaledSize = unscaledCanvas * BEAR_SPRITE_SCALE;
        BufferedImage scaled = createCompatible(scaledSize, scaledSize, Transparency.TRANSLUCENT);
        Graphics2D gs = scaled.createGraphics();
        gs.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        gs.drawImage(rotBuf, 0, 0, scaledSize, scaledSize, null);
        gs.dispose();
        return scaled;
    }

    private static int angleToBearDirIndex(double angleRad) {
        double twoPi = Math.PI * 2.0;
        double norm = ((angleRad % twoPi) + twoPi) % twoPi;
        return (int) Math.round(norm / (twoPi / BEAR_DIRECTIONS)) % BEAR_DIRECTIONS;
    }

    // Helper tạo ảnh tương thích với phần cứng đồ họa (VRAM)
    public static GraphicsConfiguration getScreenConfig() {
        if (!GraphicsEnvironment.isHeadless()) {
            try {
                return GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getDefaultScreenDevice().getDefaultConfiguration();
            } catch (Exception ignored) {}
        }
        return null;
    }

    public static BufferedImage createCompatible(int w, int h, int transparency) {
        GraphicsConfiguration gc = getScreenConfig();
        if (gc != null) {
            return gc.createCompatibleImage(w, h, transparency);
        }
        int type = (transparency == Transparency.OPAQUE) ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        return new BufferedImage(w, h, type);
    }

    // Cấu trúc dữ liệu Vòng sóng nước tái sử dụng (Object Pool - 0 GC)
    public static class WaterRipple {
        public boolean active = false;
        public double x, y;
        public double age = 0.0;
    }
    private final WaterRipple[] ripplePool = new WaterRipple[RIPPLE_POOL_SIZE];
    private double rippleSpawnTimer = 0.0;

    // Cấu trúc dữ liệu Y-Sort tái sử dụng (0 Object allocation mỗi frame)
    private static class RenderableItem implements Comparable<RenderableItem> {
        double y;
        int type; // 0: prop, 1: prey, 2: croc, 3: player, 4: ai, 5: boss
        Object ref;

        @Override
        public int compareTo(RenderableItem o) {
            return Double.compare(this.y, o.y);
        }
    }
    private RenderableItem[] renderablePool = new RenderableItem[1024];
    private int renderableCount = 0;

    // Mảng tọa độ đa giác chim hải âu tái sử dụng (0 allocation)
    private final int[] seagullWxL = new int[5];
    private final int[] seagullWyL = new int[5];
    private final int[] seagullWxLin = new int[5];
    private final int[] seagullWyLin = new int[5];
    private final int[] seagullWxR = new int[5];
    private final int[] seagullWyR = new int[5];
    private final int[] seagullWxRin = new int[5];
    private final int[] seagullWyRin = new int[5];
    private final int[] seagullTailX = new int[3];
    private final int[] seagullTailY = new int[3];
    private final int[] seagullBeakX = new int[3];
    private final int[] seagullBeakY = new int[3];

    // Bộ nhớ đệm tái sử dụng cho dải rong biển / rạn san hô (0 GC)
    private static final int RIBBON_SEGMENTS = 7;
    private final double[] ribbonPx = new double[RIBBON_SEGMENTS + 1];
    private final double[] ribbonPy = new double[RIBBON_SEGMENTS + 1];
    private final double[] ribbonHw = new double[RIBBON_SEGMENTS + 1];
    private final double[] ribbonLx = new double[RIBBON_SEGMENTS + 1];
    private final double[] ribbonLy = new double[RIBBON_SEGMENTS + 1];
    private final double[] ribbonRx = new double[RIBBON_SEGMENTS + 1];
    private final double[] ribbonRy = new double[RIBBON_SEGMENTS + 1];
    private final int[] ribbonRQx = new int[4];
    private final int[] ribbonRQy = new int[4];
    private final int[] ribbonLQx = new int[4];
    private final int[] ribbonLQy = new int[4];

    private final java.awt.geom.QuadCurve2D.Double reefCausticCurve1 = new java.awt.geom.QuadCurve2D.Double();
    private final java.awt.geom.QuadCurve2D.Double reefCausticCurve2 = new java.awt.geom.QuadCurve2D.Double();
    private final java.awt.geom.QuadCurve2D.Double reefCausticCurve3 = new java.awt.geom.QuadCurve2D.Double();

    private static final Color COLOR_SEAGULL_WING_BACK = new Color(148, 163, 184);
    private static final Color COLOR_SEAGULL_WING_INNER = new Color(248, 250, 252);
    private static final Color COLOR_SEAGULL_WING_TIP = new Color(30, 41, 59);
    private static final Color COLOR_SEAGULL_TAIL = new Color(241, 245, 249);
    private static final Color COLOR_SEAGULL_BODY_BORDER = new Color(203, 213, 225);
    private static final Color COLOR_SEAGULL_BEAK = new Color(250, 204, 21);
    private static final Color COLOR_SEAGULL_RED_SPOT = new Color(239, 68, 68);
    private static final Color COLOR_SEAGULL_EYE = new Color(15, 23, 42);
    private static final Color COLOR_SEAGULL_SWOOP_LINE = new Color(224, 242, 254, 180);

    private static class Cloud {
        double x, y, width, height, alpha;
        Color color;
        Cloud(double x, double y, double w, double h, double a) {
            this.x = x; this.y = y; this.width = w; this.height = h; this.alpha = a;
            this.color = new Color(15, 30, 22, (int) (a * 255));
        }
    }

    private final List<Cloud> clouds = new ArrayList<>();
    private double ambientTimer = 0.0;

    public LayeredRenderer() {
        // Khởi tạo các bộ đệm tái sử dụng
        for (int i = 0; i < RIPPLE_POOL_SIZE; i++) {
            ripplePool[i] = new WaterRipple();
        }
        for (int i = 0; i < renderablePool.length; i++) {
            renderablePool[i] = new RenderableItem();
        }

        initCausticsTexture();
        initPropSprites();
        initLightMask();
        initOceanBubbles();
        initBearSprites();

        for (int i = 0; i < Config.Environment.CLOUD_COUNT; i++) {
            clouds.add(new Cloud(
                (Math.random() - 0.5) * 3000,
                (Math.random() - 0.5) * 3000,
                240 + Math.random() * 200,
                140 + Math.random() * 100,
                0.12 + Math.random() * 0.12
            ));
        }
    }

    public void renderWorldLayers(Graphics2D g2d, int viewW, int viewH, Camera camera,
                                  List<Chunk> activeChunks, PlayerSnake player,
                                  List<AISnake> aiSnakes, List<Boss> bosses, List<Prey> preys,
                                  List<Crocodile> crocodiles,
                                  Eagle eagle,
                                  List<PoisonBolt> poisonBolts,
                                  ParticleSystem particles, FloatingTextManager texts, double dt, int stageId) {
        // Cập nhật thời gian môi trường đồng bộ
        ambientTimer += dt;

        // Reset throttle nướng ảnh tĩnh mảng cỏ và cụm san hô trong frame hiện tại
        GrassPatch.resetFrameBakeCount();
        CoralReef.resetFrameBakeCount();

        // Cập nhật và sinh gợn nước quanh rắn khi bơi
        updateWaterRipples(player, dt);

        // ==========================================
        // LỚP 1: GROUND & DECALS (Từ bộ nhớ đệm Chunk)
        // ==========================================
        for (Chunk chunk : activeChunks) {
            Point2D.Double screen = camera.worldToScreen(chunk.getWorldX(), chunk.getWorldY());
            g2d.drawImage(chunk.getCachedImage(), (int) Math.round(screen.x), (int) Math.round(screen.y), null);
        }

        // ==========================================
        // LỚP 1.5: MẶT NƯỚC CHUYỂN ĐỘNG & BỌT BỜ BIỂN (Dynamic Caustics & Shoreline Foam)
        // ==========================================
        renderWaterSurfaceAndFoam(g2d, viewW, viewH, camera, activeChunks, ambientTimer, stageId);

        // ==========================================
        // LỚP 1.8: GỢN NƯỚC QUANH RẮN KHI BƠI (Swimming Water Ripples)
        // ==========================================
        renderWaterRipples(g2d, camera, stageId);

        // ==========================================
        // LỚP 2.5: ĐÀN CÁ HẬU CẢNH BIỂN SÂU (Map 2 Background Fish Schools)
        // ==========================================
        if (stageId == 2) {
            renderBackgroundFishSchools(g2d, viewW, viewH, ambientTimer);
        }

        // ==========================================
        // LỚP 3: SHADOWS (Bóng elip nhất quán dưới toàn bộ thực thể & vật thể)
        // ==========================================
        bearWindupTelegraphDrawn = false;
        snakeWindupTelegraphDrawn = false;
        renderShadows(g2d, camera, activeChunks, player, aiSnakes, bosses, preys, crocodiles, eagle, stageId);
        bearWindupTelegraphDrawn = true;
        snakeWindupTelegraphDrawn = true;

        // ==========================================
        // LỚP 4: ENTITIES & PROPS (Sắp xếp theo trục Y của chân/đáy sprite - Y-SORT)
        // ==========================================
        renderYSorted(g2d, camera, activeChunks, player, aiSnakes, bosses, preys, crocodiles, stageId);
        bearWindupTelegraphDrawn = false;
        snakeWindupTelegraphDrawn = false;

        // ==========================================
        // LỚP 4.2: SINH VẬT RẶNG SAN HÔ (Map 2 Reef Fish & Crevice Bubbles)
        // ==========================================
        if (stageId == 2) {
            renderReefLife(g2d, camera, activeChunks, ambientTimer);
        }

        // ==========================================
        // LỚP 5: CANOPY / AIR (Mây Parallax, Tia Độc, Bướm, Đại bàng, Hạt, Sương mù)
        // ==========================================
        renderAirLayer(g2d, viewW, viewH, camera, eagle, preys, poisonBolts, particles, dt, stageId);

        // ==========================================
        // LỚP 6A: TÔNG MÀU THEO THỜI GIAN
        // ==========================================
        renderTimeOfDayTint(g2d, viewW, viewH, currentTimeOfDay);

        // ==========================================
        // LỚP 6B: ÁNH SÁNG QUANH RẮN (AMBIENT DARKNESS + DstOut CUTOUT)
        // ==========================================
        renderAmbientLighting(g2d, viewW, viewH, camera, player);

        // ==========================================
        // LỚP 6C: POST-FX VIGNETTE (Cached RadialGradientPaint)
        // ==========================================
        renderVignette(g2d, viewW, viewH);

        // ==========================================
        // LỚP 6D: CHỮ BAY TRONG THẾ GIỚI
        // ==========================================
        texts.render(g2d, camera);
    }

    public void renderHUDLayers(Graphics2D g2d, int viewW, int viewH, Camera camera,
                                PlayerSnake player, List<AISnake> aiSnakes, List<Boss> bosses,
                                List<Prey> preys) {
        renderMinimap(g2d, viewW, viewH, player, aiSnakes, bosses, preys);
        renderOffscreenBossIndicator(g2d, viewW, viewH, camera, player, bosses);
        renderHUD(g2d, viewW, viewH, player, bosses, preys != null ? preys.size() : 0);
    }

    public void render(Graphics2D g2d, int viewW, int viewH, Camera camera,
                       List<Chunk> activeChunks, PlayerSnake player,
                       List<AISnake> aiSnakes, List<Boss> bosses, List<Prey> preys,
                       List<Crocodile> crocodiles,
                       Eagle eagle,
                       List<PoisonBolt> poisonBolts,
                       ParticleSystem particles, FloatingTextManager texts, double dt, int stageId) {
        renderWorldLayers(g2d, viewW, viewH, camera, activeChunks, player, aiSnakes, bosses, preys, crocodiles, eagle, poisonBolts, particles, texts, dt, stageId);
        renderHUDLayers(g2d, viewW, viewH, camera, player, aiSnakes, bosses, preys);
    }

    public void render(Graphics2D g2d, int viewW, int viewH, Camera camera,
                       List<Chunk> activeChunks, PlayerSnake player,
                       List<AISnake> aiSnakes, List<Boss> bosses, List<Prey> preys,
                       List<Crocodile> crocodiles,
                       Eagle eagle,
                       List<PoisonBolt> poisonBolts,
                       ParticleSystem particles, FloatingTextManager texts, double dt) {
        render(g2d, viewW, viewH, camera, activeChunks, player, aiSnakes, bosses, preys, crocodiles, eagle, poisonBolts, particles, texts, dt, 1);
    }

    public void render(Graphics2D g2d, int viewW, int viewH, Camera camera,
                       List<Chunk> activeChunks, PlayerSnake player,
                       List<AISnake> aiSnakes, List<Boss> bosses, List<Prey> preys, Eagle eagle,
                       List<PoisonBolt> poisonBolts,
                       ParticleSystem particles, FloatingTextManager texts, double dt) {
        render(g2d, viewW, viewH, camera, activeChunks, player, aiSnakes, bosses, preys, null, eagle, poisonBolts, particles, texts, dt, 1);
    }

    private void renderShadows(Graphics2D g, Camera camera, List<Chunk> chunks,
                               PlayerSnake player, List<AISnake> aiSnakes, List<Boss> bosses, List<Prey> preys,
                               List<Crocodile> crocodiles, Eagle eagle, int stageId) {
        boolean isWater = (stageId == 2);
        int offX = SHADOW_OFFSET_X;
        int offY = SHADOW_OFFSET_Y;

        // 1. Bóng của các vật thể (Props: Cây, Bụi, Đá, Hang, Cạm bẫy, Tường)
        for (Chunk c : chunks) {
            for (WorldProp p : c.getProps()) {
                if (!camera.isVisible(p.getX(), p.getY(), 72)) continue;
                int pw = p.getWidth();
                int ph = p.getHeight();

                if (p.getType() == WorldProp.Type.TREE) {
                    // Chân thân cây: chiều rộng chân bằng ~50% bề rộng tán cây
                    int footW = (int) (pw * 0.52);
                    int sw = Math.round(footW * SHADOW_WIDTH_RATIO);
                    int sh = Math.max(4, Math.round(sw * SHADOW_HEIGHT_RATIO));
                    Point2D.Double pos = camera.worldToScreen(p.getX() + offX, p.getY() + ph * 0.35 + offY);
                    drawCachedShadow(g, pos.x, pos.y, sw, sh, isWater);
                } else if (p.getType() == WorldProp.Type.ROCK) {
                    int footW = (int) (pw * (isWater ? 0.65 : 0.70));
                    int sw = Math.round(footW * SHADOW_WIDTH_RATIO);
                    int sh = Math.max(4, Math.round(sw * SHADOW_HEIGHT_RATIO));
                    Point2D.Double pos = camera.worldToScreen(p.getX() + offX, p.getY() + ph * 0.30 + offY);
                    drawCachedShadow(g, pos.x, pos.y, sw, sh, isWater);
                } else if (p.getType() == WorldProp.Type.BUSH) {
                    // Không vẽ bóng đĩa lẻ cho cây/bụi ở cả Stage 1 và Stage 2 (dùng bóng chung của mảng/rạn)
                } else if (p.getType() == WorldProp.Type.CAVE) {
                    int footW = (int) (pw * 0.80);
                    int sw = Math.round(footW * SHADOW_WIDTH_RATIO);
                    int sh = Math.max(4, Math.round(sw * SHADOW_HEIGHT_RATIO));
                    Point2D.Double pos = camera.worldToScreen(p.getX() + offX, p.getY() + ph * 0.35 + offY);
                    drawCachedShadow(g, pos.x, pos.y, sw, sh, isWater);
                } else if (p.getType() == WorldProp.Type.TRAP) {
                    int footW = 24;
                    int sw = Math.round(footW * SHADOW_WIDTH_RATIO);
                    int sh = Math.max(4, Math.round(sw * SHADOW_HEIGHT_RATIO));
                    Point2D.Double pos = camera.worldToScreen(p.getX() + offX, p.getY() + 4 + offY);
                    drawCachedShadow(g, pos.x, pos.y, sw, sh, isWater);
                } else if (p.getType() == WorldProp.Type.WALL) {
                    int footW = (int) (pw * 0.80);
                    int sw = Math.round(footW * SHADOW_WIDTH_RATIO);
                    int sh = Math.max(4, Math.round(sw * SHADOW_HEIGHT_RATIO));
                    Point2D.Double pos = camera.worldToScreen(p.getX() + offX, p.getY() + ph * 0.35 + offY);
                    drawCachedShadow(g, pos.x, pos.y, sw, sh, isWater);
                }
            }
        }

        // 1.5. Nền tĩnh & Bóng chung mờ mềm duy nhất của Mảng Cỏ Rộng (Stage 1)
        if (!isWater) {
            for (Chunk c : chunks) {
                for (GrassPatch patch : c.getGrassPatches()) {
                    if (patch.isVisible(camera)) {
                        patch.renderStaticBase(g, camera);
                    }
                }
            }
        } else {
            // 1.6. Nền tĩnh & Bóng chung mờ mềm duy nhất của Rặng San Hô Rộng (Stage 2)
            for (Chunk c : chunks) {
                for (CoralReef reef : c.getCoralReefs()) {
                    if (reef.isVisible(camera)) {
                        reef.renderStaticBase(g, camera);
                    }
                }
            }
        }

        // 2. Bóng con mồi (Cá đại dương hoặc thú trên cạn)
        for (Prey prey : preys) {
            if (!prey.isAlive() || (!prey.isWaterOnly() && prey.getType() == Prey.Type.BUTTERFLY) || !camera.isVisible(prey.getX(), prey.getY(), 32)) continue;
            boolean preyInWater = (stageId == 2 || prey.isWaterOnly());
            int footW = 16;
            int sw = Math.round(footW * SHADOW_WIDTH_RATIO);
            int sh = Math.max(3, Math.round(sw * SHADOW_HEIGHT_RATIO));
            Point2D.Double pos = camera.worldToScreen(prey.getX() + offX, prey.getY() + 3 + offY);
            drawCachedShadow(g, pos.x, pos.y, sw, sh, preyInWater);
        }

        // 3. Bóng Cá Sấu Đầm Nước
        if (crocodiles != null) {
            for (Crocodile croc : crocodiles) {
                if (!croc.isAlive() || !camera.isVisible(croc.getX(), croc.getY(), 64)) continue;
                int footW = 50;
                int sw = Math.round(footW * SHADOW_WIDTH_RATIO);
                int sh = Math.max(4, Math.round(sw * SHADOW_HEIGHT_RATIO));
                Point2D.Double pos = camera.worldToScreen(croc.getX() + offX, croc.getY() + offY);
                drawCachedShadow(g, pos.x, pos.y, sw, sh, isWater);
            }
        }

        // 4. Bóng rắn người chơi & AI (độ rộng đều theo thân rắn)
        if (player.isAlive()) {
            int footW = Math.round(SNAKE_BODY_WIDTH);
            int segSW = Math.max(4, Math.round((float) (footW * SHADOW_WIDTH_RATIO)));
            int segSH = Math.max(3, Math.round(segSW * SHADOW_HEIGHT_RATIO));
            List<SnakeSegment> pSegs = player.getSegments();
            int pCount = pSegs.size();
            for (int i = 0; i < pCount; i += 2) {
                SnakeSegment seg = pSegs.get(i);
                if (!camera.isVisible(seg.x, seg.y, 24)) continue;
                Point2D.Double pos = camera.worldToScreen(seg.x + offX, seg.y + offY);
                drawCachedShadow(g, pos.x, pos.y, segSW, segSH, isWater);
            }
        }
        for (AISnake ai : aiSnakes) {
            if (!ai.isAlive()) continue;
            if (stageId == 2) {
                if (camera.isVisible(ai.getX(), ai.getY(), 48)) {
                    int sharkFootW = 44;
                    int sharkSW = Math.round(sharkFootW * SHADOW_WIDTH_RATIO);
                    int sharkSH = Math.max(4, Math.round(sharkSW * SHADOW_HEIGHT_RATIO));
                    Point2D.Double pos = camera.worldToScreen(ai.getX() + offX, ai.getY() + offY);
                    drawCachedShadow(g, pos.x, pos.y, sharkSW, sharkSH, true);
                }
            } else {
                int footW = Math.round(SNAKE_BODY_WIDTH);
                int segSW = Math.max(4, Math.round((float) (footW * SHADOW_WIDTH_RATIO)));
                int segSH = Math.max(3, Math.round(segSW * SHADOW_HEIGHT_RATIO));
                List<SnakeSegment> aiSegs = ai.getSegments();
                int aiCount = aiSegs.size();
                for (int i = 0; i < aiCount; i += 2) {
                    SnakeSegment seg = aiSegs.get(i);
                    if (!camera.isVisible(seg.x, seg.y, 24)) continue;
                    Point2D.Double pos = camera.worldToScreen(seg.x + offX, seg.y + offY);
                    drawCachedShadow(g, pos.x, pos.y, segSW, segSH, false);
                }
            }
        }

        // 5. Bóng Boss Khổng Lồ & Điềm báo trên mặt đất của Boss Gấu
        for (Boss b : bosses) {
            if (!b.isAlive()) continue;
            if (b.getType() == Boss.Type.BEAR) {
                if (!camera.isVisible(b.getX(), b.getY(), 300)) continue;
                initBearSprites();
                Point2D.Double pos = camera.worldToScreen(b.getX() + offX, b.getY() + offY);
                if (cachedBearShadow != null) {
                    g.drawImage(
                            cachedBearShadow,
                            (int) Math.round(pos.x - BEAR_SHADOW_W * 0.5),
                            (int) Math.round(pos.y + 12.0 - BEAR_SHADOW_H * 0.5),
                            null
                    );
                }
                if (b.getBearState() == Boss.BearState.WINDUP && b.getStunTimer() <= 0.0) {
                    drawBearTelegraph(g, camera, b);
                }
            } else if (b.getType() == Boss.Type.SWAMP_KING || b.getType() == Boss.Type.OCTOPUS) {
                if (!camera.isVisible(b.getX(), b.getY(), 64)) continue;
                Point2D.Double pos = camera.worldToScreen(b.getX() + offX, b.getY() + offY);
                int bossFootW = (b.getType() == Boss.Type.OCTOPUS) ? 68 : 62;
                int bsw = Math.round(bossFootW * SHADOW_WIDTH_RATIO);
                int bsh = Math.max(6, Math.round(bsw * SHADOW_HEIGHT_RATIO));
                drawCachedShadow(g, pos.x, pos.y + 6, bsw, bsh, isWater);
            } else {
                if (!camera.isVisible(b.getX(), b.getY(), 360)) continue;
                int bossSegFootW = Math.round(BOSS_BODY_WIDTH);
                int bsw = Math.round(bossSegFootW * SHADOW_WIDTH_RATIO);
                int bsh = Math.max(4, Math.round(bsw * SHADOW_HEIGHT_RATIO));
                for (int i = 0; i < b.getSegments().size(); i += 2) {
                    SnakeSegment seg = b.getSegments().get(i);
                    if (!camera.isVisible(seg.x, seg.y, 32)) continue;
                    Point2D.Double sPos = camera.worldToScreen(seg.x + offX, seg.y + offY);
                    drawCachedShadow(g, sPos.x, sPos.y, bsw, bsh, isWater);
                }
                if (b.getSnakeState() == Boss.SnakeBossState.ROAR_WINDUP && b.getStunTimer() <= 0.0) {
                    drawSnakeBossTelegraph(g, camera, b);
                }
            }
        }

        // 6. Bóng đại bàng trên mặt đất (Telegraph Stalking & Swoop)
        if (eagle != null && eagle.isAlive()) {
            Point2D.Double pos = camera.worldToScreen(eagle.getGroundX() + offX, eagle.getGroundY() + offY);
            double timer = eagle.getTimer();
            Eagle.State state = eagle.getState();

            if (state == Eagle.State.TELEGRAPH) {
                double progress = Math.max(0.0, Math.min(1.0, 1.0 - (timer / Config.Ecosystem.EAGLE_TELEGRAPH_TIME)));
                int shadowW = (int) (48 + progress * 60);
                int shadowH = Math.max(6, (int) (shadowW * SHADOW_HEIGHT_RATIO));

                drawCachedShadow(g, pos.x, pos.y, shadowW, shadowH, false);

                int radius = (int) Config.Ecosystem.EAGLE_STRIKE_RADIUS;
                Stroke oldStroke = g.getStroke();

                if (eagle.isLocked()) {
                    boolean blink = (System.currentTimeMillis() / 90) % 2 == 0;
                    g.setColor(blink ? new Color(239, 68, 68, 240) : new Color(220, 38, 38, 120));
                    g.setStroke(STROKE_PLAIN_3_0);
                    g.drawOval((int) pos.x - radius, (int) pos.y - (int) (radius * 0.7), radius * 2, (int) (radius * 1.4));
                    g.drawLine((int) pos.x - 20, (int) pos.y, (int) pos.x + 20, (int) pos.y);
                    g.drawLine((int) pos.x, (int) pos.y - 14, (int) pos.x, (int) pos.y + 14);
                } else {
                    g.setColor(new Color(239, 68, 68, (int) (90 + progress * 100)));
                    g.setStroke(STROKE_PLAIN_1_8);
                    g.drawOval((int) pos.x - radius, (int) pos.y - (int) (radius * 0.7), radius * 2, (int) (radius * 1.4));
                }
                g.setStroke(oldStroke);
            } else {
                int sW = 70;
                int sH = (int) (sW * SHADOW_HEIGHT_RATIO);
                drawCachedShadow(g, pos.x, pos.y, sW, sH, false);
            }
        }
    }

    private void addRenderable(double y, int type, Object ref) {
        if (renderableCount >= renderablePool.length) {
            RenderableItem[] newPool = new RenderableItem[renderablePool.length * 2];
            System.arraycopy(renderablePool, 0, newPool, 0, renderablePool.length);
            for (int i = renderablePool.length; i < newPool.length; i++) {
                newPool[i] = new RenderableItem();
            }
            renderablePool = newPool;
        }
        RenderableItem item = renderablePool[renderableCount++];
        item.y = y;
        item.type = type;
        item.ref = ref;
    }

    private void renderYSorted(Graphics2D g, Camera camera, List<Chunk> chunks,
                               PlayerSnake player, List<AISnake> aiSnakes, List<Boss> bosses, List<Prey> preys,
                               List<Crocodile> crocodiles, int stageId) {
        renderableCount = 0;

        // 1. Thêm Props (Cây, Bụi, Đá, Hang, Bẫy, Tường) sắp xếp theo y của chân (điểm tiếp đất của sprite)
        for (Chunk c : chunks) {
            for (WorldProp p : c.getProps()) {
                if (!camera.isVisible(p.getX(), p.getY(), 64)) continue;
                double footY;
                if (p.getType() == WorldProp.Type.TREE) {
                    footY = p.getY() + p.getHeight() * 0.35;
                } else if (p.getType() == WorldProp.Type.BUSH) {
                    footY = p.getY() + p.getHeight() * 0.32;
                } else if (p.getType() == WorldProp.Type.ROCK) {
                    footY = p.getY() + p.getHeight() * 0.30;
                } else if (p.getType() == WorldProp.Type.CAVE) {
                    footY = p.getY() + p.getHeight() * 0.35;
                } else if (p.getType() == WorldProp.Type.TRAP) {
                    footY = p.getY() + 4;
                } else if (p.getType() == WorldProp.Type.WALL) {
                    footY = p.getY() + p.getHeight() * 0.35;
                } else {
                    footY = p.getY() + p.getHeight() * 0.30;
                }
                addRenderable(footY, 0, p);
            }
        }

        // 1.5. Thêm các cây cỏ đung đưa trong Cụm Cỏ & Cỏ lẻ (Stage 1) sắp xếp theo footY
        if (stageId == 1) {
            for (Chunk c : chunks) {
                // Tối đa 14 cây đung đưa riêng mỗi cụm (phần còn lại nằm ở nền tĩnh)
                for (GrassPatch patch : c.getGrassPatches()) {
                    if (!patch.isVisible(camera)) continue;
                    for (GrassPatch.ClusterPlant plant : patch.getSwayingPlants()) {
                        if (!camera.isVisible(plant.worldX, plant.worldY, 48)) continue;
                        addRenderable(plant.footY, 6, plant);
                    }
                }
                // Các cây cỏ và hoa lẻ rải rác ngoài cụm
                for (GrassPatch.ClusterPlant plant : c.getStandalonePlants()) {
                    if (!camera.isVisible(plant.worldX, plant.worldY, 48)) continue;
                    addRenderable(plant.footY, 6, plant);
                }
            }
        }

        // 1.6. Thêm các cây san hô đung đưa trong Rặng San Hô & San hô đơn lẻ (Stage 2) sắp xếp theo footY
        if (stageId == 2) {
            for (Chunk c : chunks) {
                for (CoralReef reef : c.getCoralReefs()) {
                    if (!reef.isVisible(camera)) continue;
                    for (CoralReef.CoralPlant coral : reef.getSwayingCorals()) {
                        if (!camera.isVisible(coral.worldX, coral.worldY, 60)) continue;
                        addRenderable(coral.footY, 7, coral);
                    }
                }
                for (CoralReef.CoralPlant sc : c.getStandaloneCorals()) {
                    if (!camera.isVisible(sc.worldX, sc.worldY, 40)) continue;
                    addRenderable(sc.footY, 7, sc);
                }
            }
        }

        // 2. Thêm Con mồi (trừ bướm ở lớp không khí)
        for (Prey p : preys) {
            if (!p.isAlive() || (!p.isWaterOnly() && p.getType() == Prey.Type.BUTTERFLY) || !camera.isVisible(p.getX(), p.getY(), 32)) continue;
            addRenderable(p.getY() + 4, 1, p);
        }

        // 3. Thêm Cá Sấu Đầm Nước
        if (crocodiles != null) {
            for (Crocodile croc : crocodiles) {
                if (!croc.isAlive() || !camera.isVisible(croc.getX(), croc.getY(), 64)) continue;
                addRenderable(croc.getY(), 2, croc);
            }
        }

        // 4. Thêm Rắn người chơi & AI (Với rắn, dùng y của đầu để sắp xếp)
        if (player.isAlive()) {
            addRenderable(player.getY(), 3, player);
        }

        for (AISnake ai : aiSnakes) {
            if (!ai.isAlive()) continue;
            addRenderable(ai.getY(), 4, ai);
        }

        // 5. Thêm Boss Khổng Lồ (Gấu, Trăn, Bạch Tuộc, Cá Sấu Chúa, Thần Long)
        for (Boss boss : bosses) {
            if (!boss.isAlive()) continue;
            double sortY = (boss.getType() == Boss.Type.BEAR) ? (boss.getY() + BEAR_FOOT_Y_OFFSET) : boss.getY();
            addRenderable(sortY, 5, boss);
        }

        // Sắp xếp nhanh không tạo object
        java.util.Arrays.sort(renderablePool, 0, renderableCount);

        for (int i = 0; i < renderableCount; i++) {
            RenderableItem item = renderablePool[i];
            switch (item.type) {
                case 0 -> drawProp(g, camera, (WorldProp) item.ref, player, stageId);
                case 1 -> {
                    Prey p = (Prey) item.ref;
                    if (stageId == 2 || p.isWaterOnly()) {
                        drawOceanFish(g, camera, p);
                    } else {
                        drawPrey(g, camera, p);
                    }
                }
                case 2 -> drawCrocodile(g, camera, (Crocodile) item.ref);
                case 3 -> drawSnake(g, camera, (PlayerSnake) item.ref);
                case 4 -> {
                    AISnake ai = (AISnake) item.ref;
                    if (stageId == 2) {
                        drawShark(g, camera, ai);
                    } else {
                        drawSnake(g, camera, ai);
                    }
                }
                case 5 -> drawBoss(g, camera, (Boss) item.ref);
                case 6 -> drawGrassPlant(g, camera, (GrassPatch.ClusterPlant) item.ref, player, ambientTimer);
                case 7 -> drawCoralPlant(g, camera, (CoralReef.CoralPlant) item.ref, player, ambientTimer);
            }
        }
    }

    private void drawProp(Graphics2D g, Camera camera, WorldProp prop, PlayerSnake player, int stageId) {
        Point2D.Double pos = camera.worldToScreen(prop.getX(), prop.getY());
        BufferedImage img = null;

        Composite oldComp = g.getComposite();

        switch (prop.getType()) {
            case TREE -> {
                // Kiểm tra nếu rắn nằm sau cây lớn: giảm alpha xuống 0.6 để không bị che khuất hoàn toàn
                double treeFootY = prop.getY() + prop.getHeight() * 0.35;
                boolean isBehindTree = (player.getY() < treeFootY)
                    && (player.getY() > prop.getY() - prop.getHeight() * 0.75)
                    && (Math.abs(player.getX() - prop.getX()) < prop.getWidth() * 0.60);

                if (isBehindTree) {
                    g.setComposite(COMPOSITE_TREE_OCCLUSION);
                }

                double swayPhase = prop.getX() * 0.03 + prop.getY() * 0.05;

                if (stageId == 2) {
                    drawCoconutPalm(g, pos.x, pos.y, prop.getWidth(), prop.getHeight(), player, prop);
                } else {
                    drawForestOakTree(g, pos.x, pos.y, prop.getWidth(), prop.getHeight(), swayPhase, ambientTimer);
                }
            }
            case ROCK -> {
                if (stageId == 2) {
                    drawSubmergedReef(g, pos.x, pos.y, prop.getWidth(), prop.getHeight(), prop.getVariant());
                } else {
                    drawRock(g, pos.x, pos.y, prop.getWidth(), prop.getHeight(), prop.getVariant());
                }
            }
            case WALL -> drawWall(g, pos.x, pos.y, prop.getWidth(), prop.getHeight());
            case BUSH -> {
                double swayPhase = prop.getX() * 0.04 + prop.getY() * 0.06;
                if (stageId == 2) {
                    drawSeaweed(g, prop, pos.x, pos.y, prop.getWidth(), prop.getHeight(), player);
                } else {
                    drawSwayingBush(g, prop, pos.x, pos.y, prop.getWidth(), prop.getHeight(), player, swayPhase, ambientTimer);
                }
            }
            case CAVE -> {
                double dist = (player != null) ? Math.hypot(player.getX() - prop.getX(), player.getY() - prop.getY()) : 9999.0;
                boolean showLabel = dist <= PROXIMITY_LABEL_RADIUS;
                drawCave(g, pos.x, pos.y, prop.getWidth(), prop.getHeight(), showLabel);
            }
            case TRAP -> {
                img = AssetManager.getImage("trap");

                if (prop.isArmed()) {
                    // 1. Vòng hào quang đỏ cảnh báo bán kính nguy hiểm
                    g.setColor(new Color(239, 68, 68, 50));
                    g.drawOval((int) pos.x - 18, (int) pos.y - 16, 36, 32);

                    // 2. Viền góc tam giác cảnh báo Hazard vàng - đen
                    g.setColor(new Color(250, 204, 21, 220));
                    // Góc trên-trái
                    g.drawLine((int) pos.x - 18, (int) pos.y - 12, (int) pos.x - 18, (int) pos.y - 16);
                    g.drawLine((int) pos.x - 18, (int) pos.y - 16, (int) pos.x - 14, (int) pos.y - 16);
                    // Góc trên-phải
                    g.drawLine((int) pos.x + 18, (int) pos.y - 12, (int) pos.x + 18, (int) pos.y - 16);
                    g.drawLine((int) pos.x + 18, (int) pos.y - 16, (int) pos.x + 14, (int) pos.y - 16);
                    // Góc dưới-trái
                    g.drawLine((int) pos.x - 18, (int) pos.y + 12, (int) pos.x - 18, (int) pos.y + 16);
                    g.drawLine((int) pos.x - 18, (int) pos.y + 16, (int) pos.x - 14, (int) pos.y + 16);
                    // Góc dưới-phải
                    g.drawLine((int) pos.x + 18, (int) pos.y + 12, (int) pos.x + 18, (int) pos.y + 16);
                    g.drawLine((int) pos.x + 18, (int) pos.y + 16, (int) pos.x + 14, (int) pos.y + 16);

                    // 3. Vẽ Sprite bẫy kẹp
                    if (img != null) {
                        AssetManager.drawCentered(g, img, pos.x, pos.y, prop.getWidth() + 4, prop.getHeight() + 4);
                    } else {
                        g.setColor(new Color(148, 163, 184));
                        g.fillRect((int) pos.x - 15, (int) pos.y - 12, 30, 24);
                        g.setColor(new Color(239, 68, 68));
                        g.fillOval((int) pos.x - 5, (int) pos.y - 5, 10, 10);
                    }

                    // 4. Răng thép sắc bén sáng bạc và tấm mồi đỏ
                    g.setColor(new Color(248, 250, 252));
                    g.fillRect((int) pos.x - 14, (int) pos.y - 10, 3, 4);
                    g.fillRect((int) pos.x + 11, (int) pos.y - 10, 3, 4);
                    g.fillRect((int) pos.x - 14, (int) pos.y + 6, 3, 4);
                    g.fillRect((int) pos.x + 11, (int) pos.y + 6, 3, 4);

                    g.setColor(new Color(220, 38, 38));
                    g.fillOval((int) pos.x - 4, (int) pos.y - 4, 8, 8);

                    // 5. Hiệu ứng tia sáng lấp lánh (Sparkle Glint)
                    long glint = System.currentTimeMillis() % 1400;
                    if (glint < 200) {
                        g.setColor(new Color(255, 255, 255, 240));
                        g.fillRect((int) pos.x - 8, (int) pos.y - 7, 3, 3);
                        g.fillRect((int) pos.x + 7, (int) pos.y + 5, 2, 2);
                    }
                } else {
                    // Bẫy đã sập (Đang nguội 8s) -> Giảm độ mờ, hiện màu xám vô hiệu hóa
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.40f));
                    if (img != null) {
                        AssetManager.drawCentered(g, img, pos.x, pos.y, prop.getWidth(), prop.getHeight());
                    } else {
                        g.setColor(new Color(100, 116, 139));
                        g.drawRect((int) pos.x - 14, (int) pos.y - 12, 28, 24);
                    }
                    g.setColor(new Color(148, 163, 184));
                    g.drawString("OFF", (int) pos.x - 10, (int) pos.y + 4);
                }
            }
        }

        g.setComposite(oldComp);
    }

    private void drawPrey(Graphics2D g, Camera camera, Prey prey) {
        Point2D.Double pos = camera.worldToScreen(prey.getX(), prey.getY());
        String key = switch (prey.getType()) {
            case RABBIT -> "rabbit";
            case FROG -> "frog";
            case MOUSE -> "mouse";
            default -> "food_seed";
        };

        BufferedImage img = AssetManager.getImage(key);
        boolean flipH = Math.cos(prey.getAngle()) < 0;

        if (img != null) {
            AssetManager.drawFlipped(g, img, pos.x, pos.y, 28, 28, flipH);
        } else {
            g.setColor(prey.getType() == Prey.Type.RABBIT ? Color.WHITE : new Color(74, 222, 128));
            g.fillOval((int) pos.x - 9, (int) pos.y - 9, 18, 18);
        }
    }

    private void drawOceanFish(Graphics2D g, Camera camera, Prey prey) {
        Point2D.Double pos = camera.worldToScreen(prey.getX(), prey.getY());
        AffineTransform old = g.getTransform();
        g.translate(pos.x, pos.y);
        g.rotate(prey.getAngle());

        double wag = Math.sin(prey.getAnimTimer() * 10.0) * 4.0;
        int fishIdx = Math.abs(prey.hashCode()) % 4;

        if (fishIdx == 0) {
            // ==========================================
            // 1. CÁ HỀ ĐẠI DƯƠNG (CLOWNFISH / NEMO)
            // ==========================================
            // Vây đuôi ve vẩy
            g.setColor(new Color(249, 115, 22));
            int[] tx = {-14, -20, -20};
            int[] ty = {0, (int) (-7 + wag), (int) (7 + wag)};
            g.fillPolygon(tx, ty, 3);
            g.setColor(Color.WHITE);
            g.drawLine(-18, (int) (-5 + wag), -18, (int) (5 + wag));

            // Thân cá cam rực rỡ
            g.setColor(new Color(15, 23, 42)); // Viền thân
            g.fillOval(-15, -9, 28, 18);
            g.setColor(new Color(249, 115, 22)); // Cam tươi
            g.fillOval(-14, -8, 26, 16);

            // 3 sọc trắng đặc trưng viền đen
            g.setColor(new Color(15, 23, 42));
            g.fillRect(-8, -8, 4, 16);
            g.fillRect(-1, -8, 4, 16);
            g.fillRect(6, -7, 4, 14);

            g.setColor(Color.WHITE);
            g.fillRect(-7, -8, 2, 16);
            g.fillRect(0, -8, 2, 16);
            g.fillRect(7, -7, 2, 14);

            // Vây lưng tròn & Vây bụng
            g.setColor(new Color(234, 88, 12));
            g.fillOval(-4, -12, 10, 5);
            g.fillOval(-2, 7, 8, 4);

            // Mắt cá tròn xoe
            g.setColor(Color.WHITE);
            g.fillOval(5, -5, 5, 5);
            g.setColor(new Color(15, 23, 42));
            g.fillOval(7, -4, 3, 3);

        } else if (fishIdx == 1) {
            // ==========================================
            // 2. CÁ XANH BIỂN SÂU (BLUE TANG / DORY)
            // ==========================================
            // Đuôi vàng rực rỡ
            g.setColor(new Color(250, 204, 21));
            int[] tx = {-14, -22, -22};
            int[] ty = {0, (int) (-8 + wag), (int) (8 + wag)};
            g.fillPolygon(tx, ty, 3);

            // Thân xanh hoàng gia
            g.setColor(new Color(15, 23, 42));
            g.fillOval(-15, -9, 28, 18);
            g.setColor(new Color(37, 99, 235)); // Lam hoàng gia
            g.fillOval(-14, -8, 26, 16);

            // Họa tiết đen uốn lượn đặc trưng trên thân
            g.setColor(new Color(15, 23, 42));
            g.fillArc(-10, -6, 18, 12, 40, 180);

            // Vây ức vàng
            g.setColor(new Color(250, 204, 21));
            g.fillOval(-2, 2, 6, 4);

            // Mắt cá
            g.setColor(Color.WHITE);
            g.fillOval(6, -5, 5, 5);
            g.setColor(new Color(15, 23, 42));
            g.fillOval(8, -4, 3, 3);

        } else if (fishIdx == 2) {
            // ==========================================
            // 3. CÁ THIÊN THẦN (ANGELFISH)
            // ==========================================
            // Vây lưng và vây bụng dài kiêu sa
            g.setColor(new Color(6, 182, 212));
            int[] fx = {-4, 2, 8};
            int[] fTop = {-8, -16, -7};
            int[] fBot = {8, 16, 7};
            g.fillPolygon(fx, fTop, 3);
            g.fillPolygon(fx, fBot, 3);

            // Đuôi nhọn
            int[] tx = {-12, -20, -18};
            int[] ty = {0, (int) (-6 + wag), (int) (6 + wag)};
            g.fillPolygon(tx, ty, 3);

            // Thân hình thoi màu bạc lam ngọc
            g.setColor(new Color(15, 23, 42));
            g.fillPolygon(new int[]{-12, 2, 14, 2}, new int[]{0, -10, 0, 10}, 4);
            g.setColor(new Color(165, 243, 252));
            g.fillPolygon(new int[]{-10, 2, 12, 2}, new int[]{0, -8, 0, 8}, 4);

            // Sọc sẫm chạy dọc thân
            g.setColor(new Color(14, 116, 144));
            g.drawLine(1, -7, 1, 7);
            g.drawLine(-4, -5, -4, 5);

            // Mắt
            g.setColor(new Color(250, 204, 21));
            g.fillOval(5, -3, 4, 4);
            g.setColor(Color.BLACK);
            g.fillRect(6, -2, 2, 2);

        } else {
            // ==========================================
            // 4. CÁ PHÁT QUANG BIỂN SÂU (NEON MINNOW)
            // ==========================================
            // Đuôi đỏ ruby
            g.setColor(new Color(244, 63, 94));
            int[] tx = {-14, -20, -20};
            int[] ty = {0, (int) (-5 + wag), (int) (5 + wag)};
            g.fillPolygon(tx, ty, 3);

            // Thân thon dài màu xanh đen
            g.setColor(new Color(15, 23, 42));
            g.fillRoundRect(-14, -6, 26, 12, 6, 6);
            g.setColor(new Color(30, 41, 59));
            g.fillRoundRect(-13, -5, 24, 10, 5, 5);

            // Dải phát quang xanh neon rực sáng
            g.setColor(new Color(34, 211, 238));
            g.drawLine(-11, -2, 9, -2);
            g.setColor(new Color(165, 243, 252));
            g.drawLine(-8, -2, 6, -2);

            // Bụng đỏ hồng
            g.setColor(new Color(244, 63, 94));
            g.fillRect(-4, 1, 10, 3);

            // Mắt
            g.setColor(new Color(34, 211, 238));
            g.fillOval(6, -4, 4, 4);
            g.setColor(Color.BLACK);
            g.fillRect(8, -3, 2, 2);
        }

        // Phủ lớp depth tint mỏng dịu theo độ sâu đại dương
        Composite oldFishComp = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, TINT_ALPHA_CREATURE));
        g.setColor(COLOR_UNDERWATER_TINT);
        g.fillOval(-16, -10, 32, 20);
        g.setComposite(oldFishComp);

        g.setTransform(old);
    }

    private void drawCrocodile(Graphics2D g, Camera camera, Crocodile croc) {
        Point2D.Double pos = camera.worldToScreen(croc.getX(), croc.getY());
        AffineTransform old = g.getTransform();
        g.translate(pos.x, pos.y);
        g.rotate(croc.getAngle());

        double swimPhase = croc.getSwimPhase();
        double walkPhase = croc.getWalkPhase();
        double jawOpen = croc.getJawOpen();
        double curvature = croc.getSpineCurvature();
        boolean onLand = croc.isOnLand();
        boolean isLunging = croc.isLunging();
        boolean isStalking = croc.isStalking();

        // 1. Hiệu ứng nước / bóng trên cạn
        if (!onLand) {
            double wag = Math.sin(swimPhase) * 3.5;
            // Vệt rẽ sóng chữ V phía trước mõm và quanh thân
            g.setColor(new Color(165, 243, 252, isLunging ? 170 : (isStalking ? 110 : 65)));
            g.drawArc(-46, -16 + (int) wag, 36, 32, 120, 120);
            g.drawArc(-58, -20 + (int) (wag * 0.7), 48, 40, 130, 100);
            g.drawOval(-44, -15 + (int) (wag * 0.4), 88, 30);
            // Sóng rẽ nước ở đầu mũi
            g.drawLine(38, 0, 50, -10);
            g.drawLine(38, 0, 50, 10);
        } else {
            // Bóng đổ trên cạn/bãi cát
            g.setColor(new Color(15, 23, 42, 110));
            g.fillOval(-38, -12, 78, 24);
        }

        // Nhấp nhô thở và bơi
        int bobY = (int) (Math.sin(croc.getAnimTimer() * 3.5) * 1.2);

        // 2. VẼ 4 CHI CÁ SẤU (4 Scaly Quadruped Legs & Claws - theo sprite sheet 12 frame)
        // Chân bước so le nhịp nhàng: Cặp A (Trước Trái + Sau Phải), Cặp B (Trước Phải + Sau Trái)
        double legStepA = Math.sin(walkPhase);
        double legStepB = -legStepA;

        drawCrocLeg(g, 12, -11, legStepA, true, onLand);   // Trước Trái (FL)
        drawCrocLeg(g, 12, 11, legStepB, false, onLand);    // Trước Phải (FR)
        drawCrocLeg(g, -12, -10, legStepB, true, onLand);   // Sau Trái (BL)
        drawCrocLeg(g, -12, 10, legStepA, false, onLand);   // Sau Phải (BR)

        // 3. VẼ ĐUÔI UỐN LƯỢN (Flexible S-curve Undulating Tail)
        // Đuôi gồm các khúc uốn dẻo
        double tailSway = Math.sin(swimPhase) * 4.5 + curvature * 14.0;
        int t1X = -16, t1Y = (int)(tailSway * 0.25) + bobY;
        int t2X = -28, t2Y = (int)(tailSway * 0.55) + bobY;
        int t3X = -40, t3Y = (int)(tailSway * 0.85) + bobY;
        int t4X = -50, t4Y = (int)(tailSway * 1.15) + bobY;

        // Lớp yếm bụng đuôi (màu vàng cam hổ phách)
        int[] tailBellyX = {t1X, t2X, t3X, t4X, t3X, t2X, t1X};
        int[] tailBellyY = {t1Y + 6, t2Y + 4, t3Y + 3, t4Y, t3Y - 3, t2Y - 4, t1Y - 6};
        g.setColor(new Color(217, 119, 6)); // #d97706
        g.fillPolygon(tailBellyX, tailBellyY, 7);

        // Lớp vảy lưng đuôi (màu xanh rêu đậm)
        g.setColor(new Color(63, 98, 18)); // #3f6212
        int[] tailDorsalX = {t1X, t2X, t3X, t4X, t3X, t2X, t1X};
        int[] tailDorsalY = {t1Y + 4, t2Y + 3, t3Y + 2, t4Y, t3Y - 5, t2Y - 6, t1Y - 7};
        g.fillPolygon(tailDorsalX, tailDorsalY, 7);

        // Gai vây đuôi nhấp nhô kép (Dorsal crest spikes)
        g.setColor(new Color(20, 83, 45)); // #14532d
        g.fillRect(t2X - 2, t2Y - 8, 4, 3);
        g.fillRect(t3X - 2, t3Y - 6, 3, 3);
        g.fillRect(t4X - 1, t4Y - 4, 3, 2);
        g.setColor(new Color(132, 204, 22)); // #84cc16 gân sáng
        g.fillRect(t2X - 1, t2Y - 7, 2, 2);
        g.fillRect(t3X - 1, t3Y - 5, 2, 2);

        // 4. VẼ THÂN CÁ SẤU (Broad Armored Torso with Osteoderm Scutes)
        // Yếm bụng vàng cam phía dưới
        g.setColor(new Color(245, 158, 11)); // #f59e0b
        g.fillRoundRect(-16, -9 + bobY, 32, 18, 10, 10);
        g.setColor(new Color(217, 119, 6)); // Viền gấp nếp bụng
        g.drawLine(-8, -8 + bobY, -8, 8 + bobY);
        g.drawLine(0, -9 + bobY, 0, 9 + bobY);
        g.drawLine(8, -8 + bobY, 8, 8 + bobY);

        // Lớp lưng giáp vảy xanh rêu ô-liu
        g.setColor(new Color(77, 124, 15)); // #4d7c0f
        g.fillRoundRect(-16, -11 + bobY, 32, 18, 10, 10);
        g.setColor(new Color(20, 83, 45)); // Viền ngoài
        g.drawRoundRect(-16, -11 + bobY, 32, 18, 10, 10);

        // Các gờ vảy xương gai lưng gồ ghề (Osteoderm Scutes)
        g.setColor(new Color(101, 163, 13)); // #65a30d
        g.fillRect(-11, -8 + bobY, 4, 3);
        g.fillRect(-11, -3 + bobY, 4, 3);
        g.fillRect(-4, -8 + bobY, 4, 3);
        g.fillRect(-4, -3 + bobY, 4, 3);
        g.fillRect(3, -8 + bobY, 4, 3);
        g.fillRect(3, -3 + bobY, 4, 3);
        g.fillRect(10, -7 + bobY, 4, 3);
        g.fillRect(10, -2 + bobY, 4, 3);

        // Điểm sáng nổi trên sống lưng vảy
        g.setColor(new Color(163, 230, 53)); // #a3e635
        g.fillRect(-10, -7 + bobY, 2, 1);
        g.fillRect(-3, -7 + bobY, 2, 1);
        g.fillRect(4, -7 + bobY, 2, 1);
        g.fillRect(11, -6 + bobY, 2, 1);

        // 5. VẼ ĐẦU VÀ MÕM CÁ SẤU (Armored Snout & Jaws)
        int snoutLen = 22;
        int snoutW = 14;

        // Hàm dưới (yếm vàng cam)
        g.setColor(new Color(245, 158, 11));
        g.fillRoundRect(14, -5 + bobY + (int)(jawOpen * 4), snoutLen, snoutW - 3, 6, 6);

        // Khoang miệng đỏ tươi nếu há miệng
        if (jawOpen > 0.15) {
            g.setColor(new Color(185, 28, 28)); // #b91c1c
            g.fillRect(18, -4 + bobY, snoutLen - 5, (int)(jawOpen * 6));
        }

        // Hàm trên (lưng mõm xanh rêu gồ ghề)
        g.setColor(new Color(77, 124, 15));
        g.fillRoundRect(14, -7 + bobY - (int)(jawOpen * 3), snoutLen + 2, snoutW, 6, 6);
        g.setColor(new Color(20, 83, 45));
        g.drawRoundRect(14, -7 + bobY - (int)(jawOpen * 3), snoutLen + 2, snoutW, 6, 6);

        // Lỗ mũi gồ lên ở chóp mõm
        g.setColor(new Color(20, 83, 45));
        g.fillRect(33, -4 + bobY - (int)(jawOpen * 3), 2, 2);
        g.fillRect(33, 2 + bobY - (int)(jawOpen * 3), 2, 2);

        // HÀNG RĂNG NANH TRẮNG SẮC NHỌN (Conical White Teeth)
        g.setColor(Color.WHITE);
        // Răng hàm trên thò xuống
        g.fillRect(20, 0 + bobY - (int)(jawOpen * 3), 2, 3);
        g.fillRect(25, 1 + bobY - (int)(jawOpen * 3), 2, 3);
        g.fillRect(30, 0 + bobY - (int)(jawOpen * 3), 2, 2);
        // Răng hàm dưới thò lên
        g.fillRect(22, 1 + bobY + (int)(jawOpen * 4), 2, 2);
        g.fillRect(27, 0 + bobY + (int)(jawOpen * 4), 2, 3);

        // 6. ĐÔI MẮT HỔ PHÁCH SĂN MỒI (Amber Reptilian Eyes with Slit Pupil)
        int eyeX = 20;
        int eyeY = -7 + bobY - (int)(jawOpen * 2);

        // Mắt trái (hướng lên trên màn hình)
        g.setColor(new Color(20, 83, 45)); // Hốc mắt vảy
        g.fillOval(eyeX - 1, eyeY - 1, 6, 6);
        if (isLunging) {
            g.setColor(new Color(239, 68, 68)); // Đỏ rực cuồng nộ
        } else if (isStalking) {
            g.setColor(new Color(250, 204, 21)); // Vàng rực rình rập
        } else {
            g.setColor(new Color(245, 158, 11)); // Hổ phách bình thường
        }
        g.fillOval(eyeX, eyeY, 4, 4);
        // Con ngươi đen dẹt xếch
        g.setColor(new Color(15, 23, 42));
        g.fillRect(eyeX + 1, eyeY, 1, 4);

        // Mắt phải (hướng xuống dưới)
        int eyeY2 = 2 + bobY;
        g.setColor(new Color(20, 83, 45));
        g.fillOval(eyeX - 1, eyeY2 - 1, 6, 6);
        if (isLunging) {
            g.setColor(new Color(239, 68, 68));
        } else if (isStalking) {
            g.setColor(new Color(250, 204, 21));
        } else {
            g.setColor(new Color(245, 158, 11));
        }
        g.fillOval(eyeX, eyeY2, 4, 4);
        g.setColor(new Color(15, 23, 42));
        g.fillRect(eyeX + 1, eyeY2, 1, 4);

        // Nhuộm sắc nước khi cá sấu ở dưới nước
        if (!onLand) {
            Composite oldCrocComp = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, TINT_ALPHA_CREATURE));
            g.setColor(COLOR_UNDERWATER_TINT);
            g.fillRoundRect(-32, -18 + bobY, 68, 36, 12, 12);
            g.setComposite(oldCrocComp);
        }

        g.setTransform(old);

        // Thanh máu nhỏ nếu bị thương
        if (croc.getHealth() < croc.getMaxHealth()) {
            int bw = 42, bh = 5;
            int bx = (int) pos.x - bw / 2;
            int by = (int) pos.y - 24;
            g.setColor(new Color(15, 23, 42));
            g.fillRect(bx, by, bw, bh);
            double pct = (double) croc.getHealth() / croc.getMaxHealth();
            g.setColor(new Color(239, 68, 68));
            g.fillRect(bx, by, (int) (bw * pct), bh);
            g.setColor(new Color(250, 204, 21));
            g.drawRect(bx, by, bw, bh);
        }
    }

    private void drawCrocLeg(Graphics2D g, int rootX, int rootY, double step, boolean isLeft, boolean onLand) {
        int side = isLeft ? -1 : 1;
        double swing = step * 6.0;
        double lift = onLand ? (Math.max(0.0, step) * -2.5 * side) : 0;

        int kneeX = rootX + (int) swing;
        int kneeY = rootY + (int) (side * 6 + lift);
        int footX = rootX + (int) (swing * 1.3) + (isLeft ? -2 : 2);
        int footY = rootY + (int) (side * 11 + lift);

        // Đùi chân vảy xanh rêu
        g.setColor(new Color(63, 98, 18));
        g.setStroke(STROKE_CAP_ROUND_3_8);
        g.drawLine(rootX, rootY, kneeX, kneeY);

        // Cẳng chân
        g.setColor(new Color(77, 124, 15));
        g.setStroke(STROKE_CAP_ROUND_2_8);
        g.drawLine(kneeX, kneeY, footX, footY);

        // Bàn chân xòe móng vuốt
        g.setColor(new Color(20, 83, 45));
        g.fillOval(footX - 2, footY - 2, 5, 4);
        g.setColor(new Color(254, 240, 138)); // Móng vuốt trắng vàng
        g.fillRect(footX + (isLeft ? -3 : 2), footY, 2, 2);
        g.fillRect(footX, footY + side, 2, 2);
    }

    public void drawSnake(Graphics2D g, Camera camera, Snake snake) {
        Composite oldComp = g.getComposite();
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        // Tắt khử răng cưa để đảm bảo phong cách pixel-art sắc nét
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        // Ẩn nấp trong bụi rậm / hang đá (Camouflage)
        if (snake.isHidden()) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
        } else if (snake.getInvulnerableTimer() > 0 && (System.currentTimeMillis() / 100) % 2 == 0) {
            // Chớp nháy khi miễn nhiễm sát thương
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
        }

        List<SnakeSegment> segments = snake.getSegments();
        int segCount = segments.size();
        if (segCount < 2) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
            g.setComposite(oldComp);
            return;
        }

        int level = snake.getEvolutionLevel();
        boolean isPlayer = snake.isPlayer();
        boolean inWater = snake.isInWater();
        boolean isFast = snake.isDashing() || snake.isSprinting();

        // Xác định phân loại sinh vật (0..4: Player Lv1..Lv5, 5: AI Hunter, 6: AI Forager)
        int category;
        if (isPlayer) {
            category = Math.min(4, Math.max(0, level - 1));
        } else {
            AISnake ai = (AISnake) snake;
            category = (ai.getRole() == AISnake.Role.HUNTER) ? 5 : 6;
        }

        Color borderColor     = inWater ? SNAKE_OUTLINE_WATER[category] : SNAKE_OUTLINE_LAND[category];
        Color bodyColor       = inWater ? SNAKE_BASE_COLOR_WATER[category] : SNAKE_BASE_COLOR[category];
        Color highlightColor  = inWater ? SNAKE_HIGHLIGHT_COLOR_WATER[category] : SNAKE_HIGHLIGHT_COLOR[category];

        Stroke oldStroke = g.getStroke();

        // 1. Tính toán sóng sin uốn lượn vuông góc hướng di chuyển cho các đốt (Biên độ đồng đều suốt thân)
        double waveAmp = isFast ? SNAKE_WAVE_AMP_FAST : SNAKE_WAVE_AMP_NORMAL;
        double waveFreq = isFast ? SNAKE_WAVE_FREQ_FAST : SNAKE_WAVE_FREQ_NORMAL;
        double slitherTime = snake.getSlitherTime();

        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;

        for (int i = 0; i < segCount; i++) {
            SnakeSegment seg = segments.get(i);
            double progress = (double) i / (segCount - 1);
            // Đầu ổn định nhẹ để định hướng, toàn bộ thân sau có biên độ sóng đều đặn
            double headStabilizer = (i == 0) ? 0.35 : 1.0;
            double wave = Math.sin(slitherTime * waveFreq - i * 0.48) * waveAmp * headStabilizer;

            // Dưới nước: thêm sóng đuôi vẫy chậm uyển chuyển ở 40% thân sau
            if (inWater && progress > 0.50) {
                double waterProg = (progress - 0.50) / 0.50;
                double waterWave = Math.sin(slitherTime * SNAKE_WATER_TAIL_SWAY_FREQ - i * 0.38) * SNAKE_WATER_TAIL_SWAY_AMP * waterProg;
                wave += waterWave;
            }

            double perpX = -Math.sin(seg.angle) * wave;
            double perpY =  Math.cos(seg.angle) * wave;

            Point2D.Double screen = camera.worldToScreen(seg.x + perpX, seg.y + perpY);
            snakeCtrlX[i] = screen.x;
            snakeCtrlY[i] = screen.y;

            if (screen.x < minX) minX = screen.x;
            if (screen.x > maxX) maxX = screen.x;
            if (screen.y < minY) minY = screen.y;
            if (screen.y > maxY) maxY = screen.y;
        }

        // Frustum culling: Nếu toàn bộ thân rắn nằm ngoài màn hình thì bỏ qua
        int vw = camera.getViewportWidth();
        int vh = camera.getViewportHeight();
        double pad = SNAKE_BODY_WIDTH + 30.0;
        if (maxX < -pad || minX > vw + pad || maxY < -pad || minY > vh + pad) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
            g.setComposite(oldComp);
            return;
        }

        // 2. Nội suy Catmull-Rom Spline tạo dải cột sống (Spine) liên tục và thuôn dài
        int stepsPerSeg = 4;
        int m = 0;
        for (int i = 0; i < segCount - 1; i++) {
            double p0x = (i > 0) ? snakeCtrlX[i - 1] : snakeCtrlX[0];
            double p0y = (i > 0) ? snakeCtrlY[i - 1] : snakeCtrlY[0];
            double p1x = snakeCtrlX[i];
            double p1y = snakeCtrlY[i];
            double p2x = snakeCtrlX[i + 1];
            double p2y = snakeCtrlY[i + 1];
            double p3x = (i + 2 < segCount) ? snakeCtrlX[i + 2] : snakeCtrlX[segCount - 1];
            double p3y = (i + 2 < segCount) ? snakeCtrlY[i + 2] : snakeCtrlY[segCount - 1];

            for (int k = 0; k < stepsPerSeg; k++) {
                double u = (double) k / stepsPerSeg;
                double u2 = u * u;
                double u3 = u2 * u;

                double x = 0.5 * (2.0 * p1x + (-p0x + p2x) * u + (2.0 * p0x - 5.0 * p1x + 4.0 * p2x - p3x) * u2 + (-p0x + 3.0 * p1x - 3.0 * p2x + p3x) * u3);
                double y = 0.5 * (2.0 * p1y + (-p0y + p2y) * u + (2.0 * p0y - 5.0 * p1y + 4.0 * p2y - p3y) * u2 + (-p0y + 3.0 * p1y - 3.0 * p2y + p3y) * u3);

                if (m < MAX_SNAKE_SPLINE_PTS) {
                    snakeSplineX[m] = Math.round(x);
                    snakeSplineY[m] = Math.round(y);
                    m++;
                }
            }
        }
        if (m < MAX_SNAKE_SPLINE_PTS) {
            snakeSplineX[m] = Math.round(snakeCtrlX[segCount - 1]);
            snakeSplineY[m] = Math.round(snakeCtrlY[segCount - 1]);
            m++;
        }
        int totalPts = m;

        // Xây dựng Path2D cho thân rắn và dải highlight lệch góc trên-trái (-1.2px, -1.2px)
        snakeBodyPath.reset();
        snakeHighlightPath.reset();

        snakeBodyPath.moveTo(snakeSplineX[0], snakeSplineY[0]);
        snakeHighlightPath.moveTo(snakeSplineX[0] - 1.2, snakeSplineY[0] - 1.2);
        for (int j = 1; j < totalPts; j++) {
            snakeBodyPath.lineTo(snakeSplineX[j], snakeSplineY[j]);
            snakeHighlightPath.lineTo(snakeSplineX[j] - 1.2, snakeSplineY[j] - 1.2);
        }

        // Hào quang lướt nhanh (Dash Aura)
        if (snake.isDashing()) {
            g.setColor(new Color(0, 240, 255, 65));
            for (int j = 0; j < totalPts - 1; j += 4) {
                int rx = (int) snakeSplineX[j];
                int ry = (int) snakeSplineY[j];
                int rAura = (int) Math.round(SNAKE_BODY_WIDTH / 2.0 + 5.0);
                g.fillOval(rx - rAura, ry - rAura, rAura * 2, rAura * 2);
            }
        }

        // Hào quang Thần Long (Level 3, 4, 5 Player Auras)
        if (isPlayer && level >= 3) {
            SnakeSegment sHead = segments.get(0);
            Point2D.Double hPos = camera.worldToScreen(sHead.x, sHead.y);
            if (level >= 5) {
                g.setColor(new Color(250, 204, 21, 90));
                g.fillOval((int) hPos.x - 40, (int) hPos.y - 36, 80, 72);
                g.setColor(new Color(56, 189, 248, 70));
                g.drawOval((int) hPos.x - 44, (int) hPos.y - 40, 88, 80);
            } else if (level == 4) {
                g.setColor(new Color(239, 68, 68, 85));
                g.fillOval((int) hPos.x - 36, (int) hPos.y - 32, 72, 64);
                g.setColor(new Color(249, 115, 22, 60));
                g.drawOval((int) hPos.x - 40, (int) hPos.y - 36, 80, 72);
            } else {
                g.setColor(new Color(250, 204, 21, 60));
                g.fillOval((int) hPos.x - 34, (int) hPos.y - 30, 68, 60);
            }
        }

        // 3. VẼ DẢI THÂN 3 LỚP NÉT TRÒN LIỀN MẠCH (3-Layer Stroke with CAP_ROUND & JOIN_ROUND)
        // Lớp 1: Viền ngoài (Border Stroke: 20px, tối hơn 35-40% cùng gam màu)
        g.setStroke(STROKE_NORMAL_BORDER);
        g.setColor(borderColor);
        g.draw(snakeBodyPath);

        // Lớp 2: Thân chính (Body Stroke: 18px, màu chuẩn loài, độ rộng đều tuyệt đối từ đầu đến đuôi)
        g.setStroke(STROKE_NORMAL_BODY);
        g.setColor(bodyColor);
        g.draw(snakeBodyPath);

        // Lớp 3: Dải Highlight khối 3D (Highlight Stroke: 2.5px lệch phía trên-trái)
        g.setStroke(STROKE_NORMAL_HIGHLIGHT);
        g.setColor(highlightColor);
        g.draw(snakeHighlightPath);

        // Khôi phục nét vẽ mặc định
        g.setStroke(oldStroke);

        // 4. Họa tiết lặp đều đặn kích thước đồng nhất dọc theo cột sống
        for (int j = 4; j < totalPts - 3; j += 4) {
            double tx, ty;
            tx = snakeSplineX[j + 1] - snakeSplineX[j - 1];
            ty = snakeSplineY[j + 1] - snakeSplineY[j - 1];
            double tlen = Math.hypot(tx, ty);
            if (tlen < 1e-4) { tx = 1.0; ty = 0.0; }
            else { tx /= tlen; ty /= tlen; }
            double nx = -ty;
            double ny =  tx;

            drawSnakeBodyPattern(g, category, j,
                    snakeSplineX[j], snakeSplineY[j],
                    nx, ny, inWater);
        }

        // Vệt bọt nhỏ sau chóp đuôi khi bơi dưới nước
        if (inWater) {
            drawSnakeTailBubbleTrail(g, snakeSplineX[totalPts - 1], snakeSplineY[totalPts - 1], slitherTime);
        }

        // 5. Vẽ đầu rắn (Kích thước nhỉnh hơn thân 10-15%, mắt có specular glint, lưỡi thè thụt)
        drawSnakeHead(g, snake, snakeSplineX[0], snakeSplineY[0], segments.get(0).angle, category, inWater);

        // 6. Cảnh báo và thanh máu
        if (!isPlayer) {
            SnakeSegment head = segments.get(0);
            Point2D.Double hScreen = camera.worldToScreen(head.x, head.y);
            if (((AISnake) snake).isHuntingPlayer()) {
                drawOutlinedPixelString(g, "! SĂN MỒI", (int) hScreen.x - 30, (int) hScreen.y - 24,
                        new Color(239, 68, 68), HUD_TEXT_OUTLINE, FONT_PIXEL_LABEL, FONT_HUD_BOLD);
            }
            if (snake.getHealth() < snake.getMaxHealth()) {
                int bw = 38, bh = 4;
                int bx = (int) hScreen.x - bw / 2;
                int by = (int) hScreen.y - (((AISnake) snake).isHuntingPlayer() ? 36 : 22);
                g.setColor(new Color(15, 23, 42));
                g.fillRect(bx, by, bw, bh);
                double pct = Math.max(0.0, (double) snake.getHealth() / snake.getMaxHealth());
                g.setColor(new Color(239, 68, 68));
                g.fillRect(bx, by, (int) (bw * pct), bh);
                g.setColor(new Color(250, 204, 21));
                g.drawRect(bx, by, bw, bh);
            }
        }

        if (isPlayer && snake.isHidden()) {
            SnakeSegment head = segments.get(0);
            Point2D.Double hScreen = camera.worldToScreen(head.x, head.y);
            drawOutlinedPixelString(g, "[ẨN NẤP] (+4 HP/s)", (int) hScreen.x - 42, (int) hScreen.y - 26,
                    new Color(134, 239, 172), HUD_TEXT_OUTLINE, FONT_PIXEL_LABEL, FONT_HUD_BOLD);
        }

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
        g.setComposite(oldComp);
    }

    private void drawSnakeBodyPattern(Graphics2D g, int category, int j,
                                      double cx, double cy, double nx, double ny,
                                      boolean inWater) {
        int sx = (int) Math.round(cx);
        int sy = (int) Math.round(cy);

        switch (category) {
            case 0 -> {
                // CẤP 1: Garter Snake (Vằn sọc sống lưng vàng chanh + đốm sườn lục ngọc)
                g.setColor(SNAKE_LV1_STRIPE);
                g.fillRect(sx - 1, sy - 1, 3, 3);
                g.setColor(SNAKE_LV1_SPOT);
                int fx = (int) Math.round(cx + nx * 5.0 - 1);
                int fy = (int) Math.round(cy + ny * 5.0 - 1);
                g.fillRect(fx, fy, 2, 2);
            }
            case 1 -> {
                // CẤP 2: Shadow Viper (Khoanh tím neon + điểm lam dạ quang)
                g.setColor(SNAKE_LV2_BAND);
                g.fillRect(sx - 2, sy - 2, 4, 4);
                g.setColor(SNAKE_LV2_GLINT);
                int fx = (int) Math.round(cx + nx * 5.0 - 1);
                int fy = (int) Math.round(cy + ny * 5.0 - 1);
                g.fillRect(fx, fy, 2, 2);
            }
            case 2 -> {
                // CẤP 3: Hoàng Kim Cự Long (Vảy chữ V ruby đỏ + tâm vàng sáng)
                g.setColor(SNAKE_LV3_CHEVRON);
                g.fillRect(sx - 2, sy - 1, 4, 3);
                int fx = (int) Math.round(cx + nx * 4.5 - 1);
                int fy = (int) Math.round(cy + ny * 4.5 - 1);
                g.fillRect(fx, fy, 3, 2);
                g.setColor(new Color(254, 240, 138, 220));
                g.fillRect(sx - 1, sy, 2, 1);
            }
            case 3 -> {
                // CẤP 4: Huyết Diệm Hỏa Xà (Vằn ngọn lửa cam vàng)
                g.setColor(SNAKE_LV4_FLAME);
                g.fillRect(sx - 2, sy - 2, 4, 4);
                int fx = (int) Math.round(cx + nx * 4.0 - 1);
                int fy = (int) Math.round(cy + ny * 4.0 - 1);
                g.setColor(new Color(254, 240, 138, 230));
                g.fillRect(fx, fy, 3, 2);
            }
            case 4 -> {
                // CẤP 5: Kim Long Thần Xà (Khoanh tinh vân lam ngọc + vảy sáng)
                g.setColor(SNAKE_LV5_RING);
                g.fillRect(sx - 2, sy - 2, 4, 4);
                int fx = (int) Math.round(cx + nx * 4.5 - 1);
                int fy = (int) Math.round(cy + ny * 4.5 - 1);
                g.setColor(Color.WHITE);
                g.fillRect(fx, fy, 2, 2);
            }
            case 5 -> {
                // AI HUNTER: Vằn hổ đen đỏ xiên chéo sắc nét
                g.setColor(SNAKE_AI_HUNTER_STRIPE);
                int x1 = (int) Math.round(cx - nx * 3.5);
                int y1 = (int) Math.round(cy - ny * 3.5);
                int x2 = (int) Math.round(cx + nx * 7.5);
                int y2 = (int) Math.round(cy + ny * 7.5);
                g.drawLine(x1, y1, x2, y2);
                g.drawLine(x1, y1 + 1, x2, y2 + 1);
                // Đốm hổ phách điểm xuyết giữa vằn lưng
                g.setColor(new Color(254, 215, 170));
                g.fillRect(sx - 1, sy - 1, 2, 2);
            }
            case 6 -> {
                // AI FORAGER: Đốm hoa mai (Rosette) viền sẫm lõi vàng kim
                g.setColor(SNAKE_AI_FORAGER_ROSETTE);
                g.fillRect(sx - 2, sy - 2, 5, 5);
                g.setColor(SNAKE_AI_FORAGER_CORE);
                g.fillRect(sx - 1, sy - 1, 3, 3);

                int fx = (int) Math.round(cx + nx * 5.0 - 1);
                int fy = (int) Math.round(cy + ny * 5.0 - 1);
                g.setColor(SNAKE_AI_FORAGER_ROSETTE);
                g.fillRect(fx - 1, fy - 1, 3, 3);
            }
        }
    }

    private void drawSnakeTailBubbleTrail(Graphics2D g, double tx, double ty, double time) {
        g.setColor(COLOR_WATER_FOAM);
        for (int b = 1; b <= 3; b++) {
            double bTime = time * 3.5 + b * 2.1;
            int bx = (int) Math.round(tx - b * 5.5 + Math.sin(bTime) * 3.0);
            int by = (int) Math.round(ty + Math.cos(bTime * 0.9) * 2.5);
            int bSize = (b == 1) ? 2 : 1;
            g.fillRect(bx, by, bSize, bSize);
        }
    }

    private void drawSnakeHead(Graphics2D g, Snake snake, double cx, double cy, double angle, int category, boolean inWater) {
        AffineTransform old = g.getTransform();
        g.translate(cx, cy);
        g.rotate(angle);

        Color borderColor  = inWater ? SNAKE_OUTLINE_WATER[category] : SNAKE_OUTLINE_LAND[category];
        Color dorsalColor  = inWater ? SNAKE_BASE_COLOR_WATER[category] : SNAKE_BASE_COLOR[category];
        Color bellyColor   = inWater ? SNAKE_HIGHLIGHT_COLOR_WATER[category] : SNAKE_HIGHLIGHT_COLOR[category];
        double slitherTime = snake.getSlitherTime();

        // 1. Kích thước đa giác đầu rồng/rắn sắc nét (Độ rộng tối đa 20px, nhỉnh hơn thân 18px đúng 11%, không quá 10-15%)
        int snoutX, snoutUpperX, snoutW, cheekX, cheekW, jawX, jawW, neckX;
        cheekW = 10;
        switch (category) {
            case 0 -> { // Level 1 (Garter / Colubrid - Đầu tròn mềm)
                snoutX = 14; snoutUpperX = 10; snoutW = 5;
                cheekX = -3;
                jawX = -11;  jawW = 8;
                neckX = -13;
            }
            case 1 -> { // Level 2 (Shadow Viper - Đầu tam giác sắc nhọn)
                snoutX = 16; snoutUpperX = 10; snoutW = 4;
                cheekX = -4;
                jawX = -12;  jawW = 7;
                neckX = -14;
            }
            case 2 -> { // Level 3 (Apex Titan Boa - Đầu có vương miện rồng)
                snoutX = 16; snoutUpperX = 11; snoutW = 5;
                cheekX = -4;
                jawX = -13;  jawW = 8;
                neckX = -15;
            }
            case 3 -> { // Level 4 (Crimson Fire Drake - Đầu hỏa xà)
                snoutX = 17; snoutUpperX = 11; snoutW = 5;
                cheekX = -4;
                jawX = -13;  jawW = 8;
                neckX = -15;
            }
            case 4 -> { // Level 5 (Celestial Leviathan - Đầu kim long thần xà)
                snoutX = 18; snoutUpperX = 12; snoutW = 5;
                cheekX = -4;
                jawX = -14;  jawW = 8;
                neckX = -16;
            }
            case 5 -> { // AI Hunter (Đầu vát tam giác hung hãn)
                snoutX = 16; snoutUpperX = 10; snoutW = 4;
                cheekX = -4;
                jawX = -12;  jawW = 7;
                neckX = -14;
            }
            default -> { // AI Forager (Đầu tròn nhỏ nhẹ nhàng)
                snoutX = 14; snoutUpperX = 10; snoutW = 5;
                cheekX = -3;
                jawX = -11;  jawW = 8;
                neckX = -13;
            }
        }

        // Thiết lập tọa độ 8 đỉnh đa giác đầu (Tái sử dụng bộ nhớ đệm, Zero-GC)
        headBorderX[0] = snoutX;        headBorderY[0] = 0;
        headBorderX[1] = snoutUpperX;   headBorderY[1] = -snoutW;
        headBorderX[2] = cheekX;        headBorderY[2] = -cheekW;
        headBorderX[3] = jawX;          headBorderY[3] = -jawW;
        headBorderX[4] = neckX;         headBorderY[4] = 0;
        headBorderX[5] = jawX;          headBorderY[5] = jawW;
        headBorderX[6] = cheekX;        headBorderY[6] = cheekW;
        headBorderX[7] = snoutUpperX;   headBorderY[7] = snoutW;

        headInnerX[0] = snoutX - 1;      headInnerY[0] = 0;
        headInnerX[1] = snoutUpperX;     headInnerY[1] = -(snoutW - 1);
        headInnerX[2] = cheekX;          headInnerY[2] = -(cheekW - 1);
        headInnerX[3] = jawX + 1;        headInnerY[3] = -(jawW - 1);
        headInnerX[4] = neckX + 1;       headInnerY[4] = 0;
        headInnerX[5] = jawX + 1;        headInnerY[5] = jawW - 1;
        headInnerX[6] = cheekX;          headInnerY[6] = cheekW - 1;
        headInnerX[7] = snoutUpperX;     headInnerY[7] = snoutW - 1;

        // 2. Hiệu ứng bọt nước / hào quang đặc biệt theo cấp
        if (category == 0 && (inWater || snake.isSprinting())) {
            g.setColor(new Color(56, 189, 248, 95));
            g.drawOval(-16, -14, 36, 28);
            g.setColor(new Color(186, 230, 253, 60));
            g.fillOval(-16, -14, 36, 28);
        } else if (category == 1) {
            // Khí độc tím quanh đầu
            g.setColor(new Color(168, 85, 247, 65));
            g.fillOval(-20, -16, 42, 32);
        } else if (category == 2) {
            // Hào quang Chúa Tể Hoàng Kim
            g.setColor(new Color(250, 204, 21, 75));
            g.fillOval(-26, -20, 52, 40);
        } else if (category == 3) {
            // Hào quang lửa đỏ bốc cháy
            g.setColor(new Color(239, 68, 68, 80));
            g.fillOval(-30, -22, 60, 44);
            g.setColor(new Color(249, 115, 22, 60));
            g.fillOval(-26, -18, 52, 36);
        } else if (category == 4) {
            // Hào quang Thần Thánh Tối Thượng
            g.setColor(new Color(250, 204, 21, 90));
            g.fillOval(-36, -26, 72, 52);
            g.setColor(new Color(56, 189, 248, 75));
            g.drawOval(-40, -30, 80, 60);
        }

        // 3. Lưỡi thè nhấp nháy (Flickering forked tongue - chuyển động nhanh nhạy thực tế)
        double tongueCycle = Math.sin(slitherTime * 11.0);
        if (tongueCycle > 0.15) {
            double extend = (tongueCycle - 0.15) / 0.85;
            int tipX = snoutX + (int) Math.round(extend * (category >= 2 ? 11.0 : 8.0));
            int forkLen = (int) Math.round(3.0 + extend * 3.0);
            Color tongueColor = switch (category) {
                case 1 -> new Color(217, 70, 239); // Tím phát quang
                case 2 -> new Color(239, 68, 68);  // Đỏ rồng hoàng kim
                case 3 -> new Color(251, 146, 60); // Cam hỏa xà
                case 4 -> new Color(56, 189, 248); // Lam ngọc thần long
                case 5 -> new Color(239, 68, 68);  // Đỏ săn mồi
                case 6 -> new Color(244, 114, 182); // Hồng thợ săn
                default -> new Color(239, 68, 68); // Đỏ tươi
            };
            g.setColor(tongueColor);
            g.fillRect(snoutX - 1, -1, tipX - snoutX + 1, 2);
            g.drawLine(tipX, 0, tipX + forkLen, -forkLen);
            g.drawLine(tipX, 0, tipX + forkLen,  forkLen);
        }

        // 4. Các phụ kiện phía sau đầu (Sừng, Vương miện) trước khi vẽ khối đầu chính
        if (category == 1) {
            // Cặp sừng gai độc tím vuốt ngược
            g.setColor(new Color(30, 27, 75));
            g.fillPolygon(new int[]{-6, -18, -14}, new int[]{-10, -17, -9}, 3);
            g.fillPolygon(new int[]{-6, -18, -14}, new int[]{10, 17, 9}, 3);
            g.setColor(new Color(192, 132, 252));
            g.drawLine(-6, -10, -18, -17);
            g.drawLine(-6, 10, -18, 17);
            g.setColor(new Color(34, 211, 238));
            g.fillRect(-18, -18, 2, 2);
            g.fillRect(-18, 16, 2, 2);
        } else if (category == 2) {
            // Vương miện sừng rồng hoàng kim 3 đỉnh
            g.setColor(new Color(180, 83, 9));
            g.fillPolygon(new int[]{-10, -25, -16}, new int[]{-4, 0, 4}, 3);
            g.fillPolygon(new int[]{-8, -21, -14}, new int[]{-10, -18, -6}, 3);
            g.fillPolygon(new int[]{-8, -21, -14}, new int[]{10, 18, 6}, 3);
            g.setColor(new Color(250, 204, 21));
            g.fillPolygon(new int[]{-10, -24, -16}, new int[]{-3, 0, 3}, 3);
            g.fillPolygon(new int[]{-8, -20, -14}, new int[]{-9, -17, -6}, 3);
            g.fillPolygon(new int[]{-8, -20, -14}, new int[]{9, 17, 6}, 3);
            // Viên ngọc ruby đỏ
            g.setColor(new Color(220, 38, 38));
            g.fillOval(-8, -4, 8, 8);
            g.setColor(Color.WHITE);
            g.fillRect(-6, -2, 2, 2);
        } else if (category == 3) {
            // Tứ đại hỏa sừng obsidian và hỏa diệm
            g.setColor(new Color(69, 10, 10));
            g.fillPolygon(new int[]{-8, -26, -16}, new int[]{-12, -22, -8}, 3);
            g.fillPolygon(new int[]{-8, -26, -16}, new int[]{12, 22, 8}, 3);
            g.fillPolygon(new int[]{-12, -28, -18}, new int[]{-5, -12, -2}, 3);
            g.fillPolygon(new int[]{-12, -28, -18}, new int[]{5, 12, 2}, 3);
            g.setColor(new Color(239, 68, 68));
            g.fillPolygon(new int[]{-8, -25, -16}, new int[]{-11, -21, -8}, 3);
            g.fillPolygon(new int[]{-8, -25, -16}, new int[]{11, 21, 8}, 3);
            g.setColor(new Color(251, 146, 60));
            g.fillRect(-26, -23, 3, 3);
            g.fillRect(-26, 21, 3, 3);
        } else if (category == 4) {
            // Vương miện long tộc & sừng hư không chóp lam ngọc
            g.setColor(new Color(113, 63, 18));
            g.fillPolygon(new int[]{-10, -32, -20}, new int[]{-12, -26, -8}, 3);
            g.fillPolygon(new int[]{-10, -32, -20}, new int[]{12, 26, 8}, 3);
            g.setColor(new Color(250, 204, 21));
            g.fillPolygon(new int[]{-10, -31, -20}, new int[]{-11, -25, -8}, 3);
            g.fillPolygon(new int[]{-10, -31, -20}, new int[]{11, 25, 8}, 3);
            g.setColor(new Color(56, 189, 248));
            g.fillRect(-32, -27, 4, 4);
            g.fillRect(-32, 24, 4, 4);
            // Vương miện 3 đỉnh giữa trán
            g.setColor(new Color(250, 204, 21));
            g.fillPolygon(new int[]{-14, -28, -20}, new int[]{-4, 0, 4}, 3);
        }

        // 5. Vẽ khối đầu chính: Đa giác tam giác khiên sắc nét (Viền ngoài cùng gam sẫm 35% & Lõi lưng sáng)
        g.setColor(borderColor);
        g.fillPolygon(headBorderX, headBorderY, 8);

        g.setColor(dorsalColor);
        g.fillPolygon(headInnerX, headInnerY, 8);

        // Mảng màu sáng trên đỉnh đầu / sống mũi
        g.setColor(bellyColor);
        g.fillOval(cheekX, -snoutW / 2, (snoutX - cheekX) - 5, Math.max(4, snoutW));

        // Họa tiết đặc trưng trên đầu
        if (category == 1) {
            // Sọc lam ngọc trên trán
            g.setColor(new Color(6, 182, 212));
            g.fillOval(-6, -3, 14, 6);
        } else if (category == 2) {
            // Vảy sống mũi ruby & râu rồng
            g.setColor(new Color(220, 38, 38));
            g.fillOval(-4, -4, 12, 8);
            g.setColor(new Color(254, 240, 138));
            g.fillRect(-2, -2, 8, 2);
            // Râu rồng
            double whisker = Math.sin(slitherTime * 8.0) * 4.0;
            g.setColor(new Color(250, 204, 21));
            g.drawLine(10, -12, (int)(4 + whisker), -20);
            g.drawLine(10, 12, (int)(4 + whisker), 20);
            // Nanh rồng trắng ngọc
            g.setColor(Color.WHITE);
            g.fillPolygon(new int[]{14, 18, 12}, new int[]{-9, -12, -7}, 3);
            g.fillPolygon(new int[]{14, 18, 12}, new int[]{9, 12, 7}, 3);
        } else if (category == 3) {
            // Giáp sống mũi obsidian & nanh hỏa xà
            g.setColor(new Color(15, 23, 42));
            g.fillOval(-6, -4, 14, 8);
            g.setColor(new Color(249, 115, 22));
            g.fillRect(-3, -2, 10, 2);
            g.setColor(Color.WHITE);
            g.fillPolygon(new int[]{16, 21, 14}, new int[]{-10, -13, -8}, 3);
            g.fillPolygon(new int[]{16, 21, 14}, new int[]{10, 13, 8}, 3);
            // Lỗ mũi phát tàn lửa
            g.setColor(new Color(254, 240, 138));
            g.fillRect(15, -4, 2, 2);
            g.fillRect(15, 3, 2, 2);
        } else if (category == 4) {
            // Râu thần long khổng lồ
            double whisker = Math.sin(slitherTime * 7.0) * 6.0;
            g.setColor(new Color(250, 204, 21));
            g.drawLine(12, -14, (int)(2 + whisker), -26);
            g.drawLine(12, 14, (int)(2 + whisker), 26);
            g.setColor(new Color(56, 189, 248));
            g.drawLine((int)(2 + whisker), -26, (int)(-6 + whisker), -32);
            g.drawLine((int)(2 + whisker), 26, (int)(-6 + whisker), 32);
            // Long châu vũ trụ trên trán
            g.setColor(new Color(2, 132, 199));
            g.fillOval(-8, -5, 10, 10);
            g.setColor(new Color(56, 189, 248));
            g.fillOval(-6, -3, 6, 6);
            g.setColor(Color.WHITE);
            g.fillRect(-4, -1, 2, 2);
            // Nanh rồng ngọc bích
            g.setColor(new Color(240, 253, 250));
            g.fillPolygon(new int[]{18, 24, 15}, new int[]{-11, -14, -8}, 3);
            g.fillPolygon(new int[]{18, 24, 15}, new int[]{11, 14, 8}, 3);
        } else if (category == 5) {
            // AI Hunter: Vằn hổ sẫm & răng nanh săn mồi
            g.setColor(new Color(69, 10, 10));
            g.drawLine(2, -5, 0, -2);
            g.drawLine(2, 5, 0, 2);
            g.drawLine(-4, -7, -5, -3);
            g.drawLine(-4, 7, -5, 3);
            g.fillRect(-1, -1, 3, 2);
            g.setColor(Color.WHITE);
            g.fillPolygon(new int[]{11, 14, 9}, new int[]{-7, -9, -6}, 3);
            g.fillPolygon(new int[]{11, 14, 9}, new int[]{7, 9, 6}, 3);
        } else if (category == 6) {
            // AI Forager: Đốm hoa mai nhỏ trên đỉnh đầu
            g.setColor(new Color(63, 98, 18));
            g.fillRect(1, -2, 2, 2);
            g.fillRect(1, 1, 2, 2);
            g.fillRect(-4, -3, 2, 2);
            g.fillRect(-4, 1, 2, 2);
            g.setColor(new Color(234, 179, 8));
            g.fillRect(-1, -1, 2, 2);
        }

        // 2 lỗ mũi nhỏ xinh ở chóp mõm
        g.setColor(borderColor);
        g.fillRect(snoutX - 4, -2, 2, 1);
        g.fillRect(snoutX - 4, 1, 2, 1);

        // 6. ĐÔI MẮT RẮN SẮC SẢO VỚI ĐIỂM SÁNG PHẢN QUANG (SPECULAR GLINT)
        int eyeX = switch (category) {
            case 0 -> 4;
            case 1 -> 5;
            case 2 -> 5;
            case 3 -> 5;
            case 4 -> 6;
            case 5 -> 5;
            default -> 4;
        };
        int eyeYDist = 6;
        int eyeR = 3;

        int[] eyesY = { -eyeYDist, eyeYDist };
        for (int ey : eyesY) {
            // Viền mắt sắc nét
            g.setColor(new Color(15, 23, 42));
            g.fillOval(eyeX - eyeR, ey - eyeR, eyeR * 2 + 1, eyeR * 2 + 1);

            // Tròng mắt rực rỡ theo loài
            Color irisColor = switch (category) {
                case 0 -> new Color(254, 240, 138); // Vàng kem sáng
                case 1 -> new Color(34, 211, 238);  // Cyan neon rực sáng
                case 2 -> new Color(249, 115, 22);  // Cam lửa hoàng kim
                case 3 -> new Color(251, 146, 60);  // Hổ phách rực lửa
                case 4 -> new Color(56, 189, 248);  // Lam tinh vũ trụ
                case 5 -> new Color(239, 68, 68);   // Đỏ cam hung hãn
                default -> new Color(132, 204, 22); // Xanh lục bảo tinh anh
            };
            g.setColor(irisColor);
            g.fillOval(eyeX - eyeR + 1, ey - eyeR + 1, eyeR * 2 - 1, eyeR * 2 - 1);

            // Con ngươi rắn
            g.setColor(new Color(15, 23, 42));
            if (category == 0) {
                // Cấp 1: Con ngươi tròn to linh hoạt
                g.fillOval(eyeX - 1, ey - 2, 4, 4);
            } else {
                // Cấp 2-5 và AI: Con ngươi dọc sắc bén (Viper slit)
                g.fillRect(eyeX, ey - eyeR + 1, 2, eyeR * 2 - 1);
            }

            // Điểm sáng phản quang trắng (Specular Reflection Glint)
            g.setColor(Color.WHITE);
            g.fillRect(eyeX + 1, ey - 2, 2, 2);

            // Ánh sáng li ti góc dưới
            if (category == 0 || category == 4) {
                g.setColor(category == 0 ? new Color(253, 224, 71) : new Color(224, 242, 254));
                g.fillRect(eyeX - 1, ey + 1, 1, 1);
            }
        }

        g.setTransform(old);
    }


    private void drawShark(Graphics2D g, Camera camera, AISnake shark) {
        Point2D.Double pos = camera.worldToScreen(shark.getX(), shark.getY());
        AffineTransform old = g.getTransform();
        g.translate(pos.x, pos.y);
        g.rotate(shark.getAngle());

        double swimWave = Math.sin(shark.getSlitherTime() * 8.0);
        double tailWag = swimWave * 7.0;

        // 1. Vệt rẽ sóng nước màu trắng xanh (White Wake Foam)
        g.setColor(new Color(224, 242, 254, 120));
        g.drawArc(-52, (int) (-14 + tailWag * 0.6), 38, 28, 130, 100);
        g.drawArc(-62, (int) (-18 + tailWag), 48, 36, 130, 100);

        // 2. Vây đuôi cá mập (Heterocercal Caudal Fin) vung vẩy mạnh mẽ
        g.setColor(new Color(30, 41, 59)); // Viền đuôi
        int[] tailX = { -36, -56, -46, -54 };
        int[] tailY = { (int) (tailWag * 0.5), (int) (-18 + tailWag), (int) (tailWag), (int) (14 + tailWag) };
        g.fillPolygon(tailX, tailY, 4);

        g.setColor(new Color(51, 65, 85)); // Lõi vây đuôi
        int[] tailInX = { -35, -53, -45, -51 };
        int[] tailInY = { (int) (tailWag * 0.5), (int) (-16 + tailWag), (int) (tailWag), (int) (12 + tailWag) };
        g.fillPolygon(tailInX, tailInY, 4);

        // 3. Hai vây ngực (Pectoral Fins) dang rộng hình tam giác khí động học
        g.setColor(new Color(51, 65, 85));
        g.fillPolygon(new int[]{ -4, -18, 6 }, new int[]{ -12, -28, -12 }, 3);
        g.fillPolygon(new int[]{ -4, -18, 6 }, new int[]{ 12, 28, 12 }, 3);
        g.setColor(new Color(30, 41, 59));
        g.drawPolygon(new int[]{ -4, -18, 6 }, new int[]{ -12, -28, -12 }, 3);
        g.drawPolygon(new int[]{ -4, -18, 6 }, new int[]{ 12, 28, 12 }, 3);

        // 4. Thân cá mập hình ngư lôi (Torpedo Predator Body)
        g.setColor(new Color(15, 23, 42)); // Viền ngoài
        g.fillOval(-38, -15, 78, 30);

        // Lưng cá mập màu xám xanh biển sâu (Dorsal slate)
        g.setColor(new Color(51, 65, 85));
        g.fillOval(-36, -14, 74, 28);

        // Bụng cá mập sáng màu bạc (Ventral countershading)
        g.setColor(new Color(226, 232, 240));
        g.fillOval(-28, -2, 58, 12);

        // 5. Vây lưng cá mập (Dorsal Fin nhô lên rẽ nước)
        g.setColor(new Color(30, 41, 59));
        g.fillPolygon(new int[]{ -6, -16, 8 }, new int[]{ 0, -18, 0 }, 3);
        g.setColor(new Color(71, 85, 105));
        g.fillPolygon(new int[]{ -5, -14, 7 }, new int[]{ 0, -16, 0 }, 3);

        // 6. 3 khe mang (Gill slits) bên thân
        g.setColor(new Color(15, 23, 42));
        for (int i = 0; i < 3; i++) {
            g.drawLine(4 + i * 4, -8, 2 + i * 4, -2);
            g.drawLine(4 + i * 4, 8, 2 + i * 4, 2);
        }

        // 7. Hàm răng sắc nhọn & Mõm nhọn
        g.setColor(Color.WHITE);
        for (int i = 0; i < 4; i++) {
            g.drawLine(24 + i * 3, -4, 26 + i * 3, -1);
            g.drawLine(24 + i * 3, 4, 26 + i * 3, 1);
        }

        // 8. Đôi mắt săn mồi hung hãn
        g.setColor(new Color(15, 23, 42));
        g.fillOval(18, -10, 6, 5);
        g.fillOval(18, 5, 6, 5);
        g.setColor(new Color(239, 68, 68)); // Mắt đỏ săn mồi
        g.fillOval(19, -9, 4, 3);
        g.fillOval(19, 6, 4, 3);
        g.setColor(Color.BLACK);
        g.fillRect(20, -9, 2, 3);
        g.fillRect(20, 6, 2, 3);

        // Phủ lớp depth tint mỏng theo độ sâu đại dương
        Composite oldSharkComp = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, TINT_ALPHA_CREATURE));
        g.setColor(COLOR_UNDERWATER_TINT);
        g.fillOval(-42, -18, 86, 36);
        g.setComposite(oldSharkComp);

        g.setTransform(old);

        // Thanh máu nhỏ & Tên cá mập trên đầu
        int barW = 46;
        int barH = 5;
        int bx = (int) pos.x - barW / 2;
        int by = (int) pos.y - 28;

        g.setColor(new Color(15, 23, 42, 200));
        g.fillRoundRect(bx - 1, by - 1, barW + 2, barH + 2, 4, 4);

        double hpRatio = Math.max(0.0, Math.min(1.0, (double) shark.getHealth() / shark.getMaxHealth()));
        g.setColor(new Color(239, 68, 68));
        g.fillRoundRect(bx, by, (int) (barW * hpRatio), barH, 3, 3);

        g.setFont(FONT_SMALL_BOLD);
        String title = "CÁ MẬP [C" + shark.getEvolutionLevel() + "]";
        int tw = g.getFontMetrics().stringWidth(title);
        g.setColor(Color.BLACK);
        g.drawString(title, (int) pos.x - tw / 2 + 1, by - 3);
        g.setColor(new Color(224, 242, 254));
        g.drawString(title, (int) pos.x - tw / 2, by - 4);
    }

    private void drawRock(Graphics2D g, double cx, double cy, int w, int h) {
        drawRock(g, cx, cy, w, h, 0);
    }

    private void drawRock(Graphics2D g, double cx, double cy, int w, int h, int variant) {
        int x = (int) cx;
        int y = (int) cy;
        int v = Math.abs(variant) % 4;

        // Bảng màu đá tảng tự nhiên: Ánh sáng chiếu từ trên-trái (Top-Left Light)
        Color colLitHighlight = new Color(175, 190, 205); // Gờ phản quang trên-trái
        Color colLitFacet     = new Color(105, 122, 142); // Mặt đón nắng trên-trái
        Color colMidBody      = new Color(68, 82, 102);   // Thân đá trung tính
        Color colShadowFacet  = new Color(34, 44, 60);    // Mặt khuất sườn dưới-phải
        Color colDeepCrevice  = new Color(18, 24, 34);    // Khe nứt & chân đá sẫm
        Color colMossDark     = new Color(22, 101, 52);   // Mảng rêu nhung sẫm
        Color colMossLight    = new Color(34, 197, 94);   // Rêu non đón nắng

        switch (v) {
            case 0 -> {
                // BIẾN THỂ 0: ĐÁ TẢNG GÓC CẠNH BẤT ĐỐI XỨNG (Craggy Angular Boulder)
                int[] px = { x - w/2 + 2, x - w/4, x + w/5, x + w/2 - 2, x + w/2, x + w/3, x - w/3, x - w/2 };
                int[] py = { y - h/6, y - h/2 + 1, y - h/2 + 3, y - h/6, y + h/3, y + h/2, y + h/2 - 2, y + h/4 };

                // Thân đá trung tính
                g.setColor(colMidBody);
                g.fillPolygon(px, py, px.length);

                // Mặt trên-trái đón nắng
                int[] litX = { x - w/2 + 3, x - w/4, x + w/5, x - 2, x - w/3 };
                int[] litY = { y - h/6, y - h/2 + 2, y - h/2 + 4, y - 2, y + h/6 };
                g.setColor(colLitFacet);
                g.fillPolygon(litX, litY, litX.length);

                // Gờ đón nắng trên cùng
                g.setColor(colLitHighlight);
                g.drawLine(x - w/4 + 1, y - h/2 + 2, x + w/5 - 1, y - h/2 + 4);
                g.drawLine(x - w/2 + 3, y - h/6, x - w/4, y - h/2 + 2);

                // Mặt dưới-phải đổ bóng
                int[] shdX = { x - 2, x + w/5, x + w/2 - 2, x + w/2, x + w/3 };
                int[] shdY = { y - 2, y - h/2 + 4, y - h/6, y + h/3, y + h/2 };
                g.setColor(colShadowFacet);
                g.fillPolygon(shdX, shdY, shdX.length);

                // Vết nứt khe đá
                g.setColor(colDeepCrevice);
                g.drawLine(x - 4, y - h/4, x + 2, y + 2);
                g.drawLine(x + 2, y + 2, x + 8, y + 1);

                // Mảng rêu bám trên gờ đỉnh đón nắng
                g.setColor(colMossDark);
                g.fillRect(x - w/4 + 2, y - h/2 + 4, 8, 4);
                g.fillRect(x - w/4 + 4, y - h/2 + 8, 6, 3);
                g.setColor(colMossLight);
                g.fillRect(x - w/4 + 3, y - h/2 + 5, 5, 2);
            }
            case 1 -> {
                // BIẾN THỂ 1: CẶP ĐÁ TẢNG ĐÔI CHỒNG KỀ (Twin Stacked Boulders)
                // Tảng phụ bên phải nép sau
                int r2w = (int) (w * 0.52);
                int r2h = (int) (h * 0.72);
                int r2x = x + w/6;
                int r2y = y + h/8;

                g.setColor(colShadowFacet);
                g.fillOval(r2x - r2w/2, r2y - r2h/2, r2w, r2h);
                g.setColor(colMidBody);
                g.fillOval(r2x - r2w/2 + 2, r2y - r2h/2 + 2, r2w - 4, r2h - 4);
                g.setColor(colLitFacet);
                g.drawArc(r2x - r2w/2 + 2, r2y - r2h/2 + 2, r2w - 4, r2h - 4, 45, 110);

                // Tảng chính bên trái đè lên
                int r1w = (int) (w * 0.65);
                int r1h = (int) (h * 0.88);
                int r1x = x - w/8;
                int r1y = y - h/12;

                g.setColor(colMidBody);
                g.fillOval(r1x - r1w/2, r1y - r1h/2, r1w, r1h);

                // Mặt đón sáng trên-trái
                g.setColor(colLitFacet);
                g.fillOval(r1x - r1w/2 + 2, r1y - r1h/2 + 2, (int)(r1w * 0.75), (int)(r1h * 0.75));
                g.setColor(colLitHighlight);
                g.drawArc(r1x - r1w/2 + 3, r1y - r1h/2 + 2, r1w - 6, r1h - 5, 70, 110);

                // Rãnh tiếp giáp tối màu giữa 2 tảng
                g.setColor(colDeepCrevice);
                g.drawLine(x + 2, y - h/4, x + 4, y + h/3);

                // Rêu xanh phát triển trong khe tiếp giáp
                g.setColor(colMossDark);
                g.fillRect(x - 1, y - h/8, 5, 8);
                g.fillRect(x + 1, y + h/10, 4, 6);
                g.setColor(colMossLight);
                g.fillRect(x, y - h/12, 3, 5);
            }
            case 2 -> {
                // BIẾN THỂ 2: PHIẾN ĐÁ PHẲNG TRẢI THẢM RÊU (Flat Shelf Rock with Moss Carpet)
                int[] px = { x - w/2, x - w/3, x + w/3, x + w/2, x + w/2 - 2, x + w/4, x - w/4, x - w/2 + 2 };
                int[] py = { y - h/4, y - h/2 + 2, y - h/2 + 1, y - h/5, y + h/3, y + h/2, y + h/2, y + h/4 };

                g.setColor(colMidBody);
                g.fillPolygon(px, py, px.length);

                // Mặt thềm trên phẳng đón nắng
                g.setColor(colLitFacet);
                g.fillRect(x - w/3, y - h/2 + 3, (int)(w * 0.65), Math.max(4, h / 3));

                // Viền sáng trên cùng
                g.setColor(colLitHighlight);
                g.drawLine(x - w/3, y - h/2 + 2, x + w/3, y - h/2 + 1);

                // Mặt dựng bên phải đổ bóng
                g.setColor(colShadowFacet);
                g.fillRect(x + w/8, y - h/6, (int)(w * 0.32), Math.max(4, h / 2));

                // Thảm rêu nhung trải rộng trên mặt phẳng phiến đá
                g.setColor(colMossDark);
                g.fillRect(x - w/4, y - h/2 + 4, (int)(w * 0.52), 6);
                g.fillRect(x - w/5, y - h/2 + 8, (int)(w * 0.40), 4);
                g.setColor(colMossLight);
                g.fillRect(x - w/5 + 2, y - h/2 + 5, (int)(w * 0.35), 3);

                // Sỏi nhỏ dưới chân phiến đá
                g.setColor(colLitFacet);
                g.fillOval(x - w/2 - 2, y + h/3, 6, 4);
                g.fillOval(x + w/3, y + h/3 + 2, 5, 3);
            }
            case 3 -> {
                // BIẾN THỂ 3: MỎM ĐÁ THÁP NHỌN VÁT CẠNH (Pyramidal Weathered Crag)
                // Đỉnh nhọn lệch trái
                int topX = x - w/8;
                int topY = y - h/2;

                // Sườn trái đón nắng
                int[] leftX = { x - w/2, topX, x, x - w/4 };
                int[] leftY = { y + h/4, topY, y + h/3, y + h/2 };
                g.setColor(colLitFacet);
                g.fillPolygon(leftX, leftY, 4);

                // Sườn phải đổ bóng
                int[] rightX = { topX, x + w/2, x + w/3, x };
                int[] rightY = { topY, y + h/6, y + h/2, y + h/3 };
                g.setColor(colShadowFacet);
                g.fillPolygon(rightX, rightY, 4);

                // Sống đá giữa chia đôi đỉnh
                g.setColor(colLitHighlight);
                g.drawLine(topX, topY, x, y + h/3);

                // Kẽ nứt sườn phải
                g.setColor(colDeepCrevice);
                g.drawLine(x + 2, y, x + 8, y + h/4);

                // Mảng rêu bám chân đá vách trái
                g.setColor(colMossDark);
                g.fillRect(x - w/3, y + h/8, 7, 6);
                g.fillRect(x - w/4, y, 5, 5);
                g.setColor(colMossLight);
                g.fillRect(x - w/3 + 1, y + h/8 + 1, 4, 3);
            }
        }
    }

    // =========================================================================
    // HẰNG SỐ CẤU HÌNH ĐÁ NGẦM ĐẠI DƯƠNG (SUBMERGED REEF ROCKS - MÀN 2)
    // Tinh chỉnh: Nhuộm màu nước, vân sáng caustics, độ sâu, viền mờ 50%, không bóng tròn
    // =========================================================================
    public static final float REEF_WATER_OVERLAY_ALPHA = 0.24f;  // Độ phủ lớp xanh nước mỏng lên đá (0.20 - 0.30)
    public static final float REEF_CAUSTICS_INTENSITY  = 0.22f;  // Cường độ vân sáng gợn sóng caustics (0.15 - 0.25)
    public static final float REEF_SHADOW_ALPHA        = 0.28f;  // Độ mờ bóng chân đá đáy biển (0.25 - 0.30)
    public static final float REEF_OUTLINE_ALPHA       = 0.50f;  // Độ mờ viền mảnh cùng gam (~50%, không dùng đen thuần)
    public static final double REEF_CAUSTICS_SPEED     = 1.1;    // Tốc độ chuyển động chậm của vân sáng caustics

    // Bảng màu đá nhuộm theo màu nước biển (chênh lệch sáng/tối chỉ 15-20%, tuyệt đối không dùng trắng/xám sáng)
    public static final Color REEF_COLOR_TOP    = new Color(47, 111, 120); // #2f6f78 (Mặt đỉnh đón sáng dưới nước)
    public static final Color REEF_COLOR_MID    = new Color(37, 86, 95);   // #25565f (Mặt sườn / tầng bậc giữa)
    public static final Color REEF_COLOR_DARK   = new Color(28, 63, 74);   // #1c3f4a (Mặt khuất / khe nứt)
    public static final Color REEF_COLOR_DEEP   = new Color(21, 46, 55);   // #152e37 (Chân đá chìm sâu vào đáy)
    public static final Color REEF_COLOR_BROWN  = new Color(44, 62, 66);   // Mảng đá xám nâu pha xanh nước
    public static final Color REEF_OUTLINE_COL  = new Color(16, 40, 48, (int) (255 * REEF_OUTLINE_ALPHA));
    public static final Color REEF_WATER_TINT   = new Color(8, 92, 124, (int) (255 * REEF_WATER_OVERLAY_ALPHA));

    private void drawSubmergedReef(Graphics2D g, double cx, double cy, int w, int h) {
        drawSubmergedReef(g, cx, cy, w, h, 0);
    }

    private void drawSubmergedReef(Graphics2D g, double cx, double cy, int w, int h, int variant) {
        int x = (int) cx;
        int y = (int) cy;

        // 1. Chân đá hòa vào đáy: Các viên sỏi nhỏ, cát cuội quanh chân đá không tạo đường thẳng
        // (Bóng đổ đã được renderShadows xử lý đồng nhất bằng shadow cache)

        // Các viên sỏi nhỏ, cát cuội quanh chân đá không tạo đường thẳng
        g.setColor(new Color(24, 52, 62, 180));
        g.fillOval(x - w / 2 + 2, y + h / 4 + 4, 10, 6);
        g.fillOval(x + w / 2 - 12, y + h / 4 + 6, 12, 7);
        g.fillOval(x - w / 4, y + h / 4 + 8, 8, 5);
        g.fillOval(x + w / 4, y + h / 4 + 7, 9, 6);

        // 2. Vẽ cụm khối đá ngầm đa tầng bậc theo biến thể
        switch (variant % 3) {
            case 0 -> drawTwinPeakReef(g, x, y, w, h);
            case 1 -> drawSteppedShelfReef(g, x, y, w, h);
            default -> drawMonolithReef(g, x, y, w, h);
        }

        // 3. Ánh sáng dưới nước: Lớp vân sáng gợn sóng (Caustics) phủ lên mặt đá
        drawReefCaustics(g, x, y, w, h, variant);

        // 5. Vài bong bóng nhỏ bay lên thưa thớt từ khe đá (bỏ hẳn vòng tròn bọt sóng)
        drawReefSparseBubbles(g, x, y, 3);
    }

    // =========================================================================
    // BIẾN THỂ 0: MỎM ĐÁ NGẦM ĐÔI (TWIN-PEAK REEF CRAG)
    // 2 Khối đá ghép đôi: Đỉnh trái vừa, đỉnh phải cao, yên đá ở giữa
    // =========================================================================
    private void drawTwinPeakReef(Graphics2D g, int x, int y, int w, int h) {
        // --- KHỐI ĐÁ TRÁI (MID PEAK) ---
        // 1. Chân khối đá trái chìm sâu vào đáy
        int[] bLeftX = { x - 52, x - 10, x - 6, x - 48 };
        int[] bLeftY = { y + 16, y + 18, y + 8, y + 6 };
        g.setColor(REEF_COLOR_DEEP);
        g.fillPolygon(bLeftX, bLeftY, 4);

        // 2. Mặt dựng sườn trái / khuất
        int[] fLeftDarkX = { x - 48, x - 28, x - 24, x - 46 };
        int[] fLeftDarkY = { y + 6, y + 4, y - 16, y - 8 };
        g.setColor(REEF_COLOR_DARK);
        g.fillPolygon(fLeftDarkX, fLeftDarkY, 4);

        // 3. Mặt sườn trước đón sáng
        int[] fLeftMidX = { x - 28, x - 6, x - 12, x - 24 };
        int[] fLeftMidY = { y + 4, y + 6, y - 18, y - 16 };
        g.setColor(REEF_COLOR_MID);
        g.fillPolygon(fLeftMidX, fLeftMidY, 4);

        // 4. Thềm bậc thang trung gian
        int[] tLeftShelfX = { x - 44, x - 16, x - 20, x - 40 };
        int[] tLeftShelfY = { y - 8, y - 16, y - 22, y - 18 };
        g.setColor(REEF_COLOR_BROWN);
        g.fillPolygon(tLeftShelfX, tLeftShelfY, 4);

        // 5. Mặt đỉnh mỏm trái (Đón sáng dịu dưới nước)
        int[] tLeftTopX = { x - 40, x - 20, x - 24, x - 36 };
        int[] tLeftTopY = { y - 18, y - 22, y - 28, y - 26 };
        g.setColor(REEF_COLOR_TOP);
        g.fillPolygon(tLeftTopX, tLeftTopY, 4);

        // --- YÊN ĐÁ KẾT NỐI Ở GIỮA ---
        int[] saddleX = { x - 14, x + 8, x + 4, x - 10 };
        int[] saddleY = { y + 6, y + 8, y - 4, y - 6 };
        g.setColor(REEF_COLOR_DARK);
        g.fillPolygon(saddleX, saddleY, 4);

        // --- KHỐI ĐÁ PHẢI (HIGH PEAK - ĐỈNH CAO VÚT) ---
        // 1. Chân khối đá phải
        int[] bRightX = { x - 8, x + 50, x + 44, x - 4 };
        int[] bRightY = { y + 18, y + 16, y + 6, y + 6 };
        g.setColor(REEF_COLOR_DEEP);
        g.fillPolygon(bRightX, bRightY, 4);

        // 2. Tầng bậc thấp sườn phải
        int[] fRightLowX = { x - 4, x + 44, x + 38, x + 2 };
        int[] fRightLowY = { y + 6, y + 6, y - 10, y - 8 };
        g.setColor(REEF_COLOR_MID);
        g.fillPolygon(fRightLowX, fRightLowY, 4);

        // 3. Vách đứng sườn phải (Khuất bóng)
        int[] fRightDarkX = { x + 16, x + 42, x + 34, x + 12 };
        int[] fRightDarkY = { y - 8, y - 10, y - 30, y - 26 };
        g.setColor(REEF_COLOR_DARK);
        g.fillPolygon(fRightDarkX, fRightDarkY, 4);

        // 4. Mặt trước vách chóp chính
        int[] fRightMainX = { x + 2, x + 24, x + 18, x - 2 };
        int[] fRightMainY = { y - 8, y - 14, y - 34, y - 28 };
        g.setColor(REEF_COLOR_MID);
        g.fillPolygon(fRightMainX, fRightMainY, 4);

        // 5. Mặt đỉnh chóp cao nhất (Crest Peak)
        int[] tRightTopX = { x - 2, x + 22, x + 16, x + 4 };
        int[] tRightTopY = { y - 28, y - 34, y - 44, y - 40 };
        g.setColor(REEF_COLOR_TOP);
        g.fillPolygon(tRightTopX, tRightTopY, 4);

        // --- VIỀN MẢNH CÙNG GAM (ALPHA ~50%, KHÔNG DÙNG ĐEN) ---
        g.setColor(REEF_OUTLINE_COL);
        g.setStroke(STROKE_CAP_ROUND_1_2);
        g.drawPolygon(bLeftX, bLeftY, 4);
        g.drawPolygon(fLeftDarkX, fLeftDarkY, 4);
        g.drawPolygon(fLeftMidX, fLeftMidY, 4);
        g.drawPolygon(tLeftTopX, tLeftTopY, 4);
        g.drawPolygon(bRightX, bRightY, 4);
        g.drawPolygon(fRightDarkX, fRightDarkY, 4);
        g.drawPolygon(fRightMainX, fRightMainY, 4);
        g.drawPolygon(tRightTopX, tRightTopY, 4);

        // Khe nứt tự nhiên
        g.drawLine(x - 24, y - 16, x - 20, y + 2);
        g.drawLine(x + 12, y - 26, x + 16, y - 6);

        // --- SINH THÁI BIỂN BÁM TRÊN ĐÁ ---
        drawReefEcosystemDetails(g, x, y);
    }

    // =========================================================================
    // BIẾN THỂ 1: RẠN ĐÁ BẬC THANG TRẢI DÀI (STEPPED SHELF REEF)
    // 3 Tầng bậc trải rộng 130px với các gờ đá ngang và thềm san hô bám
    // =========================================================================
    private void drawSteppedShelfReef(Graphics2D g, int x, int y, int w, int h) {
        // 1. Chân đế đáy trải rộng
        int[] bBaseX = { x - 62, x + 60, x + 52, x - 54 };
        int[] bBaseY = { y + 17, y + 16, y + 7, y + 6 };
        g.setColor(REEF_COLOR_DEEP);
        g.fillPolygon(bBaseX, bBaseY, 4);

        // 2. Thềm bậc thấp bên phải
        int[] rShelfX = { x + 12, x + 52, x + 44, x + 18 };
        int[] rShelfY = { y + 6, y + 7, y - 10, y - 12 };
        g.setColor(REEF_COLOR_MID);
        g.fillPolygon(rShelfX, rShelfY, 4);

        int[] rShelfTopX = { x + 18, x + 44, x + 38, x + 22 };
        int[] rShelfTopY = { y - 12, y - 10, y - 18, y - 18 };
        g.setColor(REEF_COLOR_TOP);
        g.fillPolygon(rShelfTopX, rShelfTopY, 4);

        // 3. Thềm bậc thấp bên trái
        int[] lShelfX = { x - 54, x - 16, x - 22, x - 50 };
        int[] lShelfY = { y + 6, y + 6, y - 12, y - 10 };
        g.setColor(REEF_COLOR_DARK);
        g.fillPolygon(lShelfX, lShelfY, 4);

        int[] lShelfTopX = { x - 50, x - 22, x - 28, x - 44 };
        int[] lShelfTopY = { y - 10, y - 12, y - 20, y - 18 };
        g.setColor(REEF_COLOR_TOP);
        g.fillPolygon(lShelfTopX, lShelfTopY, 4);

        // 4. Khối gờ cao ở trung tâm
        int[] mFrontX = { x - 22, x + 18, x + 12, x - 18 };
        int[] mFrontY = { y + 6, y + 6, y - 22, y - 20 };
        g.setColor(REEF_COLOR_MID);
        g.fillPolygon(mFrontX, mFrontY, 4);

        int[] mSideX = { x + 12, x + 28, x + 22, x + 12 };
        int[] mSideY = { y - 2, y - 6, y - 24, y - 22 };
        g.setColor(REEF_COLOR_BROWN);
        g.fillPolygon(mSideX, mSideY, 4);

        int[] mTopX = { x - 18, x + 12, x + 6, x - 12 };
        int[] mTopY = { y - 20, y - 22, y - 36, y - 34 };
        g.setColor(REEF_COLOR_TOP);
        g.fillPolygon(mTopX, mTopY, 4);

        // Viền mảnh cùng gam
        g.setColor(REEF_OUTLINE_COL);
        g.setStroke(STROKE_CAP_ROUND_1_2);
        g.drawPolygon(bBaseX, bBaseY, 4);
        g.drawPolygon(rShelfX, rShelfY, 4);
        g.drawPolygon(rShelfTopX, rShelfTopY, 4);
        g.drawPolygon(lShelfX, lShelfY, 4);
        g.drawPolygon(lShelfTopX, lShelfTopY, 4);
        g.drawPolygon(mFrontX, mFrontY, 4);
        g.drawPolygon(mTopX, mTopY, 4);

        // Khe nứt ngang gờ đá
        g.drawLine(x - 14, y - 10, x + 8, y - 12);
        g.drawLine(x + 24, y - 4, x + 38, y - 6);

        drawReefEcosystemDetails(g, x, y);
    }

    // =========================================================================
    // BIẾN THỂ 2: MỎM ĐÁ CHÓP ĐƠN KHỐI LỚN (MONOLITHIC SEA MOUNT)
    // Khối chóp trung tâm nhô cao vút với 2 bờ sườn thoải và khe nứt đứng
    // =========================================================================
    private void drawMonolithReef(Graphics2D g, int x, int y, int w, int h) {
        // 1. Chân đế đáy
        int[] bBaseX = { x - 48, x + 46, x + 40, x - 42 };
        int[] bBaseY = { y + 17, y + 16, y + 7, y + 7 };
        g.setColor(REEF_COLOR_DEEP);
        g.fillPolygon(bBaseX, bBaseY, 4);

        // 2. Sườn thoải bên trái
        int[] lSlopeX = { x - 42, x - 12, x - 16, x - 36 };
        int[] lSlopeY = { y + 7, y + 5, y - 16, y - 12 };
        g.setColor(REEF_COLOR_DARK);
        g.fillPolygon(lSlopeX, lSlopeY, 4);

        int[] lTopX = { x - 36, x - 16, x - 22, x - 32 };
        int[] lTopY = { y - 12, y - 16, y - 24, y - 20 };
        g.setColor(REEF_COLOR_BROWN);
        g.fillPolygon(lTopX, lTopY, 4);

        // 3. Sườn thoải bên phải
        int[] rSlopeX = { x + 8, x + 40, x + 34, x + 14 };
        int[] rSlopeY = { y + 6, y + 6, y - 18, y - 16 };
        g.setColor(REEF_COLOR_MID);
        g.fillPolygon(rSlopeX, rSlopeY, 4);

        int[] rTopX = { x + 14, x + 34, x + 28, x + 18 };
        int[] rTopY = { y - 16, y - 18, y - 26, y - 24 };
        g.setColor(REEF_COLOR_TOP);
        g.fillPolygon(rTopX, rTopY, 4);

        // 4. Khối tháp trung tâm vươn cao nhất
        int[] mBaseX = { x - 16, x + 14, x + 8, x - 10 };
        int[] mBaseY = { y + 5, y + 6, y - 24, y - 22 };
        g.setColor(REEF_COLOR_MID);
        g.fillPolygon(mBaseX, mBaseY, 4);

        int[] mPeakX = { x - 10, x + 8, x + 4, x - 6 };
        int[] mPeakY = { y - 22, y - 24, y - 44, y - 42 };
        g.setColor(REEF_COLOR_TOP);
        g.fillPolygon(mPeakX, mPeakY, 4);

        // Viền mảnh cùng gam
        g.setColor(REEF_OUTLINE_COL);
        g.setStroke(STROKE_CAP_ROUND_1_2);
        g.drawPolygon(bBaseX, bBaseY, 4);
        g.drawPolygon(lSlopeX, lSlopeY, 4);
        g.drawPolygon(rSlopeX, rSlopeY, 4);
        g.drawPolygon(mBaseX, mBaseY, 4);
        g.drawPolygon(mPeakX, mPeakY, 4);

        // Khe nứt đứng chia đôi mỏm đá
        g.setColor(REEF_COLOR_DARK);
        g.drawLine(x - 2, y - 36, x - 4, y - 12);
        g.drawLine(x - 4, y - 12, x, y + 2);

        drawReefEcosystemDetails(g, x, y);
    }

    // =========================================================================
    // CHI TIẾT SINH THÁI BIỂN BÁM TRÊN ĐÁ (RONG RÊU, HÀ BÁM, CHẤM SAN HÔ)
    // =========================================================================
    private void drawReefEcosystemDetails(Graphics2D g, int x, int y) {
        // 1. Rong rêu xanh bám dọc khe đá và chân gờ
        g.setColor(new Color(32, 92, 68, 220));
        g.fillRect(x - 26, y - 4, 8, 3);
        g.fillRect(x + 12, y + 2, 10, 3);
        g.fillRect(x - 14, y + 8, 7, 3);
        g.setColor(new Color(52, 138, 104, 230));
        g.fillRect(x - 25, y - 4, 5, 2);
        g.fillRect(x + 14, y + 2, 6, 2);

        // 2. Cụm hà bám (Barnacles - chấm xám xanh có nhân lõm)
        drawBarnacleCluster(g, x - 34, y + 2);
        drawBarnacleCluster(g, x + 24, y + 4);

        // 3. Chấm san hô nhỏ đa sắc bám trên vách đá (theo media_1793285994903.png)
        // Đốm cam hải quỳ
        g.setColor(new Color(234, 88, 12));
        g.fillOval(x - 8, y - 2, 4, 3);
        g.fillOval(x + 18, y - 14, 3, 3);
        // Đốm hồng san hô
        g.setColor(new Color(244, 114, 182));
        g.fillOval(x - 28, y - 14, 4, 4);
        g.fillOval(x + 32, y - 4, 3, 3);
        // Đốm tím phong lan
        g.setColor(new Color(168, 85, 247));
        g.fillOval(x + 4, y - 18, 3, 3);

        // 4. Nhánh tảo nhỏ mọc từ kẽ đá đung đưa nhẹ
        double sway = Math.sin(ambientTimer * 1.8) * 3.0;
        g.setColor(new Color(42, 112, 84));
        g.setStroke(STROKE_CAP_ROUND_2_0);
        g.drawLine(x - 16, y - 10, (int) (x - 22 + sway), y - 24);
        g.setColor(new Color(74, 172, 130));
        g.drawLine((int) (x - 22 + sway), y - 24, (int) (x - 26 + sway * 1.4), y - 34);
    }

    private void drawBarnacleCluster(Graphics2D g, int bx, int by) {
        g.setColor(new Color(130, 162, 170));
        g.fillOval(bx, by, 5, 4);
        g.fillOval(bx + 4, by + 1, 4, 4);
        g.fillOval(bx + 2, by - 3, 4, 4);

        // Nhân lỗ hà
        g.setColor(new Color(20, 44, 52));
        g.fillRect(bx + 2, by + 1, 1, 2);
        g.fillRect(bx + 5, by + 2, 1, 2);
        g.fillRect(bx + 3, by - 2, 1, 2);
    }

    // =========================================================================
    // ÁNH SÁNG DƯỚI NƯỚC: VÂN SÁNG GỢN SÓNG (CAUSTICS) PHỦ LÊN ĐÁ
    // =========================================================================
    private void drawReefCaustics(Graphics2D g, int x, int y, int w, int h, int variant) {
        Composite oldComp = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, REEF_CAUSTICS_INTENSITY));

        double time = ambientTimer * REEF_CAUSTICS_SPEED + variant * 1.5;

        int left = x - w / 2 + 8;
        int right = x + w / 2 - 8;
        int top = y - h / 2 + 4;

        // Đường vân caustics 1 (vắt ngang tầng trên)
        double c1_x1 = left + Math.sin(time + 0.3) * 8;
        double c1_y1 = top + h * 0.28 + Math.cos(time * 0.9) * 5;
        double c1_cx = x + Math.cos(time * 1.1) * 10;
        double c1_cy = top + h * 0.14 + Math.sin(time * 0.8) * 6;
        double c1_x2 = right - 10 + Math.sin(time * 1.2) * 8;
        double c1_y2 = top + h * 0.32 + Math.cos(time * 1.0) * 5;

        // Đường vân caustics 2 (vắt chéo tầng giữa)
        double c2_x1 = left + 14 + Math.cos(time * 0.8) * 7;
        double c2_y1 = top + h * 0.58 + Math.sin(time * 1.2) * 5;
        double c2_cx = x - 6 + Math.sin(time * 1.3) * 9;
        double c2_cy = top + h * 0.48 + Math.cos(time * 0.7) * 6;
        double c2_x2 = right - 4 + Math.cos(time * 1.1) * 7;
        double c2_y2 = top + h * 0.68 + Math.sin(time * 0.9) * 5;

        // Đường vân caustics 3 (nối vòm cellular)
        double c3_x1 = c1_cx + 8;
        double c3_y1 = c1_cy + 4;
        double c3_cx = x + 16 + Math.sin(time * 0.9) * 8;
        double c3_cy = top + h * 0.38 + Math.cos(time * 1.2) * 5;
        double c3_x2 = c2_cx + 6;
        double c3_y2 = c2_cy - 4;

        g.setColor(COLOR_REEF_CAUSTICS);
        g.setStroke(STROKE_CAP_ROUND_1_4);
        reefCausticCurve1.setCurve(c1_x1, c1_y1, c1_cx, c1_cy, c1_x2, c1_y2);
        reefCausticCurve2.setCurve(c2_x1, c2_y1, c2_cx, c2_cy, c2_x2, c2_y2);
        reefCausticCurve3.setCurve(c3_x1, c3_y1, c3_cx, c3_cy, c3_x2, c3_y2);
        g.draw(reefCausticCurve1);
        g.draw(reefCausticCurve2);
        g.draw(reefCausticCurve3);

        // Điểm tụ quang caustics
        g.setColor(COLOR_REEF_CAUSTICS_GLINT);
        g.fillRect((int) c1_cx - 1, (int) c1_cy - 1, 3, 3);
        g.fillRect((int) c2_cx - 1, (int) c2_cy - 1, 3, 3);

        g.setComposite(oldComp);
    }

    private void drawReefSparseBubbles(Graphics2D g, int x, int y, int count) {
        for (int i = 0; i < count; i++) {
            double bubX = x + Math.sin(ambientTimer * 1.4 + i * 2.2) * 18 + (i - 1) * 16;
            double bubY = y - 8 - ((ambientTimer * 20.0 + i * 22) % 65);
            g.setColor(COLOR_REEF_BUBBLE_1);
            g.drawOval((int) bubX, (int) bubY, 3, 3);
            g.setColor(COLOR_REEF_BUBBLE_2);
            g.fillRect((int) bubX + 1, (int) bubY + 1, 1, 1);
        }
    }

    // =========================================================================
    // KHỞI TẠO BỘ ĐỆM ĐỒ HỌA MẶT NƯỚC (DUAL CAUSTICS TEXTURE) & TIA SÁNG (SUNBEAM)
    // =========================================================================
    private void initCausticsTexture() {
        if (causticsTexture1 != null) return;
        double twoPi = Math.PI * 2.0;

        // 1. Texture Caustics 1: Kích thước 160x160 (Tỉ lệ 1.0x, tần số cơ bản)
        int size1 = WATER_CAUSTICS_TEX1_SIZE;
        causticsTexture1 = new BufferedImage(size1, size1, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size1; y++) {
            double v = (y * twoPi) / size1;
            for (int x = 0; x < size1; x++) {
                double u = (x * twoPi) / size1;
                double s1 = Math.sin(u + Math.cos(v));
                double s2 = Math.sin(2.0 * u + Math.cos(2.0 * v + 0.8));
                double s3 = Math.cos(u - 2.0 * v + Math.sin(u));
                double s4 = Math.sin(2.0 * u + 2.0 * v);
                double raw = (s1 + s2 + s3 + s4 + 4.0) / 8.0;
                double ridge = Math.abs(raw - 0.5) * 2.0;
                double caustic = Math.pow(ridge, 2.5);
                int alpha = (int) Math.round(caustic * 255.0);
                alpha = Math.max(0, Math.min(255, alpha));
                int rgb = (alpha << 24) | (225 << 16) | (250 << 8) | 255;
                causticsTexture1.setRGB(x, y, rgb);
            }
        }
        causticsTexture = causticsTexture1; // Tương thích ngược

        // 2. Texture Caustics 2: Kích thước 260x260 (Tỉ lệ 1.625x phi chu kỳ, tần số điều hòa bậc 2)
        int size2 = WATER_CAUSTICS_TEX2_SIZE;
        causticsTexture2 = new BufferedImage(size2, size2, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size2; y++) {
            double v = (y * twoPi) / size2;
            for (int x = 0; x < size2; x++) {
                double u = (x * twoPi) / size2;
                double s1 = Math.sin(1.5 * u + Math.cos(1.2 * v + 0.5));
                double s2 = Math.sin(2.5 * u + Math.cos(1.8 * v + 1.4));
                double s3 = Math.cos(1.2 * u - 2.2 * v + Math.sin(u + 0.7));
                double s4 = Math.sin(1.8 * u + 2.4 * v);
                double raw = (s1 + s2 + s3 + s4 + 4.0) / 8.0;
                double ridge = Math.abs(raw - 0.5) * 2.0;
                double caustic = Math.pow(ridge, 2.3);
                int alpha = (int) Math.round(caustic * 255.0);
                alpha = Math.max(0, Math.min(255, alpha));
                int rgb = (alpha << 24) | (210 << 16) | (245 << 8) | 255;
                causticsTexture2.setRGB(x, y, rgb);
            }
        }
    }

    public static synchronized void initSunbeamTexture() {
        if (cachedSunbeamTexture != null) return;
        int bw = 128;
        int bh = 512;
        BufferedImage raw = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < bh; y++) {
            double v = (double) y / bh;
            // Độ mờ giảm dần theo độ sâu (v=0 là mặt biển sáng nhất, v=1 đáy biển mờ hoàn toàn)
            double vFade = Math.pow(Math.max(0.0, 1.0 - v), 1.35);

            for (int x = 0; x < bw; x++) {
                double u = (double) x / bw;
                // Mép vệt mờ dần hình chuông (Cosine/Sine squared), không có cạnh sắc
                double hProfile = Math.pow(Math.sin(u * Math.PI), 2.2);

                int alpha = (int) Math.round(hProfile * vFade * 255.0);
                alpha = Math.max(0, Math.min(255, alpha));
                int rgb = (alpha << 24) | (186 << 16) | (230 << 8) | 253;
                raw.setRGB(x, y, rgb);
            }
        }

        // Chuyển sang ảnh tương thích bộ nhớ GPU để render cực nhanh với AffineTransform
        cachedSunbeamTexture = createCompatible(bw, bh, Transparency.TRANSLUCENT);
        Graphics2D cg = cachedSunbeamTexture.createGraphics();
        cg.setComposite(AlphaComposite.Src);
        cg.drawImage(raw, 0, 0, null);
        cg.dispose();
    }

    private void initPropSprites() {
        if (cachedBushVariants[0] != null) return;
        int bw = 96;
        int bh = 72;
        for (int v = 0; v < 4; v++) {
            BufferedImage raw = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_ARGB);
            Graphics2D gb = raw.createGraphics();
            gb.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            drawProceduralBushVariant(gb, bw / 2, bh / 2 + 8, v);
            gb.dispose();
            cachedBushVariants[v] = AssetManager.featherEdges(raw, 3);
        }
    }

    public static synchronized void initLightMask() {
        if (cachedLightMask != null) return;
        int r = (int) LIGHT_RADIUS; // 180
        int size = r * 2; // 360
        BufferedImage raw = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D mg = raw.createGraphics();
        mg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        mg.setColor(COLOR_AMBIENT_DARKNESS);
        mg.fillRect(0, 0, size, size);

        float[] dist = {0.0f, 0.40f, 1.0f};
        Color[] maskColors = {
            new Color(0, 0, 0, 255),
            new Color(0, 0, 0, 190),
            new Color(0, 0, 0, 0)
        };
        RadialGradientPaint rgp = new RadialGradientPaint(new Point2D.Float(r, r), r, dist, maskColors);
        mg.setComposite(AlphaComposite.DstOut);
        mg.setPaint(rgp);
        mg.fillOval(0, 0, size, size);
        mg.dispose();

        // Chuyển sang ảnh tương thích bộ nhớ đồ họa để blit cực nhanh
        cachedLightMask = createCompatible(size, size, Transparency.TRANSLUCENT);
        Graphics2D cg = cachedLightMask.createGraphics();
        cg.setComposite(AlphaComposite.Src);
        cg.drawImage(raw, 0, 0, null);
        cg.dispose();
    }

    public static synchronized void initOceanBubbles() {
        if (cachedOceanBubbles[0] != null) return;
        for (int k = 0; k < 5; k++) {
            int bSize = 4 + k * 2;
            int imgSize = bSize + 4;
            BufferedImage raw = new BufferedImage(imgSize, imgSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D bg = raw.createGraphics();
            bg.setColor(COLOR_BUBBLE_OUTLINE);
            bg.drawOval(1, 1, bSize, bSize);
            bg.setColor(COLOR_BUBBLE_FILL);
            bg.fillOval(1, 1, bSize, bSize);
            bg.setColor(Color.WHITE);
            bg.fillRect(2, 2, Math.max(1, bSize / 3), Math.max(1, bSize / 3));
            bg.dispose();

            BufferedImage comp = createCompatible(imgSize, imgSize, Transparency.TRANSLUCENT);
            Graphics2D cg = comp.createGraphics();
            cg.setComposite(AlphaComposite.Src);
            cg.drawImage(raw, 0, 0, null);
            cg.dispose();
            cachedOceanBubbles[k] = comp;
        }
    }

    // =========================================================================
    // 1. MẶT NƯỚC CHUYỂN ĐỘNG & BỌT BỜ BIỂN (CAUSTICS & SHORELINE FOAM)
    // =========================================================================
    private void renderWaterSurfaceAndFoam(Graphics2D g, int viewW, int viewH, Camera camera,
                                           List<Chunk> activeChunks, double time, int stageId) {
        // 1. Vân sáng gợn sóng caustics cuộn 2 lớp ngược chiều phủ lên các ô nước
        renderWaterCaustics(g, viewW, viewH, camera, activeChunks, time, stageId);

        // 2. Bờ nước có bọt trắng mảnh 1px nhấp nhô sóng sin
        renderShorelineFoam(g, camera, activeChunks, time, stageId);
    }

    private void renderWaterCaustics(Graphics2D g, int viewW, int viewH, Camera camera,
                                     List<Chunk> activeChunks, double time, int stageId) {
        initCausticsTexture();
        if (causticsTexture1 == null || causticsTexture2 == null) return;

        Composite oldComp = g.getComposite();

        double camX = camera.getX();
        double camY = camera.getY();
        double halfW = viewW * 0.5;
        double halfH = viewH * 0.5;

        // Lớp 1: Cuộn theo chiều thuận với causticsTexture1 (160x160, tỉ lệ 1.0x)
        int texSize1 = WATER_CAUSTICS_TEX1_SIZE;
        int anchor1X = (int) Math.round((-camX + halfW + time * WATER_SCROLL_SPEED_1_X) % texSize1);
        int anchor1Y = (int) Math.round((-camY + halfH + time * WATER_SCROLL_SPEED_1_Y) % texSize1);
        if (anchor1X > 0) anchor1X -= texSize1;
        if (anchor1Y > 0) anchor1Y -= texSize1;

        TexturePaint tp1 = new TexturePaint(causticsTexture1, new java.awt.geom.Rectangle2D.Double(anchor1X, anchor1Y, texSize1, texSize1));
        g.setPaint(tp1);
        g.setComposite(COMPOSITE_CAUSTICS_1);

        double camLeft = camera.getX() - halfW;
        double camTop  = camera.getY() - halfH;

        for (int c = 0; c < activeChunks.size(); c++) {
            Chunk chunk = activeChunks.get(c);
            if (!chunk.hasWater()) continue;
            double cwx = chunk.getWorldX();
            double cwy = chunk.getWorldY();
            List<Chunk.WaterTileRun> runs = chunk.getWaterRuns();
            for (int r = 0; r < runs.size(); r++) {
                Chunk.WaterTileRun run = runs.get(r);
                double wx = cwx + run.rx;
                double wy = cwy + run.ry;
                int sx = (int) Math.round(wx - camLeft);
                int sy = (int) Math.round(wy - camTop);
                if (sx + run.rw < 0 || sx > viewW || sy + run.rh < 0 || sy > viewH) continue;
                g.fillRect(sx, sy, run.rw, run.rh);
            }
        }

        // Lớp 2: Cuộn ngược chiều với causticsTexture2 (260x260, tỉ lệ 1.625x phi chu kỳ, triệt tiêu vân lặp)
        int texSize2 = WATER_CAUSTICS_TEX2_SIZE;
        int anchor2X = (int) Math.round((-camX + halfW + time * WATER_SCROLL_SPEED_2_X) % texSize2);
        int anchor2Y = (int) Math.round((-camY + halfH + time * WATER_SCROLL_SPEED_2_Y) % texSize2);
        if (anchor2X > 0) anchor2X -= texSize2;
        if (anchor2Y > 0) anchor2Y -= texSize2;

        TexturePaint tp2 = new TexturePaint(causticsTexture2, new java.awt.geom.Rectangle2D.Double(anchor2X, anchor2Y, texSize2, texSize2));
        g.setPaint(tp2);
        g.setComposite(COMPOSITE_CAUSTICS_2);

        for (int c = 0; c < activeChunks.size(); c++) {
            Chunk chunk = activeChunks.get(c);
            if (!chunk.hasWater()) continue;
            double cwx = chunk.getWorldX();
            double cwy = chunk.getWorldY();
            List<Chunk.WaterTileRun> runs = chunk.getWaterRuns();
            for (int r = 0; r < runs.size(); r++) {
                Chunk.WaterTileRun run = runs.get(r);
                double wx = cwx + run.rx;
                double wy = cwy + run.ry;
                int sx = (int) Math.round(wx - camLeft);
                int sy = (int) Math.round(wy - camTop);
                if (sx + run.rw < 0 || sx > viewW || sy + run.rh < 0 || sy > viewH) continue;
                g.fillRect(sx, sy, run.rw, run.rh);
            }
        }

        g.setComposite(oldComp);
    }

    private void renderShorelineFoam(Graphics2D g, Camera camera, List<Chunk> activeChunks, double time, int stageId) {
        Stroke oldStroke = g.getStroke();
        g.setStroke(STROKE_FOAM);
        g.setColor(stageId == 2 ? COLOR_OCEAN_FOAM : COLOR_WATER_FOAM);

        for (int c = 0; c < activeChunks.size(); c++) {
            Chunk chunk = activeChunks.get(c);
            if (!chunk.hasWater()) continue;
            List<Chunk.ShoreSegment> segments = chunk.getShoreSegments();
            if (segments == null || segments.isEmpty()) continue;

            double cwx = chunk.getWorldX();
            double cwy = chunk.getWorldY();

            for (int i = 0; i < segments.size(); i++) {
                Chunk.ShoreSegment seg = segments.get(i);
                double wx1 = cwx + seg.x1;
                double wy1 = cwy + seg.y1;
                double wx2 = cwx + seg.x2;
                double wy2 = cwy + seg.y2;

                double midX = (wx1 + wx2) * 0.5;
                double midY = (wy1 + wy2) * 0.5;
                if (!camera.isVisible(midX, midY, 32)) continue;

                Point2D.Double s1 = camera.worldToScreen(wx1, wy1);
                Point2D.Double s2 = camera.worldToScreen(wx2, wy2);

                if (seg.normY != 0) {
                    // Đoạn bờ ngang: dao động dọc trục Y hướng về phía nước
                    int startX = (int) Math.round(Math.min(s1.x, s2.x));
                    int endX = (int) Math.round(Math.max(s1.x, s2.x));
                    int baseY = (int) Math.round(s1.y);

                    int prevX = startX;
                    double wave0 = Math.sin(time * FOAM_FREQUENCY + seg.phase) * FOAM_AMPLITUDE;
                    int prevY = baseY + (int) Math.round(seg.normY * (1.2 + wave0));

                    for (int x = startX + 3; x <= endX; x += 3) {
                        double wave = Math.sin(time * FOAM_FREQUENCY + seg.phase + (x - startX) * 0.35) * FOAM_AMPLITUDE;
                        int curY = baseY + (int) Math.round(seg.normY * (1.2 + wave));
                        g.drawLine(prevX, prevY, x, curY);
                        prevX = x;
                        prevY = curY;
                    }
                    if (prevX < endX) {
                        double wave = Math.sin(time * FOAM_FREQUENCY + seg.phase + (endX - startX) * 0.35) * FOAM_AMPLITUDE;
                        int curY = baseY + (int) Math.round(seg.normY * (1.2 + wave));
                        g.drawLine(prevX, prevY, endX, curY);
                    }
                } else {
                    // Đoạn bờ dọc: dao động dọc trục X hướng về phía nước
                    int startY = (int) Math.round(Math.min(s1.y, s2.y));
                    int endY = (int) Math.round(Math.max(s1.y, s2.y));
                    int baseX = (int) Math.round(s1.x);

                    int prevY = startY;
                    double wave0 = Math.sin(time * FOAM_FREQUENCY + seg.phase) * FOAM_AMPLITUDE;
                    int prevX = baseX + (int) Math.round(seg.normX * (1.2 + wave0));

                    for (int y = startY + 3; y <= endY; y += 3) {
                        double wave = Math.sin(time * FOAM_FREQUENCY + seg.phase + (y - startY) * 0.35) * FOAM_AMPLITUDE;
                        int curX = baseX + (int) Math.round(seg.normX * (1.2 + wave));
                        g.drawLine(prevX, prevY, curX, y);
                        prevX = curX;
                        prevY = y;
                    }
                    if (prevY < endY) {
                        double wave = Math.sin(time * FOAM_FREQUENCY + seg.phase + (endY - startY) * 0.35) * FOAM_AMPLITUDE;
                        int curX = baseX + (int) Math.round(seg.normX * (1.2 + wave));
                        g.drawLine(prevX, prevY, curX, endY);
                    }
                }
            }
        }

        g.setStroke(oldStroke);
    }

    // =========================================================================
    // 2. GỢN NƯỚC QUANH RẮN KHI BƠI (SWIMMING WATER RIPPLES)
    // =========================================================================
    private void updateWaterRipples(PlayerSnake player, double dt) {
        if (player != null && player.isAlive() && player.isInWater()) {
            rippleSpawnTimer += dt;
            if (rippleSpawnTimer >= RIPPLE_SPAWN_INTERVAL) {
                rippleSpawnTimer = 0.0;
                spawnWaterRipple(player.getX(), player.getY());
            }
        } else {
            rippleSpawnTimer = 0.0;
        }

        for (int i = 0; i < RIPPLE_POOL_SIZE; i++) {
            WaterRipple r = ripplePool[i];
            if (r.active) {
                r.age += dt;
                if (r.age >= RIPPLE_LIFETIME) {
                    r.active = false;
                }
            }
        }
    }

    private void spawnWaterRipple(double x, double y) {
        for (int i = 0; i < RIPPLE_POOL_SIZE; i++) {
            WaterRipple r = ripplePool[i];
            if (!r.active) {
                r.active = true;
                r.x = x;
                r.y = y;
                r.age = 0.0;
                return;
            }
        }
    }

    private void renderWaterRipples(Graphics2D g, Camera camera, int stageId) {
        Composite oldComp = g.getComposite();
        Stroke oldStroke = g.getStroke();
        g.setStroke(STROKE_RIPPLE);

        for (int i = 0; i < RIPPLE_POOL_SIZE; i++) {
            WaterRipple r = ripplePool[i];
            if (!r.active) continue;
            if (!camera.isVisible(r.x, r.y, 40)) continue;

            double progress = r.age / RIPPLE_LIFETIME; // 0.0 -> 1.0
            float alpha = (float) ((1.0 - progress) * RIPPLE_MAX_ALPHA);
            if (alpha <= 0.01f) continue;

            double radius = RIPPLE_START_RADIUS + progress * (RIPPLE_MAX_RADIUS - RIPPLE_START_RADIUS);
            int rw = (int) Math.round(radius * 2.0);
            int rh = (int) Math.round(radius * 1.3);

            Point2D.Double screen = camera.worldToScreen(r.x, r.y);
            int sx = (int) Math.round(screen.x);
            int sy = (int) Math.round(screen.y);

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g.setColor(stageId == 2 ? COLOR_OCEAN_RIPPLE : COLOR_WATER_RIPPLE);
            g.drawOval(sx - rw / 2, sy - rh / 2, rw, rh);

            // Vòng sóng phụ đồng tâm (concentric secondary ripple)
            if (progress > 0.15) {
                double innerProg = (progress - 0.15) / 0.85;
                double inRadius = RIPPLE_START_RADIUS + innerProg * (RIPPLE_MAX_RADIUS * 0.7 - RIPPLE_START_RADIUS);
                int inW = (int) Math.round(inRadius * 2.0);
                int inH = (int) Math.round(inRadius * 1.3);
                float inAlpha = (float) ((1.0 - innerProg) * (RIPPLE_MAX_ALPHA * 0.55f));
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, inAlpha));
                g.drawOval(sx - inW / 2, sy - inH / 2, inW, inH);
            }
        }

        g.setStroke(oldStroke);
        g.setComposite(oldComp);
    }

    // =========================================================================
    // 3. CỎ, BỤI VÀ CÂY ĐUNG ĐƯA (SWAYING VEGETATION - CẮT 5 DẢI NGANG)
    // =========================================================================
    private void drawSwayingSprite(Graphics2D g, BufferedImage img, double cx, double cy, int w, int h, double phase, double time) {
        if (img == null) return;

        int imgW = img.getWidth();
        int imgH = img.getHeight();

        int x0 = (int) Math.round(cx - w / 2.0);
        int y0 = (int) Math.round(cy - h / 2.0);

        int strips = SWAY_STRIP_COUNT;
        double stripSrcH = (double) imgH / strips;
        double stripDestH = (double) h / strips;

        for (int i = 0; i < strips; i++) {
            int sy1 = (int) Math.round(i * stripSrcH);
            int sy2 = (int) Math.round((i + 1) * stripSrcH);
            if (i == strips - 1) sy2 = imgH;

            int dy1 = y0 + (int) Math.round(i * stripDestH);
            int dy2 = y0 + (int) Math.round((i + 1) * stripDestH);
            if (i == strips - 1) dy2 = y0 + h;

            // Strip i=0 là ngọn (sway max), Strip i=strips-1 là gốc (đứng yên, sway=0)
            double ratio = (double) (strips - 1 - i) / (strips - 1.0);
            double offset = Math.sin(time * SWAY_FREQUENCY + phase) * SWAY_MAX_AMPLITUDE * ratio;
            int dx = (int) Math.round(offset);

            g.drawImage(img,
                x0 + dx, dy1, x0 + w + dx, dy2,
                0, sy1, imgW, sy2,
                null);
        }
    }

    private void drawSwayingBush(Graphics2D g, WorldProp prop, double cx, double cy, int w, int h,
                                 PlayerSnake player, double phase, double time) {
        initPropSprites();
        int variant = Math.abs(prop.getVariant()) % 4;
        BufferedImage img = (AssetManager.getImage("bush") != null) ? AssetManager.getImage("bush") : cachedBushVariants[variant];
        if (img == null) img = cachedBushVariants[0];
        if (img == null) return;

        Composite oldComp = g.getComposite();

        double distToPlayer = Math.hypot(player.getX() - prop.getX(), player.getY() - prop.getY());
        float bushAlpha = (distToPlayer < prop.getRadius()) ? 0.65f : 0.78f;
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, bushAlpha));

        drawSwayingSprite(g, img, cx, cy, w, h, phase, time);

        // 3. Mép sprite: điểm thêm vài bụi cỏ nhỏ và sỏi đè lên chân sprite để hòa vào nền đất tự nhiên
        int baseY = (int) Math.round(cy + h * 0.32);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.90f));

        // Nhánh cỏ nhỏ đè lên chân sprite
        g.setColor(new Color(22, 101, 52));
        drawGrassSpike(g, (int) cx - 14, baseY, -0.22, 11, 4);
        drawGrassSpike(g, (int) cx + 12, baseY, 0.20, 12, 4);
        g.setColor(new Color(34, 197, 94));
        drawGrassSpike(g, (int) cx - 2, baseY + 1, 0.05, 9, 3);

        // Hạt sỏi / vệt đất nhỏ tiếp đất
        g.setColor(new Color(78, 52, 38));
        g.fillOval((int) cx - 18, baseY - 1, 6, 3);
        g.fillOval((int) cx + 8, baseY, 5, 3);
        g.setColor(new Color(112, 78, 58));
        g.fillRect((int) cx - 17, baseY, 3, 1);

        g.setComposite(oldComp);
    }

    // =========================================================================
    // VẼ CÂY CỎ TRONG MẢNG CỎ RỘNG (WIDE GRASS PATCH PLANT RENDERING - ZERO-GC)
    // =========================================================================
    public static synchronized void initPlantSprites() {
        if (cachedPlantFrames[0][0] != null) return;
        int imgW = 56;
        int imgH = 68;
        int cx = 28;
        int baseY = 60;

        for (int v = 0; v < 4; v++) {
            for (int k = 0; k < PLANT_FRAME_COUNT; k++) {
                double swayFactor = Math.sin((k * 2.0 * Math.PI) / PLANT_FRAME_COUNT);
                double maxSway = swayFactor * PLANT_SWAY_MAX_AMPLITUDE;

                BufferedImage raw = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = raw.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                drawProceduralPlantFrame(g, cx, baseY, v, maxSway);
                g.dispose();

                cachedPlantFrames[v][k] = createCompatible(imgW, imgH, Transparency.TRANSLUCENT);
                Graphics2D cg = cachedPlantFrames[v][k].createGraphics();
                cg.setComposite(AlphaComposite.Src);
                cg.drawImage(raw, 0, 0, null);
                cg.dispose();
            }
        }
    }

    private static void drawProceduralPlantFrame(Graphics2D g, int cx, int baseY, int variant, double swayX) {
        switch (variant) {
            case 0 -> {
                // BIẾN THỂ 0: CỎ CAO THẢO NGUYÊN (Tall Meadow Grass Clump)
                // Khóm 7 dải lá cỏ đầy đặn, gốc xanh đậm rừng già, ngọn xanh mạ tươi đón nắng
                Color cDark  = new Color(14, 56, 26);
                Color cMid   = new Color(34, 197, 94);
                Color cLight = new Color(163, 230, 53);

                // 2 nhánh nhỏ xòe sát gốc
                drawBlade(g, cDark, cMid, cLight, cx - 8, baseY, cx - 18 + swayX * 0.45, baseY - 26, 4.8);
                drawBlade(g, cDark, cMid, cLight, cx + 8, baseY, cx + 18 + swayX * 0.50, baseY - 24, 4.6);

                // 2 dải lá hậu cảnh vươn nghiêng
                drawBlade(g, cDark, cDark, cMid, cx - 5, baseY, cx - 14 + swayX * 0.70, baseY - 42, 5.8);
                drawBlade(g, cDark, cDark, cMid, cx + 5, baseY, cx + 15 + swayX * 0.75, baseY - 40, 5.6);

                // 2 dải lá tiền cảnh xanh ngọc tươi
                drawBlade(g, cDark, cMid, cLight, cx - 3, baseY, cx - 7 + swayX * 0.88, baseY - 48, 6.2);
                drawBlade(g, cDark, cMid, cLight, cx + 3, baseY, cx + 8 + swayX * 0.92, baseY - 46, 6.0);

                // Dải lá ngọn trung tâm cao vút (sway max)
                drawBlade(g, cDark, cMid, cLight, cx, baseY, cx + 1 + swayX, baseY - 54, 6.8);
            }
            case 1 -> {
                // BIẾN THỂ 1: CỎ HOA DẠI THẢO NGUYÊN (Wildflower Grass Tuft)
                Color cDark  = new Color(18, 65, 30);
                Color cMid   = new Color(40, 160, 75);
                Color cLight = new Color(134, 239, 172);

                // Khóm 5 dải lá xanh nền tảng dày dặn
                drawBlade(g, cDark, cMid, cLight, cx - 7, baseY, cx - 16 + swayX * 0.55, baseY - 30, 5.0);
                drawBlade(g, cDark, cMid, cLight, cx + 7, baseY, cx + 16 + swayX * 0.60, baseY - 28, 4.8);
                drawBlade(g, cDark, cMid, cLight, cx - 3, baseY, cx - 8 + swayX * 0.80, baseY - 38, 5.6);
                drawBlade(g, cDark, cMid, cLight, cx + 3, baseY, cx + 9 + swayX * 0.85, baseY - 36, 5.4);
                drawBlade(g, cDark, cMid, cLight, cx, baseY, cx + swayX * 0.75, baseY - 42, 6.0);

                // Cuống hoa 1 (Hoa mao lương vàng tươi rực rỡ)
                double f1x = cx - 7 + swayX * 0.96;
                double f1y = baseY - 46;
                g.setColor(new Color(20, 83, 38));
                g.setStroke(new BasicStroke(1.6f));
                g.drawLine(cx - 3, baseY, (int) Math.round(f1x), (int) Math.round(f1y));

                // Bông hoa vàng 5 cánh nở rộ
                g.setColor(new Color(250, 204, 21));
                g.fillOval((int) Math.round(f1x) - 4, (int) Math.round(f1y) - 3, 8, 7);
                g.setColor(new Color(254, 240, 138));
                g.fillOval((int) Math.round(f1x) - 2, (int) Math.round(f1y) - 4, 4, 3);
                g.setColor(new Color(234, 88, 12));
                g.fillRect((int) Math.round(f1x) - 1, (int) Math.round(f1y) - 1, 2, 2);

                // Cuống hoa 2 (Hoa tím oải hương / cúc tím thanh nhã)
                double f2x = cx + 8 + swayX * 0.92;
                double f2y = baseY - 41;
                g.setColor(new Color(20, 83, 38));
                g.drawLine(cx + 3, baseY, (int) Math.round(f2x), (int) Math.round(f2y));

                // Bông hoa tím phấn
                g.setColor(new Color(192, 132, 252));
                g.fillOval((int) Math.round(f2x) - 3, (int) Math.round(f2y) - 3, 7, 6);
                g.setColor(new Color(233, 213, 255));
                g.fillOval((int) Math.round(f2x) - 1, (int) Math.round(f2y) - 3, 3, 2);
                g.setColor(new Color(254, 240, 138));
                g.fillRect((int) Math.round(f2x) - 1, (int) Math.round(f2y) - 1, 2, 2);

                // Cuống hoa 3 (Hoa cúc trắng nhỏ nở thấp)
                double f3x = cx + 1 + swayX * 0.72;
                double f3y = baseY - 32;
                g.setColor(new Color(20, 83, 38));
                g.drawLine(cx, baseY, (int) Math.round(f3x), (int) Math.round(f3y));
                g.setColor(new Color(248, 250, 252));
                g.fillOval((int) Math.round(f3x) - 2, (int) Math.round(f3y) - 2, 5, 5);
                g.setColor(new Color(245, 158, 11));
                g.fillRect((int) Math.round(f3x) - 1, (int) Math.round(f3y) - 1, 2, 2);
            }
            case 2 -> {
                // BIẾN THỂ 2: CỎ SẬY THON DÀI (Slender Reeds / Plume Grass)
                Color cDark  = new Color(24, 62, 32);
                Color cMid   = new Color(42, 128, 64);
                Color cLight = new Color(175, 215, 115);

                // 6 dải lá sậy mềm mại tỏa ngang
                drawBlade(g, cDark, cMid, cLight, cx - 6, baseY, cx - 18 + swayX * 0.65, baseY - 32, 4.2);
                drawBlade(g, cDark, cMid, cLight, cx + 6, baseY, cx + 18 + swayX * 0.70, baseY - 30, 4.0);
                drawBlade(g, cDark, cMid, cLight, cx - 4, baseY, cx - 12 + swayX * 0.80, baseY - 44, 4.5);
                drawBlade(g, cDark, cMid, cLight, cx + 4, baseY, cx + 13 + swayX * 0.85, baseY - 42, 4.4);

                // 2 thân sậy chính vươn cao
                // Thân 1 (chính, cao nhất)
                double reedTipX = cx + 1 + swayX;
                double reedTipY = baseY - 54;
                g.setColor(new Color(20, 70, 32));
                g.setStroke(new BasicStroke(1.8f));
                g.drawLine(cx, baseY, (int) Math.round(reedTipX), (int) Math.round(reedTipY));

                // Bông sậy phấn vàng hổ phách bồng bềnh
                g.setColor(new Color(160, 105, 35));
                g.fillOval((int) Math.round(reedTipX) - 3, (int) Math.round(reedTipY) - 3, 6, 14);
                g.setColor(new Color(254, 240, 138));
                g.fillOval((int) Math.round(reedTipX) - 2, (int) Math.round(reedTipY) - 1, 4, 10);
                g.setColor(new Color(255, 255, 255, 220));
                g.fillRect((int) Math.round(reedTipX) - 1, (int) Math.round(reedTipY) + 1, 2, 6);

                // Thân 2 (phụ bên phải)
                double reed2X = cx + 7 + swayX * 0.88;
                double reed2Y = baseY - 47;
                g.setColor(new Color(20, 70, 32));
                g.drawLine(cx + 3, baseY, (int) Math.round(reed2X), (int) Math.round(reed2Y));
                g.setColor(new Color(160, 105, 35));
                g.fillOval((int) Math.round(reed2X) - 2, (int) Math.round(reed2Y) - 2, 5, 11);
                g.setColor(new Color(254, 240, 138));
                g.fillOval((int) Math.round(reed2X) - 1, (int) Math.round(reed2Y), 3, 8);
            }
            case 3 -> {
                // BIẾN THỂ 3: CỎ KHÔ ÚA VÀNG (Withered Autumn Grass)
                Color cDark  = new Color(92, 48, 16);
                Color cMid   = new Color(175, 125, 42);
                Color cLight = new Color(245, 195, 95);

                drawBlade(g, cDark, cMid, cLight, cx - 8, baseY, cx - 17 + swayX * 0.50, baseY - 28, 4.6);
                drawBlade(g, cDark, cMid, cLight, cx + 8, baseY, cx + 17 + swayX * 0.55, baseY - 26, 4.4);
                drawBlade(g, cDark, cDark, cMid, cx - 5, baseY, cx - 13 + swayX * 0.72, baseY - 42, 5.5);
                drawBlade(g, cDark, cDark, cMid, cx + 5, baseY, cx + 14 + swayX * 0.76, baseY - 40, 5.2);
                drawBlade(g, cDark, cMid, cLight, cx - 2, baseY, cx - 6 + swayX * 0.88, baseY - 48, 6.0);
                drawBlade(g, cDark, cMid, cLight, cx + 2, baseY, cx + 8 + swayX * 0.92, baseY - 46, 5.8);
                drawBlade(g, cDark, cMid, cLight, cx, baseY, cx + 1 + swayX, baseY - 52, 6.4);
            }
        }
    }

    private static void drawBlade(Graphics2D g, Color dark, Color mid, Color light,
                                  double x0, double y0, double tipX, double tipY, double baseW) {
        double dx = tipX - x0;
        double dy = tipY - y0;
        double ctrlX = x0 + dx * 0.42;
        double ctrlY = y0 + dy * 0.62;

        // 1. Lớp viền tối bao quanh tạo bóng và độ nổi khối tách nền
        Path2D.Double p = new Path2D.Double();
        p.moveTo(x0 - baseW / 2.0, y0);
        p.quadTo(ctrlX - baseW * 0.45, ctrlY, tipX, tipY);
        p.quadTo(ctrlX + baseW * 0.45, ctrlY, x0 + baseW / 2.0, y0);
        p.closePath();
        g.setColor(dark);
        g.fill(p);

        // 2. Lớp thân chính đón nắng (Midtone)
        Path2D.Double pInner = new Path2D.Double();
        pInner.moveTo(x0 - baseW * 0.32, y0);
        pInner.quadTo(ctrlX - baseW * 0.12, ctrlY, tipX, tipY);
        pInner.quadTo(ctrlX + baseW * 0.28, ctrlY, x0 + baseW * 0.32, y0);
        pInner.closePath();
        g.setColor(mid);
        g.fill(pInner);

        // 3. Highlight viền đón nắng phía trên-trái và ngọn nhọn
        if (light != null) {
            Path2D.Double pLit = new Path2D.Double();
            pLit.moveTo(x0 - baseW * 0.30, y0);
            pLit.quadTo(ctrlX - baseW * 0.18, ctrlY, tipX, tipY);
            pLit.quadTo(ctrlX, ctrlY + 1, x0, y0);
            pLit.closePath();
            g.setColor(light);
            g.fill(pLit);

            g.fillRect((int) Math.round(tipX - 0.5), (int) Math.round(tipY - 0.5), 1, 2);
        }
    }

    private void drawGrassPlant(Graphics2D g, Camera camera, GrassPatch.ClusterPlant plant, PlayerSnake player, double time) {
        VegetationManager.init();
        double freq = 2.4;
        int frameIdx = (int) Math.floor(((time * freq + plant.swayPhase) / (Math.PI * 2.0)) * VegetationManager.SWAY_FRAME_COUNT) % VegetationManager.SWAY_FRAME_COUNT;
        if (frameIdx < 0) frameIdx += VegetationManager.SWAY_FRAME_COUNT;

        BufferedImage img = VegetationManager.getSwayFrame(plant.spriteId, plant.isFlipped, frameIdx);
        if (img == null) return;

        Point2D.Double pos = camera.worldToScreen(plant.worldX, plant.worldY);
        int drawX = (int) Math.round(pos.x - img.getWidth() / 2.0);
        int drawY = (int) Math.round(pos.y - img.getHeight() * 0.82); // Pivot chân cắm đất

        Composite oldComp = g.getComposite();

        // Kiểm tra nếu rắn ở ngay sau chân cỏ: giảm alpha xuống 0.70 để người chơi thấy bóng dáng rắn
        boolean snakeBehind = false;
        if (player.isAlive()) {
            double dy = player.getY() - plant.footY;
            double dx = Math.abs(player.getX() - plant.worldX);
            if (dy < 0 && dy > -plant.height && dx < plant.width * 0.85) {
                snakeBehind = true;
            }
        }

        if (snakeBehind) {
            g.setComposite(COMPOSITE_PLANT_OCCLUSION);
        }

        g.drawImage(img, drawX, drawY, null);

        if (snakeBehind) {
            g.setComposite(oldComp);
        }
    }

    // =========================================================================
    // VẼ THỰC VẬT RẶNG SAN HÔ (WIDE CORAL REEF RENDERING - ZERO-GC)
    // =========================================================================
    public static synchronized void initReefPlantSprites() {
        if (cachedReefPlantFrames[0][0] != null) return;
        int imgW = 76;
        int imgH = 88;
        int cx = 38;
        int baseY = 78;

        for (int v = 0; v < 5; v++) {
            for (int k = 0; k < REEF_PLANT_FRAME_COUNT; k++) {
                double swayFactor = Math.sin((k * 2.0 * Math.PI) / REEF_PLANT_FRAME_COUNT);
                double maxSway = swayFactor * REEF_SWAY_MAX_AMPLITUDE;

                BufferedImage raw = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = raw.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                drawProceduralReefFrame(g, cx, baseY, v, maxSway);
                g.dispose();

                cachedReefPlantFrames[v][k] = createCompatible(imgW, imgH, Transparency.TRANSLUCENT);
                Graphics2D cg = cachedReefPlantFrames[v][k].createGraphics();
                cg.setComposite(AlphaComposite.Src);
                cg.drawImage(raw, 0, 0, null);
                cg.dispose();
            }
        }
    }

    private static void drawProceduralReefFrame(Graphics2D g, int cx, int baseY, int variant, double swayX) {
        switch (variant) {
            case 0 -> {
                // BIẾN THỂ 0: SAN HÔ CÀNH CÀNH HƯƠU (Staghorn Branching Coral)
                // Phân nhánh đa tầng sắc nét, viền tím hồng, polyps vàng đón nước biển
                Color cDark  = new Color(136, 44, 76);
                Color cMid   = new Color(236, 72, 126);
                Color cLight = new Color(251, 182, 206);
                Color cGlint = new Color(254, 240, 138);

                double sX = swayX * 0.25; // San hô cứng đung đưa rất ít

                // 1. Thân chính vững chãi ở gốc
                drawBlade(g, cDark, cMid, cLight, cx, baseY, cx + sX * 0.2, baseY - 30, 9.0);

                // 2. Nhánh bên trái phân chạc
                drawBlade(g, cDark, cMid, cLight, cx - 3, baseY - 24, cx - 18 + sX * 0.6, baseY - 56, 5.8);
                drawBlade(g, cDark, cMid, cLight, cx - 10, baseY - 38, cx - 26 + sX * 0.5, baseY - 50, 4.5);

                // 3. Nhánh bên phải vươn cao
                drawBlade(g, cDark, cMid, cLight, cx + 3, baseY - 24, cx + 20 + sX * 0.7, baseY - 58, 6.0);
                drawBlade(g, cDark, cMid, cLight, cx + 11, baseY - 38, cx + 28 + sX * 0.6, baseY - 52, 4.6);

                // 4. Nhánh chóp ngọn trung tâm vươn cao nhất
                drawBlade(g, cDark, cMid, cLight, cx, baseY - 28, cx + 2 + sX * 0.9, baseY - 64, 5.5);

                // 5. Các hạt polyp san hô phát quang (1x1 và 2x2px vàng kim)
                g.setColor(cGlint);
                g.fillRect((int) Math.round(cx - 18 + sX * 0.6), baseY - 57, 2, 2);
                g.fillRect((int) Math.round(cx - 26 + sX * 0.5), baseY - 51, 2, 2);
                g.fillRect((int) Math.round(cx + 20 + sX * 0.7), baseY - 59, 2, 2);
                g.fillRect((int) Math.round(cx + 28 + sX * 0.6), baseY - 53, 2, 2);
                g.fillRect((int) Math.round(cx + 2 + sX * 0.9), baseY - 65, 2, 2);
                g.fillRect((int) Math.round(cx - 8), baseY - 34, 1, 1);
                g.fillRect((int) Math.round(cx + 8), baseY - 36, 1, 1);
            }
            case 1 -> {
                // BIẾN THỂ 1: SAN HÔ BÀN (Table Coral)
                // Các thềm mâm phẳng xếp tầng ngang, viền vàng hoàng kim đón ánh nắng đáy biển
                Color cDark  = new Color(120, 60, 16);
                Color cMid   = new Color(234, 140, 32);
                Color cLight = new Color(250, 204, 21);
                Color cRim   = new Color(254, 243, 199);

                double sX = swayX * 0.15; // Rất cứng cáp

                // Trụ đá ngầm gốc
                g.setColor(cDark);
                g.fillRect(cx - 6, baseY - 24, 12, 24);
                g.setColor(cMid);
                g.fillRect(cx - 4, baseY - 22, 8, 22);

                // Tầng 1: Mâm nhỏ bên trái
                int t1x = cx - 22;
                int t1y = baseY - 28;
                g.setColor(cDark);
                g.fillOval(t1x, t1y, 26, 9);
                g.setColor(cMid);
                g.fillOval(t1x + 1, t1y + 1, 24, 7);
                g.setColor(cRim);
                g.drawArc(t1x + 1, t1y + 1, 24, 7, 0, 180);

                // Tầng 2: Mâm trung bên phải
                int t2x = cx + 2;
                int t2y = baseY - 36;
                g.setColor(cDark);
                g.fillOval(t2x, t2y, 30, 10);
                g.setColor(cMid);
                g.fillOval(t2x + 1, t2y + 1, 28, 8);
                g.setColor(cRim);
                g.drawArc(t2x + 1, t2y + 1, 28, 8, 0, 180);

                // Tầng 3: Bàn san hô lớn nhất trên đỉnh
                int t3x = (int) Math.round(cx - 28 + sX * 0.4);
                int t3y = baseY - 50;
                g.setColor(cDark);
                g.fillOval(t3x, t3y, 56, 14);
                g.setColor(cLight);
                g.fillOval(t3x + 2, t3y + 2, 52, 10);
                g.setColor(cMid);
                g.fillOval(t3x + 4, t3y + 3, 48, 8);

                // Các rãnh vân hướng tâm trên mặt bàn
                g.setColor(cDark);
                g.drawLine(t3x + 14, t3y + 6, t3x + 24, t3y + 7);
                g.drawLine(t3x + 32, t3y + 7, t3x + 42, t3y + 6);

                // Viền ngọc sáng trắng ngà quanh mép bàn
                g.setColor(cRim);
                g.drawArc(t3x + 2, t3y + 2, 52, 10, 0, 180);
            }
            case 2 -> {
                // BIẾN THỂ 2: SAN HÔ NÃO TRÒN (Brain Coral)
                // Khối vòm đá san hô vững chắc có các rãnh vân mê cung cuộn sóng
                Color cDark   = new Color(16, 68, 76);
                Color cMid    = new Color(45, 190, 170);
                Color cLight  = new Color(167, 243, 208);
                Color cGroove = new Color(13, 50, 58);

                double sX = swayX * 0.08; // Khối đá san hô gần như tĩnh

                int domeX = (int) Math.round(cx - 24 + sX * 0.2);
                int domeY = baseY - 44;
                int domeW = 48;
                int domeH = 44;

                // Thân vòm nổi khối
                g.setColor(cDark);
                g.fillOval(domeX, domeY, domeW, domeH);
                g.setColor(cMid);
                g.fillOval(domeX + 2, domeY + 2, domeW - 4, domeH - 4);
                g.setColor(cLight);
                g.fillOval(domeX + 4, domeY + 3, domeW / 2, domeH / 2);

                // Các đường rãnh mê cung uốn lượn đặc trưng (Brain Maze Grooves)
                g.setColor(cGroove);
                g.drawArc(domeX + 8, domeY + 8, 14, 12, 30, 260);
                g.drawArc(domeX + 26, domeY + 10, 14, 12, 120, 240);
                g.drawArc(domeX + 12, domeY + 20, 16, 12, 0, 200);
                g.drawArc(domeX + 24, domeY + 22, 16, 10, 180, 200);
                g.drawArc(domeX + 16, domeY + 12, 18, 16, 210, 140);

                // Điểm gờ sáng đón nước biển
                g.setColor(cLight);
                g.fillRect(domeX + 10, domeY + 10, 2, 1);
                g.fillRect(domeX + 22, domeY + 8, 2, 1);
                g.fillRect(domeX + 16, domeY + 18, 2, 1);
            }
            case 3 -> {
                // BIẾN THỂ 3: SAN HÔ ỐNG / QUẠT (Tube / Sponge Coral)
                // Cụm 4 ống rực rỡ màu đỏ cam có miệng phễu rỗng và chùm polyps vàng
                Color cDark   = new Color(120, 20, 24);
                Color cMid    = new Color(239, 68, 68);
                Color cLight  = new Color(252, 165, 165);
                Color cInside = new Color(60, 12, 16);
                Color cPolyp  = new Color(250, 204, 21);

                double sX = swayX * 0.65; // Đung đưa vừa phải

                // Ống 1 (Trái ngoài, thấp 38px)
                drawCoralTube(g, cx - 16 + (int)(sX * 0.4), baseY, 10, 38, cDark, cMid, cLight, cInside, cPolyp);

                // Ống 2 (Trái trong, cao 52px)
                drawCoralTube(g, cx - 6 + (int)(sX * 0.6), baseY, 12, 52, cDark, cMid, cLight, cInside, cPolyp);

                // Ống 3 (Phải trong, cao nhất 60px)
                drawCoralTube(g, cx + 6 + (int)(sX * 0.75), baseY, 13, 60, cDark, cMid, cLight, cInside, cPolyp);

                // Ống 4 (Phải ngoài, cao 42px)
                drawCoralTube(g, cx + 18 + (int)(sX * 0.5), baseY, 11, 42, cDark, cMid, cLight, cInside, cPolyp);
            }
            case 4 -> {
                // BIẾN THỂ 4: RONG BIỂN LÁ MỀM (Leaf Kelp / Ribbon Seaweed)
                // Dải lá xanh ngọc lục bảo uốn lượn chữ S mềm mại theo dòng hải lưu
                Color cDark  = new Color(10, 48, 36);
                Color cMid   = new Color(16, 185, 129);
                Color cLight = new Color(110, 231, 183);

                double sX = swayX * 1.35; // Rong biển mềm đung đưa nhiều nhất

                // 2 Dải lá nhỏ xòe gốc
                drawBlade(g, cDark, cMid, cLight, cx - 8, baseY, cx - 18 + sX * 0.55, baseY - 32, 5.2);
                drawBlade(g, cDark, cMid, cLight, cx + 8, baseY, cx + 18 + sX * 0.60, baseY - 30, 5.0);

                // 2 Dải lá trung uốn lượn
                drawBlade(g, cDark, cDark, cMid, cx - 5, baseY, cx - 14 + sX * 0.90, baseY - 52, 6.2);
                drawBlade(g, cDark, cDark, cMid, cx + 5, baseY, cx + 15 + sX * 0.95, baseY - 50, 6.0);

                // Dải lá trung tâm vươn cao nhất (đỉnh rạn)
                drawBlade(g, cDark, cMid, cLight, cx, baseY, cx + sX * 1.35, baseY - 68, 7.0);
            }
        }
    }

    private static void drawCoralTube(Graphics2D g, int tx, int ty, int w, int h,
                                      Color dark, Color mid, Color light, Color inside, Color polyp) {
        int x0 = tx - w / 2;
        int y0 = ty - h;

        // Thân ống viền tối
        g.setColor(dark);
        g.fillRect(x0, y0, w, h);

        // Thân ống đón sáng
        g.setColor(mid);
        g.fillRect(x0 + 1, y0, w - 2, h);

        // Highlight dọc thân bên trái
        g.setColor(light);
        g.fillRect(x0 + 1, y0, 2, h);

        // Miệng phễu rỗng hình elip trên đỉnh
        g.setColor(dark);
        g.fillOval(x0 - 1, y0 - 3, w + 2, 6);
        g.setColor(inside);
        g.fillOval(x0 + 1, y0 - 2, w - 2, 4);

        // Chùm polyps vàng li ti quanh miệng ống
        g.setColor(polyp);
        g.fillRect(x0 - 1, y0 - 2, 1, 1);
        g.fillRect(x0 + w, y0 - 2, 1, 1);
        g.fillRect(x0 + w / 2, y0 - 4, 1, 1);
        g.fillRect(x0 + 2, y0 + 1, 1, 1);
    }

    private void drawCoralPlant(Graphics2D g, Camera camera, CoralReef.CoralPlant coral, PlayerSnake player, double time) {
        BufferedImage img;
        if (coral.isSwaying) {
            img = CoralManager.getSwayFrame(coral.spriteId, coral.swayPhase, coral.isFlipped, time);
        } else {
            img = CoralManager.getSprite(coral.spriteId, coral.isFlipped);
        }
        if (img == null) return;

        Point2D.Double pos = camera.worldToScreen(coral.worldX, coral.worldY);
        int drawX = (int) Math.round(pos.x - coral.width / 2.0);
        int drawY = (int) Math.round(pos.y - coral.height * 0.80);

        Composite oldComp = g.getComposite();

        // Kiểm tra nếu rắn ở ngay sau chân san hô: giảm alpha xuống 0.70 để người chơi thấy rõ
        boolean snakeBehind = false;
        if (player.isAlive()) {
            double dy = player.getY() - coral.footY;
            double dx = Math.abs(player.getX() - coral.worldX);
            if (dy < 0 && dy > -coral.height && dx < coral.width * 0.85) {
                snakeBehind = true;
            }
        }

        if (snakeBehind) {
            g.setComposite(COMPOSITE_REEF_OCCLUSION);
        }

        g.drawImage(img, drawX, drawY, coral.width, coral.height, null);

        // Chiều sâu không gian biển: Nhuộm sắc xanh teal nhẹ (alpha 0.08 - 0.24) theo khoảng cách camera
        double camDist = Math.hypot(coral.worldX - camera.getX(), coral.worldY - camera.getY());
        float depthFactor = (float) Math.min(1.0, Math.max(0.0, (camDist - 180.0) / 480.0));
        float tealAlpha = 0.08f + depthFactor * 0.16f;

        if (tealAlpha > 0.09f) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, tealAlpha));
            g.setColor(COLOR_UNDERWATER_TINT);
            g.fillRect(drawX, drawY, coral.width, coral.height);
        }

        g.setComposite(oldComp);
    }

    private void renderReefLife(Graphics2D g, Camera camera, List<Chunk> chunks, double time) {
        Composite oldComp = g.getComposite();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        for (Chunk c : chunks) {
            for (CoralReef reef : c.getCoralReefs()) {
                if (!reef.isVisible(camera)) continue;

                // 1. Đàn cá nhỏ bơi lượn quanh rạn (alpha 0.78, không va chạm)
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.78f));
                for (CoralReef.ReefFish fish : reef.getFishSchool()) {
                    double angle = time * fish.orbitSpeed + fish.phaseOffset;
                    double fx = reef.centerX + Math.cos(angle) * fish.orbitRadiusX;
                    double fy = reef.centerY + Math.sin(angle) * fish.orbitRadiusY;

                    Point2D.Double fpos = camera.worldToScreen(fx, fy);
                    int sx = (int) Math.round(fpos.x);
                    int sy = (int) Math.round(fpos.y);

                    double vx = -Math.sin(angle);
                    boolean faceRight = vx >= 0;

                    int fLen = fish.size;
                    int fH = Math.max(3, fish.size / 2);

                    // Thân cá
                    g.setColor(fish.color);
                    g.fillOval(sx - fLen / 2, sy - fH / 2, fLen, fH);

                    // Đuôi vẫy
                    double tailWag = Math.sin(time * 12.0 + fish.phaseOffset) * 2.0;
                    int tailX = faceRight ? (sx - fLen / 2 - 2) : (sx + fLen / 2 + 2);
                    int tailY = (int) (sy + tailWag);
                    g.setColor(fish.color.darker());
                    g.fillRect(tailX, tailY - 1, 2, 3);

                    // Mắt cá
                    int eyeX = faceRight ? (sx + fLen / 2 - 2) : (sx - fLen / 2 + 1);
                    g.setColor(Color.WHITE);
                    g.fillRect(eyeX, sy - 1, 1, 1);
                }

                // 2. Bong bóng khí li ti bay lên từ khe rạn
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.65f));
                for (int ci = 0; ci < reef.getBubbleCrevices().size(); ci++) {
                    Point2D.Double crev = reef.getBubbleCrevices().get(ci);
                    double bTimer = (time * 26.0 + ci * 19.0) % 55.0;
                    double by = crev.y - bTimer;
                    double bx = crev.x + Math.sin(time * 3.2 + ci * 2.1) * 3.5;

                    Point2D.Double bpos = camera.worldToScreen(bx, by);
                    int bsx = (int) Math.round(bpos.x);
                    int bsy = (int) Math.round(bpos.y);
                    int bSize = 3 + (ci % 2);

                    g.setColor(new Color(220, 245, 255, 140));
                    g.drawOval(bsx - bSize / 2, bsy - bSize / 2, bSize, bSize);
                    g.setColor(Color.WHITE);
                    g.fillRect(bsx - 1, bsy - 1, 1, 1);
                }
            }
        }

        g.setComposite(oldComp);
    }

    private void drawWall(Graphics2D g, double cx, double cy, int w, int h) {
        int x = (int) cx;
        int y = (int) cy;
        int x0 = x - w / 2;
        int y0 = y - h / 2;

        // 1. Thân tường đá khối kiên cố (Castle Stone Fortress Wall - vật cản duy nhất)
        g.setColor(new Color(30, 41, 59)); // Vữa sẫm & khung viền
        g.fillRoundRect(x0, y0, w, h, 6, 6);

        g.setColor(new Color(51, 65, 85)); // Nền gạch đá phiến
        g.fillRoundRect(x0 + 2, y0 + 2, w - 4, h - 4, 4, 4);

        // 3. Các viên gạch đá xây xen kẽ (Brick stone pattern)
        g.setColor(new Color(71, 85, 105));
        int rowH = Math.max(8, h / 3);
        // Hàng 1
        g.fillRect(x0 + 3, y0 + 3, w / 2 - 4, rowH - 2);
        g.fillRect(x0 + w / 2 + 1, y0 + 3, w / 2 - 4, rowH - 2);
        // Hàng 2 (xếp so le)
        g.fillRect(x0 + 3, y0 + rowH + 2, w / 3 - 3, rowH - 2);
        g.fillRect(x0 + w / 3 + 2, y0 + rowH + 2, w / 3 - 2, rowH - 2);
        g.fillRect(x0 + (w * 2) / 3 + 2, y0 + rowH + 2, w / 3 - 5, rowH - 2);
        // Hàng 3
        g.fillRect(x0 + 3, y0 + rowH * 2 + 1, w / 2 - 4, rowH - 3);
        g.fillRect(x0 + w / 2 + 1, y0 + rowH * 2 + 1, w / 2 - 4, rowH - 3);

        // 4. Highlight gờ đỉnh tường bằng đá mài sáng
        g.setColor(new Color(148, 163, 184));
        g.drawLine(x0 + 3, y0 + 2, x0 + w - 4, y0 + 2);

        // 5. Rêu phong và vết nứt (Ivy / Moss overgrowth)
        g.setColor(new Color(22, 101, 52));
        g.fillRect(x0 + 6, y0 + h - 10, 4, 8);
        g.fillRect(x0 + 10, y0 + h - 8, 3, 6);
        g.fillRect(x0 + w - 14, y0 + 4, 4, 6);
        g.fillRect(x0 + w - 10, y0 + 8, 3, 5);
        g.setColor(new Color(34, 197, 94));
        g.fillRect(x0 + 7, y0 + h - 9, 2, 6);
        g.fillRect(x0 + w - 13, y0 + 5, 2, 4);

        // Vết nứt đá
        g.setColor(new Color(15, 23, 42));
        g.drawLine(x - 6, y - 5, x - 2, y + 2);
        g.drawLine(x - 2, y + 2, x + 4, y + 1);
    }

    private void drawMountain(Graphics2D g, PlayerSnake player, WorldProp prop, double cx, double cy, int w, int h) {
        // NÚI ĐÁ TẦNG (Tiered Rocky Cliff / Mountain theo ảnh mẫu)
        int x = (int) cx;
        int y = (int) cy;

        double dist = Math.hypot(player.getX() - prop.getX(), player.getY() - prop.getY());
        if (player.getY() < prop.getY() && dist < 68.0) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Config.Render.TREE_OCCLUSION_ALPHA));
        }

        // 1. Thân vách núi đá nâu đất (Rocky Earth Slope)
        int[] mtnX = { x - 54, x - 32, x + 8, x + 48, x + 54, x + 44, x - 50 };
        int[] mtnY = { y + 36, y - 36, y - 46, y - 24, y + 36, y + 42, y + 42 };
        g.setColor(new Color(45, 26, 16)); // Đất nâu sẫm
        g.fillPolygon(mtnX, mtnY, mtnX.length);

        g.setColor(new Color(92, 61, 46)); // Thân vách đá đất
        g.fillPolygon(new int[]{ x - 50, x - 28, x + 6, x + 44, x + 48, x - 46 },
                      new int[]{ y + 34, y - 32, y - 42, y - 20, y + 34, y + 38 }, 6);

        // Vân nứt nẻ địa tầng thẳng đứng
        g.setColor(new Color(35, 18, 10));
        g.drawLine(x - 20, y - 10, x - 18, y + 26);
        g.drawLine(x - 6, y - 20, x - 4, y + 30);
        g.drawLine(x + 10, y - 15, x + 12, y + 15);

        // 2. Các bậc thang đá xám xếp tầng bên sườn phải (Stepped Stone Stairs như ảnh mẫu)
        drawRockStep(g, x + 10, y - 12, 28, 12);
        drawRockStep(g, x + 18, y + 2, 30, 14);
        drawRockStep(g, x + 24, y + 18, 28, 14);

        // 3. Thảm cỏ xanh trên đỉnh núi & các bậc thềm (Lush Green Grass Plateaus)
        drawGrassPlateau(g, x - 24, y - 48, 46, 16);
        drawGrassPlateau(g, x - 48, y - 14, 30, 12);
        drawGrassPlateau(g, x - 34, y + 10, 32, 12);

        // 4. Vụn đá nhỏ dưới chân núi
        g.setColor(new Color(100, 116, 139));
        g.fillRoundRect(x - 46, y + 32, 14, 8, 3, 3);
        g.fillRoundRect(x - 22, y + 34, 12, 7, 3, 3);
        g.fillRoundRect(x + 10, y + 34, 16, 8, 3, 3);
    }

    private void drawRockStep(Graphics2D g, int rx, int ry, int rw, int rh) {
        g.setColor(new Color(51, 65, 85));
        g.fillRect(rx, ry, rw, rh);
        g.setColor(new Color(100, 116, 139));
        g.fillRect(rx + 1, ry + 1, rw - 2, 4);
        g.setColor(new Color(148, 163, 184));
        g.drawLine(rx + 1, ry + 1, rx + rw - 2, ry + 1);
        g.setColor(new Color(30, 41, 59));
        g.fillRect(rx, ry + rh - 3, rw, 3);
    }

    private void drawGrassPlateau(Graphics2D g, int px, int py, int pw, int ph) {
        g.setColor(new Color(22, 101, 52));
        g.fillRoundRect(px, py + 3, pw, ph - 3, 6, 6);
        g.setColor(new Color(34, 197, 94));
        g.fillRoundRect(px + 1, py, pw - 2, ph - 4, 6, 6);
        g.setColor(new Color(134, 239, 172));
        g.fillRect(px + 4, py + 2, pw - 8, 2);

        g.setColor(new Color(22, 101, 52));
        for (int i = 4; i < pw - 6; i += 6) {
            g.fillRect(px + i, py + ph - 2, 3, 4);
        }
    }

    private void drawCave(Graphics2D g, double cx, double cy, int w, int h) {
        drawCave(g, cx, cy, w, h, false);
    }

    private void drawCave(Graphics2D g, double cx, double cy, int w, int h, boolean showLabel) {
        // HANG ĐÁ (Cobblestone Arch Cave chuẩn ảnh mẫu)
        int x = (int) cx;
        int y = (int) cy;

        // 1. Nền lòng hang tối sâu thẳm & thềm đất
        g.setColor(new Color(69, 26, 3)); // Đất lòng hang
        g.fillOval(x - 28, y + 6, 56, 20);
        g.setColor(new Color(2, 6, 23));  // Lòng hang sâu tối om
        g.fillArc(x - 25, y - 24, 50, 48, 0, 180);
        g.fillRect(x - 25, y, 50, 18);

        // Đốm phát quang thạch anh sâu trong hang
        g.setColor(new Color(56, 189, 248, 180));
        g.fillRect(x - 8, y + 2, 3, 3);
        g.setColor(new Color(134, 239, 172, 180));
        g.fillRect(x + 10, y + 6, 2, 2);

        // 2. Vòm đá xếp từ các khối đá tảng (Cobblestone Arch Boulders)
        drawCobblestone(g, x - 15, y - 38, 30, 16, true);
        drawCobblestone(g, x - 36, y - 30, 24, 16, true);
        drawCobblestone(g, x + 12, y - 30, 24, 16, true);
        drawCobblestone(g, x - 44, y - 14, 22, 18, false);
        drawCobblestone(g, x + 22, y - 14, 22, 18, false);
        drawCobblestone(g, x - 46, y + 4, 24, 20, false);
        drawCobblestone(g, x + 22, y + 4, 24, 20, false);

        // 3. Khóm cỏ và rêu xanh mọc bám trên vách đá (Overgrowth Grass & Moss)
        g.setColor(new Color(22, 101, 52));
        g.fillRect(x - 10, y - 44, 2, 8);
        g.fillRect(x - 6, y - 46, 2, 10);
        g.fillRect(x - 2, y - 43, 2, 7);
        g.fillRect(x + 4, y - 45, 2, 9);
        g.fillRect(x + 8, y - 42, 2, 6);
        g.setColor(new Color(34, 197, 94));
        g.fillRect(x - 9, y - 43, 1, 6);
        g.fillRect(x - 5, y - 45, 1, 8);
        g.fillRect(x + 5, y - 44, 1, 7);

        drawGrassTuft(g, x - 48, y - 18);
        drawGrassTuft(g, x - 46, y + 16);
        drawGrassTuft(g, x + 40, y - 14);
        drawGrassTuft(g, x + 38, y + 18);

        // 4. Các viên đá cuội nhỏ rải rác dưới chân hang
        g.setColor(new Color(100, 116, 139));
        g.fillOval(x - 40, y + 22, 8, 6);
        g.fillOval(x - 30, y + 24, 6, 5);
        g.fillOval(x + 28, y + 23, 7, 5);
        g.fillOval(x + 36, y + 21, 9, 6);

        // 5. Nhãn văn bản: chữ nhỏ, có viền 1px, chỉ hiện khi rắn đến gần
        if (showLabel) {
            drawOutlinedPixelString(g, "HANG ĐÁ (+4 HP/s)", x - 44, y - 48, new Color(134, 239, 172), HUD_TEXT_OUTLINE, FONT_PIXEL_SMALL, FONT_SMALL_BOLD);
        }
    }

    private void drawCobblestone(Graphics2D g, int sx, int sy, int sw, int sh, boolean hasMoss) {
        g.setColor(new Color(51, 65, 85));
        g.fillRoundRect(sx, sy, sw, sh, 6, 6);
        g.setColor(new Color(71, 85, 105));
        g.fillRoundRect(sx + 2, sy + 2, sw - 4, sh - 4, 4, 4);
        g.setColor(new Color(148, 163, 184));
        g.drawLine(sx + 2, sy + 1, sx + sw - 3, sy + 1);
        g.drawLine(sx + 1, sy + 2, sx + 1, sy + sh - 3);
        g.setColor(new Color(30, 41, 59));
        g.drawLine(sx + 2, sy + sh - 1, sx + sw - 2, sy + sh - 1);
        g.drawLine(sx + sw - 1, sy + 2, sx + sw - 1, sy + sh - 2);

        if (hasMoss) {
            g.setColor(new Color(22, 101, 52));
            g.fillRect(sx + 4, sy + 2, 6, 3);
            g.fillRect(sx + sw - 9, sy + 3, 5, 3);
            g.setColor(new Color(34, 197, 94));
            g.fillRect(sx + 5, sy + 2, 4, 1);
        }
    }

    private void drawGrassTuft(Graphics2D g, int gx, int gy) {
        g.setColor(new Color(22, 101, 52));
        g.fillRect(gx, gy - 6, 2, 8);
        g.fillRect(gx - 3, gy - 4, 2, 6);
        g.fillRect(gx + 3, gy - 5, 2, 7);
        g.setColor(new Color(34, 197, 94));
        g.fillRect(gx, gy - 5, 1, 6);
        g.fillRect(gx + 3, gy - 4, 1, 5);
    }

    private void drawProceduralBushVariant(Graphics2D g, int x, int y, int variant) {
        int v = Math.abs(variant) % 4;
        switch (v) {
            case 0 -> {
                // BIẾN THỂ 0: BỤI DÂU RỪNG / QUẢ MỌNG (Berry Bush)
                // 1. Tầng lá tối khuất nắng (Shadow)
                g.setColor(new Color(18, 58, 30));
                g.fillOval(x - 28, y - 26, 56, 38);
                g.fillOval(x - 36, y - 18, 38, 30);
                g.fillOval(x - 2, y - 20, 38, 30);
                g.fillOval(x - 20, y - 32, 40, 28);

                // 2. Tầng tán lá trung tính đón nắng (Midtone)
                g.setColor(new Color(28, 115, 52));
                g.fillOval(x - 25, y - 28, 50, 32);
                g.fillOval(x - 34, y - 20, 32, 26);
                g.fillOval(x + 2, y - 22, 32, 26);
                g.fillOval(x - 17, y - 33, 34, 24);

                // 3. Tầng viền sáng trên-trái (Highlight)
                g.setColor(new Color(56, 178, 86));
                g.fillOval(x - 22, y - 30, 24, 16);
                g.fillOval(x - 32, y - 22, 18, 14);
                g.fillOval(x + 5, y - 24, 18, 14);

                // Điểm xuyết lá mạ non
                g.setColor(new Color(110, 231, 130));
                g.fillRect(x - 16, y - 28, 4, 3);
                g.fillRect(x - 26, y - 19, 3, 2);
                g.fillRect(x + 10, y - 21, 3, 2);

                // 4. Chùm quả mọng chín đỏ (Ruby Berries)
                Color redDark = new Color(153, 27, 27);
                Color redBright = new Color(220, 38, 38);
                Color redHighlight = new Color(254, 202, 202);

                int[][] redBerries = {
                    {x - 16, y - 14}, {x - 12, y - 11}, {x - 17, y - 8},
                    {x + 12, y - 15}, {x + 16, y - 12},
                    {x - 2, y - 4}, {x + 3, y - 2}, {x - 4, y + 2},
                    {x - 24, y - 4}, {x + 20, y - 6}
                };
                for (int[] b : redBerries) {
                    g.setColor(redDark);
                    g.fillOval(b[0] - 3, b[1] - 3, 6, 6);
                    g.setColor(redBright);
                    g.fillOval(b[0] - 2, b[1] - 2, 4, 4);
                    g.setColor(redHighlight);
                    g.fillRect(b[0] - 1, b[1] - 1, 1, 1);
                }

                // 5. Quả mọng tím dại (Wild Blackberry)
                Color purpleDark = new Color(88, 28, 135);
                Color purpleBright = new Color(147, 51, 234);
                int[][] purpleBerries = {
                    {x - 6, y - 22}, {x + 1, y - 20}, {x - 22, y - 11}
                };
                for (int[] b : purpleBerries) {
                    g.setColor(purpleDark);
                    g.fillOval(b[0] - 3, b[1] - 3, 6, 6);
                    g.setColor(purpleBright);
                    g.fillOval(b[0] - 2, b[1] - 2, 4, 4);
                }
            }
            case 1 -> {
                // BIẾN THỂ 1: BỤI DƯƠNG XỈ / LÁ XẺ (Fern / Feathered Bush)
                int baseX = x;
                int baseY = y + 10;

                double[] angles = { -1.18, -0.72, -0.26, 0.26, 0.72, 1.18 };
                int[] lengths   = { 33, 40, 45, 45, 40, 33 };

                Color colStem = new Color(15, 62, 28);
                Color colFernDark = new Color(20, 83, 38);
                Color colFernMid = new Color(34, 197, 94);
                Color colFernLight = new Color(134, 239, 172);

                for (int f = 0; f < angles.length; f++) {
                    double ang = angles[f];
                    int len = lengths[f];
                    double endX = baseX + Math.sin(ang) * len;
                    double endY = baseY - Math.cos(ang) * len;

                    // Vẽ cuống chính
                    g.setColor(colStem);
                    g.drawLine(baseX, baseY, (int) Math.round(endX), (int) Math.round(endY));

                    // Các nhánh lá xẻ mọc dọc cuống
                    int pairs = 6;
                    for (int p = 1; p <= pairs; p++) {
                        double t = (double) p / (pairs + 1);
                        double nx = baseX + (endX - baseX) * t;
                        double ny = baseY + (endY - baseY) * t;
                        double leafLen = (1.0 - t * 0.45) * 8.5;

                        double sideAng = ang + Math.PI / 2.0;
                        int lx1 = (int) Math.round(nx - Math.sin(sideAng) * leafLen);
                        int ly1 = (int) Math.round(ny + Math.cos(sideAng) * leafLen);
                        int rx1 = (int) Math.round(nx + Math.sin(sideAng) * leafLen);
                        int ry1 = (int) Math.round(ny - Math.cos(sideAng) * leafLen);

                        g.setColor(colFernDark);
                        g.drawLine((int) Math.round(nx), (int) Math.round(ny), lx1, ly1);
                        g.drawLine((int) Math.round(nx), (int) Math.round(ny), rx1, ry1);

                        g.setColor(colFernMid);
                        g.fillRect((int) Math.round(nx) - 1, (int) Math.round(ny) - 1, 2, 2);
                        g.fillRect(lx1, ly1, 2, 2);
                        g.fillRect(rx1, ry1, 2, 2);

                        if (t > 0.6) {
                            g.setColor(colFernLight);
                            g.fillRect(lx1, ly1 - 1, 1, 1);
                            g.fillRect(rx1, ry1 - 1, 1, 1);
                        }
                    }

                    // Đỉnh ngọn măng non
                    g.setColor(colFernLight);
                    g.fillRect((int) Math.round(endX) - 1, (int) Math.round(endY) - 1, 2, 2);
                }

                // 2 mầm cuộn non (Fiddlehead / Croziers) ở gốc
                g.setColor(colFernMid);
                g.drawArc(baseX - 7, baseY - 12, 6, 6, 0, 270);
                g.drawArc(baseX + 2, baseY - 11, 5, 5, 90, 270);
            }
            case 2 -> {
                // BIẾN THỂ 2: BỤI GAI / RẬM RẠP (Bramble / Dense Thicket)
                Color colWoodDark = new Color(55, 33, 28);
                Color colThorn = new Color(130, 60, 20);

                int bx = x;
                int by = y + 8;

                // Các cành gỗ chính
                g.setColor(colWoodDark);
                g.drawLine(bx, by, bx - 18, by - 16);
                g.drawLine(bx - 18, by - 16, bx - 30, by - 26);
                g.drawLine(bx, by, bx + 16, by - 14);
                g.drawLine(bx + 16, by - 14, bx + 28, by - 24);
                g.drawLine(bx, by, bx - 3, by - 22);
                g.drawLine(bx - 3, by - 22, bx - 6, by - 34);
                g.drawLine(bx, by, bx + 9, by - 18);
                g.drawLine(bx + 9, by - 18, bx + 15, by - 32);

                // Gai nhọn nhô ra từ cành
                g.setColor(colThorn);
                g.drawLine(bx - 10, by - 9, bx - 13, by - 11);
                g.drawLine(bx - 24, by - 21, bx - 27, by - 24);
                g.drawLine(bx + 10, by - 8, bx + 13, by - 6);
                g.drawLine(bx + 22, by - 19, bx + 25, by - 17);
                g.drawLine(bx - 2, by - 15, bx - 5, by - 17);
                g.drawLine(bx + 6, by - 12, bx + 9, by - 14);

                // Các cụm lá gai góc cạnh bất đối xứng
                Color colLeafDark = new Color(20, 52, 28);
                Color colLeafMid  = new Color(44, 96, 52);
                Color colLeafLit  = new Color(82, 150, 78);

                // Cụm lá trái
                int[] lkx1 = { bx - 34, bx - 18, bx - 24, bx - 38 };
                int[] lky1 = { by - 24, by - 28, by - 14, by - 12 };
                g.setColor(colLeafDark);
                g.fillPolygon(lkx1, lky1, 4);
                g.setColor(colLeafMid);
                g.fillRect(bx - 30, by - 24, 10, 8);
                g.setColor(colLeafLit);
                g.fillRect(bx - 28, by - 26, 6, 4);

                // Cụm lá phải
                int[] rkx1 = { bx + 16, bx + 34, bx + 38, bx + 22 };
                int[] rky1 = { by - 26, by - 22, by - 10, by - 14 };
                g.setColor(colLeafDark);
                g.fillPolygon(rkx1, rky1, 4);
                g.setColor(colLeafMid);
                g.fillRect(bx + 20, by - 22, 10, 8);
                g.setColor(colLeafLit);
                g.fillRect(bx + 22, by - 24, 6, 4);

                // Cụm lá trung tâm & ngọn
                int[] ckx1 = { bx - 14, bx, bx + 14, bx + 8, bx - 10 };
                int[] cky1 = { by - 26, by - 36, by - 28, by - 18, by - 16 };
                g.setColor(colLeafDark);
                g.fillPolygon(ckx1, cky1, 5);
                g.setColor(colLeafMid);
                g.fillRect(bx - 8, by - 32, 14, 12);
                g.setColor(colLeafLit);
                g.fillRect(bx - 6, by - 34, 8, 5);

                // Điểm quả gai dại tím đen
                g.setColor(new Color(36, 18, 48));
                g.fillOval(bx - 14, by - 18, 4, 4);
                g.fillOval(bx + 12, by - 16, 4, 4);
                g.fillOval(bx - 2, by - 24, 4, 4);
            }
            case 3 -> {
                // BIẾN THỂ 3: BỤI HOA DẠI THẢO NGUYÊN (Wildflower Meadow Bush)
                // 1. Tán lá tròn mềm mại
                g.setColor(new Color(22, 78, 36));
                g.fillOval(x - 26, y - 24, 52, 34);
                g.fillOval(x - 34, y - 16, 32, 26);
                g.fillOval(x + 2, y - 18, 32, 26);

                g.setColor(new Color(34, 197, 94));
                g.fillOval(x - 22, y - 26, 44, 28);
                g.fillOval(x - 28, y - 18, 24, 20);
                g.fillOval(x + 4, y - 20, 24, 20);

                // Điểm sáng ấm ngọn lá
                g.setColor(new Color(163, 230, 53));
                g.fillRect(x - 18, y - 26, 6, 4);
                g.fillRect(x + 6, y - 24, 6, 4);
                g.fillRect(x - 6, y - 28, 8, 3);
                g.fillRect(x - 24, y - 16, 4, 3);

                // 2. Hoa vàng mao lương (Buttercups)
                Color flowerYellow = new Color(250, 204, 21);
                Color flowerOrange = new Color(217, 119, 6);
                int[][] yellowFlowers = {
                    {x - 18, y - 16}, {x + 12, y - 20}, {x - 4, y - 6},
                    {x + 18, y - 8}, {x - 22, y - 4}, {x + 6, y - 14}
                };
                for (int[] fl : yellowFlowers) {
                    g.setColor(flowerYellow);
                    g.fillRect(fl[0] - 2, fl[1] - 1, 5, 3);
                    g.fillRect(fl[0] - 1, fl[1] - 2, 3, 5);
                    g.setColor(flowerOrange);
                    g.fillRect(fl[0], fl[1], 1, 1);
                }

                // 3. Hoa cúc dại trắng (Meadow Daisies)
                Color flowerWhite = new Color(248, 250, 252);
                Color flowerAmber = new Color(245, 158, 11);
                int[][] whiteFlowers = {
                    {x - 10, y - 22}, {x + 4, y - 24}, {x - 12, y - 10},
                    {x + 14, y - 2}, {x - 2, y - 16}
                };
                for (int[] fl : whiteFlowers) {
                    g.setColor(flowerWhite);
                    g.fillRect(fl[0] - 2, fl[1] - 1, 5, 3);
                    g.fillRect(fl[0] - 1, fl[1] - 2, 3, 5);
                    g.setColor(flowerAmber);
                    g.fillRect(fl[0], fl[1], 1, 1);
                }
            }
        }
    }

    // =========================================================================
    // HẰNG SỐ CẤU HÌNH THỰC VẬT ĐẠI DƯƠNG V2 (SAN HÔ & TẢO BIỂN - OCEAN FLORA)
    // Tinh chỉnh: Dày dặn hơn 2-3x, có khối bệ đá, viền 1px tách nền, 3 tông chênh 25-30%, bóng elip chắc chắn
    // =========================================================================
    public static final float FLORA_BASE_ALPHA = 0.98f;          // Độ mờ cơ bản nâng lên 0.98 (0.95 - 1.0) để nổi rõ
    public static final float FLORA_OCCLUSION_ALPHA = 0.60f;     // Độ mờ khi rắn bơi qua bụi (~0.6)
    public static final float FLORA_SHADOW_ALPHA = 0.28f;        // Bóng elip chân đế đậm hơn (0.25 - 0.30)
    public static final double FLORA_SWAY_SPEED = 1.4;           // Tốc độ đung đưa nhịp nhàng dưới nước
    public static final double FLORA_SWAY_AMP = 8.5;             // Biên độ lắc giảm nhẹ (8.5px) vì nhánh đã dày hơn
    public static final double FLORA_SWAY_EXPONENT = 1.6;        // Số mũ gia tăng biên độ về ngọn (Math.pow(prog, 1.6))
    public static final double FLORA_BLADE_BASE_WIDTH = 6.2;     // Bán kính nhánh ở gốc (5-7px, bề ngang 11-13px)
    public static final double FLORA_BLADE_TIP_WIDTH = 2.4;      // Bán kính nhánh ở ngọn (2-3px, bề ngang 4.8-5.5px)

    // Bảng màu 4 cấp độ: Viền (~30% tối hơn) - Tối (mặt bóng) - Vừa (thân) - Sáng (mép đón nắng)
    // Độ chênh lệch giữa các tông đạt 25-30%, tăng độ bão hòa để nổi rõ khỏi nền nước biển xanh

    // 1. Gam Xanh Lục Rêu / Sage-Emerald (Tảo lá dài dải mềm)
    // 1. Gam Xanh Lục Rêu / Sage-Emerald (Tảo lá dài dải mềm - đã hòa sắc nước biển)
    public static final Color FLORA_MOSS_OUTLINE = new Color(20, 52, 42);     // Viền mảnh tách nền
    public static final Color FLORA_MOSS_DARK    = new Color(28, 70, 54);     // Mảng tối
    public static final Color FLORA_MOSS_MID     = new Color(44, 114, 88);    // Thân chính xanh rêu biển
    public static final Color FLORA_MOSS_LIGHT   = new Color(82, 168, 134);   // Highlight đón sáng dưới nước
    public static final Color FLORA_MOSS_GLINT   = new Color(132, 210, 178);  // Vệt sáng chói mảnh

    // 2. Gam Hổ Phách / Golden Amber Kelp (Tảo bẹ nâu lượn sóng)
    public static final Color FLORA_KELP_OUTLINE = new Color(48, 38, 24);     // Viền
    public static final Color FLORA_KELP_DARK    = new Color(72, 56, 34);     // Tối
    public static final Color FLORA_KELP_MID     = new Color(118, 92, 54);    // Vừa
    public static final Color FLORA_KELP_LIGHT   = new Color(168, 134, 82);   // Sáng
    public static final Color FLORA_KELP_GLINT   = new Color(212, 178, 126);  // Highlight

    // 3. Gam Hồng San Hô / Coral Rose (San hô cành gạc hươu - dịu sắc biển, không chói gắt)
    public static final Color FLORA_CORAL_OUTLINE = new Color(68, 30, 42);    // Viền
    public static final Color FLORA_CORAL_DARK    = new Color(102, 46, 60);   // Tối
    public static final Color FLORA_CORAL_MID     = new Color(152, 74, 92);   // Vừa
    public static final Color FLORA_CORAL_LIGHT   = new Color(196, 112, 130); // Sáng
    public static final Color FLORA_CORAL_GLINT   = new Color(228, 156, 172); // Highlight & chóp nụ

    // 4. Gam Tím Phấn / Royal Lavender (San hô cành tím & hải quỳ)
    public static final Color FLORA_PURPLE_OUTLINE = new Color(44, 34, 62);   // Viền
    public static final Color FLORA_PURPLE_DARK    = new Color(66, 48, 88);   // Tối
    public static final Color FLORA_PURPLE_MID     = new Color(104, 80, 134); // Vừa
    public static final Color FLORA_PURPLE_LIGHT   = new Color(150, 122, 182);// Sáng
    public static final Color FLORA_PURPLE_GLINT   = new Color(190, 168, 218);// Highlight & polyp

    // 5. Gam Xanh Ngọc Biển / Vibrant Aqua (Tảo phiến ngọc bích & San hô quạt)
    public static final Color FLORA_AQUA_OUTLINE = new Color(18, 58, 68);     // Viền
    public static final Color FLORA_AQUA_DARK    = new Color(28, 78, 90);     // Tối
    public static final Color FLORA_AQUA_MID     = new Color(46, 124, 138);   // Vừa
    public static final Color FLORA_AQUA_LIGHT   = new Color(84, 174, 188);   // Sáng
    public static final Color FLORA_AQUA_GLINT   = new Color(138, 212, 224);  // Highlight

    private void drawSeaweed(Graphics2D g, WorldProp prop, double cx, double cy, int w, int h, PlayerSnake player) {
        Composite oldComp = g.getComposite();

        // Độ trong suốt tự nhiên: 0.98f khi ở xa rắn, khi rắn bơi qua mờ còn 0.60f
        double distToPlayer = Math.hypot(player.getX() - prop.getX(), player.getY() - prop.getY());
        float alpha = (distToPlayer < prop.getRadius()) ? FLORA_OCCLUSION_ALPHA : FLORA_BASE_ALPHA;
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        int variant = prop.getVariant();
        switch (variant % 5) {
            case 0 -> drawGreenRibbonSeaweed(g, prop, cx, cy, w, h);
            case 1 -> drawTallBrownKelp(g, prop, cx, cy, w, h);
            case 2 -> drawRedAntlerCoral(g, prop, cx, cy, w, h);
            case 3 -> drawPurpleBranchingCoral(g, prop, cx, cy, w, h);
            case 4 -> drawCupCoral(g, prop, cx, cy, w, h);
            default -> drawGreenRibbonSeaweed(g, prop, cx, cy, w, h);
        }

        g.setComposite(oldComp);
    }

    // =========================================================================
    // NỀN MÓNG & BỆ ĐÁ NGẦM ĐÁY BIỂN (REEF PEDESTAL & GROUNDING SHADOW)
    // Tạo độ nặng chắc chắn ở đáy cây với cụm 3 khối đá ngầm và bóng elip đậm
    // =========================================================================
    private void drawFloraBase(Graphics2D g, int x, int y) {
        // Cụm sỏi đá ngầm và cát đáy biển bám rễ tự nhiên (Đã gỡ bỏ đĩa bệ tối 88x22 để tránh hiệu ứng đứng trên bệ)
        // Hòn đá chân trái
        drawReefPebble(g, x - 18, y + 17, 16, 10, new Color(48, 86, 98), new Color(74, 122, 136), new Color(24, 48, 56));
        // Hòn đá chân phải
        drawReefPebble(g, x + 6, y + 18, 16, 11, new Color(42, 76, 88), new Color(66, 110, 124), new Color(22, 44, 52));
        // Hòn đá khối trung tâm (nơi các nhánh bám rễ)
        drawReefPebble(g, x - 6, y + 18, 18, 12, new Color(54, 94, 108), new Color(86, 136, 152), new Color(26, 52, 60));
    }

    private void drawReefPebble(Graphics2D g, int px, int py, int pw, int ph, Color midCol, Color lightCol, Color darkCol) {
        g.setColor(darkCol);
        g.fillOval(px, py, pw, ph);
        g.setColor(midCol);
        g.fillOval(px + 1, py + 1, pw - 2, ph - 2);
        g.setColor(lightCol);
        g.fillOval(px + 2, py + 1, pw - 4, Math.max(3, ph / 2));
    }

    // =========================================================================
    // PHƯƠNG THỨC VẼ DẢI LÁ RONG/TẢO THON DÀI CHỮ S V2 (POLYGONAL RIBBON STRIP)
    // Dày 5-7px ở gốc, thon dần về 2-3px ở ngọn, viền 1px, 3 tông chênh 25-30%
    // =========================================================================
    private void drawCurvedRibbonBlade(Graphics2D g, int bx, int by, int height,
                                       double baseAngle, double sAmp, double phaseOffset,
                                       double baseHalfW, double tipHalfW,
                                       Color outlineCol, Color darkCol, Color midCol, Color lightCol, Color glintCol) {
        int segments = RIBBON_SEGMENTS;
        double sway = Math.sin(ambientTimer * FLORA_SWAY_SPEED + phaseOffset);

        for (int i = 0; i <= segments; i++) {
            double prog = (double) i / segments;
            double len = prog * height;

            // Uốn lượn hình chữ S tự nhiên
            double sOffset = Math.sin(prog * Math.PI * 1.15) * sAmp;

            // Đung đưa lượn sóng: ngọn lắc nhiều hơn gốc với Math.pow(prog, 1.6)
            double swayOffset = sway * Math.pow(prog, FLORA_SWAY_EXPONENT) * FLORA_SWAY_AMP;

            ribbonPx[i] = bx + Math.sin(baseAngle) * len + sOffset + swayOffset;
            ribbonPy[i] = by - Math.cos(baseAngle) * len;

            // Chiều rộng vuốt thon mềm mại: dày ở gốc (5-7px), thon dần về ngọn (2-3px)
            double taper = (1.0 - prog * 0.58) * (0.85 + 0.15 * Math.sin(prog * Math.PI));
            ribbonHw[i] = Math.max(tipHalfW, baseHalfW * taper);
        }

        // Tọa độ các điểm biên trái & phải
        for (int i = 0; i <= segments; i++) {
            double dx, dy;
            if (i == 0) {
                dx = ribbonPx[1] - ribbonPx[0];
                dy = ribbonPy[1] - ribbonPy[0];
            } else if (i == segments) {
                dx = ribbonPx[segments] - ribbonPx[segments - 1];
                dy = ribbonPy[segments] - ribbonPy[segments - 1];
            } else {
                dx = ribbonPx[i + 1] - ribbonPx[i - 1];
                dy = ribbonPy[i + 1] - ribbonPy[i - 1];
            }
            double segLen = Math.hypot(dx, dy);
            if (segLen < 0.001) segLen = 1.0;
            double nx = -dy / segLen;
            double ny = dx / segLen;

            ribbonLx[i] = ribbonPx[i] - nx * ribbonHw[i];
            ribbonLy[i] = ribbonPy[i] - ny * ribbonHw[i];
            ribbonRx[i] = ribbonPx[i] + nx * ribbonHw[i];
            ribbonRy[i] = ribbonPy[i] + ny * ribbonHw[i];
        }

        // Đặt stroke 1 lần duy nhất cho toàn bộ dải lá
        g.setStroke(STROKE_CAP_ROUND_1_2);

        for (int i = 1; i <= segments; i++) {
            // 1. Nửa dải bên phải / phía dưới-phải: Mảng tối tạo khối chiều sâu (Shadow Side)
            ribbonRQx[0] = (int) Math.round(ribbonPx[i - 1]);
            ribbonRQx[1] = (int) Math.round(ribbonRx[i - 1]);
            ribbonRQx[2] = (int) Math.round(ribbonRx[i]);
            ribbonRQx[3] = (int) Math.round(ribbonPx[i]);

            ribbonRQy[0] = (int) Math.round(ribbonPy[i - 1]);
            ribbonRQy[1] = (int) Math.round(ribbonRy[i - 1]);
            ribbonRQy[2] = (int) Math.round(ribbonRy[i]);
            ribbonRQy[3] = (int) Math.round(ribbonPy[i]);

            g.setColor(darkCol);
            g.fillPolygon(ribbonRQx, ribbonRQy, 4);

            // 2. Nửa dải bên trái / phía trên-trái: Thân chính tông vừa đón sáng (Lit Side)
            ribbonLQx[0] = (int) Math.round(ribbonLx[i - 1]);
            ribbonLQx[1] = (int) Math.round(ribbonPx[i - 1]);
            ribbonLQx[2] = (int) Math.round(ribbonPx[i]);
            ribbonLQx[3] = (int) Math.round(ribbonLx[i]);

            ribbonLQy[0] = (int) Math.round(ribbonLy[i - 1]);
            ribbonLQy[1] = (int) Math.round(ribbonPy[i - 1]);
            ribbonLQy[2] = (int) Math.round(ribbonPy[i]);
            ribbonLQy[3] = (int) Math.round(ribbonLy[i]);

            g.setColor(midCol);
            g.fillPolygon(ribbonLQx, ribbonLQy, 4);

            // 3. Viền mảnh 1px cùng gam màu (tối hơn thân 30%, không dùng đen thuần) để tách khỏi nền nước
            g.setColor(outlineCol);
            g.drawLine((int) Math.round(ribbonLx[i - 1]), (int) Math.round(ribbonLy[i - 1]),
                       (int) Math.round(ribbonLx[i]), (int) Math.round(ribbonLy[i]));
            g.drawLine((int) Math.round(ribbonRx[i - 1]), (int) Math.round(ribbonRy[i - 1]),
                       (int) Math.round(ribbonRx[i]), (int) Math.round(ribbonRy[i]));

            // 4. Đường highlight sáng mảnh 1px ở mép nhánh phía trên-trái (ôm sát mép trái đón sáng)
            double hx0 = ribbonLx[i - 1] + (ribbonPx[i - 1] - ribbonLx[i - 1]) * 0.28;
            double hy0 = ribbonLy[i - 1] + (ribbonPy[i - 1] - ribbonLy[i - 1]) * 0.28;
            double hx1 = ribbonLx[i] + (ribbonPx[i] - ribbonLx[i]) * 0.28;
            double hy1 = ribbonLy[i] + (ribbonPy[i] - ribbonLy[i]) * 0.28;

            g.setColor(lightCol);
            g.drawLine((int) Math.round(hx0), (int) Math.round(hy0), (int) Math.round(hx1), (int) Math.round(hy1));

            // Sống gân lá trung tâm
            if (i > 1 && i < segments) {
                g.setColor(glintCol);
                g.drawLine((int) Math.round(ribbonPx[i - 1]), (int) Math.round(ribbonPy[i - 1]),
                           (int) Math.round(ribbonPx[i]), (int) Math.round(ribbonPy[i]));
            }
        }

        // Chóp ngọn lá thon nhọn có viền và đốm sáng
        int tipX = (int) Math.round(ribbonPx[segments]);
        int tipY = (int) Math.round(ribbonPy[segments]);
        int tipR = (int) Math.round(tipHalfW);
        g.setColor(outlineCol);
        g.fillOval(tipX - tipR - 1, tipY - tipR - 1, (tipR + 1) * 2, (tipR + 1) * 2);
        g.setColor(midCol);
        g.fillOval(tipX - tipR, tipY - tipR, tipR * 2, tipR * 2);
        g.setColor(glintCol);
        g.fillRect(tipX - 1, tipY - 1, 2, 2);
    }

    // =========================================================================
    // BIẾN THỂ 0: RONG BIỂN DẢI XANH RÊU MỀM MẠI (GREEN RIBBON SEAWEED)
    // 7 nhánh dày dặn xòe tầng lớp từ bệ đá + 2 nhánh phụ nhỏ
    // =========================================================================
    private void drawGreenRibbonSeaweed(Graphics2D g, WorldProp prop, double cx, double cy, int w, int h) {
        int x = (int) cx;
        int y = (int) cy;

        // 1. Lớp lá phía sau (Layer 1 - Deep background blades mọc từ khe đá)
        drawCurvedRibbonBlade(g, x - 20, y + 19, 96, -0.38, -10.0, 0.4,
                              5.4, 2.2, FLORA_MOSS_OUTLINE, FLORA_MOSS_DARK, FLORA_MOSS_MID, FLORA_MOSS_LIGHT, FLORA_MOSS_GLINT);
        drawCurvedRibbonBlade(g, x - 10, y + 20, 126, -0.12, 12.0, 1.2,
                              6.0, 2.4, FLORA_MOSS_OUTLINE, FLORA_MOSS_DARK, FLORA_MOSS_MID, FLORA_MOSS_LIGHT, FLORA_MOSS_GLINT);
        drawCurvedRibbonBlade(g, x + 18, y + 19, 102, 0.36, -10.0, 3.4,
                              5.5, 2.2, FLORA_MOSS_OUTLINE, FLORA_MOSS_DARK, FLORA_MOSS_MID, FLORA_MOSS_LIGHT, FLORA_MOSS_GLINT);

        // 2. Thềm móng & bệ đá ngầm tạo độ nặng ở chân (Layer 2)
        drawFloraBase(g, x, y);

        // 3. Lớp lá phía trước (Layer 3 - Foreground lush blades)
        drawCurvedRibbonBlade(g, x - 14, y + 21, 114, -0.22, 11.0, 0.8,
                              6.2, 2.4, FLORA_MOSS_OUTLINE, FLORA_MOSS_DARK, FLORA_MOSS_MID, FLORA_MOSS_LIGHT, FLORA_MOSS_GLINT);
        drawCurvedRibbonBlade(g, x - 1, y + 23, 142, 0.02, -9.0, 1.8,
                              6.6, 2.6, FLORA_MOSS_OUTLINE, FLORA_MOSS_DARK, FLORA_MOSS_MID, FLORA_MOSS_LIGHT, FLORA_MOSS_GLINT);
        drawCurvedRibbonBlade(g, x + 9, y + 22, 124, 0.16, 11.0, 2.6,
                              6.2, 2.4, FLORA_MOSS_OUTLINE, FLORA_MOSS_DARK, FLORA_MOSS_MID, FLORA_MOSS_LIGHT, FLORA_MOSS_GLINT);
        drawCurvedRibbonBlade(g, x + 20, y + 21, 90, 0.30, -8.0, 3.2,
                              5.6, 2.2, FLORA_MOSS_OUTLINE, FLORA_MOSS_DARK, FLORA_MOSS_MID, FLORA_MOSS_LIGHT, FLORA_MOSS_GLINT);

        // 4. Nhánh phụ nhỏ ở tầm thấp giúp tán cây xòe đầy đặn
        drawCurvedRibbonBlade(g, x - 4, y + 14, 46, -0.42, -5.0, 1.4,
                              4.0, 1.8, FLORA_MOSS_OUTLINE, FLORA_MOSS_DARK, FLORA_MOSS_MID, FLORA_MOSS_LIGHT, FLORA_MOSS_GLINT);
        drawCurvedRibbonBlade(g, x + 6, y + 15, 42, 0.44, 5.0, 2.2,
                              3.8, 1.8, FLORA_MOSS_OUTLINE, FLORA_MOSS_DARK, FLORA_MOSS_MID, FLORA_MOSS_LIGHT, FLORA_MOSS_GLINT);

        // 5. Bong bóng khí nổi lên
        drawFloraBubbles(g, x, y, 3);
    }

    // =========================================================================
    // BIẾN THỂ 1: TẢO BẸ NÂU HỔ PHÁCH PHIẾN LÁ THON (SLENDER AMBER KELP)
    // 6 nhánh/thân chính + 8 phiến lá nhánh phụ dày dặn có khối
    // =========================================================================
    private void drawTallBrownKelp(Graphics2D g, WorldProp prop, double cx, double cy, int w, int h) {
        int x = (int) cx;
        int y = (int) cy;

        // Lớp lá phụ phía sau bệ đá
        drawCurvedRibbonBlade(g, x - 18, y + 19, 94, -0.28, -8.0, 0.5,
                              5.5, 2.2, FLORA_KELP_OUTLINE, FLORA_KELP_DARK, FLORA_KELP_MID, FLORA_KELP_LIGHT, FLORA_KELP_GLINT);
        drawCurvedRibbonBlade(g, x + 16, y + 19, 106, 0.26, 8.0, 2.0,
                              5.5, 2.2, FLORA_KELP_OUTLINE, FLORA_KELP_DARK, FLORA_KELP_MID, FLORA_KELP_LIGHT, FLORA_KELP_GLINT);

        // Bệ đá ngầm
        drawFloraBase(g, x, y);

        // 1 Cọng rong xanh điểm xuyết phía trước
        drawCurvedRibbonBlade(g, x - 12, y + 21, 68, -0.15, 6.0, 0.9,
                              4.8, 2.0, FLORA_MOSS_OUTLINE, FLORA_MOSS_DARK, FLORA_MOSS_MID, FLORA_MOSS_LIGHT, FLORA_MOSS_GLINT);

        // 2 Cành sườn tảo bẹ hổ phách
        drawCurvedRibbonBlade(g, x - 8, y + 21, 115, -0.10, -7.0, 1.0,
                              5.8, 2.4, FLORA_KELP_OUTLINE, FLORA_KELP_DARK, FLORA_KELP_MID, FLORA_KELP_LIGHT, FLORA_KELP_GLINT);
        drawCurvedRibbonBlade(g, x + 10, y + 21, 118, 0.12, 7.0, 2.4,
                              5.8, 2.4, FLORA_KELP_OUTLINE, FLORA_KELP_DARK, FLORA_KELP_MID, FLORA_KELP_LIGHT, FLORA_KELP_GLINT);

        // Cành chính tảo bẹ vươn cao với các phiến lá lượn sóng hai bên
        drawKelpFrondWithBlades(g, x + 1, y + 23, 140, 0.02, 1.4);

        drawFloraBubbles(g, x, y, 4);
    }

    private void drawKelpFrondWithBlades(Graphics2D g, int bx, int by, int height, double baseAngle, double phaseOffset) {
        int segments = 10;
        double[] px = new double[segments + 1];
        double[] py = new double[segments + 1];

        double sway = Math.sin(ambientTimer * FLORA_SWAY_SPEED + phaseOffset);

        for (int i = 0; i <= segments; i++) {
            double prog = (double) i / segments;
            double len = prog * height;
            double swayOffset = sway * Math.pow(prog, FLORA_SWAY_EXPONENT) * FLORA_SWAY_AMP;

            px[i] = bx + Math.sin(baseAngle) * len + swayOffset;
            py[i] = by - Math.cos(baseAngle) * len;
        }

        // Vẽ thân cọng tảo bẹ dày dặn (có viền 1px tách nền)
        g.setStroke(STROKE_CAP_ROUND_4_8);
        g.setColor(FLORA_KELP_OUTLINE);
        for (int i = 1; i <= segments; i++) {
            g.drawLine((int) px[i - 1], (int) py[i - 1], (int) px[i], (int) py[i]);
        }
        g.setStroke(STROKE_CAP_ROUND_3_2);
        g.setColor(FLORA_KELP_DARK);
        for (int i = 1; i <= segments; i++) {
            g.drawLine((int) px[i - 1], (int) py[i - 1], (int) px[i], (int) py[i]);
        }
        g.setStroke(STROKE_CAP_ROUND_1_8);
        g.setColor(FLORA_KELP_MID);
        for (int i = 1; i <= segments; i++) {
            g.drawLine((int) px[i - 1], (int) py[i - 1], (int) px[i], (int) py[i]);
        }

        // 8 Phiến lá thon lượn sóng mọc so le hai bên đốt thân
        for (int i = 2; i <= 8; i += 2) {
            double prog = (double) i / segments;
            int leafLen = (int) (32 * (1.1 - prog * 0.35));
            double nodeX = px[i];
            double nodeY = py[i];

            // Phiến lá trái
            drawCurvedRibbonBlade(g, (int) nodeX, (int) nodeY, leafLen, -0.68, -5.0, phaseOffset + i * 0.3,
                                  4.5, 2.0, FLORA_KELP_OUTLINE, FLORA_KELP_DARK, FLORA_KELP_MID, FLORA_KELP_LIGHT, FLORA_KELP_GLINT);
            // Phiến lá phải
            drawCurvedRibbonBlade(g, (int) nodeX, (int) nodeY, leafLen, 0.68, 5.0, phaseOffset + i * 0.3 + 1.2,
                                  4.5, 2.0, FLORA_KELP_OUTLINE, FLORA_KELP_DARK, FLORA_KELP_MID, FLORA_KELP_LIGHT, FLORA_KELP_GLINT);

            // Túi khí nổi nhỏ màu hổ phách vàng sáng có viền
            g.setColor(FLORA_KELP_OUTLINE);
            g.fillOval((int) nodeX - 3, (int) nodeY - 2, 7, 7);
            g.setColor(FLORA_KELP_LIGHT);
            g.fillOval((int) nodeX - 2, (int) nodeY - 1, 5, 5);
            g.setColor(FLORA_KELP_GLINT);
            g.fillRect((int) nodeX - 1, (int) nodeY, 2, 2);
        }

        // Chóp ngọn búp tảo nâu
        g.setColor(FLORA_KELP_OUTLINE);
        g.fillOval((int) px[segments] - 3, (int) py[segments] - 4, 7, 8);
        g.setColor(FLORA_KELP_LIGHT);
        g.fillOval((int) px[segments] - 2, (int) py[segments] - 3, 5, 6);
    }

    // =========================================================================
    // BIẾN THỂ 2: SAN HÔ CÀNH GẠC HƯƠU HỒNG PHẤN (DELICATE ANTLER CORAL)
    // 5 khóm cành sừng hươu dày dặn, chẽ nhánh tự nhiên, viền tách nền
    // =========================================================================
    private void drawRedAntlerCoral(Graphics2D g, WorldProp prop, double cx, double cy, int w, int h) {
        int x = (int) cx;
        int y = (int) cy;

        // Bệ rạn đá vôi
        drawFloraBase(g, x, y);

        // 5 Khóm san hô gạc hươu xòe nan quạt dày dặn
        drawAntlerStalk(g, x - 22, y + 21, 82, -0.36, 0.0);
        drawAntlerStalk(g, x - 11, y + 23, 110, -0.16, 0.8);
        drawAntlerStalk(g, x + 1, y + 24, 134, 0.02, 1.6);
        drawAntlerStalk(g, x + 12, y + 23, 114, 0.18, 2.4);
        drawAntlerStalk(g, x + 22, y + 21, 88, 0.36, 3.2);

        drawFloraBubbles(g, x, y, 3);
    }

    private void drawAntlerStalk(Graphics2D g, int bx, int by, int height, double tilt, double phaseOffset) {
        double sway = Math.sin(ambientTimer * FLORA_SWAY_SPEED + phaseOffset);

        // Thân chính từ gốc lên điểm phân nhánh (0% -> 42%)
        int midY = by - (int) (height * 0.42);
        double midProg = 0.42;
        int midX = bx + (int) (tilt * 24.0 + sway * Math.pow(midProg, FLORA_SWAY_EXPONENT) * (FLORA_SWAY_AMP * 0.7));

        // Đường thân chính có viền ngoài 1px, lớp bóng tối dưới và lớp thân sáng
        g.setStroke(STROKE_CAP_ROUND_6_8);
        g.setColor(FLORA_CORAL_OUTLINE);
        g.drawLine(bx, by, midX, midY);

        g.setStroke(STROKE_CAP_ROUND_5_0);
        g.setColor(FLORA_CORAL_DARK);
        g.drawLine(bx, by, midX, midY);

        g.setStroke(STROKE_CAP_ROUND_3_2);
        g.setColor(FLORA_CORAL_MID);
        g.drawLine(bx, by, midX, midY);

        // 1 Nhánh phụ nhỏ ở thân gốc (Spur branch)
        int spurY = by - (int) (height * 0.22);
        int spurX = bx + (int) (tilt * 10.0 + (tilt < 0 ? 8 : -8));
        g.setStroke(STROKE_CAP_ROUND_2_4);
        g.setColor(FLORA_CORAL_MID);
        g.drawLine(bx + (int) (tilt * 10.0), spurY, spurX, spurY - 10);
        g.setColor(FLORA_CORAL_GLINT);
        g.fillOval(spurX - 1, spurY - 11, 3, 3);

        // 2 Nhánh lớn chẽ ra (Left bough & Right bough)
        drawAntlerFork(g, midX, midY, height * 0.58, -0.38, sway, phaseOffset, 0.7);
        drawAntlerFork(g, midX, midY, height * 0.58, 0.38, sway, phaseOffset, 1.5);
    }

    private void drawAntlerFork(Graphics2D g, int startX, int startY, double len,
                                double forkAngle, double sway, double phaseOffset, double subPhase) {
        int forkSegments = 6;
        double[] fx = new double[forkSegments + 1];
        double[] fy = new double[forkSegments + 1];

        for (int i = 0; i <= forkSegments; i++) {
            double prog = (double) i / forkSegments;
            double curLen = prog * len;
            double swayOffset = sway * Math.pow(0.42 + prog * 0.58, FLORA_SWAY_EXPONENT) * (FLORA_SWAY_AMP * 0.75);

            double curve = Math.sin(prog * Math.PI * 0.8) * (forkAngle * 16.0);

            fx[i] = startX + Math.sin(forkAngle) * curLen + curve + swayOffset;
            fy[i] = startY - Math.cos(forkAngle * 0.5) * curLen;
        }

        // Vẽ thân nhánh có viền tách nền
        g.setStroke(STROKE_CAP_ROUND_4_8);
        g.setColor(FLORA_CORAL_OUTLINE);
        for (int i = 1; i <= forkSegments; i++) {
            g.drawLine((int) fx[i - 1], (int) fy[i - 1], (int) fx[i], (int) fy[i]);
        }
        g.setStroke(STROKE_CAP_ROUND_3_4);
        g.setColor(FLORA_CORAL_DARK);
        for (int i = 1; i <= forkSegments; i++) {
            g.drawLine((int) fx[i - 1], (int) fy[i - 1], (int) fx[i], (int) fy[i]);
        }
        g.setStroke(STROKE_CAP_ROUND_2_2);
        g.setColor(FLORA_CORAL_MID);
        for (int i = 1; i <= forkSegments; i++) {
            g.drawLine((int) fx[i - 1], (int) fy[i - 1], (int) fx[i], (int) fy[i]);
        }

        // Nhánh con nhỏ nhọn ở đốt thứ 3-4 (sừng hươu chẽ ngọn)
        int splitIdx = 3;
        double splitX = fx[splitIdx];
        double splitY = fy[splitIdx];
        int tipX = (int) (splitX + (forkAngle > 0 ? 10 : -10));
        int tipY = (int) (splitY - 16);

        g.setStroke(STROKE_CAP_ROUND_2_8);
        g.setColor(FLORA_CORAL_OUTLINE);
        g.drawLine((int) splitX, (int) splitY, tipX, tipY);
        g.setStroke(STROKE_CAP_ROUND_1_8);
        g.setColor(FLORA_CORAL_MID);
        g.drawLine((int) splitX, (int) splitY, tipX, tipY);

        // Nụ san hô hồng đào pastel nổi bật ở các đầu ngọn
        g.setColor(FLORA_CORAL_OUTLINE);
        g.fillOval(tipX - 3, tipY - 3, 6, 6);
        g.fillOval((int) fx[forkSegments] - 3, (int) fy[forkSegments] - 3, 7, 7);

        g.setColor(FLORA_CORAL_LIGHT);
        g.fillOval(tipX - 2, tipY - 2, 4, 4);
        g.fillOval((int) fx[forkSegments] - 2, (int) fy[forkSegments] - 2, 5, 5);

        g.setColor(FLORA_CORAL_GLINT);
        g.fillRect(tipX - 1, tipY - 1, 2, 2);
        g.fillRect((int) fx[forkSegments] - 1, (int) fy[forkSegments] - 1, 2, 2);
    }

    // =========================================================================
    // BIẾN THỂ 3: SAN HÔ CÀNH TÍM PHẤN & HẢI QUỲ MỀM (PURPLE CORAL & ANEMONES)
    // 7 nhánh tím nan quạt dày dặn + 2 cụm hải quỳ to có khối
    // =========================================================================
    private void drawPurpleBranchingCoral(Graphics2D g, WorldProp prop, double cx, double cy, int w, int h) {
        int x = (int) cx;
        int y = (int) cy;

        // 2 Dải lá xanh ngọc mọc phía sau bệ đá
        drawCurvedRibbonBlade(g, x - 20, y + 19, 86, -0.28, -7.0, 0.6,
                              5.4, 2.2, FLORA_AQUA_OUTLINE, FLORA_AQUA_DARK, FLORA_AQUA_MID, FLORA_AQUA_LIGHT, FLORA_AQUA_GLINT);
        drawCurvedRibbonBlade(g, x + 18, y + 19, 78, 0.28, 7.0, 2.2,
                              5.0, 2.0, FLORA_AQUA_OUTLINE, FLORA_AQUA_DARK, FLORA_AQUA_MID, FLORA_AQUA_LIGHT, FLORA_AQUA_GLINT);

        // Bệ đá ngầm chắc chắn
        drawFloraBase(g, x, y);

        // 1. Cụm San Hô Cành Tím Phấn ở trung tâm (7 nhánh xòe nan quạt dày dặn)
        drawPurpleFanCoral(g, x, y + 22, 108, 0.0);

        // 2. Hai cụm Hải Quỳ biển mềm mại đầy đặn ở 2 bên gốc
        drawSoftAnemone(g, x - 28, y + 20, 20, 24, 0.5);
        drawSoftAnemone(g, x + 26, y + 19, 18, 22, 1.8);

        drawFloraBubbles(g, x, y, 4);
    }

    private void drawPurpleFanCoral(Graphics2D g, int bx, int by, int height, double phaseOffset) {
        double sway = Math.sin(ambientTimer * FLORA_SWAY_SPEED + phaseOffset);

        // Gốc san hô tím dày dặn
        int trunkH = (int) (height * 0.35);
        int trunkTopY = by - trunkH;
        int trunkTopX = bx + (int) (sway * Math.pow(0.35, FLORA_SWAY_EXPONENT) * 4.0);

        g.setStroke(STROKE_CAP_ROUND_7_2);
        g.setColor(FLORA_PURPLE_OUTLINE);
        g.drawLine(bx, by, trunkTopX, trunkTopY);

        g.setStroke(STROKE_CAP_ROUND_5_2);
        g.setColor(FLORA_PURPLE_DARK);
        g.drawLine(bx, by, trunkTopX, trunkTopY);

        g.setStroke(STROKE_CAP_ROUND_3_6);
        g.setColor(FLORA_PURPLE_MID);
        g.drawLine(bx, by, trunkTopX, trunkTopY);

        // 7 Nhánh xòe nan quạt vươn lên thanh nhã và dày dặn
        double[] branchAngles = { -0.48, -0.32, -0.15, 0.0, 0.15, 0.32, 0.48 };
        int[] branchLens = {
            (int) (height * 0.52), (int) (height * 0.64), (int) (height * 0.72),
            (int) (height * 0.76),
            (int) (height * 0.70), (int) (height * 0.62), (int) (height * 0.50)
        };

        for (int b = 0; b < 7; b++) {
            double ang = branchAngles[b];
            int bLen = branchLens[b];
            int bSegs = 6;

            double curX = trunkTopX;
            double curY = trunkTopY;

            for (int s = 1; s <= bSegs; s++) {
                double prog = (double) s / bSegs;
                double totalProg = 0.35 + prog * 0.65;
                double segSway = sway * Math.pow(totalProg, FLORA_SWAY_EXPONENT) * (FLORA_SWAY_AMP * 0.80);

                double nxtX = trunkTopX + Math.sin(ang) * (prog * bLen) + segSway;
                double nxtY = trunkTopY - Math.cos(ang * 0.55) * (prog * bLen);

                float width = Math.max(2.2f, 4.4f - (float) prog * 1.8f);

                // Viền ngoài tách nền
                g.setStroke(getRoundStroke(width + 1.6f));
                g.setColor(FLORA_PURPLE_OUTLINE);
                g.drawLine((int) curX, (int) curY, (int) nxtX, (int) nxtY);

                // Thân cành tím
                g.setStroke(getRoundStroke(width));
                g.setColor(FLORA_PURPLE_MID);
                g.drawLine((int) curX, (int) curY, (int) nxtX, (int) nxtY);

                curX = nxtX;
                curY = nxtY;
            }

            // Đầu ngọn cành tím nụ polyp tròn sáng
            g.setColor(FLORA_PURPLE_OUTLINE);
            g.fillOval((int) curX - 4, (int) curY - 4, 8, 8);
            g.setColor(FLORA_PURPLE_LIGHT);
            g.fillOval((int) curX - 3, (int) curY - 3, 6, 6);
            g.setColor(FLORA_PURPLE_GLINT);
            g.fillRect((int) curX - 1, (int) curY - 1, 2, 2);
        }
    }

    private void drawSoftAnemone(Graphics2D g, int bx, int by, int stemW, int stemH, double phaseOffset) {
        // Thân củ hải quỳ có khối tròn và viền
        g.setColor(new Color(68, 28, 36));
        g.fillRoundRect(bx - stemW / 2 - 1, by - stemH - 1, stemW + 2, stemH + 2, 8, 8);

        g.setColor(new Color(138, 62, 72));
        g.fillRoundRect(bx - stemW / 2, by - stemH, stemW, stemH, 7, 7);

        g.setColor(new Color(188, 98, 108));
        g.fillRoundRect(bx - stemW / 2 + 1, by - stemH + 1, stemW - 2, stemH - 3, 6, 6);

        // Vành sáng trên mép củ hải quỳ
        g.setColor(new Color(235, 158, 168));
        g.drawArc(bx - stemW / 2 + 2, by - stemH, stemW - 4, 6, 0, 180);

        // 9 Xúc tu dày dặn uốn lượn tự nhiên
        int tentacleCount = 9;
        for (int i = 0; i < tentacleCount; i++) {
            double ang = -Math.PI * 0.84 + (i * Math.PI * 0.68 / (tentacleCount - 1));
            double tSway = Math.sin(ambientTimer * 2.2 + phaseOffset + i * 0.7) * 3.5;
            int tx = bx + (int) (Math.cos(ang) * 16 + tSway);
            int ty = (by - stemH) + (int) (Math.sin(ang) * 15);

            // Viền xúc tu
            g.setColor(new Color(68, 28, 36));
            g.setStroke(STROKE_CAP_ROUND_2_6);
            g.drawLine(bx, by - stemH + 2, tx, ty);

            // Thân xúc tu hồng đào
            g.setColor(new Color(228, 148, 158));
            g.setStroke(STROKE_CAP_ROUND_1_8);
            g.drawLine(bx, by - stemH + 2, tx, ty);

            // Chóp xúc tu điểm phấn sáng
            g.setColor(new Color(252, 206, 214));
            g.fillRect(tx - 1, ty - 1, 3, 3);
        }
    }

    // =========================================================================
    // BIẾN THỂ 4: TẢO NGỌC BÍCH NHẠT & SAN HÔ QUẠT (PALE AQUA SEAWEED & FAN CORAL)
    // 6 nhánh ngọc bích dày dặn + 2 phiến san hô quạt cam phấn xếp tầng
    // =========================================================================
    private void drawCupCoral(Graphics2D g, WorldProp prop, double cx, double cy, int w, int h) {
        int x = (int) cx;
        int y = (int) cy;

        // 2 Dải lá ngọc bích phía sau
        drawCurvedRibbonBlade(g, x - 22, y + 19, 86, -0.34, -8.0, 0.4,
                              5.5, 2.2, FLORA_AQUA_OUTLINE, FLORA_AQUA_DARK, FLORA_AQUA_MID, FLORA_AQUA_LIGHT, FLORA_AQUA_GLINT);
        drawCurvedRibbonBlade(g, x + 20, y + 19, 92, 0.32, 8.0, 2.4,
                              5.5, 2.2, FLORA_AQUA_OUTLINE, FLORA_AQUA_DARK, FLORA_AQUA_MID, FLORA_AQUA_LIGHT, FLORA_AQUA_GLINT);

        // Bệ đá ngầm
        drawFloraBase(g, x, y);

        // 4 Dải tảo ngọc bích vươn cao dày dặn phía trước
        drawCurvedRibbonBlade(g, x - 14, y + 21, 116, -0.18, 9.0, 1.0,
                              6.2, 2.4, FLORA_AQUA_OUTLINE, FLORA_AQUA_DARK, FLORA_AQUA_MID, FLORA_AQUA_LIGHT, FLORA_AQUA_GLINT);
        drawCurvedRibbonBlade(g, x - 2, y + 23, 138, 0.02, -8.0, 1.8,
                              6.6, 2.6, FLORA_AQUA_OUTLINE, FLORA_AQUA_DARK, FLORA_AQUA_MID, FLORA_AQUA_LIGHT, FLORA_AQUA_GLINT);
        drawCurvedRibbonBlade(g, x + 8, y + 22, 122, 0.16, 9.0, 2.6,
                              6.2, 2.4, FLORA_AQUA_OUTLINE, FLORA_AQUA_DARK, FLORA_AQUA_MID, FLORA_AQUA_LIGHT, FLORA_AQUA_GLINT);
        drawCurvedRibbonBlade(g, x + 16, y + 21, 98, 0.28, -7.0, 3.2,
                              5.6, 2.2, FLORA_AQUA_OUTLINE, FLORA_AQUA_DARK, FLORA_AQUA_MID, FLORA_AQUA_LIGHT, FLORA_AQUA_GLINT);

        // 2 Nhánh phụ nhỏ ở tầng thấp
        drawCurvedRibbonBlade(g, x - 6, y + 15, 48, -0.42, -5.0, 1.3,
                              4.0, 1.8, FLORA_AQUA_OUTLINE, FLORA_AQUA_DARK, FLORA_AQUA_MID, FLORA_AQUA_LIGHT, FLORA_AQUA_GLINT);
        drawCurvedRibbonBlade(g, x + 6, y + 16, 44, 0.44, 5.0, 2.1,
                              3.8, 1.8, FLORA_AQUA_OUTLINE, FLORA_AQUA_DARK, FLORA_AQUA_MID, FLORA_AQUA_LIGHT, FLORA_AQUA_GLINT);

        // Phiến san hô quạt pastel thấp tầng ở chân cụm (màu hồng cam phấn có viền và highlight)
        drawSoftCoralPlate(g, x - 14, y + 15, 44, 18, FLORA_CORAL_OUTLINE, FLORA_CORAL_DARK, FLORA_CORAL_MID, FLORA_CORAL_LIGHT);
        drawSoftCoralPlate(g, x + 12, y + 12, 48, 20, FLORA_CORAL_OUTLINE, FLORA_CORAL_DARK, FLORA_CORAL_MID, FLORA_CORAL_LIGHT);

        drawFloraBubbles(g, x, y, 3);
    }

    private void drawSoftCoralPlate(Graphics2D g, int cx, int cy, int pw, int ph,
                                    Color outlineCol, Color darkCol, Color midCol, Color lightCol) {
        // Viền tách nền
        g.setColor(outlineCol);
        g.fillOval(cx - pw / 2 - 1, cy - ph / 2 - 1, pw + 2, ph + 2);

        // Mảng tối dưới
        g.setColor(darkCol);
        g.fillOval(cx - pw / 2, cy - ph / 2, pw, ph);

        // Thân phiến màu vừa
        g.setColor(midCol);
        g.fillOval(cx - pw / 2 + 1, cy - ph / 2 + 1, pw - 2, ph - 3);

        // Vành lượn sóng sáng nhẹ trên mép phiến
        g.setColor(lightCol);
        g.drawArc(cx - pw / 2 + 3, cy - ph / 2 + 2, pw - 6, ph - 5, 0, 180);
    }

    private void drawFloraBubbles(Graphics2D g, int x, int y, int count) {
        for (int i = 0; i < count; i++) {
            double bubX = x + Math.sin(ambientTimer * 1.8 + i * 1.7) * 24 + (i - (count / 2.0)) * 14;
            double bubY = y - 10 - ((ambientTimer * 26.0 + i * 25) % 95);
            g.setColor(new Color(186, 230, 253, 140));
            g.drawOval((int) bubX, (int) bubY, 3, 3);
            g.setColor(new Color(240, 249, 255, 170));
            g.fillRect((int) bubX + 1, (int) bubY + 1, 1, 1);
        }
    }

    // =========================================================================
    // CÂY RỪNG SỒI THẢO NGUYÊN (TEMPERATE FOREST OAK TREE - MÀN 1)
    // Tán lá tròn xum xuê nhiều tầng đung đưa theo gió, thân gỗ sồi bạnh rễ
    // =========================================================================
    private void drawForestOakTree(Graphics2D g, double cx, double cy, int w, int h, double phase, double time) {
        int x = (int) Math.round(cx);
        int y = (int) Math.round(cy);

        // 1. Chân gốc và Rễ cây sồi bám đất (Trunk Base & Root Flare)
        int baseY = y + (int) Math.round(h * 0.35);
        int trunkTopY = y - (int) Math.round(h * 0.08);
        int trunkW = Math.max(10, (int) Math.round(w * 0.20));
        int trunkHalfW = trunkW / 2;

        // Bạnh rễ gỗ xòe ra bám vào đất
        int[] rootX = {
            x - trunkHalfW - 8, x - trunkHalfW, x - trunkHalfW + 2,
            x + trunkHalfW - 2, x + trunkHalfW, x + trunkHalfW + 9,
            x + trunkHalfW + 3, x, x - trunkHalfW - 3
        };
        int[] rootY = {
            baseY + 3, baseY - 6, trunkTopY,
            trunkTopY, baseY - 6, baseY + 3,
            baseY + 5, baseY + 4, baseY + 5
        };
        g.setColor(new Color(50, 30, 18)); // Viền / bóng sẫm rễ
        g.fillPolygon(rootX, rootY, rootX.length);

        // Thân gỗ sồi nâu ấm
        int[] trunkCoreX = {
            x - trunkHalfW - 5, x - trunkHalfW + 1,
            x + trunkHalfW - 1, x + trunkHalfW + 6,
            x + trunkHalfW + 1, x, x - trunkHalfW - 1
        };
        int[] trunkCoreY = {
            baseY + 1, trunkTopY + 2,
            trunkTopY + 2, baseY + 1,
            baseY + 3, baseY + 3, baseY + 3
        };
        g.setColor(new Color(78, 48, 28));
        g.fillPolygon(trunkCoreX, trunkCoreY, trunkCoreX.length);

        // Mảng vỏ thân đón nắng trên-trái
        g.setColor(new Color(115, 75, 42));
        g.fillRect(x - trunkHalfW, trunkTopY + 4, trunkHalfW, baseY - trunkTopY - 6);

        // Vân nứt dọc vỏ cây
        g.setColor(new Color(36, 20, 12));
        g.drawLine(x - 1, trunkTopY + 6, x, baseY - 2);
        g.drawLine(x + trunkHalfW - 3, trunkTopY + 10, x + trunkHalfW - 2, baseY - 4);

        // Rêu nhung bám quanh gốc thân
        g.setColor(new Color(22, 101, 52));
        g.fillRect(x - trunkHalfW - 4, baseY - 4, 6, 6);
        g.fillRect(x + trunkHalfW - 2, baseY - 3, 5, 5);
        g.setColor(new Color(34, 197, 94));
        g.fillRect(x - trunkHalfW - 2, baseY - 2, 3, 3);

        // 2. Tán lá vòm tròn xum xuê nhiều tầng (Lush Clustered Canopy)
        // Đung đưa nhẹ tự nhiên theo gió
        double sway = Math.sin(time * SWAY_FREQUENCY + phase) * (w * 0.040);
        int kx = x + (int) Math.round(sway);
        int ky = y - (int) Math.round(h * 0.16);
        int kw = (int) Math.round(w * 0.94);
        int kh = (int) Math.round(h * 0.68);

        // Định nghĩa 5 cụm vòm lá (Foliage Clusters)
        int[][] clusters = {
            { 0,                   (int)(kh * 0.10),  (int)(kw * 0.76), (int)(kh * 0.70) }, // Khối trung tâm
            { -(int)(kw * 0.24),   (int)(kh * 0.16),  (int)(kw * 0.54), (int)(kh * 0.50) }, // Khối dưới-trái
            { (int)(kw * 0.22),    (int)(kh * 0.18),  (int)(kw * 0.54), (int)(kh * 0.48) }, // Khối dưới-phải
            { -(int)(kw * 0.16),  -(int)(kh * 0.16),  (int)(kw * 0.58), (int)(kh * 0.54) }, // Khối trên-trái (đón nắng)
            { (int)(kw * 0.10),   -(int)(kh * 0.18),  (int)(kw * 0.52), (int)(kh * 0.50) }  // Khối đỉnh-phải
        };

        Color colCanopyShadow    = new Color(15, 50, 24);   // Tầng bóng tối dưới đáy vòm
        Color colCanopyMid       = new Color(28, 115, 48);  // Tầng thân lá xanh lục mướt
        Color colCanopyLit       = new Color(54, 175, 78);  // Tầng vòm đón nắng trên-trái
        Color colCanopyHighlight = new Color(134, 239, 172); // Gờ lá mạ non phản chiếu nắng

        // Bước 1: Vẽ tầng tối (Shadow Silhouette) cho tất cả các cụm
        g.setColor(colCanopyShadow);
        for (int[] cl : clusters) {
            int cx0 = kx + cl[0] - cl[2] / 2;
            int cy0 = ky + cl[1] - cl[3] / 2;
            g.fillOval(cx0, cy0, cl[2], cl[3]);
        }

        // Bước 2: Vẽ tầng trung tính (Midtone) co vào 2-3px
        g.setColor(colCanopyMid);
        for (int[] cl : clusters) {
            int cx0 = kx + cl[0] - cl[2] / 2 + 2;
            int cy0 = ky + cl[1] - cl[3] / 2 + 1;
            g.fillOval(cx0, cy0, cl[2] - 4, cl[3] - 4);
        }

        // Bước 3: Vẽ tầng sáng trên-trái (Sunlit Facet)
        g.setColor(colCanopyLit);
        for (int[] cl : clusters) {
            int cx0 = kx + cl[0] - cl[2] / 2 + 4;
            int cy0 = ky + cl[1] - cl[3] / 2 + 2;
            int litW = (int) (cl[2] * 0.65);
            int litH = (int) (cl[3] * 0.65);
            g.fillOval(cx0, cy0, litW, litH);
        }

        // Bước 4: Viền lá mạ non và các chùm lá đón nắng rực rỡ
        g.setColor(colCanopyHighlight);
        int topClX = kx - (int)(kw * 0.16);
        int topClY = ky - (int)(kh * 0.16);
        g.fillOval(topClX - 10, topClY - 14, 18, 10);
        g.fillOval(kx + (int)(kw * 0.08) - 8, ky - (int)(kh * 0.20) - 10, 16, 9);
        g.fillOval(kx - (int)(kw * 0.24) - 6, ky + (int)(kh * 0.12) - 10, 14, 8);

        // Vài chùm lá nhỏ nhô ra viền tạo cảm giác xốp tự nhiên
        g.fillRect(kx - (int)(kw * 0.44), ky - 4, 3, 4);
        g.fillRect(kx + (int)(kw * 0.42), ky - 2, 3, 4);
        g.fillRect(kx - 4, ky - (int)(kh * 0.42), 5, 3);
        g.fillRect(kx + 12, ky - (int)(kh * 0.38), 4, 3);

        // Vài quả sồi / đốm lá sẫm điểm xuyết
        g.setColor(new Color(180, 115, 35));
        g.fillRect(kx - (int)(kw * 0.10), ky + 4, 2, 3);
        g.fillRect(kx + (int)(kw * 0.15), ky + 8, 2, 3);
        g.fillRect(kx - (int)(kw * 0.05), ky - 12, 2, 3);
    }

    // =========================================================================
    // CÂY DỪA NHIỆT ĐỚI (COCONUT PALM TREE - THEO MEDIA_1793281579040.PNG)
    // =========================================================================
    private void drawCoconutPalm(Graphics2D g, double cx, double cy, int w, int h, PlayerSnake player, WorldProp prop) {
        int x = (int) cx;
        int y = (int) cy;

        Composite oldComp = g.getComposite();

        // Gò cát và Cụm rễ dừa chùm nổi (Exposed root flare)
        g.setColor(new Color(234, 179, 8, 160)); // Gò cát vàng
        g.fillOval(x - 26, y + 4, 52, 16);

        // Rễ dừa chùm tỏa ra bám trên cát
        g.setColor(new Color(120, 53, 15)); // #78350f
        g.setStroke(STROKE_CAP_ROUND_2_2);
        g.drawArc(x - 22, y + 2, 14, 12, 10, 140);
        g.drawArc(x - 26, y + 5, 16, 12, 0, 120);
        g.drawArc(x - 14, y + 7, 14, 10, 10, 160);
        g.drawArc(x + 4, y + 2, 14, 12, 40, 140);
        g.drawArc(x + 8, y + 5, 16, 12, 60, 120);
        g.drawArc(x - 2, y + 7, 14, 10, 20, 150);

        // 3. Thân dừa thon dài cong ngấn sẹo đốt lá (Segmented curved trunk)
        int trunkSegments = 10;
        int startX = x;
        int startY = y + 4;
        int crownX = x - 6;
        int crownY = y - 68;

        for (int i = 1; i <= trunkSegments; i++) {
            double prog = (double) i / trunkSegments;
            double curveOffset = Math.sin(prog * Math.PI) * -8.0;
            int curX = (int) (startX + (crownX - startX) * prog + curveOffset);
            int curY = (int) (startY + (crownY - startY) * prog);

            int segW = (int) (15.0 - prog * 5.0);

            // Khối đốt thân dừa
            g.setColor(new Color(146, 64, 14)); // #92400e
            g.fillRoundRect(curX - segW / 2, curY, segW, 8, 4, 4);

            // Vòng ngấn sẹo lá dừa (Bark ring ridges)
            g.setColor(new Color(120, 53, 15)); // #78350f
            g.drawLine(curX - segW / 2, curY + 6, curX + segW / 2, curY + 6);
            g.setColor(new Color(217, 119, 6)); // #d97706 highlight ngấn nắng
            g.drawLine(curX - segW / 2 + 1, curY + 2, curX + segW / 2 - 1, curY + 2);
        }

        // 4. Buồng dừa nâu trĩu quả (Cluster of 3 large ripe coconuts)
        g.setColor(new Color(88, 28, 135, 40));
        g.fillOval(crownX - 10, crownY + 2, 20, 14);

        drawSingleCoconut(g, crownX - 6, crownY + 4, 9, 11);
        drawSingleCoconut(g, crownX + 5, crownY + 4, 9, 11);
        drawSingleCoconut(g, crownX, crownY + 8, 10, 12);

        // 5. Vòm tán lá dừa xòe cong đu đưa trong gió (8 Arching Pinnate Palm Fronds)
        double wind = Math.sin(ambientTimer * 2.0);
        // Tán phía sau
        drawPalmFrond(g, crownX, crownY, -145, 48, wind * 2.5);
        drawPalmFrond(g, crownX, crownY, -35, 48, wind * 2.5);
        drawPalmFrond(g, crownX, crownY, -90, 52, wind * 1.5);
        // Tán hai bên xòe rộng
        drawPalmFrond(g, crownX, crownY, -175, 54, wind * 3.0);
        drawPalmFrond(g, crownX, crownY, -5, 54, wind * 3.0);
        drawPalmFrond(g, crownX, crownY, -210, 46, wind * 3.5);
        drawPalmFrond(g, crownX, crownY, 30, 46, wind * 3.5);
        // Tán rủ phía trước
        drawPalmFrond(g, crownX, crownY, -65, 46, wind * 2.0);

        g.setComposite(oldComp);
    }

    private void drawSingleCoconut(Graphics2D g, int cx, int cy, int cw, int ch) {
        g.setColor(new Color(113, 63, 18)); // #713f12 viền dừa
        g.fillOval(cx - cw / 2, cy - ch / 2, cw, ch);
        g.setColor(new Color(133, 77, 14)); // #854d0e vỏ dừa
        g.fillOval(cx - cw / 2 + 1, cy - ch / 2 + 1, cw - 2, ch - 2);
        g.setColor(new Color(161, 98, 7)); // #a16207 highlight
        g.fillRect(cx - 1, cy - ch / 2 + 2, 2, 3);
    }

    private void drawPalmFrond(Graphics2D g, int startX, int startY, double baseDeg, int length, double sway) {
        double rad = Math.toRadians(baseDeg);
        int steps = 7;
        double stepLen = (double) length / steps;

        int prevX = startX;
        int prevY = startY;

        for (int i = 1; i <= steps; i++) {
            double prog = (double) i / steps;
            double droop = prog * prog * 18.0;
            double curRad = rad + Math.toRadians(sway * prog);

            int curX = startX + (int) (Math.cos(curRad) * i * stepLen);
            int curY = startY + (int) (Math.sin(curRad) * i * stepLen + droop);

            // Gân sống lá dừa
            g.setColor(new Color(21, 128, 61)); // #15803d
            g.setStroke(STROKE_CAP_ROUND_1_8);
            g.drawLine(prevX, prevY, curX, curY);

            // Các lá chét xòe hai bên sống lá (Pinnate leaflets)
            int leafletW = (int) (12 * (1.1 - prog * 0.45));
            double normalAngle = curRad + Math.PI / 2.0;
            int nx = (int) (Math.cos(normalAngle) * leafletW);
            int ny = (int) (Math.sin(normalAngle) * leafletW);

            g.setColor(new Color(22, 163, 74)); // #16a34a
            g.drawLine(curX, curY, curX + nx, curY + ny);
            g.drawLine(curX, curY, curX - nx, curY - ny);

            g.setColor(new Color(132, 204, 22)); // #84cc16
            g.fillRect(curX + nx - 1, curY + ny - 1, 2, 2);
            g.fillRect(curX - nx - 1, curY - ny - 1, 2, 2);

            prevX = curX;
            prevY = curY;
        }

        // Chóp ngọn lá vàng óng ánh nắng
        g.setColor(new Color(234, 179, 8)); // #eab308
        g.fillRect(prevX - 1, prevY - 1, 2, 2);
    }

    private void drawGrassSpike(Graphics2D g, int bx, int by, double ang, int length, int baseW) {
        AffineTransform old = g.getTransform();
        g.translate(bx, by);
        g.rotate(ang);
        int[] px = { -baseW / 2, 0, baseW / 2 };
        int[] py = { 0, -length, 0 };
        g.fillPolygon(px, py, 3);
        g.setTransform(old);
    }

    private void drawWildflower(Graphics2D g, int fx, int fy) {
        g.setColor(new Color(248, 250, 252));
        g.fillRect(fx - 2, fy - 1, 5, 3);
        g.fillRect(fx - 1, fy - 2, 3, 5);
        g.setColor(new Color(250, 204, 21));
        g.fillRect(fx, fy, 1, 1);
    }

    public void drawBearTelegraph(Graphics2D g, Camera camera, Boss boss) {
        if (boss == null || boss.getType() != Boss.Type.BEAR) return;
        Point2D.Double pStart = camera.worldToScreen(boss.getX(), boss.getY());
        double rawDx = boss.getLockedTargetX() - boss.getX();
        double rawDy = boss.getLockedTargetY() - boss.getY();
        double dist = Math.hypot(rawDx, rawDy);
        double ux;
        double uy;
        double dirAngle;
        if (dist > 1e-3) {
            ux = rawDx / dist;
            uy = rawDy / dist;
            dirAngle = Math.atan2(uy, ux);
        } else {
            dirAngle = boss.getAngle();
            ux = Math.cos(dirAngle);
            uy = Math.sin(dirAngle);
        }
        double drawLen = Math.min(Math.max(dist, 60.0), Config.BossConfig.BEAR_LUNGE_MAX_DIST);
        double ex = pStart.x + ux * drawLen;
        double ey = pStart.y + uy * drawLen;

        double halfW = BEAR_TELEGRAPH_WIDTH * 0.5;
        double nx = -uy * halfW;
        double ny = ux * halfW;

        // Xây đa giác dải báo trước đầu bo tròn khép kín 15 đỉnh (0 chồng mờ, 0 đường nối thừa, 0 GC)
        bearTeleX[0] = (int) Math.round(pStart.x - nx);
        bearTeleY[0] = (int) Math.round(pStart.y - ny);
        bearTeleX[1] = (int) Math.round(pStart.x + nx);
        bearTeleY[1] = (int) Math.round(pStart.y + ny);
        final int capSteps = 12;
        for (int i = 0; i <= capSteps; i++) {
            double a = (dirAngle + Math.PI * 0.5) - (Math.PI * i / capSteps);
            bearTeleX[2 + i] = (int) Math.round(ex + Math.cos(a) * halfW);
            bearTeleY[2 + i] = (int) Math.round(ey + Math.sin(a) * halfW);
        }
        final int polyCount = 2 + capSteps + 1; // 15

        double progress = boss.getBearStateProgress();
        int idx = Math.max(0, Math.min(BEAR_TELEGRAPH_FILL_COLORS.length - 1, (int) Math.round(progress * 15.0)));
        boolean blink = progress >= 0.70 && (((int) Math.floor(boss.getBearStateTime() / BEAR_WINDUP_SHAKE_TIME)) & 1) == 0;

        g.setColor(blink ? BEAR_TELEGRAPH_BLINK_COLORS[idx] : BEAR_TELEGRAPH_FILL_COLORS[idx]);
        g.fillPolygon(bearTeleX, bearTeleY, polyCount);

        Stroke oldStroke = g.getStroke();
        g.setColor(COLOR_BEAR_TELEGRAPH_BORDER);
        g.setStroke(STROKE_PLAIN_1_8);
        g.drawPolygon(bearTeleX, bearTeleY, polyCount);
        g.setStroke(oldStroke);
    }

    /**
     * Vẽ vùng cảnh báo kỹ năng trên mặt đất cho Boss dạng Rắn (Lao cuộn vồ + Quẫy đuôi / Phun chùm độc).
     */
    public void drawSnakeBossTelegraph(Graphics2D g, Camera camera, Boss boss) {
        if (boss == null || boss.getStunTimer() > 0.0) return;
        if (boss.getSnakeState() != Boss.SnakeBossState.ROAR_WINDUP) return;

        Point2D.Double pStart = camera.worldToScreen(boss.getX(), boss.getY());
        double rawDx = boss.getLockedTargetX() - boss.getX();
        double rawDy = boss.getLockedTargetY() - boss.getY();
        double dist = Math.hypot(rawDx, rawDy);
        double dirAngle = (dist > 1e-3) ? Math.atan2(rawDy, rawDx) : boss.getAngle();
        double ux = Math.cos(dirAngle);
        double uy = Math.sin(dirAngle);
        double progress = boss.getSnakeStateProgress();
        Stroke oldStroke = g.getStroke();

        if (boss.getSnakeSkillType() == Boss.SnakeSkillType.COIL_DASH_WHIP) {
            // 1. Vùng nguy hiểm Quẫy Đuôi quanh phần đuôi Trăn
            List<SnakeSegment> segs = boss.getSegments();
            if (!segs.isEmpty()) {
                SnakeSegment tail = segs.get(segs.size() - 1);
                Point2D.Double tScreen = camera.worldToScreen(tail.x, tail.y);
                int r = (int) Math.round(Config.BossConfig.PYTHON_TAIL_WHIP_RADIUS);
                int curR = Math.max(18, (int) Math.round(r * progress));
                g.setColor(COLOR_PYTHON_TELE_WHIP_FILL);
                g.fillOval((int) Math.round(tScreen.x - r), (int) Math.round(tScreen.y - r), r * 2, r * 2);
                g.setColor(COLOR_PYTHON_TELE_WHIP_RING);
                g.setStroke(STROKE_PLAIN_2_0);
                g.drawOval((int) Math.round(tScreen.x - r), (int) Math.round(tScreen.y - r), r * 2, r * 2);
                g.drawOval((int) Math.round(tScreen.x - curR), (int) Math.round(tScreen.y - curR), curR * 2, curR * 2);
            }

            // 2. Đường lao thẳng (Dash Lane Capsule) không bám đuổi
            double dashDist = Config.BossConfig.PYTHON_DASH_SPEED * Config.BossConfig.PYTHON_DASH_TIME;
            double ex = pStart.x + ux * dashDist;
            double ey = pStart.y + uy * dashDist;
            double halfW = 22.0;
            double nx = -uy * halfW;
            double ny = ux * halfW;

            snakeTeleX[0] = (int) Math.round(pStart.x - nx);
            snakeTeleY[0] = (int) Math.round(pStart.y - ny);
            snakeTeleX[1] = (int) Math.round(pStart.x + nx);
            snakeTeleY[1] = (int) Math.round(pStart.y + ny);
            final int capSteps = 12;
            for (int i = 0; i <= capSteps; i++) {
                double a = (dirAngle + Math.PI * 0.5) - (Math.PI * i / capSteps);
                snakeTeleX[2 + i] = (int) Math.round(ex + Math.cos(a) * halfW);
                snakeTeleY[2 + i] = (int) Math.round(ey + Math.sin(a) * halfW);
            }
            int polyCount = 2 + capSteps + 1;
            g.setColor(COLOR_PYTHON_TELE_DASH_FILL);
            g.fillPolygon(snakeTeleX, snakeTeleY, polyCount);
            g.setColor(COLOR_PYTHON_TELE_DASH_BORDER);
            g.setStroke(STROKE_PLAIN_1_8);
            g.drawPolygon(snakeTeleX, snakeTeleY, polyCount);
        } else if (boss.getSnakeSkillType() == Boss.SnakeSkillType.VENOM_VOLLEY) {
            boolean enraged = boss.getHealth() <= boss.getMaxHealth() * 0.5;
            int count = enraged ? 5 : 3;
            double spreadStep = enraged ? 0.17 : 0.22;
            int half = count / 2;
            double rayLen = 280.0 * (0.45 + 0.55 * progress);

            g.setColor(COLOR_PYTHON_TELE_VENOM_RAY);
            g.setStroke(STROKE_PLAIN_2_5);
            for (int i = -half; i <= half; i++) {
                double rayAng = dirAngle + i * spreadStep;
                int x1 = (int) Math.round(pStart.x + Math.cos(rayAng) * 26.0);
                int y1 = (int) Math.round(pStart.y + Math.sin(rayAng) * 26.0);
                int x2 = (int) Math.round(pStart.x + Math.cos(rayAng) * rayLen);
                int y2 = (int) Math.round(pStart.y + Math.sin(rayAng) * rayLen);
                g.drawLine(x1, y1, x2, y2);
                g.fillOval(x2 - 4, y2 - 4, 8, 8);
            }
        }
        g.setStroke(oldStroke);
    }

    /**
     * Vẽ vòng hào quang tụ Mana & hạt cầu Mana xoáy tụ về Boss trong 2.0 giây hồi phục.
     */
    private void drawBossRecoveryAura(Graphics2D g, double cx, double cy, Boss boss, double baseRadius) {
        if (boss == null || !boss.isRecovering() || boss.getStunTimer() > 0.0) return;

        double anim = boss.getAnimTimer();
        double prog = boss.getRecoveryProgress();
        double pulseR = baseRadius + Math.sin(anim * 6.5) * 4.5;
        Stroke oldStroke = g.getStroke();

        // Vòng năng lượng nền & cung tiến trình hồi phục
        g.setColor(COLOR_BOSS_RECOVERY_RING);
        g.setStroke(STROKE_PLAIN_2_0);
        int ir = (int) Math.round(pulseR);
        g.drawOval((int) Math.round(cx - ir), (int) Math.round(cy - ir * 0.65), ir * 2, (int) Math.round(ir * 1.3));

        g.setColor(COLOR_BOSS_RECOVERY_PROGRESS);
        g.setStroke(STROKE_PLAIN_3_0);
        int innerR = Math.max(12, (int) Math.round(baseRadius * 0.78));
        g.drawArc((int) Math.round(cx - innerR), (int) Math.round(cy - innerR * 0.65),
                innerR * 2, (int) Math.round(innerR * 1.3), 90, -(int) Math.round(360.0 * prog));

        // 4 hạt Mana xanh ngọc xoáy hội tụ vào tâm Boss
        double spin = anim * 4.2;
        double orbDist = baseRadius * (1.12 - 0.52 * prog);
        for (int i = 0; i < 4; i++) {
            double a = spin + i * (Math.PI * 0.5);
            int ox = (int) Math.round(cx + Math.cos(a) * orbDist);
            int oy = (int) Math.round(cy + Math.sin(a) * orbDist * 0.68);
            g.setColor(COLOR_BOSS_MANA_GLOW);
            g.fillOval(ox - 5, oy - 5, 10, 10);
            g.setColor(COLOR_BOSS_RECOVERY_CORE);
            g.fillOval(ox - 2, oy - 2, 4, 4);
        }
        g.setStroke(oldStroke);
    }

    /**
     * Vẽ 3 chấm tròn choáng quay tròn phía trên đầu Boss (Zero-GC, không dùng ký tự tofu).
     */
    private void drawBossStunDots(Graphics2D g, double cx, double cy, double animTimer, double rx, double ry) {
        g.setColor(COLOR_BEAR_STUN_DOT);
        double stAngle = animTimer * 6.0;
        for (int i = 0; i < 3; i++) {
            double a = stAngle + i * (Math.PI * 2.0 / 3.0);
            int sx = (int) Math.round(cx + Math.cos(a) * rx);
            int sy = (int) Math.round(cy + Math.sin(a) * ry);
            g.fillOval(sx - 4, sy - 4, 8, 8);
        }
    }

    /**
     * Vẽ các hiệu ứng đốt thân, khớp nối vảy, chóp đuôi rung cảnh báo, sóng chấn động Quẫy Đuôi
     * và luồng năng lượng truyền dọc thân của Boss dạng Rắn (Giant Python / Void Dragon).
     */
    private void drawSnakeBossSegmentAndSkillDetails(Graphics2D g, Camera camera, Boss boss,
                                                     int segCount, int totalPts, boolean isVoid,
                                                     Composite oldComp, Stroke oldStroke) {
        Boss.SnakeBossState sState = boss.getSnakeState();
        double anim = boss.getAnimTimer();

        // 1. Gờ khớp nối giữa các đốt thân (tăng chiều sâu chuyển động đoạn thân)
        if (segCount >= 3) {
            g.setColor(isVoid ? COLOR_VOID_JOINT_RIDGE : COLOR_PYTHON_JOINT_RIDGE);
            g.setStroke(STROKE_PLAIN_2_0);
            List<SnakeSegment> segs = boss.getSegments();
            for (int i = 1; i < segCount - 1; i += 2) {
                double sx = snakeCtrlX[i];
                double sy = snakeCtrlY[i];
                double ang = segs.get(i).angle + Math.PI * 0.5;
                double halfSpan = 10.5;
                int x1 = (int) Math.round(sx - Math.cos(ang) * halfSpan);
                int y1 = (int) Math.round(sy - Math.sin(ang) * halfSpan);
                int x2 = (int) Math.round(sx + Math.cos(ang) * halfSpan);
                int y2 = (int) Math.round(sy + Math.sin(ang) * halfSpan);
                g.drawLine(x1, y1, x2, y2);
            }
            g.setStroke(oldStroke);
        }

        // 2. Chóp đuôi có đốt chuông vàng/tinh thể & rung bần bật khi rít gầm (ROAR_WINDUP)
        if (segCount >= 2) {
            SnakeSegment tail = boss.getSegments().get(segCount - 1);
            double tx = snakeCtrlX[segCount - 1];
            double ty = snakeCtrlY[segCount - 1];
            if (sState == Boss.SnakeBossState.ROAR_WINDUP && boss.getStunTimer() <= 0.0) {
                double vib = Math.sin(anim * 52.0) * 3.8;
                double perp = tail.angle + Math.PI * 0.5;
                tx += Math.cos(perp) * vib;
                ty += Math.sin(perp) * vib;
            }
            AffineTransform tailXf = g.getTransform();
            g.translate(tx, ty);
            g.rotate(tail.angle);
            g.setColor(isVoid ? COLOR_VOID_JOINT_RIDGE : COLOR_PYTHON_RATTLE_DARK);
            g.fillOval(-14, -7, 14, 14);
            g.setColor(isVoid ? COLOR_BOSS_MANA_FILL : COLOR_PYTHON_RATTLE_GOLD);
            g.fillOval(-10, -5, 9, 10);
            g.setColor(Color.WHITE);
            g.fillOval(-7, -2, 3, 4);

            if (sState == Boss.SnakeBossState.ROAR_WINDUP && boss.getStunTimer() <= 0.0) {
                int ringR = 12 + (int) Math.round(Math.sin(anim * 28.0) * 4.0);
                g.setColor(COLOR_PYTHON_CHARGE_PULSE);
                g.setStroke(STROKE_PLAIN_2_0);
                g.drawOval(-5 - ringR, -ringR, ringR * 2, ringR * 2);
                g.setStroke(oldStroke);
            }
            g.setTransform(tailXf);
        }

        // 3. Luồng xung năng lượng chạy dọc từ đuôi lên đầu khi đang ROAR_WINDUP
        if (totalPts >= 4 && sState == Boss.SnakeBossState.ROAR_WINDUP && boss.getStunTimer() <= 0.0) {
            double prog = boss.getSnakeStateProgress();
            int pulseIdx = Math.max(0, Math.min(totalPts - 1, (int) Math.round((1.0 - prog) * (totalPts - 1))));
            for (int k = 0; k < 3; k++) {
                int idx = Math.min(totalPts - 1, pulseIdx + k * 4);
                int ox = (int) Math.round(snakeSplineX[idx]);
                int oy = (int) Math.round(snakeSplineY[idx]);
                int rad = 14 - k * 3;
                g.setColor(COLOR_PYTHON_CHARGE_PULSE);
                g.fillOval(ox - rad, oy - rad, rad * 2, rad * 2);
                g.setColor(COLOR_PYTHON_CHARGE_CORE);
                g.fillOval(ox - rad / 2, oy - rad / 2, rad, rad);
            }
        }

        // 4. Các đốm sáng Mana chạy dọc đốt thân khi Boss đang nghỉ 2 giây hồi phục
        if (totalPts >= 4 && boss.isRecovering() && boss.getStunTimer() <= 0.0) {
            double flow = (anim * 0.75) % 1.0;
            for (int k = 0; k < 3; k++) {
                double posNorm = (flow + k / 3.0) % 1.0;
                int idx = Math.max(0, Math.min(totalPts - 1, (int) Math.round((1.0 - posNorm) * (totalPts - 1))));
                int ox = (int) Math.round(snakeSplineX[idx]);
                int oy = (int) Math.round(snakeSplineY[idx]);
                g.setColor(COLOR_BOSS_MANA_GLOW);
                g.fillOval(ox - 8, oy - 8, 16, 16);
                g.setColor(COLOR_BOSS_RECOVERY_CORE);
                g.fillOval(ox - 4, oy - 4, 8, 8);
            }
        }

        // 5. Sóng chấn động Quẫy Đuôi (Tail Whip Expanding Shockwave Ring)
        double whipT = boss.getTailWhipFxTimer();
        if (whipT >= 0.0) {
            double whipProg = Math.min(1.0, whipT / 0.45);
            Point2D.Double wScreen = camera.worldToScreen(boss.getTailWhipFxX(), boss.getTailWhipFxY());
            int rad = (int) Math.round(24.0 + whipProg * Config.BossConfig.PYTHON_TAIL_WHIP_RADIUS);
            g.setColor(COLOR_PYTHON_TELE_WHIP_FILL);
            g.fillOval((int) Math.round(wScreen.x - rad), (int) Math.round(wScreen.y - rad), rad * 2, rad * 2);
            g.setColor(COLOR_PYTHON_CHARGE_PULSE);
            g.setStroke(STROKE_PLAIN_3_0);
            g.drawOval((int) Math.round(wScreen.x - rad), (int) Math.round(wScreen.y - rad), rad * 2, rad * 2);
            int rad2 = (int) Math.round(rad * 0.68);
            g.setColor(COLOR_PYTHON_ROAR_WAVE);
            g.setStroke(STROKE_PLAIN_2_0);
            g.drawOval((int) Math.round(wScreen.x - rad2), (int) Math.round(wScreen.y - rad2), rad2 * 2, rad2 * 2);
            g.setStroke(oldStroke);
        }
        g.setComposite(oldComp);
    }

    public void drawBoss(Graphics2D g, Camera camera, Boss boss) {
        Point2D.Double pos = camera.worldToScreen(boss.getX(), boss.getY());
        Composite oldComp = g.getComposite();

        if (boss.getType() == Boss.Type.BEAR) {
            initBearSprites();

            if (!bearWindupTelegraphDrawn && boss.getBearState() == Boss.BearState.WINDUP && boss.getStunTimer() <= 0.0) {
                drawBearTelegraph(g, camera, boss);
            }

            boolean enraged = boss.isBearEnraged();
            int dirIdx = angleToBearDirIndex(boss.getAngle());

            // 1. Hiệu ứng bụi dưới chân lúc bắt đầu lao (4 khung x 0.07s)
            double dustT = boss.getBearDustFxTimer();
            if (dustT >= 0.0 && bearDustFrames != null) {
                int dustIdx = Math.max(0, Math.min(bearDustFrames.length - 1, (int) (dustT / BEAR_DUST_FRAME_TIME)));
                BufferedImage dustImg = bearDustFrames[dustIdx];
                if (dustImg != null) {
                    double dxOff = boss.getBearDustFxX() - boss.getX();
                    double dyOff = boss.getBearDustFxY() - boss.getY() + 10.0;
                    g.drawImage(
                            dustImg,
                            (int) Math.round(pos.x + dxOff - dustImg.getWidth() * 0.5),
                            (int) Math.round(pos.y + dyOff - dustImg.getHeight() * 0.5),
                            null
                    );
                }
            }

            // 2. Vệt gió phía sau gấu khi đang LUNGE (alpha 0.7)
            if (boss.getBearState() == Boss.BearState.LUNGE && boss.getStunTimer() <= 0.0 && bearStreakFrames != null) {
                BufferedImage streakImg = bearStreakFrames[dirIdx];
                if (streakImg != null) {
                    double sx = pos.x - Math.cos(boss.getAngle()) * 68.0;
                    double sy = pos.y - Math.sin(boss.getAngle()) * 68.0;
                    g.setComposite(COMPOSITE_BEAR_STREAK);
                    g.drawImage(
                            streakImg,
                            (int) Math.round(sx - BEAR_STREAK_BAKED_HALF),
                            (int) Math.round(sy - BEAR_STREAK_BAKED_HALF),
                            null
                    );
                    g.setComposite(oldComp);
                }
            }

            // 2.5. Hào quang hồi phục Mana 2.0 giây (chung toàn bộ Boss)
            if (boss.isRecovering() && boss.getStunTimer() <= 0.0) {
                drawBossRecoveryAura(g, pos.x, pos.y, boss, 46.0);
            }

            // 3. Chọn frame theo trạng thái (theo bear_anim.json)
            int row = 0;
            int col = 0;
            double drawScale = 1.0;

            if (boss.getStunTimer() > 0.0) {
                row = 0;
                col = 0;
            } else {
                switch (boss.getBearState()) {
                    case PATROL, CHASE -> {
                        row = 0;
                        col = boss.isBearMoving()
                                ? (((int) Math.floor(boss.getWalkAnimTimer() / BEAR_WALK_FRAME_TIME)) & 3)
                                : 0;
                    }
                    case WINDUP -> {
                        row = 1;
                        double p = boss.getBearStateProgress();
                        if (p < 0.25) {
                            col = 0;
                        } else if (p < 0.55) {
                            col = 1;
                        } else {
                            col = ((((int) Math.floor(boss.getBearStateTime() / BEAR_WINDUP_SHAKE_TIME)) & 1) == 0) ? 2 : 3;
                        }
                    }
                    case LUNGE -> {
                        row = 2;
                        col = (boss.getBearStateProgress() < 0.35) ? 0 : 1;
                    }
                    case BITE -> {
                        row = 2;
                        col = (boss.getBearStateProgress() < 0.50) ? 2 : 3;
                    }
                    case RETREAT -> {
                        row = 3;
                        col = ((int) Math.floor(boss.getBearStateTime() / BEAR_RETREAT_FRAME_TIME)) & 3;
                    }
                    case RECOVER -> {
                        row = 0;
                        col = 0;
                        drawScale = 1.0 + Math.sin(boss.getBearStateTime() * 14.0) * 0.035;
                    }
                }
            }

            BufferedImage[][][] sheetBank = enraged ? bearEnragedFrames : bearNormalFrames;
            BufferedImage bearSprite = (sheetBank != null) ? sheetBank[row][col][dirIdx] : null;
            if (bearSprite != null) {
                if (drawScale != 1.0) {
                    int w = (int) Math.round(BEAR_BAKED_SIZE * drawScale);
                    int h = (int) Math.round(BEAR_BAKED_SIZE * drawScale);
                    g.drawImage(
                            bearSprite,
                            (int) Math.round(pos.x - w * 0.5),
                            (int) Math.round(pos.y - h * 0.5),
                            w, h, null
                    );
                } else {
                    g.drawImage(
                            bearSprite,
                            (int) Math.round(pos.x - BEAR_BAKED_HALF),
                            (int) Math.round(pos.y - BEAR_BAKED_HALF),
                            null
                    );
                }
            }

            // 4. Hiệu ứng táp trước mõm gấu (5 khung x 0.05s)
            double biteT = boss.getBearBiteFxTimer();
            if (biteT >= 0.0 && bearBiteFrames != null) {
                int biteIdx = Math.max(0, Math.min(bearBiteFrames.length - 1, (int) (biteT / BEAR_BITE_FRAME_TIME)));
                int biteDirIdx = angleToBearDirIndex(boss.getBearBiteFxAngle());
                BufferedImage biteImg = bearBiteFrames[biteIdx][biteDirIdx];
                if (biteImg != null) {
                    double bxOff = boss.getBearBiteFxX() - boss.getX();
                    double byOff = boss.getBearBiteFxY() - boss.getY();
                    g.drawImage(
                            biteImg,
                            (int) Math.round(pos.x + bxOff - BEAR_BITE_BAKED_HALF),
                            (int) Math.round(pos.y + byOff - BEAR_BITE_BAKED_HALF),
                            null
                    );
                }
            }

            // 5. Hiệu ứng choáng váng (chấm tròn vẽ bằng fillOval, không dùng ký tự ★)
            if (boss.getStunTimer() > 0) {
                g.setColor(COLOR_BEAR_STUN_DOT);
                double stAngle = boss.getAnimTimer() * 6.0;
                for (int i = 0; i < 3; i++) {
                    double a = stAngle + i * (Math.PI * 2.0 / 3.0);
                    int sx = (int) Math.round(pos.x + Math.cos(a) * 26.0);
                    int sy = (int) Math.round(pos.y - 40.0 + Math.sin(a) * 7.0);
                    g.fillOval(sx - 4, sy - 4, 8, 8);
                }
            }

            // 6. Thanh máu trên đầu Boss
            drawBossHeadBar(g, pos.x, pos.y - 64.0, boss);

        } else if (boss.getType() == Boss.Type.OCTOPUS) {
            // BOSS BẠCH TUỘC CỰ ĐẠI (STAGE 2 - BOSS 1)
            drawOctopusBoss(g, camera, boss);

        } else if (boss.getType() == Boss.Type.GIANT_PYTHON) {
            // BOSS TRĂN KHỔNG LỒ (THÁI CỔ CỰ TRĂN)
            if (!snakeWindupTelegraphDrawn && boss.getSnakeState() == Boss.SnakeBossState.ROAR_WINDUP && boss.getStunTimer() <= 0.0) {
                drawSnakeBossTelegraph(g, camera, boss);
            }

            List<SnakeSegment> segments = boss.getSegments();
            boolean enraged = boss.getHealth() <= boss.getMaxHealth() * 0.5;
            Boss.SnakeBossState sState = boss.getSnakeState();

            int segCount = segments.size();
            int totalPts = 0;
            Stroke oldStroke = g.getStroke();
            if (segCount >= 2) {
                for (int i = 0; i < segCount; i++) {
                    SnakeSegment seg = segments.get(i);
                    Point2D.Double screen = camera.worldToScreen(seg.x, seg.y);
                    snakeCtrlX[i] = screen.x;
                    snakeCtrlY[i] = screen.y;
                }

                int stepsPerSeg = 4;
                int m = 0;
                for (int i = 0; i < segCount - 1; i++) {
                    double p0x = (i > 0) ? snakeCtrlX[i - 1] : snakeCtrlX[0];
                    double p0y = (i > 0) ? snakeCtrlY[i - 1] : snakeCtrlY[0];
                    double p1x = snakeCtrlX[i];
                    double p1y = snakeCtrlY[i];
                    double p2x = snakeCtrlX[i + 1];
                    double p2y = snakeCtrlY[i + 1];
                    double p3x = (i + 2 < segCount) ? snakeCtrlX[i + 2] : snakeCtrlX[segCount - 1];
                    double p3y = (i + 2 < segCount) ? snakeCtrlY[i + 2] : snakeCtrlY[segCount - 1];

                    for (int k = 0; k < stepsPerSeg; k++) {
                        double u = (double) k / stepsPerSeg;
                        double u2 = u * u;
                        double u3 = u2 * u;

                        double x = 0.5 * (2.0 * p1x + (-p0x + p2x) * u + (2.0 * p0x - 5.0 * p1x + 4.0 * p2x - p3x) * u2 + (-p0x + 3.0 * p1x - 3.0 * p2x + p3x) * u3);
                        double y = 0.5 * (2.0 * p1y + (-p0y + p2y) * u + (2.0 * p0y - 5.0 * p1y + 4.0 * p2y - p3y) * u2 + (-p0y + 3.0 * p1y - 3.0 * p2y + p3y) * u3);

                        if (m < MAX_SNAKE_SPLINE_PTS) {
                            snakeSplineX[m] = Math.round(x);
                            snakeSplineY[m] = Math.round(y);
                            m++;
                        }
                    }
                }
                if (m < MAX_SNAKE_SPLINE_PTS) {
                    snakeSplineX[m] = Math.round(snakeCtrlX[segCount - 1]);
                    snakeSplineY[m] = Math.round(snakeCtrlY[segCount - 1]);
                    m++;
                }
                totalPts = m;

                snakeBodyPath.reset();
                snakeHighlightPath.reset();
                snakeBodyPath.moveTo(snakeSplineX[0], snakeSplineY[0]);
                snakeHighlightPath.moveTo(snakeSplineX[0] - 2.0, snakeSplineY[0] - 2.0);
                for (int j = 1; j < totalPts; j++) {
                    snakeBodyPath.lineTo(snakeSplineX[j], snakeSplineY[j]);
                    snakeHighlightPath.lineTo(snakeSplineX[j] - 2.0, snakeSplineY[j] - 2.0);
                }

                // Dư ảnh lướt đâm khi đang COIL_DASH
                if (sState == Boss.SnakeBossState.COIL_DASH && boss.getStunTimer() <= 0.0) {
                    double gx1 = -boss.getLungeDirX() * 18.0;
                    double gy1 = -boss.getLungeDirY() * 18.0;
                    double gx2 = -boss.getLungeDirX() * 36.0;
                    double gy2 = -boss.getLungeDirY() * 36.0;
                    g.setStroke(STROKE_BOSS_BODY);
                    g.setColor(COLOR_PYTHON_DASH_GHOST);
                    g.setComposite(COMPOSITE_SNAKE_AFTERIMAGE_2);
                    g.translate(gx2, gy2);
                    g.draw(snakeBodyPath);
                    g.translate(-gx2 + gx1, -gy2 + gy1);
                    g.setComposite(COMPOSITE_SNAKE_AFTERIMAGE_1);
                    g.draw(snakeBodyPath);
                    g.translate(-gx1, -gy1);
                    g.setComposite(oldComp);
                }

                // 1. Thân Cự Trăn 3 lớp nét tròn đồng nhất (Độ rộng đều 28.8px, Đuôi bo tròn tự nhiên)
                g.setStroke(STROKE_BOSS_BORDER);
                g.setColor(enraged ? new Color(69, 10, 10) : SNAKE_BOSS_PYTHON_OUTLINE);
                g.draw(snakeBodyPath);

                g.setStroke(STROKE_BOSS_BODY);
                g.setColor(enraged ? new Color(127, 29, 29) : SNAKE_BOSS_PYTHON_BODY);
                g.draw(snakeBodyPath);

                g.setStroke(STROKE_BOSS_HIGHLIGHT);
                g.setColor(SNAKE_BOSS_PYTHON_HIGHLIGHT);
                g.draw(snakeHighlightPath);

                g.setStroke(oldStroke);

                // 2. Hoa văn vảy rồng hoàng kim lặp đều đặn kích thước đồng nhất suốt thân
                Color patternCol = enraged ? new Color(239, 68, 68) : SNAKE_BOSS_PYTHON_PATTERN;
                for (int j = 4; j < totalPts - 3; j += 4) {
                    int px = (int) snakeSplineX[j];
                    int py = (int) snakeSplineY[j];
                    g.setColor(patternCol);
                    g.fillRect(px - 3, py - 3, 6, 6);
                    g.setColor(Color.WHITE);
                    g.fillRect(px - 1, py - 1, 2, 2);
                }
            }

            drawSnakeBossSegmentAndSkillDetails(g, camera, boss, segCount, totalPts, false, oldComp, oldStroke);

            // 3. Đầu cự trăn
            double hWorldX = !segments.isEmpty() ? segments.get(0).x : boss.getX();
            double hWorldY = !segments.isEmpty() ? segments.get(0).y : boss.getY();
            double hAng = !segments.isEmpty() ? segments.get(0).angle : boss.getAngle();
            Point2D.Double hScreen = camera.worldToScreen(hWorldX, hWorldY);
            double breathY = (boss.isRecovering() && boss.getStunTimer() <= 0.0) ? Math.sin(boss.getAnimTimer() * 4.5) * 1.4 : 0.0;

            if (boss.isRecovering() && boss.getStunTimer() <= 0.0) {
                drawBossRecoveryAura(g, hScreen.x, hScreen.y + breathY, boss, 36.0);
            }

            AffineTransform oldXf = g.getTransform();
            g.translate(hScreen.x, hScreen.y + breathY);
            g.rotate(hAng);

            // Hào quang độc tố tím khi cuồng nộ hoặc gầm tụ chiêu
            if (enraged || sState == Boss.SnakeBossState.ROAR_WINDUP) {
                g.setColor(sState == Boss.SnakeBossState.ROAR_WINDUP ? COLOR_PYTHON_ROAR_GLOW
                        : new Color(168, 85, 247, 90 + (int) (Math.sin(boss.getAnimTimer() * 7.0) * 35)));
                g.fillOval(-30, -26, 60, 52);
            }

            // Đầu mãng xà khổng lồ
            g.setColor(new Color(59, 7, 100));
            g.fillOval(-24, -18, 48, 36);

            // Sóng âm gầm & họng tím rực khi ROAR_WINDUP
            if (sState == Boss.SnakeBossState.ROAR_WINDUP && boss.getStunTimer() <= 0.0) {
                g.setColor(COLOR_PYTHON_ROAR_WAVE);
                g.fillOval(12, -10, 14, 20);
                double waveProg = (boss.getAnimTimer() * 3.5) % 1.0;
                int wr1 = 18 + (int) Math.round(waveProg * 28.0);
                int wr2 = 10 + (int) Math.round(((waveProg + 0.5) % 1.0) * 28.0);
                g.setStroke(STROKE_PLAIN_2_0);
                g.drawArc(18 - wr1 / 2, -wr1 / 2, wr1, wr1, -60, 120);
                g.drawArc(22 - wr2 / 2, -wr2 / 2, wr2, wr2, -55, 110);
                g.setStroke(oldStroke);
            } else if (sState == Boss.SnakeBossState.VENOM_SPIT && boss.getStunTimer() <= 0.0) {
                double flare = Math.max(0.2, 1.0 - boss.getSnakeStateProgress());
                int fr = (int) Math.round(14.0 * flare + 6.0);
                g.setColor(COLOR_PYTHON_VENOM_FLARE);
                g.fillOval(22 - fr / 2, -fr / 2, fr, fr);
            }

            // Mắt rắn vàng hung bạo
            g.setColor((sState == Boss.SnakeBossState.ROAR_WINDUP || enraged) ? new Color(239, 68, 68) : new Color(234, 179, 8));
            g.fillOval(6, -12, 8, 6);
            g.fillOval(6, 6, 8, 6);
            g.setColor(Color.BLACK); // Con ngươi dọc
            g.fillRect(10, -11, 2, 4);
            g.fillRect(10, 7, 2, 4);

            // Răng nanh sắc nhọn
            g.setColor(Color.WHITE);
            g.fillRect(20, -7, 4, 3);
            g.fillRect(20, 4, 4, 3);

            // Lưỡi chẻ đỏ thụt thò
            if (Math.sin(boss.getAnimTimer() * 10.0) > 0.3) {
                g.setColor(new Color(239, 68, 68));
                g.drawLine(24, 0, 34, 0);
                g.drawLine(34, 0, 39, -4);
                g.drawLine(34, 0, 39, 4);
            }

            g.setTransform(oldXf);

            // Hiệu ứng choáng (3 chấm tròn quay đều, fix lỗi thiếu stun ở Giant Python)
            if (boss.getStunTimer() > 0.0) {
                drawBossStunDots(g, hScreen.x, hScreen.y - 26.0, boss.getAnimTimer(), 22.0, 6.0);
            }

            // Thanh máu & Mana trên đầu Boss
            drawBossHeadBar(g, hScreen.x, hScreen.y - 54.0, boss);

        } else if (boss.getType() == Boss.Type.SWAMP_KING) {
            // ==========================================
            // BOSS HẢI LONG CỰ THẦN ĐẠI DƯƠNG (SWAMP KING TITAN)
            // ==========================================
            if (boss.isRecovering() && boss.getStunTimer() <= 0.0) {
                drawBossRecoveryAura(g, pos.x, pos.y, boss, 58.0);
            }
            AffineTransform oldXf = g.getTransform();
            g.translate(pos.x, pos.y);
            g.rotate(boss.getAngle());

            boolean enraged = boss.getHealth() <= boss.getMaxHealth() * 0.45;

            // 1. Vòng nước xoáy đại dương & Hào quang cuồng nộ
            g.setColor(new Color(2, 132, 199, 110));
            g.fillOval(-65, -34, 130, 68);
            g.setColor(new Color(56, 189, 248, 80));
            g.drawOval(-63, -32, 126, 64);

            if (enraged) {
                g.setColor(new Color(239, 68, 68, 90 + (int)(Math.sin(boss.getAnimTimer() * 8.0) * 40)));
                g.fillOval(-70, -38, 140, 76);
            }

            // 2. Thân Hải Long Cự Thần bọc giáp vảy lam ngọc biển sâu
            g.setColor(new Color(7, 89, 133)); // Xanh biển thẫm viền ngoài
            g.fillRoundRect(-54, -24, 108, 48, 16, 16);
            g.setColor(new Color(2, 132, 199)); // Xanh dương rực rỡ lõi thân
            g.fillRoundRect(-52, -22, 104, 44, 14, 14);

            // 3. Hàng vây lưng & gai xương phát sáng lam ngọc (Dorsal aquatic fins)
            g.setColor(new Color(56, 189, 248));
            for (int i = -36; i <= 24; i += 12) {
                g.fillPolygon(new int[]{i - 4, i, i + 4}, new int[]{-18, -26, -18}, 3);
                g.fillPolygon(new int[]{i - 4, i, i + 4}, new int[]{18, 26, 18}, 3);
                g.fillRect(i - 2, -3, 4, 6);
            }

            // 4. Mõm Cự Thần Biển há rộng
            g.setColor(new Color(7, 89, 133));
            g.fillPolygon(new int[]{20, 58, 20}, new int[]{-16, -4, 0}, 3);
            g.fillPolygon(new int[]{20, 58, 20}, new int[]{16, 4, 0}, 3);
            g.setColor(new Color(14, 165, 233));
            g.fillPolygon(new int[]{22, 56, 22}, new int[]{-14, -4, 0}, 3);
            g.fillPolygon(new int[]{22, 56, 22}, new int[]{14, 4, 0}, 3);

            // Răng nanh sắc nhọn trắng ngọc
            g.setColor(Color.WHITE);
            for (int gx = 26; gx <= 52; gx += 6) {
                g.fillRect(gx, -12, 2, 4);
                g.fillRect(gx, 8, 2, 4);
            }

            // 5. Đôi mắt săn mồi đại dương
            g.setColor(new Color(15, 23, 42));
            g.fillOval(16, -15, 8, 7);
            g.fillOval(16, 8, 8, 7);
            g.setColor(enraged ? new Color(239, 68, 68) : new Color(56, 189, 248));
            g.fillOval(17, -14, 6, 5);
            g.fillOval(17, 9, 6, 5);
            g.setColor(Color.BLACK);
            g.fillRect(19, -13, 2, 3);
            g.fillRect(19, 10, 2, 3);

            // Nhuộm sắc xanh nước biển sâu cho Hải Long Cự Thần
            Composite oldLeviathanComp = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.14f));
            g.setColor(COLOR_UNDERWATER_TINT);
            g.fillRoundRect(-56, -26, 112, 52, 18, 18);
            g.setComposite(oldLeviathanComp);

            g.setTransform(oldXf);

            // Hiệu ứng choáng nếu có
            if (boss.getStunTimer() > 0) {
                drawBossStunDots(g, pos.x, pos.y - 28.0, boss.getAnimTimer(), 26.0, 6.5);
            }

            drawBossHeadBar(g, pos.x, pos.y - 54, boss);

        } else {
            // ==========================================
            // BOSS HƯ KHÔNG LONG VƯƠNG (VOID ASTRAL DRAGON - CẤP CAO NHẤT)
            // ==========================================
            if (!snakeWindupTelegraphDrawn && boss.getSnakeState() == Boss.SnakeBossState.ROAR_WINDUP && boss.getStunTimer() <= 0.0) {
                drawSnakeBossTelegraph(g, camera, boss);
            }

            List<SnakeSegment> segments = boss.getSegments();
            boolean enraged = boss.getHealth() <= boss.getMaxHealth() * 0.5;
            Boss.SnakeBossState sState = boss.getSnakeState();

            int segCount = segments.size();
            int totalPts = 0;
            Stroke oldStroke = g.getStroke();
            if (segCount >= 2) {
                for (int i = 0; i < segCount; i++) {
                    SnakeSegment seg = segments.get(i);
                    Point2D.Double screen = camera.worldToScreen(seg.x, seg.y);
                    snakeCtrlX[i] = screen.x;
                    snakeCtrlY[i] = screen.y;
                }

                int stepsPerSeg = 4;
                int m = 0;
                for (int i = 0; i < segCount - 1; i++) {
                    double p0x = (i > 0) ? snakeCtrlX[i - 1] : snakeCtrlX[0];
                    double p0y = (i > 0) ? snakeCtrlY[i - 1] : snakeCtrlY[0];
                    double p1x = snakeCtrlX[i];
                    double p1y = snakeCtrlY[i];
                    double p2x = snakeCtrlX[i + 1];
                    double p2y = snakeCtrlY[i + 1];
                    double p3x = (i + 2 < segCount) ? snakeCtrlX[i + 2] : snakeCtrlX[segCount - 1];
                    double p3y = (i + 2 < segCount) ? snakeCtrlY[i + 2] : snakeCtrlY[segCount - 1];

                    for (int k = 0; k < stepsPerSeg; k++) {
                        double u = (double) k / stepsPerSeg;
                        double u2 = u * u;
                        double u3 = u2 * u;

                        double x = 0.5 * (2.0 * p1x + (-p0x + p2x) * u + (2.0 * p0x - 5.0 * p1x + 4.0 * p2x - p3x) * u2 + (-p0x + 3.0 * p1x - 3.0 * p2x + p3x) * u3);
                        double y = 0.5 * (2.0 * p1y + (-p0y + p2y) * u + (2.0 * p0y - 5.0 * p1y + 4.0 * p2y - p3y) * u2 + (-p0y + 3.0 * p1y - 3.0 * p2y + p3y) * u3);

                        if (m < MAX_SNAKE_SPLINE_PTS) {
                            snakeSplineX[m] = Math.round(x);
                            snakeSplineY[m] = Math.round(y);
                            m++;
                        }
                    }
                }
                if (m < MAX_SNAKE_SPLINE_PTS) {
                    snakeSplineX[m] = Math.round(snakeCtrlX[segCount - 1]);
                    snakeSplineY[m] = Math.round(snakeCtrlY[segCount - 1]);
                    m++;
                }
                totalPts = m;

                snakeBodyPath.reset();
                snakeHighlightPath.reset();
                snakeBodyPath.moveTo(snakeSplineX[0], snakeSplineY[0]);
                snakeHighlightPath.moveTo(snakeSplineX[0] - 2.0, snakeSplineY[0] - 2.0);
                for (int j = 1; j < totalPts; j++) {
                    snakeBodyPath.lineTo(snakeSplineX[j], snakeSplineY[j]);
                    snakeHighlightPath.lineTo(snakeSplineX[j] - 2.0, snakeSplineY[j] - 2.0);
                }

                // Hào quang tím hư không bao quanh thân
                g.setColor(new Color(147, 51, 234, 45));
                for (int j = 0; j < totalPts - 1; j += 4) {
                    int rx = (int) snakeSplineX[j];
                    int ry = (int) snakeSplineY[j];
                    g.fillOval(rx - 18, ry - 18, 36, 36);
                }

                // Dư ảnh lướt đâm khi đang COIL_DASH
                if (sState == Boss.SnakeBossState.COIL_DASH && boss.getStunTimer() <= 0.0) {
                    double gx1 = -boss.getLungeDirX() * 18.0;
                    double gy1 = -boss.getLungeDirY() * 18.0;
                    double gx2 = -boss.getLungeDirX() * 36.0;
                    double gy2 = -boss.getLungeDirY() * 36.0;
                    g.setStroke(STROKE_BOSS_BODY);
                    g.setColor(COLOR_VOID_DASH_GHOST);
                    g.setComposite(COMPOSITE_SNAKE_AFTERIMAGE_2);
                    g.translate(gx2, gy2);
                    g.draw(snakeBodyPath);
                    g.translate(-gx2 + gx1, -gy2 + gy1);
                    g.setComposite(COMPOSITE_SNAKE_AFTERIMAGE_1);
                    g.draw(snakeBodyPath);
                    g.translate(-gx1, -gy1);
                    g.setComposite(oldComp);
                }

                // 1. Thân Rồng Hư Không 3 lớp nét tròn đồng nhất (Độ rộng đều 28.8px, Đuôi bo tròn tự nhiên)
                g.setStroke(STROKE_BOSS_BORDER);
                g.setColor(enraged ? new Color(69, 10, 10) : SNAKE_BOSS_VOID_OUTLINE);
                g.draw(snakeBodyPath);

                g.setStroke(STROKE_BOSS_BODY);
                g.setColor(enraged ? new Color(88, 28, 135) : SNAKE_BOSS_VOID_BODY);
                g.draw(snakeBodyPath);

                g.setStroke(STROKE_BOSS_HIGHLIGHT);
                g.setColor(SNAKE_BOSS_VOID_HIGHLIGHT);
                g.draw(snakeHighlightPath);

                g.setStroke(oldStroke);

                // 2. Vảy rồng lam tinh chói sáng lặp đều đặn kích thước đồng nhất suốt thân
                Color patternCol = enraged ? new Color(239, 68, 68) : SNAKE_BOSS_VOID_PATTERN;
                for (int j = 4; j < totalPts - 3; j += 4) {
                    int px = (int) snakeSplineX[j];
                    int py = (int) snakeSplineY[j];
                    g.setColor(patternCol);
                    g.fillRect(px - 3, py - 3, 6, 6);
                    g.setColor(Color.WHITE);
                    g.fillRect(px - 1, py - 1, 2, 2);
                }
            }

            drawSnakeBossSegmentAndSkillDetails(g, camera, boss, segCount, totalPts, true, oldComp, oldStroke);

            // 2. Đầu rồng hư không
            double hWorldX = !segments.isEmpty() ? segments.get(0).x : boss.getX();
            double hWorldY = !segments.isEmpty() ? segments.get(0).y : boss.getY();
            double hAng = !segments.isEmpty() ? segments.get(0).angle : boss.getAngle();
            Point2D.Double hScreen = camera.worldToScreen(hWorldX, hWorldY);
            double breathY = (boss.isRecovering() && boss.getStunTimer() <= 0.0) ? Math.sin(boss.getAnimTimer() * 4.5) * 1.4 : 0.0;

            if (boss.isRecovering() && boss.getStunTimer() <= 0.0) {
                drawBossRecoveryAura(g, hScreen.x, hScreen.y + breathY, boss, 40.0);
            }

            AffineTransform oldXf = g.getTransform();
            g.translate(hScreen.x, hScreen.y + breathY);
            g.rotate(hAng);

            // Hào quang Hư Không Tối Thượng
            g.setColor(new Color(168, 85, 247, 110 + (int) (Math.sin(boss.getAnimTimer() * 7.0) * 35)));
            g.fillOval(-36, -28, 72, 56);
            g.setColor(new Color(250, 204, 21, 90));
            g.drawOval(-38, -30, 76, 60);

            // Sừng rồng hư không kép uốn ngược
            g.setColor(new Color(250, 204, 21));
            g.fillPolygon(new int[]{-10, -32, -18}, new int[]{-14, -26, -8}, 3);
            g.fillPolygon(new int[]{-10, -32, -18}, new int[]{14, 26, 8}, 3);
            g.setColor(new Color(56, 189, 248));
            g.fillRect(-32, -27, 4, 4);
            g.fillRect(-32, 23, 4, 4);

            // Thân đầu rồng
            g.setColor(new Color(24, 9, 44));
            g.fillOval(-26, -20, 52, 40);
            g.setColor(new Color(59, 7, 100));
            g.fillOval(-24, -18, 48, 36);

            if (sState == Boss.SnakeBossState.ROAR_WINDUP && boss.getStunTimer() <= 0.0) {
                g.setColor(COLOR_PYTHON_ROAR_WAVE);
                g.fillOval(14, -11, 14, 22);
                double waveProg = (boss.getAnimTimer() * 3.5) % 1.0;
                int wr1 = 20 + (int) Math.round(waveProg * 30.0);
                g.setStroke(STROKE_PLAIN_2_0);
                g.drawArc(20 - wr1 / 2, -wr1 / 2, wr1, wr1, -60, 120);
                g.setStroke(oldStroke);
            } else if (sState == Boss.SnakeBossState.VENOM_SPIT && boss.getStunTimer() <= 0.0) {
                int fr = (int) Math.round(14.0 * Math.max(0.2, 1.0 - boss.getSnakeStateProgress()) + 6.0);
                g.setColor(COLOR_PYTHON_VENOM_FLARE);
                g.fillOval(24 - fr / 2, -fr / 2, fr, fr);
            }

            // Mắt rồng vũ trụ xanh neon rực lửa
            g.setColor(new Color(56, 189, 248));
            g.fillOval(8, -13, 10, 8);
            g.fillOval(8, 5, 10, 8);
            g.setColor(Color.WHITE);
            g.fillRect(12, -11, 3, 4);
            g.fillRect(12, 7, 3, 4);

            // Nanh rồng hư không
            g.setColor(new Color(241, 245, 249));
            g.fillRect(22, -8, 5, 4);
            g.fillRect(22, 4, 5, 4);

            // Lửa hư không bốc ra từ khóe miệng
            if (Math.sin(boss.getAnimTimer() * 12.0) > 0.1) {
                g.setColor(new Color(192, 132, 252));
                g.drawLine(26, 0, 38, 0);
                g.drawLine(38, 0, 45, -5);
                g.drawLine(38, 0, 45, 5);
            }

            g.setTransform(oldXf);

            // Hiệu ứng choáng
            if (boss.getStunTimer() > 0) {
                drawBossStunDots(g, hScreen.x, hScreen.y - 28.0, boss.getAnimTimer(), 24.0, 6.5);
            }

            drawBossHeadBar(g, hScreen.x, hScreen.y - 56, boss);
        }

        g.setComposite(oldComp);
    }

    private void drawOctopusBoss(Graphics2D g, Camera camera, Boss boss) {
        Point2D.Double pos = camera.worldToScreen(boss.getX(), boss.getY());
        if (boss.isRecovering() && boss.getStunTimer() <= 0.0) {
            drawBossRecoveryAura(g, pos.x, pos.y, boss, 56.0);
        }
        AffineTransform old = g.getTransform();
        g.translate(pos.x, pos.y);
        g.rotate(boss.getAngle());

        boolean enraged = boss.getHealth() <= boss.getMaxHealth() * 0.45;
        double anim = boss.getAnimTimer();

        // 1. Màn sương mực đen & Hào quang cuồng nộ lan toả
        if (enraged) {
            g.setColor(new Color(2, 6, 23, 160 + (int) (Math.sin(anim * 8.0) * 40)));
            g.fillOval(-64, -64, 128, 128);
            g.setColor(new Color(239, 68, 68, 100));
            g.drawOval(-60, -60, 120, 120);
        } else {
            g.setColor(new Color(15, 23, 42, 100));
            g.fillOval(-54, -54, 108, 108);
        }

        // 2. Vẽ 8 Xúc Tu Khổng Lồ (8 Undulating Tentacles with Suction Cups)
        for (int i = 0; i < 8; i++) {
            double tentacleBaseAngle = (i * Math.PI * 2.0 / 8.0);
            drawOctopusTentacle(g, tentacleBaseAngle, anim, enraged);
        }

        // 3. Đầu thân hình vòm của Bạch Tuộc (Bulbous Mantle)
        g.setColor(new Color(76, 5, 25)); // Đỏ thẫm viền ngoài #4c0519
        g.fillOval(-32, -30, 64, 60);

        // Lòng thân màu đỏ thau kraken (#9f1239 / #be123c)
        g.setColor(enraged ? new Color(190, 18, 60) : new Color(159, 18, 57));
        g.fillOval(-29, -27, 58, 54);

        // Đốm hoa văn xúc giác trên trán
        g.setColor(new Color(244, 63, 94, 180));
        g.fillOval(-14, -18, 10, 8);
        g.fillOval(6, -18, 10, 8);
        g.fillOval(-4, -10, 8, 6);

        // Ống phun nước / siphon thở bọt nước
        g.setColor(new Color(76, 5, 25));
        g.fillRoundRect(16, -6, 14, 12, 4, 4);

        // 4. ĐÔI MẮT BẠCH TUỘC THẦN BÍ (Cephalopod Horizontal Slit Eyes)
        int[] eyeY = { -14, 14 };
        for (int ey : eyeY) {
            g.setColor(new Color(15, 23, 42)); // Hốc mắt
            g.fillOval(6, ey - 6, 14, 12);

            // Tròng mắt vàng hổ phách (đỏ rực khi cuồng nộ)
            g.setColor(enraged ? new Color(239, 68, 68) : new Color(250, 204, 21));
            g.fillOval(8, ey - 5, 10, 10);

            // Con ngươi nằm ngang đặc trưng của loài bạch tuộc
            g.setColor(new Color(15, 23, 42));
            g.fillRect(8, ey - 1, 10, 3);

            // Điểm sáng phản chiếu
            g.setColor(Color.WHITE);
            g.fillRect(13, ey - 3, 2, 2);
        }

        g.setTransform(old);

        // Hiệu ứng choáng váng nếu dính bẫy
        if (boss.getStunTimer() > 0) {
            drawBossStunDots(g, pos.x, pos.y - 31.0, boss.getAnimTimer(), 26.0, 6.5);
        }

        // Thanh máu trên đầu Boss
        drawBossHeadBar(g, pos.x, pos.y - 56, boss);
    }

    private void drawOctopusTentacle(Graphics2D g, double baseAngle, double anim, boolean enraged) {
        AffineTransform old = g.getTransform();
        g.rotate(baseAngle);

        int segments = 5;
        int segLen = 14;
        int prevX = 20;
        int prevY = 0;

        for (int j = 1; j <= segments; j++) {
            double wave = Math.sin(anim * 4.0 + baseAngle * 2.0 + j * 0.8) * (enraged ? 9.0 : 6.0);
            int curX = 20 + j * segLen;
            int curY = (int) wave;

            int thick = Math.max(4, 12 - j * 2);

            // Xúc tu đỏ sẫm
            g.setColor(new Color(76, 5, 25));
            g.setStroke(getRoundStroke(thick + 2));
            g.drawLine(prevX, prevY, curX, curY);

            g.setColor(new Color(159, 18, 57));
            g.setStroke(getRoundStroke(thick));
            g.drawLine(prevX, prevY, curX, curY);

            // Các giác bám tròn màu hồng phấn (Suction cups)
            g.setColor(new Color(254, 205, 211));
            g.fillOval(curX - 2, curY + thick / 2 - 1, 4, 4);
            g.fillOval(curX - 2, curY - thick / 2 - 3, 4, 4);

            prevX = curX;
            prevY = curY;
        }

        // Đầu nhọn của xúc tu
        g.setColor(new Color(244, 63, 94));
        g.fillOval(prevX - 2, prevY - 2, 5, 5);

        g.setStroke(STROKE_PLAIN_1_0);
        g.setTransform(old);
    }

    private void drawBossHeadBar(Graphics2D g, double cx, double cy, Boss boss) {
        int barW = 104;
        int barH = 11;
        int mpH = 4;
        int bx = (int) Math.round(cx - barW * 0.5);
        int by = (int) Math.round(cy);

        // Nhãn trạng thái hồi phục 2.0s / tụ chiêu phía trên tên Boss
        if (boss.isRecovering() && boss.getStunTimer() <= 0.0) {
            int labelIdx = Math.max(0, Math.min(RECOVERY_LABEL_CACHE.length - 1,
                    (int) Math.round(boss.getRecoveryTimer() * 10.0)));
            String recLabel = RECOVERY_LABEL_CACHE[labelIdx];
            g.setFont(FONT_BAR_NUMBER);
            int rw = g.getFontMetrics().stringWidth(recLabel);
            drawOutlinedString(g, recLabel, (int) Math.round(cx - rw * 0.5), by - 18,
                    COLOR_BOSS_RECOVERY_TEXT, HUD_TEXT_OUTLINE);
        } else if ((boss.getType() == Boss.Type.GIANT_PYTHON || boss.getType() == Boss.Type.VOID_DRAGON)
                && boss.getSnakeState() == Boss.SnakeBossState.ROAR_WINDUP && boss.getStunTimer() <= 0.0) {
            g.setFont(FONT_BAR_NUMBER);
            String warn = "TU CHIEU!";
            int ww = g.getFontMetrics().stringWidth(warn);
            drawOutlinedString(g, warn, (int) Math.round(cx - ww * 0.5), by - 18,
                    COLOR_PYTHON_SKILL_BADGE, HUD_TEXT_OUTLINE);
        }

        // Tên Boss căn giữa
        g.setFont(FONT_BADGE);
        int nameW = g.getFontMetrics().stringWidth(boss.getName());
        int nx = (int) Math.round(cx - nameW * 0.5);
        Color nameCol = switch (boss.getType()) {
            case BEAR -> COLOR_BOSS_NAME_BEAR;
            case OCTOPUS -> COLOR_BOSS_NAME_OCTO;
            case GIANT_PYTHON -> COLOR_BOSS_NAME_PYTHON;
            case SWAMP_KING -> COLOR_BOSS_NAME_SWAMP;
            case VOID_DRAGON -> COLOR_BOSS_NAME_VOID;
        };
        drawOutlinedString(g, boss.getName(), nx, by - 5, nameCol, HUD_TEXT_OUTLINE);

        // Khung nền HP + Mana
        g.setColor(COLOR_BOSS_BAR_BG);
        g.fillRect(bx - 1, by - 1, barW + 2, barH + mpH + 4);

        // Thanh HP đỏ (clamp [0, 1] tránh tràn viền)
        double pct = boss.getMaxHealth() > 0 ? Math.max(0.0, Math.min(1.0, (double) boss.getHealth() / boss.getMaxHealth())) : 0.0;
        g.setColor(COLOR_BOSS_BAR_FILL);
        g.fillRect(bx, by, (int) Math.round(barW * pct), barH);

        // Thanh Mana xanh lam ngay bên dưới HP
        int my = by + barH + 2;
        g.setColor(HUD_BAR_BG);
        g.fillRect(bx, my, barW, mpH);
        g.setColor(COLOR_BOSS_MANA_FILL);
        g.fillRect(bx, my, (int) Math.round(barW * boss.getManaRatio()), mpH);

        g.setColor(COLOR_BOSS_BAR_BORDER);
        g.drawRect(bx - 1, by - 1, barW + 2, barH + mpH + 4);

        // Hiển thị số máu HP thực tế của Boss
        g.setFont(FONT_BAR_NUMBER);
        g.setColor(Color.WHITE);
        String hpStr = boss.getHealth() + " / " + boss.getMaxHealth();
        g.drawString(hpStr, bx + (barW - g.getFontMetrics().stringWidth(hpStr)) / 2, by + 9);
    }

    private void renderAirLayer(Graphics2D g, int viewW, int viewH, Camera camera,
                                Eagle eagle, List<Prey> preys, List<PoisonBolt> poisonBolts,
                                ParticleSystem particles, double dt, int stageId) {
        // 1. Mây trôi Parallax
        double pFactor = Config.Environment.PARALLAX_FACTOR;
        for (Cloud c : clouds) {
            c.x += Config.Environment.CLOUD_SPEED * dt;
            if (c.x > camera.getX() + 2500) c.x = camera.getX() - 2500;

            double sx = c.x - (camera.getX() * (1 - pFactor) - viewW / 2.0);
            double sy = c.y - (camera.getY() * (1 - pFactor) - viewH / 2.0);

            if (sx + c.width < 0 || sx - c.width > viewW || sy + c.height < 0 || sy - c.height > viewH) {
                continue;
            }

            g.setColor(c.color);
            g.fillOval((int) (sx - c.width / 2), (int) (sy - c.height / 2), (int) c.width, (int) c.height);
        }

        // 1.1. Bầu không khí Đại Dương Xanh Thẳm (Ocean Caustics & Air Bubbles) cho Màn 2
        if (stageId == 2) {
            Composite oldComp = g.getComposite();

            // Vệt nắng chiếu qua mặt biển sâu (Soft Tilted Sunbeams / God Rays - Map 2 Overhaul)
            initSunbeamTexture();
            if (cachedSunbeamTexture != null) {
                // 6 Tia sáng có độ rộng, độ sáng và khoảng cách không đều, nghiêng 20 độ, mép mờ dần, giảm dần theo độ sâu
                int[] raySpacings = { -160, 110, 370, 660, 960, 1280 };
                int[] rayWidths   = { 110, 175, 125, 205, 140, 190 };
                float[] rayAlphas = { 0.08f, 0.11f, 0.07f, 0.10f, 0.08f, 0.09f };

                for (int i = 0; i < raySpacings.length; i++) {
                    double sway = Math.sin(ambientTimer * 0.40 + i * 1.35) * 22.0;
                    double drift = (ambientTimer * 14.0 + i * 180.0) % (viewW + 550) - 250;
                    double rx = drift + sway;
                    int rw = rayWidths[i];

                    sunbeamTransform.setToTranslation(rx, 0);
                    sunbeamTransform.shear(SUNRAY_TILT_SHEAR, 0.0);
                    sunbeamTransform.scale((double) rw / 128.0, (double) viewH / 512.0);

                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, rayAlphas[i]));
                    g.drawImage(cachedSunbeamTexture, sunbeamTransform, null);
                }
            }

            // Bong bóng khí biển sâu bốc lên (Rising Ocean Bubbles)
            initOceanBubbles();
            g.setComposite(COMPOSITE_BUBBLE);
            for (int i = 0; i < 22; i++) {
                double bx = Math.abs((i * 137 + Math.sin(ambientTimer * 0.8 + i) * 30) % viewW);
                double by = Math.abs((viewH + 100 - (ambientTimer * 45.0 + i * 95) % (viewH + 150)));
                g.drawImage(cachedOceanBubbles[i % 5], (int) Math.round(bx - 1), (int) Math.round(by - 1), null);
            }

            // Bụi lân tinh sinh vật phù du biển (Ocean Plankton Luminescence)
            g.setComposite(COMPOSITE_PLANKTON);
            for (int i = 0; i < 15; i++) {
                double px = Math.abs((i * 211 + ambientTimer * 12.0) % viewW);
                double py = Math.abs((i * 157 + Math.cos(ambientTimer * 0.9 + i) * 40) % viewH);
                g.setColor(i % 2 == 0 ? COLOR_PLANKTON_1 : COLOR_PLANKTON_2);
                g.fillOval((int) px, (int) py, 3, 3);
            }

            g.setComposite(oldComp);
        }

        // 2. Tia Đạn Độc (Poison Bolts)
        if (poisonBolts != null) {
            for (PoisonBolt bolt : poisonBolts) {
                bolt.render(g, camera);
            }
        }

        // 3. Bướm bay lượn trên không
        for (Prey p : preys) {
            if (p.isAlive() && p.getType() == Prey.Type.BUTTERFLY && camera.isVisible(p.getX(), p.getY(), 32)) {
                Point2D.Double screen = camera.worldToScreen(p.getX(), p.getY());
                BufferedImage bImg = AssetManager.getImage("butterfly");
                if (bImg != null) {
                    AssetManager.drawRotated(g, bImg, screen.x, screen.y, 24, 24, p.getAngle());
                } else {
                    g.setColor(new Color(192, 132, 252));
                    g.fillOval((int) screen.x - 6, (int) screen.y - 6, 12, 12);
                }
            }
        }

        // 4. Đại bàng / Hải âu sải cánh trên không & Lao xuống tấn công (Swoop Dive Attack)
        if (eagle != null && eagle.isAlive()) {
            if (stageId == 2) {
                drawSeagull(g, camera, eagle, ambientTimer);
            } else {
                Point2D.Double screen = camera.worldToScreen(eagle.getX(), eagle.getY());
                BufferedImage eImg = AssetManager.getImage("eagle");

                // Kích thước co giãn linh hoạt theo độ cao (Altitude): Khi bổ nhào xuống mặt đất, kích thước tăng lên
                double altScale = Math.max(0.70, Math.min(1.35, 1.35 - (eagle.getAltitude() / 400.0) * 0.55));
                int ew = (int) (76 * altScale);
                int eh = (int) (58 * altScale);

                if (eImg != null) {
                    AssetManager.drawCentered(g, eImg, screen.x, screen.y, ew, eh);
                } else {
                    g.setColor(new Color(180, 83, 9));
                    g.fillOval((int) screen.x - ew / 2, (int) screen.y - eh / 2, ew, eh);
                }

                // Vệt khí động học khi lao xuống cực nhanh
                if (eagle.getState() == Eagle.State.SWOOP) {
                    g.setColor(new Color(254, 240, 138, 180));
                    g.drawLine((int) screen.x - 12, (int) screen.y - 35, (int) screen.x - 8, (int) screen.y - 8);
                    g.drawLine((int) screen.x + 12, (int) screen.y - 35, (int) screen.x + 8, (int) screen.y - 8);
                }
            }
        }

        // 5. Hạt hiệu ứng
        particles.render(g, camera);
    }

    private void drawSeagull(Graphics2D g, Camera camera, Eagle seagull, double animTime) {
        Point2D.Double screen = camera.worldToScreen(seagull.getX(), seagull.getY());
        AffineTransform old = g.getTransform();
        g.translate(screen.x, screen.y);

        // Kích thước co giãn theo độ cao (Altitude)
        double altScale = Math.max(0.70, Math.min(1.35, 1.35 - (seagull.getAltitude() / 400.0) * 0.55));
        g.scale(altScale, altScale);

        // Hướng bay
        double flyAngle = Math.atan2(seagull.getGroundY() - seagull.getY(), seagull.getGroundX() - seagull.getX());
        g.rotate(flyAngle);

        double wingFlap = Math.sin(animTime * 8.0) * 8.0;

        // Vệt khí động học khi bổ nhào vồ mồi
        if (seagull.getState() == Eagle.State.SWOOP) {
            g.setColor(COLOR_SEAGULL_SWOOP_LINE);
            g.drawLine(-25, -12, -45, -18);
            g.drawLine(-25, 12, -45, 18);
        }

        // 1. Đôi cánh trắng muốt viền xám tro vẫy nhịp nhàng
        // Cánh trái
        seagullWxL[0] = -4; seagullWxL[1] = -14; seagullWxL[2] = -30; seagullWxL[3] = -18; seagullWxL[4] = 4;
        seagullWyL[0] = -4; seagullWyL[1] = (int) (-22 + wingFlap); seagullWyL[2] = (int) (-34 + wingFlap * 1.3); seagullWyL[3] = (int) (-16 + wingFlap * 0.8); seagullWyL[4] = -4;
        g.setColor(COLOR_SEAGULL_WING_BACK); // Lưng cánh xám
        g.fillPolygon(seagullWxL, seagullWyL, 5);

        seagullWxLin[0] = -3; seagullWxLin[1] = -12; seagullWxLin[2] = -26; seagullWxLin[3] = -16; seagullWxLin[4] = 3;
        seagullWyLin[0] = -3; seagullWyLin[1] = (int) (-20 + wingFlap); seagullWyLin[2] = (int) (-30 + wingFlap * 1.3); seagullWyLin[3] = (int) (-14 + wingFlap * 0.8); seagullWyLin[4] = -3;
        g.setColor(COLOR_SEAGULL_WING_INNER); // Mặt trong cánh trắng
        g.fillPolygon(seagullWxLin, seagullWyLin, 5);

        // Chóp cánh đen đặc trưng loài hải âu
        g.setColor(COLOR_SEAGULL_WING_TIP);
        g.fillOval(-31, (int) (-35 + wingFlap * 1.3), 8, 8);
        g.setColor(Color.WHITE); // Chấm trắng đầu chóp
        g.fillRect(-29, (int) (-33 + wingFlap * 1.3), 2, 2);

        // Cánh phải
        seagullWxR[0] = -4; seagullWxR[1] = -14; seagullWxR[2] = -30; seagullWxR[3] = -18; seagullWxR[4] = 4;
        seagullWyR[0] = 4; seagullWyR[1] = (int) (22 - wingFlap); seagullWyR[2] = (int) (34 - wingFlap * 1.3); seagullWyR[3] = (int) (16 - wingFlap * 0.8); seagullWyR[4] = 4;
        g.setColor(COLOR_SEAGULL_WING_BACK);
        g.fillPolygon(seagullWxR, seagullWyR, 5);

        seagullWxRin[0] = -3; seagullWxRin[1] = -12; seagullWxRin[2] = -26; seagullWxRin[3] = -16; seagullWxRin[4] = 3;
        seagullWyRin[0] = 3; seagullWyRin[1] = (int) (20 - wingFlap); seagullWyRin[2] = (int) (30 - wingFlap * 1.3); seagullWyRin[3] = (int) (14 - wingFlap * 0.8); seagullWyRin[4] = 3;
        g.setColor(COLOR_SEAGULL_WING_INNER);
        g.fillPolygon(seagullWxRin, seagullWyRin, 5);

        g.setColor(COLOR_SEAGULL_WING_TIP);
        g.fillOval(-31, (int) (27 - wingFlap * 1.3), 8, 8);
        g.setColor(Color.WHITE);
        g.fillRect(-29, (int) (29 - wingFlap * 1.3), 2, 2);

        // 2. Đuôi chim hải âu xòe quạt
        seagullTailX[0] = -16; seagullTailX[1] = -28; seagullTailX[2] = -28;
        seagullTailY[0] = 0; seagullTailY[1] = -8; seagullTailY[2] = 8;
        g.setColor(COLOR_SEAGULL_TAIL);
        g.fillPolygon(seagullTailX, seagullTailY, 3);

        // 3. Thân chim thon gọn trắng tinh
        g.setColor(COLOR_SEAGULL_BODY_BORDER); // Viền thân
        g.fillOval(-18, -8, 38, 16);
        g.setColor(Color.WHITE); // Thân trắng muốt
        g.fillOval(-17, -7, 36, 14);

        // 4. Mỏ chim hải âu màu vàng với chấm đỏ đặc trưng (Red spot on lower bill)
        seagullBeakX[0] = 18; seagullBeakX[1] = 30; seagullBeakX[2] = 18;
        seagullBeakY[0] = -3; seagullBeakY[1] = 0; seagullBeakY[2] = 3;
        g.setColor(COLOR_SEAGULL_BEAK); // Mỏ vàng tươi
        g.fillPolygon(seagullBeakX, seagullBeakY, 3);

        // Chấm đỏ nhận diện nổi tiếng của chim hải âu (Gonys spot)
        g.setColor(COLOR_SEAGULL_RED_SPOT);
        g.fillRect(24, 0, 3, 2);

        // 5. Đôi mắt đen láy
        g.setColor(COLOR_SEAGULL_EYE);
        g.fillOval(14, -5, 3, 3);
        g.fillOval(14, 2, 3, 3);

        g.setTransform(old);
    }

    /**
     * LỚP 6A: TÔNG MÀU THEO THỜI GIAN
     * Áp lớp màu bán trong suốt: ấm vàng cam lúc chiều tà, xanh thẫm lúc ban đêm.
     * Mặc định ở trạng thái BAN NGÀY (DAY, alpha = 0).
     */
    private void renderTimeOfDayTint(Graphics2D g, int viewW, int viewH, TimeOfDay timeOfDay) {
        Color tint = switch (timeOfDay) {
            case DAY -> new Color(254, 240, 138, 0);       // Ban ngày: trong trẻo hoàn toàn (alpha 0)
            case AFTERNOON -> new Color(245, 158, 11, 40);  // Chiều tà: ấm áp vàng cam (#f59e0b)
            case NIGHT -> new Color(15, 23, 62, 85);        // Ban đêm: xanh tím thẫm lạnh (#0f173e)
        };
        if (tint.getAlpha() > 0) {
            g.setColor(tint);
            g.fillRect(0, 0, viewW, viewH);
        }
    }

    /**
     * LỚP 6B: ÁNH SÁNG QUANH RẮN
     * Tạo lớp tối nhẹ môi trường (hằng số AMBIENT_DARKNESS_ALPHA),
     * khoét vùng sáng mềm quanh đầu rắn bằng mặt nạ pre-bake tương thích VRAM kết hợp 4 fillRect biên.
     */
    private void renderAmbientLighting(Graphics2D g, int viewW, int viewH, Camera camera, PlayerSnake player) {
        if (currentTimeOfDay == TimeOfDay.DAY || AMBIENT_DARKNESS_ALPHA <= 0) return;
        initLightMask();

        g.setColor(COLOR_AMBIENT_DARKNESS);
        if (player != null && player.isAlive() && cachedLightMask != null) {
            Point2D.Double screenPos = camera.worldToScreen(player.getX(), player.getY());
            int cx = (int) Math.round(screenPos.x);
            int cy = (int) Math.round(screenPos.y);
            int r = (int) LIGHT_RADIUS;
            int lx = cx - r;
            int ly = cy - r;
            int lw = r * 2;
            int lh = r * 2;

            // 1. Phía trên
            if (ly > 0) {
                g.fillRect(0, 0, viewW, Math.min(ly, viewH));
            }
            // 2. Phía dưới
            if (ly + lh < viewH) {
                int bottomY = Math.max(0, ly + lh);
                g.fillRect(0, bottomY, viewW, viewH - bottomY);
            }
            // 3. Phía trái
            int midY = Math.max(0, ly);
            int midH = Math.min(viewH, ly + lh) - midY;
            if (midH > 0 && lx > 0) {
                g.fillRect(0, midY, Math.min(lx, viewW), midH);
            }
            // 4. Phía phải
            if (midH > 0 && lx + lw < viewW) {
                int rightX = Math.max(0, lx + lw);
                g.fillRect(rightX, midY, viewW - rightX, midH);
            }

            // Vẽ mặt nạ ánh sáng mềm quanh đầu rắn
            g.drawImage(cachedLightMask, lx, ly, null);
        } else {
            g.fillRect(0, 0, viewW, viewH);
        }
    }

    /**
     * LỚP 6C: POST-FX VIGNETTE
     * BufferedImage chứa RadialGradientPaint tối dần ra viền (alpha tối đa VIGNETTE_MAX_ALPHA ~90),
     * được tạo 1 lần duy nhất, cache trên VRAM và vẽ lên cuối mỗi frame.
     */
    private void renderVignette(Graphics2D g, int viewW, int viewH) {
        if (cachedVignette == null || cachedVignetteW != viewW || cachedVignetteH != viewH) {
            BufferedImage raw = new BufferedImage(viewW, viewH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D vg = raw.createGraphics();

            Point2D center = new Point2D.Float(viewW / 2.0f, viewH / 2.0f);
            float radius = (float) Math.hypot(viewW / 2.0, viewH / 2.0);
            float[] dist = {0.0f, 0.45f, 0.75f, 1.0f};
            Color[] colors = {
                new Color(0, 0, 0, 0),
                new Color(0, 0, 0, 15),
                new Color(0, 0, 0, 50),
                new Color(0, 0, 0, VIGNETTE_MAX_ALPHA)
            };
            RadialGradientPaint rgp = new RadialGradientPaint(center, radius, dist, colors);
            vg.setPaint(rgp);
            vg.fillRect(0, 0, viewW, viewH);
            vg.dispose();

            cachedVignette = createCompatible(viewW, viewH, Transparency.TRANSLUCENT);
            Graphics2D cg = cachedVignette.createGraphics();
            cg.setComposite(AlphaComposite.Src);
            cg.drawImage(raw, 0, 0, null);
            cg.dispose();

            cachedVignetteW = viewW;
            cachedVignetteH = viewH;
        }

        g.drawImage(cachedVignette, 0, 0, null);
    }

    private void renderPostFX(Graphics2D g, int viewW, int viewH) {
        renderVignette(g, viewW, viewH);
    }

    public static void setTimeOfDay(TimeOfDay tod) {
        currentTimeOfDay = tod;
    }

    public static TimeOfDay getTimeOfDay() {
        return currentTimeOfDay;
    }

    private void renderMinimap(Graphics2D g, int viewW, int viewH,
                               PlayerSnake player, List<AISnake> aiSnakes, List<Boss> bosses, List<Prey> preys) {
        int size = 110;
        int x = viewW - size - 16;
        int y = 58;
        double range = 900.0;
        double scale = (size / 2.0) / range;

        // Nền radar
        g.setColor(new Color(15, 23, 20, 215));
        g.fillRoundRect(x, y, size, size, 12, 12);
        g.setColor(new Color(34, 197, 94, 140));
        g.drawRoundRect(x, y, size, size, 12, 12);

        int cx = x + size / 2;
        int cy = y + size / 2;

        // Vòng tròn radar
        g.setColor(new Color(74, 222, 128, 45));
        g.drawOval(cx - size * 2 / 5, cy - size * 2 / 5, size * 4 / 5, size * 4 / 5);

        // Chấm con mồi (Xanh lá)
        g.setColor(new Color(134, 239, 172));
        for (Prey p : preys) {
            if (!p.isAlive()) continue;
            int rx = (int) (cx + (p.getX() - player.getX()) * scale);
            int ry = (int) (cy + (p.getY() - player.getY()) * scale);
            if (rx >= x + 4 && rx <= x + size - 4 && ry >= y + 4 && ry <= y + size - 4) {
                g.fillRect(rx - 1, ry - 1, 3, 3);
            }
        }

        // Chấm rắn AI (Đỏ)
        g.setColor(new Color(239, 68, 68));
        for (AISnake ai : aiSnakes) {
            if (!ai.isAlive()) continue;
            int rx = (int) (cx + (ai.getX() - player.getX()) * scale);
            int ry = (int) (cy + (ai.getY() - player.getY()) * scale);
            if (rx >= x + 4 && rx <= x + size - 4 && ry >= y + 4 && ry <= y + size - 4) {
                g.fillRect(rx - 2, ry - 2, 4, 4);
            }
        }

        // ĐÁNH DẤU BOSS TRÊN BẢN ĐỒ BẰNG HÌNH VƯƠNG MIỆN 👑
        for (Boss boss : bosses) {
            if (!boss.isAlive()) continue;
            double dx = boss.getX() - player.getX();
            double dy = boss.getY() - player.getY();
            int rx = (int) (cx + dx * scale);
            int ry = (int) (cy + dy * scale);

            int minBound = x + 10;
            int maxBound = x + size - 10;
            boolean insideRadar = (rx >= minBound && rx <= maxBound && ry >= minBound && ry <= maxBound);

            if (insideRadar) {
                // Boss nằm trong phạm vi radar: Vẽ Vương Miện Vàng 👑 với hào quang nhấp nháy
                drawCrownIcon(g, rx, ry, 12, true);
            } else {
                // Boss nằm ngoài phạm vi radar: Ghim vào viền Radar kèm mũi tên chỉ hướng chính xác!
                double angle = Math.atan2(dy, dx);
                double edgeR = (size / 2.0) - 8.0;
                int edgeX = (int) (cx + Math.cos(angle) * edgeR);
                int edgeY = (int) (cy + Math.sin(angle) * edgeR);

                drawCrownIcon(g, edgeX, edgeY, 10, false);

                // Mũi tên chỉ hướng ra viền
                g.setColor(new Color(250, 204, 21));
                g.drawLine(edgeX, edgeY, (int) (edgeX + Math.cos(angle) * 5), (int) (edgeY + Math.sin(angle) * 5));
            }
        }

        // Tâm người chơi (Vàng)
        g.setColor(new Color(250, 204, 21));
        g.fillOval(cx - 3, cy - 3, 6, 6);
        g.drawLine(cx, cy, (int) (cx + Math.cos(player.getAngle()) * 8), (int) (cy + Math.sin(player.getAngle()) * 8));
    }

    private void drawCrownIcon(Graphics2D g, int cx, int cy, int size, boolean drawPulsingRing) {
        if (drawPulsingRing) {
            long t = System.currentTimeMillis() % 1200;
            int pulseR = size + (int)(t / 1200.0 * 8);
            int alpha = Math.max(0, 160 - (int)(t / 1200.0 * 160));
            g.setColor(new Color(250, 204, 21, alpha));
            g.drawOval(cx - pulseR, cy - pulseR, pulseR * 2, pulseR * 2);
        }

        int half = size / 2;
        // Chân đế vương miện hoàng kim
        g.setColor(new Color(113, 63, 18));
        g.fillRect(cx - half - 1, cy + half / 4 - 1, size + 2, half / 2 + 2);
        g.setColor(new Color(250, 204, 21));
        g.fillRect(cx - half, cy + half / 4, size, half / 2);

        // 3 đỉnh tam giác vương miện
        int[] px = {
            cx - half, cx - half, cx - half / 2, cx, cx + half / 2, cx + half, cx + half
        };
        int[] py = {
            cy + half / 4, cy - half / 2, cy, cy - half - 2, cy, cy - half / 2, cy + half / 4
        };
        g.setColor(new Color(113, 63, 18));
        g.fillPolygon(px, py, 7);

        int[] pxIn = {
            cx - half + 1, cx - half + 1, cx - half / 2, cx, cx + half / 2, cx + half - 1, cx + half - 1
        };
        int[] pyIn = {
            cy + half / 4, cy - half / 2 + 2, cy + 1, cy - half, cy + 1, cy - half / 2 + 2, cy + half / 4
        };
        g.setColor(new Color(253, 224, 71));
        g.fillPolygon(pxIn, pyIn, 7);

        // 3 viên ngọc đỏ trên 3 đỉnh vương miện
        g.setColor(new Color(239, 68, 68));
        g.fillOval(cx - half - 1, cy - half / 2 - 2, 3, 3);
        g.fillOval(cx - 1, cy - half - 4, 3, 3);
        g.fillOval(cx + half - 2, cy - half / 2 - 2, 3, 3);
    }

    private void renderOffscreenBossIndicator(Graphics2D g, int viewW, int viewH, Camera camera, PlayerSnake player, List<Boss> bosses) {
        for (Boss boss : bosses) {
            if (!boss.isAlive()) continue;
            if (camera.isVisible(boss.getX(), boss.getY(), 120)) continue; // Nếu boss đang thấy trên màn hình thì không cần badge

            double dx = boss.getX() - player.getX();
            double dy = boss.getY() - player.getY();
            double dist = Math.hypot(dx, dy);

            // Mũi tên chỉ hướng theo la bàn 8 hướng
            String dirArrow = "➔";
            double angleDeg = Math.toDegrees(Math.atan2(dy, dx));
            if (angleDeg >= -22.5 && angleDeg < 22.5) dirArrow = "➔ (Đông)";
            else if (angleDeg >= 22.5 && angleDeg < 67.5) dirArrow = "➘ (Đông Nam)";
            else if (angleDeg >= 67.5 && angleDeg < 112.5) dirArrow = "⬇ (Nam)";
            else if (angleDeg >= 112.5 && angleDeg < 157.5) dirArrow = "↙ (Tây Nam)";
            else if (angleDeg >= -67.5 && angleDeg < -22.5) dirArrow = "➚ (Đông Bắc)";
            else if (angleDeg >= -112.5 && angleDeg < -67.5) dirArrow = "⬆ (Bắc)";
            else if (angleDeg >= -157.5 && angleDeg < -112.5) dirArrow = "↖ (Tây Bắc)";
            else dirArrow = "⬅ (Tây)";

            // Badge nổi bật ở góc phải màn hình
            int badgeW = 230;
            int badgeH = 26;
            int bx = viewW - badgeW - 130;
            int by = 62;

            g.setColor(new Color(15, 23, 42, 220));
            g.fillRoundRect(bx, by, badgeW, badgeH, 8, 8);
            g.setColor(new Color(250, 204, 21, 200));
            g.drawRoundRect(bx, by, badgeW, badgeH, 8, 8);

            drawCrownIcon(g, bx + 14, by + 13, 10, true);

            String txt = String.format("%s [%.0fm] %s", boss.getName(), dist, dirArrow);
            drawOutlinedPixelString(g, txt, bx + 26, by + 17, Color.WHITE, HUD_TEXT_OUTLINE, FONT_PIXEL_LABEL, FONT_BADGE);
            break;
        }
    }

    private void renderHUD(Graphics2D g, int viewW, int viewH, PlayerSnake player, List<Boss> bosses, int preyCount) {
        // Tắt khử răng cưa cho chữ theo yêu cầu để giữ nét pixel sắc cạnh
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

        // Cập nhật delta time cho các hiệu ứng HUD mượt mà
        long nowNs = System.nanoTime();
        double dt = (hudLastTimeNs > 0) ? Math.min(0.1, (nowNs - hudLastTimeNs) / 1e9) : 0.016;
        hudLastTimeNs = nowNs;

        // Thanh HUD trên cùng bo cong và nền tối trong suốt dịu mắt
        g.setColor(new Color(15, 23, 42, (int) (HUD_BG_ALPHA * 255)));
        g.fillRect(0, 0, viewW, 58);
        g.setColor(new Color(51, 65, 85, 200));
        g.drawLine(0, 58, viewW, 58);

        // ==========================================
        // DÒNG 1: SCORE, HP, MP, STM, LEVEL, EXP
        // ==========================================
        // 1. SCORE
        drawOutlinedPixelString(g, "★ " + player.getScore(), 14, 20, new Color(250, 204, 21), HUD_TEXT_OUTLINE, FONT_PIXEL_TITLE, FONT_HUD_BOLD);

        // 2. Điểm Máu (Numerical HP Bar) với hiệu ứng "tụt máu" (Lag Bar)
        int curHp = player.getHealth();
        int maxHp = player.getMaxHealth();
        if (hudLagHp < 0) {
            hudLagHp = curHp;
        } else if (curHp < hudLagHp) {
            // Thanh tụt máu sáng rút chậm phía sau thanh chính khi mất HP
            hudLagHp -= Math.max(30.0, (hudLagHp - curHp) * 3.5) * dt;
            if (hudLagHp < curHp) hudLagHp = curHp;
        } else if (curHp > hudLagHp) {
            hudLagHp = curHp;
        }

        double hpPct = Math.max(0.0, (double) curHp / maxHp);
        double lagPct = Math.max(0.0, Math.min(1.0, hudLagHp / maxHp));

        drawOutlinedPixelString(g, "HP:", 92, 20, Color.WHITE, HUD_TEXT_OUTLINE, FONT_PIXEL_HUD, FONT_HUD_BOLD);

        int hpBarW = 90;
        int hpBarH = 12;
        int hpBarX = 118;
        int hpBarY = 8;

        // Nền tối trong suốt, khung bo góc 4px
        g.setColor(HUD_BAR_BG);
        g.fillRoundRect(hpBarX, hpBarY, hpBarW, hpBarH, 4, 4);

        // Thanh tụt máu sáng chậm (Lag Bar)
        if (lagPct > hpPct) {
            g.setColor(HUD_HP_LAG_COLOR);
            g.fillRoundRect(hpBarX, hpBarY, (int) Math.round(hpBarW * lagPct), hpBarH, 4, 4);
        }

        // Thanh HP chính màu đầy dịu mắt
        Color hpColor = (hpPct > 0.5) ? HUD_HP_FILL_HIGH : ((hpPct > 0.25) ? HUD_HP_FILL_MID : HUD_HP_FILL_LOW);
        g.setColor(hpColor);
        g.fillRoundRect(hpBarX, hpBarY, (int) Math.round(hpBarW * hpPct), hpBarH, 4, 4);

        // Viền khung bo góc
        g.setColor(HUD_BAR_BORDER);
        g.drawRoundRect(hpBarX, hpBarY, hpBarW, hpBarH, 4, 4);

        // Số HP có viền 1px
        String hpText = curHp + " / " + maxHp;
        Font numFont = (FONT_PIXEL_NUMBER.canDisplayUpTo(hpText) == -1) ? FONT_PIXEL_NUMBER : FONT_BAR_NUMBER;
        g.setFont(numFont);
        int hpTextW = g.getFontMetrics().stringWidth(hpText);
        drawOutlinedString(g, hpText, hpBarX + (hpBarW - hpTextW) / 2, hpBarY + 9, Color.WHITE, HUD_TEXT_OUTLINE);

        // 3. THANH MANA (Cyan/Blue MP Bar)
        double curMp = player.getMana();
        double maxMp = player.getMaxMana();
        double mpPct = Math.max(0.0, Math.min(1.0, curMp / maxMp));

        drawOutlinedPixelString(g, "MP:", 218, 20, HUD_MP_FILL, HUD_TEXT_OUTLINE, FONT_PIXEL_HUD, FONT_HUD_BOLD);

        int mpBarW = 90;
        int mpBarH = 12;
        int mpBarX = 244;
        int mpBarY = 8;

        g.setColor(HUD_BAR_BG);
        g.fillRoundRect(mpBarX, mpBarY, mpBarW, mpBarH, 4, 4);

        g.setColor(HUD_MP_FILL);
        g.fillRoundRect(mpBarX, mpBarY, (int) Math.round(mpBarW * mpPct), mpBarH, 4, 4);

        g.setColor(new Color(14, 116, 144, 200));
        g.drawRoundRect(mpBarX, mpBarY, mpBarW, mpBarH, 4, 4);

        String mpText = (int) curMp + " / " + (int) maxMp;
        Font mpFont = (FONT_PIXEL_NUMBER.canDisplayUpTo(mpText) == -1) ? FONT_PIXEL_NUMBER : FONT_BAR_NUMBER;
        g.setFont(mpFont);
        int mpTextW = g.getFontMetrics().stringWidth(mpText);
        drawOutlinedString(g, mpText, mpBarX + (mpBarW - mpTextW) / 2, mpBarY + 9, Color.WHITE, HUD_TEXT_OUTLINE);

        // 4. Thể Lực STAMINA
        double staminaProgress = Math.max(0.0, Math.min(1.0, player.getStamina() / player.getMaxStamina()));
        drawOutlinedPixelString(g, "STM:", 345, 20, HUD_STM_FILL, HUD_TEXT_OUTLINE, FONT_PIXEL_HUD, FONT_HUD_BOLD);

        int stmBarW = 80;
        int stmBarH = 12;
        int stmBarX = 380;
        int stmBarY = 8;

        g.setColor(HUD_BAR_BG);
        g.fillRoundRect(stmBarX, stmBarY, stmBarW, stmBarH, 4, 4);

        Color staCol = (player.getStamina() > 20) ? (player.isSprinting() ? new Color(56, 189, 248) : HUD_STM_FILL) : HUD_HP_FILL_LOW;
        g.setColor(staCol);
        g.fillRoundRect(stmBarX, stmBarY, (int) Math.round(stmBarW * staminaProgress), stmBarH, 4, 4);

        g.setColor(HUD_BAR_BORDER);
        g.drawRoundRect(stmBarX, stmBarY, stmBarW, stmBarH, 4, 4);

        // 5. Cấp Rắn & Thanh Tiến Hóa EXP
        int level = player.getEvolutionLevel();
        String title = switch (level) {
            case 1 -> "GARTER SNAKE (Lv.1)";
            case 2 -> "SHADOW VIPER (Lv.2)";
            case 3 -> "APEX TITAN BOA (Lv.3)";
            case 4 -> "CRIMSON FIRE DRAKE (Lv.4)";
            case 5 -> "CELESTIAL LEVIATHAN (Lv.5 MAX)";
            default -> "SNAKE (Lv." + level + ")";
        };
        Color titleCol = switch (level) {
            case 5 -> new Color(250, 204, 21);
            case 4 -> new Color(239, 68, 68);
            case 3 -> new Color(234, 179, 8);
            case 2 -> new Color(192, 132, 252);
            default -> new Color(74, 222, 128);
        };
        drawOutlinedPixelString(g, title, 472, 20, titleCol, HUD_TEXT_OUTLINE, FONT_PIXEL_HUD, FONT_HUD_BOLD);

        int targetEnergy = switch (level) {
            case 1 -> Config.SnakeConfig.EXP_TO_LV2;
            case 2 -> Config.SnakeConfig.EXP_TO_LV3;
            case 3 -> Config.SnakeConfig.EXP_TO_LV4;
            case 4 -> Config.SnakeConfig.EXP_TO_LV5;
            default -> Config.SnakeConfig.EXP_TO_LV5;
        };

        // Cập nhật hiệu ứng bừng sáng EXP khi ăn mồi nhận kinh nghiệm
        int curExp = player.getEnergy();
        if (hudLastExp < 0) {
            hudLastExp = curExp;
        } else if (curExp > hudLastExp) {
            hudExpGlowTimer = 0.6; // Bừng sáng trong 0.6s
            hudLastExp = curExp;
        } else if (curExp < hudLastExp) {
            hudLastExp = curExp;
        }
        if (hudExpGlowTimer > 0) {
            hudExpGlowTimer -= dt;
        }

        double progress = Math.min(1.0, (double) curExp / targetEnergy);
        boolean nearLevelUp = (level < 5 && progress >= 0.85);

        int expBarX = 695;
        int expBarY = 8;
        int expBarW = 92;
        int expBarH = 12;

        g.setColor(HUD_BAR_BG);
        g.fillRoundRect(expBarX, expBarY, expBarW, expBarH, 4, 4);

        // Màu thanh EXP: tím dịu, sáng bừng khi ăn mồi hoặc nhấp nháy khi sắp lên cấp
        Color expFill;
        if (hudExpGlowTimer > 0) {
            expFill = HUD_EXP_GLOW; // Sáng bừng trắng tím
        } else if (nearLevelUp) {
            double pulse = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() * 0.009);
            int r = (int) (168 + (250 - 168) * pulse);
            int gr = (int) (85 + (204 - 85) * pulse);
            int b = (int) (247 + (21 - 247) * pulse);
            expFill = new Color(r, gr, b);
        } else {
            expFill = HUD_EXP_FILL;
        }

        g.setColor(expFill);
        g.fillRoundRect(expBarX, expBarY, (int) Math.round(expBarW * progress), expBarH, 4, 4);

        Color expBorder = nearLevelUp ? new Color(250, 204, 21, 220) : HUD_BAR_BORDER;
        g.setColor(expBorder);
        g.drawRoundRect(expBarX, expBarY, expBarW, expBarH, 4, 4);

        String expStr = (level >= 5) ? "EXP: MAX" : "EXP: " + curExp + "/" + targetEnergy;
        Font expFont = (FONT_PIXEL_NUMBER.canDisplayUpTo(expStr) == -1) ? FONT_PIXEL_NUMBER : FONT_BAR_NUMBER;
        g.setFont(expFont);
        int expTextW = g.getFontMetrics().stringWidth(expStr);
        drawOutlinedString(g, expStr, expBarX + (expBarW - expTextW) / 2, expBarY + 9, Color.WHITE, HUD_TEXT_OUTLINE);

        // ==========================================
        // DÒNG 2: BẢNG KỸ NĂNG CÓ ICON PIXEL, PHÍM VÀ HỒI CHIÊU
        // ==========================================
        // Chiêu 1: Phun độc [Q / Chuột Phải] (25 MP)
        boolean canSpit = curMp >= Config.SnakeConfig.VENOM_SPIT_MANA;
        drawSkillSlot(g, 14, 30, 22, "Q", "Phun Độc", "(25MP)", true, 1, canSpit,
                player.getVenomCooldownTimer(), 0.45, 0);

        // Chiêu 2: Rung đuôi [E / F] (35 MP, Yêu cầu Cấp 2)
        boolean canRattle = curMp >= Config.SnakeConfig.RATTLE_TAIL_MANA && player.getRattleCooldownTimer() <= 0;
        drawSkillSlot(g, 185, 30, 22, "E", "Rung Đuôi", "(35MP)", level >= 2, 2, canRattle,
                player.getRattleCooldownTimer(), Config.SnakeConfig.RATTLE_TAIL_COOLDOWN, 1);

        // Chiêu 3: Lướt Dash [SPACE] (Cấp 2+)
        boolean canDash = player.getDashCooldownTimer() <= 0;
        drawSkillSlot(g, 355, 30, 22, "SPC", "Lướt Dash", "", level >= 2, 2, canDash,
                player.getDashCooldownTimer(), Config.SnakeConfig.DASH_COOLDOWN, 2);

        // Chiêu 4: Nội Tại Da Dày [PASSIVE] (-20% DMG, Cấp 3+)
        drawSkillSlot(g, 510, 30, 22, "P", "Da Dày", "(-20%)", level >= 3, 3, true,
                0, 1.0, 3);

        // Trạng thái ẩn nấp / bơi / con mồi sống bên góc phải
        if (player.isHidden()) {
            drawOutlinedPixelString(g, "[ẨN NẤP] +4 HP/s | +25 MP/s", viewW - 250, 32,
                    new Color(134, 239, 172), HUD_TEXT_OUTLINE, FONT_PIXEL_LABEL, FONT_HUD_LABEL);
        } else if (player.isInWater()) {
            drawOutlinedPixelString(g, "[ĐANG BƠI] Cẩn thận Cá Sấu!", viewW - 250, 32,
                    new Color(56, 189, 248), HUD_TEXT_OUTLINE, FONT_PIXEL_LABEL, FONT_HUD_LABEL);
        } else {
            drawOutlinedPixelString(g, "MỒI SỐNG: " + preyCount, viewW - 145, 20,
                    new Color(203, 213, 225), HUD_TEXT_OUTLINE, FONT_PIXEL_LABEL, FONT_HUD_LABEL);
        }

        // Hiển thị chỉ số nâng cấp thuộc tính đã tích lũy
        if (player.getBaseSpeedBonus() > 0 || player.getSkillDamageBonus() > 0 || player.getMaxHealthBonus() > 0) {
            String bonusStr = String.format("NÂNG CẤP: [⚡ +%.0f TỐC ĐỘ] [⚔ +%d SÁT THƯƠNG] [♥ +%d MAX HP]",
                    player.getBaseSpeedBonus(), player.getSkillDamageBonus(), player.getMaxHealthBonus());
            drawOutlinedPixelString(g, bonusStr, 14, 55, new Color(250, 204, 21), HUD_TEXT_OUTLINE, FONT_PIXEL_NUMBER, FONT_BADGE);
        }

        // 6. THANH MÁU BOSS KHỔNG LỒ TRÊN ĐỈNH MÀN HÌNH (NẾU CÓ BOSS ĐANG SỐNG)
        for (Boss boss : bosses) {
            if (boss.isAlive()) {
                renderBossHudBanner(g, viewW, boss);
                break;
            }
        }
    }

    private void drawSkillPixelIcon(Graphics2D g, int x, int y, int size, int iconType, boolean canCast) {
        int cx = x + size / 2;
        int cy = y + size / 2;
        switch (iconType) {
            case 0 -> {
                // Icon Giọt Độc (Venom Droplet)
                g.setColor(canCast ? new Color(74, 222, 128) : new Color(34, 197, 94, 120));
                g.fillOval(cx - 4, cy - 2, 8, 8);
                int[] tx = {cx, cx - 4, cx + 4};
                int[] ty = {cy - 6, cy, cy};
                g.fillPolygon(tx, ty, 3);
                g.setColor(Color.WHITE);
                g.fillRect(cx - 1, cy + 1, 2, 2);
            }
            case 1 -> {
                // Icon Sóng Âm Rung Đuôi (Rattle Shockwaves)
                g.setColor(canCast ? new Color(250, 204, 21) : new Color(234, 179, 8, 120));
                g.drawArc(cx - 3, cy - 3, 6, 6, 45, 270);
                g.drawArc(cx - 6, cy - 6, 12, 12, 45, 270);
                g.fillRect(cx - 1, cy - 1, 3, 3);
            }
            case 2 -> {
                // Icon Lướt Dash (Dual Wind Arrows >>)
                g.setColor(canCast ? new Color(34, 211, 238) : new Color(6, 182, 212, 120));
                g.drawLine(cx - 5, cy - 4, cx - 1, cy);
                g.drawLine(cx - 5, cy + 4, cx - 1, cy);
                g.drawLine(cx, cy - 4, cx + 4, cy);
                g.drawLine(cx, cy + 4, cx + 4, cy);
            }
            case 3 -> {
                // Icon Giáp Da Dày (Armor Shield)
                g.setColor(new Color(251, 146, 60));
                int[] sx = {cx - 5, cx + 5, cx + 4, cx, cx - 4};
                int[] sy = {cy - 5, cy - 5, cy + 2, cy + 6, cy + 2};
                g.fillPolygon(sx, sy, 5);
                g.setColor(new Color(254, 240, 138));
                g.fillRect(cx - 1, cy - 3, 2, 6);
                g.fillRect(cx - 3, cy - 1, 6, 2);
            }
        }
    }

    private void drawSkillSlot(Graphics2D g, int x, int y, int size, String hotkey, String skillName, String detail,
                               boolean unlocked, int reqLevel, boolean canCast, double cdTimer, double maxCd, int iconType) {
        // 1. Nền ô kỹ năng
        g.setColor(unlocked ? HUD_BAR_BG : new Color(30, 41, 59, 210));
        g.fillRoundRect(x, y, size, size, 4, 4);

        // 2. Vẽ Icon pixel bên trong
        if (unlocked) {
            drawSkillPixelIcon(g, x, y, size, iconType, canCast);
        } else {
            // Icon khóa xám
            g.setColor(new Color(100, 116, 139));
            g.fillRect(x + size / 2 - 4, y + size / 2 - 1, 8, 7);
            g.drawArc(x + size / 2 - 3, y + size / 2 - 5, 6, 6, 0, 180);
        }

        // 3. Hiệu ứng hồi chiêu: quét quạt tròn / tối dần
        if (unlocked && cdTimer > 0 && maxCd > 0) {
            double cdPct = Math.min(1.0, cdTimer / maxCd);
            g.setColor(new Color(15, 23, 42, 200));
            g.fillArc(x, y, size, size, 90, (int) Math.round(360.0 * cdPct));

            String cdText = String.format("%.1f", cdTimer);
            Font cdFont = (FONT_PIXEL_SMALL.canDisplayUpTo(cdText) == -1) ? FONT_PIXEL_SMALL : FONT_SMALL_BOLD;
            g.setFont(cdFont);
            int tw = g.getFontMetrics().stringWidth(cdText);
            drawOutlinedString(g, cdText, x + (size - tw) / 2, y + size / 2 + 3, new Color(254, 240, 138), HUD_TEXT_OUTLINE);
        }

        // 4. Viền ô kỹ năng
        Color borderColor = !unlocked ? new Color(51, 65, 85, 220) :
                (cdTimer > 0 ? new Color(100, 116, 139, 200) :
                (canCast ? new Color(56, 189, 248, 220) : new Color(239, 68, 68, 200)));
        g.setColor(borderColor);
        g.drawRoundRect(x, y, size, size, 4, 4);

        // 5. Badge phím tắt
        Font badgeFont = (FONT_PIXEL_NUMBER.canDisplayUpTo(hotkey) == -1) ? FONT_PIXEL_NUMBER : FONT_BAR_NUMBER;
        g.setFont(badgeFont);
        Color keyColor = unlocked ? new Color(250, 204, 21) : new Color(148, 163, 184);
        drawOutlinedString(g, hotkey, x + 2, y - 2, keyColor, HUD_TEXT_OUTLINE);

        // 6. Nhãn tên chiêu & chi tiết
        int textX = x + size + 5;
        int textY = y + size / 2 + 4;
        Color labelCol = !unlocked ? new Color(100, 116, 139) :
                (cdTimer > 0 ? new Color(148, 163, 184) :
                (canCast ? Color.WHITE : new Color(248, 113, 113)));
        String labelStr = unlocked ? (skillName + " " + detail).trim() : (skillName + " (Lv." + reqLevel + ")");
        drawOutlinedPixelString(g, labelStr, textX, textY, labelCol, HUD_TEXT_OUTLINE, FONT_PIXEL_LABEL, FONT_HUD_LABEL);
    }

    private void renderBossHudBanner(Graphics2D g, int viewW, Boss boss) {
        int bannerW = 280;
        int bannerH = 14;
        int mpH = 5;
        boolean recovering = boss.isRecovering() && boss.getStunTimer() <= 0.0;
        int boxH = bannerH + (recovering ? 42 : 30);
        int bx = (viewW - bannerW) / 2;
        int by = 60;

        // Nền đen mờ bo tròn
        g.setColor(new Color(15, 23, 42, 230));
        g.fillRoundRect(bx - 12, by - 16, bannerW + 24, boxH, 6, 6);
        Color borderCol = switch (boss.getType()) {
            case BEAR -> new Color(249, 115, 22, 200);
            case OCTOPUS -> new Color(244, 63, 94, 220);
            case GIANT_PYTHON -> new Color(168, 85, 247, 200);
            case SWAMP_KING -> new Color(56, 189, 248, 220);
            case VOID_DRAGON -> new Color(250, 204, 21, 220);
        };
        g.setColor(borderCol);
        g.drawRoundRect(bx - 12, by - 16, bannerW + 24, boxH, 6, 6);

        // Tiêu đề Boss với Vương Miện có viền 1px
        String name = "★ BOSS: " + boss.getName() + " ★";
        Font bossFont = (FONT_PIXEL_LABEL.canDisplayUpTo(name) == -1) ? FONT_PIXEL_LABEL : FONT_BADGE;
        g.setFont(bossFont);
        int nw = g.getFontMetrics().stringWidth(name);
        drawOutlinedString(g, name, bx + (bannerW - nw) / 2, by - 3, Color.WHITE, HUD_TEXT_OUTLINE);

        // Thanh HP nền
        g.setColor(HUD_BAR_BG);
        g.fillRoundRect(bx, by, bannerW, bannerH, 4, 4);

        // Thanh HP đầy (clamp [0, 1])
        double bPct = boss.getMaxHealth() > 0 ? Math.max(0.0, Math.min(1.0, (double) boss.getHealth() / boss.getMaxHealth())) : 0.0;
        g.setColor(new Color(220, 38, 38));
        g.fillRoundRect(bx, by, (int) Math.round(bannerW * bPct), bannerH, 4, 4);

        // Viền vàng
        g.setColor(new Color(250, 204, 21));
        g.drawRoundRect(bx, by, bannerW, bannerH, 4, 4);

        // Chữ HP số có viền 1px
        String bHpStr = boss.getHealth() + " / " + boss.getMaxHealth() + " HP";
        Font bNumFont = (FONT_PIXEL_NUMBER.canDisplayUpTo(bHpStr) == -1) ? FONT_PIXEL_NUMBER : FONT_BAR_NUMBER;
        g.setFont(bNumFont);
        int bhw = g.getFontMetrics().stringWidth(bHpStr);
        drawOutlinedString(g, bHpStr, bx + (bannerW - bhw) / 2, by + 10, Color.WHITE, HUD_TEXT_OUTLINE);

        // Thanh Mana Boss ngay bên dưới HP
        int my = by + bannerH + 3;
        g.setColor(HUD_BAR_BG);
        g.fillRoundRect(bx, my, bannerW, mpH, 3, 3);
        g.setColor(COLOR_BOSS_MANA_FILL);
        g.fillRoundRect(bx, my, (int) Math.round(bannerW * boss.getManaRatio()), mpH, 3, 3);
        g.setColor(COLOR_BOSS_MANA_GLOW);
        g.drawRoundRect(bx, my, bannerW, mpH, 3, 3);

        // Hiển thị đồng hồ đếm ngược 2 giây nghỉ hồi Mana
        if (recovering) {
            int labelIdx = Math.max(0, Math.min(RECOVERY_LABEL_CACHE.length - 1,
                    (int) Math.round(boss.getRecoveryTimer() * 10.0)));
            String recStr = RECOVERY_LABEL_CACHE[labelIdx];
            Font recFont = (FONT_PIXEL_NUMBER.canDisplayUpTo(recStr) == -1) ? FONT_PIXEL_NUMBER : FONT_BAR_NUMBER;
            g.setFont(recFont);
            int rw = g.getFontMetrics().stringWidth(recStr);
            drawOutlinedString(g, recStr, bx + (bannerW - rw) / 2, my + mpH + 12, COLOR_BOSS_RECOVERY_TEXT, HUD_TEXT_OUTLINE);
        }
    }

    public int getHoveredCardOption(int viewW, int viewH, Point mousePos) {
        if (mousePos == null) return 0;
        int cardW = 220;
        int cardH = 270;
        int gap = 24;
        int totalW = cardW * 3 + gap * 2;
        int startX = (viewW - totalW) / 2;
        int startY = (viewH - cardH) / 2 + 20;

        for (int i = 0; i < 3; i++) {
            int cx = startX + i * (cardW + gap);
            Rectangle r = new Rectangle(cx, startY, cardW, cardH);
            if (r.contains(mousePos)) {
                return i + 1; // 1, 2, hoặc 3
            }
        }
        return 0;
    }

    public void renderLevelUpModal(Graphics2D g, int viewW, int viewH, Point mousePos) {
        // 1. Phủ màn đen mờ toàn màn hình
        g.setColor(new Color(10, 15, 26, 220));
        g.fillRect(0, 0, viewW, viewH);

        // 2. Banner tiêu đề phía trên
        g.setFont(FONT_TITLE_MODAL);
        String title = "★ THĂNG CẤP TIẾN HÓA! CHỌN 1 THUỘC TÍNH NÂNG CẤP ★";
        int tw = g.getFontMetrics().stringWidth(title);
        g.setColor(new Color(0, 0, 0, 180));
        g.drawString(title, (viewW - tw) / 2 + 2, (viewH / 2) - 158);
        g.setColor(new Color(250, 204, 21));
        g.drawString(title, (viewW - tw) / 2, (viewH / 2) - 160);

        g.setFont(FONT_HUD_LABEL);
        g.setColor(new Color(203, 213, 225));
        String sub = "Nhấn phím [1], [2], [3] hoặc CLICK CHUỘT vào thẻ bài để xác nhận lựa chọn";
        int sw = g.getFontMetrics().stringWidth(sub);
        g.drawString(sub, (viewW - sw) / 2, (viewH / 2) - 130);

        // 3. Ba Thẻ Thuộc Tính Nâng Cấp
        int cardW = 220;
        int cardH = 270;
        int gap = 24;
        int totalW = cardW * 3 + gap * 2;
        int startX = (viewW - totalW) / 2;
        int startY = (viewH - cardH) / 2 + 20;

        int hoveredOption = getHoveredCardOption(viewW, viewH, mousePos);

        // Card 1: Tăng Tốc Độ Chạy Cơ Bản
        drawUpgradeCard(g, startX, startY, cardW, cardH, 1,
                "⚡ TỐC ĐỘ DI CHUYỂN",
                "TĂNG TỐC CHẠY CƠ BẢN",
                new String[]{
                        "• +25 px/s Tốc độ cơ bản",
                        "• +35 px/s Tốc độ bơi & sprint",
                        "• Uốn lượn thần tốc, né đòn",
                        "• Dễ dàng rượt đuổi con mồi"
                },
                new Color(56, 189, 248),
                hoveredOption == 1
        );

        // Card 2: Tăng Dame Cơ Bản Cho Chiêu
        drawUpgradeCard(g, startX + cardW + gap, startY, cardW, cardH, 2,
                "⚔ UY LỰC ĐỘC TỐ",
                "TĂNG DAME CHIÊU THỨC",
                new String[]{
                        "• +15 Sát thương Phun Độc",
                        "• +4 Sát thương DoT rút độc",
                        "• +20 Sát thương Rung Đuôi",
                        "• Bào mòn và hạ Boss cực nhanh"
                },
                new Color(192, 132, 252),
                hoveredOption == 2
        );

        // Card 3: Tăng Máu Cơ Bản
        drawUpgradeCard(g, startX + (cardW + gap) * 2, startY, cardW, cardH, 3,
                "♥ THỂ CHẤT BỀN BỈ",
                "TĂNG MÁU CƠ BẢN",
                new String[]{
                        "• +60 Máu tối đa (Max HP)",
                        "• Hồi phục +35 Máu tức thì",
                        "• Hồi phục +40 Mana khẩn cấp",
                        "• Trở thành pháo đài sinh tồn"
                },
                new Color(74, 222, 128),
                hoveredOption == 3
        );
    }

    private void drawUpgradeCard(Graphics2D g, int cx, int cy, int cw, int ch, int option,
                                String category, String name, String[] perks, Color accent, boolean hovered) {
        int drawY = hovered ? cy - 8 : cy;

        // Đổ bóng thẻ
        g.setColor(new Color(0, 0, 0, hovered ? 160 : 100));
        g.fillRoundRect(cx + 4, drawY + 8, cw, ch, 14, 14);

        // Nền thẻ
        g.setColor(hovered ? new Color(30, 41, 59, 245) : new Color(15, 23, 42, 235));
        g.fillRoundRect(cx, drawY, cw, ch, 14, 14);

        // Viền thẻ
        g.setColor(hovered ? accent : new Color(71, 85, 105));
        g.setStroke(new BasicStroke(hovered ? 3.0f : 1.5f));
        g.drawRoundRect(cx, drawY, cw, ch, 14, 14);

        // Badge Phím Tắt
        g.setColor(hovered ? accent : new Color(51, 65, 85));
        g.fillRoundRect(cx + 12, drawY + 12, 54, 22, 6, 6);
        g.setFont(FONT_HUD_BOLD);
        g.setColor(hovered ? Color.BLACK : Color.WHITE);
        g.drawString("[" + option + "]", cx + 27, drawY + 27);

        // Nhãn danh mục
        g.setFont(FONT_BADGE);
        g.setColor(accent);
        g.drawString(category, cx + 72, drawY + 27);

        // Tên thuộc tính
        g.setFont(FONT_TITLE_CARD);
        g.setColor(Color.WHITE);
        g.drawString(name, cx + 14, drawY + 60);

        // Đường gạch ngang phân tách
        g.setColor(new Color(51, 65, 85));
        g.drawLine(cx + 14, drawY + 70, cx + cw - 14, drawY + 70);

        // Các dòng chi tiết
        g.setFont(FONT_BODY);
        g.setColor(new Color(226, 232, 240));
        int py = drawY + 95;
        for (String perk : perks) {
            g.drawString(perk, cx + 12, py);
            py += 24;
        }

        // Nút bấm ở đáy thẻ
        int btnW = cw - 28;
        int btnH = 34;
        int btnX = cx + 14;
        int btnY = drawY + ch - 46;

        g.setColor(hovered ? accent : new Color(30, 41, 59));
        g.fillRoundRect(btnX, btnY, btnW, btnH, 8, 8);
        g.setColor(hovered ? Color.WHITE : new Color(148, 163, 184));
        g.drawRoundRect(btnX, btnY, btnW, btnH, 8, 8);

        g.setFont(FONT_BUTTON);
        g.setColor(hovered ? Color.BLACK : Color.WHITE);
        String btnText = hovered ? "▶ CLICK ĐỂ CHỌN ◀" : "Nhấn [" + option + "] để chọn";
        int bw = g.getFontMetrics().stringWidth(btnText);
        g.drawString(btnText, btnX + (btnW - bw) / 2, btnY + 21);
    }

    // ==========================================
    // MÀN HÌNH CHÍNH (MAIN MENU)
    // ==========================================
    public static final int MENU_BTN_PLAY = 1;
    public static final int MENU_BTN_STAGE = 2;
    public static final int MENU_BTN_GUIDE = 3;
    public static final int MENU_BTN_EXIT = 4;

    public int getHoveredMainMenuButton(int viewW, int viewH, Point mousePos) {
        if (mousePos == null) return 0;
        int btnW = 360;
        int btnH = 50;
        int startX = (viewW - btnW) / 2;
        int startY = viewH / 2 - 10;
        int gap = 62;

        for (int i = 0; i < 4; i++) {
            Rectangle r = new Rectangle(startX, startY + i * gap, btnW, btnH);
            if (r.contains(mousePos)) {
                return i + 1;
            }
        }
        return 0;
    }

    public void renderMainMenu(Graphics2D g, int viewW, int viewH, Point mousePos, double animTime) {
        // Nền chuyển màu Gradient rừng sâu kỳ bí
        GradientPaint bgGrad = new GradientPaint(
                0, 0, new Color(10, 25, 18),
                0, viewH, new Color(15, 23, 42)
        );
        g.setPaint(bgGrad);
        g.fillRect(0, 0, viewW, viewH);

        // Họa tiết trang trí lưới ô mờ & mây trôi
        g.setColor(new Color(34, 197, 94, 25));
        for (int x = 0; x < viewW; x += 40) {
            g.drawLine(x, 0, x, viewH);
        }
        for (int y = 0; y < viewH; y += 40) {
            g.drawLine(0, y, viewW, y);
        }

        // Đom đóm & hạt sinh thái bay lơ lửng
        for (int i = 0; i < 28; i++) {
            double px = ((i * 97 + animTime * 35) % (viewW + 40)) - 20;
            double py = ((i * 139 + Math.sin(animTime * 1.5 + i) * 35) % (viewH + 40)) - 20;
            int r = (i % 3) + 2;
            g.setColor(new Color(250, 204, 21, 90 + (int)(Math.sin(animTime * 2.0 + i) * 60)));
            g.fillOval((int) px, (int) py, r * 2, r * 2);
        }

        // Họa tiết Rắn khổng lồ uốn lượn phong cách Silhouette ở nền
        g.setColor(new Color(34, 197, 94, 30));
        for (int i = 0; i < 24; i++) {
            double sx = viewW * 0.15 + i * 28 + Math.sin(animTime * 2.5 - i * 0.3) * 35;
            double sy = viewH * 0.32 + Math.cos(animTime * 2.0 - i * 0.25) * 45;
            int sr = 38 - i;
            if (sr > 10) g.fillOval((int) sx - sr, (int) sy - sr, sr * 2, sr * 2);
        }

        // BANNER TIÊU ĐỀ CHÍNH (TITLE LOGO)
        int titleY = viewH / 2 - 165;
        g.setFont(FONT_TITLE_HUGE);
        String mainTitle = "SNAKE ARENA: ECOSYSTEM";
        int tw = g.getFontMetrics().stringWidth(mainTitle);

        // Đổ bóng tiêu đề
        g.setColor(new Color(0, 0, 0, 220));
        g.drawString(mainTitle, (viewW - tw) / 2 + 3, titleY + 3);
        // Hào quang vàng kim
        g.setColor(new Color(250, 204, 21, 140));
        g.drawString(mainTitle, (viewW - tw) / 2 - 1, titleY - 1);
        // Chữ chính màu xanh ngọc lục bảo rực sáng
        g.setColor(new Color(74, 222, 128));
        g.drawString(mainTitle, (viewW - tw) / 2, titleY);

        // Phụ đề phong thái sử thi
        g.setFont(FONT_TITLE_SECTION);
        String sub = "CHIẾN BINH SINH TỒN THẢO NGUYÊN & ĐẦM LẦY HOANG DÃ";
        int sw = g.getFontMetrics().stringWidth(sub);
        g.setColor(new Color(226, 232, 240, 220));
        g.drawString(sub, (viewW - sw) / 2, titleY + 36);

        // Đường gạch hoa văn rực rỡ
        int lineW = 420;
        g.setColor(new Color(250, 204, 21, 180));
        g.fillRect((viewW - lineW) / 2, titleY + 46, lineW, 2);

        // 4 NÚT BẤM MENU CHÍNH
        int hovered = getHoveredMainMenuButton(viewW, viewH, mousePos);
        int btnW = 360;
        int btnH = 50;
        int startX = (viewW - btnW) / 2;
        int startY = viewH / 2 - 10;
        int gap = 62;

        drawMenuButton(g, startX, startY, btnW, btnH, "▶  CHƠI MÀN 1 - THẢO NGUYÊN", new Color(34, 197, 94), hovered == MENU_BTN_PLAY);
        drawMenuButton(g, startX, startY + gap, btnW, btnH, "✦  DANH SÁCH MÀN CHƠI", new Color(56, 189, 248), hovered == MENU_BTN_STAGE);
        drawMenuButton(g, startX, startY + gap * 2, btnW, btnH, "?  HƯỚNG DẪN ĐIỀU KHIỂN", new Color(250, 204, 21), hovered == MENU_BTN_GUIDE);
        drawMenuButton(g, startX, startY + gap * 3, btnW, btnH, "✕  THOÁT TRÒ CHƠI", new Color(239, 68, 68), hovered == MENU_BTN_EXIT);

        // Footer phiên bản
        g.setFont(FONT_SMALL_REGULAR);
        g.setColor(new Color(148, 163, 184));
        String ver = "Phiên bản Java SE 21 | Snake Arena Ecosystem v2.5 | 60 FPS Engine";
        g.drawString(ver, (viewW - g.getFontMetrics().stringWidth(ver)) / 2, viewH - 24);
    }

    private void drawMenuButton(Graphics2D g, int x, int y, int w, int h, String text, Color accent, boolean hovered) {
        int drawY = hovered ? y - 3 : y;

        // Đổ bóng nút
        g.setColor(new Color(0, 0, 0, hovered ? 180 : 120));
        g.fillRoundRect(x + 3, drawY + 6, w, h, 12, 12);

        // Nền nút
        g.setColor(hovered ? new Color(30, 41, 59, 250) : new Color(15, 23, 42, 235));
        g.fillRoundRect(x, drawY, w, h, 12, 12);

        // Viền nút rực sáng
        g.setColor(hovered ? accent : new Color(71, 85, 105));
        g.setStroke(new BasicStroke(hovered ? 2.5f : 1.5f));
        g.drawRoundRect(x, drawY, w, h, 12, 12);

        // Chấm tròn báo trạng thái
        g.setColor(accent);
        g.fillOval(x + 18, drawY + (h - 10) / 2, 10, 10);

        // Chữ trên nút
        g.setFont(FONT_BUTTON);
        g.setColor(hovered ? Color.WHITE : new Color(226, 232, 240));
        int tw = g.getFontMetrics().stringWidth(text);
        g.drawString(text, x + (w - tw) / 2 + 8, drawY + h / 2 + 6);
    }

    // ==========================================
    // GIAO DIỆN CHỌN MÀN CHƠI (STAGE SELECT)
    // ==========================================
    public int getHoveredStageCard(int viewW, int viewH, Point mousePos) {
        if (mousePos == null) return 0;
        int cardW = 270;
        int cardH = 360;
        int gap = 24;
        int totalW = cardW * 3 + gap * 2;
        int startX = (viewW - totalW) / 2;
        int startY = (viewH - cardH) / 2 - 15;

        for (int i = 0; i < 3; i++) {
            Rectangle r = new Rectangle(startX + i * (cardW + gap), startY, cardW, cardH);
            if (r.contains(mousePos)) {
                return i + 1; // 1: Stage 1, 2: Stage 2, 3: Stage 3
            }
        }

        // Nút quay lại menu ở đáy
        Rectangle backBtn = new Rectangle((viewW - 240) / 2, startY + cardH + 28, 240, 44);
        if (backBtn.contains(mousePos)) {
            return 99; // Quay lại Menu
        }
        return 0;
    }

    public void renderStageSelect(Graphics2D g, int viewW, int viewH, Point mousePos, double animTime) {
        // Nền chuyển màu
        GradientPaint bg = new GradientPaint(0, 0, new Color(15, 23, 42), 0, viewH, new Color(10, 15, 26));
        g.setPaint(bg);
        g.fillRect(0, 0, viewW, viewH);

        // Tiêu đề
        g.setFont(FONT_TITLE_LARGE);
        String title = "★ CHỌN MÀN CHƠI (STAGE SELECT) ★";
        int tw = g.getFontMetrics().stringWidth(title);
        g.setColor(new Color(250, 204, 21));
        g.drawString(title, (viewW - tw) / 2, 60);

        g.setFont(FONT_BODY);
        g.setColor(new Color(203, 213, 225));
        String sub = "Tiêu diệt toàn bộ Boss của màn để giành Chiến Thắng (Victory)";
        g.drawString(sub, (viewW - g.getFontMetrics().stringWidth(sub)) / 2, 88);

        int cardW = 270;
        int cardH = 360;
        int gap = 24;
        int totalW = cardW * 3 + gap * 2;
        int startX = (viewW - totalW) / 2;
        int startY = (viewH - cardH) / 2 - 15;

        int hovered = getHoveredStageCard(viewW, viewH, mousePos);

        // THẺ MÀN 1: THẢO NGUYÊN BÁT NGÁT (SẴN SÀNG)
        drawStageCard(g, startX, startY, cardW, cardH, 1,
                "MÀN 1: THẢO NGUYÊN BÁT NGÁT",
                "[SẴN SÀNG CHIẾN ĐẤU]",
                new String[]{
                        "• Địa hình: Cỏ xanh, Đầm nước, Hang đá",
                        "• Kẻ thù: Rắn săn AI, Cá sấu, Bẫy kẹp",
                        "• Boss 1: Bạch Mao Hùng (Lv 2)",
                        "• Boss 2: Thái Cổ Cự Trăn (Lv 3)",
                        "• Mục tiêu: Diệt sạch 2 Boss để Thắng!"
                },
                "▶ VÀO TRẬN CHIẾN",
                new Color(34, 197, 94),
                true,
                hovered == 1
        );

        // THẺ MÀN 2: ĐẠI DƯƠNG XANH THẲM (SẴN SÀNG)
        drawStageCard(g, startX + cardW + gap, startY, cardW, cardH, 2,
                "MÀN 2: ĐẠI DƯƠNG XANH THẲM",
                "[SẴN SÀNG CHIẾN ĐẤU]",
                new String[]{
                        "• Địa hình: Biển xanh sâu thẳm, Rạn đá ngầm",
                        "• Nơi ẩn náu: Tảo biển, Rong biển (+4 HP/s)",
                        "• Sinh vật: Đàn cá biển tung tăng bơi lội",
                        "• Kẻ thù: Cá mập săn mồi, Chim hải âu",
                        "• Boss 1: Bạch Tuộc Cự Đại (Lv 2)",
                        "• Boss 2: Hải Long Cự Thần (Lv 3)",
                        "• Mục tiêu: Diệt sạch 2 Boss để Thắng!"
                },
                "▶ VÀO TRẬN CHIẾN",
                new Color(56, 189, 248),
                true,
                hovered == 2
        );

        // THẺ MÀN 3: NÚI LỬA HƯ KHÔNG (KHÓA)
        drawStageCard(g, startX + (cardW + gap) * 2, startY, cardW, cardH, 3,
                "MÀN 3: NÚI LỬA HƯ KHÔNG",
                "[ĐANG KHÓA]",
                new String[]{
                        "• Địa hình: Dung nham đỏ, Tro tàn hỏa diệm",
                        "• Kẻ thù: Hỏa xà địa ngục, Quái lửa",
                        "• Boss: Hư Không Ma Long Tối Thượng",
                        "• Thử thách sinh tồn khắc nghiệt nhất",
                        "• Yêu cầu hoàn thành các màn trước"
                },
                "[CHƯA MỞ]",
                new Color(100, 116, 139),
                false,
                hovered == 3
        );

        // NÚT QUAY LẠI MENU CHÍNH
        int backW = 240;
        int backH = 44;
        int backX = (viewW - backW) / 2;
        int backY = startY + cardH + 28;
        boolean backHovered = (hovered == 99);

        g.setColor(backHovered ? new Color(51, 65, 85) : new Color(30, 41, 59));
        g.fillRoundRect(backX, backY, backW, backH, 10, 10);
        g.setColor(backHovered ? new Color(250, 204, 21) : new Color(100, 116, 139));
        g.setStroke(new BasicStroke(backHovered ? 2.0f : 1.0f));
        g.drawRoundRect(backX, backY, backW, backH, 10, 10);

        g.setFont(FONT_BUTTON);
        g.setColor(backHovered ? Color.WHITE : new Color(203, 213, 225));
        String backText = "◀  VỀ MENU CHÍNH";
        int bw = g.getFontMetrics().stringWidth(backText);
        g.drawString(backText, backX + (backW - bw) / 2, backY + 27);
    }

    private void drawStageCard(Graphics2D g, int cx, int cy, int cw, int ch, int stageNum,
                               String title, String statusBadge, String[] details,
                               String actionText, Color accent, boolean unlocked, boolean hovered) {
        int drawY = (hovered && unlocked) ? cy - 6 : cy;

        // Bóng đổ
        g.setColor(new Color(0, 0, 0, hovered ? 180 : 110));
        g.fillRoundRect(cx + 4, drawY + 6, cw, ch, 14, 14);

        // Nền thẻ
        g.setColor(hovered && unlocked ? new Color(24, 34, 48, 250) : new Color(15, 23, 42, 235));
        g.fillRoundRect(cx, drawY, cw, ch, 14, 14);

        // Viền thẻ
        g.setColor(hovered && unlocked ? accent : (unlocked ? new Color(51, 65, 85) : new Color(30, 41, 59)));
        g.setStroke(new BasicStroke(hovered && unlocked ? 2.5f : 1.5f));
        g.drawRoundRect(cx, drawY, cw, ch, 14, 14);

        // Header huy hiệu màn
        g.setColor(unlocked ? accent : new Color(71, 85, 105));
        g.fillRoundRect(cx + 14, drawY + 14, 76, 22, 6, 6);
        g.setFont(FONT_BADGE);
        g.setColor(unlocked ? Color.BLACK : Color.WHITE);
        g.drawString("MÀN 0" + stageNum, cx + 22, drawY + 29);

        // Trạng thái mở / khóa
        g.setFont(FONT_BADGE);
        g.setColor(accent);
        g.drawString(statusBadge, cx + 100, drawY + 29);

        // Tên màn chơi
        g.setFont(FONT_TITLE_CARD);
        g.setColor(unlocked ? Color.WHITE : new Color(148, 163, 184));
        g.drawString(title, cx + 14, drawY + 64);

        // Đường phân tách
        g.setColor(new Color(51, 65, 85));
        g.drawLine(cx + 14, drawY + 74, cx + cw - 14, drawY + 74);

        // Nội dung chi tiết
        g.setFont(FONT_BODY);
        g.setColor(unlocked ? new Color(226, 232, 240) : new Color(100, 116, 139));
        int py = drawY + 102;
        for (String line : details) {
            g.drawString(line, cx + 14, py);
            py += 24;
        }

        // Nút hành động đáy thẻ
        int btnW = cw - 28;
        int btnH = 38;
        int btnX = cx + 14;
        int btnY = drawY + ch - 52;

        if (unlocked) {
            g.setColor(hovered ? accent : new Color(22, 101, 52));
            g.fillRoundRect(btnX, btnY, btnW, btnH, 8, 8);
            g.setColor(hovered ? Color.WHITE : new Color(74, 222, 128));
            g.drawRoundRect(btnX, btnY, btnW, btnH, 8, 8);

            g.setFont(FONT_BUTTON);
            g.setColor(hovered ? Color.BLACK : Color.WHITE);
            int aw = g.getFontMetrics().stringWidth(actionText);
            g.drawString(actionText, btnX + (btnW - aw) / 2, btnY + 24);
        } else {
            g.setColor(new Color(30, 41, 59));
            g.fillRoundRect(btnX, btnY, btnW, btnH, 8, 8);
            g.setColor(new Color(51, 65, 85));
            g.drawRoundRect(btnX, btnY, btnW, btnH, 8, 8);

            g.setFont(FONT_BUTTON);
            g.setColor(new Color(100, 116, 139));
            int aw = g.getFontMetrics().stringWidth(actionText);
            g.drawString(actionText, btnX + (btnW - aw) / 2, btnY + 24);
        }
    }

    // ==========================================
    // MODAL HƯỚNG DẪN ĐIỀU KHIỂN (HOW TO PLAY)
    // ==========================================
    public boolean isHoveredHowToPlayClose(int viewW, int viewH, Point mousePos) {
        if (mousePos == null) return false;
        int modalW = 920;
        int modalH = 600;
        int my = (viewH - modalH) / 2;
        int btnW = 260;
        int btnH = 38;
        int bx = (viewW - btnW) / 2;
        int by = my + modalH - 50;
        Rectangle r = new Rectangle(bx, by, btnW, btnH);
        return r.contains(mousePos);
    }

    public void renderHowToPlayModal(Graphics2D g, int viewW, int viewH, Point mousePos) {
        // Phủ mờ toàn màn hình
        g.setColor(new Color(8, 12, 22, 235));
        g.fillRect(0, 0, viewW, viewH);

        int modalW = 920;
        int modalH = 600;
        int mx = (viewW - modalW) / 2;
        int my = (viewH - modalH) / 2;

        // Đổ bóng hộp
        g.setColor(new Color(0, 0, 0, 220));
        g.fillRoundRect(mx + 4, my + 8, modalW, modalH, 16, 16);

        // Nền khung hướng dẫn
        g.setColor(new Color(15, 23, 42, 252));
        g.fillRoundRect(mx, my, modalW, modalH, 16, 16);
        g.setColor(new Color(250, 204, 21));
        g.setStroke(new BasicStroke(2.0f));
        g.drawRoundRect(mx, my, modalW, modalH, 16, 16);

        // Tiêu đề chính
        g.setFont(FONT_TITLE_MODAL);
        String title = "[ CẨM NANG SINH TỒN & BÍ KÍP ĐIỀU KHIỂN TOÀN TẬP ]";
        int tw = g.getFontMetrics().stringWidth(title);
        g.setColor(new Color(250, 204, 21));
        g.drawString(title, mx + (modalW - tw) / 2, my + 34);

        g.setFont(FONT_BODY);
        g.setColor(new Color(148, 163, 184));
        String sub = "Chi tiết toàn bộ kỹ năng, cơ chế môi trường sinh thái và mục tiêu chiến thắng cả 2 màn chơi";
        int sw = g.getFontMetrics().stringWidth(sub);
        g.drawString(sub, mx + (modalW - sw) / 2, my + 52);

        // Đường gạch ngang phân cách
        g.setColor(new Color(51, 65, 85));
        g.drawLine(mx + 20, my + 64, mx + modalW - 20, my + 64);

        // Đường gạch dọc phân cách 2 cột
        g.drawLine(mx + 456, my + 74, mx + 456, my + modalH - 64);

        // TIÊU ĐỀ 2 CỘT (Sử dụng ký tự chuẩn để tránh lỗi font)
        int col1X = mx + 20;
        int col2X = mx + 474;
        int colW = 426;

        g.setFont(FONT_HUD_BOLD);
        g.setColor(new Color(56, 189, 248));
        g.drawString(">>  KỸ NĂNG CHIẾN ĐẤU & THAO TÁC", col1X + 4, my + 86);

        g.setColor(new Color(250, 204, 21));
        g.drawString("--  HỆ SINH THÁI, TIẾN HÓA & MỤC TIÊU  --", col2X + 4, my + 86);

        // CỘT 1: 5 THẺ KỸ NĂNG VÀ THAO TÁC ĐIỀU KHIỂN
        int c1Y = my + 98;
        int c1H = 66;
        int gap1 = 8;

        drawGuideCard(g, col1X, c1Y, colW, c1H,
                "[DI CHUYỂN]", "ĐIỀU HƯỚNG ĐẦU RẮN", "Chuột / WASD",
                new String[]{
                        "• Rê chuột định hướng đầu rắn hoặc dùng phím mũi tên / WASD.",
                        "• Rắn uốn lượn nhịp nhàng theo trỏ chuột, né tránh vật cản và đá."
                },
                new Color(56, 189, 248)
        );

        drawGuideCard(g, col1X, c1Y + (c1H + gap1), colW, c1H,
                "[TĂNG TỐC]", "BƠI NHANH & LƯỚT HÚC", "SHIFT / SPACE",
                new String[]{
                        "• Giữ CHUỘT TRÁI hoặc SHIFT để bơi/chạy nhanh (tốn thể lực).",
                        "• Bấm SPACE để LƯỚT HÚC (Dash) gây 40+ sát thương và húc văng quái!"
                },
                new Color(250, 204, 21)
        );

        drawGuideCard(g, col1X, c1Y + (c1H + gap1) * 2, colW, c1H,
                "[CHIÊU 1]", "PHUN ĐỘC NỔ LAN (AoE)", "Chuột Phải / Q",
                new String[]{
                        "• Bắn tia độc phát nổ lan diện rộng (AoE 68px), diệt sạch bầy mồi/đàn cá!",
                        "• Gây sát thương nổ + rút độc DoT cực mạnh cho Boss và Cá sấu."
                },
                new Color(192, 132, 252)
        );

        drawGuideCard(g, col1X, c1Y + (c1H + gap1) * 3, colW, c1H,
                "[CHIÊU 2]", "RUNG ĐUÔI CHẤN ĐỘNG", "Phím [E] / [F]",
                new String[]{
                        "• Tạo sóng âm chấn động hình vòng tròn làm choáng diện rộng 1.8s.",
                        "• Đẩy lùi mọi kẻ thù ra xa 90px, giải vây khẩn cấp khi bị quây bắt!"
                },
                new Color(249, 115, 22)
        );

        drawGuideCard(g, col1X, c1Y + (c1H + gap1) * 4, colW, c1H,
                "[HỆ THỐNG]", "MENU TẠM DỪNG & TÙY CHỌN", "[P] / [ESC]",
                new String[]{
                        "• Phím [P] / [ESC] mở Menu: Tiếp tục, Chơi lại [R], Đổi map [M], Thoát [Q].",
                        "• Điều hướng bằng chuột hoặc phím Lên / Xuống (W / S) + [ENTER]."
                },
                new Color(148, 163, 184)
        );

        // CỘT 2: 4 THẺ HỆ SINH THÁI, TIẾN HÓA & MỤC TIÊU CHIẾN THẮNG
        int c2Y = my + 98;
        int c2H = 85;
        int gap2 = 8;

        drawGuideCard(g, col2X, c2Y, colW, c2H,
                "[HỒI PHỤC]", "HỒI MÁU & ẨN NẤP", "Tự nhiên / Cỏ / Diệt quái",
                new String[]{
                        "• Tự nhiên hồi +1 HP/s. Nấp Bụi cỏ/Hang đá/Rạn san hô tăng lên +4 HP/s.",
                        "• Tiêu diệt mục tiêu để hồi máu: Mồi (+2-3 HP), Rắn AI / Cá sấu (+12-15 HP), Boss (+40 HP).",
                        "• Nấp trong cỏ/san hô còn giúp tránh kẻ săn mồi; chui Hang Đá để dịch chuyển tức thời!"
                },
                new Color(74, 222, 128)
        );

        drawGuideCard(g, col2X, c2Y + (c2H + gap2), colW, c2H,
                "[SĂN MỒI]", "ĐỘNG VẬT NHỎ & ĐIỂM EXP", "3 ~ 12 EXP / Con",
                new String[]{
                        "• Cá nhỏ, chuột, ếch, bướm: Nhận từ 3 EXP mỗi con khi ăn hoặc bắn hạ.",
                        "• Thỏ & Cá lớn: Nhận 4 EXP. Rắn AI & Cá sấu nhận 10 ~ 12 EXP.",
                        "• Boss khổng lồ thưởng lượng lớn EXP (60-150 EXP) và 500-1800 Điểm!"
                },
                new Color(251, 191, 36)
        );

        drawGuideCard(g, col2X, c2Y + (c2H + gap2) * 2, colW, c2H,
                "[TIẾN HÓA]", "THĂNG CẤP TIẾN HÓA", "Phím 1, 2, 3 / Click",
                new String[]{
                        "• Đủ EXP lên cấp: Thân dài thêm, mở khóa chiêu và chọn 1 trong 3 thẻ:",
                        "  [1] Tăng tốc chạy (+25px/s) | [2] Tăng dame chiêu (+15 Dame)",
                        "  [3] Tăng Máu tối đa (+60 HP) và hồi phục khẩn cấp +35 Máu tức thì!"
                },
                new Color(129, 140, 248)
        );

        drawGuideCard(g, col2X, c2Y + (c2H + gap2) * 3, colW, c2H,
                "[MỤC TIÊU]", "MỤC TIÊU CHIẾN THẮNG", "Diệt 2 Boss / Màn",
                new String[]{
                        "• Màn 1 (Thảo Nguyên): Hạ gục Bạch Mao Hùng (Lv2) & Cự Trăn (Lv3).",
                        "• Màn 2 (Đại Dương): Hạ gục Bạch Tuộc Cự Đại (Lv2) & Hải Long (Lv3).",
                        "• Tiêu diệt toàn bộ Boss của màn để đoạt danh hiệu Bá Chủ Sinh Thái!"
                },
                new Color(244, 63, 94)
        );

        // NÚT ĐÓNG HƯỚNG DẪN Ở ĐÁY
        int btnW = 260;
        int btnH = 38;
        int bx = (viewW - btnW) / 2;
        int by = my + modalH - 50;
        boolean hov = isHoveredHowToPlayClose(viewW, viewH, mousePos);

        g.setColor(hov ? new Color(239, 68, 68) : new Color(30, 41, 59));
        g.fillRoundRect(bx, by, btnW, btnH, 8, 8);
        g.setColor(hov ? Color.WHITE : new Color(71, 85, 105));
        g.drawRoundRect(bx, by, btnW, btnH, 8, 8);

        g.setFont(FONT_BUTTON);
        g.setColor(hov ? Color.WHITE : new Color(226, 232, 240));
        String closeStr = "X  ĐÓNG HƯỚNG DẪN (ESC)";
        int cw = g.getFontMetrics().stringWidth(closeStr);
        g.drawString(closeStr, bx + (btnW - cw) / 2, by + 24);
    }

    private void drawGuideCard(Graphics2D g, int x, int y, int w, int h,
                              String tag, String title, String hotkey,
                              String[] lines, Color accent) {
        // Nền thẻ
        g.setColor(new Color(20, 28, 44, 215));
        g.fillRoundRect(x, y, w, h, 8, 8);
        g.setColor(new Color(51, 65, 85, 200));
        g.setStroke(new BasicStroke(1.0f));
        g.drawRoundRect(x, y, w, h, 8, 8);

        // Vạch màu chỉ định loại kỹ năng bên trái
        g.setColor(accent);
        g.fillRect(x + 2, y + 4, 3, h - 8);

        // Tag danh mục
        g.setFont(FONT_BADGE);
        g.setColor(accent);
        g.drawString(tag, x + 10, y + 18);
        int tagW = g.getFontMetrics().stringWidth(tag);

        // Tên chức năng
        g.setColor(Color.WHITE);
        g.drawString(title, x + 10 + tagW + 6, y + 18);

        // Huy hiệu phím tắt góc phải
        if (hotkey != null && !hotkey.isEmpty()) {
            g.setFont(FONT_SMALL_BOLD);
            int hkw = g.getFontMetrics().stringWidth(hotkey);
            int badgeW = hkw + 10;
            int badgeH = 18;
            int badgeX = x + w - badgeW - 8;
            int badgeY = y + 5;
            g.setColor(new Color(30, 41, 59));
            g.fillRoundRect(badgeX, badgeY, badgeW, badgeH, 4, 4);
            g.setColor(accent);
            g.drawRoundRect(badgeX, badgeY, badgeW, badgeH, 4, 4);
            g.drawString(hotkey, badgeX + 5, badgeY + 13);
        }

        // Các dòng mô tả chi tiết
        g.setFont(FONT_SMALL_REGULAR);
        int textY = y + 34;
        for (int i = 0; i < lines.length; i++) {
            g.setColor(i == 0 ? new Color(226, 232, 240) : new Color(148, 163, 184));
            g.drawString(lines[i], x + 10, textY);
            textY += 15;
        }
    }

    // ==========================================
    // HUD THEO DÕI TIẾN ĐỘ NHIỆM VỤ MÀN 1
    // ==========================================
    public void renderStage1ObjectivesHUD(Graphics2D g, int viewW, int viewH, int currentStage,
                                         boolean bearDefeated, boolean pythonDefeated, List<Boss> bosses, int playerLevel) {
        renderStageObjectivesHUD(g, viewW, viewH, currentStage, bearDefeated, pythonDefeated, false, bosses, playerLevel);
    }

    public void renderStageObjectivesHUD(Graphics2D g, int viewW, int viewH, int currentStage,
                                         boolean bearDefeated, boolean pythonDefeated, boolean swampKingDefeated,
                                         List<Boss> bosses, int playerLevel) {
        renderStageObjectivesHUD(g, viewW, viewH, currentStage, bearDefeated, pythonDefeated, false, swampKingDefeated, bosses, playerLevel);
    }

    public void renderStageObjectivesHUD(Graphics2D g, int viewW, int viewH, int currentStage,
                                         boolean bearDefeated, boolean pythonDefeated,
                                         boolean octopusDefeated, boolean swampKingDefeated,
                                         List<Boss> bosses, int playerLevel) {
        // Tự động mờ dần từ 0.5 còn 0.3 sau vài giây, hiện rõ lại khi có cập nhật nhiệm vụ
        int bossesKilled = (currentStage == 1) ?
                ((bearDefeated ? 1 : 0) + (pythonDefeated ? 1 : 0)) :
                ((octopusDefeated ? 1 : 0) + (swampKingDefeated ? 1 : 0));

        if (bossesKilled != hudLastBossesKilled) {
            hudLastBossesKilled = bossesKilled;
            hudObjectiveFadeTimer = OBJECTIVE_FADE_DELAY;
            hudObjectiveAlpha = 0.88f; // Bừng sáng khi hạ gục boss hoặc nhận tiến độ mới
        } else if (hudObjectiveFadeTimer > 0) {
            hudObjectiveFadeTimer -= 0.016;
            hudObjectiveAlpha = OBJECTIVE_BASE_ALPHA;
        } else {
            hudObjectiveAlpha = (float) Math.max(OBJECTIVE_IDLE_ALPHA, hudObjectiveAlpha - 0.005);
        }

        int boxW = 222;
        int boxH = 52;
        int bx = 14;
        int by = 64; // Nằm ngay dưới thanh HUD

        // Khung nền tối mờ bo góc
        int bgAlpha = Math.max(40, (int) (hudObjectiveAlpha * 255));
        g.setColor(new Color(15, 23, 42, bgAlpha));
        g.fillRoundRect(bx, by, boxW, boxH, 6, 6);

        Color borderAccent = (currentStage == 1) ? new Color(250, 204, 21) : new Color(56, 189, 248);
        int borderAlpha = Math.min(255, (int) (hudObjectiveAlpha * 1.6f * 255));
        g.setColor(new Color(borderAccent.getRed(), borderAccent.getGreen(), borderAccent.getBlue(), borderAlpha));
        g.drawRoundRect(bx, by, boxW, boxH, 6, 6);

        // Tiêu đề tiến độ nhiệm vụ
        String headerTitle = "★ MỤC TIÊU: [" + bossesKilled + "/2] BOSS";
        drawOutlinedPixelString(g, headerTitle, bx + 8, by + 13, borderAccent, HUD_TEXT_OUTLINE, FONT_PIXEL_LABEL, FONT_BADGE);

        if (currentStage == 1) {
            boolean bearAlive = false;
            boolean pythonAlive = false;
            for (Boss b : bosses) {
                if (b.isAlive()) {
                    if (b.getType() == Boss.Type.BEAR) bearAlive = true;
                    if (b.getType() == Boss.Type.GIANT_PYTHON) pythonAlive = true;
                }
            }

            // Dòng Boss 1: Gấu Bạch Mao
            String b1Str;
            Color b1Col;
            if (bearDefeated) {
                b1Str = "✓ [Bạch Mao Hùng]: ĐÃ HẠ GỤC";
                b1Col = new Color(74, 222, 128);
            } else if (bearAlive) {
                b1Str = "⚔ [Bạch Mao Hùng]: GIAO TRANH!";
                b1Col = new Color(239, 68, 68);
            } else {
                b1Str = "○ [Bạch Mao Hùng]: (Chờ Lv.2)";
                b1Col = new Color(148, 163, 184);
            }
            drawOutlinedPixelString(g, b1Str, bx + 8, by + 27, b1Col, HUD_TEXT_OUTLINE, FONT_PIXEL_SMALL, FONT_SMALL_BOLD);

            // Dòng Boss 2: Cự Trăn
            String b2Str;
            Color b2Col;
            if (pythonDefeated) {
                b2Str = "✓ [Thái Cổ Cự Trăn]: ĐÃ HẠ GỤC";
                b2Col = new Color(74, 222, 128);
            } else if (pythonAlive) {
                b2Str = "⚔ [Thái Cổ Cự Trăn]: GIAO TRANH!";
                b2Col = new Color(239, 68, 68);
            } else {
                b2Str = "○ [Thái Cổ Cự Trăn]: (Chờ Lv.3)";
                b2Col = new Color(148, 163, 184);
            }
            drawOutlinedPixelString(g, b2Str, bx + 8, by + 41, b2Col, HUD_TEXT_OUTLINE, FONT_PIXEL_SMALL, FONT_SMALL_BOLD);

        } else if (currentStage == 2) {
            boolean octopusAlive = false;
            boolean swampKingAlive = false;
            for (Boss b : bosses) {
                if (b.isAlive()) {
                    if (b.getType() == Boss.Type.OCTOPUS) octopusAlive = true;
                    if (b.getType() == Boss.Type.SWAMP_KING) swampKingAlive = true;
                }
            }

            // Dòng Boss 1: Bạch Tuộc Cự Đại
            String oStr;
            Color oCol;
            if (octopusDefeated) {
                oStr = "✓ [Bạch Tuộc]: ĐÃ HẠ GỤC";
                oCol = new Color(74, 222, 128);
            } else if (octopusAlive) {
                oStr = "⚔ [Bạch Tuộc]: GIAO TRANH!";
                oCol = new Color(239, 68, 68);
            } else {
                oStr = "○ [Bạch Tuộc]: (Chờ Lv.2)";
                oCol = new Color(148, 163, 184);
            }
            drawOutlinedPixelString(g, oStr, bx + 8, by + 27, oCol, HUD_TEXT_OUTLINE, FONT_PIXEL_SMALL, FONT_SMALL_BOLD);

            // Dòng Boss 2: Hải Long Cự Thần
            String sStr;
            Color sCol;
            if (swampKingDefeated) {
                sStr = "✓ [Hải Long]: ĐÃ HẠ GỤC";
                sCol = new Color(74, 222, 128);
            } else if (swampKingAlive) {
                sStr = "⚔ [Hải Long]: GIAO TRANH!";
                sCol = new Color(239, 68, 68);
            } else {
                sStr = "○ [Hải Long]: (Chờ Lv.3)";
                sCol = new Color(148, 163, 184);
            }
            drawOutlinedPixelString(g, sStr, bx + 8, by + 41, sCol, HUD_TEXT_OUTLINE, FONT_PIXEL_SMALL, FONT_SMALL_BOLD);
        }
    }

    // ==========================================
    // MÀN HÌNH VICTORY (CHIẾN THẮNG)
    // ==========================================
    public int getHoveredVictoryButton(int viewW, int viewH, Point mousePos) {
        if (mousePos == null) return 0;
        int btnW = 160;
        int btnH = 44;
        int gap = 16;
        int totalW = btnW * 3 + gap * 2;
        int startX = (viewW - totalW) / 2;
        int startY = viewH / 2 + 130;

        for (int i = 0; i < 3; i++) {
            Rectangle r = new Rectangle(startX + i * (btnW + gap), startY, btnW, btnH);
            if (r.contains(mousePos)) {
                return i + 1; // 1: Chơi lại, 2: Chọn màn khác, 3: Menu chính
            }
        }
        return 0;
    }

    public void renderVictoryScreen(Graphics2D g, int viewW, int viewH, Point mousePos,
                                    double playTime, int score, int level, int kills, double animTime) {
        renderVictoryScreen(g, viewW, viewH, mousePos, playTime, score, level, kills, animTime, 1);
    }

    public void renderVictoryScreen(Graphics2D g, int viewW, int viewH, Point mousePos,
                                    double playTime, int score, int level, int kills, double animTime, int stageId) {
        // Phủ mờ toàn màn hình bằng tông vàng kim sang trọng
        g.setColor(new Color(10, 20, 15, 230));
        g.fillRect(0, 0, viewW, viewH);

        // Hiệu ứng pháo hoa tia sáng chiến thắng
        for (int i = 0; i < 36; i++) {
            double angle = (i * Math.PI * 2.0 / 36) + animTime * 0.5;
            double dist = 140 + Math.sin(animTime * 3.0 + i) * 60;
            int px = (int)(viewW / 2 + Math.cos(angle) * dist);
            int py = (int)(viewH / 2 - 80 + Math.sin(angle) * dist);
            g.setColor(new Color(250, 204, 21, 140));
            g.fillOval(px - 3, py - 3, 6, 6);
        }

        // KHUNG THÔNG BÁO CHIẾN THẮNG
        int boxW = 540;
        int boxH = 410;
        int bx = (viewW - boxW) / 2;
        int by = (viewH - boxH) / 2 - 20;

        // Nền hộp vinh quang
        g.setColor(new Color(15, 23, 42, 245));
        g.fillRoundRect(bx, by, boxW, boxH, 18, 18);
        g.setColor(new Color(250, 204, 21));
        g.setStroke(new BasicStroke(3.0f));
        g.drawRoundRect(bx, by, boxW, boxH, 18, 18);

        // Tiêu đề Hoàng Kim
        g.setFont(FONT_TITLE_LARGE);
        String vicStr = "★ VICTORY ★";
        int vw = g.getFontMetrics().stringWidth(vicStr);
        g.setColor(new Color(250, 204, 21));
        g.drawString(vicStr, (viewW - vw) / 2, by + 55);

        g.setFont(FONT_TITLE_SECTION);
        String sub = (stageId == 2) ? "BÁ CHỦ MÀN 2: ĐẠI DƯƠNG XANH THẲM!" : "BÁ CHỦ MÀN 1: THẢO NGUYÊN BÁT NGÁT!";
        int sw = g.getFontMetrics().stringWidth(sub);
        g.setColor(new Color(74, 222, 128));
        g.drawString(sub, (viewW - sw) / 2, by + 90);

        g.setFont(FONT_BODY);
        String sub2 = (stageId == 2)
                ? "Đã chinh phục 2 Siêu Boss (Bạch Tuộc Cự Đại & Hải Long Cự Thần)!"
                : "Đã quét sạch toàn bộ Siêu Boss (Bạch Mao Hùng & Cự Trăn)";
        int s2w = g.getFontMetrics().stringWidth(sub2);
        g.setColor(new Color(203, 213, 225));
        g.drawString(sub2, (viewW - s2w) / 2, by + 115);

        // Đường gạch ngang
        g.setColor(new Color(51, 65, 85));
        g.drawLine(bx + 25, by + 130, bx + boxW - 25, by + 130);

        // BẢNG THỐNG KÊ CHIẾN TÍCH
        int statY = by + 162;
        int col1 = bx + 45;
        int col2 = bx + 300;

        int minutes = (int)(playTime / 60);
        int seconds = (int)(playTime % 60);
        String timeStr = String.format("%02d:%02d", minutes, seconds);

        drawStatRow(g, col1, statY, "• Thời Gian Vượt Màn:", timeStr, new Color(56, 189, 248));
        drawStatRow(g, col2, statY, "★ Tổng Điểm Ghi Được:", score + " PTS", new Color(250, 204, 21));

        statY += 46;
        drawStatRow(g, col1, statY, "• Cấp Tiến Hóa Đạt:", "Cấp " + level, new Color(74, 222, 128));
        drawStatRow(g, col2, statY, "• Số Kẻ Địch Đã Diệt:", kills + " Kẻ Địch", new Color(244, 63, 94));

        statY += 46;
        if (stageId == 2) {
            drawStatRow(g, col1, statY, "• Boss 1: Bạch Tuộc Cự Đại", "ĐÃ HẠ GỤC ✓", new Color(74, 222, 128));
            drawStatRow(g, col2, statY, "• Boss 2: Hải Long Cự Thần", "ĐÃ HẠ GỤC ✓", new Color(74, 222, 128));
        } else {
            drawStatRow(g, col1, statY, "• Boss 1: Bạch Mao Hùng", "ĐÃ HẠ GỤC ✓", new Color(74, 222, 128));
            drawStatRow(g, col2, statY, "• Boss 2: Thái Cổ Cự Trăn", "ĐÃ HẠ GỤC ✓", new Color(74, 222, 128));
        }

        // 3 NÚT TƯƠNG TÁC
        int hovered = getHoveredVictoryButton(viewW, viewH, mousePos);
        int btnW = 160;
        int btnH = 44;
        int gap = 16;
        int totalW = btnW * 3 + gap * 2;
        int startX = (viewW - totalW) / 2;
        int startY = viewH / 2 + 130;

        String replayStr = (stageId == 2) ? "▶  CHƠI LẠI MÀN 2" : "▶  CHƠI LẠI MÀN 1";
        drawActionButton(g, startX, startY, btnW, btnH, replayStr, new Color(34, 197, 94), hovered == 1);
        drawActionButton(g, startX + btnW + gap, startY, btnW, btnH, "✦  CHỌN MÀN KHÁC", new Color(56, 189, 248), hovered == 2);
        drawActionButton(g, startX + (btnW + gap) * 2, startY, btnW, btnH, "◀  MENU CHÍNH", new Color(250, 204, 21), hovered == 3);
    }

    // ==========================================
    // MÀN HÌNH DEFEAT (THẤT BẠI)
    // ==========================================
    public int getHoveredDefeatButton(int viewW, int viewH, Point mousePos) {
        if (mousePos == null) return 0;
        int btnW = 160;
        int btnH = 44;
        int gap = 16;
        int totalW = btnW * 3 + gap * 2;
        int startX = (viewW - totalW) / 2;
        int startY = viewH / 2 + 130;

        for (int i = 0; i < 3; i++) {
            Rectangle r = new Rectangle(startX + i * (btnW + gap), startY, btnW, btnH);
            if (r.contains(mousePos)) {
                return i + 1; // 1: Thử lại, 2: Chọn màn khác, 3: Menu chính
            }
        }
        return 0;
    }

    public void renderDefeatScreen(Graphics2D g, int viewW, int viewH, Point mousePos,
                                   double playTime, int score, int level, boolean bearDefeated, boolean pythonDefeated, double animTime) {
        renderDefeatScreen(g, viewW, viewH, mousePos, playTime, score, level, bearDefeated, pythonDefeated, false, false, animTime, 1);
    }

    public void renderDefeatScreen(Graphics2D g, int viewW, int viewH, Point mousePos,
                                   double playTime, int score, int level, boolean bearDefeated, boolean pythonDefeated,
                                   boolean swampKingDefeated, double animTime, int stageId) {
        renderDefeatScreen(g, viewW, viewH, mousePos, playTime, score, level, bearDefeated, pythonDefeated, false, swampKingDefeated, animTime, stageId);
    }

    public void renderDefeatScreen(Graphics2D g, int viewW, int viewH, Point mousePos,
                                   double playTime, int score, int level, boolean bearDefeated, boolean pythonDefeated,
                                   boolean octopusDefeated, boolean swampKingDefeated, double animTime, int stageId) {
        // Phủ mờ nền tông đỏ thẫm bi tráng
        g.setColor(new Color(25, 10, 10, 235));
        g.fillRect(0, 0, viewW, viewH);

        // KHUNG THÔNG BÁO TỬ TRẬN
        int boxW = 540;
        int boxH = 410;
        int bx = (viewW - boxW) / 2;
        int by = (viewH - boxH) / 2 - 20;

        g.setColor(new Color(20, 15, 20, 245));
        g.fillRoundRect(bx, by, boxW, boxH, 18, 18);
        g.setColor(new Color(220, 38, 38));
        g.setStroke(new BasicStroke(3.0f));
        g.drawRoundRect(bx, by, boxW, boxH, 18, 18);

        // Tiêu đề Đỏ Máu
        g.setFont(FONT_TITLE_LARGE);
        String defStr = "☠ DEFEAT ☠";
        int dw = g.getFontMetrics().stringWidth(defStr);
        g.setColor(new Color(239, 68, 68));
        g.drawString(defStr, (viewW - dw) / 2, by + 55);

        g.setFont(FONT_TITLE_SECTION);
        String sub = (stageId == 2) ? "BẠN ĐÃ BỊ CHÌM VÀO ĐÁY ĐẠI DƯƠNG!" : "BẠN ĐÃ TỬ TRẬN TRÊN THẢO NGUYÊN!";
        int sw = g.getFontMetrics().stringWidth(sub);
        g.setColor(new Color(248, 113, 113));
        g.drawString(sub, (viewW - sw) / 2, by + 90);

        g.setFont(FONT_BODY);
        String sub2 = (stageId == 2)
                ? "Vực thẳm biển sâu luôn chứa đựng hiểm nguy - Hãy thử lại và phục thù!"
                : "Sinh tồn hoang dã luôn tiềm ẩn rủi ro - Hãy thử lại và phục thù!";
        int s2w = g.getFontMetrics().stringWidth(sub2);
        g.setColor(new Color(203, 213, 225));
        g.drawString(sub2, (viewW - s2w) / 2, by + 115);

        // Đường gạch ngang
        g.setColor(new Color(51, 65, 85));
        g.drawLine(bx + 25, by + 130, bx + boxW - 25, by + 130);

        // BẢNG THỐNG KÊ TIẾN TRÌNH
        int statY = by + 162;
        int col1 = bx + 45;
        int col2 = bx + 300;

        int minutes = (int)(playTime / 60);
        int seconds = (int)(playTime % 60);
        String timeStr = String.format("%02d:%02d", minutes, seconds);

        drawStatRow(g, col1, statY, "• Thời Gian Sống Sót:", timeStr, new Color(56, 189, 248));
        drawStatRow(g, col2, statY, "★ Điểm Số Đạt Được:", score + " PTS", new Color(250, 204, 21));

        statY += 46;
        drawStatRow(g, col1, statY, "• Cấp Tiến Hóa Cuối:", "Cấp " + level, new Color(74, 222, 128));

        if (stageId == 2) {
            int bossCount = (octopusDefeated ? 1 : 0) + (swampKingDefeated ? 1 : 0);
            drawStatRow(g, col2, statY, "• Tiến Độ Diệt Boss:", bossCount + " / 2 Boss", new Color(248, 113, 113));
            statY += 46;
            drawStatRow(g, col1, statY, "• Bạch Tuộc Cự Đại:", octopusDefeated ? "ĐÃ DIỆT ✓" : "CHƯA HẠ ✕",
                    octopusDefeated ? new Color(74, 222, 128) : new Color(239, 68, 68));
            drawStatRow(g, col2, statY, "• Hải Long Cự Thần:", swampKingDefeated ? "ĐÃ DIỆT ✓" : "CHƯA HẠ ✕",
                    swampKingDefeated ? new Color(74, 222, 128) : new Color(239, 68, 68));
        } else {
            int bossCount = (bearDefeated ? 1 : 0) + (pythonDefeated ? 1 : 0);
            drawStatRow(g, col2, statY, "• Tiến Độ Diệt Boss:", bossCount + " / 2 Boss", new Color(248, 113, 113));
            statY += 46;
            drawStatRow(g, col1, statY, "• Bạch Mao Hùng:", bearDefeated ? "ĐÃ DIỆT ✓" : "CHƯA HẠ ✕", bearDefeated ? new Color(74, 222, 128) : new Color(239, 68, 68));
            drawStatRow(g, col2, statY, "• Thái Cổ Cự Trăn:", pythonDefeated ? "ĐÃ DIỆT ✓" : "CHƯA HẠ ✕", pythonDefeated ? new Color(74, 222, 128) : new Color(239, 68, 68));
        }

        // 3 NÚT TƯƠNG TÁC
        int hovered = getHoveredDefeatButton(viewW, viewH, mousePos);
        int btnW = 160;
        int btnH = 44;
        int gap = 16;
        int totalW = btnW * 3 + gap * 2;
        int startX = (viewW - totalW) / 2;
        int startY = viewH / 2 + 130;

        String retryStr = (stageId == 2) ? "▶  THỬ LẠI MÀN 2" : "▶  THỬ LẠI MÀN 1";
        drawActionButton(g, startX, startY, btnW, btnH, retryStr, new Color(239, 68, 68), hovered == 1);
        drawActionButton(g, startX + btnW + gap, startY, btnW, btnH, "✦  CHỌN MÀN KHÁC", new Color(56, 189, 248), hovered == 2);
        drawActionButton(g, startX + (btnW + gap) * 2, startY, btnW, btnH, "◀  MENU CHÍNH", new Color(250, 204, 21), hovered == 3);
    }

    private void drawStatRow(Graphics2D g, int x, int y, String label, String value, Color valColor) {
        g.setFont(FONT_BODY);
        g.setColor(new Color(148, 163, 184));
        g.drawString(label, x, y);

        g.setFont(FONT_TITLE_CARD);
        g.setColor(valColor);
        g.drawString(value, x, y + 18);
    }

    private void drawActionButton(Graphics2D g, int x, int y, int w, int h, String text, Color accent, boolean hovered) {
        int drawY = hovered ? y - 3 : y;

        g.setColor(new Color(0, 0, 0, hovered ? 180 : 120));
        g.fillRoundRect(x + 2, drawY + 4, w, h, 10, 10);

        g.setColor(hovered ? new Color(30, 41, 59) : new Color(15, 23, 42));
        g.fillRoundRect(x, drawY, w, h, 10, 10);

        g.setColor(hovered ? accent : new Color(71, 85, 105));
        g.setStroke(new BasicStroke(hovered ? 2.5f : 1.5f));
        g.drawRoundRect(x, drawY, w, h, 10, 10);

        g.setFont(FONT_BUTTON);
        g.setColor(hovered ? Color.WHITE : new Color(226, 232, 240));
        int tw = g.getFontMetrics().stringWidth(text);
        g.drawString(text, x + (w - tw) / 2, drawY + h / 2 + 5);
    }

    // ==========================================
    // MENU TẠM DỪNG (PAUSE MENU MODAL)
    // ==========================================
    public static final int PAUSE_BTN_RESUME = 1;
    public static final int PAUSE_BTN_RESTART = 2;
    public static final int PAUSE_BTN_CHANGE_MAP = 3;
    public static final int PAUSE_BTN_EXIT = 4;

    public int getHoveredPauseButton(int viewW, int viewH, Point mousePos) {
        if (mousePos == null) return 0;
        int boxW = 460;
        int boxH = 430;
        int bx = (viewW - boxW) / 2;
        int by = (viewH - boxH) / 2;

        int btnW = 380;
        int btnH = 48;
        int gap = 14;
        int btnX = bx + (boxW - btnW) / 2;
        int startY = by + 120;

        for (int i = 0; i < 4; i++) {
            Rectangle r = new Rectangle(btnX, startY + i * (btnH + gap), btnW, btnH);
            if (r.contains(mousePos)) {
                return i + 1; // 1: Resume, 2: Restart, 3: Change Map, 4: Exit
            }
        }
        return 0;
    }

    public void renderPauseMenu(Graphics2D g, int viewW, int viewH, Point mousePos,
                                int selectedIndex, int currentStage, double playTime, int score, int level) {
        // 1. Phủ màn đen mờ toàn màn hình
        g.setColor(new Color(8, 12, 22, 215));
        g.fillRect(0, 0, viewW, viewH);

        // 2. Kích thước hộp hội thoại
        int boxW = 460;
        int boxH = 430;
        int bx = (viewW - boxW) / 2;
        int by = (viewH - boxH) / 2;

        // Đổ bóng hộp
        g.setColor(new Color(0, 0, 0, 210));
        g.fillRoundRect(bx + 4, by + 8, boxW, boxH, 18, 18);

        // Nền hộp Modal
        g.setColor(new Color(15, 23, 42, 250));
        g.fillRoundRect(bx, by, boxW, boxH, 18, 18);

        // Viền phát sáng theo màn
        Color themeColor = (currentStage == 2) ? new Color(56, 189, 248) : new Color(74, 222, 128);
        g.setColor(themeColor);
        g.setStroke(new BasicStroke(2.5f));
        g.drawRoundRect(bx, by, boxW, boxH, 18, 18);

        // 3. Tiêu đề: Vẽ biểu tượng pause bằng hai vạch pixel vàng kim sắc nét
        g.setFont(FONT_TITLE_MODAL);
        String title = "TRÒ CHƠI TẠM DỪNG";
        int tw = g.getFontMetrics().stringWidth(title);
        int titleTotalW = tw + 26;
        int titleStartX = bx + (boxW - titleTotalW) / 2;

        // Biểu tượng pause pixel-art 2 thanh dọc
        g.setColor(new Color(250, 204, 21));
        g.fillRect(titleStartX, by + 24, 6, 18);
        g.fillRect(titleStartX + 9, by + 24, 6, 18);

        // Chữ tiêu đề
        g.drawString(title, titleStartX + 26, by + 40);

        // Huy hiệu Màn chơi hiện tại
        String stageName = (currentStage == 2)
                ? "[ MÀN 2: ĐẠI DƯƠNG XANH THẲM ]"
                : "[ MÀN 1: THẢO NGUYÊN BÁT NGÁT ]";
        g.setFont(FONT_HUD_BOLD);
        int snw = g.getFontMetrics().stringWidth(stageName);
        g.setColor(themeColor);
        g.drawString(stageName, bx + (boxW - snw) / 2, by + 64);

        // Dòng thông số ván chơi (Thời gian, Điểm, Cấp)
        int minutes = (int)(playTime / 60);
        int seconds = (int)(playTime % 60);
        String stats = String.format("THỜI GIAN: %02d:%02d  |  ĐIỂM: %d PTS  |  CẤP: %d", minutes, seconds, score, level);
        g.setFont(FONT_BODY);
        int stw = g.getFontMetrics().stringWidth(stats);
        g.setColor(new Color(148, 163, 184));
        g.drawString(stats, bx + (boxW - stw) / 2, by + 88);

        // Đường gạch ngang phân cách
        g.setColor(new Color(51, 65, 85));
        g.drawLine(bx + 24, by + 104, bx + boxW - 24, by + 104);

        // 4. Bốn nút tương tác
        int mouseHovered = getHoveredPauseButton(viewW, viewH, mousePos);
        int btnW = 380;
        int btnH = 48;
        int gap = 14;
        int btnX = bx + (boxW - btnW) / 2;
        int startY = by + 120;

        // Button 1: TIẾP TỤC
        boolean act1 = (mouseHovered == PAUSE_BTN_RESUME) || (mouseHovered == 0 && selectedIndex == 0);
        drawPauseButton(g, btnX, startY, btnW, btnH,
                "TIẾP TỤC TRẬN ĐẤU", "ESC / P",
                new Color(34, 197, 94), act1);

        // Button 2: CHƠI LẠI
        boolean act2 = (mouseHovered == PAUSE_BTN_RESTART) || (mouseHovered == 0 && selectedIndex == 1);
        drawPauseButton(g, btnX, startY + (btnH + gap), btnW, btnH,
                "CHƠI LẠI MÀN NÀY", "R",
                new Color(245, 158, 11), act2);

        // Button 3: ĐỔI MÀN CHƠI (MAP)
        boolean act3 = (mouseHovered == PAUSE_BTN_CHANGE_MAP) || (mouseHovered == 0 && selectedIndex == 2);
        drawPauseButton(g, btnX, startY + (btnH + gap) * 2, btnW, btnH,
                "ĐỔI MÀN CHƠI (MAP)", "M",
                new Color(56, 189, 248), act3);

        // Button 4: THOÁT RA MENU CHÍNH
        boolean act4 = (mouseHovered == PAUSE_BTN_EXIT) || (mouseHovered == 0 && selectedIndex == 3);
        drawPauseButton(g, btnX, startY + (btnH + gap) * 3, btnW, btnH,
                "THOÁT RA MENU CHÍNH", "Q",
                new Color(244, 63, 94), act4);

        // 5. Gợi ý điều khiển ở đáy hộp
        g.setFont(FONT_SMALL_REGULAR);
        g.setColor(new Color(100, 116, 139));
        String tip = "Click chuột hoặc dùng phím Lên / Xuống (W / S) + [ENTER] để chọn";
        int tipW = g.getFontMetrics().stringWidth(tip);
        g.drawString(tip, bx + (boxW - tipW) / 2, by + boxH - 18);
    }

    private void drawPauseButton(Graphics2D g, int x, int y, int w, int h,
                                 String label, String hotkey, Color accent, boolean isActive) {
        int drawY = isActive ? y - 2 : y;

        // Đổ bóng nút
        g.setColor(new Color(0, 0, 0, isActive ? 190 : 130));
        g.fillRoundRect(x + 2, drawY + 5, w, h, 10, 10);

        // Nền nút
        g.setColor(isActive ? new Color(30, 41, 59, 252) : new Color(15, 23, 42, 235));
        g.fillRoundRect(x, drawY, w, h, 10, 10);

        // Viền nút
        g.setColor(isActive ? accent : new Color(71, 85, 105));
        g.setStroke(new BasicStroke(isActive ? 2.5f : 1.2f));
        g.drawRoundRect(x, drawY, w, h, 10, 10);

        // Chấm tròn hoặc icon mũi tên bên trái
        if (isActive) {
            g.setColor(accent);
            g.fillPolygon(
                    new int[]{x + 14, x + 23, x + 14},
                    new int[]{drawY + h / 2 - 6, drawY + h / 2, drawY + h / 2 + 6},
                    3
            );
        } else {
            g.setColor(new Color(100, 116, 139));
            g.fillOval(x + 15, drawY + h / 2 - 4, 8, 8);
        }

        // Nhãn chính
        g.setFont(FONT_BUTTON);
        g.setColor(isActive ? Color.WHITE : new Color(226, 232, 240));
        g.drawString(label, x + 32, drawY + h / 2 + 5);

        // Huy hiệu phím tắt bên phải
        if (hotkey != null && !hotkey.isEmpty()) {
            g.setFont(FONT_BADGE);
            int hkw = g.getFontMetrics().stringWidth(hotkey);
            int badgeW = hkw + 14;
            int badgeH = 22;
            int badgeX = x + w - badgeW - 12;
            int badgeY = drawY + (h - badgeH) / 2;

            g.setColor(isActive ? accent : new Color(51, 65, 85));
            g.fillRoundRect(badgeX, badgeY, badgeW, badgeH, 6, 6);
            g.setColor(isActive ? Color.BLACK : new Color(203, 213, 225));
            int thw = g.getFontMetrics().stringWidth(hotkey);
            g.drawString(hotkey, badgeX + (badgeW - thw) / 2, badgeY + 15);
        }
    }

    // =========================================================================
    // ĐÀN CÁ NHỎ HẬU CẢNH BIỂN SÂU (MAP 2 AMBIENT FISH SCHOOLS)
    // 3 Đàn cá nhỏ bơi theo đội hình, bóng mờ (alpha ~0.20), không va chạm, 0 GC
    // =========================================================================
    private void renderBackgroundFishSchools(Graphics2D g, int viewW, int viewH, double time) {
        Composite oldComp = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.22f));

        // Đàn 1: 5 con bơi từ trái sang phải ở tầng nước trên (y ~ 190)
        double school1X = (time * 52.0) % (viewW + 350) - 150;
        double school1Y = 190.0 + Math.sin(time * 0.7) * 20.0;
        g.setColor(new Color(8, 56, 78));
        for (int i = 0; i < 5; i++) {
            double fx = school1X - i * 14 + (i % 2) * 5;
            double fy = school1Y + (i - 2) * 9 + Math.sin(time * 3.5 + i) * 3.0;
            if (fx >= -20 && fx <= viewW + 20) {
                // Thân cá thoi dẹt 8x4 px hướng sang phải
                g.fillOval((int) fx, (int) fy, 8, 4);
                // Vây đuôi ve vẩy
                double wag = Math.sin(time * 12.0 + i) * 2.0;
                g.drawLine((int) fx - 1, (int) fy + 2, (int) fx - 3, (int) (fy + 2 + wag));
            }
        }

        // Đàn 2: 4 con bơi từ phải sang trái ở tầng nước giữa (y ~ 380)
        double school2X = (viewW + 200) - (time * 40.0) % (viewW + 350);
        double school2Y = 380.0 + Math.cos(time * 0.6) * 25.0;
        g.setColor(new Color(6, 44, 62));
        for (int i = 0; i < 4; i++) {
            double fx = school2X + i * 16 - (i % 2) * 6;
            double fy = school2Y + (i - 1.5) * 11 + Math.sin(time * 3.0 + i) * 3.0;
            if (fx >= -20 && fx <= viewW + 20) {
                // Thân cá hướng sang trái
                g.fillOval((int) fx, (int) fy, 8, 4);
                double wag = Math.sin(time * 11.0 + i) * 2.0;
                g.drawLine((int) fx + 9, (int) fy + 2, (int) fx + 11, (int) (fy + 2 + wag));
            }
        }

        // Đàn 3: 6 con bơi nhanh tầng sâu (y ~ 540)
        double school3X = (time * 65.0 + 220.0) % (viewW + 400) - 150;
        double school3Y = 540.0 + Math.sin(time * 0.85 + 1.0) * 18.0;
        g.setColor(new Color(4, 32, 48));
        for (int i = 0; i < 6; i++) {
            double fx = school3X - i * 12 + (i % 3) * 4;
            double fy = school3Y + (i - 2.5) * 8 + Math.cos(time * 4.0 + i) * 2.5;
            if (fx >= -20 && fx <= viewW + 20) {
                g.fillOval((int) fx, (int) fy, 7, 3);
                double wag = Math.sin(time * 14.0 + i) * 1.8;
                g.drawLine((int) fx - 1, (int) fy + 1, (int) fx - 3, (int) (fy + 1 + wag));
            }
        }

        g.setComposite(oldComp);
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
}

