package dev.homesev.sounddrive;

/** Host-side checks for the Korean voice command parser. */
public final class VoiceCommandsTest {
    private static int pass, fail;

    public static void main(String[] args) {
        t("빗소리 켜 줘", "ON rain");
        t("비 켜줘", "ON rain");
        t("비행기 소리 틀어줘", "ON u_airport");
        t("파도 소리 꺼 줘", "OFF waves");
        t("모닥불 꺼", "OFF fire");
        t("새소리 들려줘", "ON birds");
        t("새 오른쪽으로 옮겨", "MOVE birds right");
        t("모닥불 앞으로", "MOVE fire front");
        t("천둥 멀리", "MOVE thunder far");
        t("우주배경복사 켜줘", "ON cmb");
        t("토성 소리 틀어", "ON saturn");
        t("앰비언트 꺼줘", "OFF pad");
        t("수면 타이머 삼십 분", "SLEEP 30");
        t("30분 뒤에 꺼줘", "SLEEP 30");
        t("한 시간 뒤에 꺼 줘", "SLEEP 60");
        t("타이머 취소", "SLEEP 0");
        t("수면 타이머 켜줘", "SLEEP 30");
        t("행성 모드", "MOTION rotate");
        t("궤도 모드 켜줘", "MOTION rotate");
        t("회전 시켜줘", "MOTION rotate");
        t("움직임 멈춰", "MOTION none");
        t("캠핑 분위기로", "PRESET fire,birds,pad");
        t("바닷가 틀어줘", "PRESET waves,birds,wind");
        t("소리 줄여", "VOL_DOWN");
        t("볼륨 키워줘", "VOL_UP");
        t("좀 더 크게", "VOL_UP");
        t("다 꺼", "STOP_ALL");
        t("전부 꺼줘", "STOP_ALL");
        t("정지", "STOP_ALL");
        t("재생해줘", "PLAY");
        t("근처에 열 수 있는 소리 있어?", "NEARBY");
        t("어디 가면 소리 열려", "NEARBY");
        t("거기로 안내해 줘", "NAVIGATE");
        t("제일 가까운 도서관으로 안내해줘", "NAVIGATE library");
        t("근처 절로 데려다 줘", "NAVIGATE temple");
        t("서울역으로 가자", "NAVIGATE station");
        t("야구장 가자", "NAVIGATE baseball");
        t("춘천으로 안내해 줘", "NAVIGATE region:chuncheon");
        t("우도 가자", "NAVIGATE region:udo");
        t("이번 계절 명소 어디야", "SEASON");
        t("가을 명소 알려줘", "SEASON");
        t("오늘 날씨 어때", "UNKNOWN");
        t("절대 안 돼", "UNKNOWN");
        t("새로운 거 틀어줘", "PLAY");
        System.out.println(pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    private static void t(String said, String want) {
        String got = VoiceCommands.parse(said).toString();
        if (got.equals(want)) pass++;
        else { fail++; System.out.println("FAIL: \"" + said + "\" → " + got + " (want " + want + ")"); }
    }
}
