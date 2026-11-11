package dev.homesev.sounddrive;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Offline radar: you in the middle (north up), unlock regions around you by direction and
 * distance. Distance is on a log scale (rings 1 / 10 / 100 km) because regions are far apart.
 */
final class RadarView extends View {
    interface Listener { void onPlaceTapped(Unlocks.Place p); }

    static final double RANGE_M = 300_000;
    private static final double MIN_M = 100;

    /** 0 at 100 m … 1 at 300 km (log scale). */
    static double scale(double d) {
        if (d <= MIN_M) return 0;
        return Math.min(1, Math.log10(d / MIN_M) / Math.log10(RANGE_M / MIN_M));
    }
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG),
            dot = new Paint(Paint.ANTI_ALIAS_FLAG), me = new Paint(Paint.ANTI_ALIAS_FLAG), bg = new Paint();
    private final float dp;
    private final Listener listener;
    private List<Unlocks.Place> places = new ArrayList<>();
    private boolean hasFix;
    private float cx, cy, radius;

    RadarView(Context ctx, Listener listener) {
        super(ctx);
        this.listener = listener;
        dp = ctx.getResources().getDisplayMetrics().density;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(1.5f * dp);
        ring.setColor(0x33FFFFFF);
        text.setColor(0x99FFFFFF);
        text.setTextAlign(Paint.Align.CENTER);
        me.setColor(0xFF3D7BFF);
    }

    void setPlaces(List<Unlocks.Place> p, boolean hasFix) {
        places = p;
        this.hasFix = hasFix;
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        cx = w / 2f; cy = h / 2f;
        radius = Math.min(w, h) / 2f - 36 * dp;
        bg.setShader(new RadialGradient(cx, cy, Math.max(w, h) * 0.7f, 0xFF1B2333, 0xFF0B0F17, Shader.TileMode.CLAMP));
    }

    private float[] screen(Unlocks.Place p) {
        double r = scale(p.distanceM) * radius;
        double a = Math.toRadians(p.bearingDeg);
        return new float[] {(float) (cx + Math.sin(a) * r), (float) (cy - Math.cos(a) * r)};
    }

    @Override protected void onDraw(Canvas c) {
        c.drawRect(0, 0, getWidth(), getHeight(), bg);
        text.setTextSize(13 * dp);
        double[] rings = {1000, 10_000, 100_000, RANGE_M};
        for (double r : rings) {
            float rr = (float) (scale(r) * radius);
            c.drawCircle(cx, cy, rr, ring);
            c.drawText((int) (r / 1000) + "km", cx + rr * 0.71f + 16 * dp, cy - rr * 0.71f, text);
        }
        text.setTextSize(16 * dp);
        c.drawText("N", cx, cy - radius - 12 * dp, text);

        if (!hasFix) {
            text.setTextSize(18 * dp);
            c.drawText("위치를 찾는 중이에요…", cx, cy + radius * 0.5f, text);
        }
        // Farther places first so nearer ones draw on top.
        for (int i = places.size() - 1; i >= 0; i--) {
            Unlocks.Place p = places.get(i);
            float[] s = screen(p);
            dot.setColor(p.category.color);
            dot.setAlpha(60);
            c.drawCircle(s[0], s[1], 18 * dp, dot);
            dot.setAlpha(255);
            c.drawCircle(s[0], s[1], 4 * dp, dot);
            text.setTextSize(18 * dp);
            text.setColor(Color.WHITE);
            c.drawText(p.category.emoji, s[0], s[1] - 8 * dp, text);
            text.setColor(0x99FFFFFF);
        }
        c.drawCircle(cx, cy, 9 * dp, me);
        me.setAlpha(70);
        c.drawCircle(cx, cy, 18 * dp, me);
        me.setAlpha(255);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() != MotionEvent.ACTION_UP) return true;
        Unlocks.Place best = null;
        double bestD = 32 * dp;
        for (Unlocks.Place p : places) {
            float[] s = screen(p);
            double d = Math.hypot(s[0] - e.getX(), s[1] - e.getY());
            if (d < bestD) { bestD = d; best = p; }
        }
        if (best != null) listener.onPlaceTapped(best);
        performClick();
        return true;
    }

    @Override public boolean performClick() { return super.performClick(); }
}
