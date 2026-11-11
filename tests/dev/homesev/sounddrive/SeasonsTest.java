package dev.homesev.sounddrive;

import java.util.HashSet;
import java.util.Set;

/** Host-side checks for seasonal landmark unlocks. */
public final class SeasonsTest {
    private static int pass, fail;

    public static void main(String[] args) {
        check("months → seasons", Seasons.season(3) == 0 && Seasons.season(8) == 1 && Seasons.season(11) == 2 && Seasons.season(12) == 3 && Seasons.season(2) == 3);
        Set<String> none = new HashSet<>();
        // 양양 죽도해변 in July → surfing; in January → off season
        Seasons.Check summer = Seasons.check(37.9714, 128.7598, 7, none);
        check("양양 7월 → 서핑 열림", summer.unlock.size() == 1 && summer.unlock.get(0).id.equals("yangyang"));
        Seasons.Check winter = Seasons.check(37.9714, 128.7598, 1, none);
        check("양양 1월 → 계절 아님 안내", winter.unlock.isEmpty() && winter.offSeason.size() == 1);
        // 대관령 in December → blizzard
        check("대관령 12월 → 눈보라", has(Seasons.check(37.6880, 128.7570, 12, none).unlock, "daegwallyeong"));
        // 마이산 in November (today) → leaves
        check("마이산 11월 → 낙엽", has(Seasons.check(35.7537, 127.4117, 11, none).unlock, "maisan"));
        // 서울시청: nothing
        Seasons.Check seoul = Seasons.check(37.5665, 126.9780, 11, none);
        check("서울 → 없음", seoul.unlock.isEmpty() && seoul.offSeason.isEmpty());
        // already unlocked is skipped
        Set<String> done = new HashSet<>(java.util.Collections.singleton("maisan"));
        check("이미 연 곳은 다시 안 열림", Seasons.check(35.7537, 127.4117, 11, done).unlock.isEmpty());
        // nearest in-season spot from Seoul in November is an autumn spot
        Seasons.Spot n = Seasons.nearestInSeason(37.5665, 126.9780, 11, none);
        check("가을 가장 가까운 명소", n != null && n.season == 2);
        System.out.printf("   서울에서 가까운 가을 명소: %s %.0fkm %s쪽%n", n.place, Seasons.distance(n, 37.5665, 126.9780) / 1000,
                Unlocks.direction(Seasons.bearing(n, 37.5665, 126.9780)));
        check("spots have recordings ids", java.util.Arrays.stream(Seasons.ALL).allMatch(s -> s.sound.startsWith("s_")));
        System.out.println(pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    private static boolean has(java.util.List<Seasons.Spot> l, String id) {
        for (Seasons.Spot s : l) if (s.id.equals(id)) return true;
        return false;
    }

    private static void check(String name, boolean ok) {
        if (ok) pass++; else { fail++; System.out.println("FAIL: " + name); }
    }
}
