package dev.homesev.sounddrive;

/** Host-side checks for the mood-light color math. */
public final class MoodGradientTest {
    private static int pass, fail;

    public static void main(String[] args) {
        SoundSource[] s = Sources.all();
        SoundSource pad = find(s, "pad"), birds = find(s, "birds"), rain = find(s, "rain");
        for (SoundSource x : s) x.enabled = false;

        // Nothing on → no colors (caller restores the original light).
        float[][] four = {
            MoodGradient.seatPoint("ROW_1_LEFT"), MoodGradient.seatPoint("ROW_1_RIGHT"),
            MoodGradient.seatPoint("ROW_2_LEFT"), MoodGradient.seatPoint("ROW_2_RIGHT")};
        check("no sound → null", MoodGradient.colors(four, s, 0) == null);

        // Purple pad in front, green birds right-rear (like the phone screenshot).
        pad.enabled = true; pad.x = 0; pad.y = -0.6f;
        birds.enabled = true; birds.x = 0.5f; birds.y = 0.5f;
        int[] c = MoodGradient.colors(four, s, 0);
        int purple = pad.color & 0xFFFFFF, green = birds.color & 0xFFFFFF;
        check("front-left zone is closer to purple", MoodGradient.diff(c[0], purple) < MoodGradient.diff(c[0], green));
        check("front-right zone is closer to purple", MoodGradient.diff(c[1], purple) < MoodGradient.diff(c[1], green));
        check("rear-right zone is closer to green", MoodGradient.diff(c[3], green) < MoodGradient.diff(c[3], purple));
        check("rear-right is greener than rear-left", ((c[3] >> 8) & 0xFF) - ((c[3] >> 16) & 0xFF) > ((c[2] >> 8) & 0xFF) - ((c[2] >> 16) & 0xFF));
        System.out.printf("   4 zones: FL=%06X FR=%06X RL=%06X RR=%06X%n", c[0], c[1], c[2], c[3]);

        // Moving the sound moves the color.
        birds.x = -0.5f;
        int[] moved = MoodGradient.colors(four, s, 0);
        check("move birds left → rear-left turns green", MoodGradient.diff(moved[2], green) < MoodGradient.diff(moved[2], purple));

        // Louder sound dominates.
        birds.x = 0; birds.y = 0; pad.x = 0; pad.y = 0;
        birds.volume = 0.1f; pad.volume = 1f;
        int[] loud = MoodGradient.colors(four, s, 0);
        check("louder sound dominates", MoodGradient.diff(loud[0], purple) < MoodGradient.diff(loud[0], green));
        birds.volume = 0.8f; pad.volume = 0.8f;

        // Seats covering the whole car average to the middle.
        float[] mid = MoodGradient.zonePoint(new String[] {"ROW_1_LEFT", "ROW_1_RIGHT"});
        check("zone point averages seats", Math.abs(mid[0]) < 1e-6 && mid[1] < 0);

        // Single zone → time flow through the colors that are on.
        float[][] one = {{0, 0}};
        int t0 = MoodGradient.colors(one, s, 0)[0];
        int tHalf = MoodGradient.colors(one, s, MoodGradient.FLOW_SECONDS * 0.5)[0];
        int t1 = MoodGradient.colors(one, s, MoodGradient.FLOW_SECONDS)[0];
        check("flow starts on first color", t0 == green);
        check("flow is between colors halfway", MoodGradient.diff(tHalf, t0) > 10 && MoodGradient.diff(tHalf, t1) > 10);
        check("flow reaches next color", t1 != t0);
        int step = 0;
        for (double t = 0; t < MoodGradient.FLOW_SECONDS * 2; t += 0.25) {
            int a = MoodGradient.colors(one, s, t)[0], b = MoodGradient.colors(one, s, t + 0.25)[0];
            step = Math.max(step, MoodGradient.diff(a, b));
        }
        check("flow changes smoothly at 4 updates/s (max step " + step + ")", step <= 12);

        // Single sound → steady color.
        birds.enabled = false;
        check("one sound → its color", MoodGradient.colors(one, s, 3.7)[0] == purple);
        rain.enabled = false;

        System.out.println(pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    private static SoundSource find(SoundSource[] s, String id) {
        for (SoundSource x : s) if (x.id.equals(id)) return x;
        throw new IllegalArgumentException(id);
    }

    private static void check(String name, boolean ok) {
        if (ok) pass++; else { fail++; System.out.println("FAIL: " + name); }
    }
}
