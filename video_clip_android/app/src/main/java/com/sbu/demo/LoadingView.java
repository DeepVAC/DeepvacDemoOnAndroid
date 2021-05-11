package com.sbu.demo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.widget.ImageView;

import java.lang.ref.SoftReference;

import androidx.appcompat.widget.AppCompatImageView;

import com.sbu.videoclip.R;


//public class LoadingView extends ImageView {
public class LoadingView extends AppCompatImageView {
    private LoadingRunable runnable;
    private int width;
    private int height;

    public LoadingView(Context context) {
        super(context);
        init();
    }

    public LoadingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LoadingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        this.runnable = null;
    }

    private void init() {
        this.setScaleType(ImageView.ScaleType.MATRIX);
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_loading_white_01);
        this.setImageBitmap(bitmap);
        this.width = bitmap.getWidth() / 2;
        this.height = bitmap.getHeight() / 2;
        this.runnable = new LoadingRunable(this);
    }

    public void setLoadingImage(int resid) {
        this.setScaleType(ImageView.ScaleType.MATRIX);
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), resid);
        this.setImageBitmap(bitmap);
        this.width = bitmap.getWidth() / 2;
        this.height = bitmap.getHeight() / 2;
        this.runnable = new LoadingRunable(this);
    }

    public void startLoad() {
        if (this.runnable == null) {
            this.runnable = new LoadingRunable(this);
        }
        this.runnable.startload();
    }

    public void stopLoad() {
        if (this.runnable != null) {
            this.runnable.stopload();
        }
    }

    static class LoadingRunable implements Runnable {
        private boolean flag;
        private SoftReference<LoadingView> loadingViewSoftReference;
        private float degrees = 0f;
        private Matrix max;

        public LoadingRunable(LoadingView loadingView) {
            loadingViewSoftReference = new SoftReference<>(loadingView);
            max = new Matrix();
        }

        @Override
        public void run() {
            if (loadingViewSoftReference.get().runnable != null && max != null) {
                degrees += 30f;
                max.setRotate(degrees, loadingViewSoftReference.get().width, loadingViewSoftReference.get().height);
                loadingViewSoftReference.get().setImageMatrix(max);
                if (degrees == 360) {
                    degrees = 0;
                }
                if (flag) {
                    loadingViewSoftReference.get().postDelayed(loadingViewSoftReference.get().runnable, 80);
                }
            }
        }

        public void stopload() {
            flag = false;
        }

        public void startload() {
            flag = true;
            if (loadingViewSoftReference.get().runnable != null && max != null) {
//                Log.d("LoadingView", "startload========4444444===");
                loadingViewSoftReference.get().postDelayed(loadingViewSoftReference.get().runnable, 80);
            }
        }
    }
}
