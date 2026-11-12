package com.snakearena.test;

import com.snakearena.core.Camera;
import com.snakearena.core.FastNoise;
import com.snakearena.entity.PlayerSnake;
import com.snakearena.render.LayeredRenderer;
import com.snakearena.world.Chunk;
import com.snakearena.world.WorldProp;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RenderReefTest {
    public static void main(String[] args) throws Exception {
        int width = 960;
        int height = 500;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Nền nước biển xanh dương đại dương
        g.setColor(new Color(14, 116, 144));
        g.fillRect(0, 0, width, height);

        // Đáy cát biển sâu
        g.setColor(new Color(15, 60, 85));
        g.fillRect(0, 360, width, 140);

        Camera cam = new Camera(width, height);
        cam.snapTo(480, 250);
        PlayerSnake player = new PlayerSnake(480, 260); // Bơi ngang qua mỏm đá số 2
        LayeredRenderer renderer = new LayeredRenderer();

        List<Chunk> chunks = new ArrayList<>();
        FastNoise fn = new FastNoise(1337);
        Chunk c = new Chunk(0, 0, fn, 2);
        c.getProps().clear();

        // 3 mỏm đá ngầm tương ứng 3 biến thể 0, 1, 2
        for (int v = 0; v < 3; v++) {
            double wx = 180 + v * 280;
            double wy = 260;
            WorldProp p = new WorldProp(WorldProp.Type.ROCK, wx, wy, 48.0, 120, 80, v);
            c.getProps().add(p);
        }
        chunks.add(c);

        com.snakearena.render.ParticleSystem particles = new com.snakearena.render.ParticleSystem();
        com.snakearena.render.FloatingTextManager texts = new com.snakearena.render.FloatingTextManager();

        renderer.render(g, width, height, cam, chunks, player,
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), null, new ArrayList<>(),
                particles, texts, 0.016, 2);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        g.setColor(Color.WHITE);
        String[] titles = {
            "Biến thể 0: Mỏm Đôi Đa Tầng",
            "Biến thể 1: Thềm Bậc Thang Rạn Đá",
            "Biến thể 2: Khối Chóp Đơn Lớn"
        };
        for (int i = 0; i < 3; i++) {
            g.drawString(titles[i], 110 + i * 280, 360);
        }

        g.dispose();

        File out = new File("C:/Users/Gia Huy/.gemini/antigravity/brain/654b6c66-ba54-4cb1-9fa5-1e908db71677/reef_preview.png");
        ImageIO.write(img, "PNG", out);
        System.out.println("Rendered successfully to: " + out.getAbsolutePath());
    }
}
