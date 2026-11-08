#!/usr/bin/env python3
"""Export upstream character geometry and HEIF clips without promotional watch frames.

Requires Pillow and heif-convert (libheif-examples on Ubuntu, libheif on macOS).
Outputs are generated Android assets, not replacement illustrations.
"""
from pathlib import Path
import argparse, base64, concurrent.futures, hashlib, json, re, shutil, struct, subprocess, tempfile
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('output', type=Path)
args = parser.parse_args()
out = args.output
out.mkdir(parents=True, exist_ok=True)
source = (ROOT / 'app/ChumsWatch/Rig/RayStage.swift').read_text()
sol = source[source.index('enum SolArt'):]
def array(name):
    match = re.search(r'static let ' + name + r'[^=]*=\s*\[([^\]]+)\]', sol)
    if not match: raise ValueError('Missing original Ray geometry: ' + name)
    return [float(x) for x in re.findall(r'-?\d+(?:\.\d+)?', match[1])]
ray = {name: array(name) for name in ['base', 'angry', 'melt', 'dizzy', 'armAngle']}
raw = sol[sol.index('private static let raw:'):]
ray['glyphs'] = {name: json.loads('[' + values + ']') for name, values in re.findall(r'"([^"]+)":\s*\[([^\]]+)\]', raw)}
ray['pupils'] = {name: json.loads('[' + values + ']') for name, values in re.findall(r'"([^"]+)": SIMD4\(([^)]+)\)', sol)}
(out / 'ray.json').write_text(json.dumps(ray, separators=(',', ':')))
scribble = (ROOT / 'app/ChumsWatch/Rig/TangleStage.swift').read_text().split('private static let faceRaw:')[1]
marks = {}
for name, encoded in re.findall(r'"([^"]+)":\s*"([A-Za-z0-9+/=]+)"', scribble):
    data = base64.b64decode(encoded)
    marks[name] = [v / 8 for v in struct.unpack('<' + 'h' * (len(data) // 2), data)]
(out / 'tangle.json').write_text(json.dumps(marks, separators=(',', ':')))
noodle = (ROOT / 'app/ChumsWatch/noodle-shapes.bin').read_bytes()
ns, n, ne = struct.unpack_from('<iii', noodle, 4)
assert ns == 9 and n == 64 and ne > 0
offset = 16
shapes = []
for i in range(ns):
    values = struct.unpack_from('<' + 'f' * n * 3, noodle, offset); offset += n * 12
    shapes.append([list(values[j:j+3]) for j in range(0, len(values), 3)])
edges = []
for i in range(ne):
    a, b, k, seconds = struct.unpack_from('<iiif', noodle, offset); offset += 16
    frames = []
    for _ in range(k):
        values = struct.unpack_from('<' + 'f' * n * 3, noodle, offset); offset += n * 12
        frames.append([list(values[j:j+3]) for j in range(0, len(values), 3)])
    edges.append(dict(a=a, b=b, frames=frames, seconds=seconds))
rest = (ROOT / 'app/ChumsWatch/Rig/NoodleRest.swift').read_text()
albedo = [list(map(float, x.split(','))) for x in re.findall(r'SIMD3\(([^)]+)\)', rest)]
(out / 'noodle.json').write_text(json.dumps(dict(shapes=shapes, edges=edges, albedo=albedo), separators=(',', ':')))
meta = json.loads((ROOT / 'app/ChumsWatch/Noodle/noodle.json').read_text())
stretch = out / 'stretch'; stretch.mkdir(exist_ok=True)
(stretch / 'clips.json').write_text(json.dumps(meta, separators=(',', ':')))
converter = shutil.which('heif-convert')
if not converter: raise SystemExit('Install heif-convert: apt install libheif-examples, or brew install libheif')
def convert(item):
    name, info = item
    src = ROOT / 'app/ChumsWatch/Noodle' / (name + '.heics')
    dest = stretch / (name + '.webp')
    stamp = dest.with_suffix('.sha256')
    signature = hashlib.sha256(src.read_bytes() + b'android-webp-v1').hexdigest()
    if dest.exists() and stamp.exists() and stamp.read_text() == signature: return name
    with tempfile.TemporaryDirectory(prefix='chums-clip-') as temp:
        subprocess.run([converter, '--quiet', '-S', str(src), temp + '/frame.png'], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        paths = sorted(Path(temp).glob('frame*.png'), key=lambda p: int(re.search(r'(\d+)\.png$', p.name)[1]) if re.search(r'(\d+)\.png$', p.name) else 0)
        assert len(paths) == info['n'], (name, len(paths), info['n'])
        frames = [Image.open(p).convert('RGB') for p in paths]
        frames[0].save(dest, save_all=True, append_images=frames[1:], format='WEBP', quality=90, method=4,
                       duration=round(1000 / meta['fps']), loop=0)
        for frame in frames: frame.close()
    stamp.write_text(signature)
    return name
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
    for i, name in enumerate(pool.map(convert, meta['clips'].items()), 1):
        if i % 10 == 0: print('Converted original clips:', i, '/', len(meta['clips']), flush=True)
print('Exported original Ray geometry, Noodle shape journeys and', len(meta['clips']), 'Stretch clips.', flush=True)
