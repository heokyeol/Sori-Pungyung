package dev.homesev.sounddrive;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * App-wide playback: sounds, audio focus, sleep timer and the background service.
 * Lives as long as the process so playback continues with the screen off or the app
 * in the background (kept alive by {@link PlaybackService}).
 */
final class Player {
    interface Listener {
        void onPlayerChanged();
        /** A location unlock just happened (soundName at placeName). */
        default void onUnlocked(String soundName, String placeName) {}
        /** Found a hidden landmark whose sound was already open: just a stamp. */
        default void onStamp(String placeName) {}
        /** At a seasonal landmark out of season ("겨울에 다시 오면 …"). */
        default void onOffSeason(String message) {}
        /** New position fix (for the places radar/map). */
        default void onLocation(double lat, double lon) {}
    }

    static final int[] SLEEP_CHOICES = {0, 15, 30, 60, 90};
    /** Sound fades out over the last this-many minutes of the sleep timer. */
    static final long FADE_MS = 10 * 60_000L;
    static final String CHANNEL = "playback";
    static final int NOTIFICATION_ID = 1;

    private static Player instance;

    static synchronized Player get(Context ctx) {
        if (instance == null) instance = new Player(ctx.getApplicationContext());
        return instance;
    }

    final Context app;
    final SoundSource[] sources;
    final Birds birds;
    final AudioEngine engine;
    final SharedPreferences prefs;
    private final AudioManager audio;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private AudioFocusRequest focus;

    private boolean wantPlay, hasFocus;
    private int sleepMinutes;
    private long sleepEndAt;          // elapsedRealtime, 0 = off
    private float sleepGain = 1f;
    private boolean sleepEnded;        // timer ran out (stays until the next play)
    private float windowGain = 1f;
    private long lastNotifiedMinute = -1;
    private int saveTicks;

    // Location unlocks
    final Unlocks unlocks = new Unlocks();
    private final java.util.Set<String> unlocked = new java.util.HashSet<>();
    private final java.util.Set<String> spotsUnlocked = new java.util.HashSet<>();
    private final java.util.Set<String> regionsVisited = new java.util.HashSet<>();
    private final java.util.Set<String> stamps = new java.util.HashSet<>();
    private DeviceLocation location;
    private int visible; // number of screens currently showing
    private boolean driving;
    private double lastLat = Double.NaN, lastLon;
    private long lastCheckAt;
    private volatile boolean placesLoaded;

    private Player(Context app) {
        this.app = app;
        prefs = app.getSharedPreferences("sounddrive", Context.MODE_PRIVATE);
        audio = app.getSystemService(AudioManager.class);
        SoundSource[] base = Sources.all(), extra = Sources.unlockables();
        sources = new SoundSource[base.length + extra.length];
        System.arraycopy(base, 0, sources, 0, base.length);
        System.arraycopy(extra, 0, sources, base.length, extra.length);
        Birds b = null;
        for (SoundSource s : sources) if (s instanceof Birds) b = (Birds) s;
        birds = b;
        load();
        engine = new AudioEngine(sources);
        engine.mixer.master = prefs.getFloat("master", 0.8f);
        engine.mixer.motion = prefs.getInt("motion", Mixer.MOTION_NONE);
        main.post(tick);
        // Recordings are decoded only for sounds that are on (≈17 MB each), and dropped after a
        // minute off, so dozens of unlockable sounds don't exhaust memory.
        Thread loader = new Thread(() -> {
            boolean birdsLoaded = false;
            while (true) {
                long now = SystemClock.elapsedRealtime();
                for (SoundSource s : sources) {
                    if (s.asset == null) continue;
                    if (s.enabled && !s.locked) {
                        s.lastWanted = now;
                        if (s.loopPcm == null && !s.loadFailed) {
                            AssetLoop.Pcm p = AssetLoop.decode(app, s.asset);
                            if (p != null) { s.loopCh = p.ch; s.loopPcm = p.data; } else s.loadFailed = true;
                        }
                    } else if (s.loopPcm != null && now - s.lastWanted > 60_000) {
                        s.loopPcm = null;
                    }
                }
                // Bird calls after the first pass, so sounds that are on start first.
                if (!birdsLoaded && birds != null) { loadBirdClips(); birdsLoaded = true; }
                try { Thread.sleep(200); } catch (InterruptedException e) { return; }
            }
        }, "SoundDriveDecode");
        loader.setDaemon(true);
        loader.start();
        new Thread(() -> {
            try (java.io.Reader r = new java.io.InputStreamReader(app.getAssets().open("places.tsv"), java.nio.charset.StandardCharsets.UTF_8)) {
                unlocks.load(r);
                placesLoaded = true;
                android.util.Log.i("SoundDrive", "places loaded: " + unlocks.size());
            } catch (Exception e) {
                android.util.Log.w("SoundDrive", "places", e);
            }
        }, "SoundDrivePlaces").start();
        location = new DeviceLocation(app, main, this::onLocation);
    }

    /** Reads assets/birds_index.txt ("id start end start end …") and decodes birds_<id>.ogg. */
    private void loadBirdClips() {
        try (java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(app.getAssets().open("birds_index.txt")))) {
            String line;
            while ((line = in.readLine()) != null) {
                String[] p = line.trim().split("\\s+");
                if (p.length < 3) continue;
                int[] idx = new int[p.length - 1];
                for (int i = 1; i < p.length; i++) idx[i - 1] = Integer.parseInt(p[i]);
                float[] d = AssetLoop.decodeMonoFloat(app, "birds_" + p[0] + ".ogg");
                if (d != null) birds.setClips(p[0], d, idx);
            }
        } catch (Exception e) {
            android.util.Log.w("SoundDrive", "bird clips", e);
        }
    }

    // ── State ──────────────────────────────────────────────────────────────
    boolean wantPlay() { return wantPlay; }
    boolean hasFocus() { return hasFocus; }
    boolean playing() { return engine.isRunning(); }
    int sleepMinutes() { return sleepMinutes; }
    boolean sleepEnded() { return sleepEnded; }
    /** 1 normally, going to 0 during the sleep-timer fade. */
    float sleepGain() { return sleepGain; }

    long sleepRemainingMs() {
        return sleepEndAt == 0 ? 0 : Math.max(0, sleepEndAt - SystemClock.elapsedRealtime());
    }

    void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    void removeListener(Listener l) { listeners.remove(l); }
    private void changed() { for (Listener l : new ArrayList<>(listeners)) l.onPlayerChanged(); }

    // ── Playback & focus ───────────────────────────────────────────────────
    private final AudioManager.OnAudioFocusChangeListener focusListener = change -> main.post(() -> {
        switch (change) {
            case AudioManager.AUDIOFOCUS_GAIN:
                hasFocus = true;
                Player.this.engine.mixer.duck = 1f;
                if (wantPlay) Player.this.engine.start();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                Player.this.engine.mixer.duck = 0.3f;
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                hasFocus = false;
                Player.this.engine.stop();
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                stop();
                return;
        }
        changed();
    });

    void play() {
        wantPlay = true;
        sleepEnded = false;
        if (focus == null) {
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(focusListener, main)
                    .build();
        }
        hasFocus = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if (hasFocus) engine.start();
        try {
            app.startForegroundService(new Intent(app, PlaybackService.class));
        } catch (RuntimeException e) {
            android.util.Log.w("SoundDrive", "foreground service", e);
        }
        applyScene();
        updateLocation();
        changed();
    }

    void stop() {
        wantPlay = false;
        hasFocus = false;
        engine.stop();
        if (focus != null) audio.abandonAudioFocusRequest(focus);
        app.stopService(new Intent(app, PlaybackService.class));
        updateLocation();
        changed();
    }

    // ── Location unlocks ───────────────────────────────────────────────────
    /** The screen calls this from onStart/onStop; location runs while visible or playing. */
    void setVisible(boolean v) { visible = Math.max(0, visible + (v ? 1 : -1)); updateLocation(); }

    /** Last known position, or null. */
    double[] lastLocation() { return Double.isNaN(fixLat) ? null : new double[] {fixLat, fixLon}; }
    private double fixLat = Double.NaN, fixLon;

    /** Car build reports driving so screens like the map close themselves. */
    void setDriving(boolean d) { if (driving != d) { driving = d; changed(); } }
    boolean driving() { return driving; }
    boolean placesLoaded() { return placesLoaded; }

    boolean locationPermitted() { return location.hasPermission(); }

    void updateLocation() {
        if (visible > 0 || wantPlay) location.start(); else location.stop();
    }

    private void onLocation(double lat, double lon) {
        fixLat = lat; fixLon = lon;
        for (Listener l : new ArrayList<>(listeners)) l.onLocation(lat, lon);
        if (!placesLoaded) return;
        long now = SystemClock.elapsedRealtime();
        boolean moved = Double.isNaN(lastLat) || Math.hypot((lat - lastLat) * 111_000, (lon - lastLon) * 88_000) > 25;
        if (!moved && now - lastCheckAt < 15_000) return;
        lastLat = lat; lastLon = lon; lastCheckAt = now;
        // 1) Region sounds (default unlocks)
        for (Regions.Region r : Regions.check(lat, lon, regionsVisited)) visitRegion(r);
        // 2) Hidden landmarks: a stamp each; opens the category sound if it isn't open yet
        for (String name : unlocks.within(lat, lon)) {
            if (!stamps.add(name)) continue;
            prefs.edit().putLong("stamp." + name, System.currentTimeMillis()).apply();
            Unlocks.Category c = unlocks.categoryOf(name);
            if (c != null && !unlocked.contains(c.id)) unlock(c, name);
            else for (Listener l : new ArrayList<>(listeners)) l.onStamp(name);
        }
        // 3) Seasonal events
        int month = Calendar.getInstance().get(Calendar.MONTH) + 1;
        Seasons.Check sc = Seasons.check(lat, lon, month, spotsUnlocked);
        for (Seasons.Spot s : sc.unlock) unlockSpot(s);
        for (Seasons.Spot s : sc.offSeason) offSeason(s);
    }

    private void unlock(Unlocks.Category c, String placeName) {
        if (!unlocked.add(c.id)) return;
        prefs.edit().putString("unlock." + c.id, placeName + "\t" + System.currentTimeMillis()).apply();
        String soundName = c.sound;
        for (SoundSource s : sources) if (s.id.equals(c.sound)) { s.locked = false; soundName = s.name; }
        android.util.Log.i("SoundDrive", "unlocked " + c.id + " at " + placeName);
        for (Listener l : new ArrayList<>(listeners)) l.onUnlocked(soundName, placeName);
        changed();
    }

    private void visitRegion(Regions.Region r) {
        if (!regionsVisited.add(r.id)) return;
        prefs.edit().putString("region." + r.id, r.name + "\t" + System.currentTimeMillis()).apply();
        // A region that carries a category sound (e.g. 영종도 → airplane) also counts as that category,
        // so a hidden landmark of the same kind later only adds a stamp.
        for (Unlocks.Category c : Unlocks.ALL) {
            if (c.sound.equals(r.sound) && unlocked.add(c.id))
                prefs.edit().putString("unlock." + c.id, r.name + "\t" + System.currentTimeMillis()).apply();
        }
        String soundName = r.sound;
        boolean wasLocked = false;
        for (SoundSource s : sources) if (s.id.equals(r.sound)) { wasLocked = s.locked; s.locked = false; soundName = s.name; }
        android.util.Log.i("SoundDrive", "region " + r.id);
        for (Listener l : new ArrayList<>(listeners)) {
            if (wasLocked) l.onUnlocked(soundName, r.name); else l.onStamp(r.name);
        }
        changed();
    }

    String regionInfo(String id) { return prefs.getString("region." + id, null); }
    java.util.Set<String> regionsVisited() { return regionsVisited; }
    java.util.Set<String> stamps() { return stamps; }

    private void unlockSpot(Seasons.Spot s) {
        if (!spotsUnlocked.add(s.id)) return;
        prefs.edit().putString("spot." + s.id, s.place + "\t" + System.currentTimeMillis()).apply();
        String soundName = s.sound;
        for (SoundSource src : sources) if (src.id.equals(s.sound)) { src.locked = false; soundName = src.name; }
        android.util.Log.i("SoundDrive", "season unlock " + s.id);
        for (Listener l : new ArrayList<>(listeners)) l.onUnlocked(soundName, s.place);
        changed();
    }

    /** Tell once a day per landmark that it opens in another season. */
    private void offSeason(Seasons.Spot s) {
        String today = new java.text.SimpleDateFormat("yyyyMMdd", Locale.US).format(new java.util.Date());
        if (today.equals(prefs.getString("spotnote." + s.id, ""))) return;
        prefs.edit().putString("spotnote." + s.id, today).apply();
        String soundName = s.sound;
        for (SoundSource src : sources) if (src.id.equals(s.sound)) soundName = src.name;
        char last = s.place.charAt(s.place.length() - 1);
        String topic = last >= 0xAC00 && last <= 0xD7A3 && (last - 0xAC00) % 28 != 0 ? "은 " : "는 ";
        String msg = s.place + topic + s.seasonName() + "에 다시 오면 '" + soundName + "' 소리를 수집할 수 있어요";
        for (Listener l : new ArrayList<>(listeners)) l.onOffSeason(msg);
    }

    String spotInfo(String spotId) { return prefs.getString("spot." + spotId, null); }
    java.util.Set<String> spotsUnlocked() { return spotsUnlocked; }

    /** "place\ttimeMillis" for an unlocked category, or null. */
    String unlockInfo(String categoryId) { return prefs.getString("unlock." + categoryId, null); }

    // ── Sleep timer ────────────────────────────────────────────────────────
    /** Cycle 끔 → 15 → 30 → 60 → 90 → 끔. Starting a timer also starts playback. */
    void cycleSleep() {
        int i = 0;
        for (int k = 0; k < SLEEP_CHOICES.length; k++) if (SLEEP_CHOICES[k] == sleepMinutes) i = k;
        setSleep(SLEEP_CHOICES[(i + 1) % SLEEP_CHOICES.length]);
    }

    void setSleep(int minutes) {
        sleepMinutes = minutes;
        sleepEndAt = minutes == 0 ? 0 : SystemClock.elapsedRealtime() + minutes * 60_000L;
        sleepGain = 1f;
        lastNotifiedMinute = -1;
        if (minutes > 0 && !wantPlay) play();
        applyScene();
        updateNotification();
        changed();
    }

    /** Sound level while a window is open (Pleos window link); 1 = unchanged. */
    /** Cycle still → planet orbits → 360° turn → still. */
    int cycleMotion() {
        int m = (engine.mixer.motion + 1) % 3;
        engine.mixer.motion = m;
        prefs.edit().putInt("motion", m).apply();
        save();
        changed();
        return m;
    }

    int motion() { return engine.mixer.motion; }

    void setMotion(int m) {
        engine.mixer.motion = m;
        prefs.edit().putInt("motion", m).apply();
        save();
        changed();
    }

    void setWindowGain(float g) {
        windowGain = g;
        applyScene();
    }

    private void applyScene() { engine.mixer.scene = sleepGain * windowGain; }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            if (engine.mixer.motion != Mixer.MOTION_NONE && ++saveTicks % 10 == 0) save(); // keep orbit positions
            if (birds != null) birds.night = hour >= 19 || hour < 5;
            if (sleepEndAt != 0) {
                long left = sleepRemainingMs();
                sleepGain = left >= FADE_MS ? 1f : left / (float) FADE_MS;
                applyScene();
                if (left == 0) {
                    sleepEndAt = 0;
                    sleepMinutes = 0;
                    sleepEnded = true;
                    stop();
                    sleepGain = 1f;
                    applyScene();
                } else {
                    long minute = left / 60_000L;
                    if (minute != lastNotifiedMinute) { lastNotifiedMinute = minute; updateNotification(); }
                    changed();
                }
            }
            main.postDelayed(this, 1000);
        }
    };

    // ── Notification (shown by the foreground service) ─────────────────────
    Notification buildNotification() {
        NotificationManager nm = app.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "재생", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(app, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(app, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stop = new Intent(app, PlaybackService.class).setAction(PlaybackService.ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(app, 1, stop, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String text = sleepEndAt == 0 ? "재생 중"
                : String.format(Locale.KOREA, "수면 타이머 %d분 남음", (sleepRemainingMs() + 59_999) / 60_000);
        return new Notification.Builder(app, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("소리풍경")
                .setContentText(text)
                .setContentIntent(openPi)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(new Notification.Action.Builder(null, "정지", stopPi).build())
                .build();
    }

    private void updateNotification() {
        if (!wantPlay) return;
        try {
            app.getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, buildNotification());
        } catch (RuntimeException ignored) {}
    }

    // ── Persistence ────────────────────────────────────────────────────────
    private void load() {
        boolean first = !prefs.contains("rain.x");
        for (SoundSource s : sources) {
            s.x = prefs.getFloat(s.id + ".x", s.homeX);
            s.y = prefs.getFloat(s.id + ".y", s.homeY);
            s.volume = prefs.getFloat(s.id + ".vol", 0.8f);
            s.enabled = prefs.getBoolean(s.id + ".on", first && (s.id.equals("rain") || s.id.equals("pad")));
        }
        for (Regions.Region r : Regions.ALL) {
            if (prefs.getString("region." + r.id, null) == null) continue;
            regionsVisited.add(r.id);
            for (SoundSource s : sources) if (s.id.equals(r.sound)) s.locked = false;
        }
        for (String k : prefs.getAll().keySet()) if (k.startsWith("stamp.")) stamps.add(k.substring(6));
        for (Seasons.Spot sp : Seasons.ALL) {
            if (prefs.getString("spot." + sp.id, null) == null) continue;
            spotsUnlocked.add(sp.id);
            for (SoundSource s : sources) if (s.id.equals(sp.sound)) s.locked = false;
        }
        for (Unlocks.Category c : Unlocks.ALL) {
            if (prefs.getString("unlock." + c.id, null) == null) continue;
            unlocked.add(c.id);
            for (SoundSource s : sources) if (s.id.equals(c.sound)) s.locked = false;
        }
        if (birds != null) {
            for (int i = 0; i < Birds.ALL.length; i++) {
                birds.speciesOn[i] = prefs.getBoolean("bird." + Birds.ALL[i].id, true);
                float[] d = Birds.defaultPerch(i);
                birds.perchX[i] = prefs.getFloat("perch." + Birds.ALL[i].id + ".x", d[0]);
                birds.perchY[i] = prefs.getFloat("perch." + Birds.ALL[i].id + ".y", d[1]);
            }
            birds.autoTime = prefs.getBoolean("bird.auto", true);
        }
    }

    void save() {
        SharedPreferences.Editor e = prefs.edit();
        for (SoundSource s : sources) {
            e.putFloat(s.id + ".x", s.x).putFloat(s.id + ".y", s.y)
             .putFloat(s.id + ".vol", s.volume).putBoolean(s.id + ".on", s.enabled);
        }
        if (birds != null) {
            for (int i = 0; i < Birds.ALL.length; i++) {
                e.putBoolean("bird." + Birds.ALL[i].id, birds.speciesOn[i]);
                e.putFloat("perch." + Birds.ALL[i].id + ".x", birds.perchX[i]).putFloat("perch." + Birds.ALL[i].id + ".y", birds.perchY[i]);
            }
            e.putBoolean("bird.auto", birds.autoTime);
        }
        e.putFloat("master", engine.mixer.master);
        e.apply();
    }
}
