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
import java.util.List;

public class RenderMap1DetailedPreview {
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
        panel.startStage(1);

        PlayerSnake player = (PlayerSnake) getField(panel, "player");
        Camera camera = (Camera) getField(panel, "camera");
        ChunkManager world = (ChunkManager) getField(panel, "world");
        FastNoise worldNoise = (FastNoise) getField(world, "worldNoise");

        // 1. Quét tìm vị trí bờ hồ tự nhiên (Lake Shoreline)
        double lakeX = 0, lakeY = 0;
        boolean foundLake = false;
        for (int r = 100; r < 4000 && !foundLake; r += 50) {
            for (int angleDeg = 0; angleDeg < 360; angleDeg += 10) {
                double rad = Math.toRadians(angleDeg);
                double tx = Math.cos(rad) * r;
                double ty = Math.sin(rad) * r;
                double pot = Chunk.getWaterPotential(tx, ty, worldNoise, 1);
                if (pot > 0.04 && pot < 0.09) {
                    lakeX = tx;
                    lakeY = ty;
                    foundLake = true;
                    break;
                }
            }
        }

        if (foundLake) {
            setField(player, "x", lakeX);
            setField(player, "y", lakeY);
            camera.snapTo(lakeX, lakeY);
            setField(panel, "lastCamChunkX", Integer.MIN_VALUE);
            setField(panel, "lastCamChunkY", Integer.MIN_VALUE);
            invokeMethod(panel, "updateGame", new Class<?>[]{ double.class }, new Object[]{ 0.016 });
            BufferedImage imgLake = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = imgLake.createGraphics();
            panel.paint(g);
            g.dispose();
            ImageIO.write(imgLake, "png", new File(artifactDir + "/map1_lake_shoreline_preview.png"));
            System.out.println("Saved: map1_lake_shoreline_preview.png at (" + (int)lakeX + ", " + (int)lakeY + ")");
        }

        // 2. Quét tìm vị trí Rừng rậm (Forest Grove with Trees & Bushes) cách hồ > 800px
        double forestX = 0, forestY = 0;
        boolean foundForest = false;
        for (int r = 300; r < 4000 && !foundForest; r += 50) {
            for (int angleDeg = 0; angleDeg < 360; angleDeg += 10) {
                double rad = Math.toRadians(angleDeg);
                double tx = Math.cos(rad) * r;
                double ty = Math.sin(rad) * r;
                if (Math.hypot(tx - lakeX, ty - lakeY) < 800) continue;
                double pot = Chunk.getWaterPotential(tx, ty, worldNoise, 1);
                double zoneNoise = worldNoise.noise2D(tx * 0.0035, ty * 0.0035);
                if (pot < -0.25 && zoneNoise > 0.68) {
                    forestX = tx;
                    forestY = ty;
                    foundForest = true;
                    break;
                }
            }
        }

        if (foundForest) {
            setField(player, "x", forestX);
            setField(player, "y", forestY);
            camera.snapTo(forestX, forestY);
            setField(panel, "lastCamChunkX", Integer.MIN_VALUE);
            setField(panel, "lastCamChunkY", Integer.MIN_VALUE);
            invokeMethod(panel, "updateGame", new Class<?>[]{ double.class }, new Object[]{ 0.016 });
            BufferedImage imgForest = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = imgForest.createGraphics();
            panel.paint(g);
            g.dispose();
            ImageIO.write(imgForest, "png", new File(artifactDir + "/map1_forest_grove_preview.png"));
            System.out.println("Saved: map1_forest_grove_preview.png at (" + (int)forestX + ", " + (int)forestY + ")");
        }

        // 3. Quét tìm vị trí Bãi đá tảng (Boulder Field) cách hồ và rừng > 800px
        double rockX = 0, rockY = 0;
        boolean foundRock = false;
        for (int r = 300; r < 4000 && !foundRock; r += 50) {
            for (int angleDeg = 0; angleDeg < 360; angleDeg += 10) {
                double rad = Math.toRadians(angleDeg);
                double tx = Math.cos(rad) * r;
                double ty = Math.sin(rad) * r;
                if (Math.hypot(tx - lakeX, ty - lakeY) < 800) continue;
                if (Math.hypot(tx - forestX, ty - forestY) < 800) continue;
                double pot = Chunk.getWaterPotential(tx, ty, worldNoise, 1);
                double zoneNoise = worldNoise.noise2D(tx * 0.0035, ty * 0.0035);
                if (pot < -0.25 && zoneNoise < 0.28) {
                    rockX = tx;
                    rockY = ty;
                    foundRock = true;
                    break;
                }
            }
        }

        if (foundRock) {
            setField(player, "x", rockX);
            setField(player, "y", rockY);
            camera.snapTo(rockX, rockY);
            setField(panel, "lastCamChunkX", Integer.MIN_VALUE);
            setField(panel, "lastCamChunkY", Integer.MIN_VALUE);
            invokeMethod(panel, "updateGame", new Class<?>[]{ double.class }, new Object[]{ 0.016 });
            BufferedImage imgRock = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = imgRock.createGraphics();
            panel.paint(g);
            g.dispose();
            ImageIO.write(imgRock, "png", new File(artifactDir + "/map1_boulder_field_preview.png"));
            System.out.println("Saved: map1_boulder_field_preview.png at (" + (int)rockX + ", " + (int)rockY + ")");
        }

        // 4. Update general shadow previews
        RenderShadowsPreview.main(new String[0]);

        System.exit(0);
    }
}
