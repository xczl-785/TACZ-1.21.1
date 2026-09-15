"""Original, deterministic seamless material tiles. No input images or third-party pixels."""
import hashlib
import json
import math
from pathlib import Path
import struct
import zlib

MODULE = Path(__file__).resolve().parents[1]
OUT = MODULE / 'src/main/resources/assets/weapon_assembly_ui/textures/materials'
SIZE = 128


def png(pixels, size=SIZE):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    rows = b''.join(b'\0' + bytes(pixels[y * size * 3:(y + 1) * size * 3]) for y in range(size))
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 2, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(rows, 9)) + chunk(b'IEND', b''))


def noise(x, y, seed):
    value = ((x + seed * 131) * 374761393 + y * 668265263) & 0xffffffff
    value = ((value ^ (value >> 13)) * 1274126177) & 0xffffffff
    return ((value ^ (value >> 16)) & 65535) / 32767.5 - 1


def pixels(kind):
    values = []
    for y in range(SIZE):
        for x in range(SIZE):
            u, v = x / SIZE, y / SIZE
            grain = noise(x, y, 20260913)
            if kind == 'wood':
                wave = 2 * math.pi * (v * 9 + .18 * math.sin(u * math.tau) + .06 * math.sin(u * math.tau * 3))
                value = 219 + 18 * math.sin(wave) + 7 * math.sin(wave * 3) + grain * 3
            elif kind == 'metal':
                value = 240 + 4 * math.sin(v * math.tau * 47) + grain * 5
            elif kind == 'polymer':
                value = 234 + grain * 9
            else:
                value = 224 + grain * 7 + 5 * math.sin(u * math.tau * 32) * math.sin(v * math.tau * 32)
            value = max(0, min(255, round(value)))
            values.extend([value] * 3)
    return values


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    hashes = {}
    for kind in ('wood', 'metal', 'polymer', 'rubber'):
        data = png(pixels(kind))
        (OUT / f'{kind}.png').write_bytes(data)
        hashes[f'{kind}.png'] = hashlib.sha256(data).hexdigest()
    (MODULE / 'tools/material-provenance.json').write_text(json.dumps({
        'origin': 'Original mathematical patterns authored for NewMod. No source images, Tarkov textures, or source UVs consumed.',
        'generator': 'generate_materials.py', 'seed': 20260913, 'size': [SIZE, SIZE], 'sha256': hashes,
    }, ensure_ascii=False, indent=2) + '\n')
    print('Generated four original material tiles')


if __name__ == '__main__':
    main()
