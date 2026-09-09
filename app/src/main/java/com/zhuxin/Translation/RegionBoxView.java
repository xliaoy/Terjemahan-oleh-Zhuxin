package com.zhuxin.Translation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * 识别区域框：裁剪框式（类图片裁剪工具）。
 * 虚线边框 + 4 个角点 L 形托架 + 4 条边中点条，无线点，直观。
 * 窗口本身设置为 FLAG_NOT_TOUCHABLE，不拦截任何触摸；缩放由独立的角点把手窗口负责。
 * 角点把手窗口拖动逻辑在 FloatingWindowService 中，本类只绘制边框与把手视觉。
 */
public class RegionBoxView extends FrameLayout {

    public static final int TOP_LEFT = 0;
    public static final int TOP_RIGHT = 1;
    public static final int BOTTOM_LEFT = 2;
    public static final int BOTTOM_RIGHT = 3;
    public static final int TOP = 4;
    public static final int BOTTOM = 5;
    public static final int LEFT = 6;
    public static final int RIGHT = 7;

    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleOuterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextView textView;

    public RegionBoxView(Context context) {
        super(context);
        float density = getResources().getDisplayMetrics().density;

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2 * density);
        borderPaint.setColor(Color.parseColor("#FF6D00"));
        borderPaint.setPathEffect(new DashPathEffect(new float[]{16 * density, 10 * density}, 0));

        // 把手外圈（深色描边，保证在任意背景上可辨）
        handleOuterPaint.setStyle(Paint.Style.STROKE);
        handleOuterPaint.setStrokeWidth(8 * density);
        handleOuterPaint.setStrokeCap(Paint.Cap.ROUND);
        handleOuterPaint.setStrokeJoin(Paint.Join.ROUND);
        handleOuterPaint.setColor(0x99000000);

        // 把手主体（白色）
        handlePaint.setStyle(Paint.Style.STROKE);
        handlePaint.setStrokeWidth(4 * density);
        handlePaint.setStrokeCap(Paint.Cap.ROUND);
        handlePaint.setStrokeJoin(Paint.Join.ROUND);
        handlePaint.setColor(Color.WHITE);

        textView = new TextView(context);
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        textView.setBackgroundColor(0xA6000000);
        textView.setPadding((int) (12 * density), (int) (6 * density),
                (int) (12 * density), (int) (6 * density));
        textView.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP);
        addView(textView, lp);
    }

    /** 显示翻译结果，覆盖在识别区域顶部；传 null 或空串隐藏。 */
    public void setRegionText(String s) {
        if (s == null || s.isEmpty()) {
            textView.setText("");
            textView.setVisibility(View.GONE);
        } else {
            textView.setText(s);
            textView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        canvas.drawRect(1, 1, w - 1, h - 1, borderPaint);

        float d = getResources().getDisplayMetrics().density;
        float arm = 24 * d;       // 角点 L 形托架臂长
        float thickness = 4 * d;  // 白色臂宽
        float bar = 26 * d;       // 边中点条半长
        float barTh = 4 * d;      // 边中点条宽

        // 四个角点：L 形托架（角点区域内两段垂直臂）
        drawCorner(canvas, 0, 0, false, false, arm, thickness);
        drawCorner(canvas, w, 0, true, false, arm, thickness);
        drawCorner(canvas, 0, h, false, true, arm, thickness);
        drawCorner(canvas, w, h, true, true, arm, thickness);

        // 四边中点：短条
        drawEdgeBar(canvas, w / 2f, 0, 0, true, bar, barTh);
        drawEdgeBar(canvas, w / 2f, h, 0, true, bar, barTh);
        drawEdgeBar(canvas, 0, h / 2f, 0, false, bar, barTh);
        drawEdgeBar(canvas, w, h / 2f, 0, false, bar, barTh);
    }

    /** 画一个角点 L 形托架：以 (x,y) 为角，向矩形内侧/外侧延伸两条垂直臂。 */
    private void drawCorner(Canvas c, float x, float y, boolean toLeft, boolean toTop,
                            float arm, float thickness) {
        float dx = toLeft ? -arm : arm;
        float dy = toTop ? -arm : arm;
        // 内斜内缩一点，让托架看起来包住角点
        float inset = thickness;
        c.drawLine(x + (toLeft ? inset : -inset), y - dy, x + dx - (toLeft ? -inset : inset), y,
                handleOuterPaint);
        c.drawLine(x - dx, y + (toTop ? inset : -inset), x, y + dy - (toTop ? -inset : inset),
                handleOuterPaint);
        c.drawLine(x + (toLeft ? inset : -inset), y - dy, x + dx - (toLeft ? -inset : inset), y,
                handlePaint);
        c.drawLine(x - dx, y + (toTop ? inset : -inset), x, y + dy - (toTop ? -inset : inset),
                handlePaint);
    }

    /** 画一条边中点短条。horizontal=true 时 (x,y) 为中点、条水平；否则竖直。 */
    private void drawEdgeBar(Canvas c, float x, float y, float ignored, boolean horizontal,
                             float halfLen, float thickness) {
        if (horizontal) {
            c.drawLine(x - halfLen, y, x + halfLen, y, handleOuterPaint);
            c.drawLine(x - halfLen, y, x + halfLen, y, handlePaint);
        } else {
            c.drawLine(x, y - halfLen, x, y + halfLen, handleOuterPaint);
            c.drawLine(x, y - halfLen, x, y + halfLen, handlePaint);
        }
    }
}