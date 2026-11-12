package com.snakearena.render;

import com.snakearena.core.Camera;
import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Quản lý chữ số bay hiển thị điểm, sát thương, bẫy sập, cảnh báo sinh thái.
 */
public class FloatingTextManager {
    public static class FloatingText {
        double x, y;
        String text;
        Color color;
        double life;
        double maxLife;
        int fontSize;

        public FloatingText(double x, double y, String text, Color color, double duration, int fontSize) {
            this.x = x;
            this.y = y;
            this.text = text;
            this.color = color;
            this.maxLife = duration;
            this.life = duration;
            this.fontSize = fontSize;
        }

        public boolean update(double dt) {
            y -= 24.0 * dt; // Bay từ từ lên trên
            life -= dt;
            return life <= 0;
        }

        public void render(Graphics2D g, Camera camera) {
            Point2D.Double screen = camera.worldToScreen(x, y);
            float alpha = (float) Math.max(0.0, Math.min(1.0, life / maxLife));

            // Đảm bảo chữ bay luôn sắc nét và hỗ trợ 100% tiếng Việt không lỗi font
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(13, fontSize)));
            FontMetrics fm = g.getFontMetrics();
            int strW = fm.stringWidth(text);

            int sx = (int) (screen.x - strW / 2.0);
            int sy = (int) screen.y;

            // Viền đen 8 hướng (Halo outline) tạo độ tương phản tối đa trên mọi nền cỏ, nước, đá
            int shadowAlpha = (int) (alpha * 230);
            if (shadowAlpha > 0) {
                g.setColor(new Color(0, 0, 0, shadowAlpha));
                g.drawString(text, sx - 1, sy);
                g.drawString(text, sx + 1, sy);
                g.drawString(text, sx, sy - 1);
                g.drawString(text, sx, sy + 1);
                g.drawString(text, sx - 1, sy - 1);
                g.drawString(text, sx + 1, sy - 1);
                g.drawString(text, sx - 1, sy + 1);
                g.drawString(text, sx + 1, sy + 1);
            }

            int textAlpha = (int) (alpha * 255);
            if (textAlpha > 0) {
                g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), textAlpha));
                g.drawString(text, sx, sy);
            }
        }
    }

    private final List<FloatingText> texts = new ArrayList<>();

    public void spawn(double x, double y, String text, Color color, double duration, int fontSize) {
        texts.add(new FloatingText(x, y, text, color, duration, fontSize));
    }

    public void update(double dt) {
        Iterator<FloatingText> it = texts.iterator();
        while (it.hasNext()) {
            if (it.next().update(dt)) {
                it.remove();
            }
        }
    }

    public void render(Graphics2D g, Camera camera) {
        int size = texts.size();
        for (int i = 0; i < size; i++) {
            if (i >= texts.size()) break;
            FloatingText t = texts.get(i);
            if (t != null) {
                t.render(g, camera);
            }
        }
    }

    public void clear() {
        texts.clear();
    }
}
