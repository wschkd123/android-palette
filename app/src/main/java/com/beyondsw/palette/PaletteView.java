package com.beyondsw.palette;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Xfermode;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 画板
 * <p>
 * 支持撤销（undo）
 * <p>
 * 支持反撤销（redo）
 * <p>
 * 支持橡皮擦（eraser）
 * <p>
 * 支持清除功能（clear）
 * <p>
 * 支持保存为图像（save）
 */
public class PaletteView extends View {

    private Paint mPaint;
    private Path mPath;
    private float mLastX;
    private float mLastY;
    private Bitmap mBufferBitmap;
    private Canvas mBufferCanvas;

    private static final int MAX_CACHE_STEP = 20;

    private List<DrawingInfo> mDrawingList; //每一步的绘制信息
    private List<DrawingInfo> mRemovedList; //每一步撤销的绘制信息

    private Xfermode mXferModeClear;
    private Xfermode mXferModeDraw;
    private int mDrawSize;
    private int mEraserSize;
    private int mPenAlpha = 255;

    private boolean mCanEraser;

    private Callback mCallback;

    public enum Mode {
        DRAW,
        ERASER
    }

    private Mode mMode = Mode.DRAW;


    public PaletteView(Context context) {
        super(context);
        init();
    }

    public PaletteView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PaletteView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public interface Callback {
        void onUndoRedoStatusChanged();
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    private void init() {
        setDrawingCacheEnabled(true);
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setFilterBitmap(true);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mDrawSize = dp2px(getContext(), 3);
        mEraserSize = dp2px(getContext(), 30);
        mPaint.setStrokeWidth(mDrawSize);
        mPaint.setColor(0XFF000000);
        mXferModeDraw = new PorterDuffXfermode(PorterDuff.Mode.SRC);
        mXferModeClear = new PorterDuffXfermode(PorterDuff.Mode.CLEAR);
        mPaint.setXfermode(mXferModeDraw);
    }

    private void initBuffer() {
        mBufferBitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        mBufferCanvas = new Canvas(mBufferBitmap);
    }

    private abstract static class DrawingInfo {
        Paint paint;

        abstract void draw(Canvas canvas);
    }

    private static class PathDrawingInfo extends DrawingInfo {

        Path path;

        @Override
        void draw(Canvas canvas) {
            canvas.drawPath(path, paint);
        }
    }

    public Mode getMode() {
        return mMode;
    }

    public void setMode(Mode mode) {
        if (mode != mMode) {
            mMode = mode;
            if (mMode == Mode.DRAW) {
                mPaint.setXfermode(mXferModeDraw);
                mPaint.setStrokeWidth(mDrawSize);
            } else {
                mPaint.setXfermode(mXferModeClear);
                mPaint.setStrokeWidth(mEraserSize);
            }
        }
    }

    /**
     * 橡皮擦大小
     *
     * @param size
     */
    public void setEraserSize(int size) {
        mEraserSize = size;
        if (mMode == Mode.ERASER) {
            mPaint.setStrokeWidth(mEraserSize);
        }
    }

    /**
     * 画笔大小
     *
     * @param size
     */
    public void setPenRawSize(int size) {
        mDrawSize = size;
        if (mMode == Mode.DRAW) {
            mPaint.setStrokeWidth(mDrawSize);
        }
    }

    /**
     * 画笔颜色
     *
     * @param color
     */
    public void setPenColor(int color) {
        mPaint.setColor(color);
    }

    /**
     * 重绘每一步的轨迹
     */
    private void reDraw() {
        if (mDrawingList != null) {
            mBufferBitmap.eraseColor(Color.TRANSPARENT);
            for (DrawingInfo drawingInfo : mDrawingList) {
                drawingInfo.draw(mBufferCanvas);
            }
            invalidate();
        }
    }

    public int getPenColor() {
        return mPaint.getColor();
    }

    public int getPenSize() {
        return mDrawSize;
    }

    public int getEraserSize() {
        return mEraserSize;
    }

    public void setPenAlpha(int alpha) {
        mPenAlpha = alpha;
        if (mMode == Mode.DRAW) {
            mPaint.setAlpha(alpha);
        }
    }

    public int getPenAlpha() {
        return mPenAlpha;
    }

    public boolean canRedo() {
        return mRemovedList != null && mRemovedList.size() > 0;
    }

    public boolean canUndo() {
        return mDrawingList != null && mDrawingList.size() > 0;
    }

    /**
     * 撤销
     * <p>
     * 把最后一步删除，然后重绘
     */
    public void redo() {
        int size = mRemovedList == null ? 0 : mRemovedList.size();
        if (size > 0) {
            DrawingInfo info = mRemovedList.remove(size - 1);
            mDrawingList.add(info);
            mCanEraser = true;
            reDraw();
            if (mCallback != null) {
                mCallback.onUndoRedoStatusChanged();
            }
        }
    }

    /**
     * 反撤销
     * <p>
     * 撤销列表里面删除最后一步，然后重绘
     */
    public void undo() {
        int size = mDrawingList == null ? 0 : mDrawingList.size();
        if (size > 0) {
            DrawingInfo info = mDrawingList.remove(size - 1);
            if (mRemovedList == null) {
                mRemovedList = new ArrayList<>(MAX_CACHE_STEP);
            }
            if (size == 1) {
                mCanEraser = false;
            }
            mRemovedList.add(info);
            reDraw();
            if (mCallback != null) {
                mCallback.onUndoRedoStatusChanged();
            }
        }
    }

    public void clear() {
        if (mBufferBitmap != null) {
            if (mDrawingList != null) {
                mDrawingList.clear();
            }
            if (mRemovedList != null) {
                mRemovedList.clear();
            }
            mCanEraser = false;
            mBufferBitmap.eraseColor(Color.TRANSPARENT);
            invalidate();
            if (mCallback != null) {
                mCallback.onUndoRedoStatusChanged();
            }
        }
    }

    /**
     * 获取绘制的位图
     *
     * @return
     */
    public Bitmap buildBitmap() {
        Bitmap bm = getDrawingCache();
        Bitmap result = Bitmap.createBitmap(bm);
        destroyDrawingCache();
        return result;
    }

    /**
     * 每一步绘制完成，记录每一步的轨迹和画笔属性
     */
    private void saveDrawingPath() {
        if (mDrawingList == null) {
            mDrawingList = new ArrayList<>(MAX_CACHE_STEP);
        } /*else if (mDrawingList.size() == MAX_CACHE_STEP) {
            mDrawingList.remove(0);
        }*/
        Path cachePath = new Path(mPath);
        Paint cachePaint = new Paint(mPaint);
        PathDrawingInfo info = new PathDrawingInfo();
        info.path = cachePath;
        info.paint = cachePaint;
        mDrawingList.add(info);
        mCanEraser = true;
        if (mCallback != null) {
            mCallback.onUndoRedoStatusChanged();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mBufferBitmap != null) {
            canvas.drawBitmap(mBufferBitmap, 0, 0, null);
        }
    }

    @SuppressWarnings("all")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        final int action = event.getAction() & MotionEvent.ACTION_MASK;
        final float x = event.getX();
        final float y = event.getY();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mLastX = x;
                mLastY = y;
                if (mPath == null) {
                    mPath = new Path();
                }
                mPath.moveTo(x, y);
                break;
            case MotionEvent.ACTION_MOVE:
                //这里终点设为两点的中心点的目的在于使绘制的曲线更平滑，如果终点直接设置为x,y，效果和lineto是一样的,实际是折线效果
                mPath.quadTo(mLastX, mLastY, (x + mLastX) / 2, (y + mLastY) / 2);
                if (mBufferBitmap == null) {
                    initBuffer();
                }
                if (mMode == Mode.ERASER && !mCanEraser) {
                    break;
                }
                mBufferCanvas.drawPath(mPath, mPaint);
                invalidate();
                mLastX = x;
                mLastY = y;
                break;
            case MotionEvent.ACTION_UP:
                if (mMode == Mode.DRAW || mCanEraser) {
                    saveDrawingPath();
                }
                mPath.reset();
                break;
        }
        return true;
    }

    private static int dp2px(@NonNull Context context, int dp) {
        return (int) (context.getResources().getDisplayMetrics().density * dp + .5f);
    }
}
