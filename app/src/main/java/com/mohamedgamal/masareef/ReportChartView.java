package com.mohamedgamal.masareef;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** Small native charts; labels and exact values remain available in adjacent text rows. */
public final class ReportChartView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long[] values = new long[0];
    private int[] colors = new int[0];
    private boolean trend;
    private boolean dark;
    private String center = "";

    public ReportChartView(Context context) { super(context); }

    public void setDark(boolean dark) { this.dark = dark; invalidate(); }

    public void setDonut(long[] values, int[] colors, String center) {
        this.values = values; this.colors = colors; this.center = center; this.trend = false;
        setContentDescription("توزيع: " + center);
        invalidate();
    }

    public void setTrend(long[] values, int color, String description) {
        this.values = values; this.colors = new int[]{color}; this.trend = true;
        setContentDescription(description);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (trend) drawTrend(canvas); else drawDonut(canvas);
    }

    private void drawDonut(Canvas canvas) {
        float density = getResources().getDisplayMetrics().density;
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) / 2f - 30 * density;
        RectF oval = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
        long total = 0; for (long value : values) total += Math.max(0, value);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(22 * density); paint.setStrokeCap(Paint.Cap.BUTT);
        if (total == 0) {
            paint.setColor(0xffe2e6ed); canvas.drawArc(oval, -90, 360, false, paint);
        } else {
            float start = -90;
            for (int i = 0; i < values.length; i++) {
                if (values[i] <= 0) continue;
                float sweep = 360f * values[i] / total;
                paint.setColor(colors[i % colors.length]);
                canvas.drawArc(oval, start, sweep, false, paint);
                start += sweep;
            }
        }
        paint.setStyle(Paint.Style.FILL); paint.setColor(dark ? 0xfff5f7fb : 0xff10182d);
        paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(18 * density);
        canvas.drawText(center, cx, cy + 6 * density, paint);
    }

    private void drawTrend(Canvas canvas) {
        float density = getResources().getDisplayMetrics().density;
        float left = 8 * density, right = getWidth() - 8 * density;
        float top = 12 * density, bottom = getHeight() - 18 * density;
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1 * density);
        paint.setColor(0xffe2e6ed); canvas.drawLine(left, bottom, right, bottom, paint);
        if (values.length == 0) return;
        long max = 1; for (long value : values) max = Math.max(max, value);
        Path line = new Path();
        for (int i = 0; i < values.length; i++) {
            float x = values.length == 1 ? left : left + (right - left) * i / (values.length - 1);
            float y = bottom - (bottom - top) * values[i] / max;
            if (i == 0) line.moveTo(x, y); else line.lineTo(x, y);
        }
        Path fill = new Path(line);
        fill.lineTo(right, bottom); fill.lineTo(left, bottom); fill.close();
        int color = colors.length == 0 ? 0xffff4b0b : colors[0];
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, top, 0, bottom,
                new int[]{(color & 0x00ffffff) | 0x66000000, Color.TRANSPARENT}, null, Shader.TileMode.CLAMP));
        canvas.drawPath(fill, paint); paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3 * density);
        paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(color); canvas.drawPath(line, paint);
    }
}
