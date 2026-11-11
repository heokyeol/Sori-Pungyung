package dev.homesev.sounddrive;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Seasonal landmarks: a sound unlocks only when you are at the place in its season
 * (e.g. 양양 in summer → surfing, 대관령 in winter → blizzard). Once unlocked it stays.
 * Coordinates were looked up once from OpenStreetMap; the app never geocodes. Pure Java.
 */
final class Seasons {
    static final String[] SEASON_NAMES = {"봄", "여름", "가을", "겨울"};

    /** 0 = spring (Mar–May), 1 = summer (Jun–Aug), 2 = autumn (Sep–Nov), 3 = winter (Dec–Feb). month is 1–12. */
    static int season(int month) {
        if (month >= 3 && month <= 5) return 0;
        if (month >= 6 && month <= 8) return 1;
        if (month >= 9 && month <= 11) return 2;
        return 3;
    }

    static final class Spot {
        final String id, place, sound;
        final int season;
        final double radiusM;
        final double[][] points; // lat, lon
        Spot(String id, String place, String sound, int season, double radiusM, double[]... points) {
            this.id = id; this.place = place; this.sound = sound; this.season = season; this.radiusM = radiusM; this.points = points;
        }
        String seasonName() { return SEASON_NAMES[season]; }
        String hint() { return SEASON_NAMES[season] + "에 " + place + "에 가면 열려요"; }
    }

    static final Spot[] ALL = {
        new Spot("jinhae", "진해 여좌천", "s_jinhae", 0, 2500, new double[] {35.15688, 128.65964}),
        new Spot("boseong", "보성 녹차밭", "s_boseong", 0, 2500, new double[] {34.71585, 127.07796}),
        new Spot("gasiri", "제주 가시리 유채꽃길", "s_gasiri", 0, 2500, new double[] {33.40969, 126.71984}, new double[] {33.38189, 126.73290}),
        new Spot("yangyang", "양양 서핑 해변", "s_yangyang", 1, 2500, new double[] {38.02325, 128.72332}, new double[] {37.97143, 128.75985}),
        new Spot("haeundae", "해운대", "s_haeundae", 1, 1500, new double[] {35.15929, 129.15861}),
        new Spot("damyang", "담양 죽녹원", "s_damyang", 1, 1500, new double[] {35.32863, 126.98556}),
        new Spot("maisan", "진안 마이산", "s_maisan", 2, 3000, new double[] {35.75800, 127.41200}),
        new Spot("naejangsan", "내장산", "s_naejangsan", 2, 3000, new double[] {35.48974, 126.90198}, new double[] {35.47833, 126.88899}),
        new Spot("mindungsan", "정선 민둥산", "s_mindungsan", 2, 3000, new double[] {37.24534, 128.77296}),
        new Spot("daegwallyeong", "대관령", "s_daegwallyeong", 3, 4000, new double[] {37.68712, 128.76041}, new double[] {37.68953, 128.75330}),
        new Spot("pyeongchang", "평창 스키장", "s_pyeongchang", 3, 3000, new double[] {37.62815, 128.67773}, new double[] {37.57865, 128.31635}, new double[] {37.66244, 128.68092}),
        new Spot("hwacheon", "화천 산천어축제", "s_hwacheon", 3, 3000, new double[] {38.10590, 127.71058}),
    };

    static Spot bySound(String soundId) {
        for (Spot s : ALL) if (s.sound.equals(soundId)) return s;
        return null;
    }

    /** Distance in metres from (lat, lon) to the nearest point of a spot. */
    static double distance(Spot s, double lat, double lon) {
        double best = Double.MAX_VALUE, cosLat = Math.cos(Math.toRadians(lat));
        for (double[] p : s.points) {
            double dLat = p[0] - lat, dLon = (p[1] - lon) * cosLat;
            best = Math.min(best, Math.sqrt(dLat * dLat + dLon * dLon) * 111_195);
        }
        return best;
    }

    /** Bearing (0 = north) from (lat, lon) to the spot's nearest point. */
    static double bearing(Spot s, double lat, double lon) {
        double best = Double.MAX_VALUE, b = 0, cosLat = Math.cos(Math.toRadians(lat));
        for (double[] p : s.points) {
            double dLat = p[0] - lat, dLon = (p[1] - lon) * cosLat, d = Math.hypot(dLat, dLon);
            if (d < best) { best = d; b = (Math.toDegrees(Math.atan2(dLon, dLat)) + 360) % 360; }
        }
        return b;
    }

    /** Result of a position check. */
    static final class Check {
        final List<Spot> unlock = new ArrayList<>();    // here, in season, not yet unlocked
        final List<Spot> offSeason = new ArrayList<>(); // here, but the wrong season
    }

    static Check check(double lat, double lon, int month, Set<String> unlocked) {
        Check c = new Check();
        int now = season(month);
        for (Spot s : ALL) {
            if (unlocked.contains(s.id)) continue;
            if (distance(s, lat, lon) > s.radiusM) continue;
            if (s.season == now) c.unlock.add(s); else c.offSeason.add(s);
        }
        return c;
    }

    /** The nearest spot in the current season that is still locked, or null. */
    static Spot nearestInSeason(double lat, double lon, int month, Set<String> unlocked) {
        Spot best = null;
        double bd = Double.MAX_VALUE;
        for (Spot s : ALL) {
            if (unlocked.contains(s.id) || s.season != season(month)) continue;
            double d = distance(s, lat, lon);
            if (d < bd) { bd = d; best = s; }
        }
        return best;
    }
}
