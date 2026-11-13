// 사운드드라이브 웹판 — same sounds and features as the Galaxy app, in the browser.
// Web Audio HRTF panning, real recordings (see CREDITS.txt), offline place list for unlocks.
"use strict";

// ── Data ──────────────────────────────────────────────────────────────────
const SOUNDS = [
  { id: "rain", name: "빗소리", color: "#5AA9E6", x: -0.45, y: -0.35 },
  { id: "waves", name: "파도", color: "#3FC1A6", x: 0, y: 0.62 },
  { id: "wind", name: "바람", color: "#B8C4D6", x: 0.55, y: -0.3 },
  { id: "fire", name: "모닥불", color: "#F29B4B", x: -0.3, y: 0.35 },
  { id: "birds", name: "새", color: "#9BD45A", x: 0.4, y: 0.45, flock: true },
  { id: "pad", name: "앰비언트", color: "#B48CF2", x: 0, y: -0.6 },
  { id: "thunder", name: "천둥번개", color: "#FFE066", x: 0.5, y: -0.7 },
  { id: "cmb", name: "우주배경복사", color: "#FF9EC7", x: 0, y: -0.92 },
  { id: "saturn", name: "토성", color: "#E3C08D", x: -0.85, y: -0.35 },
  { id: "jupiter", name: "목성", color: "#D99A6C", x: 0.85, y: -0.35 },
  { id: "u_airport", name: "비행기 기내", color: "#8FB8FF", x: -0.6, y: 0.1, unlock: "airport" },
  { id: "u_library", name: "도서관", color: "#D9B98C", x: 0.6, y: 0.1, unlock: "library" },
  { id: "u_temple", name: "사찰 풍경", color: "#E8D27A", x: -0.25, y: -0.75, unlock: "temple" },
  { id: "u_station", name: "기차 안", color: "#9AA7B5", x: 0.25, y: -0.75, unlock: "station" },
  { id: "u_valley", name: "계곡물", color: "#6FD3E0", x: -0.7, y: 0.5, unlock: "valley" },
  { id: "u_harbor", name: "항구 갈매기", color: "#7FB0D8", x: 0.7, y: 0.5, unlock: "harbor" },
  { id: "u_cafe", name: "카페", color: "#C79A7A", x: -0.2, y: 0.8, unlock: "cafe" },
  { id: "u_baseball", name: "야구장", color: "#FF8A80", x: 0.2, y: 0.8, unlock: "baseball" },
  { id: "r_hangang", name: "한강 밤", color: "#7FA7D9", x: -0.35, y: 0.9, region: 1 },
  { id: "r_chuncheon", name: "호숫가 물새", color: "#7CC6C0", x: -0.1, y: 0.95, region: 1 },
  { id: "r_cheorwon", name: "두루미", color: "#E6E2D6", x: 0.1, y: 0.95, region: 1 },
  { id: "r_danyang", name: "동굴 물방울", color: "#8EA6B4", x: 0.35, y: 0.9, region: 1 },
  { id: "r_taean", name: "갯벌", color: "#B59B7A", x: -0.95, y: 0, region: 1 },
  { id: "r_jeonju", name: "한옥 처마 빗소리", color: "#9C8C7A", x: -0.9, y: -0.35, region: 1 },
  { id: "r_suncheon", name: "갈대와 철새", color: "#CDB77E", x: 0.95, y: 0, region: 1 },
  { id: "r_yeosu", name: "여수 밤바다", color: "#5E7FC2", x: 0.9, y: -0.35, region: 1 },
  { id: "r_namhae", name: "다랭이논 개구리", color: "#8BC34A", x: -0.65, y: -0.65, region: 1 },
  { id: "r_andong", name: "하회마을 대숲", color: "#79A86B", x: 0.65, y: -0.65, region: 1 },
  { id: "r_tongyeong", name: "통영 어시장", color: "#E0A06A", x: -0.35, y: -0.9, region: 1 },
  { id: "r_ulleung", name: "절벽 갈매기", color: "#B0C4D8", x: 0.35, y: -0.9, region: 1 },
  { id: "r_jeju", name: "제주 바람", color: "#9FD8C8", x: -0.1, y: -0.95, region: 1 },
  { id: "r_udo", name: "섬 파도", color: "#56B4D3", x: 0.1, y: -0.95, region: 1 },
  { id: "s_jinhae", name: "벚꽃길", color: "#F7B5C8", x: -0.85, y: 0.2, spot: "jinhae" },
  { id: "s_boseong", name: "차밭 봄바람", color: "#8FD19E", x: -0.75, y: 0.45, spot: "boseong" },
  { id: "s_gasiri", name: "봄 들판 종다리", color: "#F2D96B", x: -0.55, y: 0.7, spot: "gasiri" },
  { id: "s_yangyang", name: "서핑", color: "#4FC3D9", x: 0.85, y: 0.2, spot: "yangyang" },
  { id: "s_haeundae", name: "한여름 해변", color: "#FFB36B", x: 0.75, y: 0.45, spot: "haeundae" },
  { id: "s_damyang", name: "대숲 바람", color: "#6FBF73", x: 0.55, y: 0.7, spot: "damyang" },
  { id: "s_maisan", name: "낙엽 밟기", color: "#D9893B", x: -0.85, y: -0.2, spot: "maisan" },
  { id: "s_naejangsan", name: "단풍숲 바람", color: "#C8553D", x: -0.75, y: -0.45, spot: "naejangsan" },
  { id: "s_mindungsan", name: "억새 바람", color: "#C9B48A", x: -0.55, y: -0.7, spot: "mindungsan" },
  { id: "s_daegwallyeong", name: "눈보라", color: "#DDE8F5", x: 0.85, y: -0.2, spot: "daegwallyeong" },
  { id: "s_pyeongchang", name: "스키장", color: "#A9C8E8", x: 0.75, y: -0.45, spot: "pyeongchang" },
  { id: "s_hwacheon", name: "얼음 갈라짐", color: "#9FD3E6", x: 0.55, y: -0.7, spot: "hwacheon" },
];
// Region sounds (default unlocks): anywhere in the 시·군·섬 opens its sound.
const REGIONS = [
  ["yeongjong", "인천 영종도", "u_airport", "✈️", 8000, [[37.47754, 126.4697]]],
  ["hangang", "서울 한강", "r_hangang", "🌉", 1500, [[37.52567, 126.93606], [37.50887, 126.99394], [37.52983, 127.06811]]],
  ["chuncheon", "춘천", "r_chuncheon", "🦆", 8000, [[37.88106, 127.72976]]],
  ["sokcho", "속초·설악", "u_valley", "🏞️", 8000, [[38.20696, 128.59134], [38.16062, 128.49588]]],
  ["gangneung", "강릉", "u_cafe", "☕", 10000, [[37.75253, 128.87595]]],
  ["cheorwon", "철원", "r_cheorwon", "🕊️", 12000, [[38.24315, 127.25125]]],
  ["danyang", "단양", "r_danyang", "💧", 8000, [[36.98643, 128.36824]]],
  ["taean", "태안", "r_taean", "🦀", 15000, [[36.75791, 126.29652]]],
  ["jeonju", "전주 한옥마을", "r_jeonju", "🏮", 1500, [[35.81816, 127.15305]]],
  ["gokseong", "곡성", "u_station", "🚂", 8000, [[35.28015, 127.28443]]],
  ["suncheon", "순천만", "r_suncheon", "🌾", 4000, [[34.88668, 127.5075]]],
  ["yeosu", "여수", "r_yeosu", "🌃", 10000, [[34.7393, 127.73594]]],
  ["namhae", "남해", "r_namhae", "🐸", 15000, [[34.83855, 127.89366]]],
  ["gyeongju", "경주", "u_temple", "🛕", 12000, [[35.85567, 129.22488]]],
  ["andong", "안동 하회마을", "r_andong", "🎋", 2500, [[36.54024, 128.52082]]],
  ["busan", "부산", "u_harbor", "⚓", 15000, [[35.17995, 129.07524]]],
  ["tongyeong", "통영", "r_tongyeong", "🐟", 8000, [[34.85425, 128.4332]]],
  ["ulleung", "울릉도", "r_ulleung", "🪨", 9000, [[37.50111, 130.85861]]],
  ["jeju", "제주도", "r_jeju", "🌬️", 20000, [[33.50004, 126.52936], [33.25395, 126.55999], [33.39, 126.25], [33.45, 126.85]]],
  ["udo", "우도", "r_udo", "🌊", 3000, [[33.5067, 126.95574]]],
].map(([id, name, sound, emoji, r, pts]) => ({ id, name, sound, emoji, r, pts }));
const regionDist = (g, lat, lon) => Math.min(...g.pts.map(([a, b]) => dist(lat, lon, a, b)));
const regionPoint = (g, lat, lon) => g.pts.reduce((best, p) => (dist(lat, lon, p[0], p[1]) < dist(lat, lon, best[0], best[1]) ? p : best), g.pts[0]);

// Seasonal landmarks: unlock only at the place, in its season (0 spring … 3 winter). Kept once unlocked.
const SEASONS = ["봄", "여름", "가을", "겨울"];
const seasonOf = (m) => (m >= 3 && m <= 5 ? 0 : m >= 6 && m <= 8 ? 1 : m >= 9 && m <= 11 ? 2 : 3);
const SPOTS = [
  { id: "jinhae", place: "진해 여좌천", season: 0, r: 2500, pts: [[35.15688, 128.65964]] },
  { id: "boseong", place: "보성 녹차밭", season: 0, r: 2500, pts: [[34.71585, 127.07796]] },
  { id: "gasiri", place: "제주 가시리 유채꽃길", season: 0, r: 2500, pts: [[33.40969, 126.71984], [33.38189, 126.7329]] },
  { id: "yangyang", place: "양양 서핑 해변", season: 1, r: 2500, pts: [[38.02325, 128.72332], [37.97143, 128.75985]] },
  { id: "haeundae", place: "해운대", season: 1, r: 1500, pts: [[35.15929, 129.15861]] },
  { id: "damyang", place: "담양 죽녹원", season: 1, r: 1500, pts: [[35.32863, 126.98556]] },
  { id: "maisan", place: "진안 마이산", season: 2, r: 3000, pts: [[35.758, 127.412]] },
  { id: "naejangsan", place: "내장산", season: 2, r: 3000, pts: [[35.48974, 126.90198], [35.47833, 126.88899]] },
  { id: "mindungsan", place: "정선 민둥산", season: 2, r: 3000, pts: [[37.24534, 128.77296]] },
  { id: "daegwallyeong", place: "대관령", season: 3, r: 4000, pts: [[37.68712, 128.76041], [37.68953, 128.7533]] },
  { id: "pyeongchang", place: "평창 스키장", season: 3, r: 3000, pts: [[37.62815, 128.67773], [37.57865, 128.31635], [37.66244, 128.68092]] },
  { id: "hwacheon", place: "화천 산천어축제", season: 3, r: 3000, pts: [[38.1059, 127.71058]] },
];
const BIRDS = [
  ["dove", "멧비둘기", 0, 8], ["sparrow", "참새", 0, 2], ["magpie", "까치", 0, 6], ["tit", "박새", 0, 4],
  ["bulbul", "직박구리", 0, 5], ["oriole", "꾀꼬리", 0, 7], ["warbler", "휘파람새", 0, 8],
  ["woodpecker", "딱따구리", 0, 10], ["cuckoo", "뻐꾸기", 0, 14],
  ["owl", "부엉이", 1, 10], ["scops", "소쩍새", 1, 12], ["nightjar", "쏙독새", 1, 14],
].map(([id, name, night, gap]) => ({ id, name, night: !!night, gap }));
const CATS = [
  { id: "airport", place: "공항", r: 3000, emoji: "✈️", color: "#8FB8FF", hint: "영종도, 김포공항, 제주공항에 가면 열려요" },
  { id: "library", place: "도서관", r: 400, emoji: "📚", color: "#D9B98C", hint: "별마당도서관, 파주 지혜의숲, 국립중앙도서관에 가면 열려요" },
  { id: "temple", place: "사찰", r: 1000, emoji: "🛕", color: "#E8D27A", hint: "경주 불국사, 합천 해인사, 순천 송광사에 가면 열려요" },
  { id: "station", place: "기차", r: 1000, emoji: "🚉", color: "#9AA7B5", hint: "의왕 철도박물관이나 곡성 섬진강기차마을에 가면 열려요" },
  { id: "valley", place: "계곡", r: 2000, emoji: "🏞️", color: "#6FD3E0", hint: "설악산 비선대, 지리산 뱀사골, 가평 용추계곡에 가면 열려요" },
  { id: "harbor", place: "항구", r: 1000, emoji: "⚓", color: "#7FB0D8", hint: "부산 자갈치, 인천 연안부두, 목포항에 가면 열려요" },
  { id: "cafe", place: "카페거리", r: 1000, emoji: "☕", color: "#C79A7A", hint: "강릉 안목 커피거리나 성수동 카페거리에 가면 열려요" },
  { id: "baseball", place: "야구장", r: 500, emoji: "⚾", color: "#FF8A80", hint: "프로야구 홈구장(잠실, 사직, 고척 등)에 가면 열려요" },
];
const SLEEP = [0, 15, 30, 60, 90], FADE_MS = 10 * 60e3;
const MOTION = ["멈춤", "🪐 행성 궤도", "🔄 360° 회전"];
const $ = (id) => document.getElementById(id);

// ── State (persisted) ─────────────────────────────────────────────────────
const saved = JSON.parse(localStorage.getItem("sd:state") || "{}");
const S = {
  master: saved.master ?? 0.8, motion: saved.motion ?? 0, birdAuto: saved.birdAuto ?? true,
  birdsOn: saved.birdsOn ?? Object.fromEntries(BIRDS.map((b) => [b.id, true])),
  unlocked: saved.unlocked ?? {}, spots: saved.spots ?? {}, spotNote: saved.spotNote ?? {},
  regions: saved.regions ?? {}, stamps: saved.stamps ?? {},
};
const first = !saved.sounds;
for (const s of SOUNDS) {
  const p = saved.sounds?.[s.id] ?? {};
  s.hx = s.x; s.hy = s.y;
  s.x = p.x ?? s.x; s.y = p.y ?? s.y; s.volume = p.v ?? 0.8;
  s.on = p.on ?? (first && (s.id === "rain" || s.id === "pad"));
  s.locked = (!!s.unlock && !S.unlocked[s.unlock]) || (!!s.spot && !S.spots[s.spot])
    || (!!s.region && !REGIONS.some((g) => g.sound === s.id && S.regions[g.id]));
  s.anim = { shown: s.on ? 1 : 0, pop: 0 };
}
let saveTimer;
function save() {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => localStorage.setItem("sd:state", JSON.stringify({
    master: S.master, motion: S.motion, birdAuto: S.birdAuto, birdsOn: S.birdsOn, unlocked: S.unlocked, spots: S.spots, spotNote: S.spotNote, regions: S.regions, stamps: S.stamps,
    sounds: Object.fromEntries(SOUNDS.map((s) => [s.id, { x: s.x, y: s.y, v: s.volume, on: s.on }])),
  })), 300);
}

// ── Audio ─────────────────────────────────────────────────────────────────
let ctx, masterGain, sceneGain, playing = false, audioIndex;
const buffers = new Map();
async function buffer(url) {
  if (!buffers.has(url)) buffers.set(url, fetch(url).then((r) => r.arrayBuffer()).then((a) => ctx.decodeAudioData(a)));
  return buffers.get(url);
}
function ensureAudio() {
  if (ctx) return;
  ctx = new (window.AudioContext || window.webkitAudioContext)();
  sceneGain = ctx.createGain();
  masterGain = ctx.createGain();
  masterGain.gain.value = S.master * 1.6;
  sceneGain.connect(masterGain).connect(ctx.destination);
  fetch("audio/index.json").then((r) => r.json()).then((j) => { audioIndex = j; });
}
function panner() {
  const p = ctx.createPanner();
  p.panningModel = "HRTF"; p.distanceModel = "inverse"; p.refDistance = 1; p.maxDistance = 50; p.rolloffFactor = 1;
  return p;
}
// smooth = time constant in seconds: tiny while dragging (follow the finger), a bit more for orbits.
function place(p, x, y, smooth = 0.012) {
  let r = Math.hypot(x, y);
  if (r < 0.15) { const k = r < 1e-3 ? 0 : 0.15 / r; x *= k; y = r < 1e-3 ? -0.15 : y * k; }
  const t = ctx.currentTime;
  // stage units → metres; front of the stage (−y) is in front of the listener (−z)
  p.positionX.setTargetAtTime(x * 3, t, smooth);
  p.positionY.setTargetAtTime(0, t, smooth);
  p.positionZ.setTargetAtTime(y * 3, t, smooth);
}

// Looping recordings: overlapping copies with equal-power crossfades (no gap at the seam,
// whatever padding the mp3 encoder added).
const XF = 2.0;
class LoopVoice {
  constructor(s) {
    this.s = s; this.out = ctx.createGain(); this.pan = panner(); this.out.gain.value = 0;
    this.out.connect(this.pan).connect(sceneGain); place(this.pan, s.x, s.y, 0.001);
    this.timer = null; this.copies = []; this.stopped = false;
    buffer(`audio/${s.id}.mp3`).then((b) => { if (!this.stopped) { this.buf = b; this.next(ctx.currentTime + 0.01, true); } });
    this.out.gain.setTargetAtTime(s.volume, ctx.currentTime, 0.02);
  }
  // first = the copy that starts when the sound is switched on: near-instant fade-in.
  // Later copies overlap the previous one with a 2 s equal-power crossfade.
  next(at, first = false) {
    const d = this.buf.duration, src = ctx.createBufferSource(), g = ctx.createGain();
    src.buffer = this.buf; src.connect(g).connect(this.out);
    const n = 32, up = new Float32Array(n), down = new Float32Array(n);
    for (let i = 0; i < n; i++) { up[i] = Math.sin((i / (n - 1)) * Math.PI / 2); down[i] = Math.cos((i / (n - 1)) * Math.PI / 2); }
    g.gain.value = 0;
    g.gain.setValueCurveAtTime(up, at, first ? 0.04 : XF);
    g.gain.setValueCurveAtTime(down, at + d - XF - 0.05, XF);
    src.start(at); src.stop(at + d);
    this.copies.push(src);
    src.onended = () => { this.copies = this.copies.filter((c) => c !== src); };
    const nextAt = at + d - XF - 0.05;
    this.timer = setTimeout(() => !this.stopped && this.next(nextAt), Math.max(0, (nextAt - ctx.currentTime - 1.5) * 1000));
  }
  volume(v) { this.out.gain.setTargetAtTime(v, ctx.currentTime, 0.02); }
  move(smooth) { place(this.pan, this.s.x, this.s.y, smooth); }
  stop() {
    this.stopped = true; clearTimeout(this.timer);
    this.out.gain.setTargetAtTime(0, ctx.currentTime, 0.03);
    const copies = this.copies; setTimeout(() => { copies.forEach((c) => { try { c.stop(); } catch (e) {} }); this.out.disconnect(); }, 400);
  }
}

// Bird flock: real call clips, each from its own spot around the dot.
class FlockVoice {
  constructor(s) {
    this.s = s; this.stopped = false; this.last = {}; this.active = new Map();
    this.out = ctx.createGain(); this.out.gain.value = 0; this.out.connect(sceneGain);
    this.out.gain.setTargetAtTime(1, ctx.currentTime, 0.02);
    this.schedule(60);
  }
  schedule(ms) { this.timer = setTimeout(() => { if (!this.stopped) { this.call(); this.schedule(1200 + Math.random() * 3300); } }, ms); }
  call() {
    if (!audioIndex || this.active.size >= 5) return;
    const h = new Date().getHours(), night = h >= 19 || h < 5, now = ctx.currentTime;
    const on = BIRDS.filter((b) => S.birdsOn[b.id] && audioIndex.birds[b.id]);
    const fits = on.filter((b) => !S.birdAuto || b.night === night);
    const pool = (fits.length ? fits : on).filter((b) => !this.active.has(b.id) && now - (this.last[b.id] ?? -1e9) >= b.gap);
    if (!pool.length) return;
    const b = pool[Math.floor(Math.random() * pool.length)];
    const files = audioIndex.birds[b.id];
    this.last[b.id] = now;
    buffer(`audio/birds/${files[Math.floor(Math.random() * files.length)]}`).then((buf) => {
      if (this.stopped) return;
      const a = Math.random() * Math.PI * 2, d = 0.15 + Math.random() * 0.35;
      const call = { dx: Math.cos(a) * d, dy: Math.sin(a) * d };
      const src = ctx.createBufferSource(), g = ctx.createGain(), p = panner();
      call.p = p;
      src.buffer = buf; src.playbackRate.value = 0.97 + Math.random() * 0.06;
      g.gain.value = this.s.volume * (0.7 + Math.random() * 0.3);
      src.connect(g).connect(p).connect(this.out); this.placeCall(call, 0.001);
      call.src = src;
      this.active.set(b.id, call);
      src.onended = () => { this.active.delete(b.id); p.disconnect(); };
      src.start();
    });
  }
  // Each bird keeps its offset from the dot, so dragging or orbiting the dot moves the birds with it.
  placeCall(c, smooth) {
    let x = this.s.x + c.dx, y = this.s.y + c.dy;
    const len = Math.hypot(x, y); if (len > 1) { x /= len; y /= len; }
    place(c.p, x, y, smooth);
  }
  volume() {}
  move(smooth) { this.active.forEach((c) => this.placeCall(c, smooth)); }
  stop() {
    this.stopped = true; clearTimeout(this.timer);
    this.out.gain.setTargetAtTime(0, ctx.currentTime, 0.03);
    setTimeout(() => { this.active.forEach((c) => { try { c.src.stop(); } catch (e) {} }); this.out.disconnect(); }, 400);
  }
}

const voices = new Map();
function syncVoices() {
  for (const s of SOUNDS) {
    const want = playing && s.on && !s.locked;
    const v = voices.get(s.id);
    if (want && !v) voices.set(s.id, s.flock ? new FlockVoice(s) : new LoopVoice(s));
    else if (!want && v) { v.stop(); voices.delete(s.id); }
  }
}
function play() {
  ensureAudio(); ctx.resume();
  playing = true; sleepEnded = false; syncVoices(); applyScene(); refresh();
  preload();
}

// Fetch and decode the other sounds in the background so switching one on is instant.
// Skipped when the browser asks to save data.
let preloading = false;
async function preload() {
  if (preloading || navigator.connection?.saveData) return;
  preloading = true;
  while (!audioIndex) await new Promise((r) => setTimeout(r, 200));
  const order = SOUNDS.filter((s) => !s.flock && !s.locked).sort((a, b) => Number(b.on) - Number(a.on));
  for (const s of order) { try { await buffer(`audio/${s.id}.mp3`); } catch (e) {} }
  for (const b of BIRDS) { const f = audioIndex.birds[b.id]; if (f) for (const x of f) { try { await buffer(`audio/birds/${x}`); } catch (e) {} } }
}
function stop() { playing = false; syncVoices(); refresh(); }

// ── Sleep timer & scene level ─────────────────────────────────────────────
let sleepMin = 0, sleepEnd = 0, sleepEnded = false;
function sleepLeft() { return sleepEnd ? Math.max(0, sleepEnd - Date.now()) : 0; }
function applyScene() {
  if (!ctx) return;
  const left = sleepLeft(), g = sleepEnd && left < FADE_MS ? left / FADE_MS : 1;
  sceneGain.gain.setTargetAtTime(g, ctx.currentTime, 0.5);
}
setInterval(() => {
  if (sleepEnd) {
    applyScene();
    if (sleepLeft() === 0) { sleepEnd = 0; sleepMin = 0; sleepEnded = true; stop(); }
    refresh();
  }
}, 1000);

// ── Motion (planet orbits / 360° turn) ───────────────────────────────────
let lastMove = performance.now(), moveSave = 0;
setInterval(() => {
  const now = performance.now(), dt = Math.min(0.5, (now - lastMove) / 1000);
  lastMove = now;
  if (!S.motion) return;
  for (const s of SOUNDS) {
    const r = Math.hypot(s.x, s.y);
    if (r < 1e-4) continue;
    const period = S.motion === 1 ? 20 + 80 * Math.min(1, r) : 60;
    const a = (2 * Math.PI * dt) / period, c = Math.cos(a), sn = Math.sin(a);
    [s.x, s.y] = [s.x * c - s.y * sn, s.x * sn + s.y * c];
  }
  voices.forEach((v) => v.move(0.05)); // orbit steps every 50 ms: glide between them
  if ((moveSave += dt) > 5) { moveSave = 0; save(); }
  requestDraw();
}, 50);

// ── Stage canvas: dots, drag, water-drop ripples ─────────────────────────
const cv = $("stage"), g2 = cv.getContext("2d");
let W = 0, H = 0, DPR = 1, CX = 0, CY = 0, R = 0, drawQueued = false;
const ripples = [];
function resize() {
  DPR = window.devicePixelRatio || 1;
  const r = cv.getBoundingClientRect(); W = r.width; H = r.height;
  cv.width = W * DPR; cv.height = H * DPR; g2.setTransform(DPR, 0, 0, DPR, 0, 0);
  CX = W / 2; CY = H / 2; R = Math.min(W, H) / 2 - 62;
  requestDraw();
}
window.addEventListener("resize", resize);
function requestDraw() { if (!drawQueued) { drawQueued = true; requestAnimationFrame(draw); } }
let lastFrame = 0;
let wavePhase = 0, selected = null;
const INK = "#ededed", MUTED = "#8a8a8a";
function sideText(x, y) {
  const deg = (Math.atan2(x, -y) * 180) / Math.PI, a = Math.round(Math.abs(deg));
  if (a === 0 || a === 180) return (a === 0 ? "앞 " : "뒤 ") + a + "°";
  return (deg > 0 ? "오른쪽 " : "왼쪽 ") + a + "°";
}
function draw(t) {
  drawQueued = false;
  const dt = lastFrame ? Math.min(0.05, (t - lastFrame) / 1000) : 0.016; lastFrame = t;
  let busy = S.motion > 0 || playing;
  if (playing) wavePhase = (wavePhase + dt * 9) % 16;
  g2.fillStyle = "#0b0b0b"; g2.fillRect(0, 0, W, H);
  const glow = g2.createRadialGradient(CX, CY, 0, CX, CY, R * 0.9);
  glow.addColorStop(0, "rgba(255,255,255,.13)"); glow.addColorStop(1, "rgba(255,255,255,0)");
  g2.fillStyle = glow; g2.beginPath(); g2.arc(CX, CY, R * 0.9, 0, 7); g2.fill();
  // ripple fields from every sound that is on
  g2.lineWidth = 0.8; g2.strokeStyle = INK;
  for (const s of SOUNDS) {
    if (s.locked || s.anim.shown < 0.02) continue;
    const px = CX + s.x * R, py = CY + s.y * R, maxR = R * 1.25;
    for (let rr = 22 + wavePhase; rr < maxR; rr += 16) {
      const a = 1 - rr / maxR;
      g2.globalAlpha = 0.26 * a * a * s.anim.shown * (0.5 + 0.5 * s.volume);
      g2.beginPath(); g2.arc(px, py, rr, 0, 7); g2.stroke();
    }
  }
  // compass ring
  g2.globalAlpha = 0.43; g2.lineWidth = 1.1; g2.beginPath(); g2.arc(CX, CY, R, 0, 7); g2.stroke();
  for (let d = 0; d < 360; d += 5) {
    const a = ((d - 90) * Math.PI) / 180, card = d % 90 === 0, mid = d % 15 === 0;
    const i0 = R - (card ? 0 : mid ? 9 : 5), o0 = R + (card ? 18 : 0);
    g2.globalAlpha = card ? 0.8 : mid ? 0.47 : 0.24;
    g2.beginPath(); g2.moveTo(CX + Math.cos(a) * i0, CY + Math.sin(a) * i0); g2.lineTo(CX + Math.cos(a) * o0, CY + Math.sin(a) * o0); g2.stroke();
  }
  g2.globalAlpha = 1; g2.fillStyle = MUTED; g2.font = "15px sans-serif"; g2.textAlign = "center";
  g2.fillText("앞", CX, CY - R - 28); g2.fillText("뒤", CX, CY + R + 40);
  g2.textAlign = "right"; g2.fillText("왼쪽", CX - R - 26, CY + 5); g2.textAlign = "left"; g2.fillText("오른쪽", CX + R + 26, CY + 5); g2.textAlign = "center";
  // touch ripples
  for (let i = ripples.length - 1; i >= 0; i--) {
    const rp = ripples[i], k = (t - rp.t0) / 900;
    if (k >= 1) { ripples.splice(i, 1); continue; }
    busy = true;
    for (let j = 0; j < 3; j++) {
      const kj = k - j * 0.16; if (kj <= 0) continue;
      const e = 1 - (1 - kj) * (1 - kj);
      g2.globalAlpha = 0.66 * rp.s * (1 - kj) * (1 - kj); g2.lineWidth = (1.8 - j * 0.4);
      g2.beginPath(); g2.arc(rp.x, rp.y, 10 + e * 70 * rp.s, 0, 7); g2.stroke();
    }
  }
  // dotted guides
  g2.setLineDash([2, 6]); g2.lineWidth = 1; g2.globalAlpha = 0.22;
  for (const s of SOUNDS) if (!s.locked && s.on) { g2.beginPath(); g2.moveTo(CX, CY); g2.lineTo(CX + s.x * R, CY + s.y * R); g2.stroke(); }
  g2.setLineDash([]);
  // car
  const carR = R * 0.18;
  g2.globalAlpha = 1; g2.fillStyle = "#0b0b0b"; g2.beginPath(); g2.arc(CX, CY, carR, 0, 7); g2.fill();
  g2.globalAlpha = 0.35; g2.lineWidth = 1.2; g2.beginPath(); g2.arc(CX, CY, carR, 0, 7); g2.stroke();
  const cw = carR * 0.46, ch = carR * 1.12;
  g2.globalAlpha = 0.9; g2.lineWidth = 1.6;
  g2.beginPath(); g2.roundRect(CX - cw / 2, CY - ch / 2, cw, ch, cw / 2); g2.stroke();
  g2.beginPath(); g2.ellipse(CX, CY + ch * 0.21, cw * 0.32, ch * 0.09, 0, 0.35, Math.PI - 0.35); g2.stroke();
  g2.globalAlpha = 1; g2.fillStyle = INK; g2.beginPath(); g2.arc(CX, CY, 2.6, 0, 7); g2.fill();
  // sounds
  for (const s of SOUNDS) {
    if (s.locked) continue;
    const a = s.anim, target = s.on ? 1 : 0;
    if (Math.abs(a.shown - target) > 0.001) { a.shown += (target - a.shown) * Math.min(1, dt * 10); busy = true; }
    if (a.pop > 0.001) { a.pop *= Math.exp(-dt * 6); busy = true; } else a.pop = 0;
    const pop = 1 + 0.3 * a.pop * Math.cos(a.pop * 9), px = CX + s.x * R, py = CY + s.y * R;
    if (a.shown < 0.5) {
      g2.globalAlpha = 1; g2.fillStyle = "#0b0b0b"; g2.beginPath(); g2.arc(px, py, 6, 0, 7); g2.fill();
      g2.globalAlpha = 0.6; g2.lineWidth = 1.2; g2.beginPath(); g2.arc(px, py, 6 * pop, 0, 7); g2.stroke();
      g2.globalAlpha = 1; g2.fillStyle = MUTED; g2.font = "13px sans-serif"; g2.fillText(s.name, px, py + 24);
    } else {
      const r = (28 + 20 * s.volume) * pop * (drag && drag.s === s ? 1.12 : 1);
      g2.globalAlpha = 1; g2.fillStyle = "#0b0b0b"; g2.beginPath(); g2.arc(px, py, r, 0, 7); g2.fill();
      g2.globalAlpha = 0.94; g2.lineWidth = 1.5; g2.beginPath(); g2.arc(px, py, r, 0, 7); g2.stroke();
      if (s === selected) { g2.globalAlpha = 0.6; g2.lineWidth = 1; g2.beginPath(); g2.arc(px, py, r + 7, 0, 7); g2.stroke(); }
      drawIcon(g2, s.id, px, py, r * 0.86, 0.92);
      g2.globalAlpha = 1; g2.fillStyle = INK; g2.font = "bold 17px sans-serif"; g2.fillText(s.name, px, py + r + 24);
      g2.fillStyle = MUTED; g2.font = "12px ui-monospace, Menlo, monospace"; g2.fillText(`${sideText(s.x, s.y)}  /  ${Math.round(s.volume * 100)}`, px, py + r + 44);
    }
  }
  g2.globalAlpha = 1;
  if (busy) requestDraw(); else lastFrame = 0;
}

// ── Line-art icons (same patterns as the app's LineArt) ────────────────────
const PATTERN = { rain: "RAIN", r_jeonju: "RAIN", waves: "WAVES", r_hangang: "WAVES", r_yeosu: "WAVES", r_tongyeong: "WAVES", r_udo: "WAVES", s_yangyang: "WAVES", s_haeundae: "WAVES",
  wind: "WIND", r_jeju: "WIND", s_boseong: "WIND", fire: "FLAME", birds: "BIRDS", u_harbor: "BIRDS", r_cheorwon: "BIRDS", r_ulleung: "BIRDS", s_gasiri: "BIRDS",
  pad: "RINGS", r_chuncheon: "RINGS", r_namhae: "RINGS", thunder: "ZIGZAG", cmb: "STARS", r_taean: "STARS", saturn: "SATURN", jupiter: "BANDS",
  u_airport: "PLANE", u_library: "BOOKS", u_temple: "BELL", u_station: "RAILS", u_valley: "DROPS", r_danyang: "DROPS", u_cafe: "STEAM", u_baseball: "SEAM",
  r_suncheon: "REEDS", s_mindungsan: "REEDS", r_andong: "BAMBOO", s_damyang: "BAMBOO", s_daegwallyeong: "SNOW", s_pyeongchang: "SNOW", s_hwacheon: "CRACK",
  s_jinhae: "LEAVES", s_maisan: "LEAVES", s_naejangsan: "LEAVES" };
function seeded(str) { let h = 2166136261; for (const ch of str) h = Math.imul(h ^ ch.charCodeAt(0), 16777619); return () => ((h = Math.imul(h ^ (h >>> 15), 2246822507) ^ Math.imul(h ^ (h >>> 13), 3266489909), (h >>>= 0) / 4294967296)); }
function drawIcon(c, id, cx, cy, r, alpha) {
  const rnd = seeded(id), L = (x1, y1, x2, y2) => { c.beginPath(); c.moveTo(x1, y1); c.lineTo(x2, y2); c.stroke(); };
  const curve = (f, n = 40) => { c.beginPath(); for (let i = 0; i <= n; i++) { const [x, y] = f(i / n); i ? c.lineTo(x, y) : c.moveTo(x, y); } c.stroke(); };
  c.save(); c.beginPath(); c.arc(cx, cy, r * 0.96, 0, 7); c.clip();
  c.globalAlpha = alpha; c.strokeStyle = INK; c.fillStyle = INK; c.lineWidth = Math.max(0.8, r * 0.028); c.lineCap = "round"; c.lineJoin = "round";
  switch (PATTERN[id] || "RINGS") {
    case "RAIN": for (let i = 0; i < 26; i++) { const x = cx - r + rnd() * 2 * r, y = cy - r + rnd() * 2 * r, l = r * (0.18 + rnd() * 0.22); L(x, y, x - l * 0.35, y + l); } break;
    case "WAVES": for (let k = -3; k <= 3; k++) curve((u) => [cx - r + 2 * r * u, cy + k * r * 0.24 + Math.sin(u * Math.PI * 3 + k) * r * 0.07]); break;
    case "WIND": case "BANDS":
      for (let k = -4; k <= 4; k++) { const amp = r * (0.03 + 0.05 * rnd()), ph = rnd() * 6; curve((u) => [cx - r + 2 * r * u, cy + k * r * 0.2 + Math.sin(u * Math.PI * 2 + ph) * amp]); }
      if (PATTERN[id] === "BANDS") { c.beginPath(); c.ellipse(cx + r * 0.29, cy + r * 0.21, r * 0.17, r * 0.09, 0, 0, 7); c.stroke(); } break;
    case "FLAME": {
      const top = cy - r * 0.78, bot = cy + r * 0.72;
      for (let k = -4; k <= 4; k++) { c.beginPath(); c.moveTo(cx, top); c.bezierCurveTo(cx + k * r * 0.1, cy - r * 0.2, cx + k * r * 0.09, cy + r * 0.3, cx + k * r * 0.02, bot); c.stroke(); }
      c.beginPath(); c.moveTo(cx, top); c.bezierCurveTo(cx + r * 0.62, cy - r * 0.05, cx + r * 0.4, bot, cx, bot); c.bezierCurveTo(cx - r * 0.4, bot, cx - r * 0.62, cy - r * 0.05, cx, top); c.stroke(); break;
    }
    case "BIRDS": for (let row = 0; row < 4; row++) for (let i = 0; i < 4; i++) {
      const y = cy - r * 0.45 + row * r * 0.32, x = cx - r * 0.75 + i * r * 0.45 + (row % 2) * r * 0.2, w = r * (0.12 + rnd() * 0.06);
      c.beginPath(); c.moveTo(x - w, y); c.quadraticCurveTo(x - w / 2, y - w * 0.7, x, y); c.quadraticCurveTo(x + w / 2, y - w * 0.7, x + w, y); c.stroke(); } break;
    case "RINGS": for (let k = 1; k <= 7; k++) { c.beginPath(); c.arc(cx, cy, (r * k) / 7.6, 0, 7); c.stroke(); } break;
    case "ZIGZAG": for (let k = -2; k <= 2; k++) { c.beginPath(); let x = cx + k * r * 0.34, y = cy - r; c.moveTo(x, y); for (let i = 0; i < 6; i++) { x += (i % 2 ? -1 : 1) * r * 0.16; y += r * 0.34; c.lineTo(x, y); } c.stroke(); } break;
    case "STARS": for (let i = 0; i < 70; i++) { c.beginPath(); c.arc(cx - r + rnd() * 2 * r, cy - r + rnd() * 2 * r, (0.4 + rnd() * 1.1) * Math.max(0.6, r / 30), 0, 7); c.fill(); } break;
    case "SATURN": c.beginPath(); c.arc(cx, cy, r * 0.36, 0, 7); c.stroke();
      for (const s2 of [0.82, 0.7]) { c.beginPath(); c.ellipse(cx, cy, r * s2, r * s2 * 0.24, -0.31, 0, 7); c.stroke(); } break;
    case "PLANE": for (let k = -3; k <= 3; k++) for (let x = cx - r; x < cx + r; x += r * 0.3) L(x, cy + k * r * 0.22, x + r * 0.16, cy + k * r * 0.22); break;
    case "BOOKS": for (let i = 0; i < 9; i++) { const x = cx - r * 0.7 + i * r * 0.17; L(x, cy + r * 0.55, x, cy + r * 0.55 - r * (0.6 + rnd() * 0.5)); } L(cx - r, cy + r * 0.55, cx + r, cy + r * 0.55); break;
    case "BELL": c.beginPath(); c.moveTo(cx - r * 0.5, cy + r * 0.45); c.bezierCurveTo(cx - r * 0.45, cy - r * 0.2, cx - r * 0.3, cy - r * 0.6, cx, cy - r * 0.6);
      c.bezierCurveTo(cx + r * 0.3, cy - r * 0.6, cx + r * 0.45, cy - r * 0.2, cx + r * 0.5, cy + r * 0.45); c.closePath(); c.stroke();
      for (let k = 1; k <= 3; k++) L(cx - r * 0.46, cy + r * (0.45 - k * 0.22), cx + r * 0.46, cy + r * (0.45 - k * 0.22)); break;
    case "RAILS": L(cx - r * 0.15, cy - r, cx - r * 0.55, cy + r); L(cx + r * 0.15, cy - r, cx + r * 0.55, cy + r);
      for (let i = 0; i < 8; i++) { const u = i / 7, w = r * (0.2 + 0.4 * u); L(cx - w, cy - r + 2 * r * u, cx + w, cy - r + 2 * r * u); } break;
    case "DROPS": for (let col = -3; col <= 3; col++) for (let i = 0; i < 5; i++) {
      const x = cx + col * r * 0.26, y = cy - r * 0.8 + i * r * 0.4 + (col % 2) * r * 0.2;
      c.beginPath(); c.moveTo(x, y - r * 0.08); c.quadraticCurveTo(x + r * 0.06, y + r * 0.02, x, y + r * 0.05); c.quadraticCurveTo(x - r * 0.06, y + r * 0.02, x, y - r * 0.08); c.stroke(); } break;
    case "STEAM": for (let k = -1; k <= 1; k++) curve((u) => [cx + k * r * 0.3 + Math.sin(u * Math.PI * 2 + k) * r * 0.12, cy + r * 0.7 - 1.4 * r * u], 30); break;
    case "SEAM": c.beginPath(); c.arc(cx, cy, r * 0.75, 0, 7); c.stroke(); c.beginPath(); c.ellipse(cx - r * 0.75, cy, r * 0.55, r * 0.75, 0, -1.05, 1.05); c.stroke();
      c.beginPath(); c.ellipse(cx + r * 0.75, cy, r * 0.55, r * 0.75, 0, Math.PI - 1.05, Math.PI + 1.05); c.stroke(); break;
    case "REEDS": for (let i = 0; i < 11; i++) { const x = cx - r * 0.8 + i * r * 0.16, top = cy - r * (0.2 + rnd() * 0.6);
      c.beginPath(); c.moveTo(x, cy + r); c.quadraticCurveTo(x + r * 0.05, cy, x + r * 0.12, top); c.stroke(); c.beginPath(); c.ellipse(x + r * 0.12, top - r * 0.06, r * 0.04, r * 0.08, 0, 0, 7); c.stroke(); } break;
    case "BAMBOO": for (let i = 0; i < 6; i++) { const x = cx - r * 0.65 + i * r * 0.26; L(x, cy - r, x, cy + r); for (let j = 0; j < 5; j++) { const y = cy - r + (j + rnd() * 0.4) * r * 0.45; L(x - r * 0.04, y, x + r * 0.04, y); } } break;
    case "SNOW": for (let i = 0; i < 40; i++) { const x = cx - r + rnd() * 2 * r, y = cy - r + rnd() * 2 * r; c.beginPath(); c.arc(x, y, 0.6 + rnd(), 0, 7); c.fill(); if (i % 3 === 0) L(x, y, x - r * 0.2, y - r * 0.08); } break;
    case "CRACK": for (let k = 0; k < 5; k++) { let x = cx, y = cy, a = (k * Math.PI * 2) / 5 + rnd(); c.beginPath(); c.moveTo(x, y);
      for (let i = 0; i < 5; i++) { a += (rnd() - 0.5) * 0.8; x += Math.cos(a) * r * 0.22; y += Math.sin(a) * r * 0.22; c.lineTo(x, y); } c.stroke(); } break;
    case "LEAVES": for (let i = 0; i < 9; i++) { const x = cx - r * 0.7 + rnd() * 1.4 * r, y = cy - r * 0.7 + rnd() * 1.4 * r, rot = rnd() * Math.PI;
      c.beginPath(); c.ellipse(x, y, r * 0.16, r * 0.07, rot, 0, 7); c.stroke(); L(x - Math.cos(rot) * r * 0.16, y - Math.sin(rot) * r * 0.16, x + Math.cos(rot) * r * 0.16, y + Math.sin(rot) * r * 0.16); } break;
  }
  c.restore();
}

function drop(x, y, color, s) { ripples.push({ x, y, s, t0: performance.now() }); if (ripples.length > 8) ripples.shift(); requestDraw(); }
function buzz(ms) { if (navigator.vibrate) navigator.vibrate(ms); }
function nearest(x, y) {
  let best = null, bd = 40;
  for (const s of SOUNDS) { if (s.locked) continue; const d = Math.hypot(CX + s.x * R - x, CY + s.y * R - y); if (d < bd) { bd = d; best = s; } }
  return best;
}
let drag = null;
cv.addEventListener("pointerdown", (e) => {
  const r = cv.getBoundingClientRect(), x = e.clientX - r.left, y = e.clientY - r.top, s = nearest(x, y);
  drag = { s, x0: x, y0: y, moved: false, tx: x, ty: y };
  cv.setPointerCapture(e.pointerId);
  drop(x, y, s ? s.color : "#ffffff", s ? 1 : 0.6); buzz(8);
});
cv.addEventListener("pointermove", (e) => {
  if (!drag || !drag.s) return;
  const r = cv.getBoundingClientRect(), x = e.clientX - r.left, y = e.clientY - r.top;
  if (Math.hypot(x - drag.x0, y - drag.y0) > 8) drag.moved = true;
  if (!drag.moved) return;
  let nx = (x - CX) / R, ny = (y - CY) / R; const d = Math.hypot(nx, ny); if (d > 1) { nx /= d; ny /= d; }
  drag.s.x = nx; drag.s.y = ny; voices.get(drag.s.id)?.move(0.006); // follow the finger
  if (Math.hypot(x - drag.tx, y - drag.ty) > 40) { drag.tx = x; drag.ty = y; buzz(4); }
  requestDraw();
});
cv.addEventListener("pointerup", (e) => {
  if (!drag) return;
  const s = drag.s;
  if (s && !drag.moved) toggle(s);
  else if (s) { const r = cv.getBoundingClientRect(); drop(e.clientX - r.left, e.clientY - r.top, s.color, 0.8); save(); }
  if (s) { selected = s; refresh(); }
  drag = null; requestDraw();
});
function toggle(s) {
  selected = s;
  s.on = !s.on; s.anim.pop = 1; drop(CX + s.x * R, CY + s.y * R, s.color, 1.3); buzz(15);
  syncVoices(); save(); refresh();
}

// ── Panel ─────────────────────────────────────────────────────────────────
function presetName() {
  for (const b of document.querySelectorAll(".preset")) {
    const ids = b.dataset.preset.split(",");
    if (SOUNDS.every((s) => s.locked || s.on === ids.includes(s.id))) return b.textContent;
  }
  return "나만의 소리";
}
function drawTile(t) {
  const s = t.s, c = t.c, d = window.devicePixelRatio || 1, w = c.clientWidth || 96;
  c.width = w * d; c.height = w * d;
  const x = c.getContext("2d"); x.setTransform(d, 0, 0, d, 0, 0); x.clearRect(0, 0, w, w);
  const cx = w / 2, cy = w / 2, r = w * 0.32;
  x.strokeStyle = INK; x.lineWidth = 1.4; x.globalAlpha = s.on ? 0.92 : 0.43;
  x.beginPath(); x.arc(cx, cy, r, 0, 7); x.stroke();
  drawIcon(x, s.id, cx, cy, r, s.on ? 0.92 : 0.37);
  if (s.on) { x.globalAlpha = 0.92; x.lineWidth = 2; x.beginPath(); x.arc(cx, cy, r + 7, -Math.PI / 2, -Math.PI / 2 + (300 * s.volume * Math.PI) / 180); x.stroke(); }
  if (s === selected) { x.globalAlpha = 0.6; x.lineWidth = 1; x.beginPath(); x.arc(cx, cy, r + 12, 0, 7); x.stroke(); }
  t.el.classList.toggle("on", s.on);
  t.vol.textContent = s.on ? Math.round(s.volume * 100) : "";
}
let tiles = [];
function rows() {
  const box = $("rows"); box.innerHTML = ""; tiles = [];
  if (!selected || selected.locked) selected = SOUNDS.find((s) => !s.locked && s.on) || SOUNDS[0];
  for (const s of SOUNDS) {
    if (s.locked) continue;
    const el = document.createElement("button"); el.className = "tile"; el.setAttribute("aria-label", s.name);
    const c = document.createElement("canvas"), b = document.createElement("b"), vol = document.createElement("small");
    b.textContent = s.name; el.append(c, b, vol); box.append(el);
    const t = { s, c, el, vol }; tiles.push(t);
    el.onclick = () => { selected = s; toggle(s); };
    el.oncontextmenu = (e) => { e.preventDefault(); selected = s; refresh(); };
  }
  refresh();
}
// Tick slider: full ticks up to the thumb, dots after it.
function tickSlider(canvas, get, set) {
  const paint = () => {
    const d = window.devicePixelRatio || 1, w = canvas.clientWidth, h = canvas.clientHeight;
    canvas.width = w * d; canvas.height = h * d;
    const x = canvas.getContext("2d"); x.setTransform(d, 0, 0, d, 0, 0); x.clearRect(0, 0, w, h);
    const left = 10, right = w - 10, cy = h / 2, thumb = left + (right - left) * get();
    x.strokeStyle = INK; x.lineCap = "round";
    for (let px = left; px <= right; px += 4.2) {
      if (px <= thumb - 8) { x.globalAlpha = 0.9; x.lineWidth = 1.4; x.beginPath(); x.moveTo(px, cy - 9); x.lineTo(px, cy + 9); x.stroke(); }
      else if (px >= thumb + 8) { x.globalAlpha = 0.35; x.lineWidth = 1.2; x.beginPath(); x.moveTo(px, cy - 1.5); x.lineTo(px, cy + 1.5); x.stroke(); }
    }
    x.globalAlpha = 1; x.fillStyle = "#111"; x.beginPath(); x.arc(thumb, cy, 8, 0, 7); x.fill();
    x.lineWidth = 1.6; x.stroke(); x.fillStyle = INK; x.beginPath(); x.arc(thumb, cy, 2.2, 0, 7); x.fill();
  };
  const at = (e) => { const r = canvas.getBoundingClientRect(); set(Math.max(0, Math.min(1, (e.clientX - r.left - 10) / (r.width - 20)))); paint(); };
  canvas.addEventListener("pointerdown", (e) => { canvas.setPointerCapture(e.pointerId); at(e); });
  canvas.addEventListener("pointermove", (e) => { if (e.buttons) at(e); });
  canvas.tabIndex = 0;
  canvas.addEventListener("keydown", (e) => { const step = e.key === "ArrowRight" ? 0.05 : e.key === "ArrowLeft" ? -0.05 : 0; if (step) { set(Math.max(0, Math.min(1, get() + step))); paint(); } });
  return paint;
}
const paintSel = tickSlider($("selSlider"), () => (selected ? selected.volume : 0), (v) => { if (!selected) return; selected.volume = v; voices.get(selected.id)?.volume(v); save(); refreshLight(); });
const paintMaster = tickSlider($("masterSlider"), () => S.master, (v) => { S.master = v; if (masterGain) masterGain.gain.setTargetAtTime(v * 1.6, ctx.currentTime, 0.05); $("masterVal").textContent = Math.round(v * 100); save(); });
function refreshLight() {
  for (const t of tiles) drawTile(t);
  if (selected) {
    $("selName").textContent = selected.name;
    const side = sideText(selected.x, selected.y), where = /^(오른쪽|왼쪽)/.test(side) ? side.replace(" ", ` ${selected.y <= 0 ? "앞" : "뒤"} `) : side;
    $("selInfo").textContent = `볼륨 ${Math.round(selected.volume * 100)}  /  ${where}`;
  }
  paintSel(); requestDraw();
}
function refresh() {
  const on = SOUNDS.filter((s) => !s.locked && s.on).length, name = presetName();
  $("title").textContent = name;
  $("status").textContent = playing ? `소리 ${on}개 재생 중` : `멈춤 · 소리 ${on}개 켜짐`;
  $("play").className = "round " + (playing ? "playing" : "paused");
  $("play").setAttribute("aria-label", playing ? "정지" : "재생");
  for (const b of document.querySelectorAll(".preset")) b.classList.toggle("active", b.textContent === name);
  refreshLight();
  $("masterVal").textContent = Math.round(S.master * 100); paintMaster();
  const left = Math.ceil(sleepLeft() / 60e3);
  $("sleep").querySelector("b").textContent = sleepMin ? `${left}분 남음` : "끔";
  $("motion").querySelector("b").textContent = ["멈춤", "행성 궤도", "360° 회전"][S.motion];
  $("dex").textContent = `소리 도감 (${Object.keys(S.regions).length + Object.keys(S.stamps).length + Object.keys(S.spots).length}/${REGIONS.length + (places ? places.length : 25) + SPOTS.length})`;
  $("darkText").textContent = "화면을 누르면 돌아와요" + (sleepMin ? `\n수면 타이머 ${left}분 남음` : "");
}
const tickClock = () => { $("clock").textContent = new Date().toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit", hour12: false }); };
tickClock(); setInterval(tickClock, 20000);
$("more").onclick = () => ($("moreSheet").hidden = false);
$("moreClose").onclick = () => ($("moreSheet").hidden = true);
$("moreSheet").onclick = (e) => { if (e.target.id === "moreSheet") $("moreSheet").hidden = true; };
for (const id of ["places", "dex", "reset", "credits"]) $(id).addEventListener("click", () => ($("moreSheet").hidden = true));
$("play").onclick = () => (playing ? stop() : play());
for (const b of document.querySelectorAll(".preset")) b.onclick = () => {
  const ids = b.dataset.preset.split(",");
  for (const s of SOUNDS) { const on = ids.includes(s.id); if (on !== s.on) { s.on = on; s.anim.pop = on ? 1 : 0; } }
  if (!playing) play(); else syncVoices();
  save(); refresh(); requestDraw();
};
$("motion").onclick = () => { S.motion = (S.motion + 1) % 3; save(); refresh(); requestDraw(); };
$("sleep").onclick = () => {
  sleepMin = SLEEP[(SLEEP.indexOf(sleepMin) + 1) % SLEEP.length];
  sleepEnd = sleepMin ? Date.now() + sleepMin * 60e3 : 0;
  if (sleepMin && !playing) play();
  applyScene(); refresh();
};
$("reset").onclick = () => { for (const s of SOUNDS) { s.x = s.hx; s.y = s.hy; } voices.forEach((v) => v.move()); save(); requestDraw(); };
let wakeLock = null;
$("dark").onclick = async () => {
  $("darkOverlay").hidden = false;
  try { wakeLock = await navigator.wakeLock?.request("screen"); } catch (e) {}
};
$("darkOverlay").onclick = () => { $("darkOverlay").hidden = true; wakeLock?.release?.(); wakeLock = null; };

// Water-drop ripple + press feel on every button
document.addEventListener("pointerdown", (e) => {
  const b = e.target.closest(".btn, .tile, .cell"); if (!b) return;
  const r = b.getBoundingClientRect(), d = Math.max(r.width, r.height), sp = document.createElement("span");
  sp.className = "drop"; sp.style.width = sp.style.height = d + "px";
  sp.style.left = e.clientX - r.left - d / 2 + "px"; sp.style.top = e.clientY - r.top - d / 2 + "px";
  b.append(sp); setTimeout(() => sp.remove(), 650); buzz(6);
});

// ── Dialogs ───────────────────────────────────────────────────────────────
function modal(title, body) { $("modalTitle").textContent = title; const m = $("modalBody"); m.innerHTML = ""; if (typeof body === "string") m.textContent = body; else m.append(body); $("modal").hidden = false; }
$("modalClose").onclick = () => ($("modal").hidden = true);
$("modal").onclick = (e) => { if (e.target.id === "modal") $("modal").hidden = true; };
$("credits").onclick = () => fetch("CREDITS.txt").then((r) => r.text()).then((t) => modal("소리 출처", t));
$("species").onclick = () => {
  const f = document.createElement("div");
  const add = (label, checked, on) => { const l = document.createElement("label"), c = document.createElement("input"); c.type = "checkbox"; c.checked = checked; c.onchange = () => { on(c.checked); save(); }; l.append(c, label); f.append(l); };
  add("시간대에 맞춰 자동 (밤엔 밤 새만)", S.birdAuto, (v) => (S.birdAuto = v));
  for (const b of BIRDS) add(b.name + (b.night ? " (밤)" : ""), S.birdsOn[b.id], (v) => (S.birdsOn[b.id] = v));
  modal("새 종류", f);
};
$("dex").onclick = () => {
  const day = (t) => new Date(t).toLocaleDateString("ko-KR", { month: "long", day: "numeric" });
  const sn = (id) => SOUNDS.find((x) => x.id === id).name;
  const L = [`── 지역 소리 (${Object.keys(S.regions).length}/${REGIONS.length}) ──`];
  for (const g of REGIONS) L.push(`${S.regions[g.id] ? "🔓" : "🔒"} ${g.name} · ${sn(g.sound)}${S.regions[g.id] ? " · " + day(S.regions[g.id].at) : ""}`);
  L.push("", `── 숨은 명소 도장 (${Object.keys(S.stamps).length}/${places ? places.length : 25}) ──`);
  const st = Object.keys(S.stamps).sort();
  L.push(...(st.length ? st.map((n) => "◉ " + n) : ["아직 찾은 숨은 명소가 없어요."]));
  L.push(`공항, 기차, 도서관, 야구장처럼 이름난 곳 ${places ? places.length : 25}곳이 지도에 안 보이게 숨어 있어요. 도서관과 야구장 소리는 숨은 명소에서만 열려요.`);
  const now = seasonOf(new Date().getMonth() + 1);
  L.push("", `── 계절 명소 (지금은 ${SEASONS[now]}) ──`);
  for (const sp of SPOTS) {
    const s = SOUNDS.find((x) => x.spot === sp.id), u = S.spots[sp.id];
    L.push(u ? `🔓 ${s.name} · ${sp.place} · ${day(u.at)}` : `${sp.season === now ? "◆" : "🔒"} ${s.name} · ${SEASONS[sp.season]}에 ${sp.place}에 가면 열려요`);
  }
  modal("소리 도감", L.join("\n") + "\n\n" + (geoOk ? "위치는 이 페이지가 열려 있을 때만 확인해요." : "위치 권한이 없어서 소리가 열리지 않아요. 브라우저에서 위치를 허용해 주세요."));
};
window.toast = toast;
function toast(msg) { const t = $("toast"); t.textContent = msg; t.hidden = false; clearTimeout(toast.t); toast.t = setTimeout(() => (t.hidden = true), 4000); }

// ── Location unlocks (offline place list, no map API) ─────────────────────
let places = null, geoOk = false, here = null, lastCheck = { lat: NaN, lon: NaN, t: 0 };
fetch("places.json").then((r) => r.json()).then((p) => { places = p; if (here) check(here.lat, here.lon, true); });
function dist(lat1, lon1, lat2, lon2) { const dLat = lat2 - lat1, dLon = (lon2 - lon1) * Math.cos((lat1 * Math.PI) / 180); return Math.hypot(dLat, dLon) * 111195; }
function bearing(lat1, lon1, lat2, lon2) { const dLat = lat2 - lat1, dLon = (lon2 - lon1) * Math.cos((lat1 * Math.PI) / 180); return ((Math.atan2(dLon, dLat) * 180) / Math.PI + 360) % 360; }
function check(lat, lon, force) {
  if (!places) return;
  const moved = isNaN(lastCheck.lat) || dist(lat, lon, lastCheck.lat, lastCheck.lon) > 25;
  if (!force && !moved && Date.now() - lastCheck.t < 15e3) return;
  lastCheck = { lat, lon, t: Date.now() };
  const best = {}, msgs = [];
  const toast = (m) => msgs.push(m); // several unlocks at once → one combined message
  // 1) Region sounds
  for (const g of REGIONS) {
    if (S.regions[g.id] || regionDist(g, lat, lon) > g.r) continue;
    S.regions[g.id] = { at: Date.now() }; best["region:" + g.id] = 1;
    const s = SOUNDS.find((x) => x.id === g.sound), was = s.locked; s.locked = false;
    const cat = CATS.find((c) => SOUNDS.find((x) => x.unlock === c.id)?.id === g.sound);
    if (cat && !S.unlocked[cat.id]) S.unlocked[cat.id] = { place: g.name, at: Date.now() };
    toast(was ? `🔓 ${g.name}에서 '${s.name}' 소리가 열렸어요` : `◉ ${g.name} 도장을 찍었어요`); buzz([20, 60, 20]);
  }
  // 2) Hidden landmarks: a stamp each, opening the category sound if needed
  for (const [c, la, lo, name, rad] of places) {
    if (S.stamps[name]) continue;
    const cat = CATS.find((x) => x.id === c), r = rad || cat.r;
    if (dist(lat, lon, la / 1e5, lo / 1e5) > r) continue;
    S.stamps[name] = Date.now(); best["stamp:" + name] = 1;
    const s = SOUNDS.find((x) => x.unlock === c);
    if (!S.unlocked[c]) { S.unlocked[c] = { place: name, at: Date.now() }; s.locked = false; toast(`🔓 숨은 명소 ${name}에서 '${s.name}' 소리가 열렸어요`); }
    else toast(`◉ 숨은 명소 ${name} 도장을 찍었어요`);
    buzz([20, 60, 20]);
  }
  // Seasonal landmarks
  const month = new Date().getMonth() + 1, now = seasonOf(month), today = new Date().toISOString().slice(0, 10);
  let changed = Object.keys(best).length > 0;
  for (const sp of SPOTS) {
    if (S.spots[sp.id]) continue;
    const d = Math.min(...sp.pts.map(([a, b]) => dist(lat, lon, a, b)));
    if (d > sp.r) continue;
    const s = SOUNDS.find((x) => x.spot === sp.id);
    if (sp.season === now) {
      S.spots[sp.id] = { place: sp.place, at: Date.now() }; s.locked = false; changed = true;
      toast(`🔓 ${sp.place}에서 '${s.name}' 소리가 열렸어요`); buzz([20, 60, 20]);
    } else if (S.spotNote[sp.id] !== today) {
      S.spotNote[sp.id] = today; save();
      toast(`${sp.place}${topic(sp.place)} ${SEASONS[sp.season]}에 다시 오면 '${s.name}' 소리가 열려요`);
    }
  }
  if (msgs.length) window.toast(msgs.join("\n"));
  if (changed) { save(); rows(); requestDraw(); }
}
// Korean topic particle: 은 after a final consonant, 는 otherwise ("해변은", "대관령은", "해운대는").
function topic(w) { const c = w.charCodeAt(w.length - 1); return c >= 0xac00 && c <= 0xd7a3 && (c - 0xac00) % 28 ? "은" : "는"; }
function nearestSpot(me) {
  const now = seasonOf(new Date().getMonth() + 1);
  let best = null;
  for (const sp of SPOTS) {
    if (S.spots[sp.id] || sp.season !== now) continue;
    for (const [a, b] of sp.pts) { const d = dist(me.lat, me.lon, a, b); if (!best || d < best.d) best = { sp, d, b: bearing(me.lat, me.lon, a, b) }; }
  }
  return best;
}
if (navigator.geolocation) {
  navigator.geolocation.watchPosition((p) => {
    geoOk = true; here = { lat: p.coords.latitude, lon: p.coords.longitude };
    check(here.lat, here.lon); if (!$("placesView").hidden) drawPlaces();
  }, () => { geoOk = false; }, { enableHighAccuracy: true, maximumAge: 10000 });
}

// ── Places view: radar + map ─────────────────────────────────────────────
let showAll = false, mapObj = null, radarPts = [];
// Regions are far apart, so the radar uses a log distance scale: 100 m at the centre … 300 km at the edge.
const RANGE = 300000, FALLBACK = { lat: 37.5665, lon: 126.978 };
const radarScale = (d) => (d <= 100 ? 0 : Math.min(1, Math.log10(d / 100) / Math.log10(RANGE / 100)));
const rc = $("radar"), rg = rc.getContext("2d");
// Radar/map show regions (and seasonal spots on the map). Hidden landmarks are never shown.
function nearPlaces(me) {
  return REGIONS.filter((g) => showAll || !S.regions[g.id]).map((g) => {
    const [la, lo] = regionPoint(g, me.lat, me.lon);
    return { g, name: g.name, d: regionDist(g, me.lat, me.lon), b: bearing(me.lat, me.lon, la, lo), lat: la, lon: lo, r: g.r };
  }).filter((p) => p.d <= RANGE).sort((a, b) => a.d - b.d).slice(0, 12);
}
function drawPlaces() {
  $("placesFilter").textContent = showAll ? "전부 보기" : "안 열린 것만";
  const me = here || FALLBACK, r = rc.getBoundingClientRect(), dpr = window.devicePixelRatio || 1;
  rc.width = r.width * dpr; rc.height = r.height * dpr; rg.setTransform(dpr, 0, 0, dpr, 0, 0);
  const cx = r.width / 2, cy = r.height / 2, rad = Math.min(r.width, r.height) / 2 - 30;
  rg.fillStyle = "#0b0f17"; rg.fillRect(0, 0, r.width, r.height);
  rg.strokeStyle = "rgba(255,255,255,.2)"; rg.fillStyle = "rgba(255,255,255,.6)"; rg.font = "12px sans-serif"; rg.textAlign = "center";
  for (const m of [1000, 10000, 100000, RANGE]) { const rr = radarScale(m) * rad; rg.beginPath(); rg.arc(cx, cy, rr, 0, 7); rg.stroke(); rg.fillText(m / 1000 + "km", cx + rr * 0.71 + 16, cy - rr * 0.71); }
  rg.font = "15px sans-serif"; rg.fillText("N", cx, cy - rad - 10);
  radarPts = [];
  const list = nearPlaces(me);
  for (const p of list.slice().reverse()) {
    const cat = { color: SOUNDS.find((x) => x.id === p.g.sound).color, emoji: p.g.emoji }, rr = radarScale(p.d) * rad, a = (p.b * Math.PI) / 180;
    const x = cx + Math.sin(a) * rr, y = cy - Math.cos(a) * rr;
    rg.globalAlpha = 0.25; rg.fillStyle = cat.color; rg.beginPath(); rg.arc(x, y, 16, 0, 7); rg.fill();
    rg.globalAlpha = 1; rg.font = "17px sans-serif"; rg.fillText(cat.emoji, x, y + 6);
    radarPts.push({ x, y, p });
  }
  rg.fillStyle = "#3D7BFF"; rg.beginPath(); rg.arc(cx, cy, 8, 0, 7); rg.fill();
  if (!here) { rg.fillStyle = "rgba(255,255,255,.6)"; rg.fillText("위치를 못 받아서 서울시청 기준이에요", cx, cy + rad * 0.55); }
  if ($("placeInfo").dataset.tapped !== "1") {
    const ns = nearestSpot(me), now = SEASONS[seasonOf(new Date().getMonth() + 1)];
    $("placeInfo").textContent = "점을 누르면 어떤 소리가 열리는지 보여 줘요.\n" + (ns
      ? `이번 ${now} 명소: ${ns.sp.place} · ${["북", "북동", "동", "남동", "남", "남서", "서", "북서"][Math.round(ns.b / 45) % 8]}쪽 ${Math.round(ns.d / 1000)}km (${SOUNDS.find((x) => x.spot === ns.sp.id).name})`
      : `이번 ${now} 명소는 모두 열었어요`);
  }
  if (mapObj && mapObj.getSource("places")) updateMap();
}
function info(p) {
  const g = p.g || REGIONS.find((x) => x.id === p.c);
  const cat = { emoji: g.emoji, r: g.r }, s = SOUNDS.find((x) => x.id === g.sound);
  p.c = g.id;
  const dd = p.d >= 1000 ? (p.d / 1000).toFixed(1) + "km" : Math.round(p.d) + "m";
  const dir = ["북", "북동", "동", "남동", "남", "남서", "서", "북서"][Math.round(p.b / 45) % 8];
  $("placeInfo").dataset.tapped = "1";
  $("placeInfo").textContent = `${cat.emoji} ${p.name} · ${dir}쪽 ${dd}\n` + (S.regions[g.id] ? `이미 열린 소리: ${s.name}` : `가면 열리는 소리: ${s.name} (${(p.r || cat.r) >= 1000 ? (p.r || cat.r) / 1000 + "km" : (p.r || cat.r) + "m"} 안으로)`);
}
rc.addEventListener("pointerup", (e) => {
  const r = rc.getBoundingClientRect(), x = e.clientX - r.left, y = e.clientY - r.top;
  let best = null, bd = 30; for (const q of radarPts) { const d = Math.hypot(q.x - x, q.y - y); if (d < bd) { bd = d; best = q.p; } }
  if (best) info(best);
});
function geojson() {
  const now = seasonOf(new Date().getMonth() + 1), feats = [];
  for (const g of REGIONS) {
    if (!showAll && S.regions[g.id]) continue;
    for (const [la, lo] of g.pts) feats.push({ type: "Feature", geometry: { type: "Point", coordinates: [lo, la] },
      properties: { cat: g.id, n: g.name, c: SOUNDS.find((x) => x.id === g.sound).color } });
  }
  for (const sp of SPOTS) {
    if (!showAll && (S.spots[sp.id] || sp.season !== now)) continue;
    for (const [la, lo] of sp.pts) feats.push({ type: "Feature", geometry: { type: "Point", coordinates: [lo, la] },
      properties: { cat: "spot:" + sp.id, n: `${sp.place} (${SEASONS[sp.season]})`, c: "#FFD27A" } });
  }
  return { type: "FeatureCollection", features: feats };
}
function updateMap() {
  mapObj.getSource("places").setData(geojson());
  const me = here || FALLBACK;
  mapObj.getSource("me").setData({ type: "FeatureCollection", features: here ? [{ type: "Feature", geometry: { type: "Point", coordinates: [me.lon, me.lat] }, properties: {} }] : [] });
}
function loadScript(src) { return new Promise((ok, no) => { const s = document.createElement("script"); s.src = src; s.onload = ok; s.onerror = no; document.head.append(s); }); }
async function openMap() {
  $("radar").hidden = true; $("map").hidden = false;
  $("tabMap").classList.add("active"); $("tabRadar").classList.remove("active");
  if (mapObj) { mapObj.resize(); return; }
  const css = document.createElement("link"); css.rel = "stylesheet"; css.href = "https://unpkg.com/maplibre-gl@4.7.1/dist/maplibre-gl.css"; document.head.append(css);
  try { await loadScript("https://unpkg.com/maplibre-gl@4.7.1/dist/maplibre-gl.js"); } catch (e) { $("placeInfo").textContent = "지도를 불러오지 못했어요. 인터넷 연결을 확인해 주세요."; return; }
  const me = here || FALLBACK;
  mapObj = new maplibregl.Map({ container: "map", style: "https://tiles.openfreemap.org/styles/liberty", center: [me.lon, me.lat], zoom: 7, dragRotate: false });
  mapObj.on("load", () => {
    mapObj.addSource("places", { type: "geojson", data: geojson() });
    mapObj.addSource("me", { type: "geojson", data: { type: "FeatureCollection", features: [] } });
    mapObj.addLayer({ id: "dots", type: "circle", source: "places", paint: { "circle-color": ["get", "c"], "circle-radius": 6, "circle-stroke-color": "#fff", "circle-stroke-width": 1.5 } });
    mapObj.addLayer({ id: "labels", type: "symbol", source: "places", minzoom: 6, layout: { "text-field": ["get", "n"], "text-size": 13, "text-offset": [0, 1.3], "text-optional": true }, paint: { "text-color": "#000", "text-halo-color": "#fff", "text-halo-width": 1.5 } });
    mapObj.addLayer({ id: "me", type: "circle", source: "me", paint: { "circle-color": "#3D7BFF", "circle-radius": 8, "circle-stroke-color": "#fff", "circle-stroke-width": 2.5 } });
    updateMap();
    mapObj.on("click", "dots", (e) => {
      const f = e.features[0], [lo, la] = f.geometry.coordinates, m = here || FALLBACK;
      if (f.properties.cat.startsWith("spot:")) {
        const sp = SPOTS.find((x) => x.id === f.properties.cat.slice(5)), s = SOUNDS.find((x) => x.spot === sp.id);
        const d = dist(m.lat, m.lon, la, lo), dir = ["북", "북동", "동", "남동", "남", "남서", "서", "북서"][Math.round(bearing(m.lat, m.lon, la, lo) / 45) % 8];
        $("placeInfo").dataset.tapped = "1";
        $("placeInfo").textContent = `${sp.place} · ${dir}쪽 ${Math.round(d / 1000)}km\n${S.spots[sp.id] ? "이미 열린 소리" : SEASONS[sp.season] + "에 가면 열리는 소리"}: ${s.name}`;
        return;
      }
      info({ c: f.properties.cat, name: f.properties.n, d: dist(m.lat, m.lon, la, lo), b: bearing(m.lat, m.lon, la, lo) });
    });
  });
}
function openRadar() { $("map").hidden = true; $("radar").hidden = false; $("tabRadar").classList.add("active"); $("tabMap").classList.remove("active"); drawPlaces(); }
$("places").onclick = () => { $("placesView").hidden = false; $("placeInfo").dataset.tapped = ""; openRadar(); };
$("placesClose").onclick = () => ($("placesView").hidden = true);
$("tabRadar").onclick = openRadar;
$("tabMap").onclick = openMap;
$("placesFilter").onclick = () => { showAll = !showAll; drawPlaces(); };

// ── Start ─────────────────────────────────────────────────────────────────
rows(); resize();
