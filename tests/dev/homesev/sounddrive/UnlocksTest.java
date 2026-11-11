package dev.homesev.sounddrive;

/** Host-side checks for offline location unlocks, using the real places.tsv. */
public final class UnlocksTest {
    private static int pass, fail;

    public static void main(String[] args) throws Exception {
        Unlocks u = new Unlocks();
        try (java.io.Reader r = new java.io.InputStreamReader(new java.io.FileInputStream(args[0]), java.nio.charset.StandardCharsets.UTF_8)) {
            u.load(r);
        }
        System.out.println("   places: " + u.size());
        for (Unlocks.Category c : Unlocks.ALL) System.out.println("   " + c.id + " " + u.count(c.id));
        check("all categories have places", java.util.Arrays.stream(Unlocks.ALL).allMatch(c -> u.count(c.id) > 0));

        java.util.Set<String> none = new java.util.HashSet<>();
        check("25 hidden landmarks loaded", u.size() == 25);
        // 영종도 is a region now (Regions), not a hidden landmark
        check("인천공항 터미널 → no hidden landmark", !has(u.check(37.4490, 126.4510, none), "airport"));
        check("제주공항 → airport", has(u.check(33.5071, 126.4916, none), "airport"));
        check("김포공항 → airport", has(u.check(37.5587, 126.7945, none), "airport"));
        check("의왕 철도박물관 → station", has(u.check(37.3140, 126.9508, none), "station"));
        check("잠실야구장 → baseball", has(u.check(37.5122, 127.0719, none), "baseball"));
        check("강릉 안목해변 → cafe", has(u.check(37.7736, 128.9454, none), "cafe"));
        // Ordinary places no longer unlock (only curated regions do)
        check("서울역 → nothing now", u.check(37.5547, 126.9707, none).isEmpty());
        check("바다 한가운데 → nothing", u.check(36.0, 124.8, none).isEmpty());
        java.util.Set<String> done = new java.util.HashSet<>(java.util.Collections.singleton("airport"));
        check("unlocked airport not reported again", !has(u.check(37.5587, 126.7945, done), "airport"));
        check("within(): 잠실 stamp", u.within(37.5122, 127.0719).contains("잠실야구장"));
        // Radar query: nearest first, per-category cap, filter, bearing.
        java.util.List<Unlocks.Place> near = u.near(37.5665, 126.9780, RadarView_RANGE, 3, null);
        boolean sorted = true;
        for (int i = 1; i < near.size(); i++) if (near.get(i).distanceM < near.get(i - 1).distanceM) sorted = false;
        check("near: sorted by distance", sorted && !near.isEmpty());
        check("near: baseball capped at 3", near.stream().filter(p -> p.category.id.equals("baseball")).count() <= 3);
        java.util.Set<String> onlyBall = new java.util.HashSet<>(java.util.Collections.singleton("baseball"));
        java.util.List<Unlocks.Place> balls = u.near(37.5665, 126.9780, RadarView_RANGE, 3, onlyBall);
        check("near: filter keeps only baseball", !balls.isEmpty() && balls.stream().allMatch(p -> p.category.id.equals("baseball")));
        System.out.printf("   시청→가까운 야구장 %s %s쪽 %.1fkm (반경 %.0fm)%n", balls.get(0).name, Unlocks.direction(balls.get(0).bearingDeg), balls.get(0).distanceM / 1000, balls.get(0).radiusM);
        check("per-region radius kept", balls.get(0).radiusM == 500);
        check("direction words", Unlocks.direction(0).equals("북") && Unlocks.direction(90).equals("동") && Unlocks.direction(225).equals("남서"));
        check("all(): filter", u.all(onlyBall).size() == u.count("baseball"));

        // Speed: one check over all places must be cheap (runs every few seconds on device)
        long t0 = System.nanoTime();
        for (int i = 0; i < 100; i++) u.check(37.5 + i * 0.001, 127.0, none);
        double ms = (System.nanoTime() - t0) / 1e6 / 100;
        check("check is fast (" + String.format("%.2f", ms) + " ms)", ms < 20);

        System.out.println(pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    private static final double RadarView_RANGE = 300_000;

    private static boolean has(java.util.List<Unlocks.Hit> hits, String id) {
        for (Unlocks.Hit h : hits) if (h.category.id.equals(id)) return true;
        return false;
    }

    private static void check(String name, boolean ok) {
        if (ok) pass++; else { fail++; System.out.println("FAIL: " + name); }
    }
}
