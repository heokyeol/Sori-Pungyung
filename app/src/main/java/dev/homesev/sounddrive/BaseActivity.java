package dev.homesev.sounddrive;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

/**
 * Screen shared by the Pleos and Galaxy builds: stage, sound rows, presets, bird species,
 * sleep timer and the dark (sleep) screen. Each build's MainActivity adds its extras.
 */
abstract class BaseActivity extends Activity implements StageView.Listener, Player.Listener {
    protected final Handler main = new Handler(Looper.getMainLooper());
    protected Player player;
    protected SoundSource[] sources;
    protected StageView stage;
    protected TextView status;
    protected float dp, k = 1f;
    private Button play, sleepBtn, motionBtn;
    private Button[] toggles;
    private SeekBar[] volumes;
    private View darkOverlay;
    private TextView darkText;
    private boolean dark, darkKeepMood;

    /** Car head unit (larger text, landscape) vs phone. */
    protected abstract boolean isCar();
    /** Build-specific switches below the master volume. */
    protected void addExtras(LinearLayout panel) {}
    /** Build-specific dark-screen buttons; default is a single "화면 어둡게". */
    protected void addDarkButtons(LinearLayout panel) {
        Button b = bigButton("화면 어둡게", 0xFF2A3446);
        b.setOnClickListener(v -> setDark(true, false));
        panel.addView(b, matchWrap(px(12)));
    }
    /** Extra text for the status line. */
    protected String statusText() { return ""; }
    protected void onDarkChanged() {}

    protected boolean isDark() { return dark; }
    protected boolean darkKeepsMood() { return darkKeepMood; }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        k = isCar() ? 1f : 0.72f;
        player = Player.get(this);
        sources = player.sources;
        buildUi();
        player.addListener(this);
        requestLocationIfNeeded();
    }

    private static final int REQ_LOCATION = 7;

    /** Build-specific runtime permissions asked together with location (one dialog run). */
    protected String[] extraPermissions() { return new String[0]; }

    private void requestLocationIfNeeded() {
        java.util.List<String> all = new java.util.ArrayList<>(java.util.Arrays.asList(DeviceLocation.PERMISSIONS));
        if (android.os.Build.VERSION.SDK_INT >= 33) all.add("android.permission.POST_NOTIFICATIONS");
        all.addAll(java.util.Arrays.asList(extraPermissions()));
        java.util.List<String> want = new java.util.ArrayList<>();
        for (String p : all)
            if (checkSelfPermission(p) != android.content.pm.PackageManager.PERMISSION_GRANTED) want.add(p);
        if (!want.isEmpty()) requestPermissions(want.toArray(new String[0]), REQ_LOCATION);
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == REQ_LOCATION) player.updateLocation();
    }

    @Override protected void onStart() { super.onStart(); player.setVisible(true); }
    @Override protected void onStop() { player.setVisible(false); super.onStop(); }

    /** New sound unlocked: rebuild so it appears; builds decide how loudly to announce it. */
    @Override public void onUnlocked(String soundName, String placeName) {
        buildUi();
        announceUnlock("🔓 " + placeName + " 근처에서 '" + soundName + "' 소리가 열렸어요");
    }

    /** The car build blocks the map while driving. */
    protected boolean canOpenPlaces() { return true; }

    @Override public void onOffSeason(String message) { announceUnlock(message); }

    /** Default: a short toast. The car build stays quiet while driving. */
    protected void announceUnlock(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show();
    }

    @Override protected void onDestroy() {
        player.removeListener(this);
        super.onDestroy();
    }

    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        buildUi();
    }

    @Override public void onBackPressed() {
        if (dark) setDark(false, false); else super.onBackPressed();
    }

    @Override public void onPlayerChanged() { refresh(); }

    // ── UI ──────────────────────────────────────────────────────────────────
    protected void buildUi() {
        boolean portrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(portrait ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        content.setBackgroundColor(0xFF0B0F17);

        stage = new StageView(this, sources, this, isCar());
        stage.setAnimating(player.motion() != Mixer.MOTION_NONE);
        content.addView(stage, portrait
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 3f));

        ScrollView scroll = new ScrollView(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = px(isCar() ? 20 : 14);
        panel.setPadding(pad, pad, pad, pad);
        scroll.addView(panel);
        content.addView(scroll, portrait
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.15f)
                : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 2f));

        panel.addView(label("사운드드라이브", 34, Color.WHITE));
        status = label("", 20, 0xFF9AA6B8);
        panel.addView(status);

        play = bigButton("재생", 0xFF3D7BFF);
        play.setOnClickListener(v -> { if (player.wantPlay()) player.stop(); else player.play(); });
        panel.addView(play, matchWrap(px(16)));

        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        presets.addView(preset("빗속 드라이브", "rain", "wind", "pad"), weight());
        presets.addView(preset("바닷가", "waves", "birds", "wind"), weight());
        presets.addView(preset("캠핑", "fire", "birds", "pad"), weight());
        panel.addView(presets, matchWrap(px(12)));

        motionBtn = bigButton("", 0xFF2A3446);
        motionBtn.setOnClickListener(v -> {
            int m = player.cycleMotion();
            stage.setAnimating(m != Mixer.MOTION_NONE);
        });
        panel.addView(motionBtn, matchWrap(px(12)));

        sleepBtn = bigButton("", 0xFF2A3446);
        sleepBtn.setOnClickListener(v -> player.cycleSleep());
        panel.addView(sleepBtn, matchWrap(px(12)));
        addDarkButtons(panel);

        panel.addView(label("전체 볼륨", 20, 0xFFCFD6E2), matchWrap(px(18)));
        SeekBar master = seek((int) (player.engine.mixer.master * 100));
        master.setOnSeekBarChangeListener(new SeekListener(p -> { player.engine.mixer.master = p / 100f; player.save(); }));
        panel.addView(master, matchWrap(0));

        addExtras(panel);

        toggles = new Button[sources.length];
        volumes = new SeekBar[sources.length];
        for (int i = 0; i < sources.length; i++) {
            final SoundSource s = sources[i];
            if (s.locked) continue; // location-unlock sounds appear once unlocked
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            Button t = new Button(this);
            t.setAllCaps(false);
            t.setTextSize(21 * k);
            t.setOnClickListener(v -> { s.enabled = !s.enabled; stage.pop(s); onStageChanged(); });
            pressFeedback(t);
            toggles[i] = t;
            row.addView(t, new LinearLayout.LayoutParams(px(180 * k), px(isCar() ? 64 : 48)));
            SeekBar vol = seek((int) (s.volume * 100));
            vol.setOnSeekBarChangeListener(new SeekListener(p -> { s.volume = p / 100f; player.save(); }));
            volumes[i] = vol;
            row.addView(vol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            panel.addView(row, matchWrap(px(10)));
        }

        if (player.birds != null) {
            Button species = bigButton("새 종류 고르기", 0xFF2A3446);
            species.setOnClickListener(v -> showSpeciesDialog());
            panel.addView(species, matchWrap(px(12)));
        }

        Button places = bigButton("🗺 주변 해금 장소 (레이더·지도)", 0xFF2A3446);
        places.setOnClickListener(v -> {
            if (!canOpenPlaces()) {
                android.widget.Toast.makeText(this, "정차 중에만 볼 수 있어요", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new android.content.Intent(this, PlacesActivity.class));
        });
        panel.addView(places, matchWrap(px(12)));

        Button dex = bigButton("소리 도감 (" + unlockedCount() + "/" + (Unlocks.ALL.length + Seasons.ALL.length) + ")", 0xFF2A3446);
        dex.setOnClickListener(v -> showCollection());
        panel.addView(dex, matchWrap(px(12)));

        Button reset = bigButton("위치 처음으로", 0xFF2A3446);
        reset.setOnClickListener(v -> {
            for (SoundSource s : sources) { s.x = s.homeX; s.y = s.homeY; }
            stage.invalidate();
            player.save();
        });
        panel.addView(reset, matchWrap(px(12)));

        Button credits = bigButton("소리 출처", 0xFF1E2636);
        credits.setTextSize(17 * k);
        credits.setOnClickListener(v -> showCredits());
        panel.addView(credits, matchWrap(px(12)));

        panel.addView(label(isCar()
                ? "소리를 끌어서 차 주변 원하는 곳에 놓으세요. 점을 누르면 켜고 꺼져요."
                : "소리를 끌어서 내 주변 원하는 곳에 놓으세요. 점을 누르면 켜고 꺼져요. 헤드폰을 쓰면 방향이 더 잘 들려요.",
                17, 0xFF6F7A8C), matchWrap(px(12)));

        // Dark (sleep) overlay on top of everything.
        FrameLayout root = new FrameLayout(this);
        root.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        darkText = new TextView(this);
        darkText.setBackgroundColor(Color.BLACK);
        darkText.setTextColor(0x33FFFFFF);
        darkText.setTextSize(16 * k);
        darkText.setGravity(Gravity.CENTER);
        darkText.setOnClickListener(v -> setDark(false, false));
        darkOverlay = darkText;
        root.addView(darkOverlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        darkOverlay.setVisibility(dark ? View.VISIBLE : View.GONE);
        setContentView(root);
        refresh();
    }

    protected void setDark(boolean on, boolean keepMood) {
        dark = on;
        darkKeepMood = on && keepMood;
        darkOverlay.setVisibility(on ? View.VISIBLE : View.GONE);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = on ? 0.01f : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(lp);
        onDarkChanged();
        refresh();
    }

    private int unlockedCount() {
        int n = player.spotsUnlocked().size();
        for (Unlocks.Category c : Unlocks.ALL) if (player.unlockInfo(c.id) != null) n++;
        return n;
    }

    /** Collection of location-unlock sounds: where each was found, or a hint. */
    private void showCollection() {
        StringBuilder b = new StringBuilder();
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("M월 d일", Locale.KOREA);
        for (Unlocks.Category c : Unlocks.ALL) {
            String name = c.sound;
            for (SoundSource s : sources) if (s.id.equals(c.sound)) name = s.name;
            String info = player.unlockInfo(c.id);
            if (info != null) {
                String[] p = info.split("\t");
                String when = p.length > 1 ? fmt.format(new java.util.Date(Long.parseLong(p[1]))) : "";
                b.append("🔓 ").append(name).append("\n    ").append(p[0]).append(" · ").append(when).append("\n\n");
            } else {
                b.append("🔒 ").append(name).append("\n    ").append(c.hint).append("\n\n");
            }
        }
        int nowSeason = Seasons.season(java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1);
        b.append("── 계절 명소 (지금은 ").append(Seasons.SEASON_NAMES[nowSeason]).append(") ──\n\n");
        for (int season = 0; season < 4; season++) {
            for (Seasons.Spot sp : Seasons.ALL) {
                if (sp.season != season) continue;
                String name = sp.sound;
                for (SoundSource s : sources) if (s.id.equals(sp.sound)) name = s.name;
                String info = player.spotInfo(sp.id);
                if (info != null) {
                    String[] p = info.split("\t");
                    String when = p.length > 1 ? fmt.format(new java.util.Date(Long.parseLong(p[1]))) : "";
                    b.append("🔓 ").append(name).append("\n    ").append(sp.place).append(" · ").append(when).append("\n\n");
                } else {
                    b.append(sp.season == nowSeason ? "◆ " : "🔒 ").append(name).append("\n    ").append(sp.hint())
                     .append(sp.season == nowSeason ? " · 지금이 그 계절이에요" : "").append("\n\n");
                }
            }
        }
        b.append(player.locationPermitted() ? "위치는 앱이 켜져 있거나 재생 중일 때만 확인해요."
                : "위치 권한이 꺼져 있어서 소리가 열리지 않아요. 설정에서 위치 권한을 켜 주세요.");
        TextView tv = new TextView(this);
        tv.setText(b.toString());
        tv.setTextSize(16 * k);
        int p = px(18);
        tv.setPadding(p, p, p, p);
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("소리 도감")
                .setView(sv)
                .setPositiveButton("닫기", null)
                .show();
    }

    /** Attribution for the recordings (required for the CC BY / CC BY-SA ones). */
    private void showCredits() {
        String text;
        try (java.io.InputStream in = getAssets().open("CREDITS.txt")) {
            text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            text = "출처 파일을 읽지 못했어요.";
        }
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13 * k);
        tv.setTextIsSelectable(true);
        int p = px(16);
        tv.setPadding(p, p, p, p);
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("소리 출처")
                .setView(sv)
                .setPositiveButton("닫기", null)
                .show();
    }

    private void showSpeciesDialog() {
        Birds b = player.birds;
        String[] items = new String[Birds.ALL.length + 1];
        boolean[] checked = new boolean[items.length];
        items[0] = "시간대에 맞춰 자동 (밤엔 밤 새만)";
        checked[0] = b.autoTime;
        for (int i = 0; i < Birds.ALL.length; i++) {
            items[i + 1] = Birds.ALL[i].name + (Birds.ALL[i].night ? " (밤)" : "");
            checked[i + 1] = b.speciesOn[i];
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("새 종류")
                .setMultiChoiceItems(items, checked, (d, which, on) -> {
                    if (which == 0) b.autoTime = on; else b.speciesOn[which - 1] = on;
                    player.save();
                })
                .setPositiveButton("닫기", null)
                .show();
    }

    private Button preset(String name, String... ids) {
        Button b = bigButton(name, 0xFF2A3446);
        b.setTextSize(19 * k);
        b.setOnClickListener(v -> applyPreset(ids));
        return b;
    }

    /** Switch to exactly these sounds and start playing. */
    protected void applyPreset(String... ids) {
        for (SoundSource s : sources) {
            boolean on = false;
            for (String id : ids) if (id.equals(s.id)) on = true;
            if (on && !s.enabled) stage.pop(s);
            s.enabled = on;
        }
        stage.invalidate();
        onStageChanged();
        if (!player.wantPlay()) player.play();
    }

    protected SoundSource sourceById(String id) {
        for (SoundSource s : sources) if (s.id.equals(id)) return s;
        return null;
    }

    /** Turn one sound on/off with the same animation as a tap. */
    protected void setSound(SoundSource s, boolean on) {
        if (s.enabled != on) { s.enabled = on; stage.pop(s); }
        onStageChanged();
        if (on && !player.wantPlay()) player.play();
    }

    protected void setMotion(int m) {
        player.setMotion(m);
        stage.setAnimating(m != Mixer.MOTION_NONE);
    }

    protected void refresh() {
        if (play == null) return;
        play.setText(player.wantPlay() ? (player.hasFocus() ? "정지" : "대기 중 (다른 소리 재생 중)") : "재생");
        for (int i = 0; i < sources.length; i++) {
            SoundSource s = sources[i];
            if (toggles[i] == null) continue;
            toggles[i].setText((s.enabled ? "● " : "○ ") + s.name);
            GradientDrawable d = new GradientDrawable();
            d.setCornerRadius(px(12));
            d.setColor(s.enabled ? s.color : 0xFF1E2636);
            toggles[i].setBackground(ripple(d));
            toggles[i].setTextColor(s.enabled ? 0xFF0B0F17 : 0xFFCFD6E2);
            volumes[i].setEnabled(s.enabled);
        }
        String sleep;
        if (player.sleepMinutes() == 0) sleep = "수면 타이머: 끔";
        else {
            long min = (player.sleepRemainingMs() + 59_999) / 60_000;
            sleep = String.format(Locale.KOREA, "수면 타이머: %d분 (%d분 남음)", player.sleepMinutes(), min);
        }
        sleepBtn.setText(sleep);
        int m = player.motion();
        motionBtn.setText(m == Mixer.MOTION_ORBIT ? "움직임: 🪐 행성 궤도" : m == Mixer.MOTION_ROTATE ? "움직임: 🔄 360° 회전" : "움직임: 멈춤");
        String base = isCar() ? "" : "휴대폰 모드 · 헤드폰 추천";
        String extra = statusText();
        status.setText(base.isEmpty() ? extra.replaceFirst("^ · ", "") : base + extra);
        if (darkText != null) {
            darkText.setText("화면을 누르면 돌아와요" + (player.sleepMinutes() > 0
                    ? String.format(Locale.KOREA, "\n수면 타이머 %d분 남음", (player.sleepRemainingMs() + 59_999) / 60_000) : ""));
        }
    }

    @Override public void onStageChanged() { player.save(); refresh(); }

    // ── Small view helpers ─────────────────────────────────────────────────
    protected int px(float v) { return Math.round(v * dp); }

    protected TextView label(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp * k);
        t.setTextColor(color);
        return t;
    }

    protected Button bigButton(String s, int color) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(24 * k);
        b.setTextColor(Color.WHITE);
        b.setMinHeight(px(isCar() ? 72 : 52));
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(px(14));
        d.setColor(color);
        b.setBackground(ripple(d));
        pressFeedback(b);
        return b;
    }

    /** Water-like ripple spreading from the touch point, on top of the button's own shape. */
    protected static android.graphics.drawable.Drawable ripple(android.graphics.drawable.Drawable content) {
        return new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x40FFFFFF), content, null);
    }

    /** Buttons sink slightly under the finger and spring back, with a light tap vibration. */
    protected static void pressFeedback(View v) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start();
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(260)
                            .setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();
                    break;
            }
            return false; // let the click and ripple happen
        });
    }

    protected android.widget.Switch switchRow(String text, boolean on) {
        android.widget.Switch sw = new android.widget.Switch(this);
        sw.setText(text);
        sw.setTextColor(0xFFCFD6E2);
        sw.setTextSize(19 * k);
        sw.setChecked(on);
        return sw;
    }

    protected SeekBar seek(int progress) {
        SeekBar s = new SeekBar(this);
        s.setMax(100);
        s.setProgress(progress);
        s.setMinimumHeight(px(48));
        return s;
    }

    protected LinearLayout.LayoutParams matchWrap(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = top;
        return p;
    }

    protected LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, px(isCar() ? 72 : 52), 1f);
        p.setMarginEnd(px(6));
        return p;
    }

    protected static final class SeekListener implements SeekBar.OnSeekBarChangeListener {
        interface OnChange { void on(int progress); }
        private final OnChange c;
        SeekListener(OnChange c) { this.c = c; }
        @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) { if (fromUser) c.on(p); }
        @Override public void onStartTrackingTouch(SeekBar s) {}
        @Override public void onStopTrackingTouch(SeekBar s) {}
    }
}
