"""
extract_sprites.py
Script trích xuất 18 sprite thực vật pixel art từ ảnh media_1794230909168.png
và lưu vào thư mục src/assets/vegetation/.
"""

import os
import math
from PIL import Image

SOURCE_IMAGE_PATH = "C:/Users/Gia Huy/.gemini/antigravity/brain/654b6c66-ba54-4cb1-9fa5-1e908db71677/.user_uploaded/media_1794230909168.png"
OUTPUT_DIR = "src/assets/vegetation"

def is_bg(r, g, b):
    dist = math.sqrt((r - 242)**2 + (g - 245)**2 + (b - 250)**2)
    return dist < 22

def extract_and_clean(src, box):
    x0, y0, w, h = box
    crop = src.crop((x0, y0, x0 + w, y0 + h)).convert("RGBA")
    pixels = crop.load()
    cw, ch = crop.size

    for y in range(ch):
        for x in range(cw):
            r, g, b, a = pixels[x, y]
            dist = math.sqrt((r - 242)**2 + (g - 245)**2 + (b - 250)**2)
            if dist < 22:
                pixels[x, y] = (0, 0, 0, 0)
            elif dist < 34:
                alpha = int(((dist - 22) / 12.0) * 255)
                pixels[x, y] = (r, g, b, alpha)
            else:
                pixels[x, y] = (r, g, b, 255)

    # Clean stray connector pixels at bottom
    for x in range(cw):
        for y in range(max(0, ch - 3), ch):
            if pixels[x, y][3] > 0:
                neighbors = 0
                for dy in (-1, 0, 1):
                    for dx in (-1, 0, 1):
                        nx, ny = x + dx, y + dy
                        if 0 <= nx < cw and 0 <= ny < ch and not (dx == 0 and dy == 0):
                            if pixels[nx, ny][3] > 30:
                                neighbors += 1
                if neighbors < 2:
                    pixels[x, y] = (0, 0, 0, 0)

    # Trim to bbox
    bbox = crop.getbbox()
    if bbox:
        return crop.crop(bbox)
    return crop

def main():
    if not os.path.exists(SOURCE_IMAGE_PATH):
        print(f"Error: source image not found at {SOURCE_IMAGE_PATH}")
        return

    os.makedirs(OUTPUT_DIR, exist_ok=True)
    img = Image.open(SOURCE_IMAGE_PATH)

    # 1. Grass 1..7
    grass_boxes = [
        (30, 61, 26, 21),
        (72, 57, 30, 25),
        (118, 49, 37, 33),
        (170, 40, 48, 46),
        (232, 35, 58, 51),
        (303, 37, 49, 49),
        (365, 36, 54, 50)
    ]
    for i, b in enumerate(grass_boxes, 1):
        sprite = extract_and_clean(img, b)
        sprite.save(os.path.join(OUTPUT_DIR, f"grass_{i}.png"))
        print(f"Saved grass_{i}.png {sprite.size}")

    # 2. Flowers 1..8
    flower_boxes = [
        (28, 106, 28, 35),
        (73, 106, 31, 35),
        (124, 108, 24, 33),
        (167, 110, 31, 31),
        (215, 109, 33, 32),
        (266, 104, 31, 37),
        (315, 99, 39, 42),
        (372, 98, 40, 43)
    ]
    for i, b in enumerate(flower_boxes, 1):
        sprite = extract_and_clean(img, b)
        sprite.save(os.path.join(OUTPUT_DIR, f"flower_{i}.png"))
        print(f"Saved flower_{i}.png {sprite.size}")

    # 3. Grass clump
    clump = extract_and_clean(img, (200, 174, 56, 60))
    clump.save(os.path.join(OUTPUT_DIR, "grass_clump.png"))
    print(f"Saved grass_clump.png {clump.size}")

    # 4. Yellow bush
    yellow = extract_and_clean(img, (196, 280, 60, 48))
    yellow.save(os.path.join(OUTPUT_DIR, "bush_yellow.png"))
    print(f"Saved bush_yellow.png {yellow.size}")

    # 5. Bush dome
    x0, y0, dw, dh = 46, 175, 113, 95
    crop = img.crop((x0, y0, x0 + dw, y0 + dh)).convert("RGBA")
    pix = crop.load()
    dome_cx, dome_cy = (104.0 - x0), (221.0 - y0)
    dome_rx, dome_ry = 47.0, 45.0

    for y in range(dh):
        for x in range(dw):
            r, g, b, a = pix[x, y]
            dist_bg = math.sqrt((r - 242)**2 + (g - 245)**2 + (b - 250)**2)
            nx = (x - dome_cx) / dome_rx
            ny = (y - dome_cy) / dome_ry
            dist_oval = nx * nx + ny * ny
            is_trunk = (y >= dome_cy + 25 and y <= dh - 1 and abs(x - dome_cx) <= 22 and r > 40 and g < 110)

            if dist_bg < 20:
                pix[x, y] = (0, 0, 0, 0)
            elif dist_oval <= 1.05 or is_trunk:
                if dist_oval > 0.88 and r > 165 and b > 120 and g < 150:
                    pix[x, y] = (0, 0, 0, 0)
                elif dist_bg < 32:
                    alpha = int(((dist_bg - 20) / 12.0) * 255)
                    pix[x, y] = (r, g, b, alpha)
                else:
                    pix[x, y] = (r, g, b, 255)
            else:
                pix[x, y] = (0, 0, 0, 0)

    bbox = crop.getbbox()
    if bbox:
        crop = crop.crop(bbox)
    crop.save(os.path.join(OUTPUT_DIR, "bush_dome.png"))
    print(f"Saved bush_dome.png {crop.size}")

if __name__ == "__main__":
    main()
