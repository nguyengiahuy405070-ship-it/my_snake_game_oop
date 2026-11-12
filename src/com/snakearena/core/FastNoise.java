package com.snakearena.core;

/**
 * Bộ sinh nhiễu Perlin 2D và Seeded Random xác định thuần Java.
 * Giúp sinh thế giới, quần xã địa hình, hồ nước sinh thái nhất quán theo tọa độ chunk.
 */
public class FastNoise {
    private final int seed;
    private final int[] perm = new int[512];

    public FastNoise(int seed) {
        this.seed = seed;
        initPermutation();
    }

    private void initPermutation() {
        int[] permutation = new int[256];
        for (int i = 0; i < 256; i++) {
            permutation[i] = i;
        }

        // Fisher-Yates shuffle bằng Mulberry32
        Mulberry32 rng = new Mulberry32(seed);
        for (int i = 255; i > 0; i--) {
            int j = (int) (rng.nextFloat() * (i + 1));
            int temp = permutation[i];
            permutation[i] = permutation[j];
            permutation[j] = temp;
        }

        for (int i = 0; i < 512; i++) {
            perm[i] = permutation[i & 255];
        }
    }

    public double noise2D(double x, double y) {
        int X = ((int) Math.floor(x)) & 255;
        int Y = ((int) Math.floor(y)) & 255;

        double xf = x - Math.floor(x);
        double yf = y - Math.floor(y);

        double u = fade(xf);
        double v = fade(yf);

        int aa = perm[perm[X] + Y];
        int ab = perm[perm[X] + Y + 1];
        int ba = perm[perm[X + 1] + Y];
        int bb = perm[perm[X + 1] + Y + 1];

        double x1 = lerp(grad(aa, xf, yf), grad(ba, xf - 1, yf), u);
        double x2 = lerp(grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1), u);

        return (lerp(x1, x2, v) + 1.0) * 0.5; // Chuẩn hóa về [0, 1]
    }

    private double fade(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    private double grad(int hash, double x, double y) {
        int h = hash & 3;
        double u = h < 2 ? x : y;
        double v = h < 2 ? y : x;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    /**
     * Thuật toán băm tọa độ chunk (cx, cy) với worldSeed thành seed số nguyên.
     */
    public static int hashCoords(int cx, int cy, int worldSeed) {
        int h = worldSeed ^ (cx * 73856093) ^ (cy * 19349663);
        h = (h ^ (h >>> 16)) * 0x85ebca6b;
        h = (h ^ (h >>> 13)) * 0xc2b2ae35;
        return (h ^ (h >>> 16));
    }

    /**
     * Mulberry32 Seeded Random Generator.
     */
    public static class Mulberry32 {
        private int a;

        public Mulberry32(int seed) {
            this.a = seed;
        }

        public double nextFloat() {
            int t = (a += 0x6D2B79F5);
            t = (int) (((long) (t ^ (t >>> 15)) * (long) (t | 1)) & 0xFFFFFFFFL);
            t ^= t + (int) (((long) (t ^ (t >>> 7)) * 61L) & 0xFFFFFFFFL);
            long res = ((long) (t ^ (t >>> 14))) & 0xFFFFFFFFL;
            return res / 4294967296.0;
        }
    }
}
