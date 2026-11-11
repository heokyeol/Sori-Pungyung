"""Chosen recordings for SoundDrive. Builds build/dl/manifest.json from the search results."""
import json, sys
dl = sys.argv[1]
fs = {}
for f in ("fs-ambience.json", "fs-birds.json", "fs-birds2.json", "fs-unlock.json", "fs-season.json", "fs-season2.json"):
    for rows in json.load(open(f"{dl}/{f}")).values():
        for r in rows: fs[r["id"]] = r
cm = {}
for f in ("search-birds-commons.json", "search-space.json"):
    for rows in json.load(open(f"{dl}/{f}")).values():
        for r in rows: cm[r["title"]] = r

# (target, kind, source-key)  kind: loop = ambience loop, calls = bird call clips
PICKS = [
    ("rain", "loop", "fs:466241"), ("wind", "loop", "fs:181252"), ("fire", "loop", "fs:40699"),
    ("pad", "loop", "fs:242773"),
    ("dove", "calls", "cm:File:Streptopelia orientalis.ogg"),
    ("sparrow", "calls", "fs:383160"), ("magpie", "calls", "fs:418110"), ("tit", "calls", "fs:418102"),
    ("bulbul", "calls", "cm:File:Hypsipetes-amaurotis-2025-05-20-282646657.ogg"),
    ("bulbul", "calls", "cm:File:Hypsipetes-amaurotis-2025-05-20-282619241.ogg"),
    ("bulbul", "calls", "cm:File:Hypsipetes-amaurotis-2025-05-20-282617142.ogg"),
    ("oriole", "calls", "cm:File:Blacknapedoriole.ogg"),
    ("warbler", "calls", "fs:32295"), ("woodpecker", "calls", "fs:428146"),
    ("cuckoo", "calls", "fs:517779"), ("cuckoo", "calls", "fs:517780"), ("cuckoo", "calls", "fs:517781"),
    ("owl", "calls", "fs:400147"),
    ("scops", "calls", "cm:File:Otus sunia.ogg"),
    ("nightjar", "calls", "cm:File:Jungle nightjar Mt Abu.ogg"),
    # Location-unlocked sounds
    ("u_airport", "loop", "fs:341591"), ("u_library", "loop", "fs:173344"), ("u_temple", "loop", "fs:437337"),
    ("u_station", "loop", "fs:347425"), ("u_valley", "loop", "fs:469009"), ("u_harbor", "loop", "fs:208074"),
    ("u_cafe", "loop", "fs:482990"), ("u_baseball", "loop", "fs:239936"),
    # Weather and space
    ("thunder", "loop", "cm:File:Thunderstorm after hot summer day 17 minutes 01 of 04.ogg"),
    ("cmb", "loop", "cm:File:Sounds of the Ancient Universe (PIA16881).wav"),
    ("saturn", "loop", "cm:File:Eerie Sounds of Saturn’s Radio Emissions (PIA07966).wav"),
    ("jupiter", "loop", "cm:File:Cassini RPWS Jupiter 010104.ogg"),
    # Seasonal landmarks
    ("s_jinhae", "loop", "fs:731819"), ("s_boseong", "loop", "fs:215414"), ("s_gasiri", "loop", "fs:387426"),
    ("s_yangyang", "loop", "fs:191444"), ("s_haeundae", "loop", "fs:580707"), ("s_damyang", "loop", "fs:404688"),
    ("s_maisan", "loop", "fs:218305"), ("s_naejangsan", "loop", "fs:403764"), ("s_mindungsan", "loop", "fs:666180"),
    ("s_daegwallyeong", "loop", "fs:505999"), ("s_pyeongchang", "loop", "fs:558792"), ("s_hwacheon", "loop", "fs:737896"),
]
out = []
for target, kind, key in PICKS:
    src, k = key.split(":", 1)
    if src == "fs":
        r = fs[k]
        out.append({"target": target, "kind": kind, "url": r["ogg"], "page": r["page"], "title": r["title"],
                    "author": r["user"], "license": "CC0 1.0" if r["license"] == "cc0" else "CC BY (see page)",
                    "file": f"fs{k}.ogg"})
    else:
        r = cm[k]
        out.append({"target": target, "kind": kind, "url": r["url"], "page": r["page"], "title": r["title"][5:],
                    "author": r["artist"], "license": r["license"], "file": "cm_" + r["title"][5:].replace(" ", "_")})
# The Commons thunderstorm .ogg uses an Ogg Skeleton stream libsndfile can't read; use Commons' mp3 transcode.
for o in out:
    if o["target"] == "thunder": o["file"] = "thunder.mp3"
json.dump(out, open(f"{dl}/manifest.json", "w"), ensure_ascii=False, indent=1)
for o in out: print(f"{o['target']:<10} {o['license']:<14} {o['author'][:22]:<22} {o['title'][:45]}")
