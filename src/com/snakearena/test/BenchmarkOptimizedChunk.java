package com.snakearena.test;

import com.snakearena.core.Config;
import com.snakearena.core.FastNoise;
import com.snakearena.world.Chunk;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

public class BenchmarkOptimizedChunk {

    // Int RGB Palette constants (0xRRGGBB)
    public static final int RGB_GRASS_VIBRANT     = 0x408E52;
    public static final int RGB_GRASS_MID         = 0x307242;
    public static final int RGB_GRASS_MOSS        = 0x205236;
    public static final int RGB_GRASS_WARM        = 0x8A7A44;
    public static final int RGB_DIRT_WARM         = 0x6A543A;
    public static final int RGB_DIRT_DRY          = 0x806A4C;

    public static final int RGB_SHORE_WET_MUD     = 0x261E16;
    public static final int RGB_SHORE_SAND        = 0x847252;
    public static final int RGB_SHORE_GRASS_TRANS = 0x4A6238;

    public static final int RGB_WATER_SHALLOW     = 0x24707E;
    public static final int RGB_WATER_MID         = 0x165260;
    public static final int RGB_WATER_DEEP        = 0x0E3644;
    public static final int RGB_WATER_WAVE        = 0x4A9CAC; // (74, 156, 172)
    public static final int RGB_WATER_LILY        = 0x228A4C; // (34, 138, 76)

    public static final int RGB_OCEAN_SHALLOW     = 0x06B6D4;
    public static final int RGB_OCEAN_MID         = 0x0284C7;
    public static final int RGB_OCEAN_DEEP        = 0x075985;
    public static final int RGB_ISLAND_GRASS      = 0x16A34A;
    public static final int RGB_ISLAND_INTERIOR   = 0x22C55E; // (34, 197, 94)

    public static final int RGB_BEACH_WET_MUD     = 0x6E5430;
    public static final int RGB_BEACH_WET         = 0xAC8E58;
    public static final int RGB_BEACH_DRY         = 0xEDD58A;
    public static final int RGB_BEACH_GRAIN       = 0xDAB868;
    public static final int RGB_BEACH_DUNES       = 0xFAE39C;

    public static final int RGB_SEABED_TRENCH     = 0x062036;
    public static final int RGB_SEABED_SHOAL      = 0x1C94A8;
    public static final int RGB_SEABED_SILT       = 0x13586D;
    public static final int RGB_SEABED_RIPPLE     = 0x32A2B8;
    public static final int RGB_SEABED_GRAIN      = 0x0E485C;

    public static final int RGB_SEABED_STARFISH_ORANGE = 0xF97316;
    public static final int RGB_SEABED_STARFISH_RED    = 0xE11D48;
    public static final int RGB_SEABED_SHELL_IVORY     = 0xF8FAFC;
    public static final int RGB_SEABED_SHELL_PINK      = 0xFBCFE8;
    public static final int RGB_SEABED_PEBBLE          = 0x283A48;

    private static final double COS_RIP_ANGLE = Math.cos(0.62);
    private static final double SIN_RIP_ANGLE = Math.sin(0.62);

    private static final GraphicsConfiguration GRAPHICS_CONFIG;
    static {
        GraphicsConfiguration gc = null;
        try {
            if (!GraphicsEnvironment.isHeadless()) {
                gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
            }
        } catch (Throwable t) {
            gc = null;
        }
        GRAPHICS_CONFIG = gc;
    }

    public static BufferedImage createCompatibleImage(int width, int height) {
        if (GRAPHICS_CONFIG != null) {
            try {
                BufferedImage img = GRAPHICS_CONFIG.createCompatibleImage(width, height, Transparency.OPAQUE);
                if (img.getType() == BufferedImage.TYPE_INT_RGB && img.getRaster().getDataBuffer() instanceof DataBufferInt) {
                    return img;
                }
            } catch (Throwable ignored) {}
        }
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    }

    private static double clamp01(double val) {
        return Math.max(0.0, Math.min(1.0, val));
    }

    public static int lerpRgb(int c1, int c2, double t) {
        if (t <= 0.0) return c1;
        if (t >= 1.0) return c2;
        int r1 = (c1 >> 16) & 0xFF;
        int g1 = (c1 >> 8) & 0xFF;
        int b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF;
        int g2 = (c2 >> 8) & 0xFF;
        int b2 = c2 & 0xFF;
        int r = (int) Math.round(r1 + (r2 - r1) * t);
        int g = (int) Math.round(g1 + (g2 - g1) * t);
        int b = (int) Math.round(b1 + (b2 - b1) * t);
        if (r < 0) r = 0; else if (r > 255) r = 255;
        if (g < 0) g = 0; else if (g > 255) g = 255;
        if (b < 0) b = 0; else if (b > 255) b = 255;
        return (r << 16) | (g << 8) | b;
    }

    public static BufferedImage generateOptimized(int cx, int cy, FastNoise worldNoise, int stageId, boolean drawDecals) {
        int size = 512;
        double worldX = (double) cx * size;
        double worldY = (double) cy * size;
        int seed = FastNoise.hashCoords(cx, cy, Config.World.SEED + (stageId - 1) * 10007);
        FastNoise.Mulberry32 rng = new FastNoise.Mulberry32(seed);

        BufferedImage cachedImage = createCompatibleImage(size, size);
        int[] pixels = ((DataBufferInt) cachedImage.getRaster().getDataBuffer()).getData();

        int tilesPerChunk = size / 8; // 64
        int pad = 3;
        int padGridSize = tilesPerChunk + pad * 2; // 70
        double[] rawWater = new double[padGridSize * padGridSize];
        for (int gx = 0; gx < padGridSize; gx++) {
            int tx = gx - pad;
            double wx = worldX + (tx + 0.5) * 8;
            int colOffset = gx * padGridSize;
            for (int gy = 0; gy < padGridSize; gy++) {
                int ty = gy - pad;
                double wy = worldY + (ty + 0.5) * 8;
                rawWater[colOffset + gy] = Chunk.getWaterPotential(wx, wy, worldNoise, stageId);
            }
        }

        double[] smoothWater = new double[padGridSize * padGridSize];
        for (int gx = 1; gx < padGridSize - 1; gx++) {
            int colPrev = (gx - 1) * padGridSize;
            int colCurr = gx * padGridSize;
            int colNext = (gx + 1) * padGridSize;
            for (int gy = 1; gy < padGridSize - 1; gy++) {
                double center = rawWater[colCurr + gy] * 4.0;
                double cross = rawWater[colPrev + gy] + rawWater[colNext + gy] + rawWater[colCurr + gy - 1] + rawWater[colCurr + gy + 1];
                smoothWater[colCurr + gy] = (center + cross) * 0.125;
            }
        }

        int colShallow = (stageId == 2) ? RGB_OCEAN_SHALLOW : RGB_WATER_SHALLOW;
        int colMid     = (stageId == 2) ? RGB_OCEAN_MID     : RGB_WATER_MID;
        int colDeep    = (stageId == 2) ? RGB_OCEAN_DEEP    : RGB_WATER_DEEP;

        // 4x4 low frequency noise grid (129x129)
        int gridDim = 129;
        double[] gridMacro = null;
        double[] gridDetail = null;
        double[] gridDepth = null;
        double[] gridSilt = null;
        double[] gridRip = null;
        double[] gridIsland = null;

        if (stageId == 1) {
            gridMacro = new double[gridDim * gridDim];
            gridDetail = new double[gridDim * gridDim];
            int idx = 0;
            for (int gy = 0; gy < gridDim; gy++) {
                double wy = worldY + gy * 4;
                for (int gx = 0; gx < gridDim; gx++) {
                    double wx = worldX + gx * 4;
                    gridMacro[idx] = Chunk.fbm(worldNoise, wx * Chunk.NOISE_SCALE_BIOME, wy * Chunk.NOISE_SCALE_BIOME, 2);
                    gridDetail[idx] = Chunk.fbm(worldNoise, wx * Chunk.NOISE_SCALE_DETAIL, wy * Chunk.NOISE_SCALE_DETAIL, 2);
                    idx++;
                }
            }
        } else {
            gridDepth = new double[gridDim * gridDim];
            gridSilt = new double[gridDim * gridDim];
            gridRip = new double[gridDim * gridDim];
            gridIsland = new double[gridDim * gridDim];
            int idx = 0;
            for (int gy = 0; gy < gridDim; gy++) {
                double wy = worldY + gy * 4;
                for (int gx = 0; gx < gridDim; gx++) {
                    double wx = worldX + gx * 4;
                    gridDepth[idx] = Chunk.fbm(worldNoise, wx * 0.0035, wy * 0.0035, 2);
                    gridSilt[idx] = worldNoise.noise2D(wx * 0.018, wy * 0.018);
                    gridRip[idx] = worldNoise.noise2D(wx * 0.012, wy * 0.012) * 16.0;
                    gridIsland[idx] = Chunk.fbm(worldNoise, wx * 0.02, wy * 0.02, 2);
                    idx++;
                }
            }
        }

        // Row buffers
        double[] rowWater = new double[size];
        double[] rowMacro = (stageId == 1) ? new double[size] : null;
        double[] rowDetail = (stageId == 1) ? new double[size] : null;
        double[] rowDepth = (stageId == 2) ? new double[size] : null;
        double[] rowSilt = (stageId == 2) ? new double[size] : null;
        double[] rowRip = (stageId == 2) ? new double[size] : null;
        double[] rowIsland = (stageId == 2) ? new double[size] : null;

        for (int y = 0; y < size; y++) {
            int ty = y >> 3;
            int ly = y & 7;
            double vWater = ly * 0.125;
            int gyWater = ty + pad;

            // Interpolate rowWater
            for (int tx = 0; tx < tilesPerChunk; tx++) {
                int gxWater = tx + pad;
                int colCurr = gxWater * padGridSize;
                int colNext = (gxWater + 1) * padGridSize;
                double w00 = smoothWater[colCurr + gyWater];
                double w10 = smoothWater[colNext + gyWater];
                double w01 = smoothWater[colCurr + gyWater + 1];
                double w11 = smoothWater[colNext + gyWater + 1];
                double wLeft = w00 + (w01 - w00) * vWater;
                double wRight = w10 + (w11 - w10) * vWater;
                double wStep = (wRight - wLeft) * 0.125;
                int px = tx << 3;
                for (int lx = 0; lx < 8; lx++) {
                    rowWater[px + lx] = wLeft + lx * wStep;
                }
            }

            // Interpolate 4x4 low frequency noise row
            int by = y >> 2;
            int dy = y & 3;
            double fyNoise = dy * 0.25;
            int gridRowOffset = by * gridDim;

            if (stageId == 1) {
                for (int bx = 0; bx < 128; bx++) {
                    int g00 = gridRowOffset + bx;
                    int px = bx << 2;

                    double m00 = gridMacro[g00], m10 = gridMacro[g00 + 1], m01 = gridMacro[g00 + gridDim], m11 = gridMacro[g00 + gridDim + 1];
                    double mLeft = m00 + (m01 - m00) * fyNoise, mRight = m10 + (m11 - m10) * fyNoise, mStep = (mRight - mLeft) * 0.25;

                    double d00 = gridDetail[g00], d10 = gridDetail[g00 + 1], d01 = gridDetail[g00 + gridDim], d11 = gridDetail[g00 + gridDim + 1];
                    double dLeft = d00 + (d01 - d00) * fyNoise, dRight = d10 + (d11 - d10) * fyNoise, dStep = (dRight - dLeft) * 0.25;

                    rowMacro[px] = mLeft; rowMacro[px + 1] = mLeft + mStep; rowMacro[px + 2] = mLeft + 2 * mStep; rowMacro[px + 3] = mLeft + 3 * mStep;
                    rowDetail[px] = dLeft; rowDetail[px + 1] = dLeft + dStep; rowDetail[px + 2] = dLeft + 2 * dStep; rowDetail[px + 3] = dLeft + 3 * dStep;
                }
            } else {
                for (int bx = 0; bx < 128; bx++) {
                    int g00 = gridRowOffset + bx;
                    int px = bx << 2;

                    double dp00 = gridDepth[g00], dp10 = gridDepth[g00 + 1], dp01 = gridDepth[g00 + gridDim], dp11 = gridDepth[g00 + gridDim + 1];
                    double dpLeft = dp00 + (dp01 - dp00) * fyNoise, dpRight = dp10 + (dp11 - dp10) * fyNoise, dpStep = (dpRight - dpLeft) * 0.25;

                    double s00 = gridSilt[g00], s10 = gridSilt[g00 + 1], s01 = gridSilt[g00 + gridDim], s11 = gridSilt[g00 + gridDim + 1];
                    double sLeft = s00 + (s01 - s00) * fyNoise, sRight = s10 + (s11 - s10) * fyNoise, sStep = (sRight - sLeft) * 0.25;

                    double r00 = gridRip[g00], r10 = gridRip[g00 + 1], r01 = gridRip[g00 + gridDim], r11 = gridRip[g00 + gridDim + 1];
                    double rLeft = r00 + (r01 - r00) * fyNoise, rRight = r10 + (r11 - r10) * fyNoise, rStep = (rRight - rLeft) * 0.25;

                    double is00 = gridIsland[g00], is10 = gridIsland[g00 + 1], is01 = gridIsland[g00 + gridDim], is11 = gridIsland[g00 + gridDim + 1];
                    double isLeft = is00 + (is01 - is00) * fyNoise, isRight = is10 + (is11 - is10) * fyNoise, isStep = (isRight - isLeft) * 0.25;

                    rowDepth[px] = dpLeft; rowDepth[px + 1] = dpLeft + dpStep; rowDepth[px + 2] = dpLeft + 2 * dpStep; rowDepth[px + 3] = dpLeft + 3 * dpStep;
                    rowSilt[px] = sLeft; rowSilt[px + 1] = sLeft + sStep; rowSilt[px + 2] = sLeft + 2 * sStep; rowSilt[px + 3] = sLeft + 3 * sStep;
                    rowRip[px] = rLeft; rowRip[px + 1] = rLeft + rStep; rowRip[px + 2] = rLeft + 2 * rStep; rowRip[px + 3] = rLeft + 3 * rStep;
                    rowIsland[px] = isLeft; rowIsland[px + 1] = isLeft + isStep; rowIsland[px + 2] = isLeft + 2 * isStep; rowIsland[px + 3] = isLeft + 3 * isStep;
                }
            }

            int rowOffset = y * size;
            double wy = worldY + y;
            double wyWaveTerm = wy * 0.08;
            double wyRipTerm = wy * SIN_RIP_ANGLE;
            int lModY = (int) Math.abs(wy) % 6;

            for (int x = 0; x < size; x++) {
                double waterVal = rowWater[x];
                double wx = worldX + x;
                int pixelCol;

                if (waterVal > 0.0) {
                    if (stageId == 2) {
                        double depthRatio = clamp01(waterVal * 4.0); // waterVal / 0.25
                        int oceanBase;
                        if (depthRatio < 0.35) {
                            oceanBase = lerpRgb(RGB_OCEAN_SHALLOW, RGB_OCEAN_MID, depthRatio / 0.35);
                        } else {
                            oceanBase = lerpRgb(RGB_OCEAN_MID, RGB_OCEAN_DEEP, (depthRatio - 0.35) / 0.65);
                        }

                        double nDepth = rowDepth[x];
                        if (nDepth < 0.32) {
                            double trenchF = clamp01((0.32 - nDepth) / 0.16) * 0.50;
                            oceanBase = lerpRgb(oceanBase, RGB_SEABED_TRENCH, trenchF);
                        } else if (nDepth > 0.65) {
                            double shoalF = clamp01((nDepth - 0.65) * 5.0) * 0.40;
                            oceanBase = lerpRgb(oceanBase, RGB_SEABED_SHOAL, shoalF);
                        }

                        double nSilt = rowSilt[x];
                        if (nSilt > 0.54) {
                            oceanBase = lerpRgb(oceanBase, RGB_SEABED_SILT, (nSilt - 0.54) * 0.55);
                        }

                        double ripCoord = wx * COS_RIP_ANGLE + wyRipTerm;
                        double ripPerturb = rowRip[x];
                        double ripWave = Math.sin((ripCoord + ripPerturb) * 0.18);
                        if (ripWave > 0.80) {
                            oceanBase = lerpRgb(oceanBase, RGB_SEABED_RIPPLE, 0.26);
                        }

                        double nGrain = worldNoise.noise2D(wx * 0.14, wy * 0.14);
                        if (nGrain > 0.62) {
                            oceanBase = lerpRgb(oceanBase, RGB_SEABED_GRAIN, 0.12);
                        }

                        pixelCol = oceanBase;
                    } else {
                        double depthRatio = clamp01(waterVal / 0.22);
                        if (depthRatio < 0.45) {
                            pixelCol = lerpRgb(colShallow, colMid, depthRatio / 0.45);
                        } else {
                            pixelCol = lerpRgb(colMid, colDeep, (depthRatio - 0.45) / 0.55);
                        }

                        double waveNoise = Math.sin(wx * 0.12 + wyWaveTerm);
                        if (waveNoise > 0.78) {
                            pixelCol = lerpRgb(pixelCol, RGB_WATER_WAVE, 0.35);
                        }

                        if (waterVal < 0.045) {
                            double lilyNoise = worldNoise.noise2D(wx * 0.08, wy * 0.08);
                            if (lilyNoise > 0.68) {
                                int lModX = (int) Math.abs(wx) % 6;
                                if (lModX >= 1 && lModX <= 4 && lModY >= 1 && lModY <= 4 && (lModX != 4 || lModY != 2)) {
                                    pixelCol = RGB_WATER_LILY;
                                }
                            }
                        }
                    }
                } else {
                    double shoreWobble = (waterVal >= -0.190) ? (worldNoise.noise2D(wx * 0.04, wy * 0.04) - 0.5) * 0.035 : 0.0;

                    if (stageId == 2) {
                        double beachDist = -waterVal;
                        double sandNoise = worldNoise.noise2D(wx * 0.12, wy * 0.12);

                        if (beachDist < 0.030) {
                            double t = clamp01(beachDist / 0.030);
                            pixelCol = lerpRgb(RGB_BEACH_WET_MUD, RGB_BEACH_WET, t);
                        } else if (beachDist < 0.080 + shoreWobble * 0.5) {
                            double t = clamp01((beachDist - 0.030) / (0.050 + shoreWobble * 0.5));
                            pixelCol = lerpRgb(RGB_BEACH_WET, RGB_BEACH_DRY, t);
                        } else if (beachDist < 0.140 + shoreWobble) {
                            int baseSand = RGB_BEACH_DRY;
                            if (sandNoise > 0.58) {
                                baseSand = lerpRgb(baseSand, RGB_BEACH_GRAIN, (sandNoise - 0.58) * 0.55);
                            } else if (sandNoise < 0.38) {
                                baseSand = lerpRgb(baseSand, RGB_BEACH_DUNES, (0.38 - sandNoise) * 0.50);
                            }
                            double beachRipple = Math.sin(wx * 0.09 + wy * 0.15 + worldNoise.noise2D(wx * 0.03, wy * 0.03) * 6.0);
                            if (beachRipple > 0.82) {
                                baseSand = lerpRgb(baseSand, RGB_BEACH_GRAIN, 0.25);
                            }
                            pixelCol = baseSand;
                        } else if (beachDist < 0.190 + shoreWobble) {
                            double t = clamp01((beachDist - 0.140) / 0.050);
                            double grassNoise = worldNoise.noise2D(wx * 0.06, wy * 0.06) + (sandNoise - 0.5) * 0.35;
                            double blendFactor = clamp01(t + grassNoise * 0.40);
                            pixelCol = lerpRgb(RGB_BEACH_DRY, RGB_ISLAND_GRASS, blendFactor);
                        } else {
                            double nIsland = rowIsland[x];
                            pixelCol = lerpRgb(RGB_ISLAND_GRASS, RGB_ISLAND_INTERIOR, (nIsland - 0.5) * 0.30);
                        }
                    } else {
                        if (waterVal >= -0.032) {
                            pixelCol = RGB_SHORE_WET_MUD;
                        } else if (waterVal >= -0.095 + shoreWobble) {
                            pixelCol = RGB_SHORE_SAND;
                        } else if (waterVal >= -0.155 + shoreWobble) {
                            boolean dither = ((x + y) & 1) == 0;
                            pixelCol = dither ? RGB_SHORE_SAND : RGB_SHORE_GRASS_TRANS;
                        } else {
                            double nMacro = rowMacro[x];
                            double nDetail = rowDetail[x];

                            int grassBase;
                            if (nMacro > 0.58) {
                                grassBase = lerpRgb(RGB_GRASS_MID, RGB_GRASS_WARM, clamp01((nMacro - 0.58) / 0.22));
                            } else if (nMacro < 0.42) {
                                grassBase = lerpRgb(RGB_GRASS_MOSS, RGB_GRASS_MID, clamp01(nMacro / 0.42));
                            } else {
                                grassBase = lerpRgb(RGB_GRASS_MID, RGB_GRASS_VIBRANT, clamp01((nMacro - 0.42) / 0.16));
                            }

                            int dirtBase = lerpRgb(RGB_DIRT_WARM, RGB_DIRT_DRY, nDetail);

                            double dirtBlend = clamp01((nDetail - 0.52) / 0.18);
                            if (dirtBlend > 0.0) {
                                boolean dither = ((x + y) & 1) == 0;
                                pixelCol = dither ? lerpRgb(grassBase, dirtBase, dirtBlend) : grassBase;
                            } else {
                                double nColor = Chunk.fbm(worldNoise, wx * Chunk.NOISE_SCALE_COLOR, wy * Chunk.NOISE_SCALE_COLOR, Chunk.NOISE_OCTAVES);
                                pixelCol = lerpRgb(grassBase, RGB_GRASS_VIBRANT, (nColor - 0.5) * 0.25);
                            }
                        }
                    }
                }

                pixels[rowOffset + x] = pixelCol;
            }
        }

        // Draw decals if enabled
        if (drawDecals) {
            Graphics2D g2d = cachedImage.createGraphics();
            int decalCount = Chunk.DECAL_MIN_COUNT + (int) (rng.nextFloat() * (Chunk.DECAL_MAX_COUNT - Chunk.DECAL_MIN_COUNT + 1));
            for (int i = 0; i < decalCount; i++) {
                double dx = 12 + rng.nextFloat() * (size - 24);
                double dy = 12 + rng.nextFloat() * (size - 24);
                double dwx = worldX + dx;
                double dwy = worldY + dy;

                if (Chunk.isWaterAt(dwx, dwy, worldNoise, stageId)) {
                    if (stageId == 2) {
                        int idxX = (int) Math.round(dx);
                        int idxY = (int) Math.round(dy);
                        double dType = rng.nextFloat();
                        if (dType < 0.28) {
                            boolean isOrange = rng.nextFloat() < 0.5;
                            g2d.setColor(isOrange ? Chunk.COLOR_SEABED_STARFISH_ORANGE : Chunk.COLOR_SEABED_STARFISH_RED);
                            g2d.fillRect(idxX, idxY, 1, 1);
                            g2d.fillRect(idxX, idxY - 2, 1, 1);
                            g2d.fillRect(idxX + 2, idxY - 1, 1, 1);
                            g2d.fillRect(idxX + 1, idxY + 2, 1, 1);
                            g2d.fillRect(idxX - 1, idxY + 2, 1, 1);
                            g2d.fillRect(idxX - 2, idxY - 1, 1, 1);
                            g2d.setColor(new Color(254, 215, 170));
                            g2d.fillRect(idxX, idxY, 1, 1);
                        } else if (dType < 0.62) {
                            boolean isPink = rng.nextFloat() < 0.5;
                            Color mainCol = isPink ? Chunk.COLOR_SEABED_SHELL_PINK : Chunk.COLOR_SEABED_SHELL_IVORY;
                            Color rimCol  = isPink ? new Color(244, 114, 182) : new Color(203, 213, 225);
                            g2d.setColor(mainCol);
                            g2d.fillRect(idxX - 1, idxY - 1, 3, 2);
                            g2d.fillRect(idxX, idxY + 1, 1, 1);
                            g2d.setColor(rimCol);
                            g2d.drawLine(idxX - 1, idxY - 2, idxX + 1, idxY - 2);
                        } else {
                            g2d.setColor(Chunk.COLOR_SEABED_PEBBLE);
                            int pw = (rng.nextFloat() > 0.5) ? 3 : 2;
                            g2d.fillRect(idxX, idxY, pw, 2);
                            g2d.setColor(new Color(74, 102, 120));
                            g2d.fillRect(idxX, idxY, 1, 1);
                        }
                    }
                    continue;
                }

                int idxX = (int) Math.round(dx);
                int idxY = (int) Math.round(dy);
                double dType = rng.nextFloat();

                if (dType < 0.32) {
                    g2d.setColor(new Color(94, 186, 114));
                    g2d.fillRect(idxX, idxY, 1, 3);
                    g2d.fillRect(idxX + 1, idxY - 1, 1, 4);
                    g2d.fillRect(idxX + 2, idxY + 1, 1, 2);
                } else if (dType < 0.56) {
                    Color flowerCol = rng.nextFloat() < 0.5 ? new Color(254, 240, 138) : new Color(241, 245, 249);
                    g2d.setColor(flowerCol);
                    g2d.fillRect(idxX, idxY - 1, 1, 3);
                    g2d.fillRect(idxX - 1, idxY, 3, 1);
                    g2d.setColor(new Color(245, 158, 11));
                    g2d.fillRect(idxX, idxY, 1, 1);
                } else if (dType < 0.78) {
                    g2d.setColor(new Color(71, 85, 105));
                    g2d.fillRect(idxX, idxY, 3, 2);
                    g2d.setColor(new Color(148, 163, 184));
                    g2d.fillRect(idxX, idxY, 1, 1);
                } else {
                    g2d.setColor(new Color(180, 130, 60));
                    g2d.fillRect(idxX, idxY, 2, 2);
                    g2d.setColor(new Color(146, 64, 14));
                    g2d.fillRect(idxX + 1, idxY + 1, 1, 1);
                }
            }
            g2d.dispose();
        }

        return cachedImage;
    }

    public static void main(String[] args) {
        FastNoise noise = new FastNoise(1337);

        // Verify visual difference between original Chunk.generate and generateOptimized (WITH DECALS)
        System.out.println("Comparing pixel accuracy with identical decals...");
        for (int stage = 1; stage <= 2; stage++) {
            Chunk orig = new Chunk(0, 0, noise, stage);
            BufferedImage origImg = orig.getCachedImage();
            BufferedImage optImg = generateOptimized(0, 0, noise, stage, true);

            int maxDiff = 0;
            long totalDiff = 0;
            int diffPixelCount = 0;

            int[] diffHisto = new int[256];
            for (int y = 0; y < 512; y++) {
                for (int x = 0; x < 512; x++) {
                    int c1 = origImg.getRGB(x, y);
                    int c2 = optImg.getRGB(x, y);
                    int r1 = (c1 >> 16) & 0xFF, r2 = (c2 >> 16) & 0xFF;
                    int g1 = (c1 >> 8) & 0xFF, g2 = (c2 >> 8) & 0xFF;
                    int b1 = c1 & 0xFF, b2 = c2 & 0xFF;
                    int dr = Math.abs(r1 - r2);
                    int dg = Math.abs(g1 - g2);
                    int db = Math.abs(b1 - b2);
                    int diff = Math.max(dr, Math.max(dg, db));
                    diffHisto[diff]++;
                    if (diff > maxDiff) maxDiff = diff;
                    if (diff > 0) {
                        diffPixelCount++;
                        totalDiff += diff;
                    }
                }
            }
            System.out.printf("Stage %d Visual Comparison: Max channel diff = %d, diff pixels = %d / 262144 (%.2f%%), avg diff on changed = %.2f\n",
                    stage, maxDiff, diffPixelCount, (diffPixelCount * 100.0 / 262144.0),
                    diffPixelCount > 0 ? (totalDiff / (double) diffPixelCount) : 0.0);
            System.out.printf("  Diff=0: %d (%.2f%%), Diff=1: %d (%.2f%%), Diff=2: %d (%.2f%%), Diff>=3: %d (%.2f%%)\n",
                    diffHisto[0], diffHisto[0] * 100.0 / 262144,
                    diffHisto[1], diffHisto[1] * 100.0 / 262144,
                    diffHisto[2], diffHisto[2] * 100.0 / 262144,
                    (262144 - diffHisto[0] - diffHisto[1] - diffHisto[2]), (262144 - diffHisto[0] - diffHisto[1] - diffHisto[2]) * 100.0 / 262144);

        }

        // Benchmark generateOptimized
        System.out.println("\nWarming up JIT for generateOptimized...");
        for (int i = 0; i < 5; i++) {
            generateOptimized(i + 100, i + 100, noise, 1, true);
            generateOptimized(i + 100, i + 100, noise, 2, true);
        }

        int chunkCount = 10;
        System.out.println("\nBenchmarking generateOptimized Stage 1 (10 chunks)...");
        long totalStage1Ns = 0;
        long minStage1Ns = Long.MAX_VALUE;
        long maxStage1Ns = 0;
        for (int i = 0; i < chunkCount; i++) {
            long t0 = System.nanoTime();
            generateOptimized(i, 0, noise, 1, true);
            long t1 = System.nanoTime();
            long dur = t1 - t0;
            totalStage1Ns += dur;
            if (dur < minStage1Ns) minStage1Ns = dur;
            if (dur > maxStage1Ns) maxStage1Ns = dur;
            System.out.printf("  Stage 1 Chunk %d: %.2f ms\n", i, dur / 1_000_000.0);
        }

        System.out.println("\nBenchmarking generateOptimized Stage 2 (10 chunks)...");
        long totalStage2Ns = 0;
        long minStage2Ns = Long.MAX_VALUE;
        long maxStage2Ns = 0;
        for (int i = 0; i < chunkCount; i++) {
            long t0 = System.nanoTime();
            generateOptimized(i, 0, noise, 2, true);
            long t1 = System.nanoTime();
            long dur = t1 - t0;
            totalStage2Ns += dur;
            if (dur < minStage2Ns) minStage2Ns = dur;
            if (dur > maxStage2Ns) maxStage2Ns = dur;
            System.out.printf("  Stage 2 Chunk %d: %.2f ms\n", i, dur / 1_000_000.0);
        }

        double avg1 = (totalStage1Ns / (double) chunkCount) / 1_000_000.0;
        double min1 = minStage1Ns / 1_000_000.0;
        double max1 = maxStage1Ns / 1_000_000.0;

        double avg2 = (totalStage2Ns / (double) chunkCount) / 1_000_000.0;
        double min2 = minStage2Ns / 1_000_000.0;
        double max2 = maxStage2Ns / 1_000_000.0;

        System.out.println("\n=== OPTIMIZED RESULTS ===");
        System.out.printf("Stage 1: Avg = %.2f ms (Min = %.2f ms, Max = %.2f ms)\n", avg1, min1, max1);
        System.out.printf("Stage 2: Avg = %.2f ms (Min = %.2f ms, Max = %.2f ms)\n", avg2, min2, max2);
    }
}
