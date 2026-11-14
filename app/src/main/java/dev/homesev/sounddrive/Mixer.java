package dev.homesev.sounddrive;

/** Mixes all sources into interleaved stereo float. Pure Java so it can be tested off-device. */
final class Mixer {
    final SoundSource[] sources;
    final float sr;
    volatile float master = 0.8f;
    /** Extra gain in dB, e.g. road-noise compensation from vehicle speed. */
    volatile float extraDb;
    /** 0..1, lowered while another app ducks us. */
    volatile float duck = 1f;
    /** 0..1 slow fade (sleep timer, open window). Smoothed over ~0.5 s. */
    volatile float scene = 1f;
    /** 0 = still, 1 = planet orbits (inner sounds circle faster), 2 = whole stage turns 360°. */
    volatile int motion;
    static final int MOTION_NONE = 0, MOTION_ORBIT = 1, MOTION_ROTATE = 2;
    /** Seconds per turn: orbit at the center … at the edge, and for the whole-stage turn. */
    static final float ORBIT_INNER_S = 20f, ORBIT_OUTER_S = 100f, ROTATE_S = 60f;
    private float sceneSmooth = 1f;

    private float masterSmooth = -1;
    private float[] mono = new float[0], l = new float[0], r = new float[0], l0 = new float[0], r0 = new float[0];
    private final Spatial.Target tg = new Spatial.Target();

    Mixer(SoundSource[] sources, float sr) { this.sources = sources; this.sr = sr; }

    /** Move every sound one block along its orbit (runs on the audio thread, so it keeps going with the screen off). */
    void moveSources(float dt) {
        int m = motion;
        if (m == MOTION_NONE) return;
        for (SoundSource s : sources) {
            float x = s.x, y = s.y;
            float r = (float) Math.hypot(x, y);
            if (r < 1e-4f) continue;
            float period = m == MOTION_ORBIT ? ORBIT_INNER_S + (ORBIT_OUTER_S - ORBIT_INNER_S) * Math.min(1f, r) : ROTATE_S;
            double a = 2 * Math.PI * dt / period; // positive = clockwise on screen (y points down)
            float c = (float) Math.cos(a), sn = (float) Math.sin(a);
            s.x = x * c - y * sn;
            s.y = x * sn + y * c;
        }
    }

    void render(float[] interleaved, int frames) {
        moveSources(frames / sr);
        if (mono.length < frames) { mono = new float[frames]; l = new float[frames]; r = new float[frames]; l0 = new float[frames]; r0 = new float[frames]; }
        java.util.Arrays.fill(l, 0, frames, 0f);
        java.util.Arrays.fill(r, 0, frames, 0f);
        float fadeStep = frames / (sr * 0.08f); // on/off in ~80 ms: feels instant, still click-free
        for (SoundSource s : sources) {
            float want = s.enabled && !s.locked ? 1f : 0f;
            float start = s.fade;
            if (start < want) s.fade = Math.min(want, start + fadeStep);
            else if (start > want) s.fade = Math.max(want, start - fadeStep);
            if (start == 0f && s.fade == 0f) { s.level *= 0.8f; continue; }
            // Linear fade across the block avoids clicks when toggling.
            System.arraycopy(l, 0, l0, 0, frames);
            System.arraycopy(r, 0, r0, 0, frames);
            s.renderInto(l, r, frames, sr, start, s.fade, tg, mono);
            // How loud this one sound is right now (RMS of what it added), for the mood-light picture.
            double e = 0;
            for (int i = 0; i < frames; i += 2) { float dl = l[i] - l0[i], dr = r[i] - r0[i]; e += dl * dl + dr * dr; }
            float rms = (float) Math.sqrt(e / Math.max(1, frames / 2) / 2);
            s.level = rms > s.level ? s.level + (rms - s.level) * 0.6f : s.level * 0.85f + rms * 0.15f;
        }
        sceneSmooth += (scene - sceneSmooth) * Math.min(1f, frames / (sr * 0.5f));
        float targetMaster = sceneSmooth * master * duck * (float) Math.pow(10, extraDb / 20.0) * 1.6f;
        if (masterSmooth < 0) masterSmooth = targetMaster;
        float dm = (targetMaster - masterSmooth) / frames;
        for (int i = 0; i < frames; i++) {
            masterSmooth += dm;
            interleaved[2 * i] = (float) Math.tanh(l[i] * masterSmooth);
            interleaved[2 * i + 1] = (float) Math.tanh(r[i] * masterSmooth);
        }
    }
}
