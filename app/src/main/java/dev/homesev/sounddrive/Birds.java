package dev.homesev.sounddrive;

/**
 * "새": a flock of many bird species calling from different spots around the dot.
 * Each call gets its own position (scattered around the dot), so it sounds like
 * several birds in a forest rather than one bird in one place.
 * Calls are real recordings (see assets/CREDITS.txt); a synthesized imitation is
 * used only for a species whose recording isn't loaded.
 */
final class Birds extends SoundSource {

    /** One bird species: Korean name, whether it calls at night, and its call recipe. */
    static final class Species {
        final String id, name;
        final boolean night;
        final float minGapSec; // the same species won't call again sooner than this
        Species(String id, String name, boolean night, float minGapSec) {
            this.id = id; this.name = name; this.night = night; this.minGapSec = minGapSec;
        }
    }

    static final Species[] ALL = {
        new Species("dove", "멧비둘기", false, 8),
        new Species("sparrow", "참새", false, 2),
        new Species("magpie", "까치", false, 6),
        new Species("tit", "박새", false, 4),
        new Species("bulbul", "직박구리", false, 5),
        new Species("oriole", "꾀꼬리", false, 7),
        new Species("warbler", "휘파람새", false, 8),
        new Species("woodpecker", "딱따구리", false, 10),
        new Species("cuckoo", "뻐꾸기", false, 14),
        new Species("owl", "부엉이", true, 10),
        new Species("scops", "소쩍새", true, 12),
        new Species("nightjar", "쏙독새", true, 14),
    };

    /** Which species are allowed (UI writes, audio thread reads). */
    final boolean[] speciesOn = new boolean[ALL.length];
    /** When true, only night birds at night and day birds by day. */
    volatile boolean autoTime = true;
    /** Set from the clock by the player (true roughly 19:00–05:00). */
    volatile boolean night;

    private static final int MAX_VOICES = 5;
    private final Voice[] voices = new Voice[MAX_VOICES];
    private final double[] lastCall = new double[ALL.length];
    private int waitSamples;
    private double clock; // seconds of audio rendered
    private float[] tmpL = new float[0], tmpR = new float[0], tmpMono = new float[0];
    private final Spatial.Target vt = new Spatial.Target();

    Birds() {
        super("birds", "새", 0xFF9BD45A, 0.4f, 0.45f, 53);
        java.util.Arrays.fill(speciesOn, true);
        java.util.Arrays.fill(lastCall, -1e9);
        for (int i = 0; i < MAX_VOICES; i++) voices[i] = new Voice();
    }

    /** Recorded call clips per species: samples + [start, end) pairs. */
    private final java.util.Map<String, float[]> clipData = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, int[]> clipIndex = new java.util.concurrent.ConcurrentHashMap<>();

    void setClips(String species, float[] data, int[] startEnd) {
        if (data == null || startEnd == null || startEnd.length < 2) return;
        clipIndex.put(species, startEnd);
        clipData.put(species, data);
    }

    boolean hasClips(String species) { return clipData.containsKey(species); }

    /** Something a voice can play: a synthesized call or a recorded clip. */
    interface Playable {
        String species();
        /** Fill out[0..n); returns false once finished. */
        boolean render(float[] out, int n, float sr);
    }

    /** One recorded clip, with slight random pitch/level so repeats don't sound identical. */
    static final class Clip implements Playable {
        final String species;
        final float[] data;
        final int end;
        final double rate;
        final float gain;
        double pos;
        Clip(String species, float[] data, int start, int end, double rate, float gain) {
            this.species = species; this.data = data; this.pos = start; this.end = Math.min(end, data.length - 1);
            this.rate = rate; this.gain = gain;
        }
        @Override public String species() { return species; }
        @Override public boolean render(float[] out, int n, float sr) {
            for (int i = 0; i < n; i++) {
                if (pos >= end) { out[i] = 0; continue; }
                int a = (int) pos;
                float f = (float) (pos - a);
                out[i] = (data[a] + (data[a + 1] - data[a]) * f) * gain;
                pos += rate;
            }
            return pos < end;
        }
    }

    private static final class Voice {
        Playable call;
        float ox, oy; // offset from the dot: the bird moves when the dot is dragged or orbits
        final Spatial.State st = new Spatial.State();
    }

    // ── Rendering ──────────────────────────────────────────────────────────
    @Override void render(float[] out, int n, float sr) {
        if (tmpL.length < n) { tmpL = new float[n]; tmpR = new float[n]; tmpMono = new float[n]; }
        java.util.Arrays.fill(tmpL, 0, n, 0f);
        java.util.Arrays.fill(tmpR, 0, n, 0f);
        renderInto(tmpL, tmpR, n, sr, 1f, 1f, vt, tmpMono);
        for (int i = 0; i < n; i++) out[i] = (tmpL[i] + tmpR[i]) * 0.7f;
    }

    @Override void renderInto(float[] l, float[] r, int n, float sr, float fadeFrom, float fadeTo,
                              Spatial.Target tg, float[] mono) {
        waitSamples -= n;
        if (waitSamples <= 0) {
            spawn(sr);
            waitSamples = (int) (sr * rng.range(1.2f, 4.5f));
        }
        for (Voice v : voices) {
            if (v.call == null) continue;
            boolean alive = v.call.render(mono, n, sr);
            for (int i = 0; i < n; i++) mono[i] *= fadeFrom + (fadeTo - fadeFrom) * i / n;
            float px = x + v.ox, py = y + v.oy;
            float len = (float) Math.hypot(px, py);
            if (len > 1f) { px /= len; py /= len; }
            Spatial.target(px, py, sr, tg);
            Spatial.process(v.st, tg, volume, mono, l, r, n);
            if (!alive) v.call = null;
        }
        clock += n / sr;
    }

    private void spawn(float sr) {
        Voice free = null;
        for (Voice v : voices) if (v.call == null) { free = v; break; }
        if (free == null) return;
        int pick = pickSpecies();
        if (pick < 0) return;
        lastCall[pick] = clock;
        free.call = makePlayable(ALL[pick].id);
        // Scatter around the dot: 0.15–0.5 away in a random direction, kept inside the stage.
        double a = rng.next() * Math.PI * 2, d = rng.range(0.15f, 0.5f);
        free.ox = (float) (Math.cos(a) * d);
        free.oy = (float) (Math.sin(a) * d);
        free.st.primed = false;
        java.util.Arrays.fill(free.st.ring, 0f);
        free.st.lpL = free.st.lpR = 0;
    }

    private int pickSpecies() {
        int[] cand = new int[ALL.length];
        // Respect the time of day unless none of the selected species fits it at all
        // (e.g. only day birds selected at night) — then ignore the time of day.
        boolean anyFits = false;
        for (int i = 0; i < ALL.length; i++) if (speciesOn[i] && (!autoTime || ALL[i].night == night)) anyFits = true;
        int n = collect(cand, anyFits);
        if (n == 0) return -1;
        return cand[(int) (rng.next() * n)];
    }

    private int collect(int[] cand, boolean useTime) {
        int n = 0;
        for (int i = 0; i < ALL.length; i++) {
            if (!speciesOn[i]) continue;
            if (useTime && autoTime && ALL[i].night != night) continue;
            if (clock - lastCall[i] < ALL[i].minGapSec) continue;
            boolean busy = false;
            for (Voice v : voices) if (v.call != null && v.call.species().equals(ALL[i].id)) busy = true;
            if (!busy) cand[n++] = i;
        }
        return n;
    }

    private Playable makePlayable(String id) {
        float[] data = clipData.get(id);
        int[] idx = clipIndex.get(id);
        if (data != null && idx != null) {
            int k = (int) (rng.next() * (idx.length / 2)) * 2;
            return new Clip(id, data, idx[k], idx[k + 1], rng.range(0.97f, 1.03f), rng.range(0.7f, 1f));
        }
        return make(id, rng);
    }

    // ── Call synthesis (fallback) ──────────────────────────────────────────
    /** One note: a tone gliding f0→f1 with an envelope, optional harmonics, breath noise, harshness. */
    static final class Note {
        double t0, dur, f0, f1, amp, h2, h3, noise, harsh, vibHz, vibDepth, att = 0.01, rel = 0.03, decay;
        Note(double t0, double dur, double f0, double f1, double amp) {
            this.t0 = t0; this.dur = dur; this.f0 = f0; this.f1 = f1; this.amp = amp;
        }
        Note h(double h2, double h3) { this.h2 = h2; this.h3 = h3; return this; }
        Note env(double att, double rel) { this.att = att; this.rel = rel; return this; }
        Note breath(double noise) { this.noise = noise; return this; }
        Note harsh(double harsh) { this.harsh = harsh; return this; }
        Note vib(double hz, double depth) { this.vibHz = hz; this.vibDepth = depth; return this; }
        /** Knock: exponential decay instead of a held note. */
        Note knock(double decay) { this.decay = decay; return this; }
    }

    /** A call = a list of notes rendered together, plus a gentle low-pass for softness. */
    static final class Call implements Playable {
        final String species;
        final Note[] notes;
        final double[] ph;
        final double end;
        final float lpHz;
        final Noise rnd;
        double t;
        float lp, nlp;
        Call(String species, java.util.List<Note> list, float lpHz, Noise rnd) {
            this.species = species;
            this.notes = list.toArray(new Note[0]);
            this.ph = new double[notes.length];
            double e = 0;
            for (Note n : notes) e = Math.max(e, n.t0 + n.dur + (n.decay > 0 ? n.decay * 6 : 0));
            this.end = e + 0.05;
            this.lpHz = lpHz;
            this.rnd = rnd;
        }
        @Override public String species() { return species; }
        /** Fill out[0..n); returns false once the call has finished. */
        @Override public boolean render(float[] out, int n, float sr) {
            float a = Noise.lp(lpHz, sr), an = Noise.lp(3000, sr);
            double dt = 1.0 / sr;
            for (int i = 0; i < n; i++) {
                double s = 0;
                float w = rnd.white();
                nlp += an * (w - nlp);
                for (int k = 0; k < notes.length; k++) {
                    Note no = notes[k];
                    double u = t - no.t0;
                    double len = no.decay > 0 ? no.decay * 6 : no.dur;
                    if (u < 0 || u > len) continue;
                    double p = Math.min(1, u / no.dur);
                    double f = no.f0 + (no.f1 - no.f0) * p;
                    if (no.vibHz > 0) f += no.vibDepth * Math.sin(Sources.TAU * no.vibHz * u);
                    ph[k] += Sources.TAU * f * dt;
                    double env;
                    if (no.decay > 0) env = Math.min(1, u / 0.0008) * Math.exp(-u / no.decay);
                    else {
                        env = Math.min(1, u / no.att) * Math.min(1, (no.dur - u) / no.rel);
                        env = env * env * (3 - 2 * env); // smoothstep
                    }
                    double tone = Math.sin(ph[k]) + no.h2 * Math.sin(2 * ph[k]) + no.h3 * Math.sin(3 * ph[k]);
                    if (no.harsh > 0) tone *= 1 + no.harsh * w;
                    s += no.amp * env * (tone + no.noise * nlp * 3);
                }
                lp += a * ((float) s - lp);
                out[i] = lp;
                t += dt;
            }
            return t < end;
        }
    }

    static Call make(String id, Noise r) {
        java.util.List<Note> ns = new java.util.ArrayList<>();
        float lpHz = 9000;
        switch (id) {
            case "dove": { // 멧비둘기: "구구- 구구-" low breathy coos, phrase repeated
                double f = r.range(470, 540);
                int reps = 2 + (int) (r.next() * 2);
                for (int k = 0; k < reps; k++) {
                    double o = k * 2.0;
                    ns.add(new Note(o, 0.30, f * 1.02, f * 0.95, 0.5).h(0.25, 0.05).breath(0.06).env(0.05, 0.1));
                    ns.add(new Note(o + 0.42, 0.22, f, f * 0.96, 0.45).h(0.25, 0.05).breath(0.06).env(0.04, 0.08));
                    ns.add(new Note(o + 0.72, 0.45, f * 1.05, f * 0.9, 0.5).h(0.25, 0.05).breath(0.06).env(0.06, 0.15));
                    ns.add(new Note(o + 1.32, 0.28, f, f * 0.94, 0.4).h(0.25, 0.05).breath(0.06).env(0.04, 0.1));
                }
                lpHz = 2500;
                break;
            }
            case "sparrow": { // 참새: irregular short "짹" chirps
                int k = 3 + (int) (r.next() * 6);
                double t = 0;
                for (int i = 0; i < k; i++) {
                    double f = r.range(4000, 5300);
                    ns.add(new Note(t, r.range(0.05f, 0.09f), f, f - r.range(800, 1500), 0.32).h(0.3, 0.1).env(0.005, 0.02));
                    t += r.range(0.12f, 0.32f);
                }
                break;
            }
            case "magpie": { // 까치: harsh "깍깍깍" chatter
                int k = 4 + (int) (r.next() * 4);
                double f = r.range(1250, 1600);
                for (int i = 0; i < k; i++)
                    ns.add(new Note(i * 0.17, 0.11, f, f * 0.93, 0.33).h(0.5, 0.35).harsh(0.9).env(0.008, 0.03));
                lpHz = 6000;
                break;
            }
            case "tit": { // 박새: "쯔삐 쯔삐" alternating high-low pair
                int k = 4 + (int) (r.next() * 3);
                double hi = r.range(4900, 5600), lo = r.range(3500, 4000);
                for (int i = 0; i < k; i++) {
                    double o = i * 0.3;
                    ns.add(new Note(o, 0.07, hi, hi * 0.97, 0.3).h(0.1, 0).env(0.006, 0.02));
                    ns.add(new Note(o + 0.11, 0.1, lo, lo * 1.02, 0.3).h(0.1, 0).env(0.006, 0.025));
                }
                break;
            }
            case "bulbul": { // 직박구리: loud shrill "삐이- 삐요"
                int k = 2 + (int) (r.next() * 3);
                double t = 0;
                for (int i = 0; i < k; i++) {
                    boolean up = i % 2 == 0;
                    double d = r.range(0.22f, 0.4f);
                    ns.add(new Note(t, d, up ? 2600 : 3800, up ? 3700 : 2700, 0.33).h(0.2, 0.05).harsh(0.15).vib(30, 60).env(0.02, 0.05));
                    t += d + r.range(0.05f, 0.15f);
                }
                break;
            }
            case "oriole": { // 꾀꼬리: fluty melodic glides
                int k = 4 + (int) (r.next() * 3);
                double t = 0, f = r.range(1500, 2200);
                for (int i = 0; i < k; i++) {
                    double next = Math.max(1300, Math.min(2800, f + r.range(-600, 600)));
                    double d = r.range(0.14f, 0.3f);
                    ns.add(new Note(t, d, f, next, 0.33).h(0.05, 0).vib(6, 20).env(0.03, 0.05));
                    f = next;
                    t += d + r.range(0.03f, 0.08f);
                }
                lpHz = 5000;
                break;
            }
            case "warbler": { // 휘파람새: long rising whistle then quick "호케꾜"
                double f = r.range(1100, 1250);
                ns.add(new Note(0, 1.0, f, f * 1.08, 0.32).h(0.05, 0).env(0.45, 0.08));
                ns.add(new Note(1.18, 0.12, 2450, 2200, 0.3).h(0.05, 0).env(0.01, 0.03));
                ns.add(new Note(1.34, 0.1, 1800, 2050, 0.3).h(0.05, 0).env(0.01, 0.03));
                ns.add(new Note(1.48, 0.3, 2850, 2450, 0.32).h(0.05, 0).env(0.015, 0.08));
                lpHz = 6000;
                break;
            }
            case "woodpecker": { // 딱따구리: drumming on wood, slight speed-up
                int k = 12 + (int) (r.next() * 12);
                double f = r.range(850, 1300), t = 0, step = 0.06;
                for (int i = 0; i < k; i++) {
                    ns.add(new Note(t, 0.004, f, f * 0.9, 0.5).knock(0.011).breath(0.5));
                    t += step;
                    step = Math.max(0.045, step * 0.985);
                }
                lpHz = 4000;
                break;
            }
            case "cuckoo": { // 뻐꾸기: "뻐-꾹" falling pair, repeated
                int k = 3 + (int) (r.next() * 4);
                double f = r.range(650, 720);
                for (int i = 0; i < k; i++) {
                    double o = i * 1.0;
                    ns.add(new Note(o, 0.22, f, f * 0.97, 0.45).h(0.12, 0).env(0.02, 0.08));
                    ns.add(new Note(o + 0.32, 0.32, f * 0.8, f * 0.78, 0.45).h(0.12, 0).env(0.02, 0.12));
                }
                lpHz = 3000;
                break;
            }
            case "owl": { // 부엉이: deep "부-엉"
                double f = r.range(300, 345);
                ns.add(new Note(0, 0.3, f, f * 0.96, 0.6).h(0.15, 0).breath(0.08).env(0.06, 0.12));
                ns.add(new Note(0.55, 0.6, f * 0.97, f * 0.88, 0.6).h(0.15, 0).breath(0.08).env(0.08, 0.2));
                lpHz = 1500;
                break;
            }
            case "scops": { // 소쩍새: "소쩍- 소쩍" repeated
                int k = 4 + (int) (r.next() * 5);
                double f = r.range(1000, 1120);
                for (int i = 0; i < k; i++) {
                    double o = i * 2.0;
                    ns.add(new Note(o, 0.1, f, f * 1.03, 0.4).h(0.1, 0).env(0.015, 0.03));
                    ns.add(new Note(o + 0.24, 0.17, f * 1.1, f * 0.98, 0.42).h(0.1, 0).env(0.015, 0.06));
                }
                lpHz = 3000;
                break;
            }
            case "nightjar": { // 쏙독새: rapid "쏙쏙쏙…"
                int k = 15 + (int) (r.next() * 20);
                double f = r.range(1400, 1650);
                for (int i = 0; i < k; i++)
                    ns.add(new Note(i * 0.2, 0.06, f, f * 0.84, 0.33).h(0.3, 0.1).env(0.003, 0.02));
                lpHz = 4000;
                break;
            }
            default:
                throw new IllegalArgumentException(id);
        }
        return new Call(id, ns, lpHz, r);
    }
}
