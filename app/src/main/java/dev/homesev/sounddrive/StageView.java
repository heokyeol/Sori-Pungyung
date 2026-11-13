package dev.homesev.sounddrive;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.DragEvent;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Top-down stage in monochrome line art: the car in the middle, front is up, compass ticks
 * around. Each sound that is on emits slow concentric ripples, so the field shows where the
 * sound comes from. Drag a sound to move it, tap to switch it on/off (it also becomes the
 * selected sound shown in the panel).
 */
final class StageView extends View {
    interface Listener {
        void onStageChanged();
        default void onSelected(SoundSource s) {}
        default void onBirdEdit(boolean on) {}
        /** A sound was dropped onto the stage from the panel (on) or dragged far off the ring (off). */
        default void onSoundDropped(SoundSource s, boolean on) {}
    }

    // Bird perches: each species sings from its own spot around the "새" dot.
    // The edit mode (long-press the 새 dot) shows them: drag to move, tap to switch on/off.
    private Birds birds;
    private boolean birdEdit;
    private int draggingPerch = -1;
    private boolean longPressed;
    private final Runnable longPress = () -> {
        if (StageView.this.dragging instanceof Birds && !StageView.this.moved && !StageView.this.locked) {
            longPressed = true;
            setBirdEdit(true);
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
    };

    void setBirdEdit(boolean on) {
        if (birds == null || birds.locked) on = false;
        birdEdit = on;
        if (on && birds != null && !birds.enabled) { birds.enabled = true; state(birds)[1] = 1f; listener.onStageChanged(); }
        listener.onBirdEdit(on);
        invalidate();
    }

    boolean birdEdit() { return birdEdit; }

    private float[] perchPos(int i) {
        float px = birds.x + birds.perchX[i], py = birds.y + birds.perchY[i];
        float len = (float) Math.hypot(px, py);
        if (len > 1f) { px /= len; py /= len; }
        return new float[] {cx + px * radius, cy + py * radius};
    }

    private final SoundSource[] sources;
    private final Listener listener;
    private final boolean isCar;
    private final float dp, k;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG),
            text = new Paint(Paint.ANTI_ALIAS_FLAG), mono = new Paint(Paint.ANTI_ALIAS_FLAG), glow = new Paint();
    private float cx, cy, radius;
    private SoundSource dragging, selected;
    private float downX, downY, lastTickX, lastTickY;
    private boolean moved, locked, animateAlways, playing;
    // Dragging a dot well past the ring turns it off (it snaps back to where the drag began).
    private static final float OFF_DIST = 1.15f;
    private boolean dragOut;
    private float startX, startY;
    // Drop preview while a tile from the panel is dragged over the stage.
    private SoundSource dropHint;
    private float dropX, dropY;

    private static final long RIPPLE_MS = 900;
    private static final class Ripple { float x, y; long start; float strength; }
    private final List<Ripple> ripples = new ArrayList<>();
    private final Map<SoundSource, float[]> anim = new HashMap<>();
    private long lastFrame;
    private float wavePhase; // ripple fields drift outward while playing

    StageView(Context ctx, SoundSource[] sources, Listener listener, boolean isCar) {
        super(ctx);
        this.sources = sources;
        this.listener = listener;
        this.isCar = isCar;
        dp = ctx.getResources().getDisplayMetrics().density;
        k = isCar ? 1.5f : 0.75f; // car head units are low density
        line.setStyle(Paint.Style.STROKE);
        line.setColor(Widgets.INK);
        line.setStrokeCap(Paint.Cap.ROUND);
        text.setColor(Widgets.INK);
        text.setTextAlign(Paint.Align.CENTER);
        mono.setTypeface(Widgets.MONO);
        mono.setTextAlign(Paint.Align.CENTER);
        mono.setColor(Widgets.MUTED);
        setHapticFeedbackEnabled(true);
        for (SoundSource s : sources) if (s instanceof Birds) birds = (Birds) s;
        setOnDragListener(this::onTileDrag);
    }

    /** A sound tile dragged out of the panel: preview on the ring, drop = turn it on right there. */
    private boolean onTileDrag(View v, DragEvent e) {
        if (!(e.getLocalState() instanceof SoundSource)) return false;
        SoundSource s = (SoundSource) e.getLocalState();
        switch (e.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED: return true;
            case DragEvent.ACTION_DRAG_LOCATION: {
                float[] n = clampNorm(e.getX(), e.getY());
                dropHint = s; dropX = n[0]; dropY = n[1];
                invalidate();
                return true;
            }
            case DragEvent.ACTION_DRAG_EXITED:
            case DragEvent.ACTION_DRAG_ENDED:
                dropHint = null;
                invalidate();
                return true;
            case DragEvent.ACTION_DROP: {
                dropHint = null;
                if (!locked) { float[] n = clampNorm(e.getX(), e.getY()); s.x = n[0]; s.y = n[1]; }
                selected = s;
                drop(cx + s.x * radius, cy + s.y * radius, 1.3f);
                performHapticFeedback(android.os.Build.VERSION.SDK_INT >= 30
                        ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY);
                listener.onSoundDropped(s, true);
                return true;
            }
        }
        return true;
    }

    private float[] clampNorm(float x, float y) {
        float nx = (x - cx) / radius, ny = (y - cy) / radius;
        float d = (float) Math.hypot(nx, ny);
        if (d > 1) { nx /= d; ny /= d; }
        return new float[] {nx, ny};
    }

    void setLocked(boolean locked) { this.locked = locked; invalidate(); }
    void setAnimating(boolean on) { animateAlways = on; if (on) postInvalidateOnAnimation(); }
    void setPlaying(boolean on) { playing = on; postInvalidateOnAnimation(); }
    void setSelected(SoundSource s) { selected = s; invalidate(); }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        cx = w / 2f; cy = h / 2f;
        radius = Math.min(w, h) / 2f - 62 * dp * k;
        glow.setShader(new RadialGradient(cx, cy, radius * 0.9f, 0x22FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
    }

    private float[] state(SoundSource s) {
        float[] st = anim.get(s);
        if (st == null) { st = new float[] {s.enabled ? 1f : 0f, 0f}; anim.put(s, st); }
        return st;
    }

    /** "오른쪽 36°" — side and angle from straight ahead (0–180°). */
    static String side(float x, float y) {
        double deg = Math.toDegrees(Math.atan2(x, -y));
        int a = (int) Math.round(Math.abs(deg));
        if (a == 0 || a == 180) return (a == 0 ? "앞 " : "뒤 ") + a + "°";
        return (deg > 0 ? "오른쪽 " : "왼쪽 ") + a + "°";
    }

    @Override protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        boolean busy = animateAlways || playing;
        if (playing) wavePhase = (wavePhase + dt * 9 * dp * k) % (16 * dp * k);

        c.drawColor(Widgets.BG);
        c.drawCircle(cx, cy, radius * 0.9f, glow);

        // Ripple fields from every sound that is on (the interference pattern)
        float spacing = 16 * dp * k;
        for (SoundSource s : sources) {
            if (s.locked) continue;
            float[] st = state(s);
            if (st[0] < 0.02f) continue;
            float px = cx + s.x * radius, py = cy + s.y * radius, maxR = radius * 1.25f;
            line.setStrokeWidth(0.8f * dp);
            for (float rr = 22 * dp * k + wavePhase; rr < maxR; rr += spacing) {
                float a = (1 - rr / maxR);
                line.setAlpha((int) (66 * a * a * st[0] * (0.5f + 0.5f * s.volume)));
                c.drawCircle(px, py, rr, line);
            }
        }

        // Compass ring: ticks every 5°, longer every 15°, cardinal marks with labels
        line.setStrokeWidth(1.1f * dp);
        line.setAlpha(110);
        c.drawCircle(cx, cy, radius, line);
        for (int d = 0; d < 360; d += 5) {
            double a = Math.toRadians(d - 90);
            boolean cardinal = d % 90 == 0, mid = d % 15 == 0;
            float in = radius - (cardinal ? 0 : mid ? 9 * dp * k : 5 * dp * k), out = radius + (cardinal ? 18 * dp * k : 0);
            line.setAlpha(cardinal ? 200 : mid ? 120 : 60);
            c.drawLine(cx + (float) Math.cos(a) * in, cy + (float) Math.sin(a) * in, cx + (float) Math.cos(a) * out, cy + (float) Math.sin(a) * out, line);
        }
        text.setTypeface(Typeface.DEFAULT);
        text.setTextSize(15 * dp * k);
        text.setColor(Widgets.MUTED);
        c.drawText("앞", cx, cy - radius - 28 * dp * k, text);
        c.drawText("뒤", cx, cy + radius + 40 * dp * k, text);
        text.setTextAlign(Paint.Align.RIGHT);
        c.drawText("왼쪽", cx - radius - 26 * dp * k, cy + 5 * dp * k, text);
        text.setTextAlign(Paint.Align.LEFT);
        c.drawText("오른쪽", cx + radius + 26 * dp * k, cy + 5 * dp * k, text);
        text.setTextAlign(Paint.Align.CENTER);

        // Water-drop ripples from touches
        for (Iterator<Ripple> it = ripples.iterator(); it.hasNext(); ) {
            Ripple r = it.next();
            float t = (now - r.start) / (float) RIPPLE_MS;
            if (t >= 1f) { it.remove(); continue; }
            busy = true;
            for (int i = 0; i < 3; i++) {
                float ti = t - i * 0.16f;
                if (ti <= 0) continue;
                float ease = 1 - (1 - ti) * (1 - ti);
                line.setAlpha((int) (170 * r.strength * (1 - ti) * (1 - ti)));
                line.setStrokeWidth((1.8f - i * 0.4f) * dp);
                c.drawCircle(r.x, r.y, (10 + ease * 70 * r.strength) * dp, line);
            }
        }

        // Faint dotted guides from the car to each sound that is on
        Paint dash = new Paint(line);
        dash.setPathEffect(new DashPathEffect(new float[] {2 * dp, 6 * dp}, 0));
        dash.setStrokeWidth(1f * dp);
        for (SoundSource s : sources) {
            if (s.locked || !s.enabled) continue;
            dash.setAlpha(55);
            c.drawLine(cx, cy, cx + s.x * radius, cy + s.y * radius, dash);
        }

        // Car in the middle
        float carR = radius * 0.18f;
        fill.setColor(Widgets.BG);
        c.drawCircle(cx, cy, carR, fill);
        line.setStrokeWidth(1.2f * dp);
        line.setAlpha(90);
        c.drawCircle(cx, cy, carR, line);
        line.setAlpha(230);
        line.setStrokeWidth(1.6f * dp);
        float cw = carR * 0.46f, ch = carR * 1.12f;
        c.drawRoundRect(new RectF(cx - cw / 2, cy - ch / 2, cx + cw / 2, cy + ch / 2), cw * 0.5f, cw * 0.5f, line);
        c.drawArc(new RectF(cx - cw * 0.32f, cy + ch * 0.12f, cx + cw * 0.32f, cy + ch * 0.3f), 20, 140, false, line);
        fill.setColor(Widgets.INK);
        c.drawCircle(cx, cy, 2.6f * dp * k, fill);

        // Sounds: small dots when off, line-art discs when on
        for (SoundSource s : sources) {
            if (s.locked) continue;
            float[] st = state(s);
            float target = s.enabled ? 1f : 0f;
            if (Math.abs(st[0] - target) > 0.001f) { st[0] += (target - st[0]) * Math.min(1f, dt * 10); busy = true; }
            if (st[1] > 0.001f) { st[1] *= (float) Math.exp(-dt * 6); busy = true; } else st[1] = 0;
            float pop = 1f + 0.3f * st[1] * (float) Math.cos(st[1] * 9);
            float px = cx + s.x * radius, py = cy + s.y * radius;
            float on = st[0];
            if (on < 0.05f) continue; // off sounds aren't on the stage; they come in by dragging a tile
            {
                float r = (28 + 20 * s.volume) * dp * k * pop * (s == dragging ? 1.12f : 1f) * (0.3f + 0.7f * on);
                if (s == dragging && dragOut) { // "놓으면 꺼짐": faint dashed ring only
                    line.setStrokeWidth(1.2f * dp);
                    line.setAlpha(120);
                    line.setPathEffect(new android.graphics.DashPathEffect(new float[] {5 * dp, 5 * dp}, 0));
                    c.drawCircle(px, py, r, line);
                    line.setPathEffect(null);
                    text.setTypeface(Typeface.DEFAULT_BOLD);
                    text.setTextSize(16 * dp * k);
                    text.setColor(Widgets.INK);
                    c.drawText("놓으면 꺼짐", px, py + r + 24 * dp * k, text);
                    continue;
                }
                fill.setColor(Widgets.BG);
                c.drawCircle(px, py, r, fill);
                line.setStrokeWidth(1.5f * dp);
                line.setAlpha(240);
                c.drawCircle(px, py, r, line);
                if (s == selected) {
                    line.setStrokeWidth(1f * dp);
                    line.setAlpha(150);
                    c.drawCircle(px, py, r + 7 * dp * k, line);
                }
                LineArt.draw(c, s.id, px, py, r * 0.86f, 235, dp);
                if (birdEdit && s == birds) continue; // perches sit around the dot in edit mode; skip its label
                text.setTypeface(Typeface.DEFAULT_BOLD);
                text.setTextSize(17 * dp * k);
                text.setColor(Widgets.INK);
                c.drawText(s.name, px, py + r + 24 * dp * k, text);
                mono.setTextSize(12 * dp * k);
                c.drawText(side(s.x, s.y) + "  /  " + Math.round(s.volume * 100), px, py + r + 44 * dp * k, mono);
            }
        }
        // Bird names pop up at their perch when they call (fade over 2.5 s)
        if (birds != null && !birds.locked && birds.enabled) {
            long nowMs = System.nanoTime() / 1_000_000;
            text.setTypeface(Typeface.DEFAULT);
            text.setTextSize(12 * dp * k);
            for (int i = 0; i < Birds.ALL.length; i++) {
                float age = (nowMs - birds.calledAt[i]) / 2500f;
                boolean show = birdEdit || (age >= 0 && age < 1 && birds.calledAt[i] > 0);
                if (!show) continue;
                float[] pp = perchPos(i);
                float a = birdEdit ? 1f : (age < 0.15f ? age / 0.15f : 1 - (age - 0.15f) / 0.85f);
                boolean on = birds.speciesOn[i];
                float rr = (birdEdit ? 9 : 5) * dp * k;
                fill.setColor(birdEdit && on ? Widgets.INK : Widgets.BG);
                fill.setAlpha((int) (255 * a));
                c.drawCircle(pp[0], pp[1], rr, fill);
                line.setStrokeWidth(1.2f * dp);
                line.setAlpha((int) ((on ? 220 : 90) * a));
                c.drawCircle(pp[0], pp[1], rr, line);
                if (!birdEdit) { // a small ripple while it sings
                    line.setAlpha((int) (90 * a));
                    c.drawCircle(pp[0], pp[1], rr + age * 26 * dp * k, line);
                }
                text.setColor(on ? Widgets.INK : Widgets.MUTED);
                text.setAlpha((int) (255 * a));
                c.drawText(Birds.ALL[i].name + (birdEdit && !on ? " (꺼짐)" : ""), pp[0], pp[1] - rr - 6 * dp * k, text);
                busy = true;
            }
            text.setAlpha(255);
            if (birdEdit) {
                text.setTextSize(15 * dp * k);
                text.setColor(Widgets.INK);
                c.drawText("새 자리 편집 · 끌어서 옮기고, 눌러서 켜고 끄기 · 빈 곳을 누르면 끝", cx, getHeight() - 16 * dp * k, text);
            }
        }
        if (dropHint != null) { // where the dragged tile will land
            float px = cx + dropX * radius, py = cy + dropY * radius, r = (28 + 20 * dropHint.volume) * dp * k + 14 * dp * k;
            line.setStrokeWidth(1.4f * dp);
            line.setAlpha(220);
            line.setPathEffect(new android.graphics.DashPathEffect(new float[] {6 * dp, 5 * dp}, 0));
            c.drawCircle(px, py, r, line);
            line.setPathEffect(null);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(16 * dp * k);
            text.setColor(Widgets.INK);
            c.drawText(locked ? dropHint.name + " 켜기" : dropHint.name + " · 여기에 놓기", px, py + r + 24 * dp * k, text);
        }
        if (locked) {
            text.setTypeface(Typeface.DEFAULT);
            text.setTextSize(15 * dp * k);
            text.setColor(Widgets.MUTED);
            c.drawText("주행 중에는 위치 이동이 잠겨요", cx, getHeight() - 16 * dp * k, text);
        }
        if (busy) postInvalidateOnAnimation(); else lastFrame = 0;
    }

    private void drop(float x, float y, float strength) {
        Ripple r = new Ripple();
        r.x = x; r.y = y; r.strength = strength;
        r.start = SystemClock.uptimeMillis();
        ripples.add(r);
        if (ripples.size() > 8) ripples.remove(0);
        postInvalidateOnAnimation();
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (birdEdit) {
                    draggingPerch = nearestPerch(x, y);
                    downX = x; downY = y; moved = false;
                    if (draggingPerch < 0) drop(x, y, 0.6f);
                    return true;
                }
                longPressed = false;
                dragging = nearest(x, y);
                if (dragging instanceof Birds) postDelayed(longPress, 550);
                downX = lastTickX = x; downY = lastTickY = y; moved = false; dragOut = false;
                if (dragging != null) { startX = dragging.x; startY = dragging.y; }
                drop(x, y, dragging != null ? 1f : 0.6f);
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (birdEdit) {
                    if (draggingPerch < 0 || locked) return true;
                    if (Math.hypot(x - downX, y - downY) > 8 * dp) moved = true;
                    if (moved) {
                        float nx = (x - cx) / radius, ny = (y - cy) / radius;
                        float d = (float) Math.hypot(nx, ny);
                        if (d > 1) { nx /= d; ny /= d; }
                        birds.perchX[draggingPerch] = nx - birds.x;
                        birds.perchY[draggingPerch] = ny - birds.y;
                        invalidate();
                    }
                    return true;
                }
                if (dragging == null) return true;
                if (Math.hypot(x - downX, y - downY) > 8 * dp) { moved = true; removeCallbacks(longPress); }
                if (moved && !locked) {
                    float nx = (x - cx) / radius, ny = (y - cy) / radius;
                    float d = (float) Math.hypot(nx, ny);
                    boolean out = dragging.enabled && d > OFF_DIST;
                    if (out != dragOut) { dragOut = out; performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); }
                    if (d > 1) { nx /= d; ny /= d; }
                    dragging.x = nx; dragging.y = ny;
                    if (Math.hypot(x - lastTickX, y - lastTickY) > 40 * dp) {
                        lastTickX = x; lastTickY = y;
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    }
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(longPress);
                if (birdEdit) {
                    if (draggingPerch >= 0) {
                        if (!moved) {
                            birds.speciesOn[draggingPerch] = !birds.speciesOn[draggingPerch];
                            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        }
                        listener.onStageChanged();
                    } else if (!longPressed) {
                        setBirdEdit(false);
                    }
                    draggingPerch = -1;
                    longPressed = false;
                    invalidate();
                    return true;
                }
                if (longPressed) { dragging = null; longPressed = false; return true; }
                if (dragging != null) {
                    if (!moved) { // a tap only selects; on/off is drag in / drag out
                        state(dragging)[1] = 1f;
                    } else if (dragOut) { // pulled off the stage: switch off, put it back where it was
                        dragging.x = startX; dragging.y = startY;
                        dragOut = false;
                        selected = dragging;
                        SoundSource off = dragging;
                        dragging = null;
                        listener.onSoundDropped(off, false);
                        invalidate();
                        return true;
                    } else {
                        drop(x, y, 0.8f);
                    }
                    selected = dragging;
                    listener.onSelected(dragging);
                    dragging = null;
                    invalidate();
                    listener.onStageChanged();
                    performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (dragging != null && dragOut) { dragging.x = startX; dragging.y = startY; }
                dragging = null; dragOut = false;
                invalidate();
                return true;
        }
        return false;
    }

    @Override public boolean performClick() { return super.performClick(); }

    void pop(SoundSource s) {
        state(s)[1] = 1f;
        drop(cx + s.x * radius, cy + s.y * radius, 1.1f);
    }

    private int nearestPerch(float x, float y) {
        int best = -1;
        double bestD = 34 * dp * k;
        for (int i = 0; i < Birds.ALL.length; i++) {
            float[] pp = perchPos(i);
            double d = Math.hypot(pp[0] - x, pp[1] - y);
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }

    private SoundSource nearest(float x, float y) {
        SoundSource best = null;
        double bestD = 52 * dp * k;
        for (SoundSource s : sources) {
            if (s.locked || !s.enabled) continue; // only sounds on the stage can be grabbed
            double d = Math.hypot(cx + s.x * radius - x, cy + s.y * radius - y);
            if (d < bestD) { bestD = d; best = s; }
        }
        return best;
    }
}
