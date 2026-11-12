package com.snakearena.test;

import com.snakearena.core.Camera;
import com.snakearena.core.Config;
import com.snakearena.entity.AISnake;
import com.snakearena.entity.PlayerSnake;
import com.snakearena.entity.Prey;
import com.snakearena.render.FloatingTextManager;
import com.snakearena.render.ParticleSystem;
import com.snakearena.world.ChunkManager;

public class VerifySnakeRegenTest {
    public static void main(String[] args) {
        System.out.println("=== BẮT ĐẦU KIỂM THỬ HỒI MÁU VÀ HEAL ON KILL ===");

        // Test 1: Công thức calcKillHeal
        int playerHp = 100;
        int preyNormal = Config.SnakeConfig.calcKillHeal(Config.SnakeConfig.KILL_HEAL_PREY_NORMAL_FIXED, Config.SnakeConfig.KILL_HEAL_PREY_NORMAL_RATIO, playerHp);
        int preyFly = Config.SnakeConfig.calcKillHeal(Config.SnakeConfig.KILL_HEAL_PREY_BUTTERFLY_FIXED, Config.SnakeConfig.KILL_HEAL_PREY_BUTTERFLY_RATIO, playerHp);
        int aiSnakeHeal = Config.SnakeConfig.calcKillHeal(Config.SnakeConfig.KILL_HEAL_AI_SNAKE_FIXED, Config.SnakeConfig.KILL_HEAL_AI_SNAKE_RATIO, playerHp);
        int crocHeal = Config.SnakeConfig.calcKillHeal(Config.SnakeConfig.KILL_HEAL_CROCODILE_FIXED, Config.SnakeConfig.KILL_HEAL_CROCODILE_RATIO, playerHp);
        int bossHeal = Config.SnakeConfig.calcKillHeal(Config.SnakeConfig.KILL_HEAL_BOSS_FIXED, Config.SnakeConfig.KILL_HEAL_BOSS_RATIO, playerHp);

        assert preyNormal == 3 : "Prey normal heal mismatch: " + preyNormal;
        assert preyFly == 2 : "Prey butterfly heal mismatch: " + preyFly;
        assert aiSnakeHeal == 12 : "AI snake heal mismatch: " + aiSnakeHeal; // max(12, round(0.06*100)=6) -> 12
        assert crocHeal == 15 : "Crocodile heal mismatch: " + crocHeal;     // max(15, round(0.07*100)=7) -> 15
        assert bossHeal == 40 : "Boss heal mismatch: " + bossHeal;          // max(40, round(0.25*100)=25) -> 40

        // Với player HP cao hơn (ví dụ cấp 5 = 480 HP)
        int highHp = 480;
        int bossHealHigh = Config.SnakeConfig.calcKillHeal(Config.SnakeConfig.KILL_HEAL_BOSS_FIXED, Config.SnakeConfig.KILL_HEAL_BOSS_RATIO, highHp);
        assert bossHealHigh == 120 : "Boss heal high mismatch: " + bossHealHigh; // max(40, round(0.25*480)=120) -> 120
        int aiHealHigh = Config.SnakeConfig.calcKillHeal(Config.SnakeConfig.KILL_HEAL_AI_SNAKE_FIXED, Config.SnakeConfig.KILL_HEAL_AI_SNAKE_RATIO, highHp);
        assert aiHealHigh == 29 : "AI snake heal high mismatch: " + aiHealHigh; // round(0.06*480)=28.8 -> 29

        System.out.println("[PASS] 1. calcKillHeal hoạt động chính xác cho mọi mốc máu!");

        // Test 2: Hồi máu tự nhiên ngoài trời (+1 HP/s) cho Player
        PlayerSnake player = new PlayerSnake(500, 500);
        ChunkManager world = new ChunkManager(1);
        ParticleSystem particles = new ParticleSystem();
        FloatingTextManager texts = new FloatingTextManager();

        // Gây sát thương đưa HP về 50
        player.takeDamage(50, null);
        assert player.getHealth() == 50 : "Health expected 50, got " + player.getHealth();

        // Mô phỏng 1 giây với các bước dt nhỏ (60 FPS, dt = 1/60s)
        double dt = 1.0 / 60.0;
        for (int i = 0; i < 60; i++) {
            player.update(dt, world, particles);
        }
        // Sau 1 giây, hồi +1 HP -> health phải là 51
        System.out.println("Player HP sau 1s ngoài bụi: " + player.getHealth() + " (Kỳ vọng: 51)");
        assert player.getHealth() == 51 : "Expected 51 HP after 1s natural regen, got " + player.getHealth();
        System.out.println("[PASS] 2. Hồi máu tự nhiên ngoài trời đạt chuẩn +1 HP/s!");

        // Test 3: healOnKill
        player.healOnKill(10, particles, texts);
        assert player.getHealth() == 61 : "Expected 61 HP after healOnKill 10, got " + player.getHealth();
        System.out.println("Player HP sau healOnKill(+10): " + player.getHealth() + " (Kỳ vọng: 61)");

        // Test healOnKill vượt trần maxHealth
        player.healOnKill(1000, particles, texts);
        assert player.getHealth() == player.getMaxHealth() : "Expected maxHealth " + player.getMaxHealth() + ", got " + player.getHealth();
        System.out.println("[PASS] 3. healOnKill hoạt động chính xác, không vượt maxHealth!");

        // Test 4: Rắn AI không có hồi tự nhiên ngoài bụi
        AISnake ai = new AISnake(500, 500);
        ai.takeDamage(30, null);
        int aiHp = ai.getHealth();
        for (int i = 0; i < 60; i++) {
            ai.update(dt, world, particles);
        }
        assert ai.getHealth() == aiHp : "AISnake shouldn't regen naturally, expected " + aiHp + ", got " + ai.getHealth();
        System.out.println("[PASS] 4. Rắn AI không tự hồi máu khi ở ngoài bụi!");

        // Test 5: Không hồi máu khi đã chết (alive == false)
        player.update(0.5, world, particles); // Hết thời gian miễn nhiễm 1.2s
        player.takeDamage(999, null);
        assert !player.isAlive() : "Player should be dead!";
        int deadHp = player.getHealth();
        for (int i = 0; i < 60; i++) {
            player.update(dt, world, particles);
        }
        player.healOnKill(50, particles, texts);
        assert player.getHealth() == deadHp : "Dead snake must not heal! Expected " + deadHp + ", got " + player.getHealth();
        System.out.println("[PASS] 5. Rắn đã chết (alive == false) không thể hồi máu tự nhiên hoặc từ kill!");

        // Test 6: Kiểm thử ẩn nấp hồi máu +4 HP/s
        // Tạo ChunkManager giả lập trả về isInsideBush = true
        ChunkManager hidingWorld = new ChunkManager(1) {
            @Override
            public boolean isInsideBush(double x, double y) {
                return true;
            }
        };
        PlayerSnake bushPlayer = new PlayerSnake(100, 100);
        bushPlayer.takeDamage(50, null); // HP = 50
        assert bushPlayer.getHealth() == 50;
        for (int i = 0; i < 60; i++) {
            bushPlayer.update(dt, hidingWorld, particles);
        }
        // Sau 1s trong bụi: +4 HP -> 54 HP
        System.out.println("Player HP sau 1s trong bụi: " + bushPlayer.getHealth() + " (Kỳ vọng: 54)");
        assert bushPlayer.getHealth() == 54 : "Expected 54 HP after 1s in bush, got " + bushPlayer.getHealth();
        System.out.println("[PASS] 6. Hồi máu trong bụi rậm/rạn san hô/hang đá đạt chuẩn +4 HP/s!");

        // Test 7: Rắn AI trong bụi cũng hồi phục +4 HP/s
        AISnake bushAi = new AISnake(100, 100);
        bushAi.takeDamage(30, null); // 85 - 30 = 55 HP
        assert bushAi.getHealth() == 55;
        for (int i = 0; i < 60; i++) {
            bushAi.update(dt, hidingWorld, particles);
        }
        System.out.println("AI Snake HP sau 1s trong bụi: " + bushAi.getHealth() + " (Kỳ vọng: 59)");
        assert bushAi.getHealth() == 59 : "Expected 59 HP after 1s in bush for AI, got " + bushAi.getHealth();
        System.out.println("[PASS] 7. Rắn AI trong bụi rậm hồi phục đúng +4 HP/s!");

        System.out.println("=== TẤT CẢ KIỂM THỬ THÀNH CÔNG RỰC RỠ! ===");
    }
}
