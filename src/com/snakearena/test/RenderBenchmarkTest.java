package com.snakearena.test;

import com.snakearena.core.Camera;
import com.snakearena.core.GamePanel;
import com.snakearena.entity.*;
import com.snakearena.render.FloatingTextManager;
import com.snakearena.render.LayeredRenderer;
import com.snakearena.render.ParticleSystem;
import com.snakearena.world.Chunk;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class RenderBenchmarkTest {
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
        runBenchmarkForStage(1);
        System.out.println();
        runBenchmarkForStage(2);
    }

    private static void runBenchmarkForStage(int stageId) throws Exception {
        int width = 1280;
        int height = 720;

        GamePanel panel = new GamePanel();
        panel.stopGameLoop();
        panel.setSize(width, height);
        panel.startStage(stageId);

        BufferedImage screenImg = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D gScreen = screenImg.createGraphics();

        Method mUpdate = GamePanel.class.getDeclaredMethod("updateGame", double.class);
        mUpdate.setAccessible(true);

        LayeredRenderer renderer = (LayeredRenderer) getField(panel, "renderer");
        Camera camera = (Camera) getField(panel, "camera");
        PlayerSnake player = (PlayerSnake) getField(panel, "player");
        @SuppressWarnings("unchecked")
        List<Chunk> cachedActiveChunks = (List<Chunk>) getField(panel, "cachedActiveChunks");
        @SuppressWarnings("unchecked")
        List<AISnake> aiSnakes = (List<AISnake>) getField(panel, "aiSnakes");
        @SuppressWarnings("unchecked")
        List<Boss> bosses = (List<Boss>) getField(panel, "bosses");
        @SuppressWarnings("unchecked")
        List<Prey> preys = (List<Prey>) getField(panel, "preys");
        @SuppressWarnings("unchecked")
        List<Crocodile> crocodiles = (List<Crocodile>) getField(panel, "crocodiles");
        Eagle eagle = (Eagle) getField(panel, "eagle");
        @SuppressWarnings("unchecked")
        List<PoisonBolt> poisonBolts = (List<PoisonBolt>) getField(panel, "poisonBolts");
        ParticleSystem particles = (ParticleSystem) getField(panel, "particles");
        FloatingTextManager texts = (FloatingTextManager) getField(panel, "texts");
        BufferedImage logicBuffer = (BufferedImage) getField(panel, "logicBuffer");
        Graphics2D logicGraphics = logicBuffer.createGraphics();

        // Warm up
        for (int i = 0; i < 30; i++) {
            mUpdate.invoke(panel, 0.016);
            panel.paint(gScreen);
        }

        int frames = 200;
        long tWorld = 0;
        long tBlit = 0;
        long tHUD = 0;
        long tObjectives = 0;

        for (int i = 0; i < frames; i++) {
            mUpdate.invoke(panel, 0.016);

            long tw0 = System.nanoTime();
            renderer.renderWorldLayers(
                logicGraphics, logicBuffer.getWidth(), logicBuffer.getHeight(), camera,
                cachedActiveChunks, player, aiSnakes, bosses, preys,
                crocodiles, eagle, poisonBolts, particles, texts,
                0.016, stageId
            );
            long tw1 = System.nanoTime();
            tWorld += (tw1 - tw0);

            long tb0 = System.nanoTime();
            gScreen.drawImage(logicBuffer, 0, 0, null);
            long tb1 = System.nanoTime();
            tBlit += (tb1 - tb0);

            long th0 = System.nanoTime();
            renderer.renderHUDLayers(gScreen, width, height, camera, player, aiSnakes, bosses, preys);
            long th1 = System.nanoTime();
            tHUD += (th1 - th0);

            long to0 = System.nanoTime();
            renderer.renderStageObjectivesHUD(gScreen, width, height, stageId, false, false, false, false, bosses, player.getEvolutionLevel());
            long to1 = System.nanoTime();
            tObjectives += (to1 - to0);
        }

        double avgWorld = (tWorld / 1e6) / frames;
        double avgBlit = (tBlit / 1e6) / frames;
        double avgHUD = (tHUD / 1e6) / frames;
        double avgObj = (tObjectives / 1e6) / frames;
        double avgTotal = avgWorld + avgBlit + avgHUD + avgObj;

        System.out.printf("=== PERFORMANCE BENCHMARK (Stage %d - 200 frames) ===\n", stageId);
        System.out.printf("  renderWorldLayers():          %5.2f ms\n", avgWorld);
        System.out.printf("  directBitBlt(logicBuffer):    %5.2f ms\n", avgBlit);
        System.out.printf("  renderHUDLayers():            %5.2f ms\n", avgHUD);
        System.out.printf("  renderStageObjectivesHUD():   %5.2f ms\n", avgObj);
        System.out.printf("  ---------------------------------------\n");
        System.out.printf("  TOTAL FRAME RENDER TIME:      %5.2f ms (%.1f FPS)\n", avgTotal, 1000.0 / avgTotal);
    }
}
