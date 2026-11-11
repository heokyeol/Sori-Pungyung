package dev.homesev.sounddrive;

/**
 * Procedurally synthesized ambient sounds. Everything is generated in code,
 * so the app ships no third-party audio files.
 */
final class Sources {
    private Sources() {}

    static final double TAU = Math.PI * 2;

    static SoundSource[] all() {
        return new SoundSource[] {
            new Rain(), new Waves(), new Wind(), new Fire(), new Birds(), new Pad(),
            new Recorded("thunder", "천둥번개", 0xFFFFE066, 0.5f, -0.7f, false),
            new Recorded("cmb", "우주배경복사", 0xFFFF9EC7, 0.0f, -0.92f, false),
            new Recorded("saturn", "토성", 0xFFE3C08D, -0.85f, -0.35f, false),
            new Recorded("jupiter", "목성", 0xFFD99A6C, 0.85f, -0.35f, false),
        };
    }

    /** Sounds unlocked by visiting places (see {@link Unlocks}); hidden until then. */
    static SoundSource[] unlockables() {
        return new SoundSource[] {
            new Recorded("u_airport", "비행기 기내", 0xFF8FB8FF, -0.6f, 0.1f, true),
            new Recorded("u_library", "도서관", 0xFFD9B98C, 0.6f, 0.1f, true),
            new Recorded("u_temple", "사찰 풍경", 0xFFE8D27A, -0.25f, -0.75f, true),
            new Recorded("u_station", "기차 안", 0xFF9AA7B5, 0.25f, -0.75f, true),
            new Recorded("u_valley", "계곡물", 0xFF6FD3E0, -0.7f, 0.5f, true),
            new Recorded("u_harbor", "항구 갈매기", 0xFF7FB0D8, 0.7f, 0.5f, true),
            new Recorded("u_cafe", "카페", 0xFFC79A7A, -0.2f, 0.8f, true),
            new Recorded("u_baseball", "야구장", 0xFFFF8A80, 0.2f, 0.8f, true),
            // Seasonal landmarks (see Seasons)
            new Recorded("s_jinhae", "벚꽃길", 0xFFF7B5C8, -0.85f, 0.2f, true),
            new Recorded("s_boseong", "차밭 봄바람", 0xFF8FD19E, -0.75f, 0.45f, true),
            new Recorded("s_gasiri", "봄 들판 종다리", 0xFFF2D96B, -0.55f, 0.7f, true),
            new Recorded("s_yangyang", "서핑", 0xFF4FC3D9, 0.85f, 0.2f, true),
            new Recorded("s_haeundae", "한여름 해변", 0xFFFFB36B, 0.75f, 0.45f, true),
            new Recorded("s_damyang", "대숲 바람", 0xFF6FBF73, 0.55f, 0.7f, true),
            new Recorded("s_maisan", "낙엽 밟기", 0xFFD9893B, -0.85f, -0.2f, true),
            new Recorded("s_naejangsan", "단풍숲 바람", 0xFFC8553D, -0.75f, -0.45f, true),
            new Recorded("s_mindungsan", "억새 바람", 0xFFC9B48A, -0.55f, -0.7f, true),
            new Recorded("s_daegwallyeong", "눈보라", 0xFFDDE8F5, 0.85f, -0.2f, true),
            new Recorded("s_pyeongchang", "스키장", 0xFFA9C8E8, 0.75f, -0.45f, true),
            new Recorded("s_hwacheon", "얼음 갈라짐", 0xFF9FD3E6, 0.55f, -0.7f, true),
        };
    }

    /** A sound that exists only as a recording (assets/<id>.ogg); silent until decoded. Recorded sounds have no synth fallback. */
    static final class Recorded extends SoundSource {
        Recorded(String id, String name, int color, float x, float y, boolean lockedUntilVisited) {
            super(id, name, color, x, y, id.hashCode());
            asset = id + ".ogg";
            locked = lockedUntilVisited;
        }
        @Override void render(float[] out, int n, float sr) { java.util.Arrays.fill(out, 0, n, 0f); }
    }

    // ── 빗소리: band-limited hiss + pitched droplets ─────────────────────────
    static final class Rain extends SoundSource {
        private float lp1, lp2, intensity = 0.85f, intensityTarget = 0.85f;
        private final float[] dF = new float[24], dAmp = new float[24], dDec = new float[24];
        private final double[] dPh = new double[24];
        Rain() {
            super("rain", "빗소리", 0xFF5AA9E6, -0.45f, -0.35f, 11);
            asset = "rain.ogg"; // CC0 recording; synth below is only the fallback
        }
        @Override void render(float[] out, int n, float sr) {
            float a1 = Noise.lp(5500, sr), a2 = Noise.lp(700, sr);
            float rate = 45f / sr;
            if (rng.next() < n / (sr * 4f)) intensityTarget = rng.range(0.6f, 1f);
            for (int i = 0; i < n; i++) {
                intensity += (intensityTarget - intensity) * 0.00002f;
                float w = rng.white();
                lp1 += a1 * (w - lp1);
                lp2 += a2 * (lp1 - lp2);
                float s = (lp1 - lp2) * 0.9f * intensity;
                if (rng.next() < rate * intensity) {
                    for (int k = 0; k < dAmp.length; k++) if (dAmp[k] < 1e-4f) {
                        float r = rng.next();
                        dF[k] = rng.range(1500, 5000);
                        dAmp[k] = 0.04f + 0.22f * r * r;
                        dDec[k] = (float) Math.exp(-1.0 / (sr * rng.range(0.003f, 0.012f)));
                        dPh[k] = 0;
                        break;
                    }
                }
                for (int k = 0; k < dAmp.length; k++) {
                    if (dAmp[k] < 1e-4f) continue;
                    s += dAmp[k] * (float) Math.sin(dPh[k]);
                    dPh[k] += TAU * dF[k] / sr;
                    dF[k] *= 0.99985f;
                    dAmp[k] *= dDec[k];
                }
                out[i] = s;
            }
        }
    }

    // ── 파도: slow swell of low rumble with surf hiss on the crest ───────────
    static final class Waves extends SoundSource {
        private double phase;
        private float period = 9f, rumble, surfLp;
        Waves() {
            super("waves", "파도", 0xFF3FC1A6, 0.0f, 0.62f, 23);
            asset = "waves.ogg"; // CC0 recording; synth below is only the fallback
        }
        @Override void render(float[] out, int n, float sr) {
            float aR = Noise.lp(380, sr), aS = Noise.lp(3500, sr);
            for (int i = 0; i < n; i++) {
                phase += 1.0 / (period * sr);
                if (phase >= 1) { phase -= 1; period = rng.range(7f, 12f); }
                float e = (float) Math.pow(0.5 - 0.5 * Math.cos(TAU * phase), 1.6);
                rumble += aR * (rng.brown() - rumble);
                surfLp += aS * (rng.pink() - surfLp);
                out[i] = (0.25f + 0.75f * e) * rumble * 1.1f + e * e * surfLp * 0.9f;
            }
        }
    }

    // ── 바람: pink noise through a drifting resonant band-pass ───────────────
    static final class Wind extends SoundSource {
        private float low, band, fc = 400, fcTarget = 400, gust = 0.6f, gustTarget = 0.6f, body;
        Wind() {
            super("wind", "바람", 0xFFB8C4D6, 0.55f, -0.3f, 37);
            asset = "wind.ogg"; // CC0 recording; synth below is only the fallback
        }
        @Override void render(float[] out, int n, float sr) {
            if (rng.next() < n / (sr * 2.5f)) fcTarget = rng.range(220, 900);
            if (rng.next() < n / (sr * 1.8f)) gustTarget = rng.range(0.3f, 1f);
            float aB = Noise.lp(250, sr);
            float q = 0.45f;
            for (int i = 0; i < n; i++) {
                fc += (fcTarget - fc) * 0.00004f;
                gust += (gustTarget - gust) * 0.00003f;
                float f = (float) (2 * Math.sin(Math.PI * fc / sr));
                float in = rng.pink();
                low += f * band;
                float high = in - low - q * band;
                band += f * high;
                body += aB * (rng.brown() - body);
                out[i] = (band * 0.9f + body * 0.25f) * gust;
            }
        }
    }

    // ── 모닥불: low roar + random crackles and pops ──────────────────────────
    static final class Fire extends SoundSource {
        private float roar, hpLp, crack, crackDec = 0.99f, flicker = 0.7f, flickerTarget = 0.7f;
        Fire() {
            super("fire", "모닥불", 0xFFF29B4B, -0.3f, 0.35f, 41);
            asset = "fire.ogg"; // CC0 recording; synth below is only the fallback
        }
        @Override void render(float[] out, int n, float sr) {
            float aR = Noise.lp(300, sr), aH = Noise.lp(1500, sr);
            float crackRate = 9f / sr, popRate = 0.8f / sr;
            if (rng.next() < n / (sr * 0.7f)) flickerTarget = rng.range(0.45f, 1f);
            for (int i = 0; i < n; i++) {
                flicker += (flickerTarget - flicker) * 0.0002f;
                roar += aR * (rng.brown() - roar);
                float r = rng.next();
                if (r < crackRate) {
                    crack = Math.max(crack, rng.range(0.15f, 0.7f));
                    crackDec = (float) Math.exp(-1.0 / (sr * rng.range(0.0006f, 0.003f)));
                } else if (r < crackRate + popRate) {
                    crack = Math.max(crack, rng.range(0.6f, 1f));
                    crackDec = (float) Math.exp(-1.0 / (sr * rng.range(0.004f, 0.009f)));
                }
                float w = rng.white();
                hpLp += aH * (w - hpLp);
                out[i] = roar * 0.8f * flicker + (w - hpLp) * crack;
                crack *= crackDec;
            }
        }
    }

    // ── 앰비언트 패드: slowly changing soft chords ───────────────────────────
    static final class Pad extends SoundSource {
        private static final float[][] CHORDS = {
            {130.81f, 196.00f, 246.94f, 329.63f},
            {110.00f, 164.81f, 246.94f, 261.63f},
            {87.31f, 174.61f, 220.00f, 329.63f},
            {98.00f, 146.83f, 246.94f, 293.66f},
        };
        private final float[] freq = CHORDS[0].clone();
        private final double[] phA = new double[4], phB = new double[4], lfo = new double[4];
        private final float[] lfoRate = {0.07f, 0.09f, 0.11f, 0.06f};
        private int chord, samplesToChange;
        private float lp;
        Pad() {
            super("pad", "앰비언트", 0xFFB48CF2, 0.0f, -0.6f, 67);
            asset = "pad.ogg"; // CC0 recording; synth below is only the fallback
        }
        @Override void render(float[] out, int n, float sr) {
            float glide = 1f / (sr * 3f), aL = Noise.lp(1800, sr);
            for (int i = 0; i < n; i++) {
                if (--samplesToChange <= 0) { chord = (chord + 1) % CHORDS.length; samplesToChange = (int) (sr * 14); }
                float s = 0;
                for (int v = 0; v < 4; v++) {
                    freq[v] += (CHORDS[chord][v] - freq[v]) * glide;
                    phA[v] += TAU * freq[v] * 1.0025 / sr;
                    phB[v] += TAU * freq[v] * 0.9975 / sr;
                    lfo[v] += TAU * lfoRate[v] / sr;
                    float amp = 0.6f + 0.4f * (float) Math.sin(lfo[v]);
                    s += amp * ((float) Math.sin(phA[v]) + (float) Math.sin(phB[v])
                            + 0.2f * (float) Math.sin(2 * phA[v]));
                }
                lp += aL * (s - lp);
                out[i] = lp * 0.11f;
            }
            for (int v = 0; v < 4; v++) { phA[v] %= TAU; phB[v] %= TAU; lfo[v] %= TAU; }
        }
    }
}
