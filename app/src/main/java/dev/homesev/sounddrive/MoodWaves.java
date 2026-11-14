package dev.homesev.sounddrive;

/**
 * How each sound looks in the mood-light screen ("소리 물결"): rain is fine and quick, waves are wide
 * and slow, wind is airy, fire crackles. Pure Java; the web build has the same table in app.js.
 * All distances are fractions of the picture's half-size.
 */
final class MoodWaves {
    private MoodWaves() {}

    static final class Style {
        final float interval, speed, maxR, stroke, jitter;
        Style(float interval, float speed, float maxR, float stroke, float jitter) {
            this.interval = interval; this.speed = speed; this.maxR = maxR; this.stroke = stroke; this.jitter = jitter;
        }
    }

    static final Style FINE = new Style(0.14f, 0.22f, 0.22f, 0.9f, 0.5f);
    static final Style SWELL = new Style(2.6f, 0.12f, 0.95f, 1.4f, 0.15f);
    static final Style AIRY = new Style(1.8f, 0.16f, 0.8f, 1.0f, 0.3f);
    static final Style CRACKLE = new Style(0.45f, 0.08f, 0.25f, 1.0f, 0.9f);
    static final Style STORM = new Style(4.0f, 0.35f, 1.2f, 1.6f, 0.4f);
    static final Style MEDIUM = new Style(1.1f, 0.14f, 0.5f, 1.0f, 0.3f);
    /** A bird call: two quick small rings at its perch. */
    static final Style CALL = new Style(0f, 0.30f, 0.18f, 1.0f, 0f);

    private static final String[] FINE_IDS = {"rain", "jeonju", "valley", "danyang"};
    private static final String[] SWELL_IDS = {"waves", "udo", "yeosu", "yangyang", "haeundae", "taean", "harbor"};
    private static final String[] AIRY_IDS = {"wind", "jeju", "damyang", "andong", "naejangsan", "mindungsan", "boseong",
            "daegwallyeong", "cmb", "pad", "saturn", "jupiter", "airport", "station"};
    private static final String[] CRACKLE_IDS = {"fire", "maisan", "hwacheon"};

    static Style of(String id) {
        if (id.contains("thunder")) return STORM;
        if (has(id, FINE_IDS)) return FINE;
        if (has(id, SWELL_IDS)) return SWELL;
        if (has(id, AIRY_IDS)) return AIRY;
        if (has(id, CRACKLE_IDS)) return CRACKLE;
        return MEDIUM;
    }

    private static boolean has(String id, String[] keys) {
        for (String k : keys) if (id.contains(k)) return true;
        return false;
    }
}
