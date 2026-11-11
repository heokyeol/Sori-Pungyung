package dev.homesev.sounddrive;

/**
 * Turns a mono source plus a stage position into stereo:
 * equal-power panning, distance attenuation, inter-aural delay,
 * head shadow on the far ear and a darker tone for sounds behind you.
 */
final class Spatial {
    private Spatial() {}

    static final int RING = 128;

    /** Per-source running state (audio thread only). */
    static final class State {
        float gL, gR, aL = 1, aR = 1, dL, dR, lpL, lpR;
        final float[] ring = new float[RING];
        int w;
        boolean primed;
    }

    /** Desired parameters for one block. */
    static final class Target {
        float gL, gR, aL, aR, dL, dR;
    }

    static void target(float x, float y, float sr, Target t) {
        float d = (float) Math.hypot(x, y);
        double az = Math.atan2(x, -y); // 0 = front, + = right
        float s = (float) Math.sin(az), c = (float) Math.cos(az);
        float directional = Math.min(1f, d / 0.25f); // near the head → less directional
        float p = s * directional;
        float gl = (float) Math.cos((p + 1) * Math.PI / 4), gr = (float) Math.sin((p + 1) * Math.PI / 4);
        float dist = 0.4f / (0.4f + Math.max(0f, d - 0.1f) * 1.6f);
        t.gL = gl * dist;
        t.gR = gr * dist;
        float itd = 0.00066f * sr * Math.abs(p);
        t.dL = p > 0 ? itd : 0;
        t.dR = p < 0 ? itd : 0;
        float rear = Math.max(0f, -c) * directional;
        float base = 16000f * (1 - 0.55f * rear) * (1 - 0.35f * Math.min(1f, d));
        float far = base * (1 - 0.8f * Math.abs(p));
        t.aL = Noise.lp(p > 0 ? far : base, sr);
        t.aR = Noise.lp(p < 0 ? far : base, sr);
    }

    /** Spatialize mono[0..n) scaled by gain and add it into l/r. */
    static void process(State st, Target tg, float gain, float[] mono, float[] l, float[] r, int n) {
        if (!st.primed) {
            st.gL = tg.gL; st.gR = tg.gR; st.aL = tg.aL; st.aR = tg.aR; st.dL = tg.dL; st.dR = tg.dR;
            st.primed = true;
        }
        float inv = 1f / n;
        float dgL = (tg.gL - st.gL) * inv, dgR = (tg.gR - st.gR) * inv;
        float daL = (tg.aL - st.aL) * inv, daR = (tg.aR - st.aR) * inv;
        float[] ring = st.ring;
        for (int i = 0; i < n; i++) {
            ring[st.w] = mono[i];
            st.dL += (tg.dL - st.dL) * 0.002f;
            st.dR += (tg.dR - st.dR) * 0.002f;
            float vl = read(ring, st.w, st.dL), vr = read(ring, st.w, st.dR);
            st.w = (st.w + 1) & (RING - 1);
            st.aL += daL; st.aR += daR; st.gL += dgL; st.gR += dgR;
            st.lpL += st.aL * (vl - st.lpL);
            st.lpR += st.aR * (vr - st.lpR);
            l[i] += st.lpL * st.gL * gain;
            r[i] += st.lpR * st.gR * gain;
        }
    }

    private static float read(float[] ring, int w, float delay) {
        float pos = w - delay;
        int i0 = (int) Math.floor(pos);
        float frac = pos - i0;
        float a = ring[i0 & (RING - 1)], b = ring[(i0 + 1) & (RING - 1)];
        return a + (b - a) * frac;
    }
}
