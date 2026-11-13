package dev.homesev.sounddrive;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/** Monochrome line-style widgets used by the screen (tick slider, sound tile, round button). */
final class Widgets {
    private Widgets() {}

    static final int INK = 0xFFEDEDED, MUTED = 0xFF8A8A8A, FAINT = 0x33FFFFFF, BG = 0xFF0B0B0B, PANEL = 0xFF111111;
    static final Typeface MONO = Typeface.MONOSPACE;
    /** Size multiplier for the current screen (the car head unit is ≈1.8). */
    static float scale = 1f;

    /** Background that draws a bottom line when on (active preset tab). */
    static final class Underline extends android.graphics.drawable.Drawable {
        private final Paint p = new Paint();
        private final int thickness;
        private boolean on;
        Underline(int thicknessPx) { thickness = thicknessPx; p.setColor(INK); }
        void setOn(boolean v) { on = v; invalidateSelf(); }
        @Override public void draw(Canvas c) { if (on) { android.graphics.Rect b = getBounds(); c.drawRect(b.left, b.bottom - thickness, b.right, b.bottom, p); } }
        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(android.graphics.ColorFilter f) {}
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /** A slider drawn as a row of ticks: full ticks up to the thumb, dots after it. */
    static final class TickSlider extends View {
        interface OnChange { void on(float value); }
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float dp;
        private float value;
        private OnChange listener;

        TickSlider(Context c) {
            super(c);
            dp = c.getResources().getDisplayMetrics().density * scale;
            p.setStrokeCap(Paint.Cap.ROUND);
            setMinimumHeight((int) (44 * dp));
        }

        void setValue(float v) { value = Math.max(0, Math.min(1, v)); invalidate(); }
        void setOnChange(OnChange l) { listener = l; }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), (int) (44 * dp));
        }

        @Override protected void onDraw(Canvas c) {
            float left = 10 * dp, right = getWidth() - 10 * dp, cy = getHeight() / 2f, step = 4.2f * dp;
            float thumb = left + (right - left) * value;
            p.setColor(INK);
            for (float x = left; x <= right; x += step) {
                if (x <= thumb - 8 * dp) {
                    p.setAlpha(230); p.setStrokeWidth(1.4f * dp);
                    c.drawLine(x, cy - 9 * dp, x, cy + 9 * dp, p);
                } else if (x >= thumb + 8 * dp) {
                    p.setAlpha(90); p.setStrokeWidth(1.2f * dp);
                    c.drawLine(x, cy - 1.5f * dp, x, cy + 1.5f * dp, p);
                }
            }
            p.setAlpha(255);
            p.setStyle(Paint.Style.FILL);
            p.setColor(BG);
            c.drawCircle(thumb, cy, 8 * dp, p);
            p.setColor(INK);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.6f * dp);
            c.drawCircle(thumb, cy, 8 * dp, p);
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(thumb, cy, 2.2f * dp, p);
            p.setStyle(Paint.Style.STROKE);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE || a == MotionEvent.ACTION_UP) {
                getParent().requestDisallowInterceptTouchEvent(a != MotionEvent.ACTION_UP);
                float left = 10 * dp, right = getWidth() - 10 * dp;
                float v = Math.max(0, Math.min(1, (e.getX() - left) / (right - left)));
                if (Math.abs(v - value) > 0.04f) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                value = v;
                invalidate();
                if (listener != null) listener.on(v);
                return true;
            }
            return a == MotionEvent.ACTION_CANCEL;
        }
    }

    /** One sound in the grid: line-art circle, volume arc when on, name and level below. */
    static final class SoundTile extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), t = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float dp, k;
        final SoundSource s;
        boolean selected;

        SoundTile(Context c, SoundSource s, float textScale) {
            super(c);
            this.s = s;
            dp = c.getResources().getDisplayMetrics().density * scale;
            k = textScale;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            t.setTextAlign(Paint.Align.CENTER);
        }

        @Override protected void onMeasure(int w, int h) {
            int width = MeasureSpec.getSize(w);
            float r = Math.min(width * 0.3f, 40 * dp); // same as onDraw
            setMeasuredDimension(width, (int) (r * 2 + 10 * dp + (24 + 19 + 10) * dp * k / scale));
        }

        @Override protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, r = Math.min(getWidth() * 0.3f, 40 * dp), cy = r + 10 * dp;
            boolean on = s.enabled;
            p.setColor(INK);
            p.setStrokeWidth(1.4f * dp);
            p.setAlpha(on ? 235 : 110);
            c.drawCircle(cx, cy, r, p);
            LineArt.draw(c, s.id, cx, cy, r, on ? 235 : 95, dp);
            if (on) { // volume arc
                p.setAlpha(235);
                p.setStrokeWidth(2f * dp);
                float rr = r + 7 * dp;
                c.drawArc(new RectF(cx - rr, cy - rr, cx + rr, cy + rr), -90, 300 * s.volume, false, p);
            }
            if (selected) {
                p.setAlpha(150);
                p.setStrokeWidth(1f * dp);
                c.drawCircle(cx, cy, r + 12 * dp, p);
            }
            t.setColor(on ? INK : MUTED);
            t.setTextSize(15 * dp * k / scale);
            t.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            float maxW = getWidth() - 6 * dp, w = t.measureText(s.name);
            if (w > maxW) t.setTextSize(t.getTextSize() * maxW / w); // long names shrink to fit their cell
            float ty = cy + r + 24 * dp * k / scale;
            c.drawText(s.name, cx, ty, t);
            if (on) {
                t.setTypeface(MONO);
                t.setColor(MUTED);
                t.setTextSize(12 * dp * k / scale);
                c.drawText(String.valueOf(Math.round(s.volume * 100)), cx, ty + 19 * dp * k / scale, t);
            }
        }
    }

    /** Big outlined circle with play / pause glyph. */
    static final class RoundButton extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float dp;
        private boolean playing;

        RoundButton(Context c) {
            super(c);
            dp = c.getResources().getDisplayMetrics().density;
            p.setColor(INK);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            setClickable(true);
            setHapticFeedbackEnabled(true);
        }

        void setPlaying(boolean on) { playing = on; invalidate(); }

        @Override protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(cx, cy) - 2 * dp;
            p.setStrokeWidth(1.6f * dp);
            p.setStyle(Paint.Style.STROKE);
            c.drawCircle(cx, cy, r, p);
            if (playing) {
                p.setStrokeWidth(2.2f * dp);
                c.drawLine(cx - r * 0.13f, cy - r * 0.25f, cx - r * 0.13f, cy + r * 0.25f, p);
                c.drawLine(cx + r * 0.13f, cy - r * 0.25f, cx + r * 0.13f, cy + r * 0.25f, p);
            } else {
                android.graphics.Path tri = new android.graphics.Path();
                tri.moveTo(cx - r * 0.16f, cy - r * 0.26f);
                tri.lineTo(cx + r * 0.28f, cy);
                tri.lineTo(cx - r * 0.16f, cy + r * 0.26f);
                tri.close();
                p.setStrokeWidth(2f * dp);
                c.drawPath(tri, p);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { animate().scaleX(0.95f).scaleY(0.95f).setDuration(90).start(); performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); }
            if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) animate().scaleX(1f).scaleY(1f).setDuration(180).start();
            return super.onTouchEvent(e);
        }
    }

    /** Small outlined circle icon button (mic, more). */
    static final class IconButton extends View {
        enum Kind { MIC, MORE }
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float dp;
        private final Kind kind;
        private boolean active;

        IconButton(Context c, Kind kind) {
            super(c);
            this.kind = kind;
            dp = c.getResources().getDisplayMetrics().density;
            p.setColor(INK);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            setClickable(true);
        }

        void setActive(boolean on) { active = on; invalidate(); }

        @Override protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(cx, cy) - 2 * dp;
            p.setStrokeWidth(1.3f * dp);
            p.setAlpha(active ? 255 : 150);
            c.drawCircle(cx, cy, r, p);
            p.setStrokeWidth(1.6f * dp);
            if (kind == Kind.MIC) {
                c.drawRoundRect(new RectF(cx - r * 0.18f, cy - r * 0.45f, cx + r * 0.18f, cy + r * 0.12f), r * 0.18f, r * 0.18f, p);
                c.drawArc(new RectF(cx - r * 0.32f, cy - r * 0.2f, cx + r * 0.32f, cy + r * 0.3f), 0, 180, false, p);
                c.drawLine(cx, cy + r * 0.3f, cx, cy + r * 0.45f, p);
            } else {
                Paint f = new Paint(p);
                f.setStyle(Paint.Style.FILL);
                for (int i = -1; i <= 1; i++) c.drawCircle(cx + i * r * 0.32f, cy, 1.8f * dp, f);
            }
        }
    }
}
