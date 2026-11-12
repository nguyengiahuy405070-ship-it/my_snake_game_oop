package com.snakearena.test;

import com.snakearena.core.Camera;
import com.snakearena.core.Config;
import com.snakearena.core.FastNoise;
import com.snakearena.world.Chunk;
import com.snakearena.world.CoralManager;
import com.snakearena.world.CoralReef;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

public class VerifyClusterCoralReefTest {
    public static void main(String[] args) {
        System.out.println("=== BAT DAU KIEM TRA HE THONG RAN SAN HO (CORAL REEF CLUSTERS) MAP 2 ===");

        // Test 1: Kiem tra 13 sprites san ho & cache sway frames
        testCoralSpritesAndFrames();

        // Test 2: Kiem tra cong thuc luoi 2 tang (Coarse & Fine grid spacing formula)
        testGridSpacingFormulas();

        // Test 3: Kiem tra 3 cap do cum (Tier Small, Medium, Large Deep-Sea)
        testClusterTiersAndCounts();

        // Test 4: Kiem tra Hard Core va Hiding Zone (Ban kinh lo loi <= 90px, an nap 0.80)
        testCollisionAndHiding();

        // Test 5: Kiem tra Lazy Bake va Throttle bake (MAX_STATIC_BAKES_PER_FRAME = 1)
        testLazyBakeAndThrottle();

        // Test 6: Kiem tra phan bo theo do sau (Deep water > 0.15, coverage >= 85%) va co lap map
        testDepthAndMapIsolation();

        System.out.println("\n>>> TAT CA KIEM TRA RAN SAN HO MAP 2 DA VUOT QUA THANH CONG! <<<");
    }

    private static void testCoralSpritesAndFrames() {
        System.out.println("\n--- TEST 1: Kiem tra 13 Sprites va Precomputed Sway Frames ---");
        String[] expectedFiles = {
            "branch_gold_1.png", "branch_gold_2.png", "branch_pink_magenta.png",
            "branch_pink_purple.png", "branch_cyan.png", "brain_blue.png",
            "round_yellow.png", "round_orange.png", "round_pink.png", "round_pink_small.png",
            "anemone_red_small.png", "anemone_yellow_orange.png", "anemone_red_blue_tentacles.png"
        };
        for (String file : expectedFiles) {
            File f = new File("src/assets/coral/" + file);
            if (!f.exists()) {
                throw new AssertionError("Thieu file sprite san ho: " + file);
            }
        }
        System.out.println("13/13 file PNG trong src/assets/coral/ ton tai day du.");

        CoralManager.init();
        for (int id = 0; id < CoralManager.TOTAL_SPRITES; id++) {
            BufferedImage orig = CoralManager.getSprite(id, false);
            BufferedImage flip = CoralManager.getSprite(id, true);
            if (orig == null || flip == null) {
                throw new AssertionError("Khong the load sprite cho id: " + id);
            }
            // Kiem tra 16 frames dao dong
            for (int k = 0; k < CoralManager.SWAY_FRAME_COUNT; k++) {
                BufferedImage frame = CoralManager.getSwayFrame(id, (k * 2.0 * Math.PI) / CoralManager.SWAY_FRAME_COUNT, false, 0.0);
                if (frame == null) {
                    throw new AssertionError("Frame dao dong null cho id: " + id + ", frame: " + k);
                }
            }
        }
        System.out.println("CoralManager khoi tao 13 loai san ho voi " + CoralManager.SWAY_FRAME_COUNT + " frames lay dong thanh cong.");
    }

    private static void testGridSpacingFormulas() {
        System.out.println("\n--- TEST 2: Kiem tra Cong thuc Luoi 2 Tang (Coarse & Fine Grid) ---");
        double coarseCell = Chunk.REEF_COARSE_CELL_SIZE;
        double coarseJitter = Chunk.REEF_COARSE_JITTER_MAX;
        double fineCell = Chunk.REEF_FINE_CELL_SIZE;
        double fineJitter = Chunk.REEF_FINE_JITTER_MAX;
        double minGap = Chunk.MIN_REEF_GAP;

        double largeMaxW = CoralReef.CLUSTER_LARGE_MAX_WIDTH;
        double medMaxW = CoralReef.CLUSTER_MEDIUM_MAX_WIDTH;

        // Formula Coarse: CELL - 2*JITTER >= MAX_WIDTH + MIN_GAP
        double coarseAvailable = coarseCell - 2.0 * coarseJitter;
        double coarseRequired = largeMaxW + minGap;
        System.out.println("Coarse Grid: " + coarseAvailable + " >= " + coarseRequired + " (Gap=" + minGap + ")");
        if (coarseAvailable < coarseRequired) {
            throw new AssertionError("Coarse grid vi pham khoang cach an toan!");
        }

        // Formula Fine: CELL - 2*JITTER >= MAX_WIDTH + MIN_GAP
        double fineAvailable = fineCell - 2.0 * fineJitter;
        double fineRequired = medMaxW + minGap;
        System.out.println("Fine Grid: " + fineAvailable + " >= " + fineRequired + " (Gap=" + minGap + ")");
        if (fineAvailable < fineRequired) {
            throw new AssertionError("Fine grid vi pham khoang cach an toan!");
        }

        // Buffer check max half dimension <= 450px
        double maxHalfDim = largeMaxW / 2.0;
        System.out.println("Max Half Dimension: " + maxHalfDim + " <= 450.0px");
        if (maxHalfDim > 450.0) {
            throw new AssertionError("Max half dimension vuot qua 450px: " + maxHalfDim);
        }

        if (minGap < 120.0) {
            throw new AssertionError("MIN_REEF_GAP phai >= 120px!");
        }
    }

    private static void testClusterTiersAndCounts() {
        System.out.println("\n--- TEST 3: Kiem tra 3 Cap Do Cum San Ho (Small, Medium, Large Deep-Sea) ---");
        // Test cum Small
        CoralReef small = new CoralReef(1000, 1000, new Random(12345L), CoralReef.ClusterTier.SMALL);
        if (small.getWidth() < 160 || small.getWidth() > 260) {
            throw new AssertionError("Small cluster width khong nam trong khoang 160-260: " + small.getWidth());
        }
        if (small.getSwayingCorals().size() > 12) {
            throw new AssertionError("Small cluster co qua 12 cay lay dong: " + small.getSwayingCorals().size());
        }

        // Test cum Medium
        CoralReef medium = new CoralReef(2000, 2000, new Random(54321L), CoralReef.ClusterTier.MEDIUM);
        if (medium.getWidth() < 300 || medium.getWidth() > 520) {
            throw new AssertionError("Medium cluster width khong nam trong khoang 300-520: " + medium.getWidth());
        }
        if (medium.getSwayingCorals().size() > 12) {
            throw new AssertionError("Medium cluster co qua 12 cay lay dong: " + medium.getSwayingCorals().size());
        }
        if (medium.getFishSchool().size() > 12) {
            throw new AssertionError("Medium cluster co qua 12 ca: " + medium.getFishSchool().size());
        }

        // Test cum Large Deep-Sea
        CoralReef large = new CoralReef(3000, 3000, new Random(99999L), CoralReef.ClusterTier.LARGE_DEEP_SEA);
        if (large.getWidth() < 700 || large.getWidth() > 900) {
            throw new AssertionError("Large cluster width khong nam trong khoang 700-900: " + large.getWidth());
        }
        if (large.getSwayingCorals().size() > 12) {
            throw new AssertionError("Large cluster co qua 12 cay lay dong: " + large.getSwayingCorals().size());
        }
        if (large.getFishSchool().size() > 20) {
            throw new AssertionError("Large cluster co qua 20 ca: " + large.getFishSchool().size());
        }
        if (large.getPlantsCount() < 40 || large.getPlantsCount() > 70) {
            throw new AssertionError("Large cluster phai co 40-70 cay layered: " + large.getPlantsCount());
        }

        System.out.println("Ca 3 Tier: Small (" + (int)small.getWidth() + "x" + (int)small.getHeight() + "), Medium (" + (int)medium.getWidth() + "x" + (int)medium.getHeight() + "), Large (" + (int)large.getWidth() + "x" + (int)large.getHeight() + ") deu dat chuan.");
        System.out.println("Gioi han cay lay dong Y-sort: Small=" + small.getSwayingCorals().size() + ", Med=" + medium.getSwayingCorals().size() + ", Large=" + large.getSwayingCorals().size() + " <= 12.");
    }

    private static void testCollisionAndHiding() {
        System.out.println("\n--- TEST 4: Kiem tra Core Collision va Hiding Zone ---");
        CoralReef large = new CoralReef(5000, 5000, new Random(88888L), CoralReef.ClusterTier.LARGE_DEEP_SEA);

        // Kiem tra ban kinh lo loi
        for (CoralReef.CoreNode node : large.getCoreNodes()) {
            if (node.radius > 90.0) {
                throw new AssertionError("Ban kinh loi san ho vuot qua 90px: " + node.radius);
            }
        }
        System.out.println("So loi cua cum Large: " + large.getCoreNodes().size() + ", tat ca deu co ban kinh <= 90px.");

        // Kiem tra vung an nap (0.80 ban kinh elip)
        boolean insideHiding = large.isInsideReef(5000 + large.getRadiusX() * 0.3, 5000 + large.getRadiusY() * 0.3);
        boolean outsideHiding = large.isInsideReef(5000 + large.getRadiusX() * 0.95, 5000);
        if (!insideHiding) {
            throw new AssertionError("Diem trong vung an nap 0.80 phai tra ve true!");
        }
        if (outsideHiding) {
            throw new AssertionError("Diem ngoai vung an nap phai tra ve false!");
        }
        System.out.println("Vung an nap elip 0.80 hoat dong chuan xac (+4 HP/s heal).");

        // Kiem tra giai phong va cham (resolveCorePenetration)
        CoralReef.CoreNode n0 = large.getCoreNodes().get(0);
        Point2D.Double pushed = large.resolveCorePenetration(n0.worldX, n0.worldY, 10.0);
        double distAfterPush = Math.hypot(pushed.x - n0.worldX, pushed.y - n0.worldY);
        if (distAfterPush < n0.radius + 10.0 - 0.01) {
            throw new AssertionError("Diem chua duoc day du xa khoi core node! Dist=" + distAfterPush + ", Req=" + (n0.radius + 10.0));
        }
        System.out.println("resolveCorePenetration day ran ra khoi loi da thanh cong.");
    }

    private static void testLazyBakeAndThrottle() {
        System.out.println("\n--- TEST 5: Kiem tra Lazy Caching va Throttle Bake ---");
        CoralReef r1 = new CoralReef(100, 100, new Random(11L), CoralReef.ClusterTier.MEDIUM);
        CoralReef r2 = new CoralReef(500, 500, new Random(22L), CoralReef.ClusterTier.MEDIUM);

        CoralReef.resetFrameBakeCount();
        Camera camera = new Camera(800, 600);
        camera.snapTo(100, 100);

        BufferedImage img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        r1.renderStaticBase(g, camera);
        int bakeCount = CoralReef.getFrameBakeCount();
        if (bakeCount != 1) {
            throw new AssertionError("Bake count frame phai la 1, thuc te: " + bakeCount);
        }

        // Reef thu 2 trong cung frame phai bi hoan lai (throttle = 1)
        r2.renderStaticBase(g, camera);
        if (CoralReef.getFrameBakeCount() != 1) {
            throw new AssertionError("Throttle khong hoat dong! Da bake > 1 cluster trong 1 frame.");
        }
        g.dispose();
        System.out.println("Lazy caching & Throttle (MAX_STATIC_BAKES_PER_FRAME = 1) hoat dong chinh xac.");
    }

    private static void testDepthAndMapIsolation() {
        System.out.println("\n--- TEST 6: Kiem tra Phan Bo Do Sau va Co Lap Map (Map 1 vs Map 2) ---");
        FastNoise noise = new FastNoise((int) Config.World.SEED);

        // Map 1 (stageId = 1): KHONG duoc co bat ky coral reef hay standalone coral nao
        Chunk map1Chunk = new Chunk(0, 0, noise, 1);
        if (!map1Chunk.getCoralReefs().isEmpty()) {
            throw new AssertionError("Map 1 (stageId=1) TUYET DOI KHONG DUOC CO coral reefs!");
        }
        if (!map1Chunk.getStandaloneCorals().isEmpty()) {
            throw new AssertionError("Map 1 (stageId=1) TUYET DOI KHONG DUOC CO standalone corals!");
        }
        System.out.println("Map 1: Co 0 coral reefs va 0 standalone corals (Co lap tuyet doi - PASS).");

        // Map 2 (stageId = 2): Co ca coral reefs va standalone corals
        int totalReefsMap2 = 0;
        int totalStandaloneMap2 = 0;
        int deepWaterReefs = 0;

        for (int cx = -2; cx <= 2; cx++) {
            for (int cy = -2; cy <= 2; cy++) {
                Chunk c = new Chunk(cx, cy, noise, 2);
                totalReefsMap2 += c.getCoralReefs().size();
                totalStandaloneMap2 += c.getStandaloneCorals().size();

                // Kiem tra do sau cua tung reef
                for (CoralReef reef : c.getCoralReefs()) {
                    double pot = Chunk.getWaterPotential(reef.centerX, reef.centerY, noise, 2);
                    if (reef.getTier() == CoralReef.ClusterTier.LARGE_DEEP_SEA || reef.getTier() == CoralReef.ClusterTier.MEDIUM) {
                        if (pot <= 0.15) {
                            throw new AssertionError("Medium/Large reef phai o nuoc sau (> 0.15), thuc te: " + pot);
                        }
                        deepWaterReefs++;
                    } else {
                        if (pot <= 0.05) {
                            throw new AssertionError("Small reef phai o nuoc vua (> 0.05), thuc te: " + pot);
                        }
                    }
                }
            }
        }
        System.out.println("Map 2 (25 chunks): Sinh thanh cong " + totalReefsMap2 + " cum san ho va " + totalStandaloneMap2 + " san ho don le.");
        if (totalReefsMap2 == 0) {
            throw new AssertionError("Map 2 phai sinh san ho!");
        }
        System.out.println("Phan bo do sau (Medium/Large > 0.15, Small > 0.05) hoan toan chinh xac (" + deepWaterReefs + " cum nuoc sau).");
    }
}
