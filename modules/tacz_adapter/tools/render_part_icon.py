"""Bake inventory previews from the same mesh, region bindings and UV textures as the gun.
No external pictures; depth-tested orthographic rendering, transparent background.
"""
import numpy as np
from PIL import Image


def render_part_icon(model, library, bindings, texture_path, size=128):
    triangles = [t for mesh in model['meshes'] for t in mesh['triangles']]
    vertices = np.asarray([t['vertices'] for t in triangles], dtype=float)
    # Mostly side-on, with enough depth to show openings and separate adjacent faces.
    horizontal = np.array([-.22, 0, .9755]); horizontal /= np.linalg.norm(horizontal)
    depth = np.cross(np.array([0., 1., 0.]), horizontal)
    vertical = np.cross(horizontal, depth)
    screen = np.stack((vertices @ horizontal, -(vertices @ vertical)), axis=-1)
    low = screen.min(axis=(0, 1)); high = screen.max(axis=(0, 1))
    resolution = size * 2
    scale = (resolution - 24) / max(float((high - low).max()), 1e-6)
    screen = (screen - (low + high) / 2) * scale + resolution / 2
    z = vertices @ depth
    pixels = np.zeros((resolution, resolution, 4), dtype=np.uint8)
    zbuffer = np.full((resolution, resolution), -np.inf)
    part = bindings['parts'].get(model['definitionId'], {})
    default = part.get('defaultMaterial', bindings['defaultMaterial'])
    textures = {}
    for index, triangle in enumerate(triangles):
        material = library['materials'][part.get('regions', {}).get(triangle['region'], default)]
        color = np.array([int(material['baseColor'][n:n+2], 16) for n in (1, 3, 5)], dtype=float)
        texture_id = material.get('texture', '')
        if texture_id not in textures:
            textures[texture_id] = np.asarray(Image.open(texture_path(texture_id)).convert('RGB'), dtype=float) if texture_id else np.full((1, 1, 3), 255.)
        texture = textures[texture_id]
        a, b, c = screen[index]
        determinant = (b[1]-c[1])*(a[0]-c[0]) + (c[0]-b[0])*(a[1]-c[1])
        if abs(determinant) < 1e-9:
            continue
        x0, y0 = np.maximum(np.floor(screen[index].min(axis=0)).astype(int), 0)
        x1, y1 = np.minimum(np.ceil(screen[index].max(axis=0)).astype(int), resolution-1)
        if x0 > x1 or y0 > y1:
            continue
        yy, xx = np.mgrid[y0:y1+1, x0:x1+1]; xx = xx + .5; yy = yy + .5
        w0 = ((b[1]-c[1])*(xx-c[0]) + (c[0]-b[0])*(yy-c[1])) / determinant
        w1 = ((c[1]-a[1])*(xx-c[0]) + (a[0]-c[0])*(yy-c[1])) / determinant
        w2 = 1-w0-w1
        interpolated_z = w0*z[index, 0] + w1*z[index, 1] + w2*z[index, 2]
        target_z = zbuffer[y0:y1+1, x0:x1+1]
        mask = (w0 >= -1e-7) & (w1 >= -1e-7) & (w2 >= -1e-7) & (interpolated_z > target_z)
        uv = np.asarray(triangle['uv']) * material.get('textureScale', 1)
        u = w0*uv[0,0] + w1*uv[1,0] + w2*uv[2,0]
        v = w0*uv[0,1] + w1*uv[1,1] + w2*uv[2,1]
        texels = texture[(np.floor(v*texture.shape[0]).astype(int) % texture.shape[0]), (np.floor(u*texture.shape[1]).astype(int) % texture.shape[1])]
        normal = np.cross(vertices[index,1]-vertices[index,0], vertices[index,2]-vertices[index,0])
        normal /= max(np.linalg.norm(normal), 1e-9)
        lighting = .65 + .35 * abs(float(normal @ np.array([.8,.5,.33])))
        rgb = np.clip(texels / 255 * color * lighting, 0, 255).astype(np.uint8)
        target = pixels[y0:y1+1, x0:x1+1]
        target[mask, :3] = rgb[mask]; target[mask, 3] = 255
        target_z[mask] = interpolated_z[mask]
    return Image.fromarray(pixels).resize((size, size), Image.Resampling.LANCZOS)
