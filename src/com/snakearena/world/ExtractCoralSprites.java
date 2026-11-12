package com.snakearena.world;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Trích xuất 13 sprite san hô và hải quỳ pixel art từ ảnh tham khảo sang src/assets/coral/.
 * Khử nền xám xanh mịn màng, crop sát viền bounding box, bảo tồn pixel art.
 */
public class ExtractCoralSprites {

    private static final String SOURCE_IMAGE_PATH = "C:/Users/Gia Huy/.gemini/antigravity/brain/654b6c66-ba54-4cb1-9fa5-1e908db71677/.user_uploaded/media_1794235211176.png";
    private static final String OUTPUT_DIR = "src/assets/coral";

    private static boolean isBg(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        double dist = Math.sqrt((r - 242) * (r - 242) + (g - 245) * (g - 245) + (b - 250) * (b - 250));
        return dist < 22;
    }

    private static BufferedImage extractAndClean(BufferedImage src, int x0, int y0, int w, int h) {
        BufferedImage temp = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x0 + x, y0 + y);
                if (isBg(rgb)) {
                    temp.setRGB(x, y, 0);
                } else {
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    double dist = Math.sqrt((r - 242) * (r - 242) + (g - 245) * (g - 245) + (b - 250) * (b - 250));
                    if (dist < 34) {
                        double alpha = (dist - 22) / 12.0;
                        int a = (int) (alpha * 255);
                        temp.setRGB(x, y, (a << 24) | (rgb & 0x00FFFFFF));
                    } else {
                        temp.setRGB(x, y, 0xFF000000 | (rgb & 0x00FFFFFF));
                    }
                }
            }
        }

        // Bỏ pixel cô lập nhỏ ở viền
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = (temp.getRGB(x, y) >>> 24);
                if (a > 0) {
                    int neighbors = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx;
                            int ny = y + dy;
                            if (nx >= 0 && nx < w && ny >= 0 && ny < h && !(dx == 0 && dy == 0)) {
                                if ((temp.getRGB(nx, ny) >>> 24) > 30) neighbors++;
                            }
                        }
                    }
                    if (neighbors < 2) {
                        temp.setRGB(x, y, 0);
                    }
                }
            }
        }

        // Crop sát viền
        int minX = w, maxX = -1, minY = h, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((temp.getRGB(x, y) >>> 24) > 20) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }

        if (minX > maxX || minY > maxY) {
            return temp;
        }
        return temp.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    public static void main(String[] args) throws IOException {
        File file = new File(SOURCE_IMAGE_PATH);
        if (!file.exists()) {
            file = new File("C:/Users/Gia Huy/.gemini/antigravity/brain/654b6c66-ba54-4cb1-9fa5-1e908db71677/.user_uploaded/media_1794230909168.png");
        }
        BufferedImage img = ImageIO.read(file);
        File outDir = new File(OUTPUT_DIR);
        outDir.mkdirs();

        // 6 San hô lớn (Row 1)
        saveSprite(img, 458, 47, 67, 67, "branch_gold_1");
        saveSprite(img, 532, 44, 61, 70, "branch_gold_2");
        saveSprite(img, 598, 45, 63, 69, "branch_pink_magenta");
        saveSprite(img, 667, 45, 59, 69, "branch_pink_purple");
        saveSprite(img, 732, 47, 58, 67, "branch_cyan");
        saveSprite(img, 795, 56, 68, 58, "brain_blue");

        // 4 Khối tròn nhỏ (Row 2 đầu)
        saveSprite(img, 467, 133, 49, 41, "round_yellow");
        saveSprite(img, 533, 140, 41, 32, "round_orange");
        saveSprite(img, 590, 140, 42, 32, "round_pink");
        saveSprite(img, 649, 144, 33, 28, "round_pink_small");

        // 3 Hải quỳ (Row 2 sau)
        saveSprite(img, 701, 140, 30, 32, "anemone_red_small");
        saveSprite(img, 748, 131, 44, 43, "anemone_yellow_orange");
        saveSprite(img, 809, 131, 43, 43, "anemone_red_blue_tentacles");

        System.out.println("All 13 coral & anemone sprites successfully extracted to " + outDir.getAbsolutePath());
    }

    private static void saveSprite(BufferedImage src, int x, int y, int w, int h, String name) throws IOException {
        BufferedImage cropped = extractAndClean(src, x, y, w, h);
        File dest = new File(OUTPUT_DIR, name + ".png");
        ImageIO.write(cropped, "png", dest);
        System.out.printf("Saved: %s.png (%dx%d)\n", name, cropped.getWidth(), cropped.getHeight());
    }
}
