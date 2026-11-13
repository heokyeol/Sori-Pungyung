package dev.homesev.sounddrive;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Monochrome line-art icons for every sound, drawn inside a circle (white strokes on black).
 * One small set of patterns, mapped per sound id, so ~60 sounds share a consistent hand.
 */
final class LineArt {
    private LineArt() {}

    enum Pattern { RAIN, WAVES, WIND, FLAME, BIRDS, RINGS, ZIGZAG, STARS, SATURN, BANDS, PLANE, BOOKS, BELL, RAILS, DROPS, STEAM, SEAM, REEDS, BAMBOO, SNOW, CRACK, LEAVES }

    static Pattern of(String id) {
        switch (id) {
            case "rain": case "r_jeonju": return Pattern.RAIN;
            case "waves": case "r_hangang": case "r_yeosu": case "r_tongyeong": case "r_udo": case "s_yangyang": case "s_haeundae": return Pattern.WAVES;
            case "wind": case "r_jeju": case "s_boseong": return Pattern.WIND;
            case "fire": return Pattern.FLAME;
            case "birds": case "u_harbor": case "r_cheorwon": case "r_ulleung": case "s_gasiri": return Pattern.BIRDS;
            case "pad": case "r_chuncheon": case "r_namhae": return Pattern.RINGS;
            case "thunder": return Pattern.ZIGZAG;
            case "cmb": case "r_taean": return Pattern.STARS;
            case "saturn": return Pattern.SATURN;
            case "jupiter": return Pattern.BANDS;
            case "u_airport": return Pattern.PLANE;
            case "u_library": return Pattern.BOOKS;
            case "u_temple": return Pattern.BELL;
            case "u_station": return Pattern.RAILS;
            case "u_valley": case "r_danyang": return Pattern.DROPS;
            case "u_cafe": return Pattern.STEAM;
            case "u_baseball": return Pattern.SEAM;
            case "r_suncheon": case "s_mindungsan": return Pattern.REEDS;
            case "r_andong": case "s_damyang": return Pattern.BAMBOO;
            case "s_daegwallyeong": case "s_pyeongchang": return Pattern.SNOW;
            case "s_hwacheon": return Pattern.CRACK;
            case "s_jinhae": case "s_maisan": case "s_naejangsan": return Pattern.LEAVES;
            default: return Pattern.RINGS;
        }
    }

    /** Draw the icon for sound id inside the circle (cx, cy, r). alpha 0..255. */
    static void draw(Canvas c, String id, float cx, float cy, float r, int alpha, float dp) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(0xFFFFFFFF);
        p.setAlpha(alpha);
        p.setStrokeWidth(Math.max(0.8f * dp, r * 0.028f));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        Path clip = new Path();
        clip.addCircle(cx, cy, r * 0.96f, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        long seed = id.hashCode();
        java.util.Random rnd = new java.util.Random(seed);
        Path path = new Path();
        switch (of(id)) {
            case RAIN:
                for (int i = 0; i < 26; i++) {
                    float x = cx - r + rnd.nextFloat() * 2 * r, y = cy - r + rnd.nextFloat() * 2 * r, l = r * (0.18f + rnd.nextFloat() * 0.22f);
                    c.drawLine(x, y, x - l * 0.35f, y + l, p);
                }
                break;
            case WAVES:
                for (int k = -3; k <= 3; k++) {
                    path.reset();
                    float y0 = cy + k * r * 0.24f;
                    for (int i = 0; i <= 40; i++) {
                        float x = cx - r + 2 * r * i / 40f;
                        float y = y0 + (float) Math.sin(i / 40f * Math.PI * 3 + k) * r * 0.07f;
                        if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
                    }
                    c.drawPath(path, p);
                }
                break;
            case WIND: case BANDS:
                for (int k = -4; k <= 4; k++) {
                    path.reset();
                    float y0 = cy + k * r * 0.2f;
                    float amp = r * (0.03f + 0.05f * rnd.nextFloat()), ph = rnd.nextFloat() * 6;
                    for (int i = 0; i <= 40; i++) {
                        float x = cx - r + 2 * r * i / 40f;
                        float y = y0 + (float) Math.sin(i / 40f * Math.PI * 2 + ph) * amp;
                        if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
                    }
                    c.drawPath(path, p);
                }
                if (of(id) == Pattern.BANDS) c.drawOval(new RectF(cx + r * 0.12f, cy + r * 0.12f, cx + r * 0.46f, cy + r * 0.3f), p);
                break;
            case FLAME: {
                float top = cy - r * 0.78f, bottom = cy + r * 0.72f;
                for (int k = -4; k <= 4; k++) {
                    path.reset();
                    float w = r * 0.42f * (1 - Math.abs(k) / 5.5f);
                    path.moveTo(cx, top);
                    path.cubicTo(cx + k * r * 0.1f + w * Math.signum(k + 0.01f) * 0.2f, cy - r * 0.2f, cx + k * r * 0.09f, cy + r * 0.3f, cx + k * r * 0.02f, bottom);
                    c.drawPath(path, p);
                }
                path.reset();
                path.moveTo(cx, top);
                path.cubicTo(cx + r * 0.62f, cy - r * 0.05f, cx + r * 0.4f, bottom, cx, bottom);
                path.cubicTo(cx - r * 0.4f, bottom, cx - r * 0.62f, cy - r * 0.05f, cx, top);
                c.drawPath(path, p);
                break;
            }
            case BIRDS:
                for (int row = 0; row < 4; row++) {
                    float y = cy - r * 0.45f + row * r * 0.32f;
                    for (int i = 0; i < 4; i++) {
                        float x = cx - r * 0.75f + i * r * 0.45f + (row % 2) * r * 0.2f, w = r * (0.12f + rnd.nextFloat() * 0.06f);
                        path.reset();
                        path.moveTo(x - w, y);
                        path.quadTo(x - w / 2, y - w * 0.7f, x, y);
                        path.quadTo(x + w / 2, y - w * 0.7f, x + w, y);
                        c.drawPath(path, p);
                    }
                }
                break;
            case RINGS:
                for (int k = 1; k <= 7; k++) c.drawCircle(cx, cy, r * k / 7.6f, p);
                break;
            case ZIGZAG:
                for (int k = -2; k <= 2; k++) {
                    path.reset();
                    float x = cx + k * r * 0.34f, y = cy - r;
                    path.moveTo(x, y);
                    for (int i = 0; i < 6; i++) { x += (i % 2 == 0 ? 1 : -1) * r * 0.16f; y += r * 0.34f; path.lineTo(x, y); }
                    c.drawPath(path, p);
                }
                break;
            case STARS: {
                Paint f = new Paint(p);
                f.setStyle(Paint.Style.FILL);
                for (int i = 0; i < 70; i++) {
                    float x = cx - r + rnd.nextFloat() * 2 * r, y = cy - r + rnd.nextFloat() * 2 * r;
                    c.drawCircle(x, y, (0.4f + rnd.nextFloat() * 1.1f) * dp * Math.max(0.6f, r / (30 * dp)), f);
                }
                break;
            }
            case SATURN: {
                c.drawCircle(cx, cy, r * 0.36f, p);
                c.save();
                c.rotate(-18, cx, cy);
                c.drawOval(new RectF(cx - r * 0.82f, cy - r * 0.2f, cx + r * 0.82f, cy + r * 0.2f), p);
                c.drawOval(new RectF(cx - r * 0.7f, cy - r * 0.15f, cx + r * 0.7f, cy + r * 0.15f), p);
                c.restore();
                break;
            }
            case PLANE:
                for (int k = -3; k <= 3; k++) {
                    float y = cy + k * r * 0.22f;
                    for (float x = cx - r; x < cx + r; x += r * 0.3f) c.drawLine(x, y, x + r * 0.16f, y, p);
                }
                break;
            case BOOKS:
                for (int i = 0; i < 9; i++) {
                    float x = cx - r * 0.7f + i * r * 0.17f, h = r * (0.6f + rnd.nextFloat() * 0.5f);
                    c.drawLine(x, cy + r * 0.55f, x, cy + r * 0.55f - h, p);
                }
                c.drawLine(cx - r, cy + r * 0.55f, cx + r, cy + r * 0.55f, p);
                break;
            case BELL:
                path.moveTo(cx - r * 0.5f, cy + r * 0.45f);
                path.cubicTo(cx - r * 0.45f, cy - r * 0.2f, cx - r * 0.3f, cy - r * 0.6f, cx, cy - r * 0.6f);
                path.cubicTo(cx + r * 0.3f, cy - r * 0.6f, cx + r * 0.45f, cy - r * 0.2f, cx + r * 0.5f, cy + r * 0.45f);
                path.close();
                c.drawPath(path, p);
                for (int k = 1; k <= 3; k++) c.drawLine(cx - r * 0.48f + k * 0.02f * r, cy + r * (0.45f - k * 0.22f), cx + r * 0.48f - k * 0.02f * r, cy + r * (0.45f - k * 0.22f), p);
                c.drawCircle(cx, cy + r * 0.6f, r * 0.07f, p);
                break;
            case RAILS:
                c.drawLine(cx - r * 0.15f, cy - r, cx - r * 0.55f, cy + r, p);
                c.drawLine(cx + r * 0.15f, cy - r, cx + r * 0.55f, cy + r, p);
                for (int i = 0; i < 8; i++) {
                    float t = i / 7f, y = cy - r + 2 * r * t, w = r * (0.2f + 0.4f * t);
                    c.drawLine(cx - w, y, cx + w, y, p);
                }
                break;
            case DROPS:
                for (int col = -3; col <= 3; col++) {
                    for (int i = 0; i < 5; i++) {
                        float x = cx + col * r * 0.26f, y = cy - r * 0.8f + i * r * 0.4f + (col % 2) * r * 0.2f;
                        path.reset();
                        path.moveTo(x, y - r * 0.08f);
                        path.quadTo(x + r * 0.06f, y + r * 0.02f, x, y + r * 0.05f);
                        path.quadTo(x - r * 0.06f, y + r * 0.02f, x, y - r * 0.08f);
                        c.drawPath(path, p);
                    }
                }
                break;
            case STEAM:
                for (int k = -1; k <= 1; k++) {
                    path.reset();
                    float x0 = cx + k * r * 0.3f;
                    for (int i = 0; i <= 30; i++) {
                        float y = cy + r * 0.7f - 1.4f * r * i / 30f, x = x0 + (float) Math.sin(i / 30f * Math.PI * 2 + k) * r * 0.12f;
                        if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
                    }
                    c.drawPath(path, p);
                }
                break;
            case SEAM:
                c.drawCircle(cx, cy, r * 0.75f, p);
                c.drawArc(new RectF(cx - r * 1.3f, cy - r * 0.75f, cx - r * 0.2f, cy + r * 0.75f), -60, 120, false, p);
                c.drawArc(new RectF(cx + r * 0.2f, cy - r * 0.75f, cx + r * 1.3f, cy + r * 0.75f), 120, 120, false, p);
                break;
            case REEDS:
                for (int i = 0; i < 11; i++) {
                    float x = cx - r * 0.8f + i * r * 0.16f, top = cy - r * (0.2f + rnd.nextFloat() * 0.6f);
                    path.reset();
                    path.moveTo(x, cy + r);
                    path.quadTo(x + r * 0.05f, cy, x + r * 0.12f, top);
                    c.drawPath(path, p);
                    c.drawOval(new RectF(x + r * 0.08f, top - r * 0.14f, x + r * 0.16f, top + r * 0.02f), p);
                }
                break;
            case BAMBOO:
                for (int i = 0; i < 6; i++) {
                    float x = cx - r * 0.65f + i * r * 0.26f;
                    c.drawLine(x, cy - r, x, cy + r, p);
                    for (int j = 0; j < 5; j++) { float y = cy - r + (j + rnd.nextFloat() * 0.4f) * r * 0.45f; c.drawLine(x - r * 0.04f, y, x + r * 0.04f, y, p); }
                }
                break;
            case SNOW: {
                Paint f = new Paint(p);
                f.setStyle(Paint.Style.FILL);
                for (int i = 0; i < 40; i++) {
                    float x = cx - r + rnd.nextFloat() * 2 * r, y = cy - r + rnd.nextFloat() * 2 * r;
                    c.drawCircle(x, y, (0.6f + rnd.nextFloat()) * dp, f);
                    if (i % 3 == 0) c.drawLine(x, y, x - r * 0.2f, y - r * 0.08f, p);
                }
                break;
            }
            case CRACK:
                for (int k = 0; k < 5; k++) {
                    path.reset();
                    float x = cx, y = cy;
                    path.moveTo(x, y);
                    double a = k * Math.PI * 2 / 5 + rnd.nextFloat();
                    for (int i = 0; i < 5; i++) {
                        a += (rnd.nextFloat() - 0.5f) * 0.8f;
                        x += (float) Math.cos(a) * r * 0.22f; y += (float) Math.sin(a) * r * 0.22f;
                        path.lineTo(x, y);
                    }
                    c.drawPath(path, p);
                }
                break;
            case LEAVES:
                for (int i = 0; i < 9; i++) {
                    float x = cx - r * 0.7f + rnd.nextFloat() * 1.4f * r, y = cy - r * 0.7f + rnd.nextFloat() * 1.4f * r;
                    c.save();
                    c.rotate(rnd.nextFloat() * 180, x, y);
                    c.drawOval(new RectF(x - r * 0.16f, y - r * 0.07f, x + r * 0.16f, y + r * 0.07f), p);
                    c.drawLine(x - r * 0.16f, y, x + r * 0.16f, y, p);
                    c.restore();
                }
                break;
        }
        c.restore();
    }
}
