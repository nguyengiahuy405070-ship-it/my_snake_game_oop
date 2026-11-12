package com.snakearena.test;

import com.snakearena.core.GamePanel;
import com.snakearena.entity.Boss;
import com.snakearena.entity.PlayerSnake;
import com.snakearena.render.FloatingTextManager;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.List;

public class RenderAllUIPreview {
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
        } else {
            throw new NoSuchFieldException(fieldName);
        }
    }

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

        GamePanel panel = new GamePanel();
        panel.stopGameLoop();
        panel.setSize(width, height);

        // 1. In-game HUD & Objectives Box
        panel.startStage(1);
        PlayerSnake player = (PlayerSnake) getField(panel, "player");
        setField(player, "health", 85);
        setField(player, "mana", 65.0);
        setField(player, "stamina", 90.0);
        setField(player, "score", 1850);
        setField(player, "energy", 35);

        @SuppressWarnings("unchecked")
        List<Boss> bosses = (List<Boss>) getField(panel, "bosses");
        if (bosses.isEmpty()) {
            bosses.add(new Boss(Boss.Type.BEAR, 550, 350));
        }
        Boss bear = bosses.get(0);
        setField(bear, "health", 750);

        FloatingTextManager texts = (FloatingTextManager) getField(panel, "texts");
        texts.spawn(player.getX() + 30, player.getY() - 40, "+3 EXP", new Color(250, 204, 21), 5.0, 15);
        texts.spawn(player.getX() - 20, player.getY() - 65, "★ BẠO KÍCH! -120 HP", new Color(239, 68, 68), 5.0, 16);
        texts.spawn(player.getX() + 50, player.getY() - 15, "✓ HẠ GỤC CON MỒI", new Color(74, 222, 128), 5.0, 13);

        BufferedImage imgHud = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g1 = imgHud.createGraphics();
        panel.paint(g1);
        g1.dispose();
        ImageIO.write(imgHud, "png", new File(artifactDir + "/ui_hud_objectives_preview.png"));
        System.out.println("Saved: ui_hud_objectives_preview.png");

        // 2. Level Up Modal
        setField(panel, "isLevelUpSelecting", true);
        BufferedImage imgLvl = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = imgLvl.createGraphics();
        panel.paint(g2);
        g2.dispose();
        ImageIO.write(imgLvl, "png", new File(artifactDir + "/ui_levelup_modal_preview.png"));
        System.out.println("Saved: ui_levelup_modal_preview.png");

        // 3. Pause Menu Modal
        setField(panel, "isLevelUpSelecting", false);
        setField(panel, "gameState", GamePanel.GameState.PAUSED);
        setField(panel, "pauseMenuIndex", 0);
        setField(panel, "stagePlayTime", 185.0);
        BufferedImage imgPause = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g3 = imgPause.createGraphics();
        panel.paint(g3);
        g3.dispose();
        ImageIO.write(imgPause, "png", new File(artifactDir + "/ui_pause_modal_preview.png"));
        System.out.println("Saved: ui_pause_modal_preview.png");

        // 4. How To Play Modal
        setField(panel, "gameState", GamePanel.GameState.HOW_TO_PLAY);
        BufferedImage imgGuide = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g4 = imgGuide.createGraphics();
        panel.paint(g4);
        g4.dispose();
        ImageIO.write(imgGuide, "png", new File(artifactDir + "/ui_howtoplay_preview.png"));
        System.out.println("Saved: ui_howtoplay_preview.png");

        // 5. Stage Select Screen
        setField(panel, "gameState", GamePanel.GameState.STAGE_SELECT);
        BufferedImage imgStage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g5 = imgStage.createGraphics();
        panel.paint(g5);
        g5.dispose();
        ImageIO.write(imgStage, "png", new File(artifactDir + "/ui_stageselect_preview.png"));
        System.out.println("Saved: ui_stageselect_preview.png");

        // 6. Victory Screen
        setField(panel, "gameState", GamePanel.GameState.VICTORY);
        setField(panel, "enemiesKilledCount", 24);
        BufferedImage imgVic = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g6 = imgVic.createGraphics();
        panel.paint(g6);
        g6.dispose();
        ImageIO.write(imgVic, "png", new File(artifactDir + "/ui_victory_preview.png"));
        System.out.println("Saved: ui_victory_preview.png");

        System.out.println("SUCCESSFULLY GENERATED ALL UI SCREENSHOTS!");
        System.exit(0);
    }
}
