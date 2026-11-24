package com.example.hw_online_paint;


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public class DrawingView extends View {

    // 告訴 MainActivity2 要傳送哪些畫筆事件
    public interface OnStrokeListener {
        void onBegin(String id, float x, float y);
        void onPoint(String id, float x, float y);
        void onEnd(String id);
        void onRemove(String id);
    }

    private OnStrokeListener listener;

    private static class Stroke {
        String owner;
        String id;
        int color;

        ArrayList<PointF> points = new ArrayList<>();

        Stroke(String owner, String id, int color) {
            this.owner = owner;
            this.id = id;
            this.color = color;
        }
    }

    private final LinkedHashMap<String, Stroke> strokes =
            new LinkedHashMap<>();

    private final Paint brush =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    private String myId = null;
    private int myColor = Color.MAGENTA;

    private boolean eraserMode = false;

    private Stroke currentStroke;
    private int strokeNumber = 0;

    public DrawingView(Context context) {
        super(context);

        setBackgroundColor(Color.WHITE);

        brush.setStyle(Paint.Style.STROKE);
        brush.setStrokeWidth(
                5f * getResources()
                        .getDisplayMetrics().density
        );
        brush.setStrokeCap(Paint.Cap.ROUND);
        brush.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setOnStrokeListener(
            OnStrokeListener listener) {
        this.listener = listener;
    }

    // Server 告訴我們自己的 ID 和顏色
    public void setIdentity(String id, int color) {
        myId = id;
        myColor = color;
    }

    public void setEraserMode(boolean enabled) {
        eraserMode = enabled;
    }

    public boolean isEraserMode() {
        return eraserMode;
    }

    private String key(String owner, String id) {
        return owner + ":" + id;
    }

    private float nx(float x) {
        return Math.max(0f, Math.min(
                1f, x / Math.max(1, getWidth())
        ));
    }

    private float ny(float y) {
        return Math.max(0f, Math.min(
                1f, y / Math.max(1, getHeight())
        ));
    }

    // 畫出所有使用者的筆跡
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        for (Stroke s : strokes.values()) {
            if (s.points.isEmpty()) continue;

            brush.setColor(s.color);
            brush.setStyle(Paint.Style.STROKE);

            Path path = new Path();
            PointF first = s.points.get(0);

            path.moveTo(
                    first.x * getWidth(),
                    first.y * getHeight()
            );

            for (int i = 1; i < s.points.size(); i++) {
                PointF p = s.points.get(i);

                path.lineTo(
                        p.x * getWidth(),
                        p.y * getHeight()
                );
            }

            if (s.points.size() == 1) {
                brush.setStyle(Paint.Style.FILL);
                canvas.drawCircle(
                        first.x * getWidth(),
                        first.y * getHeight(),
                        brush.getStrokeWidth() / 2f,
                        brush
                );
            } else {
                canvas.drawPath(path, brush);
            }
        }
    }

    // 偵測使用者畫畫
    @Override
    public boolean onTouchEvent(MotionEvent event) {

        // 尚未連上 Server，不允許畫畫
        if (myId == null) return true;

        float x = nx(event.getX());
        float y = ny(event.getY());

        switch (event.getActionMasked()) {

            case MotionEvent.ACTION_DOWN:

                // 橡皮擦：整筆刪除
                if (eraserMode) {
                    eraseAt(event.getX(), event.getY());
                    return true;
                }

                String id = "s" +
                        System.currentTimeMillis() +
                        "_" + (++strokeNumber);

                currentStroke = new Stroke(
                        myId, id, myColor
                );

                currentStroke.points.add(
                        new PointF(x, y)
                );

                strokes.put(
                        key(myId, id), currentStroke
                );

                if (listener != null) {
                    listener.onBegin(id, x, y);
                }

                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:

                if (currentStroke != null) {
                    currentStroke.points.add(
                            new PointF(x, y)
                    );

                    if (listener != null) {
                        listener.onPoint(
                                currentStroke.id, x, y
                        );
                    }

                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:

                if (currentStroke != null) {
                    currentStroke.points.add(
                            new PointF(x, y)
                    );

                    if (listener != null) {
                        listener.onPoint(
                                currentStroke.id, x, y
                        );
                        listener.onEnd(
                                currentStroke.id
                        );
                    }

                    currentStroke = null;
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_CANCEL:

                if (currentStroke != null) {
                    if (listener != null) {
                        listener.onEnd(
                                currentStroke.id
                        );
                    }
                    currentStroke = null;
                }
                return true;
        }

        return true;
    }

    // 接收 Server 廣播的新筆跡
    public void remoteBegin(
            String owner, String id,
            int color, float x, float y) {

        // 自己的筆跡已在本機顯示，避免重複
        if (owner.equals(myId)) return;

        Stroke s = new Stroke(owner, id, color);
        s.points.add(new PointF(x, y));

        strokes.put(key(owner, id), s);
        invalidate();
    }

    // 接收其他使用者的座標
    public void remotePoint(
            String owner, String id,
            float x, float y) {

        if (owner.equals(myId)) return;

        Stroke s = strokes.get(key(owner, id));

        if (s != null) {
            s.points.add(new PointF(x, y));
            invalidate();
        }
    }

    // 移除指定筆跡
    public void removeStroke(String owner, String id) {
        strokes.remove(key(owner, id));
        invalidate();
    }

    // 移除指定使用者所有筆跡
    public void removeOwner(String owner) {

        strokes.entrySet().removeIf(
                entry -> entry.getValue()
                        .owner.equals(owner)
        );

        invalidate();
    }

    // 清除全部使用者筆跡
    public void clearAll() {
        strokes.clear();
        currentStroke = null;
        invalidate();
    }

    // 撤銷自己最後一筆
    public String undoMine() {

        String lastId = null;

        for (Stroke s : strokes.values()) {
            if (s.owner.equals(myId)) {
                lastId = s.id;
            }
        }

        if (lastId != null) {
            removeStroke(myId, lastId);
        }

        return lastId;
    }

    // 找到手指附近的自己的筆跡並刪除
    private void eraseAt(float x, float y) {

        ArrayList<Stroke> list =
                new ArrayList<>(strokes.values());

        float threshold = 20f *
                getResources().getDisplayMetrics().density;

        float limit = threshold * threshold;

        for (int i = list.size() - 1; i >= 0; i--) {
            Stroke s = list.get(i);

            if (!s.owner.equals(myId)) continue;

            for (int j = 0; j < s.points.size(); j++) {

                PointF p = s.points.get(j);

                float px = p.x * getWidth();
                float py = p.y * getHeight();

                float dx = x - px;
                float dy = y - py;

                if (dx * dx + dy * dy < limit) {

                    removeStroke(myId, s.id);

                    if (listener != null) {
                        listener.onRemove(s.id);
                    }
                    return;
                }
            }
        }
    }
}
