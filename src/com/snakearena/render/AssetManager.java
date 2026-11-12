package com.snakearena.render;

import com.snakearena.core.Direction;
import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * AssetManager quản lý tải và xử lý hình ảnh sprite pixel art do người dùng cung cấp.
 * Hỗ trợ xoay hình theo góc tùy ý, lật ảnh theo hướng di chuyển và căn giữa tâm vẽ.
 */
public class AssetManager {
    private static final Map<String, BufferedImage> images = new HashMap<>();
    private static boolean initialized = false;

    public static void loadAssets() {
        if (initialized) return;

        loadImage("rabbit", "rabbit.png");
        loadImage("frog", "frog.png");
        loadImage("mouse", "mouse.png");
        loadImage("butterfly", "butterfly.png");
        loadImage("food_seed", "food_seed.png");
        loadImage("eagle", "eagle.png");
        loadImage("eagle_shadow", "eagle_shadow.png");
        loadImage("bush", "bush.png");
        loadImage("tree", "tree.png");
        loadImage("wall", "wall.png");
        loadImage("stone_block", "stone_block.png");
        loadImage("trap", "trap.png");
        loadImage("ground_tile", "ground_tile.png");

        loadImage("snake_golden", "snake_golden.png");
        loadImage("snake_blue", "snake_blue.png");
        loadImage("snake_purple", "snake_purple.png");
        loadImage("snake_body_green", "snake_body_green.png");
        loadImage("crocodile", "crocodile.png");

        initialized = true;
    }

    private static void loadImage(String key, String filename) {
        try {
            // 1. Thử đọc từ ClassLoader resource
            InputStream is = AssetManager.class.getResourceAsStream("/assets/" + filename);
            if (is != null) {
                BufferedImage img = ImageIO.read(is);
                if (img != null) {
                    if ("bush".equals(key)) {
                        img = featherEdges(img, 4);
                    }
                    images.put(key, img);
                    return;
                }
            }

            // 2. Thử đọc từ thư mục assets/ tương đối
            File f1 = new File("assets/" + filename);
            if (f1.exists()) {
                BufferedImage img = ImageIO.read(f1);
                if (img != null) {
                    if ("bush".equals(key)) {
                        img = featherEdges(img, 4);
                    }
                    images.put(key, img);
                    return;
                }
            }

            // 3. Thử đọc từ src/assets/
            File f2 = new File("src/assets/" + filename);
            if (f2.exists()) {
                BufferedImage img = ImageIO.read(f2);
                if (img != null) {
                    if ("bush".equals(key)) {
                        img = featherEdges(img, 4);
                    }
                    images.put(key, img);
                    return;
                }
            }
        } catch (Exception e) {
            System.err.println("Không thể nạp sprite: " + filename + " (" + e.getMessage() + ")");
        }
    }

    /**
     * Làm mềm các mép bị cắt thẳng (feather 3-4px) nếu sprite có điểm ảnh chạm sát viền ngoài
     */
    public static BufferedImage featherEdges(BufferedImage src, int featherPx) {
        if (src == null) return null;
        int w = src.getWidth();
        int h = src.getHeight();

        boolean hasLeftCut = false;
        boolean hasRightCut = false;
        boolean hasTopCut = false;
        boolean hasBottomCut = false;

        for (int y = 0; y < h; y++) {
            if (((src.getRGB(0, y) >> 24) & 0xFF) > 10) hasLeftCut = true;
            if (((src.getRGB(w - 1, y) >> 24) & 0xFF) > 10) hasRightCut = true;
        }
        for (int x = 0; x < w; x++) {
            if (((src.getRGB(x, 0) >> 24) & 0xFF) > 10) hasTopCut = true;
            if (((src.getRGB(x, h - 1) >> 24) & 0xFF) > 10) hasBottomCut = true;
        }

        if (!hasLeftCut && !hasRightCut && !hasTopCut && !hasBottomCut) {
            return src;
        }

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                if (a <= 0) continue;

                float factor = 1.0f;
                if (hasLeftCut && x < featherPx) {
                    factor = Math.min(factor, (float) (x + 1) / (featherPx + 1));
                }
                if (hasRightCut && (w - 1 - x) < featherPx) {
                    factor = Math.min(factor, (float) (w - x) / (featherPx + 1));
                }
                if (hasTopCut && y < featherPx) {
                    factor = Math.min(factor, (float) (y + 1) / (featherPx + 1));
                }
                if (hasBottomCut && (h - 1 - y) < featherPx) {
                    factor = Math.min(factor, (float) (h - y) / (featherPx + 1));
                }

                if (factor < 1.0f) {
                    a = Math.round(a * factor);
                    argb = (a << 24) | (argb & 0x00FFFFFF);
                }
                out.setRGB(x, y, argb);
            }
        }
        return out;
    }

    public static BufferedImage getImage(String key) {
        if (!initialized) loadAssets();
        return images.get(key);
    }

    /**
     * Vẽ ảnh căn giữa tại (cx, cy).
     */
    public static void drawCentered(Graphics2D g, BufferedImage img, double cx, double cy, double width, double height) {
        if (img == null) return;
        g.drawImage(img, (int) Math.round(cx - width / 2.0), (int) Math.round(cy - height / 2.0),
                (int) Math.round(width), (int) Math.round(height), null);
    }

    /**
     * Vẽ ảnh xoay theo góc radian quanh tâm (cx, cy).
     */
    public static void drawRotated(Graphics2D g, BufferedImage img, double cx, double cy, double width, double height, double angleRad) {
        if (img == null) return;
        AffineTransform old = g.getTransform();
        g.translate(cx, cy);
        g.rotate(angleRad);
        g.drawImage(img, (int) -Math.round(width / 2.0), (int) -Math.round(height / 2.0),
                (int) Math.round(width), (int) Math.round(height), null);
        g.setTransform(old);
    }

    /**
     * Vẽ ảnh có lật ngang (Horizontal flip) theo hướng chạy của động vật.
     */
    public static void drawFlipped(Graphics2D g, BufferedImage img, double cx, double cy, double width, double height, boolean flipH) {
        if (img == null) return;
        AffineTransform old = g.getTransform();
        g.translate(cx, cy);
        if (flipH) {
            g.scale(-1, 1);
        }
        g.drawImage(img, (int) -Math.round(width / 2.0), (int) -Math.round(height / 2.0),
                (int) Math.round(width), (int) Math.round(height), null);
        g.setTransform(old);
    }

    /**
     * Vẽ ảnh theo hướng di chuyển Direction (tương thích ngược).
     */
    public static void drawRotated(Graphics2D g, BufferedImage img, int x, int y, int width, int height, Direction dir) {
        if (img == null) return;

        AffineTransform old = g.getTransform();
        double cx = x + width / 2.0;
        double cy = y + height / 2.0;

        if (dir == Direction.LEFT) {
            g.translate(cx, cy);
            g.scale(-1, 1);
            g.translate(-cx, -cy);
        } else if (dir == Direction.UP) {
            g.rotate(Math.toRadians(-90), cx, cy);
        } else if (dir == Direction.DOWN) {
            g.rotate(Math.toRadians(90), cx, cy);
        }

        g.drawImage(img, x, y, width, height, null);
        g.setTransform(old);
    }
}
