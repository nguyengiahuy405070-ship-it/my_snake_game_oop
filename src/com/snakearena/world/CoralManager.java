package com.snakearena.world;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Quản lý tập trung toàn bộ 13 sprite san hô và hải quỳ pixel art trên Map 2.
 * Nạp 1 lần duy nhất lúc khởi động bằng NEAREST_NEIGHBOR, cache sẵn các bản lật ngang (Flipped)
 * và 16 frame đung đưa theo sóng biển (Precomputed Sway Frames).
 * Hoàn toàn Zero-GC trong vòng lặp render game loop.
 */
public class CoralManager {

    public static final int TOTAL_SPRITES = 13;
    public static final int SWAY_FRAME_COUNT = 16;

    // Danh sách tên sprite chuẩn trong src/assets/coral/
    public static final String[] SPRITE_NAMES = {
        "branch_gold_1",               // 0: San hô cành vàng 1
        "branch_gold_2",               // 1: San hô cành vàng 2
        "branch_pink_magenta",         // 2: San hô cành hồng-magenta
        "branch_pink_purple",          // 3: San hô cành hồng-tím
        "branch_cyan",                 // 4: San hô cành xanh dương/cyan
        "brain_blue",                  // 5: San hô não xanh dương
        "round_yellow",                // 6: Khối tròn vàng
        "round_orange",                // 7: Khối tròn cam
        "round_pink",                  // 8: Khối tròn hồng
        "round_pink_small",            // 9: Khối tròn hồng nhỏ
        "anemone_red_small",           // 10: Hải quỳ đỏ nhỏ
        "anemone_yellow_orange",       // 11: Hải quỳ vàng-cam
        "anemone_red_blue_tentacles"   // 12: Hải quỳ đỏ xúc tu xanh
    };

    // ID hằng số tiện truy xuất
    public static final int ID_BRANCH_GOLD_1 = 0;
    public static final int ID_BRANCH_GOLD_2 = 1;
    public static final int ID_BRANCH_PINK_MAGENTA = 2;
    public static final int ID_BRANCH_PINK_PURPLE = 3;
    public static final int ID_BRANCH_CYAN = 4;
    public static final int ID_BRAIN_BLUE = 5;
    public static final int ID_ROUND_YELLOW = 6;
    public static final int ID_ROUND_ORANGE = 7;
    public static final int ID_ROUND_PINK = 8;
    public static final int ID_ROUND_PINK_SMALL = 9;
    public static final int ID_ANEMONE_RED_SMALL = 10;
    public static final int ID_ANEMONE_YELLOW_ORANGE = 11;
    public static final int ID_ANEMONE_RED_BLUE = 12;

    private static BufferedImage[] normalSprites = new BufferedImage[TOTAL_SPRITES];
    private static BufferedImage[] flippedSprites = new BufferedImage[TOTAL_SPRITES];
    private static BufferedImage[][][] swayFrames = new BufferedImage[TOTAL_SPRITES][2][SWAY_FRAME_COUNT];
    private static boolean initialized = false;

    public static synchronized void init() {
        if (initialized) return;

        GraphicsConfiguration gc = null;
        try {
            gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        } catch (Exception ignored) {}

        File assetDir = new File("src/assets/coral");
        for (int i = 0; i < TOTAL_SPRITES; i++) {
            String name = SPRITE_NAMES[i];
            File file = new File(assetDir, name + ".png");
            BufferedImage raw = null;
            if (file.exists()) {
                try {
                    raw = ImageIO.read(file);
                } catch (Exception e) {
                    System.err.println("[CoralManager] Lỗi đọc sprite: " + file.getAbsolutePath());
                }
            }

            if (raw == null) {
                raw = generateFallbackSprite(i);
            }

            // Chuẩn hóa sang TYPE_INT_ARGB tương thích VRAM
            BufferedImage optNormal = (gc != null)
                    ? gc.createCompatibleImage(raw.getWidth(), raw.getHeight(), Transparency.TRANSLUCENT)
                    : new BufferedImage(raw.getWidth(), raw.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = optNormal.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(raw, 0, 0, null);
            g.dispose();
            normalSprites[i] = optNormal;

            // Biến thể lật ngang (Flipped)
            BufferedImage optFlipped = (gc != null)
                    ? gc.createCompatibleImage(raw.getWidth(), raw.getHeight(), Transparency.TRANSLUCENT)
                    : new BufferedImage(raw.getWidth(), raw.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D gf = optFlipped.createGraphics();
            gf.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            gf.drawImage(raw, raw.getWidth(), 0, -raw.getWidth(), raw.getHeight(), null);
            gf.dispose();
            flippedSprites[i] = optFlipped;

            // Biên độ đung đưa theo yêu cầu:
            // - San hô cứng (cành, não, khối tròn): gần như đứng yên (biên độ 0.4px -> shear ~ 0.01)
            // - Hải quỳ (IDs 10..12): lắc xúc tu vừa (biên độ ~2.0px -> shear ~ 0.08)
            double shearMax = isAnemone(i) ? 0.08 : 0.015;
            precomputeSwayFrames(i, gc, shearMax);
        }

        initialized = true;
        System.out.println("[CoralManager] Successfully initialized 13 coral sprites & precomputed 416 sway frames!");
    }

    public static boolean isAnemone(int spriteId) {
        return spriteId >= ID_ANEMONE_RED_SMALL && spriteId <= ID_ANEMONE_RED_BLUE;
    }

    public static boolean isLargeBranch(int spriteId) {
        return spriteId >= ID_BRANCH_GOLD_1 && spriteId <= ID_BRANCH_CYAN;
    }

    public static boolean isBrain(int spriteId) {
        return spriteId == ID_BRAIN_BLUE;
    }

    public static boolean isRoundMass(int spriteId) {
        return spriteId >= ID_ROUND_YELLOW && spriteId <= ID_ROUND_PINK_SMALL;
    }

    private static void precomputeSwayFrames(int spriteId, GraphicsConfiguration gc, double maxShear) {
        for (int flipIdx = 0; flipIdx < 2; flipIdx++) {
            BufferedImage base = (flipIdx == 0) ? normalSprites[spriteId] : flippedSprites[spriteId];
            int w = base.getWidth();
            int h = base.getHeight();
            int pad = (int) Math.ceil(h * maxShear) + 2;
            int frameW = w + pad * 2;
            int frameH = h;

            for (int f = 0; f < SWAY_FRAME_COUNT; f++) {
                BufferedImage frameImg = (gc != null)
                        ? gc.createCompatibleImage(frameW, frameH, Transparency.TRANSLUCENT)
                        : new BufferedImage(frameW, frameH, BufferedImage.TYPE_INT_ARGB);

                Graphics2D g = frameImg.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

                double angle = (f / (double) SWAY_FRAME_COUNT) * Math.PI * 2.0;
                double shear = Math.sin(angle) * maxShear;
                AffineTransform at = new AffineTransform();
                at.translate(pad, 0);
                at.translate(0, h);
                at.shear(-shear, 0.0);
                at.translate(0, -h);
                g.drawImage(base, at, null);
                g.dispose();
                swayFrames[spriteId][flipIdx][f] = frameImg;
            }
        }
    }

    public static BufferedImage getSprite(int spriteId, boolean isFlipped) {
        if (!initialized) init();
        int id = Math.max(0, Math.min(TOTAL_SPRITES - 1, spriteId));
        return isFlipped ? flippedSprites[id] : normalSprites[id];
    }

    public static BufferedImage getSwayFrame(int spriteId, boolean isFlipped, int frameIdx) {
        if (!initialized) init();
        int id = Math.max(0, Math.min(TOTAL_SPRITES - 1, spriteId));
        int f = Math.floorMod(frameIdx, SWAY_FRAME_COUNT);
        return swayFrames[id][isFlipped ? 1 : 0][f];
    }

    public static BufferedImage getSwayFrame(int spriteId, double phase, boolean isFlipped, double time) {
        if (!initialized) init();
        int frameIdx = (int) Math.floor(((time * 1.8 + phase) / (Math.PI * 2.0)) * SWAY_FRAME_COUNT);
        return getSwayFrame(spriteId, isFlipped, frameIdx);
    }

    private static BufferedImage generateFallbackSprite(int id) {
        int w = 48, h = 48;
        BufferedImage fb = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = fb.createGraphics();
        g.setColor(new Color(236, 72, 153));
        g.fillOval(4, 4, 40, 40);
        g.dispose();
        return fb;
    }
}
