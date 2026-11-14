package dev.homesev.sounddrive;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

/**
 * First-run demo: a ghost of a sound tile is carried by a fingertip onto the stage, on a loop,
 * until the listener drops a real tile there once. Draws over everything, never takes touches.
 */
final class CoachView extends View {
    interface Host {
        /** An off sound whose tile is on screen right now, or null. */
        Widgets.SoundTile coachTile();
        StageView coachStage();
        SoundSource[] coachSources();
        /** True while something else owns the screen (dark mode, bird edit, driving lock…). */
        boolean coachBlocked();
    }

    static final String TEXT = "소리를 무대로 끌어 놓아 보세요";
    private static final long PRESS = 650, CARRY = 1300, RELEASE = 450, PAUSE = 1200, LOOP = PRESS + CARRY + RELEASE + PAUSE;
    private static final long IDLE_MS = 6000;
    private static final float[][] SPOTS = {{-0.45f, -0.35f}, {0.45f, -0.35f}, {-0.45f, 0.4f}, {0.45f, 0.4f}, {0f, -0.55f}};

    private final Host host;
    private final float dp, k;
    private final boolean still; // system animations off → text only
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] me = new int[2], there = new int[2];
    private final Rect vis = new Rect();
    private long loopStart, hiddenUntil;
    private boolean done;
    // per-loop geometry, fixed at the start of each loop
    private SoundSource ghost;
    private float fromX, fromY, toX, toY;

    CoachView(Context c, Host host, float k) {
        super(c);
        this.host = host;
        this.k = k;
        dp = c.getResources().getDisplayMetrics().density;
        float scale = android.provider.Settings.Global.getFloat(c.getContentResolver(), android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        still = scale == 0f;
        line.setStyle(Paint.Style.STROKE);
        line.setColor(Widgets.INK);
        fill.setColor(Widgets.BG);
        text.setColor(Widgets.MUTED);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.DEFAULT);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        loopStart = SystemClock.uptimeMillis() + 800; // let the screen settle first
    }

    /** Any touch hides the demo; it comes back after a quiet moment. */
    void poke() {
        if (done) return;
        hiddenUntil = SystemClock.uptimeMillis() + IDLE_MS;
        ghost = null;
        invalidate();
    }

    void finish() {
        done = true;
        setVisibility(GONE);
    }

    @Override public boolean onTouchEvent(android.view.MotionEvent e) { return false; }

    @Override protected void onDraw(Canvas c) {
        if (done) return;
        long now = SystemClock.uptimeMillis();
        StageView stage = host.coachStage();
        if (stage == null || host.coachBlocked() || now < hiddenUntil || now < loopStart) {
            if (now >= hiddenUntil && hiddenUntil != 0) { hiddenUntil = 0; loopStart = now + 300; }
            postInvalidateDelayed(250);
            return;
        }
        getLocationInWindow(me);
        stage.getLocationInWindow(there);
        float sx = there[0] - me[0], sy = there[1] - me[1];
        // hint line at the bottom of the stage
        text.setTextSize(15 * dp * k);
        text.setAlpha(230);
        c.drawText(TEXT, sx + stage.getWidth() / 2f, sy + stage.getHeight() - 16 * dp * k, text);
        if (still) { postInvalidateDelayed(500); return; }

        long t = now - loopStart;
        if (t >= LOOP || ghost == null) { // new loop: pick the tile and an empty spot again
            if (!pickLoop(stage, sx, sy)) { postInvalidateDelayed(400); return; }
            loopStart = now;
            t = 0;
        }
        float x, y, press, alpha = 1f;
        float pop = 1f; // the ghost pops loose near the end of the hold, like a real lift
        if (t < PRESS) { x = fromX; y = fromY; press = 1f - 0.15f * Math.min(1f, t / 350f); pop = t < 420 ? 0f : Math.min(1f, (t - 420) / 230f); }
        else if (t < PRESS + CARRY) {
            float u = (t - PRESS) / (float) CARRY, e = u < 0.5f ? 2 * u * u : 1 - (float) Math.pow(-2 * u + 2, 2) / 2;
            x = fromX + (toX - fromX) * e;
            y = fromY + (toY - fromY) * e - (float) Math.sin(Math.PI * e) * 40 * dp * k; // slight arc
            press = 0.85f;
        } else if (t < PRESS + CARRY + RELEASE) {
            float u = (t - PRESS - CARRY) / (float) RELEASE;
            x = toX; y = toY; press = 0.85f + 0.15f * u; alpha = 1f - u;
            line.setStrokeWidth(1.2f * dp);
            line.setAlpha((int) (160 * (1 - u)));
            c.drawCircle(x, y, (30 + 40 * u) * dp * k, line); // landing ripple
        } else { postInvalidateOnAnimation(); return; }

        float r = 28 * dp * k * (pop < 1f ? 0.8f + 0.35f * pop : 1.1f);
        alpha *= pop;
        fill.setAlpha((int) (150 * alpha));
        c.drawCircle(x, y, r, fill);
        line.setStrokeWidth(1.4f * dp);
        line.setAlpha((int) (140 * alpha));
        c.drawCircle(x, y, r, line);
        LineArt.draw(c, ghost.id, x, y, r * 0.86f, (int) (140 * alpha), dp);
        line.setStrokeWidth(1.6f * dp);
        line.setAlpha((int) (180 * (t < PRESS ? 1f : alpha)));
        c.drawCircle(x + 10 * dp * k, y + 14 * dp * k, 20 * dp * k * press, line); // fingertip
        postInvalidateOnAnimation();
    }

    private boolean pickLoop(StageView stage, float sx, float sy) {
        Widgets.SoundTile tile = host.coachTile();
        if (tile == null || !tile.getGlobalVisibleRect(vis)) return false;
        tile.getLocationInWindow(there);
        ghost = tile.s;
        fromX = there[0] - me[0] + tile.getWidth() / 2f;
        fromY = there[1] - me[1] + Math.min(tile.getWidth() * 0.3f, 40 * dp * Widgets.scale) + 10 * dp * Widgets.scale;
        float best = -1, bx = 0, by = 0;
        for (float[] p : SPOTS) {
            float nearest = Float.MAX_VALUE;
            for (SoundSource s : host.coachSources()) {
                if (s.locked || !s.enabled) continue;
                nearest = Math.min(nearest, (float) Math.hypot(s.x - p[0], s.y - p[1]));
            }
            if (nearest > best) { best = nearest; bx = p[0]; by = p[1]; }
        }
        float[] v = stage.toView(bx, by);
        toX = sx + v[0];
        toY = sy + v[1];
        return true;
    }
}
