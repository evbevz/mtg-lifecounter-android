#!/usr/bin/env python3
"""Converts the iOS dice mesh (CUBE_OBJ.h) into the binary asset used by the Android port.

Output layout: little-endian int32 vertex count, then per vertex
interleaved float32 position (x, y, z) and normal (nx, ny, nz).

Usage: convert_dice_mesh.py [CUBE_OBJ.h] [dice_mesh.bin]
"""
import re
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_SRC = ROOT / "MTG LifeCounter" / "CUBE_OBJ.h"
DEFAULT_DST = ROOT / "AndroidPort" / "app" / "src" / "main" / "assets" / "dice_mesh.bin"


def read_array(text, name):
    match = re.search(r"float\s+" + name + r"\s*\[\]\s*=\s*\{(.*?)\};", text, re.S)
    if not match:
        sys.exit(f"{name} not found")
    return [float(v) for v in match.group(1).replace("\n", "").split(",") if v.strip()]


def main():
    src = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_SRC
    dst = Path(sys.argv[2]) if len(sys.argv) > 2 else DEFAULT_DST
    text = src.read_text()

    count = int(re.search(r"CUBE_NumVerts\s*=\s*(\d+)", text).group(1))
    verts = read_array(text, "CUBE_Verts")
    normals = read_array(text, "CUBE_Normals")
    if len(verts) != count * 3 or len(normals) != count * 3:
        sys.exit(f"unexpected sizes: {len(verts)} verts, {len(normals)} normals, {count} expected")

    data = bytearray(struct.pack("<i", count))
    for i in range(count):
        data += struct.pack("<6f", *verts[i * 3:i * 3 + 3], *normals[i * 3:i * 3 + 3])

    dst.parent.mkdir(parents=True, exist_ok=True)
    dst.write_bytes(data)
    print(f"{count} vertices -> {dst} ({len(data)} bytes)")


if __name__ == "__main__":
    main()
