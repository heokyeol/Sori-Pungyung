package dev.homesev.sounddrive;

/**
 * Works out ambient-light colors from the sound stage. Pure Java (testable off-device).
 *
 * Several light zones → spatial gradient: each zone takes the colors of the sounds
 * near it (closer and louder sounds count more), so the cabin mirrors the stage.
 * One light zone → time gradient: the light slowly flows through the colors of the
 * sounds that are on.
 */
final class MoodGradient {
    private MoodGradient() {}

    /** Seconds spent on each color in the single-zone flow. */
    static final double FLOW_SECONDS = 8;

    /** Stage position (listener at 0,0, front = -y) of a seat such as "ROW_2_LEFT". */
    static float[] seatPoint(String seat) {
        float x = seat.endsWith("_LEFT") ? -0.5f : seat.endsWith("_RIGHT") ? 0.5f : 0f;
        float y = seat.startsWith("ROW_1") ? -0.45f : seat.startsWith("ROW_2") ? 0.35f : seat.startsWith("ROW_3") ? 0.75f : 0f;
        return new float[] {x, y};
    }

    /** Average point of several seats (a zone may cover more than one seat). */
    static float[] zonePoint(String[] seats) {
        if (seats == null || seats.length == 0) return new float[] {0, 0};
        float x = 0, y = 0;
        for (String s : seats) { float[] p = seatPoint(s); x += p[0]; y += p[1]; }
        return new float[] {x / seats.length, y / seats.length};
    }

    /**
     * One 0xRRGGBB color per zone, or null when no sound is on.
     * @param zones stage points of the light zones
     * @param timeSec monotonically increasing time, used only for the single-zone flow
     */
    static int[] colors(float[][] zones, SoundSource[] sources, double timeSec) {
        int on = 0;
        for (SoundSource s : sources) if (s.enabled && s.volume > 0.01f) on++;
        if (on == 0 || zones.length == 0) return null;
        int[] out = new int[zones.length];
        if (zones.length == 1) {
            out[0] = flow(sources, on, timeSec);
            return out;
        }
        for (int z = 0; z < zones.length; z++) {
            double r = 0, g = 0, b = 0, wSum = 0;
            for (SoundSource s : sources) {
                if (!s.enabled || s.volume <= 0.01f) continue;
                double dx = s.x - zones[z][0], dy = s.y - zones[z][1];
                double w = s.volume / (dx * dx + dy * dy + 0.04);
                int c = s.color;
                // Mix in squared (roughly linear-light) space so blends don't go muddy.
                r += w * sq((c >> 16) & 0xFF); g += w * sq((c >> 8) & 0xFF); b += w * sq(c & 0xFF);
                wSum += w;
            }
            out[z] = rgb(Math.sqrt(r / wSum), Math.sqrt(g / wSum), Math.sqrt(b / wSum));
        }
        return out;
    }

    private static int flow(SoundSource[] sources, int on, double timeSec) {
        int[] cols = new int[on];
        int n = 0;
        for (SoundSource s : sources) if (s.enabled && s.volume > 0.01f) cols[n++] = s.color;
        if (on == 1) return cols[0] & 0xFFFFFF;
        double pos = (timeSec / FLOW_SECONDS) % on;
        if (pos < 0) pos += on;
        int i = (int) pos;
        double f = pos - i;
        double t = (1 - Math.cos(Math.PI * f)) / 2; // ease in/out
        return mix(cols[i], cols[(i + 1) % on], t);
    }

    static int mix(int a, int b, double t) {
        double r = Math.sqrt(sq((a >> 16) & 0xFF) * (1 - t) + sq((b >> 16) & 0xFF) * t);
        double g = Math.sqrt(sq((a >> 8) & 0xFF) * (1 - t) + sq((b >> 8) & 0xFF) * t);
        double bl = Math.sqrt(sq(a & 0xFF) * (1 - t) + sq(b & 0xFF) * t);
        return rgb(r, g, bl);
    }

    /** Largest per-channel difference between two colors. */
    static int diff(int a, int b) {
        return Math.max(Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF)),
               Math.max(Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF)), Math.abs((a & 0xFF) - (b & 0xFF))));
    }

    private static double sq(int v) { return (double) v * v; }

    private static int rgb(double r, double g, double b) {
        int ri = (int) Math.round(Math.max(0, Math.min(255, r)));
        int gi = (int) Math.round(Math.max(0, Math.min(255, g)));
        int bi = (int) Math.round(Math.max(0, Math.min(255, b)));
        return (ri << 16) | (gi << 8) | bi;
    }
}
