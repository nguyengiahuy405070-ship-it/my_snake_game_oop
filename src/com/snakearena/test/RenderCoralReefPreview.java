package com.snakearena.test;

import com.snakearena.core.Camera;
import com.snakearena.core.GamePanel;
import com.snakearena.entity.PlayerSnake;
import com.snakearena.entity.SnakeSegment;
import com.snakearena.world.Chunk;
import com.snakearena.world.ChunkManager;
import com.snakearena.world.CoralReef;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class RenderCoralReefPreview {

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
        panel.startStage(2); // Khởi động Stage 2: Đại dương

        PlayerSnake player = (PlayerSnake) getField(panel, "player");
        Camera camera = (Camera) getField(panel, "camera");
        ChunkManager world = (ChunkManager) getField(panel, "world");

        // Nạp các chunk xung quanh
        List<Chunk> allChunks = new ArrayList<>();
        List<CoralReef> allReefs = new ArrayList<>();
        for (int cx = -4; cx <= 4; cx++) {
            for (int cy = -4; cy <= 4; cy++) {
                Chunk c = world.getOrCreateChunk(cx, cy);
                allChunks.add(c);
                allReefs.addAll(c.getCoralReefs());
            }
        }

        System.out.println("Tổng số cụm san hô tìm thấy: " + allReefs.size());
        if (allReefs.isEmpty()) {
            System.err.println("Không tìm thấy cụm san hô nào trong Stage 2!");
            System.exit(1);
        }

        // Tìm một cụm lớn Biển Sâu (Large Deep-Sea) hoặc cụm vừa đẹp mắt
        CoralReef targetReef = null;
        for (CoralReef r : allReefs) {
            if (r.getTier() == CoralReef.ClusterTier.LARGE_DEEP_SEA) {
                targetReef = r;
                break;
            }
        }
        if (targetReef == null) {
            targetReef = allReefs.get(0);
        }

        // Tìm 1 cặp rạn san hô cách nhau khoảng 450 - 750px để cả hai cùng xuất hiện trên khung hình 1280x720
        CoralReef pair1 = null;
        CoralReef pair2 = null;
        for (CoralReef r1 : allReefs) {
            for (CoralReef r2 : allReefs) {
                if (r1 == r2) continue;
                double d = Math.hypot(r1.centerX - r2.centerX, r1.centerY - r2.centerY);
                if (d >= 450 && d <= 750) {
                    pair1 = r1;
                    pair2 = r2;
                    break;
                }
            }
            if (pair1 != null) break;
        }

        if (pair1 == null) {
            pair1 = targetReef;
            // Tìm cụm gần nhất
            double minD = Double.MAX_VALUE;
            for (CoralReef r : allReefs) {
                if (r == pair1) continue;
                double d = Math.hypot(r.centerX - pair1.centerX, r.centerY - pair1.centerY);
                if (d < minD) {
                    minD = d;
                    pair2 = r;
                }
            }
        }

        System.out.println("Cụm san hô mục tiêu: Tier=" + targetReef.getTier() + " tại (" + (int) targetReef.centerX + ", " + (int) targetReef.centerY +
                "), Kích thước: (" + (int) targetReef.getWidth() + "x" + (int) targetReef.getHeight() +
                "px), Tổng cây: " + targetReef.getPlantsCount() + ", Đung đưa: " + targetReef.getSwayingCorals().size());

        // =========================================================================
        // ẢNH 1: TỔNG QUAN HỆ THỐNG CỤM SAN HÔ MAP 2 (OVERVIEW PREVIEW)
        // =========================================================================
        {
            double viewMidX = (pair1 != null && pair2 != null) ? (pair1.centerX + pair2.centerX) / 2.0 : targetReef.centerX;
            double viewMidY = (pair1 != null && pair2 != null) ? (pair1.centerY + pair2.centerY) / 2.0 : targetReef.centerY;
            CoralReef secondReef = pair2;

            // Đặt rắn bơi ở luồng nước trống giữa các cụm
            setField(player, "x", viewMidX);
            setField(player, "y", viewMidY);
            player.getSegments().clear();
            double curHeading = (secondReef != null) ?
                    Math.atan2(secondReef.centerY - targetReef.centerY, secondReef.centerX - targetReef.centerX) + Math.PI / 2 : 0;
            for (int i = 0; i < 20; i++) {
                double segDist = i * 18.0;
                double segX = viewMidX - Math.cos(curHeading) * segDist;
                double segY = viewMidY - Math.sin(curHeading) * segDist + Math.sin(i * 0.45) * 6.0;
                player.getSegments().add(new SnakeSegment(segX, segY, 9.0));
            }

            camera.snapTo(viewMidX, viewMidY);
            setField(panel, "lastCamChunkX", Integer.MIN_VALUE);
            setField(panel, "lastCamChunkY", Integer.MIN_VALUE);
            invokeMethod(panel, "updateGame", new Class<?>[]{ double.class }, new Object[]{ 0.016 });

            BufferedImage frame1 = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g1 = frame1.createGraphics();
            panel.paint(g1);

            // Bảng thông tin hiển thị
            drawInfoBox(g1, 14, 14, 540, 165,
                    "TỔNG THỂ CỤM SAN HÔ TỰ NHIÊN (STAGE 2: ĐẠI DƯƠNG XANH)",
                    new String[]{
                            "• Lưới phân bố 2 tầng: Lưới thô 1800px (Cụm Lớn), Lưới mịn 900px (Cụm Vừa & Nhỏ).",
                            "• Khoảng nước trống an toàn: Đảm bảo khoảng cách >= 120px giữa các rạn cho rắn bơi tự do.",
                            "• 13 Sprite san hô & hải quỳ tách từ pixel-art chuẩn, tải 1 lần lúc nạp game.",
                            "• Bệ đá xanh teal sẫm có đốm tím/xanh ngọc, 1 bóng mờ mềm chung duy nhất (alpha 0.16).",
                            "• San hô đơn lẻ (khối tròn/hải quỳ) rải rác đáy biển ~1 cây / 60.000px² tạo chiều sâu sinh thái."
                    });

            g1.dispose();
            File out1 = new File(artifactDir, "coral_reef_overview_preview.png");
            ImageIO.write(frame1, "PNG", out1);
            System.out.println("Saved: " + out1.getAbsolutePath());
        }

        // =========================================================================
        // ẢNH 2: CẬN CẢNH CỤM SAN HÔ - ĐA DẠNG 13 LOÀI, BỆ ĐÁ & SINH VẬT RẠN (CLOSEUP PREVIEW)
        // =========================================================================
        {
            camera.snapTo(targetReef.centerX, targetReef.centerY);

            // Rắn bơi nhẹ nhàng phía dưới cụm san hô
            double snakeX = targetReef.centerX;
            double snakeY = targetReef.centerY + targetReef.getRadiusY() + 48.0;
            setField(player, "x", snakeX);
            setField(player, "y", snakeY);
            player.getSegments().clear();
            for (int i = 0; i < 20; i++) {
                double segX = snakeX - i * 18.0;
                double segY = snakeY + Math.sin(i * 0.45) * 5.0;
                player.getSegments().add(new SnakeSegment(segX, segY, 9.0));
            }

            setField(panel, "lastCamChunkX", Integer.MIN_VALUE);
            setField(panel, "lastCamChunkY", Integer.MIN_VALUE);
            invokeMethod(panel, "updateGame", new Class<?>[]{ double.class }, new Object[]{ 0.016 });

            BufferedImage frame2 = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = frame2.createGraphics();
            panel.paint(g2);

            drawInfoBox(g2, 14, 14, 540, 165,
                    "CẬN CẢNH CỤM SAN HÔ: 13 LOÀI SPRITE & BỆ ĐÁ SÂU",
                    new String[]{
                            "• Cụm Lớn Biển Sâu (700-900px, 40-70 cây layered): Tỉ lệ ~2:1, cành cao giữa/sau, khối tròn trước.",
                            "• 13 Loài Sprite: 2 Cành vàng, Hồng-Magenta, Hồng-Tím, Xanh cyan, Não xanh, 4 Khối tròn, 3 Hải quỳ.",
                            "• Bệ đá sẫm đáy biển: Đá teal viền tối, điểm xuyết đốm tím & xanh ngọc tinh tế.",
                            "• Sinh vật rạn: Đàn cá nhỏ bơi lượn quanh rạn (alpha 0.78), các khe đá nhả bọt khí li ti.",
                            "• Tối ưu hiệu năng: Bệ đá & san hô cứng nướng ảnh tĩnh (1 cụm/frame), tối đa 12 cây đung đưa."
                    });

            g2.dispose();
            File out2 = new File(artifactDir, "coral_reef_closeup_preview.png");
            ImageIO.write(frame2, "PNG", out2);
            System.out.println("Saved: " + out2.getAbsolutePath());
        }

        // =========================================================================
        // ẢNH 3: RẮN BƠI TRONG RẠN - Y-SORT, OCCLUSION & ẨN NẤP HỒI MÁU (SWIMMING & COLLISION)
        // =========================================================================
        {
            // Đặt rắn trườn luồn qua tán san hô của rạn (vùng ẩn nấp 80%)
            double insideX = targetReef.centerX + targetReef.getRadiusX() * 0.28;
            double insideY = targetReef.centerY + targetReef.getRadiusY() * 0.25;

            setField(player, "x", insideX);
            setField(player, "y", insideY);
            player.getSegments().clear();
            for (int i = 0; i < 22; i++) {
                double segX = insideX - i * 16.0;
                double segY = insideY + Math.sin(i * 0.50) * 7.0;
                player.getSegments().add(new SnakeSegment(segX, segY, 9.0));
            }

            camera.snapTo(insideX, insideY);
            setField(panel, "lastCamChunkX", Integer.MIN_VALUE);
            setField(panel, "lastCamChunkY", Integer.MIN_VALUE);
            invokeMethod(panel, "updateGame", new Class<?>[]{ double.class }, new Object[]{ 0.016 });

            // Cập nhật trạng thái ẩn nấp
            boolean isHidden = world.isInsideBush(insideX, insideY);

            BufferedImage frame3 = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g3 = frame3.createGraphics();
            panel.paint(g3);

            drawInfoBox(g3, 14, 14, 540, 165,
                    "RẮN BƠI TRONG RẠN: Y-SORT OCCLUSION & ẨN NẤP HỒI MÁU",
                    new String[]{
                            "• Vùng ẩn nấp (80% elip): Trạng thái ngụy trang = " + (isHidden ? "ĐANG BẬT [TRUE]" : "TẮT") + " (+4 HP/s hồi liên tục).",
                            "• Chống kẹt hoàn hảo: Lõi rạn cứng khống chế bán kính <= 90px (Cụm lớn chia 2-3 lõi nhỏ <= 75px).",
                            "• Đẩy va chạm (resolveCorePenetration): Nếu spawn trúng lõi, rắn được đẩy nhẹ ra rìa an toàn.",
                            "• Y-Sorting & Occlusion: Cây san hô che phía trước rắn tự động giảm nhẹ alpha xuống ~0.70.",
                            "• Chiều sâu không gian: Nhuộm sắc xanh teal nhẹ (0.08 - 0.24) theo khoảng cách camera."
                    });

            g3.dispose();
            File out3 = new File(artifactDir, "coral_reef_swimming_collision_preview.png");
            ImageIO.write(frame3, "PNG", out3);
            System.out.println("Saved: " + out3.getAbsolutePath());
        }

        System.out.println("Tất cả 3 ảnh preview cụm san hô mới đã tạo thành công!");
    }

    private static void drawInfoBox(Graphics2D g, int x, int y, int w, int h, String title, String[] lines) {
        Composite old = g.getComposite();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Hộp nền tối mờ
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.88f));
        g.setColor(new Color(6, 30, 44));
        g.fillRoundRect(x, y, w, h, 10, 10);

        // Viền cyan biển sáng
        g.setColor(new Color(14, 165, 233));
        g.setStroke(new BasicStroke(1.8f));
        g.drawRoundRect(x, y, w, h, 10, 10);

        g.setComposite(old);

        // Tiêu đề
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.setColor(new Color(254, 240, 138)); // Vàng tươi
        g.drawString(title, x + 16, y + 24);

        // Các dòng nội dung
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.setColor(new Color(224, 242, 254)); // Trắng xanh
        int curY = y + 46;
        for (String line : lines) {
            g.drawString(line, x + 16, curY);
            curY += 20;
        }
    }
}
