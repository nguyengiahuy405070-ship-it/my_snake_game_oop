package com.snakearena.test;

import com.snakearena.core.GamePanel;
import com.snakearena.render.LayeredRenderer;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class RenderDepthLightingTest {
    public static void main(String[] args) throws Exception {
        int panelW = 1024;
        int panelH = 720;

        System.out.println("Testing Y-Sort, Consistent Shadows, Lighting & Vignette...");

        GamePanel panel = new GamePanel();
        panel.setSize(panelW, panelH);
        panel.startStage(1);
        panel.stopGameLoop();

        // 1. Stage 1 - Daytime (Mặc định: Ban ngày)
        LayeredRenderer.setTimeOfDay(LayeredRenderer.TimeOfDay.DAY);
        BufferedImage imgDay = new BufferedImage(panelW, panelH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gDay = imgDay.createGraphics();
        panel.paint(gDay);
        gDay.dispose();
        File outDay = new File("C:/Users/Gia Huy/.gemini/antigravity/brain/654b6c66-ba54-4cb1-9fa5-1e908db71677/depth_stage1_day.png");
        ImageIO.write(imgDay, "png", outDay);
        System.out.println("Daytime snapshot saved: " + outDay.getAbsolutePath());

        // 2. Stage 1 - Afternoon (Chiều tà - tông ấm)
        LayeredRenderer.setTimeOfDay(LayeredRenderer.TimeOfDay.AFTERNOON);
        BufferedImage imgAfternoon = new BufferedImage(panelW, panelH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gAfternoon = imgAfternoon.createGraphics();
        panel.paint(gAfternoon);
        gAfternoon.dispose();
        File outAfternoon = new File("C:/Users/Gia Huy/.gemini/antigravity/brain/654b6c66-ba54-4cb1-9fa5-1e908db71677/depth_stage1_afternoon.png");
        ImageIO.write(imgAfternoon, "png", outAfternoon);
        System.out.println("Afternoon snapshot saved: " + outAfternoon.getAbsolutePath());

        // 3. Stage 1 - Night (Ban đêm - tông xanh lạnh)
        LayeredRenderer.setTimeOfDay(LayeredRenderer.TimeOfDay.NIGHT);
        BufferedImage imgNight = new BufferedImage(panelW, panelH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gNight = imgNight.createGraphics();
        panel.paint(gNight);
        gNight.dispose();
        File outNight = new File("C:/Users/Gia Huy/.gemini/antigravity/brain/654b6c66-ba54-4cb1-9fa5-1e908db71677/depth_stage1_night.png");
        ImageIO.write(imgNight, "png", outNight);
        System.out.println("Night snapshot saved: " + outNight.getAbsolutePath());

        // Reset về DAY
        LayeredRenderer.setTimeOfDay(LayeredRenderer.TimeOfDay.DAY);

        // 4. Stage 2 - Ocean
        panel.startStage(2);
        panel.stopGameLoop();
        BufferedImage imgStage2 = new BufferedImage(panelW, panelH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = imgStage2.createGraphics();
        panel.paint(g2);
        g2.dispose();
        File outStage2 = new File("C:/Users/Gia Huy/.gemini/antigravity/brain/654b6c66-ba54-4cb1-9fa5-1e908db71677/depth_stage2_day.png");
        ImageIO.write(imgStage2, "png", outStage2);
        System.out.println("Stage 2 snapshot saved: " + outStage2.getAbsolutePath());

        panel.stopGameLoop();
        System.out.println("All depth & lighting tests completed successfully!");
    }
}
