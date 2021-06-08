package com.deepait.accessorysegment.common.component;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceView;

import java.util.ArrayList;

public class DrawView extends SurfaceView
{
    private static String TAG = DrawView.class.getSimpleName();
    private Paint paint = new Paint();
    private Paint key_paint = new Paint();
    private ArrayList<String> labels = new ArrayList<String>();
    private ArrayList<Rect> rects = new ArrayList<Rect>();
    private ArrayList<float[]> points_list = new ArrayList<float[]>();
    private String humanKeyPointStr = null;
    private boolean isFrontCamera = true;

    public int previewWidth = 0;
    public int previewHeight = 0;

    private Context mContext;

    public DrawView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        mContext = context;
        paint.setARGB(255, 0, 255, 0);
        key_paint.setARGB(255, 0, 255, 0);
        paint.setStyle(Paint.Style.STROKE);
        key_paint.setStyle(Paint.Style.STROKE);
        key_paint.setStrokeWidth(5);
        setWillNotDraw(false);
    }


    @Override
    protected void onDraw(Canvas canvas)
    {
        if (rects.size() > 0)
        {
            for (int i=0; i<rects.size(); i++) {
                Log.d(TAG, "rect " + rects.get(i));
                paint.setARGB(255, 0, 255, 0);
                canvas.drawRect(rects.get(i), paint);
                if(labels.size() > 0) {
                    canvas.drawText(labels.get(i), rects.get(i).left, rects.get(i).top - 5, paint);
                }
            }
        }

        if(points_list.size() > 0) {
            for(int i = 0; i < points_list.size(); ++i) {
                float[] points = points_list.get(i);
                canvas.drawPoints(points, key_paint);
            }
        }

    }

}
