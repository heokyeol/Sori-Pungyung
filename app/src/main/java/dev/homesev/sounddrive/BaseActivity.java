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
abstract class BaseActivity extends Activity implements StageView.Listener, Player.Listener, CoachView.Host {
    protected final Handler main = new Handler(Looper.getMainLooper());
    protected Player player;
    protected SoundSource[] sources;
    protected StageView stage;
    protected TextView status;
    protected float dp, k = 1f;
    private Widgets.RoundButton play;
    private TextView title, timeView, tempView, selName, selInfo, motionValue, sleepValue, screenValue;
    private Widgets.TickSlider selSlider;
    private final java.util.List<Widgets.SoundTile> tiles = new java.util.ArrayList<>();
    private final java.util.List<TextView[]> tabs = new java.util.ArrayList<>(); // {label, underline-holder}
    private final java.util.List<Widgets.Underline> tabUnders = new java.util.ArrayList<>();
    private final java.util.List<String> tabNames = new java.util.ArrayList<>();
    private CoachView coach; // first-run drag demo until the first real drop
    private MyPresets myPresets; // "나만의 소리" saved on this device (SharedPreferences "mypresets")
    private LinearLayout moreSheet;
    private SoundSource selected;
    static final String[][] PRESETS = {{"빗속 드라이브", "rain,wind,pad"}, {"바닷가", "waves,birds,wind"}, {"캠핑", "fire,birds,pad"}};
    private View darkOverlay;
    private TextView darkText;
    private boolean dark, darkKeepMood;

    /** Car head unit (larger text, landscape) vs phone. */
    protected abstract boolean isCar();
    /** Build-specific switches in the panel. */
    protected void addExtras(LinearLayout panel) {}
    /** Build-specific dark-screen buttons; default is a single "화면 어둡게". */
    protected void addDarkButtons(LinearLayout panel) {
        Button b = bigButton("화면 어둡게", 0xFF2A3446);
        b.setOnClickListener(v -> setDark(true, false));
        panel.addView(b, matchWrap(px(12)));
    }
    /** Build-specific buttons at the top right of the panel (the car build adds the mic). */
    protected void addHeaderActions(LinearLayout header) {}
    /** Outside temperature for the header, or null. */
    protected String headerTemp() { return null; }
    /** Whether the "화면 어둡게" cell keeps the mood light on. */
    protected boolean darkKeepsMoodDefault() { return false; }
    /** Extra text for the status line. */
    protected String statusText() { return ""; }
    protected void onDarkChanged() {}

    protected boolean isDark() { return dark; }
    protected boolean darkKeepsMood() { return darkKeepMood; }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        // Car head units run at low density (≈160 dpi on a 2560 px screen): scale the whole UI up there.
        k = isCar() ? 1.5f : 0.72f;
        Widgets.scale = isCar() ? 1.5f : 1f;
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
        announceUnlock(placeName + " 근처에서 '" + soundName + "' 소리를 수집했어요");
    }

    /** The car build blocks the map while driving. */
    protected boolean canOpenPlaces() { return true; }

    @Override public void onOffSeason(String message) { announceUnlock(message); }

    @Override public void onStamp(String placeName) { buildUi(); announceUnlock("◉ " + placeName + " 도장을 찍었어요"); }

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
        tiles.clear(); tabs.clear(); tabUnders.clear(); tabNames.clear();
        if (myPresets == null) myPresets = MyPresets.decode(player.prefs.getString("mypresets", ""));
        if (selected == null || selected.locked) selected = firstOn();
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(portrait ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        content.setBackgroundColor(Widgets.BG);

        // ── Left: stage with brand and hint on top ──
        FrameLayout left = new FrameLayout(this);
        stage = new StageView(this, sources, this, isCar());
        stage.setAnimating(player.motion() != Mixer.MOTION_NONE);
        stage.setPlaying(player.playing());
        stage.setSelected(selected);
        left.addView(stage, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(px(28 * k), px(22 * k), px(28 * k), 0);
        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.ImageView pleos = new android.widget.ImageView(this); // official Pleos wordmark
        pleos.setImageResource(R.drawable.pleos_logo);
        pleos.setContentDescription("Pleos");
        pleos.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        brand.addView(pleos, new LinearLayout.LayoutParams(px(63 * k), px(18 * k)));
        View bar = new View(this);
        bar.setBackgroundColor(0x55FFFFFF);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(px(1 * k), px(16 * k));
        blp.setMargins(px(12 * k), 0, px(12 * k), 0);
        brand.addView(bar, blp);
        TextView appName = label("소리풍경.", 25, Widgets.INK); // app name in its own face (Diphylleia, OFL, subset)
        appName.setSingleLine(true);
        appName.setIncludeFontPadding(false);
        try { appName.setTypeface(android.graphics.Typeface.createFromAsset(getAssets(), "fonts/soripungyung.ttf")); }
        catch (RuntimeException e) { /* falls back to the system face */ }
        brand.addView(appName);
        head.addView(brand);
        TextView hint = label("소리를 끌어서\n들리는 방향과 거리를 바꿔 보세요", 13, Widgets.MUTED);
        hint.setLineSpacing(px(4 * k), 1f);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = px(36 * k);
        head.addView(hint, hlp);
        left.addView(head, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START));
        content.addView(left, portrait
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.7f));
        View div = new View(this);
        div.setBackgroundColor(0x1FFFFFFF);
        content.addView(div, portrait ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
                : new LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT));

        // ── Right: panel ──
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Widgets.PANEL);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(px(36 * k), px(18 * k), px(36 * k), px(28 * k));
        scroll.addView(panel);
        content.addView(scroll, portrait
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.1f)
                : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        // top row: temperature, time, actions
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        tempView = label("", 15, Widgets.MUTED);
        tempView.setTypeface(Widgets.MONO);
        timeView = label("", 17, Widgets.INK);
        timeView.setTypeface(Widgets.MONO);
        top.addView(tempView);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.setMargins(px(24 * k), 0, px(16 * k), 0);
        top.addView(timeView, tlp);
        addHeaderActions(top);
        Widgets.IconButton more = new Widgets.IconButton(this, Widgets.IconButton.Kind.MORE);
        more.setOnClickListener(v -> showMore());
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(px(40 * k), px(40 * k));
        mlp.setMarginStart(px(8 * k));
        top.addView(more, mlp);
        panel.addView(top);

        // title + play
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        title = label("", 40, Widgets.INK);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setAutoSizeTextTypeUniformWithConfiguration((int) (20 * k), (int) (40 * k), 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        titles.addView(title);
        status = label("", 14, Widgets.MUTED);
        titles.addView(status);
        titleRow.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        play = new Widgets.RoundButton(this);
        play.setOnClickListener(v -> { if (player.wantPlay()) player.stop(); else player.play(); });
        titleRow.addView(play, new LinearLayout.LayoutParams(px(88 * k), px(88 * k)));
        panel.addView(titleRow, matchWrap(px(26 * k)));

        // preset tabs
        LinearLayout tabRow = new LinearLayout(this);
        for (String[] pr : PRESETS) addTab(tabRow, pr[0], v -> applyPreset(pr[1].split(",")), null);
        for (MyPresets.Preset mp : myPresets.list) addTab(tabRow, mp.name, v -> applyMyPreset(mp), v -> { editMyPreset(mp); return true; });
        TextView save = label("+ 저장", 17, Widgets.MUTED);
        save.setSingleLine(true);
        save.setAlpha(0.7f);
        save.setPadding(0, px(10 * k), 0, px(12 * k));
        save.setOnClickListener(v -> saveMyPreset());
        tabRow.addView(save);
        android.widget.HorizontalScrollView tabScroll = new android.widget.HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.addView(tabRow);
        panel.addView(tabScroll, matchWrap(px(20 * k)));
        panel.addView(hairline());

        // sound grid
        android.widget.GridLayout grid = new android.widget.GridLayout(this);
        // Columns from the panel's real width: each tile needs ≈ 92 dp (scaled) to keep names readable.
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int panelW = portrait ? screenW : Math.round(screenW / 2.7f);
        int cols = Math.max(3, Math.min(5, panelW / Math.round(92 * dp * k)));
        grid.setColumnCount(cols);
        for (SoundSource s : sources) {
            if (s.locked) continue;
            Widgets.SoundTile tile = new Widgets.SoundTile(this, s, k);
            tile.selected = s == selected;
            tile.setOnClickListener(v -> select(s)); // on/off is drag onto / off the stage
            tile.setOnLongClickListener(v -> { select(s); return tile.startCarry(); });
            android.widget.GridLayout.LayoutParams gp = new android.widget.GridLayout.LayoutParams(
                    android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED),
                    android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f));
            gp.width = 0;
            gp.topMargin = px(14 * k);
            grid.addView(tile, gp);
            tiles.add(tile);
        }
        panel.addView(grid, matchWrap(px(6 * k)));
        panel.addView(hairline(), matchWrap(px(18 * k)));

        // selected sound
        LinearLayout selRow = new LinearLayout(this);
        selRow.setGravity(Gravity.CENTER_VERTICAL);
        selName = label("", 22, Widgets.INK);
        selName.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        selRow.addView(selName, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        selInfo = label("", 13, Widgets.MUTED);
        selInfo.setTypeface(Widgets.MONO);
        selRow.addView(selInfo);
        panel.addView(selRow, matchWrap(px(18 * k)));
        selSlider = new Widgets.TickSlider(this);
        selSlider.setOnChange(v -> { if (selected != null) { selected.volume = v; player.save(); refreshLight(); } });
        panel.addView(selSlider, matchWrap(px(8 * k)));
        panel.addView(hairline(), matchWrap(px(14 * k)));

        // bird species
        if (player.birds != null && !player.birds.locked) {
            LinearLayout birdRow = new LinearLayout(this);
            birdRow.setGravity(Gravity.CENTER_VERTICAL);
            birdRow.addView(label("새 종류", 15, Widgets.MUTED), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView pick = label("고르기", 15, Widgets.INK);
            pick.setPaintFlags(pick.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
            pick.setPadding(px(12 * k), px(12 * k), 0, px(12 * k));
            pick.setOnClickListener(v -> showSpeciesDialog());
            birdRow.addView(pick);
            panel.addView(birdRow, matchWrap(px(6 * k)));
        }

        panel.addView(hairline(), matchWrap(px(14 * k)));

        // bottom cells: motion, sleep timer, screen
        LinearLayout cells = new LinearLayout(this);
        motionValue = cell(cells, "움직임", v -> { int m = player.cycleMotion(); stage.setAnimating(m != Mixer.MOTION_NONE); }, false);
        sleepValue = cell(cells, "수면 타이머", v -> player.cycleSleep(), true);
        screenValue = cell(cells, "화면", v -> setDark(true, darkKeepsMoodDefault()), true);
        panel.addView(cells, matchWrap(px(4 * k)));

        // "⋯" sheet: build-specific switches and the less frequent actions
        moreSheet = new LinearLayout(this);
        moreSheet.setOrientation(LinearLayout.VERTICAL);
        moreSheet.setPadding(px(20 * k), px(8 * k), px(20 * k), px(20 * k));
        addExtras(moreSheet);
        addDarkButtons(moreSheet);
        Button places = bigButton("주변 수집 장소 (레이더 · 지도)", Widgets.PANEL);
        places.setOnClickListener(v -> {
            if (!canOpenPlaces()) { android.widget.Toast.makeText(this, "정차 중에만 볼 수 있어요", android.widget.Toast.LENGTH_SHORT).show(); return; }
            startActivity(new android.content.Intent(this, PlacesActivity.class));
        });
        moreSheet.addView(places, matchWrap(px(12 * k)));
        Button dex = bigButton("소리 도감 (" + unlockedCount() + "/" + collectionTotal() + ")", Widgets.PANEL);
        dex.setOnClickListener(v -> showCollection());
        moreSheet.addView(dex, matchWrap(px(10 * k)));
        Button reset = bigButton("위치 처음으로", Widgets.PANEL);
        reset.setOnClickListener(v -> { for (SoundSource x : sources) { x.x = x.homeX; x.y = x.homeY; } stage.invalidate(); player.save(); });
        moreSheet.addView(reset, matchWrap(px(10 * k)));
        Button credits = bigButton("소리 출처", Widgets.PANEL);
        credits.setOnClickListener(v -> showCredits());
        moreSheet.addView(credits, matchWrap(px(10 * k)));

        // Dark (sleep) overlay on top of everything.
        FrameLayout root = new FrameLayout(this);
        root.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        coach = null;
        if (!player.prefs.getBoolean("coach.done", false)) {
            coach = new CoachView(this, this, k);
            root.addView(coach, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
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
        main.removeCallbacks(clock);
        main.post(clock);
        refresh();
    }

    private final Runnable clock = new Runnable() {
        @Override public void run() {
            if (timeView != null) timeView.setText(new java.text.SimpleDateFormat("HH:mm", Locale.KOREA).format(new java.util.Date()));
            if (tempView != null) { String t = headerTemp(); tempView.setText(t == null ? "" : t); }
            main.postDelayed(this, 20_000);
        }
    };

    private View hairline() {
        View v = new View(this);
        v.setBackgroundColor(0x1FFFFFFF);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return v;
    }

    private TextView cell(LinearLayout row, String name, View.OnClickListener click, boolean divider) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(divider ? px(18 * k) : 0, px(12 * k), px(8 * k), px(12 * k));
        if (divider) {
            android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
            d.setColor(Widgets.PANEL);
            android.graphics.drawable.LayerDrawable ld = new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[] {
                    new android.graphics.drawable.ColorDrawable(0x1FFFFFFF), d});
            ld.setLayerInset(1, 1, 0, 0, 0);
            c.setBackground(ld);
        }
        c.addView(label(name, 13, Widgets.MUTED));
        TextView v = label("", 20, Widgets.INK);
        c.addView(v);
        c.setOnClickListener(click);
        pressFeedback(c);
        row.addView(c, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return v;
    }

    private void showMore() {
        if (moreSheet.getParent() != null) ((ViewGroup) moreSheet.getParent()).removeView(moreSheet);
        showSheet("더보기", moreSheet);
    }

    /** Dark panel dialog in the app's line style: title, 닫기, scrolling body. */
    protected android.app.Dialog showSheet(String titleText, View body) {
        android.app.Dialog d = new android.app.Dialog(this);
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Widgets.PANEL);
        bg.setCornerRadius(px(16 * k));
        bg.setStroke(1, 0x33FFFFFF);
        box.setBackground(bg);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(px(24 * k), px(18 * k), px(16 * k), px(8 * k));
        TextView t = label(titleText, 22, Widgets.INK);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        head.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = label("닫기", 16, Widgets.INK);
        close.setPaintFlags(close.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        close.setPadding(px(12 * k), px(10 * k), px(12 * k), px(10 * k));
        close.setOnClickListener(v -> d.dismiss());
        head.addView(close);
        box.addView(head);
        ScrollView sv = new ScrollView(this);
        sv.addView(body);
        box.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        d.setContentView(box);
        android.view.Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            w.setLayout(Math.min(dm.widthPixels - px(40), px(560 * k)), Math.round(dm.heightPixels * 0.8f));
        }
        d.show();
        return d;
    }

    private TextView sheetText(String text) {
        TextView tv = label(text, 16, 0xFFCFCFCF);
        tv.setLineSpacing(px(4 * k), 1f);
        int p = px(24 * k);
        tv.setPadding(p, px(4 * k), p, p);
        return tv;
    }

    private SoundSource firstOn() {
        for (SoundSource s : sources) if (!s.locked && s.enabled) return s;
        for (SoundSource s : sources) if (!s.locked) return s;
        return sources[0];
    }

    /** Make s the sound shown in the detail row (and ringed on the stage and grid). */
    protected void select(SoundSource s) {
        selected = s;
        stage.setSelected(s);
        refresh();
    }

    @Override public void onSelected(SoundSource s) { select(s); }

    private String presetName() {
        for (String[] pr : PRESETS) {
            java.util.Set<String> want = new java.util.HashSet<>(java.util.Arrays.asList(pr[1].split(",")));
            boolean same = true;
            for (SoundSource s : sources) if (!s.locked && s.enabled != want.contains(s.id)) { same = false; break; }
            if (same) return pr[0];
        }
        MyPresets.Preset mine = myPresets != null ? myPresets.find(sources) : null;
        return mine != null ? mine.name : "나만의 소리";
    }

    /** A tab is just text; the active one gets a 2 px underline drawn by its background. */
    private void addTab(LinearLayout row, String name, View.OnClickListener click, View.OnLongClickListener longClick) {
        TextView tl = label(name, 17, Widgets.MUTED);
        tl.setSingleLine(true);
        tl.setPadding(0, px(10 * k), 0, px(12 * k));
        Widgets.Underline under = new Widgets.Underline(px(2 * k));
        tl.setBackground(under);
        tl.setOnClickListener(click);
        if (longClick != null) tl.setOnLongClickListener(longClick);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.setMarginEnd(px(32 * k));
        row.addView(tl, tp);
        tabs.add(new TextView[] {tl});
        tabUnders.add(under);
        tabNames.add(name);
    }

    // ── First-run coach ────────────────────────────────────────────────────
    @Override public Widgets.SoundTile coachTile() {
        android.graphics.Rect r = new android.graphics.Rect();
        for (Widgets.SoundTile t : tiles) if (!t.s.enabled && !t.s.locked && t.getGlobalVisibleRect(r) && r.height() > t.getHeight() / 2) return t;
        return null;
    }
    @Override public StageView coachStage() { return stage; }
    @Override public SoundSource[] coachSources() { return sources; }
    @Override public boolean coachBlocked() { return dark || stage == null || stage.birdEdit() || stage.isLocked(); }

    @Override public boolean dispatchTouchEvent(android.view.MotionEvent e) {
        if (coach != null && e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) coach.poke();
        return super.dispatchTouchEvent(e);
    }

    private void storeMyPresets() { player.prefs.edit().putString("mypresets", myPresets.encode()).apply(); }

    private void shortToast(String msg) { android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show(); }

    /** "+ 저장": keep what's on, where and how loud, as "내 소리 N". */
    private void saveMyPreset() {
        MyPresets.Preset now = MyPresets.capture(myPresets.nextName(), sources);
        MyPresets.Preset same = myPresets.find(sources);
        if (now.sounds.isEmpty()) { shortToast("켜진 소리가 없어요"); return; }
        if (same != null) { shortToast("이미 저장된 조합이에요 (" + same.name + ")"); return; }
        if (myPresets.list.size() >= MyPresets.MAX) { shortToast("저장은 " + MyPresets.MAX + "개까지예요. 저장한 탭을 길게 눌러 지울 수 있어요"); return; }
        myPresets.list.add(now);
        storeMyPresets();
        buildUi();
        boolean batchim = now.name.endsWith("3") || now.name.endsWith("6") || now.name.endsWith("0"); // 삼·육·영 → 으로
        shortToast("'" + now.name + "'" + (batchim ? "으로" : "로") + " 저장했어요");
    }

    protected void applyMyPreset(MyPresets.Preset p) {
        for (SoundSource s : MyPresets.apply(p, sources)) stage.pop(s);
        stage.invalidate();
        onStageChanged();
        if (!player.wantPlay()) player.play();
    }

    /** Long-press a saved tab: overwrite it with what's playing now, or delete it. */
    private void editMyPreset(MyPresets.Preset p) {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        Button over = bigButton("지금 소리로 덮어쓰기", Widgets.PANEL);
        list.addView(over, matchWrap(px(4 * k)));
        Button del = bigButton("지우기", Widgets.PANEL);
        list.addView(del, matchWrap(px(8 * k)));
        android.app.Dialog sheet = showSheet(p.name, list);
        over.setOnClickListener(v -> {
            MyPresets.Preset now = MyPresets.capture(p.name, sources);
            if (now.sounds.isEmpty()) { shortToast("켜진 소리가 없어요"); return; }
            p.sounds.clear(); p.sounds.putAll(now.sounds);
            storeMyPresets(); sheet.dismiss(); refresh(); shortToast("덮어썼어요");
        });
        del.setOnClickListener(v -> { myPresets.list.remove(p); storeMyPresets(); sheet.dismiss(); buildUi(); shortToast("지웠어요"); });
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
        return player.regionsVisited().size() + player.stamps().size() + player.spotsUnlocked().size();
    }

    private int collectionTotal() { return Regions.ALL.length + player.unlocks.size() + Seasons.ALL.length; }

    private String soundName(String id) {
        SoundSource s = sourceById(id);
        return s != null ? s.name : id;
    }

    private String when(String info, java.text.SimpleDateFormat fmt) {
        String[] p = info.split("\t");
        return p.length > 1 ? fmt.format(new java.util.Date(Long.parseLong(p[1]))) : "";
    }

    /** Collection: region sounds, hidden-landmark stamps, seasonal events. */
    private void showCollection() {
        StringBuilder b = new StringBuilder();
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("M월 d일", Locale.KOREA);
        b.append("── 지역 소리 (").append(player.regionsVisited().size()).append("/").append(Regions.ALL.length).append(") ──\n\n");
        for (Regions.Region r : Regions.ALL) {
            String info = player.regionInfo(r.id);
            if (info != null) b.append("●  ").append(r.name).append(" · ").append(soundName(r.sound)).append(" · ").append(when(info, fmt)).append("\n");
            else b.append("○  ").append(r.name).append(" · ").append(soundName(r.sound)).append("\n");
        }
        b.append("\n── 숨은 명소 도장 (").append(player.stamps().size()).append("/").append(player.unlocks.size()).append(") ──\n\n");
        if (player.stamps().isEmpty()) b.append("아직 찾은 숨은 명소가 없어요.\n");
        else for (String st : new java.util.TreeSet<>(player.stamps())) b.append("◉  ").append(st).append("\n");
        b.append("공항, 기차, 도서관, 야구장처럼 이름난 곳 " + player.unlocks.size() + "곳이 지도에 안 보이게 숨어 있어요. 도서관과 야구장 소리는 숨은 명소에서만 수집할 수 있어요.\n");
        int nowSeason = Seasons.season(java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1);
        b.append("\n── 계절 명소 (지금은 ").append(Seasons.SEASON_NAMES[nowSeason]).append(") ──\n\n");
        for (int season = 0; season < 4; season++) {
            for (Seasons.Spot sp : Seasons.ALL) {
                if (sp.season != season) continue;
                String info = player.spotInfo(sp.id);
                if (info != null) b.append("●  ").append(soundName(sp.sound)).append(" · ").append(sp.place).append(" · ").append(when(info, fmt)).append("\n");
                else b.append(sp.season == nowSeason ? "◆  " : "○  ").append(soundName(sp.sound)).append(" · ").append(sp.hint()).append("\n");
            }
        }
        b.append("\n").append(player.locationPermitted() ? "위치는 앱이 켜져 있거나 재생 중일 때만 확인해요."
                : "위치 권한이 꺼져 있어서 소리가 열리지 않아요. 설정에서 위치 권한을 켜 주세요.");
        showSheet("소리 도감", sheetText(b.toString()));
    }

    /** Attribution for the recordings (required for the CC BY / CC BY-SA ones). */
    private void showCredits() {
        String text;
        try (java.io.InputStream in = getAssets().open("CREDITS.txt")) {
            text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            text = "출처 파일을 읽지 못했어요.";
        }
        TextView tv = sheetText(text);
        tv.setTextSize(13 * k);
        tv.setTextIsSelectable(true);
        showSheet("소리 출처", tv);
    }

    private void showSpeciesDialog() {
        Birds b = player.birds;
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(px(24 * k), 0, px(24 * k), px(20 * k));
        Button edit = bigButton("무대에서 새 자리 옮기기", Widgets.PANEL);
        list.addView(edit, matchWrap(px(4 * k)));
        Button resetPerch = bigButton("새 자리 처음으로", Widgets.PANEL);
        list.addView(resetPerch, matchWrap(px(8 * k)));
        android.widget.Switch auto = switchRow("시간대에 맞춰 자동 (밤엔 밤 새만)", b.autoTime);
        auto.setOnCheckedChangeListener((v, on) -> { b.autoTime = on; player.save(); });
        list.addView(auto, matchWrap(px(8 * k)));
        for (int i = 0; i < Birds.ALL.length; i++) {
            final int idx = i;
            android.widget.Switch sw = switchRow(Birds.ALL[i].name + (Birds.ALL[i].night ? "  (밤)" : ""), b.speciesOn[i]);
            sw.setOnCheckedChangeListener((v, on) -> { b.speciesOn[idx] = on; player.save(); });
            list.addView(sw, matchWrap(px(8 * k)));
        }
        android.app.Dialog sheet = showSheet("새 종류", list);
        edit.setOnClickListener(v -> { sheet.dismiss(); stage.setBirdEdit(true); });
        resetPerch.setOnClickListener(v -> { b.resetPerches(); player.save(); stage.invalidate(); });
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
        String name = presetName();
        title.setText(name);
        // No small status line (count, speed, gear): only the one case the listener must know about.
        boolean waiting = player.wantPlay() && !player.hasFocus();
        status.setText(waiting ? "다른 소리가 나오는 중이라 기다리고 있어요" : "");
        status.setVisibility(waiting ? View.VISIBLE : View.GONE);
        play.setPlaying(player.wantPlay());
        stage.setPlaying(player.playing());
        for (int i = 0; i < tabs.size(); i++) {
            boolean active = tabNames.get(i).equals(name);
            tabs.get(i)[0].setTextColor(active ? Widgets.INK : Widgets.MUTED);
            tabUnders.get(i).setOn(active);
        }
        refreshLight();
        int m = player.motion();
        motionValue.setText(m == Mixer.MOTION_ORBIT ? "행성 궤도" : m == Mixer.MOTION_ROTATE ? "360° 회전" : "멈춤");
        sleepValue.setText(player.sleepMinutes() == 0 ? "끔"
                : String.format(Locale.KOREA, "%d분 남음", (player.sleepRemainingMs() + 59_999) / 60_000));
        screenValue.setText(isDark() ? "어둡게 켜짐" : "어둡게");
        if (darkText != null) {
            darkText.setText("화면을 누르면 돌아와요" + (player.sleepMinutes() > 0
                    ? String.format(Locale.KOREA, "\n수면 타이머 %d분 남음", (player.sleepRemainingMs() + 59_999) / 60_000) : ""));
        }
    }

    /** Parts that change while dragging or sliding (cheap to update often). */
    private void refreshLight() {
        for (Widgets.SoundTile t : tiles) { t.selected = t.s == selected; t.invalidate(); }
        if (selected != null) {
            selName.setText(selected.name);
            String side = StageView.side(selected.x, selected.y);
            boolean front = selected.y <= 0;
            String where = side.startsWith("오른쪽") || side.startsWith("왼쪽")
                    ? side.replaceFirst(" ", " " + (front ? "앞 " : "뒤 ")) : side;
            selInfo.setText(String.format(Locale.KOREA, "볼륨 %d  /  %s", Math.round(selected.volume * 100), where));
            selSlider.setValue(selected.volume);
        }
        stage.invalidate();
    }

    @Override public void onStageChanged() { player.save(); refresh(); }

    @Override public void onSoundDropped(SoundSource s, boolean on) {
        if (on && coach != null) { coach.finish(); coach = null; player.prefs.edit().putBoolean("coach.done", true).apply(); }
        setSound(s, on);
        select(s);
    }

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
        d.setCornerRadius(px(12));
        d.setColor(color);
        d.setStroke(1, 0x44FFFFFF);
        b.setBackground(ripple(d));
        b.setTextSize(17 * k);
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
        sw.setTextColor(Widgets.INK);
        sw.setTextSize(17 * k);
        sw.setChecked(on);
        sw.setMinHeight(px(52 * k));
        int[][] states = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new android.content.res.ColorStateList(states, new int[] {Widgets.INK, 0xFF8A8A8A}));
        sw.setTrackTintList(new android.content.res.ColorStateList(states, new int[] {0x88FFFFFF, 0x33FFFFFF}));
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
