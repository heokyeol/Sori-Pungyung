package dev.homesev.sounddrive;

/** One sound on the stage: a mono generator plus its position, level and on/off state. */
abstract class SoundSource {
    final String id, name;
    final int color;
    /** Stage position: listener at (0,0), front is -y, radius 1 is the edge. */
    volatile float x, y;
    volatile float volume = 0.8f;
    volatile boolean enabled;
    /** Location-unlock sounds stay hidden and silent until unlocked. */
    volatile boolean locked;
    final float homeX, homeY;
    final Noise rng;
    final Spatial.State spatial = new Spatial.State();
    float fade; // smoothed on/off, touched only by the audio thread

    /** Asset name of a recorded stereo loop (e.g. "waves.ogg"), or null for synth only. */
    String asset;
    /** Decoded loop [channel][sample] at the mixer rate; when set it replaces the synth. */
    volatile float[][] loop;
    private int loopPos;
    private float lgL = -1, lgR, la = 1, lpL, lpR;

    SoundSource(String id, String name, int color, float x, float y, long seed) {
        this.id = id; this.name = name; this.color = color;
        this.x = this.homeX = x; this.y = this.homeY = y;
        this.rng = new Noise(seed);
    }

    /** Fill out[0..n) with mono samples roughly within -1..1. */
    abstract void render(float[] out, int n, float sr);

    /**
     * Add this source into the stereo bus. fadeFrom/fadeTo ramp the on/off fade across
     * the block. The default renders mono and places it at (x, y); sources made of
     * several moving voices (the bird flock) override this.
     */
    void renderInto(float[] l, float[] r, int n, float sr, float fadeFrom, float fadeTo,
                    Spatial.Target tg, float[] mono) {
        float[][] rec = loop;
        if (rec != null) { renderLoop(rec, l, r, n, sr, fadeFrom, fadeTo); return; }
        render(mono, n, sr);
        for (int i = 0; i < n; i++) mono[i] *= fadeFrom + (fadeTo - fadeFrom) * i / n;
        Spatial.target(x, y, sr, tg);
        Spatial.process(spatial, tg, volume, mono, l, r, n);
    }

    /**
     * Recorded stereo loop placed on the stage. Keeps the recording's natural width,
     * narrowing it and shifting the balance toward the side the dot is on; farther is
     * quieter, behind is darker (same rules as {@link Spatial}).
     */
    private void renderLoop(float[][] rec, float[] l, float[] r, int n, float sr, float fadeFrom, float fadeTo) {
        float[] cl = rec[0], cr = rec.length > 1 ? rec[1] : rec[0];
        int len = cl.length;
        float d = (float) Math.hypot(x, y);
        double az = Math.atan2(x, -y);
        float sn = (float) Math.sin(az), cs = (float) Math.cos(az);
        float directional = Math.min(1f, d / 0.25f);
        float p = sn * directional;
        float width = 1f - 0.6f * Math.abs(p);
        float dist = 0.4f / (0.4f + Math.max(0f, d - 0.1f) * 1.6f);
        float gl = (float) (Math.cos((p + 1) * Math.PI / 4) * Math.sqrt(2)) * dist * volume;
        float gr = (float) (Math.sin((p + 1) * Math.PI / 4) * Math.sqrt(2)) * dist * volume;
        float rear = Math.max(0f, -cs) * directional;
        float a = Noise.lp(16000f * (1 - 0.55f * rear) * (1 - 0.35f * Math.min(1f, d)), sr);
        if (lgL < 0) { lgL = gl; lgR = gr; la = a; }
        float dgl = (gl - lgL) / n, dgr = (gr - lgR) / n, da = (a - la) / n;
        if (loopPos >= len) loopPos = 0;
        for (int i = 0; i < n; i++) {
            float L = cl[loopPos], R = cr[loopPos];
            if (++loopPos >= len) loopPos = 0;
            float mid = (L + R) * 0.5f, side = (L - R) * 0.5f * width;
            float f = fadeFrom + (fadeTo - fadeFrom) * i / n;
            lgL += dgl; lgR += dgr; la += da;
            lpL += la * ((mid + side) * f - lpL);
            lpR += la * ((mid - side) * f - lpR);
            l[i] += lpL * lgL;
            r[i] += lpR * lgR;
        }
    }
}
