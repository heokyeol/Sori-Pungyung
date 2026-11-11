"""Export app assets for the web build: mp3 loops, one mp3 per bird call, places.json, credits.
Usage: python make_web_assets.py <assets dir> <web dir>"""
import json, os, sys
import numpy as np
import soundfile as sf

A, W = sys.argv[1], sys.argv[2]
os.makedirs(f"{W}/audio/birds", exist_ok=True)

def mp3(path, data, sr):
    data = np.clip(data, -1, 1).astype("float32")
    ch = 1 if data.ndim == 1 else data.shape[1]
    with sf.SoundFile(path, "w", sr, ch, format="MP3", subtype="MPEG_LAYER_III", compression_level=0.35) as f:
        for i in range(0, len(data), sr): f.write(data[i:i + sr])

loops = []
for name in sorted(os.listdir(A)):
    if name.endswith(".ogg") and not name.startswith("birds_"):
        x, sr = sf.read(f"{A}/{name}", dtype="float32")
        mp3(f"{W}/audio/{name[:-4]}.mp3", x, sr)
        loops.append(name[:-4])
birds = {}
for line in open(f"{A}/birds_index.txt"):
    p = line.split()
    if len(p) < 3: continue
    sid, idx = p[0], list(map(int, p[1:]))
    x, sr = sf.read(f"{A}/birds_{sid}.ogg", dtype="float32")
    files = []
    for n in range(0, len(idx), 2):
        fn = f"{sid}_{n // 2}.mp3"
        mp3(f"{W}/audio/birds/{fn}", x[idx[n]:idx[n + 1]], sr)
        files.append(fn)
    birds[sid] = files
places = []
for line in open(f"{A}/places.tsv", encoding="utf-8"):
    if line.startswith("#") or not line.strip(): continue
    c, la, lo, *rest = line.rstrip("\n").split("\t")
    places.append([c, int(la), int(lo), rest[0] if rest else "", int(float(rest[1])) if len(rest) > 1 else 0])
json.dump({"loops": loops, "birds": birds}, open(f"{W}/audio/index.json", "w"), ensure_ascii=False)
json.dump(places, open(f"{W}/places.json", "w"), ensure_ascii=False, separators=(",", ":"))
open(f"{W}/CREDITS.txt", "w").write(open(f"{A}/CREDITS.txt").read())
total = sum(os.path.getsize(os.path.join(d, f)) for d, _, fs in os.walk(f"{W}/audio") for f in fs)
print(len(loops), "loops,", sum(len(v) for v in birds.values()), "bird clips,", len(places), "places, audio", total // 1024, "KB")
