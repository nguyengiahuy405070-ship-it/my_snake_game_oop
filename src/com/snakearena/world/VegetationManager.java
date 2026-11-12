package com.snakearena.world;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * VegetationManager: Quản lý nạp, tạo bản lật ngang, tiền kết xuất các khung hình
 * dao động gió (sway frames) cho 18 sprite thực vật pixel-art của Map 1.
 *
 * Tính năng hiệu năng cốt lõi (Zero-GC):
 * 1. Nạp toàn bộ 18 sprite một lần duy nhất lúc khởi động game.
 * 2. Phóng sẵn hoặc dùng kích thước chuẩn pixel-art với NEAREST_NEIGHBOR.
 * 3. Tạo sẵn bản lật ngang (Horizontally flipped) cho mọi sprite.
 * 4. Tiền kết xuất 16 khung hình dao động gió (sinusoidal sway frames) cho cỏ và hoa,
 *    giữ gốc cắm đất cố định, chỉ ngọn lắc lư mềm mại.
 * 5. Cung cấp hàm truy xuất O(1) tức thời, tuyệt đối không tạo đối tượng (Zero allocations) trong render loop.
 */
public class VegetationManager {

    public static final int TOTAL_SPRITES = 18;

    // Sprite IDs
    public static final int SPRITE_GRASS_1 = 0;
    public static final int SPRITE_GRASS_2 = 1;
    public static final int SPRITE_GRASS_3 = 2;
    public static final int SPRITE_GRASS_4 = 3;
    public static final int SPRITE_GRASS_5 = 4;
    public static final int SPRITE_GRASS_6 = 5;
    public static final int SPRITE_GRASS_7 = 6;

    public static final int SPRITE_FLOWER_1 = 7; // Tím
    public static final int SPRITE_FLOWER_2 = 8; // Tím nhỏ
    public static final int SPRITE_FLOWER_3 = 9; // Hồng
    public static final int SPRITE_FLOWER_4 = 10; // Hồng nhỏ
    public static final int SPRITE_FLOWER_5 = 11; // Đỏ
    public static final int SPRITE_FLOWER_6 = 12; // Vàng
    public static final int SPRITE_FLOWER_7 = 13; // Cụm vàng
    public static final int SPRITE_FLOWER_8 = 14; // Cụm tím-vàng

    public static final int SPRITE_BUSH_DOME = 15;    // Bụi tròn lớn có gốc nâu (tĩnh)
    public static final int SPRITE_BUSH_YELLOW = 16;  // Bụi hoa vàng nhỏ
    public static final int SPRITE_GRASS_CLUMP = 17;  // Cỏ cụm lớn

    public static final int SWAY_FRAME_COUNT = 16;
    public static final double SWAY_MAX_PIXELS = 4.0; // Biên độ lắc ngọn tối đa (px)

    private static final String ASSETS_DIR = "src/assets/vegetation";

    // Mảng lưu trữ sprite gốc và bản lật: [spriteId][isFlipped ? 1 : 0]
    private static BufferedImage[][] baseSprites = new BufferedImage[TOTAL_SPRITES][2];

    // Mảng lưu trữ sway frames: [spriteId][isFlipped ? 1 : 0][frameIdx]
    private static BufferedImage[][][] swayFrames = new BufferedImage[TOTAL_SPRITES][2][SWAY_FRAME_COUNT];

    private static boolean initialized = false;

    private static GraphicsConfiguration GRAPHICS_CONFIG;
    static {
        try {
            if (!GraphicsEnvironment.isHeadless()) {
                GRAPHICS_CONFIG = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
            }
        } catch (Throwable ignored) {}
    }

    private static BufferedImage createCompatible(int w, int h, int transparency) {
        if (GRAPHICS_CONFIG != null) {
            try {
                return GRAPHICS_CONFIG.createCompatibleImage(w, h, transparency);
            } catch (Throwable ignored) {}
        }
        return new BufferedImage(w, h, (transparency == Transparency.OPAQUE) ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
    }

    public static synchronized void init() {
        if (initialized) return;

        String[] fileNames = {
            "grass_1.png", "grass_2.png", "grass_3.png", "grass_4.png",
            "grass_5.png", "grass_6.png", "grass_7.png",
            "flower_1.png", "flower_2.png", "flower_3.png", "flower_4.png",
            "flower_5.png", "flower_6.png", "flower_7.png", "flower_8.png",
            "bush_dome.png", "bush_yellow.png", "grass_clump.png"
        };

        File dir = new File(ASSETS_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // Tự động kiểm tra nếu thiếu sprite thì gọi ExtractVegetationSprites trích xuất một lần
        boolean missing = false;
        for (String fn : fileNames) {
            if (!new File(dir, fn).exists()) {
                missing = true;
                break;
            }
        }
        if (missing) {
            try {
                ExtractVegetationSprites.extractAll();
            } catch (Exception e) {
                System.err.println("[VegetationManager] Auto extraction failed: " + e.getMessage());
            }
        }

        for (int i = 0; i < TOTAL_SPRITES; i++) {
            File f = new File(dir, fileNames[i]);
            BufferedImage raw = null;
            if (f.exists()) {
                try {
                    raw = ImageIO.read(f);
                } catch (Exception e) {
                    System.err.println("[VegetationManager] Failed reading: " + f.getName());
                }
            }
            if (raw == null) {
                raw = createFallbackSprite(i);
            }

            int w = raw.getWidth();
            int h = raw.getHeight();

            // 1. Chuyển sang ảnh tương thích phần cứng
            BufferedImage normal = createCompatible(w, h, Transparency.TRANSLUCENT);
            Graphics2D gN = normal.createGraphics();
            gN.setComposite(AlphaComposite.Src);
            gN.drawImage(raw, 0, 0, null);
            gN.dispose();
            baseSprites[i][0] = normal;

            // 2. Tạo bản lật ngang (Horizontally flipped)
            BufferedImage flipped = createCompatible(w, h, Transparency.TRANSLUCENT);
            Graphics2D gF = flipped.createGraphics();
            gF.setComposite(AlphaComposite.Src);
            gF.drawImage(normal, w, 0, -w, h, null);
            gF.dispose();
            baseSprites[i][1] = flipped;

            // 3. Tiền kết xuất 16 sway frames (chỉ dành cho cỏ và hoa, bụi tròn đứng yên)
            boolean canSway = (i != SPRITE_BUSH_DOME);
            int padX = canSway ? (int) Math.ceil(SWAY_MAX_PIXELS) + 2 : 0;
            int swayW = w + padX * 2;
            int swayH = h;

            for (int flip = 0; flip < 2; flip++) {
                BufferedImage srcImg = baseSprites[i][flip];
                for (int frame = 0; frame < SWAY_FRAME_COUNT; frame++) {
                    if (!canSway) {
                        swayFrames[i][flip][frame] = srcImg;
                        continue;
                    }

                    double angle = (frame * 2.0 * Math.PI) / SWAY_FRAME_COUNT;
                    double swayAmount = Math.sin(angle) * SWAY_MAX_PIXELS;

                    BufferedImage frameImg = createCompatible(swayW, swayH, Transparency.TRANSLUCENT);
                    Graphics2D gS = frameImg.createGraphics();
                    gS.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

                    // Biến dạng cành cỏ theo trục Y: gốc (y=h-1) lệch 0px, ngọn (y=0) lệch tối đa swayAmount
                    for (int y = 0; y < h; y++) {
                        double heightRatio = 1.0 - (double) y / Math.max(1, h - 1);
                        double dx = swayAmount * Math.pow(heightRatio, 1.4);
                        int destX = (int) Math.round(padX + dx);
                        gS.drawImage(srcImg, destX, y, destX + w, y + 1, 0, y, w, y + 1, null);
                    }
                    gS.dispose();
                    swayFrames[i][flip][frame] = frameImg;
                }
            }
        }

        initialized = true;
        System.out.println("[VegetationManager] Successfully initialized 18 vegetation sprites & precomputed "
                + (TOTAL_SPRITES * 2 * SWAY_FRAME_COUNT) + " sway frames!");
    }

    public static BufferedImage getSprite(int spriteId, boolean flipped) {
        if (!initialized) init();
        int s = Math.max(0, Math.min(TOTAL_SPRITES - 1, spriteId));
        return baseSprites[s][flipped ? 1 : 0];
    }

    public static BufferedImage getSwayFrame(int spriteId, boolean flipped, int frameIdx) {
        if (!initialized) init();
        int s = Math.max(0, Math.min(TOTAL_SPRITES - 1, spriteId));
        int f = Math.floorMod(frameIdx, SWAY_FRAME_COUNT);
        return swayFrames[s][flipped ? 1 : 0][f];
    }

    public static int getSpriteWidth(int spriteId) {
        if (!initialized) init();
        int s = Math.max(0, Math.min(TOTAL_SPRITES - 1, spriteId));
        return baseSprites[s][0].getWidth();
    }

    public static int getSpriteHeight(int spriteId) {
        if (!initialized) init();
        int s = Math.max(0, Math.min(TOTAL_SPRITES - 1, spriteId));
        return baseSprites[s][0].getHeight();
    }

    public static boolean isSwayable(int spriteId) {
        return spriteId != SPRITE_BUSH_DOME;
    }

    /**
     * Fallback tạo sprite bằng code thuần phòng ngừa trường hợp tệp ảnh bị xóa
     */
    private static BufferedImage createFallbackSprite(int id) {
        int w = 32, h = 36;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        if (id == SPRITE_BUSH_DOME) {
            w = 90; h = 90;
            img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            g = img.createGraphics();
            g.setColor(new Color(34, 139, 34));
            g.fillOval(0, 0, w, h - 16);
            g.setColor(new Color(101, 67, 33));
            g.fillRect(w / 2 - 12, h - 20, 24, 20);
        } else if (id == SPRITE_BUSH_YELLOW) {
            w = 54; h = 48;
            img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            g = img.createGraphics();
            g.setColor(new Color(46, 125, 50));
            g.fillOval(0, 0, w, h);
            g.setColor(new Color(253, 224, 71));
            g.fillOval(10, 10, 8, 8);
            g.fillOval(30, 16, 8, 8);
        } else if (id >= SPRITE_FLOWER_1 && id <= SPRITE_FLOWER_8) {
            g.setColor(new Color(34, 120, 48));
            g.drawLine(w / 2, h - 1, w / 2, 10);
            Color flowerCol = switch (id) {
                case SPRITE_FLOWER_1, SPRITE_FLOWER_2 -> new Color(192, 132, 252);
                case SPRITE_FLOWER_3, SPRITE_FLOWER_4 -> new Color(244, 114, 182);
                case SPRITE_FLOWER_5 -> new Color(239, 68, 68);
                default -> new Color(250, 204, 21);
            };
            g.setColor(flowerCol);
            g.fillOval(w / 2 - 6, 2, 12, 12);
        } else {
            g.setColor(new Color(40, 140, 60));
            g.fillPolygon(new int[]{w / 2 - 6, w / 2, w / 2 + 6}, new int[]{h, 2, h}, 3);
        }
        g.dispose();
        return img;
    }
}
