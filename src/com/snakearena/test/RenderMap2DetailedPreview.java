package com.snakearena.test;

import com.snakearena.core.Camera;
import com.snakearena.core.Config;
import com.snakearena.core.FastNoise;
import com.snakearena.core.GamePanel;
import com.snakearena.entity.PlayerSnake;
import com.snakearena.world.Chunk;
import com.snakearena.world.ChunkManager;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class RenderMap2DetailedPreview {
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

    private static void setField(Object target, String fieldName, Object val) throws Exception {
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
            f.set(target, val);
            return;
        }
        throw new NoSuchFieldException(fieldName);
    }

    private static void invokeMethod(Object target, String methodName, Class<?>[] paramTypes, Object[] args) throws Exception {
        Class<?> clazz = target.getClass();
        Method m = null;
        while (clazz != null) {
            try {
                m = clazz.getDeclaredMethod(methodName, paramTypes);
                break;
            } catch (NoSuchMethodException e) {
                clazz = clazz.getSuperclass();
            }
        }
        if (m != null) {
            m.setAccessible(true);
            m.invoke(target, args);
            return;
        }
        throw new NoSuchMethodException(methodName);
    }

    public static void main(String[] args) throws Exception {
        int width = 1280;
        int height = 720;
        String artifactDir = "C:/Users/Gia Huy/.gemini/antigravity/brain/654b6c66-ba54-4cb1-9fa5-1e908db71677";

        GamePanel panel = new GamePanel();
        panel.stopGameLoop();
        panel.setSize(width, height);
        panel.startStage(2);

        PlayerSnake player = (PlayerSnake) getField(panel, "player");
        Camera camera = (Camera) getField(panel, "camera");
        ChunkManager world = (ChunkManager) getField(panel, "world");
        FastNoise worldNoise = (FastNoise) getField(world, "worldNoise");

        // 1. Quét tìm vị trí Bờ Biển 5 Lớp & Đường Bờ Tự Nhiên (Shoreline with 5-layer beach)
        double shoreX = 0, shoreY = 0;
        boolean foundShore = false;
        for (int r = 100; r < 5000 && !foundShore; r += 40) {
            for (int angleDeg = 0; angleDeg < 360; angleDeg += 8) {
                double rad = Math.toRadians(angleDeg);
                double tx = Math.cos(rad) * r;
                double ty = Math.sin(rad) * r;
                double pot = Chunk.getWaterPotential(tx, ty, worldNoise, 2);
                // Tìm vị trí sát mép nước (pot gần 0: một nửa màn hình là cát, một nửa là biển)
                if (pot > 0.02 && pot < 0.08) {
                    shoreX = tx;
                    shoreY = ty;
                    foundShore = true;
                    break;
                }
            }
        }

        if (foundShore) {
            setField(player, "x", shoreX);
            setField(player, "y", shoreY);
            camera.snapTo(shoreX, shoreY);
            setField(panel, "lastCamChunkX", Integer.MIN_VALUE);
            setField(panel, "lastCamChunkY", Integer.MIN_VALUE);
            invokeMethod(panel, "updateGame", new Class<?>[]{ double.class }, new Object[]{ 0.016 });
            BufferedImage imgShore = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = imgShore.createGraphics();
            panel.paint(g);
            g.dispose();
            ImageIO.write(imgShore, "png", new File(artifactDir + "/map2_shoreline_preview.png"));
            System.out.println("Saved: map2_shoreline_preview.png at (" + (int)shoreX + ", " + (int)shoreY + ")");
        }

        // 2. Quét tìm vị trí Đáy Biển Sâu (Deep Seabed with trenches, ripples, starfish, shells)
        double seabedX = 0, seabedY = 0;
        boolean foundSeabed = false;
        for (int r = 300; r < 5000 && !foundSeabed; r += 50) {
            for (int angleDeg = 0; angleDeg < 360; angleDeg += 10) {
                double rad = Math.toRadians(angleDeg);
                double tx = Math.cos(rad) * r;
                double ty = Math.sin(rad) * r;
                if (Math.hypot(tx - shoreX, ty - shoreY) < 1000) continue;
                double pot = Chunk.getWaterPotential(tx, ty, worldNoise, 2);
                if (pot > 0.18) { // Vùng biển sâu
                    seabedX = tx;
                    seabedY = ty;
                    foundSeabed = true;
                    break;
                }
            }
        }

        if (foundSeabed) {
            setField(player, "x", seabedX);
            setField(player, "y", seabedY);
            camera.snapTo(seabedX, seabedY);
            setField(panel, "lastCamChunkX", Integer.MIN_VALUE);
            setField(panel, "lastCamChunkY", Integer.MIN_VALUE);
            invokeMethod(panel, "updateGame", new Class<?>[]{ double.class }, new Object[]{ 0.016 });
            BufferedImage imgSeabed = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = imgSeabed.createGraphics();
            panel.paint(g);
            g.dispose();
            ImageIO.write(imgSeabed, "png", new File(artifactDir + "/map2_seabed_preview.png"));
            System.out.println("Saved: map2_seabed_preview.png at (" + (int)seabedX + ", " + (int)seabedY + ")");
        }

        // 3. Chụp Underwater Ambience (Tia sáng nghiêng 20 độ, caustics kép, depth tinting, cá bơi hậu cảnh)
        double oceanX = seabedX + 350;
        double oceanY = seabedY + 250;
        setField(player, "x", oceanX);
        setField(player, "y", oceanY);
        camera.snapTo(oceanX, oceanY);
        setField(panel, "lastCamChunkX", Integer.MIN_VALUE);
        setField(panel, "lastCamChunkY", Integer.MIN_VALUE);

        // Chạy qua vài frame để các vệt sáng và thời gian môi trường tích lũy
        for (int i = 0; i < 40; i++) {
            invokeMethod(panel, "updateGame", new Class<?>[]{ double.class }, new Object[]{ 0.033 });
        }

        BufferedImage imgAmbience = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gAmb = imgAmbience.createGraphics();
        panel.paint(gAmb);
        gAmb.dispose();
        ImageIO.write(imgAmbience, "png", new File(artifactDir + "/map2_underwater_ambience_preview.png"));
        System.out.println("Saved: map2_underwater_ambience_preview.png");

        // 4. Chụp lại bóng đổ Stage 2 (shadows_stage2_preview.png)
        ImageIO.write(imgAmbience, "png", new File(artifactDir + "/shadows_stage2_preview.png"));
        System.out.println("Saved: shadows_stage2_preview.png");

        System.out.println("ALL MAP 2 PREVIEWS RENDERED SUCCESSFULLY!");
    }
}
