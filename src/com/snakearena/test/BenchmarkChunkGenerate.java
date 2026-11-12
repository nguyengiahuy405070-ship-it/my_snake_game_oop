package com.snakearena.test;

import com.snakearena.core.FastNoise;
import com.snakearena.world.Chunk;

public class BenchmarkChunkGenerate {
    public static void main(String[] args) {
        FastNoise noise = new FastNoise(1337);
        int chunkCount = 10;

        // Warm up JIT (để C2 JIT Compiler hoàn tất biên dịch máy)
        System.out.println("Warming up JIT (25 chunks each)...");
        for (int i = 0; i < 25; i++) {
            new Chunk(i + 100, i + 100, noise, 1);
            new Chunk(i + 100, i + 100, noise, 2);
        }

        // Benchmark Stage 1
        System.out.println("\nBenchmarking Stage 1 (10 chunks)...");
        long totalStage1Ns = 0;
        long minStage1Ns = Long.MAX_VALUE;
        long maxStage1Ns = 0;
        for (int i = 0; i < chunkCount; i++) {
            long t0 = System.nanoTime();
            Chunk c = new Chunk(i, 0, noise, 1);
            long t1 = System.nanoTime();
            long dur = t1 - t0;
            totalStage1Ns += dur;
            if (dur < minStage1Ns) minStage1Ns = dur;
            if (dur > maxStage1Ns) maxStage1Ns = dur;
            System.out.printf("  Stage 1 Chunk %d: %.2f ms\n", i, dur / 1_000_000.0);
        }

        // Benchmark Stage 2
        System.out.println("\nBenchmarking Stage 2 (10 chunks)...");
        long totalStage2Ns = 0;
        long minStage2Ns = Long.MAX_VALUE;
        long maxStage2Ns = 0;
        for (int i = 0; i < chunkCount; i++) {
            long t0 = System.nanoTime();
            Chunk c = new Chunk(i, 0, noise, 2);
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

        System.out.println("\n========================================================");
        System.out.println("BẢNG SO SÁNH THỜI GIAN KHỞI TẠO CHUNK TRƯỚC VÀ SAU TỐI ƯU");
        System.out.println("========================================================");
        System.out.printf("%-10s | %-16s | %-16s | %-14s | %-10s\n",
                "Giai đoạn", "Trước tối ưu", "Sau tối ưu (Avg)", "Nhanh hơn", "Mục tiêu");
        System.out.println("------------------------------------------------------------------------");
        System.out.printf("%-10s | %-16s | %-16.2f | %-14s | %-10s\n",
                "Stage 1", "67.34 ms (JIT)", avg1, String.format("%.1fx", 67.34 / avg1), "< 25.0 ms");
        System.out.printf("%-10s | %-16s | %-16.2f | %-14s | %-10s\n",
                "Stage 2", "71.40 ms (JIT)", avg2, String.format("%.1fx", 71.40 / avg2), "< 25.0 ms");
        System.out.println("========================================================");
    }
}
