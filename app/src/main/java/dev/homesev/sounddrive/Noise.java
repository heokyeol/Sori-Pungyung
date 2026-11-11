package dev.homesev.sounddrive;

/** Small deterministic noise source (white / pink / brown). Pure Java, no Android. */
final class Noise {
    private long s;
    private float b0, b1, b2, br;
    Noise(long seed) { s = seed == 0 ? 0x9E3779B97F4A7C15L : seed; }
    /** Uniform 0..1. */
    float next() {
        s ^= s << 13; s ^= s >>> 7; s ^= s << 17;
        return (s >>> 40) / (float) (1L << 24);
    }
    /** Uniform -1..1. */
    float white() { return next() * 2f - 1f; }
    float range(float lo, float hi) { return lo + (hi - lo) * next(); }
    /** Pink noise (Paul Kellet economy filter), roughly -1..1. */
    float pink() {
        float w = white();
        b0 = 0.99765f * b0 + w * 0.0990460f;
        b1 = 0.96300f * b1 + w * 0.2965164f;
        b2 = 0.57000f * b2 + w * 1.0526913f;
        return (b0 + b1 + b2 + w * 0.1848f) * 0.22f;
    }
    /** Brown (red) noise, roughly -1..1. */
    float brown() {
        br = (br + 0.02f * white()) / 1.02f;
        return br * 3.5f;
    }
    /** One-pole low-pass coefficient for cutoff fc. */
    static float lp(float fc, float sr) { return (float) (1.0 - Math.exp(-2.0 * Math.PI * fc / sr)); }
}
