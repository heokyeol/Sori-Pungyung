package dev.homesev.sounddrive;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Top-down stage: the listener (car or head) in the middle, front is up.
 * Drag a dot to move a sound, tap it to switch it on/off.
 * Touches leave water-drop ripples; dots pop and fade smoothly; orbiting sounds glide.
 */
final class StageView extends View {
    interface Listener { void onStageChanged(); }

    private final SoundSource[] sources;
    private final Listener listener;
    private final boolean isCar;
    private final float dp, k;
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG), dot = new Paint(Paint.ANTI_ALIAS_FLAG),
            text = new Paint(Paint.ANTI_ALIAS_FLAG), car = new Paint(Paint.ANTI_ALIAS_FLAG),
            wave = new Paint(Paint.ANTI_ALIAS_FLAG), bg = new Paint();
    private float cx, cy, radius;
    private SoundSource dragging;
    private float downX, downY, lastTickX, lastTickY;
    private boolean moved, locked, animateAlways;

    // Water-drop ripples
    private static final long RIPPLE_MS = 900;
    private static final class Ripple { float x, y; int color; long start; float strength; }
    private final List<Ripple> ripples = new ArrayList<>();

    // Per-dot smooth state: shown 0..1 (fade), pop (scale kick after toggling)
    private final Map<SoundSource, float[]> anim = new HashMap<>();
    private long lastFrame;

    StageView(Context ctx, SoundSource[] sources, Listener listener, boolean isCar) {
        super(ctx);
        this.sources = sources;
        this.listener = listener;
        this.isCar = isCar;
        dp = ctx.getResources().getDisplayMetrics().density;
        k = isCar ? 1f : 0.75f;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(1.5f * dp);
        ring.setColor(0x33FFFFFF);
        wave.setStyle(Paint.Style.STROKE);
        text.setColor(Color.WHITE);
        text.setTextAlign(Paint.Align.CENTER);
        car.setColor(0xFFE8ECF2);
        setHapticFeedbackEnabled(true);
    }

    /** While driving, moving sounds is locked to keep interaction minimal. */
    void setLocked(boolean locked) { this.locked = locked; invalidate(); }

    /** Keep redrawing every frame (sounds are orbiting). */
    void setAnimating(boolean on) { animateAlways = on; if (on) postInvalidateOnAnimation(); }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        cx = w / 2f; cy = h / 2f;
        radius = Math.min(w, h) / 2f - 60 * dp * k;
        bg.setShader(new RadialGradient(cx, cy, Math.max(w, h) * 0.7f, 0xFF1B2333, 0xFF0B0F17, Shader.TileMode.CLAMP));
    }

    private float[] state(SoundSource s) {
        float[] st = anim.get(s);
        if (st == null) { st = new float[] {s.enabled ? 1f : 0f, 0f}; anim.put(s, st); }
        return st;
    }

    @Override protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        boolean busy = animateAlways;

        c.drawRect(0, 0, getWidth(), getHeight(), bg);
        for (float f : new float[] {0.33f, 0.66f, 1f}) c.drawCircle(cx, cy, radius * f, ring);
        text.setAlpha(140);
        text.setTextSize(18 * dp * k);
        c.drawText("앞", cx, cy - radius - 12 * dp, text);
        c.drawText("뒤", cx, cy + radius + 30 * dp, text);

        // Ripples under the dots
        for (Iterator<Ripple> it = ripples.iterator(); it.hasNext(); ) {
            Ripple r = it.next();
            float t = (now - r.start) / (float) RIPPLE_MS;
            if (t >= 1f) { it.remove(); continue; }
            busy = true;
            for (int i = 0; i < 3; i++) { // three rings, like a drop hitting water
                float ti = t - i * 0.16f;
                if (ti <= 0) continue;
                float ease = 1 - (1 - ti) * (1 - ti);
                wave.setColor(r.color);
                wave.setAlpha((int) (180 * r.strength * (1 - ti) * (1 - ti)));
                wave.setStrokeWidth((2.5f - i * 0.6f) * dp * (1 - ti * 0.6f));
                c.drawCircle(r.x, r.y, (10 + ease * 70 * r.strength) * dp, wave);
            }
            if (t < 0.25f) { // the drop itself
                dot.setColor(r.color);
                dot.setAlpha((int) (200 * (1 - t / 0.25f)));
                c.drawCircle(r.x, r.y, 6 * dp * (1 - t / 0.25f), dot);
            }
        }

        // Listener in the middle
        if (!isCar) {
            car.setColor(0xFFE8ECF2);
            c.drawCircle(cx, cy, 16 * dp, car);
            c.drawCircle(cx - 15 * dp, cy, 5 * dp, car);
            c.drawCircle(cx + 15 * dp, cy, 5 * dp, car);
            car.setColor(0xFF2A3446);
            c.drawCircle(cx, cy - 14 * dp, 3 * dp, car);
        } else {
            float cw = 34 * dp, ch = 62 * dp;
            car.setColor(0xFFE8ECF2);
            c.drawRoundRect(new RectF(cx - cw / 2, cy - ch / 2, cx + cw / 2, cy + ch / 2), 12 * dp, 12 * dp, car);
            car.setColor(0xFF2A3446);
            c.drawRoundRect(new RectF(cx - cw / 2 + 5 * dp, cy - ch / 2 + 10 * dp, cx + cw / 2 - 5 * dp, cy - ch / 2 + 22 * dp), 4 * dp, 4 * dp, car);
        }

        text.setTextSize(20 * dp * k);
        for (SoundSource s : sources) {
            if (s.locked) continue;
            float[] st = state(s);
            float target = s.enabled ? 1f : 0f;
            if (Math.abs(st[0] - target) > 0.001f) { st[0] += (target - st[0]) * Math.min(1f, dt * 10); busy = true; }
            if (st[1] > 0.001f) { st[1] *= (float) Math.exp(-dt * 6); busy = true; } else st[1] = 0;
            float pop = 1f + 0.35f * st[1] * (float) Math.cos(st[1] * 9); // springy kick
            float shown = 0.27f + 0.73f * st[0];
            float px = cx + s.x * radius, py = cy + s.y * radius;
            boolean held = s == dragging;
            dot.setColor(s.color);
            dot.setAlpha((int) (255 * (0.08f + 0.16f * st[0])));
            c.drawCircle(px, py, 34 * dp * k * pop * (held ? 1.25f : 1f), dot);
            dot.setAlpha((int) (255 * shown));
            c.drawCircle(px, py, 20 * dp * k * pop, dot);
            text.setAlpha((int) (255 * shown));
            c.drawText(s.name, px, py + 44 * dp * k, text);
        }
        if (locked) {
            text.setAlpha(200);
            text.setTextSize(19 * dp * k);
            c.drawText("주행 중에는 위치 이동이 잠겨요", cx, getHeight() - 14 * dp, text);
        }
        if (busy) postInvalidateOnAnimation(); else lastFrame = 0;
    }

    private void drop(float x, float y, int color, float strength) {
        Ripple r = new Ripple();
        r.x = x; r.y = y; r.color = color; r.strength = strength;
        r.start = SystemClock.uptimeMillis();
        ripples.add(r);
        if (ripples.size() > 8) ripples.remove(0);
        postInvalidateOnAnimation();
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = nearest(x, y);
                downX = lastTickX = x; downY = lastTickY = y; moved = false;
                drop(x, y, dragging != null ? dragging.color : 0xFFFFFFFF, dragging != null ? 1f : 0.6f);
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging == null) return true;
                if (Math.hypot(x - downX, y - downY) > 8 * dp) moved = true;
                if (moved && !locked) {
                    float nx = (x - cx) / radius, ny = (y - cy) / radius;
                    float d = (float) Math.hypot(nx, ny);
                    if (d > 1) { nx /= d; ny /= d; }
                    dragging.x = nx; dragging.y = ny;
                    if (Math.hypot(x - lastTickX, y - lastTickY) > 40 * dp) { // light ticks while dragging
                        lastTickX = x; lastTickY = y;
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    }
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging != null) {
                    if (!moved) {
                        dragging.enabled = !dragging.enabled;
                        state(dragging)[1] = 1f; // pop
                        drop(cx + dragging.x * radius, cy + dragging.y * radius, dragging.color, 1.3f);
                        performHapticFeedback(android.os.Build.VERSION.SDK_INT >= 30
                                ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY);
                    } else {
                        drop(x, y, dragging.color, 0.8f);
                    }
                    dragging = null;
                    invalidate();
                    listener.onStageChanged();
                    performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = null;
                return true;
        }
        return false;
    }

    @Override public boolean performClick() { return super.performClick(); }

    /** Animate the pop for a toggle that came from elsewhere (e.g. the list buttons). */
    void pop(SoundSource s) {
        state(s)[1] = 1f;
        drop(cx + s.x * radius, cy + s.y * radius, s.color, 1.1f);
    }

    private SoundSource nearest(float x, float y) {
        SoundSource best = null;
        double bestD = 48 * dp;
        for (SoundSource s : sources) {
            if (s.locked) continue;
            double d = Math.hypot(cx + s.x * radius - x, cy + s.y * radius - y);
            if (d < bestD) { bestD = d; best = s; }
        }
        return best;
    }
}
