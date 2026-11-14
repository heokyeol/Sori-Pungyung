package dev.homesev.sounddrive;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Mood-light screen: thin rings ripple out from where each sound sits, driven by how loud that
 * sound really is right now (Mixer writes SoundSource.level). Bird calls ping at their perch,
 * thunder flashes a big ring. Black everywhere else; tap to leave.
 */
final class MoodView extends View {
    interface Host {
        SoundSource[] moodSources();
        /** Timer ran out or playback stopped for good: fade the picture to black. */
        boolean moodFading();
    }

    private static final int MAX_RINGS = 160;
    private static final float BASE_ALPHA = 0.38f;

    private static final class Ring { float x, y, age, speed, maxR, stroke, strength; }

    private final Host host;
    private final float dp;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Ring> rings = new ArrayList<>();
    private final Random rnd = new Random();
    private float[] acc = new float[0], peak = new float[0], slow = new float[0], next = new float[0];
    private long[] seenCall = new long[Birds.ALL.length];
    private long last, shownAt;
    private float fade = 0f; // 0..1, fades in on open and out when the timer ends

    MoodView(Context c, Host host) {
        super(c);
        this.host = host;
        dp = c.getResources().getDisplayMetrics().density;
        setBackgroundColor(Color.BLACK);
        line.setStyle(Paint.Style.STROKE);
        line.setColor(Widgets.INK);
        text.setColor(Widgets.MUTED);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(15 * dp * Widgets.scale);
        setContentDescription("무드등. 화면을 누르면 돌아와요");
    }

    void start() {
        rings.clear();
        last = 0;
        fade = 0f;
        shownAt = SystemClock.uptimeMillis();
        SoundSource[] src = host.moodSources();
        acc = new float[src.length]; peak = new float[src.length]; slow = new float[src.length]; next = new float[src.length];
        for (SoundSource s : src) if (s instanceof Birds) seenCall = ((Birds) s).calledAt.clone();
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        if (getVisibility() != VISIBLE) return;
        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 0f : Math.min(0.1f, (now - last) / 1000f);
        last = now;
        boolean fading = host.moodFading();
        fade = fading ? Math.max(0f, fade - dt / 20f) : Math.min(1f, fade + dt / 2f); // in over 2 s, out over 20 s

        float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f, size = Math.min(w, h) * 0.45f;
        SoundSource[] src = host.moodSources();
        if (acc.length != src.length) start();
        for (int i = 0; i < src.length; i++) {
            SoundSource s = src[i];
            if (s.locked) continue;
            float lvl = s.level;
            peak[i] = Math.max(lvl, peak[i] * (1f - dt * 0.15f));
            float norm = peak[i] > 1e-4f ? Math.min(1f, lvl / peak[i]) : 0f;
            boolean loudJump = slow[i] > 1e-4f && lvl > slow[i] * 3f && lvl > 0.02f;
            slow[i] += (lvl - slow[i]) * Math.min(1f, dt * 0.8f);
            if (lvl < 1e-4f || !s.enabled) { acc[i] = 0; continue; }
            if (s instanceof Birds) { birdCalls((Birds) s, cx, cy, size); continue; }
            MoodWaves.Style st = MoodWaves.of(s.id);
            if (loudJump && st == MoodWaves.STORM) spawn(cx + s.x * size, cy + s.y * size, st, size, 1f);
            acc[i] += dt * (0.4f + 1.2f * norm);
            if (next[i] <= 0) next[i] = st.interval * (1f + st.jitter * (rnd.nextFloat() - 0.5f));
            if (acc[i] >= next[i]) {
                acc[i] = 0;
                next[i] = 0;
                spawn(cx + s.x * size, cy + s.y * size, st, size, s.volume * (0.35f + 0.65f * norm));
            }
        }

        for (int i = rings.size() - 1; i >= 0; i--) {
            Ring r = rings.get(i);
            r.age += dt;
            if (r.age < 0) continue; // delayed second ring of a bird call
            float rad = 6 * dp + r.speed * r.age;
            if (rad >= r.maxR) { rings.remove(i); continue; }
            float life = 1f - rad / r.maxR;
            line.setStrokeWidth(r.stroke * dp);
            line.setAlpha((int) (255 * BASE_ALPHA * r.strength * life * Math.sqrt(life) * fade));
            c.drawCircle(r.x, r.y, rad, line);
        }

        long since = now - shownAt;
        if (since < 4000) { // a quiet hint, then nothing but the picture
            text.setAlpha((int) (200 * Math.min(1f, (4000 - since) / 1000f) * Math.min(1f, since / 500f)));
            c.drawText("화면을 누르면 돌아와요", cx, h - 28 * dp * Widgets.scale, text);
        }
        postInvalidateDelayed(33); // ~30 fps is plenty and kind to the battery
    }

    private void birdCalls(Birds b, float cx, float cy, float size) {
        for (int k = 0; k < Birds.ALL.length; k++) {
            if (b.calledAt[k] != seenCall[k]) {
                seenCall[k] = b.calledAt[k];
                if (!b.speciesOn[k]) continue;
                float px = cx + (b.x + b.perchX[k]) * size, py = cy + (b.y + b.perchY[k]) * size;
                spawn(px, py, MoodWaves.CALL, size, 0.9f);
                Ring second = spawn(px, py, MoodWaves.CALL, size, 0.6f);
                if (second != null) second.age = -0.18f; // a beat later
            }
        }
    }

    private Ring spawn(float x, float y, MoodWaves.Style st, float size, float strength) {
        if (rings.size() >= MAX_RINGS) rings.remove(0);
        Ring r = new Ring();
        r.x = x; r.y = y;
        r.speed = st.speed * size;
        r.maxR = st.maxR * size;
        r.stroke = st.stroke;
        r.strength = Math.max(0f, Math.min(1f, strength));
        rings.add(r);
        return r;
    }
}
