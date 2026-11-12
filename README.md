# 🐍 Snake Arena: Hệ Sinh Thái Săn Mồi (Java Edition)

Phiên bản **Java thuần (Pure Java SE 21 - Swing / Graphics2D)** của game **Snake Arena: Hệ Sinh Thái Săn Mồi**.
Kế thừa trọn vẹn toàn bộ đồ họa Pixel Art, cơ chế thế giới vô hạn, chuyển động vật lý dây mềm (Rope Physics), hệ thống thể lực, cạm bẫy thú, rắn sát thủ, bụi rậm ngụy trang và hồ nước sinh thái.

---

## 🚀 Cách Mở & Chạy Trên IntelliJ IDEA

### Bước 1: Mở Project trong IntelliJ IDEA
1. Mở phần mềm **IntelliJ IDEA**.
2. Chọn **Open** (hoặc **File** $\rightarrow$ **Open...**).
3. Tìm và chọn thư mục dự án:
   ```text
   C:\Users\Gia Huy\.gemini\antigravity\scratch\snake_arena_ecosystem
   ```
4. Nhấn **OK** $\rightarrow$ Chọn **Trust Project** nếu được hỏi.

### Bước 2: Chạy Game
- Dự án đã tích hợp sẵn cấu hình chạy **`Run Snake Arena`** trong IntelliJ!
- Bạn chỉ cần nhìn lên góc trên bên phải của IntelliJ, chọn cấu hình **`Run Snake Arena`** và bấm nút **Play (Tam giác xanh ▶)** hoặc nhấn tổ hợp phím **`Shift + F10`**.
- *Hoặc*: Mở file `src/com/snakearena/Main.java`, bấm vào biểu tượng tam giác xanh ▶ cạnh hàm `public static void main` $\rightarrow$ **Run 'Main.main()'**.

> 💡 **Cách chạy nhanh ngoài IntelliJ**:
> Nhấp đúp chuột vào file **`run.bat`** trong thư mục dự án để chạy ngay lập tức mà không cần mở phần mềm nào!

---

## 🎮 Hướng Dẫn Điều Khiển (Controls)

| Thao Tác | Hành Động |
|---|---|
| **Di Chuột** | Rắn tự động trườn bám theo con trỏ chuột mượt mà (360 độ) |
| **Giữ Chuột Trái** hoặc **Giữ `Shift`** | **Tăng tốc (Sprint / Stamina Boost)**: Phóng nhanh lên 225 px/s (tiêu hao thanh thể lực `STAMINA`) |
| `W`, `A`, `S`, `D` hoặc `Phím Mũi Tên` | Điều khiển rắn bằng bàn phím (nếu không dùng chuột) |
| `SPACE` (Phím Cách) | Kích hoạt kỹ năng **Lướt nhanh (Dash)** *(Mở khóa khi đạt Cấp 2)* |
| `P` | Tạm dừng trò chơi (Pause / Resume) |
| `R` hoặc `SPACE` | Chơi lại khi Thua cuộc (Game Over) |

---

## 🌟 Các Tính Năng & Cơ Chế Gameplay Nổi Bật

### 1. Mở Rộng Không Gian – Thế Giới Vô Hạn (Infinite Procedural World)
- **Không có 4 vách ngăn**: Thế giới mở rộng vô tận theo mọi hướng.
- **Hệ thống Chunk (512x512 px)**: Sinh địa hình theo seed cố định (`FastNoise` 2D Perlin Noise) và tự động dọn dẹp các chunk ngoài tầm nhìn để đảm bảo tốc độ ổn định 60 FPS.
- **Camera Lerp**: Camera bám theo đầu rắn với hệ số mượt mà `0.10`, đón đầu hướng trườn (`lead distance 40px`) và rung lắc chấn động khi va chạm.

### 2. Tách Lớp Môi Trường (Layered Rendering 7 Lớp)
1. **Lớp 1 (Ground)**: Thảo nguyên, đất khô, rừng rậm và hồ nước sinh thái.
2. **Lớp 2 (Decals)**: Hoa dại, cỏ non, sỏi đá nhỏ.
3. **Lớp 3 (Shadows)**: Bóng đổ elip mờ dưới cây, đá, con mồi, rắn và bóng đại bàng trôi trên mặt đất.
4. & 5. **Lớp 4 & 5 (Entities & Props - Y-Sort)**: Cây cối, đá tảng, bụi rậm, bẫy kẹp, rắn và con mồi được sắp xếp theo trục Y. Rắn trườn sau cổ thụ sẽ làm cây trong suốt nhẹ (`alpha = 0.55`).
6. **Lớp 6 (Canopy / Air)**: Mây trôi Parallax (chậm hơn 15%), bướm bay dập dờn, đại bàng săn mồi trên không.
7. **Lớp 7 (Post-FX & HUD)**: Vignette viền màn hình, chữ số sát thương bay lên, radar Minimap và thanh HUD cố định.

### 3. Hệ Thống Thể Lực & Tăng Tốc (Sprint & Stamina)
- Thanh `STAMINA` hiển thị trực tiếp trên thanh HUD.
- Giữ chuột trái hoặc phím `Shift` để tăng tốc từ 135 lên 225 px/s, để lại vệt bụi hoặc bọt nước sau đuôi.
- Thể lực tự động hồi phục khi thả phím.

### 4. Cạm Bẫy Thú Nguy Hiểm (Bear Traps)
- Rải rác khắp bản đồ; khi người chơi hoặc rắn AI giẫm vào sẽ sập bẫy, mất 1 HP, rung màn hình cực mạnh và văng tia lửa.
- Bẫy sau khi sập có thời gian hồi phục 8 giây trước khi tự gài lại.

### 5. Rắn AI Sát Thủ Săn Người Chơi (Hunter Snake)
- Rắn AI có vai trò Sát thủ: Khi người chơi vào bán kính 460px, rắn AI sẽ rực sáng đỏ, hiện cảnh báo `⚠ SĂN MỒI!` và lao vào cắn đầu người chơi.
- Bẫy rắn AI đâm đầu vào thân mình để hạ gục chúng và nhận thưởng lớn (+50 Điểm)!

### 6. Kỹ Năng Ẩn Nấp Trong Bụi Rậm (Bush Camouflage)
- Chui vào các bụi cây (`bush.png`) giúp rắn trở nên mờ ảo (45% opacity) và kích hoạt trạng thái `🛡 ẨN NẤP`.
- Rắn sát thủ và đại bàng săn mồi trên trời sẽ lập tức mất dấu vết mục tiêu!

### 7. Hồ Nước Sinh Thái (Ponds & Swimming Physics)
- Các hồ nước sâu màu ngọc bích có hiệu ứng gợn sóng lăn tăn.
- Khi bơi trong hồ, rắn tạo vệt sóng rẽ nước `Water Wake Ripple` và bọt nước; loài ếch khi xuống hồ được tăng 50% tốc độ bơi.

### 8. Cơ Chế Bất Tử Tạm Thời (I-Frames 1.2s)
- Khi bị dính bẫy hoặc bị cắn, rắn người chơi sẽ nhấp nháy trong 1.2 giây để tránh bị dồn sát thương liên tục.

---

## 📁 Cấu Trúc Mã Nguồn (Clean OOP)

```text
src/
├── assets/                     # Toàn bộ pixel art spritesheet
└── com/snakearena/
    ├── Main.java               # Điểm khởi chạy JFrame Swing
    ├── core/
    │   ├── Config.java         # Hằng số toàn cục (World, Snake, AI, Cam)
    │   ├── FastNoise.java      # Bộ sinh nhiễu 2D Perlin & Mulberry32 PRNG
    │   ├── Camera.java         # Camera Lerp, Lead & Screen Shake
    │   ├── SoundManager.java   # Âm thanh 8-bit procedural không cần file ngoài
    │   └── GamePanel.java      # Vòng lặp 60 FPS, input chuột/phím, game logic
    ├── world/
    │   ├── WorldProp.java      # Vật thể cây, đá, bụi ngụy trang, bẫy thú
    │   ├── Chunk.java          # Chunk 512x512 cache BufferedImage địa hình
    │   └── ChunkManager.java   # Quản lý thế giới vô hạn, va chạm, bẫy, hồ nước
    ├── entity/
    │   ├── SnakeSegment.java   # Đốt thân rắn (Rope physics)
    │   ├── Snake.java          # Lớp cơ sở chuyển động uốn lượn, máu, thể lực
    │   ├── PlayerSnake.java    # Rắn người chơi điều khiển
    │   ├── AISnake.java        # Rắn AI săn mồi & truy sát người chơi
    │   ├── Prey.java           # Con mồi (Thỏ, Ếch, Chuột, Bướm)
    │   └── Eagle.java          # Đại bàng săn mồi trên không
    └── render/
        ├── AssetManager.java   # Nạp và vẽ sprite xoay góc 360 độ mượt mà
        ├── ParticleSystem.java # Hiệu ứng hạt bụi, bọt nước, tia lửa, hào quang
        ├── FloatingTextManager.java # Chữ số bay điểm số & cảnh báo
        └── LayeredRenderer.java# Quy trình render 7 lớp môi trường
```
