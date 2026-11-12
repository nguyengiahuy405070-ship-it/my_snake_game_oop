package com.snakearena.core;

import java.awt.Color;

/**
 * Bảng hằng số cấu hình toàn cục cho Snake Arena (Java Edition).
 * Hỗ trợ hệ thống Điểm Máu (HP Points), Boss Khổng Lồ, Bụi Rậm & Hang Đá mở rộng,
 * giảm mạnh mật độ bẫy và nâng độ khó Đại Bàng săn mồi.
 */
public class Config {
    // 1. Cấu hình Thế giới & Chunk
    public static class World {
        public static final int CHUNK_SIZE = 512;          // Kích thước mỗi chunk (pixel)
        public static final int BUFFER_CHUNKS = 1;         // Số chunk đệm ngoài tầm nhìn
        public static final int MAX_CACHED_CHUNKS = 40;    // Số chunk tối đa trong RAM
        public static final int SEED = 133742;             // Seed thế giới thủ tục
        public static final double TREE_DENSITY = 0.024;   // Mật độ cây cối (có thể đi xuyên qua)
        public static final double ROCK_DENSITY = 0.014;   // Mật độ đá tảng
        public static final double WALL_DENSITY = 0.018;   // Mật độ Bức Tường Đá (vật cản duy nhất cản đường rắn)
        public static final double MOUNTAIN_DENSITY = 0.0; // Đã loại bỏ núi đá
        public static final double BUSH_DENSITY = 0.040;   // Mật độ bụi cỏ lau mở rộng
        public static final double CAVE_DENSITY = 0.004;   // Giảm mật độ hang đá ẩn nấp (hiếm và đặc sắc hơn)
        public static final double TRAP_DENSITY = 0.0025;  // Mật độ bẫy thú
        public static final double WATER_THRESHOLD = 0.62; // Ngưỡng hồ nước

        public static final double PROP_TREE_RADIUS = 24;
        public static final double PROP_ROCK_RADIUS = 16;
        public static final double PROP_WALL_RADIUS = 22;  // Bán kính cản trở của Bức Tường
        public static final double PROP_BUSH_RADIUS = 88;  // Bụi cỏ mở rộng to lớn thêm vài block (rộng rãi ẩn mình)
        public static final double PROP_CAVE_RADIUS = 46;  // Hang đá rộng lớn
        public static final double PROP_TRAP_RADIUS = 16;
    }

    // 2. Cấu hình Camera mượt mà (Lerp)
    public static class CameraConfig {
        public static final double LERP_SPEED = 0.10;
        public static final double LEAD_DISTANCE = 40.0;
        public static final double SHAKE_DECAY = 0.90;
    }

    // 3. Cấu hình Rắn & Điểm Máu (HP Points), Mana & 5 Cấp Độ Tiến Hóa
    public static class SnakeConfig {
        public static final double BASE_SPEED = 135.0;     // Tốc độ di chuyển cơ bản (px/s)
        public static final double SPRINT_SPEED = 225.0;   // Tốc độ chạy nhanh
        public static final double DASH_SPEED = 275.0;     // Tốc độ lướt (Space)
        public static final double SEGMENT_DIST = 16.0;    // Khoảng cách đốt thân
        public static final double TURN_SPEED = 3.8;       // Tốc độ quay đầu (rad/s)
        public static final int INITIAL_LENGTH = 6;
        public static final int MAX_SNAKE_LENGTH = 38;     // Trần chiều dài tối đa phù hợp của thân rắn
        public static final double DASH_DURATION = 1.2;
        public static final double DASH_COOLDOWN = 4.5;
        public static final double WAVE_AMPLITUDE = 4.0;
        public static final double WAVE_FREQUENCY = 6.0;

        // Điểm Máu Số Học (Numerical HP Points) 5 Cấp Độ
        public static final int HP_LV1 = 100;              // Cấp 1: Emerald Garter
        public static final int HP_LV2 = 175;              // Cấp 2: Shadow Viper
        public static final int HP_LV3 = 260;              // Cấp 3: Apex Titan Boa
        public static final int HP_LV4 = 360;              // Cấp 4: Crimson Fire Drake (+100 HP)
        public static final int HP_LV5 = 480;              // Cấp 5: Celestial Leviathan (+120 HP)
        
        // Cơ chế Hồi máu liên tục (HP Regen per second)
        public static final double BASE_HEAL_PER_SEC = 1.0;    // Hồi máu tự nhiên ngoài trời
        public static final double HIDING_HEAL_PER_SEC = 4.0;  // Hồi máu trong bụi rậm/rạn san hô/hang đá
        public static final double HEAL_DELAY_AFTER_HIT = 0.0; // Số giây tạm ngưng hồi sau khi nhận damage (0 = tắt)
        public static final int HIDING_MIN_HEAL_PER_SEC = 4;
        public static final int HIDING_MAX_HEAL_PER_SEC = 4;

        // Hồi máu khi tiêu diệt mục tiêu (Heal On Kill)
        public static final int KILL_HEAL_PREY_NORMAL_FIXED = 3;
        public static final double KILL_HEAL_PREY_NORMAL_RATIO = 0.0;
        public static final int KILL_HEAL_PREY_BUTTERFLY_FIXED = 2;
        public static final double KILL_HEAL_PREY_BUTTERFLY_RATIO = 0.0;

        public static final int KILL_HEAL_AI_SNAKE_FIXED = 12;
        public static final double KILL_HEAL_AI_SNAKE_RATIO = 0.06;

        public static final int KILL_HEAL_CROCODILE_FIXED = 15;
        public static final double KILL_HEAL_CROCODILE_RATIO = 0.07;

        public static final int KILL_HEAL_BOSS_FIXED = 40;
        public static final double KILL_HEAL_BOSS_RATIO = 0.25;

        public static int calcKillHeal(int fixedAmt, double ratio, int maxHp) {
            int fromRatio = (ratio > 0.0) ? (int) Math.round(ratio * maxHp) : 0;
            return Math.max(fixedAmt, fromRatio);
        }

        // Máu cơ bản của rắn AI đối thủ (tăng từ 60 lên 85 HP)
        public static final int AI_SNAKE_MAX_HP = 85;

        // Thanh Mana & Năng Lượng Tối Đa 5 Cấp Độ
        public static final double MANA_LV1 = 100.0;       // Mana Cấp 1
        public static final double MANA_LV2 = 160.0;       // Mana Cấp 2
        public static final double MANA_LV3 = 240.0;       // Mana Cấp 3
        public static final double MANA_LV4 = 320.0;       // Mana Cấp 4
        public static final double MANA_LV5 = 420.0;       // Mana Cấp 5
        public static final double MAX_MANA = MANA_LV1;
        public static final double HIDING_MANA_RECOVERY = 25.0; // Hồi Mana trong bụi/hang
        public static final double BASE_MANA_RECOVERY = 3.0;   // Hồi Mana tự nhiên ngoài trời

        // Mốc Điểm Kinh Nghiệm (EXP) để lên 5 cấp độ
        public static final int EXP_TO_LV2 = 35;           // Mốc lên Cấp 2 (Mở khóa Boss Gấu)
        public static final int EXP_TO_LV3 = 110;          // Mốc lên Cấp 3 (Mở khóa Boss Cự Trăn)
        public static final int EXP_TO_LV4 = 210;          // Mốc lên Cấp 4 (Mở khóa Hải Long Cự Thần / Boss Màn 2)
        public static final int EXP_TO_LV5 = 340;          // Mốc lên Cấp 5 (Mở khóa Boss Thần Long)

        // Chiêu thức theo cấp độ
        public static final int VENOM_SPIT_MANA = 25;          // [Lv.1] Phun độc tốn 25 Mana
        public static final double VENOM_SPIT_SPEED = 440.0;   // Vận tốc tia độc
        public static final int VENOM_IMPACT_DMG = 20;         // Sát thương va chạm đạn độc
        public static final int VENOM_DOT_DMG = 6;             // Rút 6 HP mỗi 0.5s
        public static final double VENOM_DOT_DURATION = 3.5;   // Kéo dài 3.5s (tổng cộng 42 DoT)

        public static final int RATTLE_TAIL_MANA = 35;         // [Lv.2] Rung đuôi tốn 35 Mana
        public static final double RATTLE_TAIL_RADIUS = 180.0; // Bán kính sóng xung kích
        public static final double RATTLE_TAIL_COOLDOWN = 2.5; // Hồi chiêu 2.5s
        public static final double RATTLE_TAIL_STUN = 1.8;     // Gây choáng 1.8 giây
        public static final double RATTLE_TAIL_PUSH = 90.0;    // Đẩy lùi 90px

        public static final double THICK_SKIN_REDUCTION = 0.20;// [Lv.3+] Da dày giảm 20% sát thương nhận vào
        public static final double FIRE_DRAKE_BURN_DMG = 12.0; // [Lv.4] Đòn cắn/lướt gây thiêu đốt
        public static final double LEVIATHAN_DMG_BONUS = 0.30; // [Lv.5] Tăng 30% toàn bộ sát thương chiêu

        public static final double MAX_STAMINA = 100.0;
        public static final double STAMINA_DRAIN = 35.0;
        public static final double STAMINA_RECOVERY = 22.0;

        // Sát thương cơ bản
        public static final int TRAP_DAMAGE = 25;
        public static final int AI_BITE_DAMAGE = 20;
    }

    // 4. Cấu hình Hệ Sinh Thái & Kẻ Săn Mồi Trên Không & Đầm Nước
    public static class Ecosystem {
        public static final int TARGET_PREY = 14;
        public static final int TARGET_AI = 3;
        public static final double SPAWN_MIN_DIST = 550.0;
        public static final double SPAWN_MAX_DIST = 1050.0;
        public static final double DESPAWN_DIST = 1500.0;

        // Cá sấu đầm nước (Rình rập và cắn khi người chơi xuống nước / ven bờ)
        public static final int TARGET_CROCODILES = 6;
        public static final int CROCODILE_MAX_HP = 120;
        public static final int CROCODILE_DAMAGE = 25;
        public static final double CROCODILE_AGGRO_RADIUS = 460.0;
        public static final double CROCODILE_SPEED = 105.0;
        public static final double CROCODILE_LUNGE_SPEED = 255.0;

        // Đại Bàng Săn Mồi: Lao nhanh hơn & Phạm vi rộng hơn
        public static final double EAGLE_INTERVAL = 24.0;
        public static final double EAGLE_TELEGRAPH_TIME = 2.6;
        public static final double EAGLE_LOCK_TIME = 0.8;
        public static final double EAGLE_DIVE_TIME = 0.28;     // Lao xuống cực nhanh
        public static final double EAGLE_STRIKE_RADIUS = 95.0; // Phạm vi tấn công diện rộng lớn
        public static final int EAGLE_DAMAGE = 40;             // Sát thương 40 HP
        public static final double EAGLE_ASCEND_TIME = 1.4;

        public static final double AI_HUNTER_AGGRO_RADIUS = 460.0;
        public static final double AI_HUNTER_SPEED = 175.0;
    }

    // 5. Cấu hình Boss Khổng Lồ (Chỉ xuất hiện khi lên cấp, mỗi cấp càng cao càng mạnh)
    public static class BossConfig {
        // Khoảng nghỉ hồi phục chung cho tất cả Boss sau mỗi đòn đánh / kỹ năng (2000ms = 2.0s)
        public static final long   BOSS_REST_DURATION_MS = 2000L;
        public static final double BOSS_RECOVERY_DURATION = BOSS_REST_DURATION_MS / 1000.0; // 2.0 giây đứng yên hồi Mana
        public static final double BOSS_MAX_MANA = 100.0;
        public static final double BOSS_MANA_REGEN_ACTIVE = 6.0;    // Hồi thụ động khi đang di chuyển
        public static final double BOSS_MANA_REGEN_RECOVERY = 35.0; // Hồi nhanh trong 2s nghỉ (+70 MP)

        // Cấp 2 Boss (Màn 1): Bạch Mao Hùng
        public static final int BEAR_MAX_HP = 650;
        public static final double BEAR_BASE_SPEED = 105.0;
        public static final double BEAR_CHARGE_SPEED = 185.0;
        public static final int BEAR_DAMAGE = 35;
        public static final double BEAR_LUNGE_MANA_COST = 40.0;

        // Máy trạng thái đòn lao - táp - lùi của Boss Gấu
        public static final double BEAR_DETECT_RADIUS = 400.0;
        public static final double BEAR_TRIGGER_DIST = 230.0;
        public static final double BEAR_LOST_SIGHT_MAX_DIST = 1200.0;
        public static final double BEAR_WINDUP_TIME = 0.55;
        public static final double BEAR_LUNGE_SPEED = 520.0;
        public static final double BEAR_LUNGE_MAX_DIST = 280.0;
        public static final double BEAR_BITE_RADIUS = 58.0;
        public static final double BEAR_BITE_ARC_DEG = 70.0;
        public static final double BEAR_BITE_TIME = 0.25;
        public static final double BEAR_RETREAT_TIME = 0.9;
        public static final double BEAR_RETREAT_SPEED = 170.0;
        public static final double BEAR_RETREAT_DAMAGE_MULT = 1.25;
        public static final double BEAR_RECOVER_TIME = BOSS_RECOVERY_DURATION; // 2.0 giây đứng yên hồi mana
        public static final double BEAR_ATTACK_COOLDOWN = 0.8;
        public static final double BEAR_STUN_RECOVERY_COOLDOWN = 1.0;
        public static final double BEAR_HIT_STOP_TIME = 0.06;

        // Hệ số nổi điên Boss Gấu (máu <= 40% maxHP)
        public static final double BEAR_ENRAGE_HP_RATIO = 0.4;
        public static final double BEAR_ENRAGE_WINDUP_MULT = 0.65;
        public static final double BEAR_ENRAGE_LUNGE_SPEED_MULT = 1.2;
        public static final double BEAR_ENRAGE_RETREAT_MULT = 0.7;
        public static final double BEAR_ENRAGE_DOUBLE_LUNGE_CHANCE = 0.35;

        // Cấp 3 Boss (Màn 1): Thái Cổ Cự Trăn & Kỹ năng Boss Rắn
        public static final int PYTHON_MAX_HP = 950;
        public static final double PYTHON_SPEED = 140.0;
        public static final int PYTHON_DAMAGE = 40;
        public static final int PYTHON_SEGMENTS = 20;
        public static final double PYTHON_SKILL_RANGE = 420.0;
        public static final double PYTHON_ROAR_TIME = 0.65;
        public static final double PYTHON_VENOM_RECOIL_TIME = 0.35;
        public static final double PYTHON_VENOM_MANA_COST = 35.0;
        public static final int PYTHON_VENOM_DAMAGE = 22;
        public static final int PYTHON_VENOM_DOT_DAMAGE = 4;
        public static final double PYTHON_VENOM_SPEED = 360.0;
        public static final double PYTHON_DASH_MANA_COST = 45.0;
        public static final double PYTHON_DASH_TIME = 0.45;
        public static final double PYTHON_DASH_SPEED = 380.0;
        public static final double PYTHON_TAIL_WHIP_RADIUS = 145.0;
        public static final int PYTHON_TAIL_WHIP_DAMAGE = 26;
        public static final double PYTHON_SKILL_COOLDOWN = 2.4;

        // Boss 1 Màn 2 (Đại Dương): Bạch Tuộc Cự Đại (Giant Octopus / Kraken)
        public static final int OCTOPUS_MAX_HP = 750;
        public static final double OCTOPUS_SPEED = 120.0;
        public static final int OCTOPUS_DAMAGE = 38;
        public static final double OCTOPUS_INK_MANA_COST = 40.0;
        public static final int OCTOPUS_INK_DAMAGE = 20;
        public static final double OCTOPUS_SKILL_COOLDOWN = 4.0;

        // Boss 2 Màn 2 (Đại Dương): Hải Long Cự Thần (Thần Thú Đại Dương Sâu)
        public static final int SWAMP_KING_MAX_HP = 1350;
        public static final double SWAMP_KING_SPEED = 145.0;
        public static final int SWAMP_KING_DAMAGE = 48;
        public static final double SWAMP_KING_WAVE_MANA_COST = 40.0;
        public static final int SWAMP_KING_WAVE_DAMAGE = 24;
        public static final double SWAMP_KING_SKILL_COOLDOWN = 4.2;

        // Boss Kết Màn / Đặc Biệt: Hư Không Thần Long (Rồng Thần Tối Thượng)
        public static final int VOID_DRAGON_MAX_HP = 1850;
        public static final double VOID_DRAGON_SPEED = 160.0;
        public static final int VOID_DRAGON_DAMAGE = 60;
    }

    // 6. Môi trường & Chu kỳ
    public static class Environment {
        public static final double DAY_NIGHT_DURATION = 200.0;
        public static final int CLOUD_COUNT = 7;
        public static final double CLOUD_SPEED = 22.0;
        public static final double PARALLAX_FACTOR = 0.15;
    }

    // 7. Đồ họa
    public static class Render {
        public static final int SHADOW_OFFSET_X = 5;
        public static final int SHADOW_OFFSET_Y = 7;
        public static final float SHADOW_ALPHA = 0.32f;
        public static final float TREE_OCCLUSION_ALPHA = 0.55f;
        public static final Color WATER_COLOR = new Color(29, 66, 75);
    }
}
