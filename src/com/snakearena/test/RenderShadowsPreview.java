package com.snakearena.test;

import com.snakearena.core.GamePanel;
import com.snakearena.entity.Boss;
import com.snakearena.entity.PlayerSnake;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.List;

public class RenderShadowsPreview {
    private static Object getField(Object target, String fieldName) throws Exception {
        Class<?> clazz = target.getClass();
        Field f = null;
        while (clazz != null) {
            try {
                f = clazz.getDeclaredField(fieldName);
                break;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        if (f != null) {
            f.setAccessible(true);
            return f.get(target);
        }
        throw new NoSuchFieldException(fieldName);
    }

    public static void main(String[] args) throws Exception {
        int width = 1280;
        int height = 720;
        String artifactDir = "C:/Users/Gia Huy/.gemini/antigravity/brain/654b6c66-ba54-4cb1-9fa5-1e908db71677";

        // 1. Stage 1 - Cây, bụi lá, đá, rắn, boss gấu
        GamePanel panel1 = new GamePanel();
        panel1.stopGameLoop();
        panel1.setSize(width, height);
        panel1.startStage(1);

        BufferedImage img1 = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g1 = img1.createGraphics();
        panel1.paint(g1);
        g1.dispose();
        ImageIO.write(img1, "png", new File(artifactDir + "/shadows_stage1_preview.png"));
        System.out.println("Saved: shadows_stage1_preview.png");

        // 2. Stage 2 - San hô, rong biển, rạn đá ngầm, cá sấu/cá mập
        GamePanel panel2 = new GamePanel();
        panel2.stopGameLoop();
        panel2.setSize(width, height);
        panel2.startStage(2);

        BufferedImage img2 = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img2.createGraphics();
        panel2.paint(g2);
        g2.dispose();
        ImageIO.write(img2, "png", new File(artifactDir + "/shadows_stage2_preview.png"));
        System.out.println("Saved: shadows_stage2_preview.png");

        System.exit(0);
    }
}
