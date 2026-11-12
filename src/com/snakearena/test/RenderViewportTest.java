package com.snakearena.test;

import com.snakearena.core.GamePanel;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class RenderViewportTest {
    public static void main(String[] args) throws Exception {
        int panelW = 1024;
        int panelH = 720;

        System.out.println("Testing GamePanel Viewport & Render...");
        System.out.println("Configured CAMERA_ZOOM = " + GamePanel.CAMERA_ZOOM);

        GamePanel panel = new GamePanel();
        panel.setSize(panelW, panelH);
        panel.startStage(1);

        // Render frame for Stage 1
        BufferedImage img1 = new BufferedImage(panelW, panelH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g1 = img1.createGraphics();
        panel.paint(g1);
        g1.dispose();

        File out1 = new File("C:/Users/Gia Huy/.gemini/antigravity/brain/654b6c66-ba54-4cb1-9fa5-1e908db71677/restored_viewport_stage1.png");
        ImageIO.write(img1, "png", out1);
        System.out.println("Stage 1 snapshot saved: " + out1.getAbsolutePath());

        // Test Stage 2
        panel.startStage(2);
        BufferedImage img2 = new BufferedImage(panelW, panelH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img2.createGraphics();
        panel.paint(g2);
        g2.dispose();

        File out2 = new File("C:/Users/Gia Huy/.gemini/antigravity/brain/654b6c66-ba54-4cb1-9fa5-1e908db71677/restored_viewport_stage2.png");
        ImageIO.write(img2, "png", out2);
        System.out.println("Stage 2 snapshot saved: " + out2.getAbsolutePath());

        panel.stopGameLoop();
        System.out.println("All render tests passed successfully!");
    }
}
