package com.snakearena.test;

import com.snakearena.core.Camera;
import com.snakearena.core.Config;
import com.snakearena.core.GamePanel;
import com.snakearena.entity.PlayerSnake;
import com.snakearena.world.Chunk;
import com.snakearena.world.ChunkManager;
import com.snakearena.world.GrassPatch;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class RenderGrassPatchPreview {

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

        // Tải các chunk xung quanh tọa độ gốc
        for (int cx = -3; cx <= 3; cx++) {
            for (int cy = -3; cy <= 3; cy++) {
                world.getOrCreateChunk(cx, cy);
            }
        }

        // Quét tìm tất cả các mảng cỏ trong các chunk đã tải
        java.util.List<Chunk> allChunks = new java.util.ArrayList<>();
        java.util.List<GrassPatch> allPatches = new java.util.ArrayList<>();
        for (int cx = -4; cx <= 4; cx++) {
            for (int cy = -4; cy <= 4; cy++) {
                Chunk c = world.getOrCreateChunk(cx, cy);
                allChunks.add(c);
                allPatches.addAll(c.getGrassPatches());
            }
        }

        // Tìm một cặp mảng cỏ (p1, p2) cách nhau 380 - 650px nằm ở đồng cỏ thoáng, không sát hang đá
        GrassPatch targetPatch = null;
        GrassPatch secondPatch = null;

        for (GrassPatch p1 : allPatches) {
            boolean nearCave = false;
            for (Chunk c : allChunks) {
                for (com.snakearena.world.WorldProp prop : c.getProps()) {
                    if (prop.getType() == com.snakearena.world.WorldProp.Type.CAVE && Math.hypot(prop.getX() - p1.centerX, prop.getY() - p1.centerY) < 260) {
                        nearCave = true;
                        break;
                    }
                }
                if (nearCave) break;
            }
            if (nearCave) continue;

            for (GrassPatch p2 : allPatches) {
                if (p1 == p2) continue;
                double d = Math.hypot(p1.centerX - p2.centerX, p1.centerY - p2.centerY);
                if (d >= 800 && d <= 1600) {
                    targetPatch = p1;
                    secondPatch = p2;
                    break;
                }
            }
            if (targetPatch != null) break;
        }

        if (targetPatch == null && !allPatches.isEmpty()) {
            targetPatch = allPatches.get(0);
        }

        if (targetPatch == null) {
            System.err.println("Không tìm thấy mảng cỏ nào!");
            System.exit(1);
        }

        System.out.println("Mảng cỏ 1 tìm thấy tại: (" + (int) targetPatch.centerX + ", " + (int) targetPatch.centerY +
                "), Bán kính: (" + (int) targetPatch.radiusX + ", " + (int) targetPatch.radiusY + "), Số cây: " + targetPatch.getPlants().size());
        if (secondPatch != null) {
            double gap = Math.hypot(targetPatch.centerX - secondPatch.centerX, targetPatch.centerY - secondPatch.centerY)
                    - (targetPatch.radiusX + secondPatch.radiusX);
            System.out.println("Mảng cỏ 2 tìm thấy tại: (" + (int) secondPatch.centerX + ", " + (int) secondPatch.centerY +
                    "), Khoảng trống giữa 2 mảng: " + (int) gap + "px");
        }

        // =========================================================================
        // 1. ẢNH PREVIEW 1: TỔNG QUAN MẢNG CỎ RỘNG & KHOẢNG TRỐNG (OVERVIEW PREVIEW)
        // =========================================================================
        double viewCenterX = (secondPatch != null) ? (targetPatch.centerX + secondPatch.centerX) / 2.0 : targetPatch.centerX;
        double viewCenterY = (secondPatch != null) ? (targetPatch.centerY + secondPatch.centerY) / 2.0 : targetPatch.centerY;

        player.teleportTo(viewCenterX, viewCenterY);
        camera.snapTo(viewCenterX, viewCenterY);
        setField(panel, "lastCamChunkX", Integer.MIN_VALUE);
        setField(panel, "lastCamChunkY", Integer.MIN_VALUE);
        invokeMethod(panel, "updateGame", new Class<?>[]{ double.class }, new Object[]{ 0.016 });
        camera.snapTo(viewCenterX, viewCenterY);
        setField(panel, "cachedActiveChunks", world.updateActiveChunks(camera, 0));

        BufferedImage imgOverview = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g1 = imgOverview.createGraphics();
        panel.paint(g1);

        // Chú thích trực quan
        g1.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g1.setColor(new Color(15, 23, 42, 220));
        g1.fillRoundRect(20, 20, 500, 80, 10, 10);
        g1.setColor(new Color(34, 197, 94));
        g1.drawRoundRect(20, 20, 500, 80, 10, 10);

        g1.setFont(new Font("SansSerif", Font.BOLD, 15));
        g1.setColor(new Color(248, 250, 252));
        g1.drawString("CỤM CỎ & HOA (GRASS & FLOWER CLUSTERS) - TỔNG QUAN", 35, 45);
        g1.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g1.setColor(new Color(203, 213, 225));
        g1.drawString("• 4 Kiểu cụm A, B, C, D (260-800px) theo mẫu pixel art: Vành hoa, vòng cỏ giữa trống, bụi tròn.", 35, 66);
        g1.drawString("• Lưới 2 tầng (1600px thô & 800px mịn), cỏ/hoa lẻ ~1 cây/40.000px², không dính nhau.", 35, 84);

        g1.dispose();
        ImageIO.write(imgOverview, "png", new File(artifactDir + "/grass_patch_overview_preview.png"));
        System.out.println("Saved: grass_patch_overview_preview.png");

        // =========================================================================
        // 2. ẢNH PREVIEW 2: CẬN CẢNH MẢNG CỎ (CLOSEUP PREVIEW - 4 BIẾN THỂ & 1 BÓNG CHUNG)
        // =========================================================================
        player.teleportTo(targetPatch.centerX - targetPatch.radiusX - 80, targetPatch.centerY);
        camera.snapTo(targetPatch.centerX, targetPatch.centerY);
        setField(panel, "lastCamChunkX", Integer.MIN_VALUE);
        setField(panel, "lastCamChunkY", Integer.MIN_VALUE);
        invokeMethod(panel, "updateGame", new Class<?>[]{ double.class }, new Object[]{ 0.016 });
        camera.snapTo(targetPatch.centerX, targetPatch.centerY);
        setField(panel, "cachedActiveChunks", world.updateActiveChunks(camera, 0));

        BufferedImage imgCloseup = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = imgCloseup.createGraphics();
        panel.paint(g2);

        // Chú thích trực quan
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(new Color(15, 23, 42, 220));
        g2.fillRoundRect(20, 20, 520, 100, 10, 10);
        g2.setColor(new Color(250, 204, 21));
        g2.drawRoundRect(20, 20, 520, 100, 10, 10);

        g2.setFont(new Font("SansSerif", Font.BOLD, 15));
        g2.setColor(new Color(248, 250, 252));
        g2.drawString("CẬN CẢNH CỤM CỎ & HOA: 18 SPRITE PIXEL ART & 1 BÓNG CHUNG", 35, 45);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g2.setColor(new Color(203, 213, 225));
        g2.drawString("• 18 Sprite: 7 cọng cỏ, 8 loại hoa sắc màu, bụi tròn dome/vàng, cỏ cụm lớn.", 35, 66);
        g2.drawString("• Chiều cao thực vật: Cỏ 20-56px, hoa 14-26px, bụi dome 70-130px, bụi vàng 40-60px.", 35, 84);
        g2.drawString("• 1 Bóng chung mờ mềm duy nhất (alpha ~0.20), chân dither hòa vào đất.", 35, 102);

        g2.dispose();
        ImageIO.write(imgCloseup, "png", new File(artifactDir + "/grass_patch_closeup_preview.png"));
        System.out.println("Saved: grass_patch_closeup_preview.png");

        // =========================================================================
        // 3. ẢNH PREVIEW 3: RẮN TRƯỜN VÀO MẢNG - Y-SORT OCCLUSION (ALPHA ~0.70) & ẨN NẤP
        // =========================================================================
        // Đặt rắn vào chính giữa lòng mảng cỏ
        player.teleportTo(targetPatch.centerX, targetPatch.centerY + 8);
        camera.snapTo(targetPatch.centerX, targetPatch.centerY + 8);
        setField(panel, "lastCamChunkX", Integer.MIN_VALUE);
        setField(panel, "lastCamChunkY", Integer.MIN_VALUE);

        // Chạy update để kích hoạt kiểm tra isInsideBush -> hidden = true
        invokeMethod(panel, "updateGame", new Class<?>[]{ double.class }, new Object[]{ 0.016 });
        camera.snapTo(targetPatch.centerX, targetPatch.centerY + 8);
        setField(panel, "cachedActiveChunks", world.updateActiveChunks(camera, 0));

        BufferedImage imgOcclusion = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g3 = imgOcclusion.createGraphics();
        panel.paint(g3);

        // Chú thích trực quan
        g3.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g3.setColor(new Color(15, 23, 42, 220));
        g3.fillRoundRect(20, 20, 540, 100, 10, 10);
        g3.setColor(new Color(56, 189, 248));
        g3.drawRoundRect(20, 20, 540, 100, 10, 10);

        g3.setFont(new Font("SansSerif", Font.BOLD, 15));
        g3.setColor(new Color(248, 250, 252));
        g3.drawString("RẮN TRƯỜN TRONG CỤM: Y-SORT OCCLUSION & NGỤY TRANG (+4 HP/S)", 35, 45);
        g3.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g3.setColor(new Color(203, 213, 225));
        g3.drawString("• Trạng thái ẩn nấp (Hidden/Camouflage): " + (player.isHidden() ? "ĐANG BẬT [TRUE]" : "TẮT [FALSE]"), 35, 66);
        g3.drawString("• Y-Sorting: Cỏ sau lưng vẽ trước rắn; cỏ trước mặt vẽ sau rắn theo chân đế.", 35, 84);
        g3.drawString("• Occlusion: Cỏ ngay trước thân rắn giảm nhẹ alpha xuống ~0.70 để quan sát rõ.", 35, 102);

        g3.dispose();
        ImageIO.write(imgOcclusion, "png", new File(artifactDir + "/grass_patch_occlusion_hiding_preview.png"));
        System.out.println("Saved: grass_patch_occlusion_hiding_preview.png");

        System.out.println("Tất cả ảnh preview đã tạo thành công!");
        System.exit(0);
    }
}
