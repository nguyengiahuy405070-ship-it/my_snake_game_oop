package com.snakearena.test;

import com.snakearena.core.Camera;
import com.snakearena.core.Config;
import com.snakearena.core.GamePanel;
import com.snakearena.entity.AISnake;
import com.snakearena.entity.Boss;
import com.snakearena.entity.PlayerSnake;
import com.snakearena.entity.Snake;
import com.snakearena.entity.SnakeSegment;
import com.snakearena.render.LayeredRenderer;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.List;

public class RenderSnakeDetailedPreview {
    private static final String ARTIFACT_DIR = "C:/Users/Gia Huy/.gemini/antigravity/brain/654b6c66-ba54-4cb1-9fa5-1e908db71677";

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

    private static void setupSnake(Snake snake, double startX, double startY, double baseAngle, int segCount, double slitherTime) {
        snake.setX(startX);
        snake.setY(startY);
        snake.setAngle(baseAngle);
        snake.setSlitherTime(slitherTime);

        List<SnakeSegment> segments = snake.getSegments();
        segments.clear();

        double curX = startX;
        double curY = startY;
        double curAngle = baseAngle;
        double segDist = Config.SnakeConfig.SEGMENT_DIST;

        for (int i = 0; i < segCount; i++) {
            segments.add(new SnakeSegment(curX, curY, curAngle));
            // Uốn lượn tự nhiên theo sóng sin cột sống
            double turn = Math.sin(i * 0.28 + slitherTime * 1.5) * 0.24;
            curAngle = baseAngle + turn;
            curX -= Math.cos(curAngle) * segDist;
            curY -= Math.sin(curAngle) * segDist;
        }
    }

    private static void setupBoss(Boss boss, double startX, double startY, double baseAngle, int segCount, double animTimer) {
        boss.setX(startX);
        boss.setY(startY);
        boss.setAngle(baseAngle);
        boss.setAnimTimer(animTimer);

        List<SnakeSegment> segments = boss.getSegments();
        segments.clear();

        double curX = startX;
        double curY = startY;
        double curAngle = baseAngle;
        double segDist = 20.0;

        for (int i = 0; i < segCount; i++) {
            segments.add(new SnakeSegment(curX, curY, curAngle));
            double turn = Math.sin(i * 0.22 + animTimer * 1.5) * 0.20;
            curAngle = baseAngle + turn;
            curX -= Math.cos(curAngle) * segDist;
            curY -= Math.sin(curAngle) * segDist;
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Bắt đầu tinh chỉnh ảnh kiểm thử hiển thị thân rắn mới...");
        int width = 1280;
        int height = 720;

        // =========================================================================
        // ẢNH 1: snake_seamless_preview.png
        // =========================================================================
        {
            GamePanel panel = new GamePanel();
            panel.stopGameLoop();
            panel.setSize(width, height);
            panel.startStage(1);

            // Dọn sạch các thực thể nền ngẫu nhiên để khung cảnh sạch sẽ
            @SuppressWarnings("unchecked")
            List<AISnake> aiList = (List<AISnake>) getField(panel, "aiSnakes");
            aiList.clear();
            PlayerSnake bgPlayer = (PlayerSnake) getField(panel, "player");
            bgPlayer.setX(-10000);
            bgPlayer.setY(-10000);
            bgPlayer.getSegments().clear();

            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            panel.paint(g); // Vẽ nền Map 1

            LayeredRenderer renderer = new LayeredRenderer();
            Camera camera = new Camera(width, height);
            camera.snapTo(640, 360);

            // 1. Rắn Player Cấp 1 (Garter Snake) - Bò thường
            PlayerSnake playerNorm = new PlayerSnake(500, 225);
            playerNorm.setEvolutionLevel(1);
            setupSnake(playerNorm, 500, 225, 0.12, 22, 1.4);
            renderer.drawSnake(g, camera, playerNorm);

            // 2. Rắn Player Cấp 1 (Garter Snake) - Lướt nhanh (Sprint/Dash)
            PlayerSnake playerDash = new PlayerSnake(500, 360);
            playerDash.setEvolutionLevel(1);
            playerDash.setSprinting(true);
            playerDash.setDashing(true);
            setupSnake(playerDash, 500, 360, -0.06, 22, 2.6);
            renderer.drawSnake(g, camera, playerDash);

            // 3. Rắn AI Hunter (Vằn Hổ Đỏ Cam, nanh săn mồi, đầu vát hổ, mắt đỏ slit)
            AISnake aiHunter = new AISnake(500, 495, AISnake.Role.HUNTER);
            setupSnake(aiHunter, 500, 495, 0.10, 20, 1.8);
            renderer.drawSnake(g, camera, aiHunter);

            // 4. Rắn AI Forager (Đốm Hoa Mai Lục Vàng, đầu thanh thoát, mắt tinh anh)
            AISnake aiForager = new AISnake(500, 630, AISnake.Role.FORAGER);
            setupSnake(aiForager, 500, 630, -0.10, 18, 3.1);
            renderer.drawSnake(g, camera, aiForager);

            // Inset phóng to 3X chi tiết đầu rắn (Viper Wedge, Specular Glint, Flickering Tongue)
            int insetX = 890;
            int insetY = 70;
            int insetW = 360;
            int insetH = 340;

            g.setColor(new Color(15, 23, 42, 235));
            g.fillRoundRect(insetX, insetY, insetW, insetH, 12, 12);
            g.setColor(new Color(56, 189, 248));
            g.drawRoundRect(insetX, insetY, insetW, insetH, 12, 12);

            g.setFont(new Font("Consolas", Font.BOLD, 15));
            g.setColor(new Color(250, 204, 21));
            g.drawString("CHI TIẾT ĐẦU RẮN & MẮT (ZOOM 3X)", insetX + 15, insetY + 28);

            // Vẽ đầu rắn zoom 3x bên trong hộp Inset
            Graphics2D gZoom = (Graphics2D) g.create();
            gZoom.setClip(insetX + 10, insetY + 40, insetW - 20, insetH - 50);
            gZoom.translate(insetX + 160, insetY + 160);
            gZoom.scale(3.2, 3.2);

            PlayerSnake zoomSnake = new PlayerSnake(0, 0);
            zoomSnake.setEvolutionLevel(1);
            setupSnake(zoomSnake, 0, 0, 0.0, 8, 1.4);
            Camera zoomCam = new Camera(insetW, insetH);
            zoomCam.snapTo(0, 0);
            renderer.drawSnake(gZoom, zoomCam, zoomSnake);
            gZoom.dispose();

            // Chú thích Inset
            g.setFont(new Font("Consolas", Font.PLAIN, 12));
            g.setColor(new Color(226, 232, 240));
            g.drawString("• Đầu nhỉnh hơn thân 11% (20px vs 18px), <=15%", insetX + 15, insetY + 265);
            g.drawString("• Đôi mắt to tròn + Specular Glint phản quang", insetX + 15, insetY + 285);
            g.drawString("• Lưỡi đỏ chẻ đôi thè nhấp nháy nhịp nhàng", insetX + 15, insetY + 305);
            g.drawString("• Viền cùng gam tối hơn 38% (Không đen thuần)", insetX + 15, insetY + 325);

            // Bảng thông tin tính năng ở góc trái
            int infoX = 30;
            int infoY = 55;
            int infoW = 460;
            int infoH = 110;
            g.setColor(new Color(15, 23, 42, 230));
            g.fillRoundRect(infoX, infoY, infoW, infoH, 10, 10);
            g.setColor(new Color(74, 222, 128));
            g.drawRoundRect(infoX, infoY, infoW, infoH, 10, 10);

            g.setFont(new Font("Consolas", Font.BOLD, 15));
            g.setColor(new Color(74, 222, 128));
            g.drawString("HỆ THỐNG THÂN RẮN ĐỘ RỘNG ĐỀU, THUÔN DÀI", infoX + 15, infoY + 25);
            g.setFont(new Font("Consolas", Font.PLAIN, 12));
            g.setColor(new Color(241, 245, 249));
            g.drawString("1. Thân độ rộng đều 18px tuyệt đối, đuôi bo tròn bán kính thân", infoX + 15, infoY + 46);
            g.drawString("2. Thuôn dài mảnh (~62% bản cũ), nét liền 3 lớp (Viền, Thân, Highlight)", infoX + 15, infoY + 64);
            g.drawString("3. Họa tiết lặp đều kích thước bằng nhau suốt thân", infoX + 15, infoY + 82);
            g.drawString("4. Sóng sin biên độ đồng đều, đầu nhỉnh hơn thân 11%", infoX + 15, infoY + 100);

            // Nhãn tên từng con rắn
            g.setFont(new Font("Consolas", Font.BOLD, 13));
            g.setColor(new Color(34, 197, 94));
            g.drawString("PLAYER LV1 (DI CHUYỂN BÌNH THƯỜNG - UỐN SÓNG TỰ NHIÊN)", 50, 195);

            g.setColor(new Color(56, 189, 248));
            g.drawString("PLAYER LV1 (LƯỚT NHANH / DASH - SÓNG TẦN SỐ CAO + HÀO QUANG)", 50, 330);

            g.setColor(new Color(239, 68, 68));
            g.drawString("AI HUNTER (VẰN HỔ ĐỎ CAM, ĐẦU GÓC CẠNH, NẰNH SĂN MỒI)", 50, 465);

            g.setColor(new Color(234, 179, 8));
            g.drawString("AI FORAGER (ĐỐM HOA MAI LỤC VÀNG, ĐẦU THANH THOÁT, MẮT SẮC)", 50, 600);

            g.dispose();
            File out1 = new File(ARTIFACT_DIR + "/snake_seamless_preview.png");
            ImageIO.write(img, "png", out1);
            System.out.println("Đã lưu: " + out1.getAbsolutePath());
        }

        // =========================================================================
        // ẢNH 2: snake_evolution_preview.png
        // =========================================================================
        {
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();

            // Nền tối thanh lịch làm nổi bật hiệu ứng và ánh sáng của rắn
            g.setColor(new Color(15, 23, 42));
            g.fillRect(0, 0, width, height);

            // Lưới pixel mờ trang trí
            g.setColor(new Color(30, 41, 59, 100));
            for (int x = 0; x < width; x += 32) g.drawLine(x, 0, x, height);
            for (int y = 0; y < height; y += 32) g.drawLine(0, y, width, y);

            LayeredRenderer renderer = new LayeredRenderer();
            Camera camera = new Camera(width, height);
            camera.snapTo(640, 360);

            // Tiêu đề
            g.setFont(new Font("Consolas", Font.BOLD, 22));
            g.setColor(new Color(250, 204, 21));
            g.drawString("5 CẤP TIẾN HÓA RẮN (PLAYER EVOLUTION TIERS) - ĐỘ RỘNG ĐỀU 18px", 40, 45);

            String[] titles = {
                "CẤP 1: GARTER SNAKE (Lục Bảo Thảo Xà - Thân Đều 18px Xanh Ngọc, Đuôi Bo Tròn, Vằn Vàng Lặp Đều)",
                "CẤP 2: SHADOW VIPER (U Ảnh Độc Xà - Thân Đều 18px Tím Thạch Anh, Sừng Gai Độc, Khoanh Tím Neon)",
                "CẤP 3: APEX TITAN BOA (Hoàng Kim Cự Long - Thân Đều 18px Hoàng Kim, Vảy Chữ V Ruby Đều Đặn)",
                "CẤP 4: CRIMSON FIRE DRAKE (Huyết Diệm Hỏa Xà - Thân Đều 18px Huyết Rồng, Vằn Lửa Rực Cháy)",
                "CẤP 5: CELESTIAL LEVIATHAN (Kim Long Thần Xà - Thân Đều 18px Thần Thánh, Tinh Vân Lam Ngọc)"
            };

            Color[] titleColors = {
                new Color(74, 222, 128),
                new Color(192, 132, 252),
                new Color(250, 204, 21),
                new Color(248, 113, 113),
                new Color(56, 189, 248)
            };

            int startY = 100;
            int gapY = 125;

            for (int lv = 1; lv <= 5; lv++) {
                int yPos = startY + (lv - 1) * gapY;

                // Khung viền mờ cho từng cấp
                g.setColor(new Color(30, 41, 59, 140));
                g.fillRoundRect(35, yPos - 25, width - 70, 110, 10, 10);
                g.setColor(titleColors[lv - 1]);
                g.drawRoundRect(35, yPos - 25, width - 70, 110, 10, 10);

                // Nhãn tiêu đề cấp độ
                g.setFont(new Font("Consolas", Font.BOLD, 13));
                g.setColor(titleColors[lv - 1]);
                g.drawString(titles[lv - 1], 50, yPos - 6);

                // Bố trí đầu rắn ở X = 1140 hướng sang phải (angle = 0.0), thân uốn lượn về phía bên trái (X = 820..1140)
                PlayerSnake snake = new PlayerSnake(1140, yPos + 35);
                snake.setEvolutionLevel(lv);
                int segCount = 18 + lv * 2;
                setupSnake(snake, 1140, yPos + 35, 0.0, segCount, 1.2 + lv * 0.4);
                renderer.drawSnake(g, camera, snake);
            }

            g.dispose();
            File out2 = new File(ARTIFACT_DIR + "/snake_evolution_preview.png");
            ImageIO.write(img, "png", out2);
            System.out.println("Đã lưu: " + out2.getAbsolutePath());
        }

        // =========================================================================
        // ẢNH 3: snake_underwater_preview.png
        // =========================================================================
        {
            GamePanel panel = new GamePanel();
            panel.stopGameLoop();
            panel.setSize(width, height);
            panel.startStage(2); // Map 2 Biển/Đáy biển

            // Dọn sạch các thực thể nền ngẫu nhiên
            @SuppressWarnings("unchecked")
            List<AISnake> aiList = (List<AISnake>) getField(panel, "aiSnakes");
            aiList.clear();
            PlayerSnake bgPlayer = (PlayerSnake) getField(panel, "player");
            bgPlayer.setX(-10000);
            bgPlayer.setY(-10000);
            bgPlayer.getSegments().clear();

            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            panel.paint(g); // Vẽ nền Map 2

            LayeredRenderer renderer = new LayeredRenderer();
            Camera camera = new Camera(width, height);
            camera.snapTo(640, 360);

            // 1. Rắn Player Cấp 1 bơi dưới nước
            PlayerSnake pWater = new PlayerSnake(560, 260);
            pWater.setEvolutionLevel(1);
            pWater.setInWater(true);
            setupSnake(pWater, 560, 260, 0.15, 24, 2.8);
            renderer.drawSnake(g, camera, pWater);

            // 2. Rắn Player Cấp 3 (Apex Titan Boa) bơi dưới nước
            PlayerSnake pWater3 = new PlayerSnake(520, 500);
            pWater3.setEvolutionLevel(3);
            pWater3.setInWater(true);
            setupSnake(pWater3, 520, 500, -0.15, 28, 4.2);
            renderer.drawSnake(g, camera, pWater3);

            // Bảng chú thích tính năng bơi lội dưới nước
            int boxX = 40;
            int boxY = 70;
            int boxW = 460;
            int boxH = 125;
            g.setColor(new Color(15, 23, 42, 235));
            g.fillRoundRect(boxX, boxY, boxW, boxH, 10, 10);
            g.setColor(new Color(56, 189, 248));
            g.drawRoundRect(boxX, boxY, boxW, boxH, 10, 10);

            g.setFont(new Font("Consolas", Font.BOLD, 15));
            g.setColor(new Color(56, 189, 248));
            g.drawString("HIỆU ỨNG RẮN BƠI DƯỚI NƯỚC (MAP 2 - ĐỘ RỘNG ĐỀU 18px)", boxX + 15, boxY + 26);
            g.setFont(new Font("Consolas", Font.PLAIN, 12));
            g.setColor(new Color(241, 245, 249));
            g.drawString("1. Tự động chuyển gam màu ngả lam ngọc/nước biển sâu", boxX + 15, boxY + 50);
            g.drawString("2. Độ rộng đều 18px, đuôi vẫy chậm ở 40% phần thân sau", boxX + 15, boxY + 70);
            g.drawString("3. Vệt bọt nước sủi tăm mềm li ti (Bubble Trail) sau chóp đuôi", boxX + 15, boxY + 90);
            g.drawString("4. Viền bọt nước rẽ sóng trong suốt quanh đầu rắn", boxX + 15, boxY + 110);

            g.setFont(new Font("Consolas", Font.BOLD, 13));
            g.setColor(new Color(56, 189, 248));
            g.drawString("PLAYER LV1 (BƠI DƯỚI NƯỚC - BỌT ĐUÔI + TINT XANH BIỂN)", 60, 235);
            g.drawString("PLAYER LV3 (CỰ LONG BƠI BIỂN SÂU - VƯƠNG MIỆN & RÂU RỒNG)", 60, 465);

            g.dispose();
            File out3 = new File(ARTIFACT_DIR + "/snake_underwater_preview.png");
            ImageIO.write(img, "png", out3);
            System.out.println("Đã lưu: " + out3.getAbsolutePath());
        }

        // =========================================================================
        // ẢNH 4: snake_boss_preview.png (BOSS DẠNG RẮN: ĐỘ RỘNG ĐỀU x1.6 = 28.8px)
        // =========================================================================
        {
            GamePanel panel = new GamePanel();
            panel.stopGameLoop();
            panel.setSize(width, height);
            panel.startStage(1);

            @SuppressWarnings("unchecked")
            List<AISnake> aiList = (List<AISnake>) getField(panel, "aiSnakes");
            aiList.clear();
            PlayerSnake bgPlayer = (PlayerSnake) getField(panel, "player");
            bgPlayer.setX(-10000);
            bgPlayer.setY(-10000);
            bgPlayer.getSegments().clear();

            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            panel.paint(g); // Nền Map 1

            LayeredRenderer renderer = new LayeredRenderer();
            Camera camera = new Camera(width, height);
            camera.snapTo(640, 360);

            // 1. Boss 1: Thái Cổ Cự Trăn (Giant Python) - Thân đều 28.8px, Đuôi bo tròn cùng bán kính, Vảy vàng kim
            Boss python = new Boss(Boss.Type.GIANT_PYTHON, 680, 255);
            setupBoss(python, 680, 255, 0.10, 22, 1.8);
            renderer.drawBoss(g, camera, python);

            // 2. Boss 2: Hư Không Long Vương (Void Dragon) - Thân đều 28.8px, Hào quang hư không, Sừng rồng & vảy lam tinh
            Boss dragon = new Boss(Boss.Type.VOID_DRAGON, 680, 535);
            setupBoss(dragon, 680, 535, -0.08, 24, 3.2);
            renderer.drawBoss(g, camera, dragon);

            // Bảng thông tin
            int bInfoX = 30;
            int bInfoY = 55;
            int bInfoW = 520;
            int bInfoH = 115;
            g.setColor(new Color(15, 23, 42, 235));
            g.fillRoundRect(bInfoX, bInfoY, bInfoW, bInfoH, 10, 10);
            g.setColor(new Color(250, 204, 21));
            g.drawRoundRect(bInfoX, bInfoY, bInfoW, bInfoH, 10, 10);

            g.setFont(new Font("Consolas", Font.BOLD, 15));
            g.setColor(new Color(250, 204, 21));
            g.drawString("BOSS DẠNG RẮN: ĐỘ RỘNG ĐỀU (x1.6 = 28.8px)", bInfoX + 15, bInfoY + 25);
            g.setFont(new Font("Consolas", Font.PLAIN, 12));
            g.setColor(new Color(241, 245, 249));
            g.drawString("1. Phóng to theo hệ số cố định x1.6 (Thân 28.8px, Viền 30.8px)", bInfoX + 15, bInfoY + 48);
            g.drawString("2. GIỮ ĐỘ RỘNG ĐỀU từ cổ đến chóp đuôi, không thu nhỏ ở đuôi", bInfoX + 15, bInfoY + 68);
            g.drawString("3. Đuôi kết thúc bằng đầu bo tròn cùng bán kính với thân", bInfoX + 15, bInfoY + 88);
            g.drawString("4. Họa tiết vảy rồng lặp đều kích thước bằng nhau suốt thân", bInfoX + 15, bInfoY + 108);

            // Inset so sánh kích thước: Thân rắn thường (18px) vs Thân Boss (28.8px)
            int insetX = 890;
            int insetY = 70;
            int insetW = 360;
            int insetH = 240;

            g.setColor(new Color(15, 23, 42, 235));
            g.fillRoundRect(insetX, insetY, insetW, insetH, 12, 12);
            g.setColor(new Color(168, 85, 247));
            g.drawRoundRect(insetX, insetY, insetW, insetH, 12, 12);

            g.setFont(new Font("Consolas", Font.BOLD, 14));
            g.setColor(new Color(250, 204, 21));
            g.drawString("SO SÁNH BÁN KÍNH: THƯỜNG VS BOSS", insetX + 15, insetY + 26);

            g.setFont(new Font("Consolas", Font.PLAIN, 12));
            g.setColor(new Color(226, 232, 240));
            g.drawString("• Rắn thường (Player & AI):", insetX + 15, insetY + 55);
            g.drawString("  Độ rộng = 18.0px (Bán kính = 9.0px)", insetX + 15, insetY + 75);
            g.drawString("  Viền = 20.0px (Lớp stroke +2px)", insetX + 15, insetY + 95);
            g.drawString("• Boss dạng rắn (Python & Dragon):", insetX + 15, insetY + 125);
            g.drawString("  Độ rộng = 28.8px (Hệ số x1.6 cố định)", insetX + 15, insetY + 145);
            g.drawString("  Viền = 30.8px (Lớp stroke +2px)", insetX + 15, insetY + 165);
            g.drawString("• Cả hai đều ĐỘ RỘNG ĐỀU từ đầu đến đuôi", insetX + 15, insetY + 195);
            g.drawString("• Đuôi bo tròn tự nhiên bằng CAP_ROUND", insetX + 15, insetY + 215);

            // Nhãn tên Boss
            g.setFont(new Font("Consolas", Font.BOLD, 13));
            g.setColor(new Color(234, 179, 8));
            g.drawString("THÁI CỔ CỰ TRĂN (GIANT PYTHON) - THÂN ĐỘ RỘNG ĐỀU 28.8px, ĐUÔI BO TRÒN", 50, 210);

            g.setColor(new Color(192, 132, 252));
            g.drawString("HƯ KHÔNG LONG VƯƠNG (VOID DRAGON) - THÂN ĐỘ RỘNG ĐỀU 28.8px, VẢY LAM TINH", 50, 485);

            g.dispose();
            File out4 = new File(ARTIFACT_DIR + "/snake_boss_preview.png");
            ImageIO.write(img, "png", out4);
            System.out.println("Đã lưu: " + out4.getAbsolutePath());
        }

        System.out.println("Tất cả ảnh kiểm thử hiển thị rắn đã hoàn tất hoàn hảo!");
        System.exit(0);
    }
}
