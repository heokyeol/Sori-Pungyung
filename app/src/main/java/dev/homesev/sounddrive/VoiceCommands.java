package dev.homesev.sounddrive;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a spoken Korean sentence into an app command. Pure Java (tested off-device).
 * Examples: "빗소리 켜 줘", "새 오른쪽으로", "수면 타이머 삼십 분", "행성 모드",
 * "소리 줄여", "다 꺼", "근처에 열 수 있는 소리 있어?"
 */
final class VoiceCommands {
    enum Type { ON, OFF, MOVE, VOL_UP, VOL_DOWN, SLEEP, MOTION, PRESET, PLAY, STOP_ALL, NEARBY, SEASON, NAVIGATE, UNKNOWN }

    static final class Command {
        final Type type;
        final String sound;   // source id for ON/OFF/MOVE
        final String arg;     // MOVE: left/right/front/back/near/far; PRESET: ids; MOTION: orbit/rotate/none; NAVIGATE: place category or null
        final int minutes;    // SLEEP
        Command(Type t, String sound, String arg, int minutes) { type = t; this.sound = sound; this.arg = arg; this.minutes = minutes; }
        @Override public String toString() { return type + (sound != null ? " " + sound : "") + (arg != null ? " " + arg : "") + (type == Type.SLEEP ? " " + minutes : ""); }
    }

    /** Spoken words → source id. Longer words first so "빗소리" wins over "비". */
    static final String[][] NAMES = {
        {"우주배경복사", "cmb"}, {"우주 배경 복사", "cmb"}, {"배경복사", "cmb"}, {"우주", "cmb"},
        {"빗소리", "rain"}, {"비 소리", "rain"}, {"빗물", "rain"},
        {"파도", "waves"}, {"바닷소리", "waves"}, {"바다", "waves"},
        {"바람", "wind"},
        {"모닥불", "fire"}, {"캠프파이어", "fire"}, {"장작", "fire"}, {"불", "fire"},
        {"새소리", "birds"}, {"새 소리", "birds"}, {"새", "birds"},
        {"앰비언트", "pad"}, {"엠비언트", "pad"}, {"음악", "pad"},
        {"천둥번개", "thunder"}, {"천둥", "thunder"}, {"번개", "thunder"},
        {"토성", "saturn"}, {"목성", "jupiter"},
        {"비행기", "u_airport"}, {"기내", "u_airport"},
        {"도서관", "u_library"},
        {"풍경", "u_temple"}, {"사찰", "u_temple"}, {"절", "u_temple"},
        {"기차", "u_station"},
        {"계곡", "u_valley"}, {"물소리", "u_valley"},
        {"갈매기", "u_harbor"}, {"항구", "u_harbor"},
        {"카페", "u_cafe"},
        {"야구", "u_baseball"},
        {"비", "rain"},
    };

    static final String[][] PRESETS = {
        {"빗속 드라이브", "rain,wind,pad"}, {"빗속", "rain,wind,pad"},
        {"바닷가", "waves,birds,wind"}, {"해변", "waves,birds,wind"},
        {"캠핑", "fire,birds,pad"},
    };

    /** Region words → "region:<id>" (for "춘천으로 안내해 줘"). Checked before place categories. */
    static final String[][] REGION_WORDS = {
        {"영종도", "yeongjong"}, {"한강", "hangang"}, {"춘천", "chuncheon"}, {"속초", "sokcho"}, {"설악", "sokcho"},
        {"강릉", "gangneung"}, {"철원", "cheorwon"}, {"단양", "danyang"}, {"태안", "taean"}, {"전주", "jeonju"},
        {"곡성", "gokseong"}, {"순천", "suncheon"}, {"여수", "yeosu"}, {"남해", "namhae"}, {"경주", "gyeongju"},
        {"안동", "andong"}, {"하회", "andong"}, {"부산", "busan"}, {"통영", "tongyeong"}, {"울릉", "ulleung"},
        {"우도", "udo"}, {"제주", "jeju"},
    };

    /** Spoken place words → unlock category (for "가까운 도서관으로 안내해 줘"). */
    static final String[][] PLACES = {
        {"공항", "airport"}, {"도서관", "library"}, {"사찰", "temple"}, {"절", "temple"}, {"기차역", "station"}, {"역", "station"},
        {"계곡", "valley"}, {"국립공원", "valley"}, {"바닷가", "harbor"}, {"해변", "harbor"}, {"항구", "harbor"}, {"바다", "harbor"},
        {"카페", "cafe"}, {"야구장", "baseball"},
    };

    private static final Pattern DIGITS = Pattern.compile("(\\d+)\\s*분");
    private static final Pattern HOURS = Pattern.compile("(\\d+|한|두|세)\\s*시간");

    static Command parse(String raw) {
        if (raw == null) return unknown();
        String t = raw.replaceAll("[.?!,~]", " ").replaceAll("\\s+", " ").trim();
        String n = t.replace(" ", "");
        if (n.isEmpty()) return unknown();

        // Guide me there (built-in navigation)
        if (n.contains("안내") || n.contains("데려다") || n.contains("길찾") || n.contains("가자") || n.contains("가줘")) {
            String place = null;
            for (String[] r : REGION_WORDS) if (n.contains(r[0])) return new Command(Type.NAVIGATE, null, "region:" + r[1], 0);
            for (String[] p : PLACES) if (n.contains(p[0]) && !(p[0].equals("절") && n.contains("절대")) && !(p[0].equals("역") && !n.contains("역으로") && !n.contains("기차역"))) { place = p[1]; break; }
            return new Command(Type.NAVIGATE, null, place, 0);
        }

        // This season's landmark
        if (n.contains("명소") || (n.contains("계절") && !n.contains("타이머")))
            return new Command(Type.SEASON, null, null, 0);

        // Where to unlock sounds
        if ((n.contains("근처") || n.contains("주변") || n.contains("어디")) && (n.contains("열") || n.contains("해금") || n.contains("소리"))
                && !n.contains("켜") && !n.contains("꺼"))
            return new Command(Type.NEARBY, null, null, 0);

        // Sleep timer
        if (n.contains("타이머") || n.contains("뒤에꺼") || n.contains("후에꺼") || n.contains("있다가꺼") || n.contains("자고싶")) {
            if (n.contains("타이머") && (n.contains("꺼") || n.contains("취소") || n.contains("해제") || n.contains("그만"))) return new Command(Type.SLEEP, null, null, 0);
            int m = minutes(n);
            if (m > 0) return new Command(Type.SLEEP, null, null, m);
            if (n.contains("타이머")) return new Command(Type.SLEEP, null, null, 30);
        }
        int bareMinutes = minutes(n);
        if (bareMinutes > 0 && (n.contains("뒤") || n.contains("후"))) return new Command(Type.SLEEP, null, null, bareMinutes);

        // Motion
        if (n.contains("행성") || n.contains("궤도")) return new Command(Type.MOTION, null, n.contains("꺼") || n.contains("멈") ? "none" : "orbit", 0);
        if (n.contains("회전") || n.contains("빙글") || n.contains("돌려")) return new Command(Type.MOTION, null, n.contains("꺼") || n.contains("멈") ? "none" : "rotate", 0);
        if (n.contains("움직임") && (n.contains("꺼") || n.contains("멈") || n.contains("그만"))) return new Command(Type.MOTION, null, "none", 0);

        // Presets
        for (String[] p : PRESETS) if (n.contains(p[0].replace(" ", ""))) return new Command(Type.PRESET, null, p[1], 0);

        // Volume
        boolean volWord = n.contains("볼륨") || n.contains("소리") || n.contains("음량");
        if (n.contains("줄여") || n.contains("작게") || (volWord && n.contains("낮춰"))) return new Command(Type.VOL_DOWN, null, null, 0);
        if (n.contains("키워") || n.contains("크게") || (volWord && n.contains("높여"))) return new Command(Type.VOL_UP, null, null, 0);

        String sound = sound(n);
        boolean off = n.contains("꺼") || n.contains("끄") || n.contains("그만") || n.contains("멈춰") || n.contains("빼");
        boolean on = n.contains("켜") || n.contains("틀어") || n.contains("들려") || n.contains("재생") || n.contains("넣어") || n.contains("추가");

        if (sound != null) {
            String dir = direction(n);
            if (dir != null && !off) return new Command(Type.MOVE, sound, dir, 0);
            if (off) return new Command(Type.OFF, sound, null, 0);
            return new Command(Type.ON, sound, null, 0); // naming a sound alone turns it on
        }
        if (off && (n.contains("다") || n.contains("모두") || n.contains("전부") || n.length() <= 4 || n.contains("정지")))
            return new Command(Type.STOP_ALL, null, null, 0);
        if (n.contains("정지")) return new Command(Type.STOP_ALL, null, null, 0);
        if (on || n.contains("시작")) return new Command(Type.PLAY, null, null, 0);
        return unknown();
    }

    private static Command unknown() { return new Command(Type.UNKNOWN, null, null, 0); }

    static String sound(String n) {
        for (String[] w : NAMES) {
            String key = w[0].replace(" ", "");
            int i = n.indexOf(key);
            if (i < 0) continue;
            // One-syllable names are easy to mishear inside other words:
            // 비디오, 새로/새벽, 불러, 절대 — skip those.
            if (key.length() == 1) {
                String after = n.substring(i + 1);
                if (key.equals("비") && after.startsWith("디")) continue;
                if (key.equals("새") && (after.startsWith("로") || after.startsWith("벽"))) continue;
                if (key.equals("불") && after.startsWith("러")) continue;
                if (key.equals("절") && after.startsWith("대")) continue;
            }
            return w[1];
        }
        return null;
    }

    static String direction(String n) {
        if (n.contains("왼")) return "left";
        if (n.contains("오른")) return "right";
        if (n.contains("앞")) return "front";
        if (n.contains("뒤")) return "back";
        if (n.contains("가까이") || n.contains("가깝게")) return "near";
        if (n.contains("멀리") || n.contains("멀게")) return "far";
        return null;
    }

    /** Minutes from "30분", "삼십 분", "한 시간", "1시간 반". 0 if none. */
    static int minutes(String n) {
        Matcher d = DIGITS.matcher(n);
        if (d.find()) return Integer.parseInt(d.group(1));
        Matcher h = HOURS.matcher(n);
        if (h.find()) {
            String g = h.group(1);
            int hrs = g.equals("한") ? 1 : g.equals("두") ? 2 : g.equals("세") ? 3 : Integer.parseInt(g);
            return hrs * 60 + (n.contains("반") ? 30 : 0);
        }
        String[][] words = {{"구십분", "90"}, {"육십분", "60"}, {"사십오분", "45"}, {"삼십분", "30"}, {"이십분", "20"},
                {"십오분", "15"}, {"십분", "10"}, {"오분", "5"}, {"반시간", "30"}};
        for (String[] w : words) if (n.contains(w[0])) return Integer.parseInt(w[1]);
        return 0;
    }
}
