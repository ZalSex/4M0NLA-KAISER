#!/usr/bin/env python3

import sys
import os
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    print("Pillow not installed. Run: pip install Pillow")
    sys.exit(1)

MIPMAP_SIZES = [
    ("mdpi", 48),
    ("hdpi", 72),
    ("xhdpi", 96),
    ("xxhdpi", 144),
    ("xxxhdpi", 192),
]

def generate_icons(source_path: str, output_base: str = "app/src/main/res"):
    src = Path(source_path)
    if not src.exists():
        print(f"Error: Source File '{source_path}' Not Found.")
        sys.exit(1)

    img = Image.open(src).convert("RGBA")

    for suffix, size in MIPMAP_SIZES:
        folder = Path(output_base) / f"mipmap-{suffix}"
        folder.mkdir(parents=True, exist_ok=True)

        resized = img.resize((size, size), Image.LANCZOS)
        out_path = folder / "ic_launcher.png"
        resized.save(out_path, "PNG")
        print(f"  Created: {out_path}  ({size}x{size})")

        round_img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        mask = Image.new("L", (size, size), 0)
        from PIL import ImageDraw
        draw = ImageDraw.Draw(mask)
        draw.ellipse((0, 0, size, size), fill=255)
        round_img.paste(resized, (0, 0), mask)
        round_path = folder / "ic_launcher_round.png"
        round_img.save(round_path, "PNG")
        print(f"  Created: {round_path}  ({size}x{size} round)")

    print("\nAll Mipmap Icons Generated Successfully.")

if __name__ == "__main__":
    source = sys.argv[1] if len(sys.argv) > 1 else "icon.jpg"
    generate_icons(source)