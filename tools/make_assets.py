"""Turn downloaded recordings into app assets.
  loops → app/src/main/assets/<id>.ogg   (90 s seamless stereo loop, 48 kHz)
  calls → app/src/main/assets/birds_<id>.ogg + birds_index.txt (cut bird calls, mono)
  previews → build/previews/*.wav
Usage: python make_assets.py <build dir> <assets dir> [--force]

Existing outputs are kept as they are (re-encoding changes the bytes and bloats git);
only missing sounds are made. Pass --force to rebuild everything."""
import json, os, sys
import numpy as np
import soundfile as sf

BUILD, ASSETS = sys.argv[1], sys.argv[2]
FORCE = "--force" in sys.argv[3:]
FORCE_CALLS = FORCE or "--force=calls" in sys.argv[3:]  # rebuild only the bird call clips

# Keep the "Edited:" note of sounds we don't rebuild, read from the existing credits file.
OLD_EDIT = {}
if os.path.exists(f"{ASSETS}/CREDITS.txt"):
    cur = None
    for line in open(f"{ASSETS}/CREDITS.txt"):
        if line.startswith("["): cur = line[1:line.index("]")]
        elif line.strip().startswith("Edited:") and cur: OLD_EDIT.setdefault(cur, line.strip()[len("Edited: "):])
SR = 48000
os.makedirs(ASSETS, exist_ok=True)
os.makedirs(f"{BUILD}/previews", exist_ok=True)
man = json.load(open(f"{BUILD}/dl/manifest.json"))

LOOP_RMS = {"rain": 0.16, "wind": 0.14, "fire": 0.13, "pad": 0.12, "thunder": 0.15, "cmb": 0.11, "saturn": 0.10, "jupiter": 0.10}
UNLOCK_RMS = 0.13
# high-pass cutoff (Hz) and max gap merged inside one call (s)
# (high-pass Hz, merge gap s, threshold × noise floor, low-pass Hz or None)
# Gaps are long enough to keep a whole phrase together (소쩍-소쩍, 뻐-꾹, 구구-구구).
CALL_CFG = {"dove": (150, 1.0, 4, None), "owl": (120, 1.5, 4, None), "scops": (400, 2.5, 4, None),
            "cuckoo": (250, 1.2, 4, None), "woodpecker": (300, 0.4, 4, None), "warbler": (800, 0.7, 4, None),
            "bulbul": (800, 0.6, 4, None), "oriole": (800, 0.6, 4, None),
            "nightjar": (700, 0.7, 3, 2500)}  # quiet recording: detect in its call band
DEFAULT_CALL = (1000, 0.4, 4, None)


def load(path):
    x, sr = sf.read(path, dtype="float64", always_2d=True)
    if sr != SR:  # linear resample (fine for these broadband/noisy sources)
        n = int(len(x) * SR / sr)
        src = np.arange(len(x)); dst = np.linspace(0, len(x) - 1, n)
        x = np.stack([np.interp(dst, src, x[:, c]) for c in range(x.shape[1])], axis=1)
    return x


def write_ogg(path, data):
    data = np.clip(data, -1, 1).astype("float32")
    ch = 1 if data.ndim == 1 else data.shape[1]
    with sf.SoundFile(path, "w", SR, ch, format="OGG", subtype="VORBIS") as f:
        for i in range(0, len(data), SR):
            f.write(data[i:i + SR])


def highpass(x, fc, lp=None):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    mask = 1 / (1 + (fc / np.maximum(f, 1)) ** 4)  # ~24 dB/oct
    if lp: mask = mask / (1 + (f / lp) ** 4)
    return np.fft.irfft(spec * mask, len(x))


def stretch_texture(x, seconds, grain=4.0, fade=1.5, seed=1):
    """Overlap-add random pieces of a short recording into a longer one (no repeating
    pattern every few seconds). Only for noise-like textures such as the CMB sonification."""
    rng = np.random.default_rng(seed)
    g, f = int(grain * SR), int(fade * SR)
    hop = g - f
    n = int(seconds * SR) + g
    out = np.zeros((n, x.shape[1]))
    win = np.ones(g)
    win[:f] = np.sqrt(np.linspace(0, 1, f)); win[-f:] = np.sqrt(np.linspace(1, 0, f))
    for start in range(0, n - g, hop):
        a = rng.integers(0, max(1, len(x) - g))
        out[start:start + g] += x[a:a + g] * win[:, None]
    return out[: int(seconds * SR) + 4 * SR]


def make_loop(o):
    if not FORCE and os.path.exists(f"{ASSETS}/{o['target']}.ogg"):
        o["excerpt"] = OLD_EDIT.get(o["target"], "seamless loop, 48 kHz, level normalized")
        return
    x = load(f"{BUILD}/dl/src/{o['file']}")
    if x.shape[1] == 1: x = np.repeat(x, 2, axis=1)
    x = x[:, :2]
    if len(x) / SR < 30:
        x = stretch_texture(x, 60)  # very short recording: rebuild a longer, varied bed from its own pieces
    X = 3
    L = int(min(90, len(x) / SR - X - 1))  # short recordings make shorter loops
    m = x.mean(axis=1)
    w = SR * 5
    db = np.array([20 * np.log10(np.sqrt(np.mean(m[i:i + w] ** 2)) + 1e-9) for i in range(0, len(m) - w, w)])
    need = (L + X) // 5 + 1
    if len(db) <= need:
        start = 0
    else:
        med = np.median(db)
        best = min(range(len(db) - need), key=lambda s: np.std(db[s:s + need]) + 0.5 * abs(np.median(db[s:s + need]) - med))
        start = best * w
    seg = x[start:start + (L + X) * SR]
    n = X * SR
    t = np.linspace(0, 1, n)[:, None]
    head = seg[:n] * np.sqrt(t) + seg[L * SR:L * SR + n] * np.sqrt(1 - t)
    loop = np.concatenate([head, seg[n:L * SR]])
    # Match loudness without hard clipping: soft-limit the rare loud peaks (rain drops,
    # fire pops) with tanh, then search the gain that reaches the target level.
    target = LOOP_RMS.get(o["target"], UNLOCK_RMS)
    soft = lambda v: np.tanh(v / 0.8) * 0.8
    lo_g, hi_g = 0.0, 50.0
    for _ in range(40):
        g = (lo_g + hi_g) / 2
        if np.sqrt(np.mean(soft(loop * g) ** 2)) < target: lo_g = g
        else: hi_g = g
    g = min(g, 80.0 if o["target"] == "r_chuncheon" else 30.0 if o["target"] in ("rain", "u_valley", "u_cafe", "u_library", "u_temple") or o["target"].startswith(("s_", "r_")) else 8.0)  # rain is noise-like, others would turn to hiss
    loop = soft(loop * g)
    write_ogg(f"{ASSETS}/{o['target']}.ogg", loop)
    sf.write(f"{BUILD}/previews/loop-{o['target']}.wav", np.clip(loop[:20 * SR], -1, 1), SR, subtype="PCM_16")
    o["excerpt"] = f"{L} s from {start / SR:.0f} s, seamless loop, 48 kHz, level normalized"
    print(f"loop {o['target']:<6} from {start / SR:5.0f}s peak {np.abs(loop).max():.2f} rms {np.sqrt(np.mean(loop ** 2)):.3f}")


def find_calls(x, fc, gap, k=4, lp=None):
    y = highpass(x, fc, lp)
    win = int(0.02 * SR)
    env = np.sqrt(np.convolve(y ** 2, np.ones(win) / win, mode="same"))
    floor = np.percentile(env, 20)
    thr = max(floor * k, env.max() * 0.06)
    act = env > thr
    segs, i, n = [], 0, len(act)
    while i < n:
        if not act[i]: i += 1; continue
        j = i
        while j < n:
            k = j
            while k < n and act[k]: k += 1
            nxt = k
            while nxt < n and not act[nxt]: nxt += 1
            if nxt < n and (nxt - k) < gap * SR: j = nxt; continue
            j = k; break
        segs.append((i, j)); i = j + 1
    out = []
    release = floor * 1.6  # end a call only once it has really died away (no clipped tails)
    for a, b in segs:
        while b < n - 1 and env[b] > release: b += 1
        while a > 0 and env[a] > release: a -= 1
        d = (b - a) / SR
        if d < 0.15: continue
        if d > 10: b = a + 10 * SR
        snr = env[a:b].max() / (floor + 1e-12)
        out.append((snr, a, b))
    return y, sorted(out, reverse=True)


def make_calls(target, items):
    if not FORCE_CALLS and os.path.exists(f"{ASSETS}/birds_{target}.ogg"):
        old = [l for l in open(f"{ASSETS}/birds_index.txt") if l.split()[:1] == [target]] if os.path.exists(f"{ASSETS}/birds_index.txt") else []
        if old:
            for o in items: o["excerpt"] = OLD_EDIT.get(target, "call clips cut out, high-passed, mono, 48 kHz")
            return old[0].strip()
    clips = []
    fc, gap, k, lp = CALL_CFG.get(target, DEFAULT_CALL)
    for o in items:
        try:
            x = load(f"{BUILD}/dl/src/{o['file']}").mean(axis=1)
        except Exception as e:
            print("skip", o["file"], e); o["skip"] = True; continue
        y, found = find_calls(x, fc, gap, k, lp)
        take = found[: (8 if len(items) == 1 else 4)]
        for snr, a, b in sorted(take, key=lambda t: t[1]):
            a, b = max(0, a - int(0.1 * SR)), min(len(y), b + int(0.25 * SR))
            c = y[a:b].copy()
            fi, fo = int(0.05 * SR), min(int(0.25 * SR), len(c) // 3)
            c[:fi] *= np.sin(np.linspace(0, np.pi / 2, fi)) ** 2       # gentle start
            c[-fo:] *= np.cos(np.linspace(0, np.pi / 2, fo)) ** 2      # long, smooth tail
            c *= 0.6 / (np.abs(c).max() + 1e-9)
            clips.append(c)
    if not clips:
        print("NO CALLS", target); return None
    gapz = np.zeros(int(0.05 * SR))
    data, idx, pos = [], [], 0
    for c in clips:
        idx += [pos, pos + len(c)]
        data += [c, gapz]; pos += len(c) + len(gapz)
    allc = np.concatenate(data)
    write_ogg(f"{ASSETS}/birds_{target}.ogg", allc)
    sf.write(f"{BUILD}/previews/bird-{target}.wav", np.concatenate([np.concatenate([c, np.zeros(SR // 2)]) for c in clips]), SR, subtype="PCM_16")
    print(f"calls {target:<10} {len(clips)} clips, {sum(len(c) for c in clips) / SR:5.1f}s")
    return target + " " + " ".join(map(str, idx))


index = []
by_target = {}
for o in man:
    if o["kind"] == "loop": make_loop(o)
    else: by_target.setdefault(o["target"], []).append(o)
for target, items in by_target.items():
    line = make_calls(target, items)
    if line: index.append(line)
    for o in items: o.setdefault("excerpt", "call clips cut out, high-passed, mono, 48 kHz")
open(f"{ASSETS}/birds_index.txt", "w").write("\n".join(index) + "\n")

# Credits (attribution for CC BY / CC BY-SA; CC0 listed as courtesy)
lines = ["사운드드라이브 소리 출처 / Sound credits", ""]
for o in man:
    if o.get("skip"): continue
    lines.append(f"[{o['target']}] \"{o['title']}\" — {o['author']} — {o['license']}")
    lines.append(f"  {o['page']}")
    lines.append(f"  Edited: {o['excerpt']}")
lines.append("[waves] \"Ocean Waves on a Tropical Beach\" — Jarrod stanley — CC0 1.0")
lines.append("  https://commons.wikimedia.org/wiki/File:Ocean_Waves_on_a_Tropical_Beach.ogg")
lines.append("  Edited: 90 s from 75 s, seamless loop, 48 kHz, level normalized")
lines.append("")
lines.append("CC BY-SA recordings: the edited versions in this app are shared under the same license.")
lines.append("")
lines.append("장소 데이터 / Place data: © OpenStreetMap contributors, ODbL 1.0 — https://www.openstreetmap.org/copyright")
lines.append("  Extracted once (airports, libraries, temples, stations, valleys/national parks, beaches/harbours, cafes, baseball stadiums) into assets/places.tsv.")
open(f"{ASSETS}/CREDITS.txt", "w").write("\n".join(lines) + "\n")
print("credits written")
