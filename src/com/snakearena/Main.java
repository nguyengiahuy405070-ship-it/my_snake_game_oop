package com.snakearena;

import com.snakearena.core.GamePanel;
import javax.swing.*;

/**
 * Điểm khởi chạy chương trình Snake Arena: Hệ Sinh Thái Săn Mồi.
 */
public class Main {
    static {
        // Bật tăng tốc phần cứng OpenGL cho Java 2D trên nền tảng hỗ trợ (Windows/Linux/Mac)
        try {
            System.setProperty("sun.java2d.opengl", "true");
        } catch (Exception ignored) {}
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Snake Arena: Hệ Sinh Thái Săn Mồi (Pixel Art)");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);

            GamePanel gamePanel = new GamePanel();
            frame.add(gamePanel);
            frame.pack();

            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
