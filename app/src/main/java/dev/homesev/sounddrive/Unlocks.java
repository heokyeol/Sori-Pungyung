package dev.homesev.sounddrive;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Location unlocks, fully offline: a short curated region list (category, coordinates, name,
 * radius) ships in assets/places.tsv — e.g. 영종도 → airplane cabin, 의왕 철도박물관 → train.
 * Being inside a region unlocks its category's sound. Seasonal events live in {@link Seasons}.
 * Pure Java so it can be tested off-device.
 */
final class Unlocks {
    /** One unlockable: place category, the sound it unlocks, unlock radius and a hint. */
    static final class Category {
        final String id, sound, place, hint, emoji;
        final double radiusM;
        final int color;
        Category(String id, String sound, String place, double radiusM, String hint, String emoji, int color) {
            this.id = id; this.sound = sound; this.place = place; this.radiusM = radiusM; this.hint = hint;
            this.emoji = emoji; this.color = color;
        }
    }

    static final Category[] ALL = {
        new Category("airport", "u_airport", "공항", 3000, "영종도, 김포공항, 제주공항에 가면 열려요", "✈️", 0xFF8FB8FF),
        new Category("library", "u_library", "도서관", 400, "별마당도서관, 파주 지혜의숲, 국립중앙도서관에 가면 열려요", "📚", 0xFFD9B98C),
        new Category("temple", "u_temple", "사찰", 1000, "경주 불국사, 합천 해인사, 순천 송광사에 가면 열려요", "🛕", 0xFFE8D27A),
        new Category("station", "u_station", "기차", 1000, "의왕 철도박물관이나 곡성 섬진강기차마을에 가면 열려요", "🚉", 0xFF9AA7B5),
        new Category("valley", "u_valley", "계곡", 2000, "설악산 비선대, 지리산 뱀사골, 가평 용추계곡에 가면 열려요", "🏞️", 0xFF6FD3E0),
        new Category("harbor", "u_harbor", "항구", 1000, "부산 자갈치, 인천 연안부두, 목포항에 가면 열려요", "⚓", 0xFF7FB0D8),
        new Category("cafe", "u_cafe", "카페거리", 1000, "강릉 안목 커피거리나 성수동 카페거리에 가면 열려요", "☕", 0xFFC79A7A),
        new Category("baseball", "u_baseball", "야구장", 500, "프로야구 홈구장(잠실, 사직, 고척 등)에 가면 열려요", "⚾", 0xFFFF8A80),
    };

    /** A newly reached category and the nearest place that triggered it. */
    static final class Hit {
        final Category category;
        final String placeName;
        final double distanceM;
        Hit(Category c, String name, double d) { category = c; placeName = name; distanceM = d; }
    }

    // Places, stored compactly: category index, lat/lon in 1e-5 degrees, name.
    private final List<Integer> cat = new ArrayList<>();
    private final List<int[]> pos = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private final List<Double> radii = new ArrayList<>();

    int size() { return pos.size(); }

    int count(String categoryId) {
        int ci = index(categoryId), n = 0;
        for (int c : cat) if (c == ci) n++;
        return n;
    }

    static int index(String categoryId) {
        for (int i = 0; i < ALL.length; i++) if (ALL[i].id.equals(categoryId)) return i;
        return -1;
    }

    static Category bySound(String soundId) {
        for (Category c : ALL) if (c.sound.equals(soundId)) return c;
        return null;
    }

    /** Lines of "category \t lat*1e5 \t lon*1e5 \t name [\t radius m]"; '#' lines are comments. */
    void load(Reader r) throws IOException {
        BufferedReader in = new BufferedReader(r);
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split("\t", 5);
            if (p.length < 3) continue;
            int ci = index(p[0]);
            if (ci < 0) continue;
            add(ci, Integer.parseInt(p[1]), Integer.parseInt(p[2]), p.length > 3 ? p[3] : "",
                    p.length > 4 ? Double.parseDouble(p[4].trim()) : ALL[ci].radiusM);
        }
    }

    void add(int categoryIndex, int lat5, int lon5, String name) { add(categoryIndex, lat5, lon5, name, ALL[categoryIndex].radiusM); }

    void add(int categoryIndex, int lat5, int lon5, String name, double radiusM) {
        cat.add(categoryIndex);
        pos.add(new int[] {lat5, lon5});
        names.add(name);
        radii.add(radiusM);
    }

    /** Unlock radius of place i (regions differ: an island vs a library). */
    double radius(int i) { return radii.get(i); }

    /** A place for display (radar / map). */
    static final class Place {
        final Category category;
        final double lat, lon, distanceM, bearingDeg;
        final String name;
        double radiusM;
        Place(Category c, double lat, double lon, String name, double d, double b) {
            category = c; this.lat = lat; this.lon = lon; this.name = name; distanceM = d; bearingDeg = b; radiusM = c.radiusM;
        }
    }

    /**
     * Places within {@code radiusM} of a position, nearest first, limited to {@code perCategory}
     * per category (cafes alone run into the thousands in a city).
     * @param only categories to include, or null for all
     */
    List<Place> near(double lat, double lon, double radiusM, int perCategory, Set<String> only) {
        double cosLat = Math.cos(Math.toRadians(lat));
        List<Place> all = new ArrayList<>();
        for (int i = 0; i < pos.size(); i++) {
            Category c = ALL[cat.get(i)];
            if (only != null && !only.contains(c.id)) continue;
            int[] p = pos.get(i);
            double dLat = p[0] / 1e5 - lat;
            if (Math.abs(dLat) * 111_000 > radiusM) continue;
            double dLon = (p[1] / 1e5 - lon) * cosLat;
            double d = Math.sqrt(dLat * dLat + dLon * dLon) * 111_195;
            if (d > radiusM) continue;
            double b = (Math.toDegrees(Math.atan2(dLon, dLat)) + 360) % 360; // 0 = north, 90 = east
            String n = names.get(i);
            Place pl = new Place(c, p[0] / 1e5, p[1] / 1e5, n.isEmpty() ? c.place : n, d, b);
            pl.radiusM = radii.get(i);
            all.add(pl);
        }
        all.sort((a, b) -> Double.compare(a.distanceM, b.distanceM));
        int[] per = new int[ALL.length];
        List<Place> out = new ArrayList<>();
        for (Place p : all) if (per[index(p.category.id)]++ < perCategory) out.add(p);
        return out;
    }

    /** Every place of the given categories (for the map layer). */
    List<Place> all(Set<String> only) {
        List<Place> out = new ArrayList<>();
        for (int i = 0; i < pos.size(); i++) {
            Category c = ALL[cat.get(i)];
            if (only != null && !only.contains(c.id)) continue;
            int[] p = pos.get(i);
            String n = names.get(i);
            out.add(new Place(c, p[0] / 1e5, p[1] / 1e5, n.isEmpty() ? c.place : n, 0, 0));
        }
        return out;
    }

    /** Compass word for a bearing, e.g. 45 → "북동". */
    static String direction(double bearingDeg) {
        String[] d = {"북", "북동", "동", "남동", "남", "남서", "서", "북서"};
        return d[(int) Math.round(((bearingDeg % 360) + 360) % 360 / 45) % 8];
    }

    /** Categories (not yet in {@code unlocked}) whose radius covers this position. */
    List<Hit> check(double lat, double lon, Set<String> unlocked) {
        double[] best = new double[ALL.length];
        int[] bestIdx = new int[ALL.length];
        java.util.Arrays.fill(best, Double.MAX_VALUE);
        java.util.Arrays.fill(bestIdx, -1);
        double cosLat = Math.cos(Math.toRadians(lat));
        for (int i = 0; i < pos.size(); i++) {
            int ci = cat.get(i);
            if (unlocked.contains(ALL[ci].id)) continue;
            int[] p = pos.get(i);
            double dLat = p[0] / 1e5 - lat;
            // Quick reject before the distance (1° ≈ 111 km)
            double r = radii.get(i);
            if (Math.abs(dLat) * 111_000 > r) continue;
            double dLon = (p[1] / 1e5 - lon) * cosLat;
            double d = Math.sqrt(dLat * dLat + dLon * dLon) * 111_195;
            if (d <= r && d < best[ci]) { best[ci] = d; bestIdx[ci] = i; }
        }
        List<Hit> hits = new ArrayList<>();
        for (int ci = 0; ci < ALL.length; ci++) {
            if (bestIdx[ci] < 0) continue;
            String n = names.get(bestIdx[ci]);
            hits.add(new Hit(ALL[ci], n.isEmpty() ? ALL[ci].place : n, best[ci]));
        }
        return hits;
    }
}
