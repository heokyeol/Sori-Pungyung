package dev.homesev.sounddrive;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Region sounds (the default unlocks): each 시·군·섬 has a signature sound that opens when
 * you are anywhere in it — 영종도 → airplane cabin, 철원 → cranes, 여수 → night sea.
 * Regions are broad (km to tens of km). Coordinates were looked up once from OpenStreetMap.
 * Pure Java. Hidden landmarks (stamps) live in {@link Unlocks}; seasonal events in {@link Seasons}.
 */
final class Regions {
    static final class Region {
        final String id, name, sound, emoji;
        final double radiusM;
        final double[][] points; // lat, lon
        Region(String id, String name, String sound, String emoji, double radiusM, double[]... points) {
            this.id = id; this.name = name; this.sound = sound; this.emoji = emoji; this.radiusM = radiusM; this.points = points;
        }
        String hint() { return name + "에 가면 열려요"; }
    }

    static final Region[] ALL = {
        new Region("yeongjong", "인천 영종도", "u_airport", "✈️", 8000, new double[] {37.47754, 126.46970}),
        new Region("hangang", "서울 한강", "r_hangang", "🌉", 1500, new double[] {37.52567, 126.93606}, new double[] {37.50887, 126.99394}, new double[] {37.52983, 127.06811}),
        new Region("chuncheon", "춘천", "r_chuncheon", "🦆", 8000, new double[] {37.88106, 127.72976}),
        new Region("sokcho", "속초·설악", "u_valley", "🏞️", 8000, new double[] {38.20696, 128.59134}, new double[] {38.16062, 128.49588}),
        new Region("gangneung", "강릉", "u_cafe", "☕", 10000, new double[] {37.75253, 128.87595}),
        new Region("cheorwon", "철원", "r_cheorwon", "🕊️", 12000, new double[] {38.24315, 127.25125}),
        new Region("danyang", "단양", "r_danyang", "💧", 8000, new double[] {36.98643, 128.36824}),
        new Region("taean", "태안", "r_taean", "🦀", 15000, new double[] {36.75791, 126.29652}),
        new Region("jeonju", "전주 한옥마을", "r_jeonju", "🏮", 1500, new double[] {35.81816, 127.15305}),
        new Region("gokseong", "곡성", "u_station", "🚂", 8000, new double[] {35.28015, 127.28443}),
        new Region("suncheon", "순천만", "r_suncheon", "🌾", 4000, new double[] {34.88668, 127.50750}),
        new Region("yeosu", "여수", "r_yeosu", "🌃", 10000, new double[] {34.73930, 127.73594}),
        new Region("namhae", "남해", "r_namhae", "🐸", 15000, new double[] {34.83855, 127.89366}),
        new Region("gyeongju", "경주", "u_temple", "🛕", 12000, new double[] {35.85567, 129.22488}),
        new Region("andong", "안동 하회마을", "r_andong", "🎋", 2500, new double[] {36.54024, 128.52082}),
        new Region("busan", "부산", "u_harbor", "⚓", 15000, new double[] {35.17995, 129.07524}),
        new Region("tongyeong", "통영", "r_tongyeong", "🐟", 8000, new double[] {34.85425, 128.43320}),
        new Region("ulleung", "울릉도", "r_ulleung", "🪨", 9000, new double[] {37.50111, 130.85861}),
        new Region("jeju", "제주도", "r_jeju", "🌬️", 20000, new double[] {33.50004, 126.52936}, new double[] {33.25395, 126.55999},
                new double[] {33.39000, 126.25000}, new double[] {33.45000, 126.85000}),
        new Region("udo", "우도", "r_udo", "🌊", 3000, new double[] {33.50670, 126.95574}),
    };

    static Region byId(String id) {
        for (Region r : ALL) if (r.id.equals(id)) return r;
        return null;
    }

    /** Regions whose sound is this source id (a sound can belong to one region). */
    static Region bySound(String soundId) {
        for (Region r : ALL) if (r.sound.equals(soundId)) return r;
        return null;
    }

    static double distance(Region r, double lat, double lon) {
        double best = Double.MAX_VALUE, cosLat = Math.cos(Math.toRadians(lat));
        for (double[] p : r.points) {
            double dLat = p[0] - lat, dLon = (p[1] - lon) * cosLat;
            best = Math.min(best, Math.sqrt(dLat * dLat + dLon * dLon) * 111_195);
        }
        return best;
    }

    static double[] nearestPoint(Region r, double lat, double lon) {
        double best = Double.MAX_VALUE, cosLat = Math.cos(Math.toRadians(lat));
        double[] out = r.points[0];
        for (double[] p : r.points) {
            double dLat = p[0] - lat, dLon = (p[1] - lon) * cosLat, d = Math.hypot(dLat, dLon);
            if (d < best) { best = d; out = p; }
        }
        return out;
    }

    static double bearing(Region r, double lat, double lon) {
        double[] p = nearestPoint(r, lat, lon);
        double dLat = p[0] - lat, dLon = (p[1] - lon) * Math.cos(Math.toRadians(lat));
        return (Math.toDegrees(Math.atan2(dLon, dLat)) + 360) % 360;
    }

    /** Regions you are inside that you haven't visited yet. */
    static List<Region> check(double lat, double lon, Set<String> visited) {
        List<Region> out = new ArrayList<>();
        for (Region r : ALL) if (!visited.contains(r.id) && distance(r, lat, lon) <= r.radiusM) out.add(r);
        return out;
    }

    /** Unvisited regions sorted by distance (nearest first). */
    static List<Region> nearest(double lat, double lon, Set<String> visited, int max) {
        List<Region> out = new ArrayList<>();
        for (Region r : ALL) if (visited == null || !visited.contains(r.id)) out.add(r);
        out.sort((a, b) -> Double.compare(distance(a, lat, lon), distance(b, lat, lon)));
        return out.size() > max ? out.subList(0, max) : out;
    }
}
