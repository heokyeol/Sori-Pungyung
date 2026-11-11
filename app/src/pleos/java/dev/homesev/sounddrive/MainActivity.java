package dev.homesev.sounddrive;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;

import java.util.Locale;

/** Pleos (car) build: vehicle speed/gear, window link and mood-light gradient. */
public final class MainActivity extends BaseActivity implements VehicleLink.Listener {
    private static final String CAR_SPEED = "android.car.permission.CAR_SPEED";
    private static final long MOOD_TICK_MS = 250; // at most 4 light updates per second
    private static final float MOOD_LEVEL = 0.4f;
    private static final float WINDOW_GAIN = 0.7f; // about -3 dB while a window is open

    private static final String MIC = "android.permission.RECORD_AUDIO";
    private VehicleLink vehicle;
    private VoiceControl voice;
    private Navigator navigator;
    /** The place mentioned in the last "근처에 열 수 있는 소리 있어?" answer, for "거기로 안내해 줘". */
    private Unlocks.Place lastSuggested;
    /** Last suggested seasonal landmark (lat, lon, name), for "거기로 안내해 줘". */
    private double[] lastSpot;
    private String lastSpotName;
    private Button micBtn;
    private String voiceStatus = "";
    private Switch speedComp, moodSwitch, windowSwitch;
    private boolean driving;
    private Float speedKmh;
    private String gear;
    private Boolean windowOpen;
    /** Number of ambient light zones (-1 = not known yet, 0 = none). */
    private int moodZones = -1;
    private float[][] moodPoints;
    private int[] moodSent;
    private float moodLevelSent = -1;

    @Override protected boolean isCar() { return true; }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        vehicle = new VehicleLink(this, main, this);
        // Duck the sounds while the voice answers.
        voice = new VoiceControl(this, main, on -> player.engine.mixer.duck = on ? 0.3f : 1f);
        navigator = new Navigator(this);
        if (checkSelfPermission(CAR_SPEED) == PackageManager.PERMISSION_GRANTED) vehicle.startSpeed();
        main.post(moodTicker);
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (vehicle != null && checkSelfPermission(CAR_SPEED) == PackageManager.PERMISSION_GRANTED) vehicle.startSpeed();
        refresh();
    }

    @Override protected boolean canOpenPlaces() { return !driving; }

    @Override protected String[] extraPermissions() { return new String[] {CAR_SPEED, MIC}; }

    // ── Voice ──────────────────────────────────────────────────────────────
    private void listen() {
        if (checkSelfPermission(MIC) != PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[] {MIC}, 9); return; }
        voice.listen(new VoiceControl.Heard() {
            @Override public void onListening(boolean on) {
                micBtn.setText(on ? "🎙 듣고 있어요…" : "🎤 음성 명령");
                if (on) player.engine.mixer.duck = 0.3f; else player.engine.mixer.duck = 1f;
            }
            @Override public void onPartial(String text) { voiceStatus = "\"" + text + "\""; refresh(); }
            @Override public void onText(String text) { voiceStatus = "\"" + text + "\""; refresh(); run(VoiceCommands.parse(text)); }
            @Override public void onError(String why) { voiceStatus = why; refresh(); voice.say(why); }
        });
    }

    /** Carry out a spoken command and answer briefly by voice. */
    private void run(VoiceCommands.Command c) {
        SoundSource s = c.sound == null ? null : sourceById(c.sound);
        if (s != null && s.locked) {
            Unlocks.Category cat = Unlocks.bySound(s.id);
            voice.say("아직 열리지 않은 소리예요. " + (cat != null ? cat.hint : ""));
            return;
        }
        switch (c.type) {
            case ON: setSound(s, true); voice.say(s.name + " 켰어요"); break;
            case OFF: setSound(s, false); voice.say(s.name + " 껐어요"); break;
            case MOVE: {
                float r = Math.max(0.5f, (float) Math.hypot(s.x, s.y));
                switch (c.arg) {
                    case "left": s.x = -r; s.y = 0; break;
                    case "right": s.x = r; s.y = 0; break;
                    case "front": s.x = 0; s.y = -r; break;
                    case "back": s.x = 0; s.y = r; break;
                    case "near": { float k2 = 0.25f / Math.max(0.01f, (float) Math.hypot(s.x, s.y)); s.x *= k2; s.y *= k2; break; }
                    case "far": { float k2 = 0.95f / Math.max(0.01f, (float) Math.hypot(s.x, s.y)); s.x *= k2; s.y *= k2; break; }
                }
                setSound(s, true);
                String where = c.arg.equals("left") ? "왼쪽으로" : c.arg.equals("right") ? "오른쪽으로" : c.arg.equals("front") ? "앞으로"
                        : c.arg.equals("back") ? "뒤로" : c.arg.equals("near") ? "가까이" : "멀리";
                voice.say(s.name + " " + where + " 옮겼어요");
                break;
            }
            case VOL_UP: case VOL_DOWN: {
                float m = player.engine.mixer.master + (c.type == VoiceCommands.Type.VOL_UP ? 0.15f : -0.15f);
                player.engine.mixer.master = Math.max(0.05f, Math.min(1f, m));
                player.save();
                buildUi();
                voice.say(c.type == VoiceCommands.Type.VOL_UP ? "크게 했어요" : "작게 했어요");
                break;
            }
            case SLEEP:
                player.setSleep(c.minutes);
                voice.say(c.minutes == 0 ? "수면 타이머를 껐어요" : c.minutes + "분 뒤에 천천히 꺼질게요");
                break;
            case MOTION: {
                int m = c.arg.equals("orbit") ? Mixer.MOTION_ORBIT : c.arg.equals("rotate") ? Mixer.MOTION_ROTATE : Mixer.MOTION_NONE;
                setMotion(m);
                voice.say(m == Mixer.MOTION_ORBIT ? "행성 궤도로 돌게요" : m == Mixer.MOTION_ROTATE ? "천천히 회전할게요" : "움직임을 멈췄어요");
                break;
            }
            case PRESET: applyPreset(c.arg.split(",")); voice.say("분위기를 바꿨어요"); break;
            case PLAY: player.play(); voice.say("재생할게요"); break;
            case STOP_ALL: player.stop(); voice.say("모두 껐어요"); break;
            case NEARBY: lastSpot = null; voice.say(nearbyAnswer()); break;
            case SEASON: voice.say(seasonAnswer()); break;
            case NAVIGATE: navigateTo(c.arg); break;
            default: voice.say("잘 모르겠어요. 빗소리 켜 줘, 수면 타이머 삼십 분처럼 말해 주세요"); break;
        }
    }

    /** "북동쪽 1.2킬로미터에 ○○사가 있어요. 가면 사찰 풍경 소리가 열려요." */
    private String nearbyAnswer() {
        double[] me = player.lastLocation();
        if (me == null) return "아직 위치를 모르겠어요";
        if (!player.placesLoaded()) return "장소 목록을 읽는 중이에요";
        java.util.Set<String> locked = new java.util.HashSet<>();
        for (Unlocks.Category cat : Unlocks.ALL) if (player.unlockInfo(cat.id) == null) locked.add(cat.id);
        if (locked.isEmpty()) return "모든 소리를 이미 열었어요";
        java.util.List<Unlocks.Place> near = player.unlocks.near(me[0], me[1], 600_000, 1, locked);
        if (near.isEmpty()) return "가까운 곳에 열 수 있는 지역이 없어요";
        Unlocks.Place p = near.get(0);
        lastSuggested = p;
        String sound = p.category.sound;
        SoundSource src = sourceById(p.category.sound);
        if (src != null) sound = src.name;
        String dist = p.distanceM >= 10_000 ? Math.round(p.distanceM / 1000) + "킬로미터"
                : p.distanceM >= 1000 ? String.format(Locale.KOREA, "%.1f킬로미터", p.distanceM / 1000) : Math.round(p.distanceM / 10) * 10 + "미터";
        return Unlocks.direction(p.bearingDeg) + "쪽 " + dist + "에 " + p.name + subject(p.name) + " 있어요. 가면 " + sound + " 소리가 열려요";
    }

    /** "이번 가을엔 정선 민둥산이 가장 가까워요. 동쪽 162킬로미터예요. 가면 억새 바람 소리가 열려요." */
    private String seasonAnswer() {
        double[] me = player.lastLocation();
        int month = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1;
        String season = Seasons.SEASON_NAMES[Seasons.season(month)];
        if (me == null) return "아직 위치를 모르겠어요";
        Seasons.Spot sp = Seasons.nearestInSeason(me[0], me[1], month, player.spotsUnlocked());
        if (sp == null) return "이번 " + season + " 명소는 모두 열었어요";
        lastSpot = sp.points[0];
        lastSpotName = sp.place;
        SoundSource src = sourceById(sp.sound);
        double km = Seasons.distance(sp, me[0], me[1]) / 1000;
        return "이번 " + season + "엔 " + sp.place + subject(sp.place) + " 가장 가까워요. " + Unlocks.direction(Seasons.bearing(sp, me[0], me[1]))
                + "쪽 " + Math.round(km) + "킬로미터예요. 가면 " + (src != null ? src.name : "새") + " 소리가 열려요";
    }

    /** At a landmark out of season: say it (safe while driving) besides the usual notice. */
    @Override public void onOffSeason(String message) {
        super.onOffSeason(message);
        voice.say(message);
    }

    /** Voice "안내해 줘": a named place type (nearest of that kind), else the last suggestion, else the nearest locked place. */
    private void navigateTo(String category) {
        double[] me = player.lastLocation();
        Unlocks.Place target = null;
        if (category != null) {
            if (me == null) { voice.say("아직 위치를 모르겠어요"); return; }
            java.util.List<Unlocks.Place> near = player.unlocks.near(me[0], me[1], 600_000, 1, java.util.Collections.singleton(category));
            if (near.isEmpty()) { voice.say("가까운 곳에 그런 지역이 없어요"); return; }
            target = near.get(0);
        } else if (lastSpot != null) {
            final double[] p = lastSpot; final String name = lastSpotName;
            navigator.go(p[0], p[1], name, err -> main.post(() -> voice.say(err != null ? err : name + towards(name) + " 안내할게요")));
            return;
        } else if (lastSuggested != null) {
            target = lastSuggested;
        } else {
            String answer = nearbyAnswer(); // also sets lastSuggested
            if (lastSuggested == null) { voice.say(answer); return; }
            target = lastSuggested;
        }
        final Unlocks.Place t = target;
        navigator.go(t.lat, t.lon, t.name, err -> main.post(() -> voice.say(err != null ? err : t.name + towards(t.name) + " 안내할게요")));
    }

    /** "으로" after a final consonant except ㄹ, "로" otherwise ("사찰로", "공항으로"). */
    private static String towards(String word) {
        if (word == null || word.isEmpty()) return "으로";
        char c = word.charAt(word.length() - 1);
        if (c < 0xAC00 || c > 0xD7A3) return "으로";
        int jong = (c - 0xAC00) % 28;
        return jong == 0 || jong == 8 ? "로" : "으로";
    }

    /** Korean subject particle: 이 after a final consonant, 가 otherwise ("사찰이", "카페가"). */
    private static String subject(String word) {
        if (word == null || word.isEmpty()) return "이";
        char c = word.charAt(word.length() - 1);
        if (c < 0xAC00 || c > 0xD7A3) return "이";
        return (c - 0xAC00) % 28 != 0 ? "이" : "가";
    }

    private String pendingUnlock;

    /** Unlocks are also spoken, which is safe while driving. */
    @Override public void onUnlocked(String soundName, String placeName) {
        super.onUnlocked(soundName, placeName);
        voice.say(placeName + " 근처에서 " + soundName + " 소리가 열렸어요");
    }

    /** No pop-ups while driving: keep it in the status line and show it once parked. */
    @Override protected void announceUnlock(String message) {
        if (driving) { pendingUnlock = message; refresh(); }
        else super.announceUnlock(message);
    }

    @Override protected void onDestroy() {
        main.removeCallbacks(moodTicker);
        player.setWindowGain(1f);
        player.engine.mixer.extraDb = 0;
        vehicle.close(); // restores the original mood light
        voice.release();
        navigator.release();
        super.onDestroy();
    }

    @Override protected void buildUi() {
        super.buildUi();
        stage.setLocked(driving);
    }

    // ── Build-specific UI ──────────────────────────────────────────────────
    @Override protected void addExtras(LinearLayout panel) {
        micBtn = bigButton("🎤 음성 명령", 0xFF3D7BFF);
        micBtn.setOnClickListener(v -> listen());
        panel.addView(micBtn, matchWrap(px(16)));

        speedComp = switchRow("속도가 빠를수록 볼륨 보정 (도로 소음 대응)", player.prefs.getBoolean("speedComp", true));
        speedComp.setOnCheckedChangeListener((v, on) -> { saveExtras(); applyVehicle(); });
        panel.addView(speedComp, matchWrap(px(12)));

        moodSwitch = switchRow("무드등을 소리 배치에 맞추기", player.prefs.getBoolean("mood", false));
        moodSwitch.setOnCheckedChangeListener((v, on) -> { saveExtras(); moodTick(); refresh(); });
        panel.addView(moodSwitch, matchWrap(px(12)));

        windowSwitch = switchRow("창문을 열면 소리 조금 줄이기", player.prefs.getBoolean("windowLower", true));
        windowSwitch.setOnCheckedChangeListener((v, on) -> { saveExtras(); applyWindow(); refresh(); });
        panel.addView(windowSwitch, matchWrap(px(12)));
    }

    @Override protected void addDarkButtons(LinearLayout panel) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button withMood = bigButton("무드등 켜고 어둡게", 0xFF2A3446);
        withMood.setTextSize(19 * k);
        withMood.setOnClickListener(v -> setDark(true, true));
        Button fully = bigButton("완전히 어둡게", 0xFF2A3446);
        fully.setTextSize(19 * k);
        fully.setOnClickListener(v -> setDark(true, false));
        row.addView(withMood, weight());
        row.addView(fully, weight());
        panel.addView(row, matchWrap(px(12)));
    }

    @Override protected void onDarkChanged() { moodTick(); }

    private void saveExtras() {
        player.prefs.edit()
                .putBoolean("speedComp", speedComp.isChecked())
                .putBoolean("mood", moodSwitch.isChecked())
                .putBoolean("windowLower", windowSwitch.isChecked())
                .apply();
    }

    @Override protected String statusText() {
        if (speedComp == null) return "";
        String sp = speedKmh == null ? "속도 정보 없음" : String.format(Locale.KOREA, "%.0f km/h", speedKmh);
        String gr = gear == null ? "기어 정보 없음" : "기어 " + gear;
        float db = player.engine.mixer.extraDb;
        String mood = !moodSwitch.isChecked() ? "" : moodZones < 0 ? " · 무드등 확인 중" : moodZones == 0 ? " · 무드등 제어 불가"
                : moodZones == 1 ? " · 무드등 1구역(색 흐름)" : " · 무드등 " + moodZones + "구역";
        String win = Boolean.TRUE.equals(windowOpen) ? " · 창문 열림" + (windowSwitch.isChecked() ? "(소리 줄임)" : "") : "";
        String unlock = (pendingUnlock != null ? " · 🔓 새 소리 열림" : "") + (voiceStatus.isEmpty() ? "" : " · 🎤 " + voiceStatus);
        return " · " + sp + " · " + gr + (db > 0.05f ? String.format(Locale.KOREA, " · 볼륨 +%.1fdB", db) : "")
                + (driving ? " · 주행 중" : "") + mood + win + unlock;
    }

    // ── Vehicle ────────────────────────────────────────────────────────────
    @Override public void onVehicle(Float kmh, String g) {
        speedKmh = kmh;
        gear = g;
        applyVehicle();
    }

    private void applyVehicle() {
        boolean moving = speedKmh != null && speedKmh > 3f;
        driving = gear != null ? !"P".equals(gear) || moving : moving;
        player.setDriving(driving);
        if (!driving && pendingUnlock != null) { String m = pendingUnlock; pendingUnlock = null; super.announceUnlock(m); }
        stage.setLocked(driving);
        float kmh = speedKmh == null ? 0 : speedKmh;
        player.engine.mixer.extraDb = speedComp.isChecked() ? Math.min(6f, kmh / 100f * 6f) : 0f;
        refresh();
    }

    @Override public void onWindowOpen(Boolean anyOpen) {
        windowOpen = anyOpen;
        applyWindow();
        refresh();
    }

    private void applyWindow() {
        player.setWindowGain(windowSwitch.isChecked() && Boolean.TRUE.equals(windowOpen) ? WINDOW_GAIN : 1f);
    }

    // ── Mood light ─────────────────────────────────────────────────────────
    @Override public void onMoodZones(int count) {
        moodZones = count;
        moodPoints = count > 0 ? vehicle.zonePoints() : null;
        moodSent = null;
        refresh();
    }

    private final Runnable moodTicker = new Runnable() {
        @Override public void run() {
            moodTick();
            main.postDelayed(this, MOOD_TICK_MS);
        }
    };

    /**
     * Follow the stage while playing with the switch on. When the sleep timer ends or the
     * screen is fully dark, switch the light off (not back to the original, which may be
     * bright). Otherwise give the light back to its original state.
     */
    private void moodTick() {
        if (vehicle == null || moodSwitch == null) return;
        boolean lightsOut = player.sleepEnded() || (isDark() && !darkKeepsMood());
        if (moodSwitch.isChecked() && lightsOut) {
            vehicle.moodOff();
            moodSent = null;
            return;
        }
        boolean want = moodSwitch.isChecked() && player.playing() && moodPoints != null;
        int[] colors = want ? MoodGradient.colors(moodPoints, sources, SystemClock.elapsedRealtime() / 1000.0) : null;
        if (colors == null) {
            if (vehicle.moodActive()) vehicle.restoreMood();
            moodSent = null;
            return;
        }
        float level = MOOD_LEVEL * player.sleepGain(); // dims along with the sleep-timer fade
        boolean changed = moodSent == null || Math.abs(level - moodLevelSent) > 0.01f;
        if (!changed) for (int i = 0; i < colors.length; i++) if (MoodGradient.diff(colors[i], moodSent[i]) > 1) { changed = true; break; }
        if (!changed) return;
        vehicle.setZoneColors(colors, level);
        moodSent = colors;
        moodLevelSent = level;
    }
}
