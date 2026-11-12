package com.snakearena.world;

import com.snakearena.core.Camera;
import com.snakearena.core.Config;
import com.snakearena.core.FastNoise;
import com.snakearena.entity.Boss;
import java.util.*;

/**
 * Quản lý toàn bộ hệ thống Chunk của thế giới vô hạn.
 * Xử lý tải/hủy chunk theo tầm nhìn của camera, kiểm tra va chạm, cạm bẫy, bụi ngụy trang và hồ nước.
 */
public class ChunkManager {
    private final Map<String, Chunk> chunks = new HashMap<>();
    private final FastNoise worldNoise;
    private final int stageId;

    public ChunkManager() {
        this(1);
    }

    public ChunkManager(int stageId) {
        this.stageId = stageId;
        this.worldNoise = new FastNoise(Config.World.SEED + (stageId - 1) * 7919);
    }

    public int getStageId() {
        return stageId;
    }

    private String getChunkKey(int cx, int cy) {
        return cx + "," + cy;
    }

    public Chunk getOrCreateChunk(int cx, int cy) {
        String key = getChunkKey(cx, cy);
        Chunk chunk = chunks.get(key);
        if (chunk == null) {
            chunk = new Chunk(cx, cy, worldNoise, stageId);
            chunks.put(key, chunk);
        }
        return chunk;
    }

    public List<Chunk> updateActiveChunks(Camera camera, double dt) {
        Camera.ViewBounds bounds = camera.getViewBounds(Config.World.CHUNK_SIZE * Config.World.BUFFER_CHUNKS);
        int minCx = (int) Math.floor(bounds.left / Config.World.CHUNK_SIZE);
        int maxCx = (int) Math.floor(bounds.right / Config.World.CHUNK_SIZE);
        int minCy = (int) Math.floor(bounds.top / Config.World.CHUNK_SIZE);
        int maxCy = (int) Math.floor(bounds.bottom / Config.World.CHUNK_SIZE);

        Set<String> activeKeys = new HashSet<>();
        List<Chunk> activeChunks = new ArrayList<>();

        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cy = minCy; cy <= maxCy; cy++) {
                String key = getChunkKey(cx, cy);
                activeKeys.add(key);
                Chunk chunk = getOrCreateChunk(cx, cy);
                if (dt > 0) {
                    chunk.update(dt);
                }
                activeChunks.add(chunk);
            }
        }

        // Giải phóng các chunk ở quá xa khi vượt quá bộ nhớ cache
        if (chunks.size() > Config.World.MAX_CACHED_CHUNKS) {
            Iterator<Map.Entry<String, Chunk>> it = chunks.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Chunk> entry = it.next();
                if (!activeKeys.contains(entry.getKey())) {
                    entry.getValue().dispose();
                    it.remove();
                }
            }
        }

        return activeChunks;
    }

    public static class CollisionResult {
        public final boolean collided;
        public final WorldProp prop;
        public CollisionResult(boolean collided, WorldProp prop) {
            this.collided = collided;
            this.prop = prop;
        }
    }

    public CollisionResult checkCollision(double x, double y, double radius) {
        int cx = (int) Math.floor(x / Config.World.CHUNK_SIZE);
        int cy = (int) Math.floor(y / Config.World.CHUNK_SIZE);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                Chunk chunk = chunks.get(getChunkKey(cx + dx, cy + dy));
                if (chunk == null) continue;

                // 1. Kiểm tra va chạm với vật thể Wall
                for (WorldProp prop : chunk.getProps()) {
                    if (prop.getType() != WorldProp.Type.WALL) continue;

                    double dist = Math.hypot(x - prop.getX(), y - prop.getY());
                    if (dist < radius + prop.getRadius()) {
                        return new CollisionResult(true, prop);
                    }
                }

                // 2. Kiểm tra va chạm với lõi rạn san hô cứng (Stage 2)
                for (CoralReef reef : chunk.getCoralReefs()) {
                    if (reef.isCollidingWithCore(x, y, radius)) {
                        return new CollisionResult(true, null);
                    }
                }
            }
        }
        return new CollisionResult(false, null);
    }

    public WorldProp getNearbyCaveEntrance(double x, double y, double distThreshold) {
        int cx = (int) Math.floor(x / Config.World.CHUNK_SIZE);
        int cy = (int) Math.floor(y / Config.World.CHUNK_SIZE);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                Chunk chunk = chunks.get(getChunkKey(cx + dx, cy + dy));
                if (chunk == null) continue;

                for (WorldProp prop : chunk.getProps()) {
                    if (prop.getType() == WorldProp.Type.CAVE) {
                        double dist = Math.hypot(x - prop.getX(), y - prop.getY());
                        if (dist < distThreshold) {
                            return prop;
                        }
                    }
                }
            }
        }
        return null;
    }

    public WorldProp getRandomDestinationCave(WorldProp currentCave, double playerX, double playerY) {
        List<WorldProp> candidates = new ArrayList<>();
        for (Chunk c : chunks.values()) {
            for (WorldProp p : c.getProps()) {
                if (p.getType() == WorldProp.Type.CAVE && p != currentCave) {
                    candidates.add(p);
                }
            }
        }
        if (candidates.isEmpty()) {
            int cx = (int) Math.floor(playerX / Config.World.CHUNK_SIZE);
            int cy = (int) Math.floor(playerY / Config.World.CHUNK_SIZE);
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -3; dy <= 3; dy++) {
                    if (Math.abs(dx) <= 1 && Math.abs(dy) <= 1) continue;
                    Chunk c = getOrCreateChunk(cx + dx, cy + dy);
                    for (WorldProp p : c.getProps()) {
                        if (p.getType() == WorldProp.Type.CAVE && p != currentCave) {
                            candidates.add(p);
                        }
                    }
                }
            }
        }
        if (candidates.isEmpty()) return null;
        return candidates.get((int) (Math.random() * candidates.size()));
    }

    public static class TrapResult {
        public final boolean triggered;
        public final WorldProp trap;
        public TrapResult(boolean triggered, WorldProp trap) {
            this.triggered = triggered;
            this.trap = trap;
        }
    }

    public TrapResult checkTrapTrigger(double x, double y, double radius) {
        int cx = (int) Math.floor(x / Config.World.CHUNK_SIZE);
        int cy = (int) Math.floor(y / Config.World.CHUNK_SIZE);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                Chunk chunk = chunks.get(getChunkKey(cx + dx, cy + dy));
                if (chunk == null) continue;

                for (WorldProp prop : chunk.getProps()) {
                    if (prop.getType() == WorldProp.Type.TRAP && prop.isArmed()) {
                        double dist = Math.hypot(x - prop.getX(), y - prop.getY());
                        if (dist < radius + prop.getRadius()) {
                            prop.setArmed(false);
                            prop.setTriggerTimer(8.0); // Bẫy sập và giữ vô hiệu hóa trong 8 giây
                            return new TrapResult(true, prop);
                        }
                    }
                }
            }
        }
        return new TrapResult(false, null);
    }

    public boolean isInsideBush(double x, double y) {
        int cx = (int) Math.floor(x / Config.World.CHUNK_SIZE);
        int cy = (int) Math.floor(y / Config.World.CHUNK_SIZE);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                Chunk chunk = chunks.get(getChunkKey(cx + dx, cy + dy));
                if (chunk == null) continue;

                // 1. Kiểm tra Mảng Cỏ Rộng (Wide Grass Patches - Stage 1)
                for (GrassPatch patch : chunk.getGrassPatches()) {
                    if (patch.isInsideHidingZone(x, y)) {
                        return true;
                    }
                }

                // 2. Kiểm tra Rặng San Hô Rộng (Wide Coral Reefs - Stage 2)
                for (CoralReef reef : chunk.getCoralReefs()) {
                    if (reef.isInsideHidingZone(x, y)) {
                        return true;
                    }
                }

                // 2. Kiểm tra Vật thể Bush (Stage 2 san hô/tảo) hoặc Hang Động (CAVE)
                for (WorldProp prop : chunk.getProps()) {
                    if (prop.getType() == WorldProp.Type.BUSH || prop.getType() == WorldProp.Type.CAVE) {
                        double dist = Math.hypot(x - prop.getX(), y - prop.getY());
                        if (dist < prop.getRadius() + 12.0) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public boolean isInWater(double x, double y) {
        if (stageId == 2) {
            // Đại dương bao la: Nước chiếm ~85-90%, chỉ trừ các đảo san hô
            double nBiome = worldNoise.noise2D(x * 0.0012, y * 0.0012);
            double nDetail = worldNoise.noise2D(x * 0.012, y * 0.012);
            boolean isIsland = (nBiome > 0.65 && nDetail > 0.45);
            return !isIsland;
        }
        double nBiome = worldNoise.noise2D(x * 0.0015, y * 0.0015);
        double nDetail = worldNoise.noise2D(x * 0.015, y * 0.015);
        return nBiome > Config.World.WATER_THRESHOLD && nDetail > 0.40;
    }

    public FastNoise getWorldNoise() {
        return worldNoise;
    }

    public Boss.BiomeType getBiomeType() {
        return Boss.getBiomeForStage(stageId);
    }

    public Boss.Type[] getAllowedBossTypes() {
        return Boss.getBossesForStage(stageId);
    }

    public boolean canSpawnBoss(Boss.Type type) {
        return Boss.isBossAllowedInStage(type, stageId);
    }

    public Boss.Type getBossTypeForLevel(int level) {
        return Boss.getBossForStageLevel(stageId, level);
    }

    /**
     * Tìm tọa độ sinh Boss hợp lệ theo môi trường bản đồ:
     * - Tránh va chạm vật cản cứng / lõi rạn san hô.
     * - Map 1 ưu tiên mặt đất rừng/thảo nguyên; Map 2 ưu tiên vùng nước biển.
     */
    public double[] findValidBossSpawnPoint(Boss.Type type, double anchorX, double anchorY) {
        if (!canSpawnBoss(type)) {
            return null;
        }
        double[] backup = null;
        double baseAngle = Math.random() * Math.PI * 2.0;
        double[] radii = { 680.0, 760.0, 600.0 };

        for (double dist : radii) {
            for (int i = 0; i < 12; i++) {
                double ang = baseAngle + i * (Math.PI * 2.0 / 12.0);
                double candX = anchorX + Math.cos(ang) * dist;
                double candY = anchorY + Math.sin(ang) * dist;

                int chunkX = (int) Math.floor(candX / Config.World.CHUNK_SIZE);
                int chunkY = (int) Math.floor(candY / Config.World.CHUNK_SIZE);
                getOrCreateChunk(chunkX, chunkY);

                if (checkCollision(candX, candY, 36.0).collided) {
                    continue;
                }

                boolean water = isInWater(candX, candY);
                if (stageId == 2) {
                    if (water) {
                        return new double[]{ candX, candY };
                    }
                    if (backup == null) {
                        backup = new double[]{ candX, candY };
                    }
                } else {
                    if (!water) {
                        return new double[]{ candX, candY };
                    }
                    if (backup == null) {
                        backup = new double[]{ candX, candY };
                    }
                }
            }
        }

        if (backup != null) {
            return backup;
        }
        return new double[]{
            anchorX + Math.cos(baseAngle) * 680.0,
            anchorY + Math.sin(baseAngle) * 680.0
        };
    }

    public Boss createBossForStage(Boss.Type type, double anchorX, double anchorY) {
        if (!canSpawnBoss(type)) {
            return null;
        }
        double[] pt = findValidBossSpawnPoint(type, anchorX, anchorY);
        if (pt == null) {
            return null;
        }
        return new Boss(type, pt[0], pt[1]);
    }

    /**
     * Dọn dẹp và giải phóng tài nguyên của toàn bộ chunk đang được quản lý.
     */
    public void clear() {
        for (Chunk chunk : chunks.values()) {
            chunk.dispose();
        }
        chunks.clear();
    }
}
