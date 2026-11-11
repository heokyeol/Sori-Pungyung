package dev.homesev.sounddrive;

import java.util.HashSet;
import java.util.Set;

/** Host-side checks for region sounds. */
public final class RegionsTest {
    private static int pass, fail;

    public static void main(String[] args) {
        Set<String> none = new HashSet<>();
        check("20 regions", Regions.ALL.length == 20);
        check("인천공항 터미널 → 영종도", has(Regions.check(37.4490, 126.4510, none), "yeongjong"));
        check("여수 엑스포역 → 여수", has(Regions.check(34.7531, 127.7480, none), "yeosu"));
        check("철원 고석정 → 철원", has(Regions.check(38.1890, 127.2720, none), "cheorwon"));
        check("반포대교 → 서울 한강", has(Regions.check(37.5107, 126.9960, none), "hangang"));
        check("서울시청 → 한강 아님", Regions.check(37.5665, 126.9780, none).isEmpty());
        check("성산일출봉 → 제주도", has(Regions.check(33.4581, 126.9425, none), "jeju"));
        check("우도 → 우도 + 제주도", has(Regions.check(33.5067, 126.9557, none), "udo") && has(Regions.check(33.5067, 126.9557, none), "jeju"));
        check("서귀포 중문 → 제주도", has(Regions.check(33.2500, 126.4120, none), "jeju"));
        check("해운대 → 부산", has(Regions.check(35.1587, 129.1604, none), "busan"));
        check("대전 → 없음", Regions.check(36.3504, 127.3845, none).isEmpty());
        Set<String> done = new HashSet<>(java.util.Collections.singleton("yeongjong"));
        check("방문한 지역은 다시 안 열림", Regions.check(37.4490, 126.4510, done).isEmpty());
        java.util.List<Regions.Region> near = Regions.nearest(37.5665, 126.9780, none, 3);
        check("시청에서 가까운 지역 = 한강", near.get(0).id.equals("hangang"));
        System.out.printf("   시청에서 가까운 지역: %s, %s, %s%n", near.get(0).name, near.get(1).name, near.get(2).name);
        check("sound ids", java.util.Arrays.stream(Regions.ALL).allMatch(r -> r.sound.startsWith("r_") || r.sound.startsWith("u_")));
        check("each sound in one region", java.util.Arrays.stream(Regions.ALL).map(r -> r.sound).distinct().count() == 20);
        System.out.println(pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    private static boolean has(java.util.List<Regions.Region> l, String id) {
        for (Regions.Region r : l) if (r.id.equals(id)) return true;
        return false;
    }

    private static void check(String name, boolean ok) {
        if (ok) pass++; else { fail++; System.out.println("FAIL: " + name); }
    }
}
