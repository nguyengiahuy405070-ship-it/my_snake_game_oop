package com.snakearena.world;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * ExtractVegetationSprites: Trích xuất và tiền xử lý 18 sprite thực vật pixel art
 * từ hình ảnh tham khảo media_1794230909168.png sang thư mục src/assets/vegetation/.
 *
 * Xử lý:
 * 1. Khử màu nền xám xanh (0xF1F4F9 ~ 0xF4F5FA) với viền anti-aliased mềm.
 * 2. Cắt sát viền bounding box, loại bỏ triệt để các đường nối, mũi tên, nhãn chữ.
 * 3. Trích xuất đúng 18 sprite:
 *    - grass_1 .. grass_7
 *    - flower_1 .. flower_8
 *    - bush_dome (bụi tròn lớn có gốc nâu)
 *    - bush_yellow (bụi hoa vàng nhỏ)
 *    - grass_clump (cỏ cụm lớn)
 */
public class ExtractVegetationSprites {

    private static final String SOURCE_IMAGE_PATH = "C:/Users/Gia Huy/.gemini/antigravity/brain/654b6c66-ba54-4cb1-9fa5-1e908db71677/.user_uploaded/media_1794230909168.png";
    private static final String OUTPUT_DIR = "src/assets/vegetation";

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

        // Loại bỏ nhiễu / pixel cô lập nhỏ ở đáy (đường nối)
        for (int x = 0; x < w; x++) {
            for (int y = h - 1; y >= Math.max(0, h - 3); y--) {
                int a = (temp.getRGB(x, y) >>> 24);
                if (a > 0) {
                    // Kiểm tra xem có pixel lân cận kết nối không
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
                        temp.setRGB(x, y, 0); // Xóa pixel mồ côi
                    }
                }
            }
        }

        // Trim sát viền
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

    public static void extractAll() throws IOException {
        File srcFile = new File(SOURCE_IMAGE_PATH);
        if (!srcFile.exists()) {
            System.err.println("[ERROR] Source image not found: " + SOURCE_IMAGE_PATH);
            return;
        }

        BufferedImage src = ImageIO.read(srcFile);
        File outDir = new File(OUTPUT_DIR);
        outDir.mkdirs();

        // 1. Trích xuất 7 cọng cỏ (grass_1 .. grass_7)
        int[][] grassBoxes = {
            {30, 61, 26, 21},   // grass_1
            {72, 57, 30, 25},   // grass_2
            {118, 49, 37, 33},  // grass_3
            {170, 40, 48, 46},  // grass_4
            {232, 35, 58, 51},  // grass_5
            {303, 37, 49, 49},  // grass_6
            {365, 36, 54, 50}   // grass_7
        };

        for (int i = 0; i < grassBoxes.length; i++) {
            int[] b = grassBoxes[i];
            BufferedImage img = extractAndClean(src, b[0], b[1], b[2], b[3]);
            File target = new File(outDir, "grass_" + (i + 1) + ".png");
            ImageIO.write(img, "png", target);
            System.out.println("Saved " + target.getName() + " (" + img.getWidth() + "x" + img.getHeight() + ")");
        }

        // 2. Trích xuất 8 loại hoa (flower_1 .. flower_8)
        int[][] flowerBoxes = {
            {28, 106, 28, 35},  // flower_1: tím
            {73, 106, 31, 35},  // flower_2: tím nhỏ
            {124, 108, 24, 33}, // flower_3: hồng
            {167, 110, 31, 31}, // flower_4: hồng nhỏ
            {215, 109, 33, 32}, // flower_5: đỏ
            {266, 104, 31, 37}, // flower_6: vàng
            {315, 99, 39, 42},  // flower_7: cụm vàng
            {372, 98, 40, 43}   // flower_8: cụm tím-vàng
        };

        for (int i = 0; i < flowerBoxes.length; i++) {
            int[] b = flowerBoxes[i];
            BufferedImage img = extractAndClean(src, b[0], b[1], b[2], b[3]);
            File target = new File(outDir, "flower_" + (i + 1) + ".png");
            ImageIO.write(img, "png", target);
            System.out.println("Saved " + target.getName() + " (" + img.getWidth() + "x" + img.getHeight() + ")");
        }

        // 3. Trích xuất cỏ cụm lớn (grass_clump)
        BufferedImage clumpImg = extractAndClean(src, 200, 174, 56, 60);
        ImageIO.write(clumpImg, "png", new File(outDir, "grass_clump.png"));
        System.out.println("Saved grass_clump.png (" + clumpImg.getWidth() + "x" + clumpImg.getHeight() + ")");

        // 4. Trích xuất bụi hoa vàng nhỏ (bush_yellow)
        BufferedImage bushYellowImg = extractAndClean(src, 196, 280, 60, 48);
        ImageIO.write(bushYellowImg, "png", new File(outDir, "bush_yellow.png"));
        System.out.println("Saved bush_yellow.png (" + bushYellowImg.getWidth() + "x" + bushYellowImg.getHeight() + ")");

        // 5. Trích xuất bụi tròn lớn có gốc nâu (bush_dome)
        // Vòm tròn tại cx=104, cy=220 trong ảnh gốc
        int x0 = 46, x1 = 158, y0 = 175, y1 = 269;
        int dw = x1 - x0 + 1;
        int dh = y1 - y0 + 1;
        BufferedImage domeRaw = new BufferedImage(dw, dh, BufferedImage.TYPE_INT_ARGB);

        double domeCx = (104.0 - x0);
        double domeCy = (221.0 - y0);
        double domeRx = 47.0;
        double domeRy = 45.0;

        for (int y = 0; y < dh; y++) {
            for (int x = 0; x < dw; x++) {
                int rgb = src.getRGB(x0 + x, y0 + y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                double distBg = Math.sqrt((r - 242) * (r - 242) + (g - 245) * (g - 245) + (b - 250) * (b - 250));

                double nx = (x - domeCx) / domeRx;
                double ny = (y - domeCy) / domeRy;
                double distOval = nx * nx + ny * ny;

                // Thân gốc nâu bên dưới vòm lá
                boolean isTrunk = (y >= domeCy + 25 && y <= dh - 1 && Math.abs(x - domeCx) <= 22 && r > 40 && g < 110);

                if (distBg < 20) {
                    domeRaw.setRGB(x, y, 0);
                } else if (distOval <= 1.05 || isTrunk) {
                    // Loại bỏ các cánh hoa lạc ở viền góc ngoài
                    boolean isWingFlower = (distOval > 0.75 && r > 135 && b > 100 && g < 145);
                    if (isWingFlower) {
                        domeRaw.setRGB(x, y, 0);
                    } else if (distBg < 32) {
                        double alpha = (distBg - 20) / 12.0;
                        int a = (int) (alpha * 255);
                        domeRaw.setRGB(x, y, (a << 24) | (rgb & 0x00FFFFFF));
                    } else {
                        domeRaw.setRGB(x, y, 0xFF000000 | (rgb & 0x00FFFFFF));
                    }
                } else {
                    domeRaw.setRGB(x, y, 0);
                }
            }
        }

        // Trim dome
        int minDx = dw, maxDx = -1, minDy = dh, maxDy = -1;
        for (int y = 0; y < dh; y++) {
            for (int x = 0; x < dw; x++) {
                if ((domeRaw.getRGB(x, y) >>> 24) > 20) {
                    if (x < minDx) minDx = x;
                    if (x > maxDx) maxDx = x;
                    if (y < minDy) minDy = y;
                    if (y > maxDy) maxDy = y;
                }
            }
        }

        BufferedImage domeClean = domeRaw.getSubimage(minDx, minDy, maxDx - minDx + 1, maxDy - minDy + 1);
        ImageIO.write(domeClean, "png", new File(outDir, "bush_dome.png"));
        System.out.println("Saved bush_dome.png (" + domeClean.getWidth() + "x" + domeClean.getHeight() + ")");

        System.out.println("=== EXTRACTED ALL 18 VEGETATION SPRITES SUCCESSFULLY! ===");
    }

    public static void main(String[] args) {
        try {
            extractAll();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
