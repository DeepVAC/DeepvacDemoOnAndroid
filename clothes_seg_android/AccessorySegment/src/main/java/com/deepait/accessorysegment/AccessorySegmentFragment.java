package com.deepait.accessorysegment;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.deepait.accessorysegment.common.component.CameraSetting;
import com.deepait.accessorysegment.common.component.DrawView;
import com.deepait.accessorysegment.common.fragment.BaseFragment;
import com.deepait.accessorysegment.common.sufaceHolder.DemoSurfaceHolder;
import com.deepait.accessorysegment.tnn.R;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;


public class AccessorySegmentFragment extends BaseFragment {

    private final static String TAG = AccessorySegmentFragment.class.getSimpleName();

    /**********************************     Define    **********************************/

    private SurfaceView mPreview;

    private DrawView mDrawView;
    private int mCameraWidth;
    private int mCameraHeight;

    Camera mOpenedCamera;
    int mOpenedCameraId = 0;
    DemoSurfaceHolder mDemoSurfaceHolder = null;

    private static final int NET_H_INPUT = 300;
    private static final int NET_W_INPUT = 300;

    int mCameraFacing = -1;
    int mRotate = 0;
    int cameraRotate = 0;
    SurfaceHolder mSurfaceHolder;

    private AccessorySegment mBodyDetector = new AccessorySegment();
    private boolean mIsDetectingObject = false;
    private FpsCounter mFpsCounter = new FpsCounter();
    private boolean mIsCountFps = false;

    private FrameLayout live_preview_layout;
    private int previewLayoutWidth;
    private int previewLayoutHeight;

    private ToggleButton mGPUSwitch;
    private boolean mUseGPU = false;
    //add for npu
    private ToggleButton mHuaweiNPUswitch;
    private boolean mUseHuaweiNpu = false;
    private TextView HuaweiNpuTextView;

    private boolean mDeviceSwiched = false;

    boolean isAsyncImage = true;
    ImageView maskImgView;
    private Bitmap maskBitmap;
    private int[] maskPixels;

    ImageView srcImgView;
    private Bitmap srcBitmap;
    private Bitmap cameraBitmap;
    private int[] srcPixels;

    TextView monitor_result_view;

    TextView result_view;
    /**********************************     Get Preview Advised    **********************************/

    boolean isInit = false;
    String mModelPath;

    HandlerThread handlerThread;
    Handler subHandler;
    Handler mainHandler;
    AtomicBoolean isSkip = new AtomicBoolean(false);
    Context mContext;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        System.loadLibrary("BodySegment");
        //start SurfaceHolder
        mDemoSurfaceHolder = new DemoSurfaceHolder(this);
        mModelPath = initModel();
        NpuEnable = mBodyDetector.checkNpu(mModelPath);

        handlerThread = new HandlerThread("ProcThread");
        handlerThread.start();
        subHandler = new Handler(handlerThread.getLooper());

        mainHandler = new Handler(getActivity().getMainLooper());

        mContext = getActivity();
    }

    private String initModel()
    {

        String targetDir =  getActivity().getFilesDir().getAbsolutePath();

        //copy detect model to sdcard
        String[] modelPathsDetector = {
//                "espnetv2_2.0_384x384.opt.tnnmodel",
//                "espnetv2_2.0_384x384.opt.tnnproto"
                "Clothes_Seg.opt.tnnmodel",
                "Clothes_Seg.opt.tnnproto"
//                "model.quantized.tnnmodel",
//                "model.quantized.tnnproto"
        };

        // 以下值固定不要修改
        String[] modelNames = {
                "clothes.tnnmodel",
                "clothes.tnnproto"
        };


        for (int i = 0; i < modelPathsDetector.length; i++) {
            String modelFilePath = modelPathsDetector[i];
            //String interModelFilePath = targetDir + "/" + modelFilePath ;
            String interModelFilePath = targetDir + "/" + modelNames[i] ;
            File file = new File(interModelFilePath);
            if(file.exists()){
                try {
                    file.delete();
                }catch (Exception e){
                    //e.printStackTrace();
                }
            }
            Log.i("Test","Copy " + modelFilePath+" to "+ interModelFilePath);
            FileUtils.copyAsset(getActivity().getAssets(), modelFilePath, interModelFilePath);
        }
        return targetDir;
    }

    @Override
    public void onClick(View view) {
        int i = view.getId();
        if (i == R.id.back_rl) {
            clickBack();
        }
    }

//    private void restartCamera()
//    {
//        closeCamera();
//        openCamera(mCameraFacing);
//        startPreview(mSurfaceHolder);
//    }

    private void onSwichGPU(boolean b)
    {
        if (b && mHuaweiNPUswitch.isChecked()) {
            mHuaweiNPUswitch.setChecked(false);
            mUseHuaweiNpu = false;
        }
        mUseGPU = b;
        result_view.setText("");
        mDeviceSwiched = true;
    }

    private void onSwichNPU(boolean b)
    {
        if (b && mGPUSwitch.isChecked()) {
            mGPUSwitch.setChecked(false);
            mUseGPU = false;
        }
        mUseHuaweiNpu = b;
        result_view.setText("");
        mDeviceSwiched = true;
    }

    private void clickBack() {
        if (getActivity() != null) {
            (getActivity()).finish();
        }
    }

    @Override
    public void setFragmentView() {
        Log.d(TAG, "setFragmentView");
        setView(R.layout.fragment_body_detector);
        setTitleGone();
        $$(R.id.gpu_switch);
        $$(R.id.back_rl);

        live_preview_layout = (FrameLayout) $(R.id.live_preview_layout);
        mGPUSwitch = $(R.id.gpu_switch);
        mGPUSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                onSwichGPU(b);
            }
        });

        $$(R.id.npu_switch);
        mHuaweiNPUswitch = $(R.id.npu_switch);
        mHuaweiNPUswitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                onSwichNPU(b);
            }
        });
        HuaweiNpuTextView = $(R.id.npu_text);
        if (!NpuEnable) {
            HuaweiNpuTextView.setVisibility(View.INVISIBLE);
            mHuaweiNPUswitch.setVisibility(View.INVISIBLE);
        }

        // modify ----------------
        TextView gpuTextView = $(R.id.gpu_text);
        gpuTextView.setVisibility(View.INVISIBLE);
        mGPUSwitch.setVisibility(View.INVISIBLE);
        //

        init();
    }


    private void init() {
        mPreview = $(R.id.live_detection_preview);
        result_view = (TextView)$(R.id.result);

        Button btnSwitchCamera = $(R.id.switch_camera);
        btnSwitchCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeCamera();
                if (mCameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    openCamera(Camera.CameraInfo.CAMERA_FACING_BACK);
                }
                else {
                    openCamera(Camera.CameraInfo.CAMERA_FACING_FRONT);
                }
                startPreview(mSurfaceHolder);
            }
        });


        mDrawView = (DrawView) $(R.id.drawView);
        isInit = true;

        srcImgView = (ImageView)$(R.id.srcImgView);
        maskImgView = (ImageView)$(R.id.maskImgView);

        monitor_result_view = (TextView) $(R.id.monitor_result);
    }

    @Override
    public void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();
        if (null != mDemoSurfaceHolder) {
            SurfaceHolder holder = mPreview.getHolder();
            holder.setKeepScreenOn(true);
            mDemoSurfaceHolder.setSurfaceHolder(holder);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");

        getFocus();
        preview();
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.i(TAG, "onStop");
        super.onStop();
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");


        if(mBodyDetector!=null) {
            mBodyDetector.deinit();
            mBodyDetector = null;
        }

        maskImgView.setImageBitmap(null);
        if(maskBitmap!=null && (!maskBitmap.isRecycled())){
            maskBitmap.recycle();
        }
        maskBitmap = null;

        srcImgView.setImageBitmap(null);
        if(srcBitmap!=null && (!srcBitmap.isRecycled())){
            srcBitmap.recycle();
        }
        srcBitmap = null;

        if(cameraBitmap!=null && !cameraBitmap.isRecycled()){
            cameraBitmap.recycle();
        }
        cameraBitmap = null;


        try {
            handlerThread.interrupt();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void preview() {
        Log.i(TAG, "preview");

    }

    private void getFocus() {
        getView().setFocusableInTouchMode(true);
        getView().requestFocus();
        getView().setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
                    clickBack();
                    return true;
                }
                return false;
            }
        });
    }

    /**********************************     Camera    **********************************/

    boolean isFrontCamera = true;
    public void openCamera() {
        //openCamera(Camera.CameraInfo.CAMERA_FACING_BACK);
        openCamera(Camera.CameraInfo.CAMERA_FACING_FRONT);
    }

    private void openCamera(int cameraFacing) {
        mIsDetectingObject = true;
        mCameraFacing = cameraFacing;
        try {
            int numberOfCameras = Camera.getNumberOfCameras();
            if (numberOfCameras < 1) {
                Log.e(TAG, "no camera device found");
            } else if (1 == numberOfCameras) {
                mOpenedCamera = Camera.open(0);
                mOpenedCameraId = 0;

                // 只有一个摄像头的时候，重新设置
                Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
                Camera.getCameraInfo(0, cameraInfo);
                mCameraFacing = cameraInfo.facing;
            } else {
                Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
                for (int i = 0; i < numberOfCameras; i++) {
                    Camera.getCameraInfo(i, cameraInfo);
                    if (cameraInfo.facing == cameraFacing) {
                        mOpenedCamera = Camera.open(i);
                        mOpenedCameraId = i;
                        break;
                    }
                }
            }
            if (mOpenedCamera == null) {
                Log.e(TAG, "can't find camera");
            }
            else {

                int r = CameraSetting.initCamera(mContext.getApplicationContext(),mOpenedCamera,mOpenedCameraId);
                if (r == 0) {
                    //设置摄像头朝向
                    CameraSetting.setCameraFacing(cameraFacing);

                    Camera.Parameters parameters = mOpenedCamera.getParameters();
                    isFrontCamera = mCameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT;
                    mRotate = CameraSetting.getRotate(mContext, mOpenedCameraId, mCameraFacing);
                    cameraRotate = CameraSetting.getVideoRotate(mContext, mOpenedCameraId);

                    mCameraWidth = parameters.getPreviewSize().width;
                    mCameraHeight = parameters.getPreviewSize().height;



                    //int width = getContext().getResources().getDisplayMetrics().widthPixels;

                    mDrawView.previewWidth = mCameraHeight;
                    mDrawView.previewHeight = mCameraWidth;

                    RelativeLayout.LayoutParams rlp = (RelativeLayout.LayoutParams) live_preview_layout.getLayoutParams();
                    if(rlp==null){
                        rlp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                    }
                    int sw = getActivity().getResources().getDisplayMetrics().widthPixels;
                    int sh = getActivity().getResources().getDisplayMetrics().heightPixels;
                    int nw = sw;
                    int nh = nw * mDrawView.previewHeight / mDrawView.previewWidth;
                    if(nh > sh){
                        nh = sh;
                        nw = nh * mDrawView.previewWidth / mDrawView.previewHeight;
                    }
                    rlp.width = nw;
                    rlp.height = nh;
                    previewLayoutWidth = nw;
                    previewLayoutHeight = nh;
                    live_preview_layout.setLayoutParams(rlp);


                    //modelPath = initModel();
                    int device = 0;
                    if (mUseHuaweiNpu) {
                        device = 2;
                    } else if (mUseGPU) {
                        device = 1;
                    }
                    int ret = mBodyDetector.init(mModelPath, device);
                    if (ret == 0) {
                        mIsDetectingObject = true;
                    } else {
                        mIsDetectingObject = false;
                        Log.e(TAG, "Face detector init failed " + ret);
                    }

                    ret = mFpsCounter.init();
                    if (ret == 0) {
                        mIsCountFps = true;
                    } else {
                        mIsCountFps = false;
                        Log.e(TAG, "Fps Counter init failed " + ret);
                    }
                } else {
                    Log.e(TAG, "Failed to init camera");
                }
            }
        }
        catch (Exception e) {
            Log.e(TAG, "open camera failed:" + e.getLocalizedMessage());
        }
    }

    byte[] previewData = null;
    int previewWidth = 0;
    int previewHeight = 0;
    public void startPreview(SurfaceHolder surfaceHolder) {
             startPreview2(surfaceHolder);
    }
    public void startPreview2(SurfaceHolder surfaceHolder) {

        try {
            if (null != mOpenedCamera) {
                Log.i(TAG, "start preview, is previewing");
                mOpenedCamera.setPreviewCallback(new Camera.PreviewCallback() {
                    @Override
                    public void onPreviewFrame(byte[] data, Camera camera) {
                        if (mIsDetectingObject && isInit && data.length>0) {
                            if(!isSkip.get()) {
                                isSkip.set(true);
                                Camera.Parameters mCameraParameters = camera.getParameters();
                                Camera.Size size = mCameraParameters.getPreviewSize();

                                mRotate = CameraSetting.getRotate(mContext, mOpenedCameraId, mCameraFacing);
                                cameraRotate = CameraSetting.getCameraRotate(mContext, mOpenedCameraId);
                                // Log.e("Test","mOpenedCameraId:"+mOpenedCameraId+" mRotate:"+mRotate+" cameraRotate:"+cameraRotate);
                                if (previewWidth != size.width || previewHeight != size.height) {
                                    previewWidth = size.width;
                                    previewHeight = size.height;
                                    previewData = new byte[data.length];
                                }

                                if (mIsCountFps) {
                                    mFpsCounter.begin("ObjectDetect");
                                }
                                // reinit
                                if (mDeviceSwiched) {
                                    String modelPath = getActivity().getFilesDir().getAbsolutePath();
                                    int device = 0;
                                    if (mUseHuaweiNpu) {
                                        device = 2;
                                    } else if (mUseGPU) {
                                        device = 1;
                                    }
                                    int ret = mBodyDetector.init(modelPath, device);
                                    if (ret == 0) {
                                        mIsDetectingObject = true;
                                    } else {
                                        mIsDetectingObject = false;
                                        Log.e(TAG, "Face detector init failed " + ret);
                                    }
                                    mDeviceSwiched = false;
                                }

                                int iw = previewWidth;
                                int ih = previewHeight;
                                if (mRotate >= 5 && mRotate <= 8) {
                                    iw = previewHeight;
                                    ih = previewWidth;
                                }
                                if (maskBitmap == null || maskBitmap.isRecycled()) {
                                    maskBitmap = Bitmap.createBitmap(iw, ih, Bitmap.Config.ARGB_8888);
                                    maskPixels = new int[iw * ih];
                                    maskBitmap.getPixels(maskPixels, 0, iw, 0, 0, iw, ih);
                                }
                                if (maskBitmap.getWidth() != iw || maskBitmap.getHeight() != ih) {
                                    maskImgView.setImageBitmap(null);
                                    maskBitmap.recycle();
                                    maskBitmap = Bitmap.createBitmap(iw, ih, Bitmap.Config.ARGB_8888);
                                    maskPixels = new int[iw * ih];
                                    maskBitmap.getPixels(maskPixels, 0, iw, 0, 0, iw, ih);
                                }
                                if (isAsyncImage) {
                                    if (srcBitmap == null || srcBitmap.isRecycled()) {
                                        srcBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
                                        srcPixels = new int[previewWidth * previewHeight];
                                        srcBitmap.getPixels(srcPixels, 0, previewWidth, 0, 0, previewWidth, previewHeight);

                                        cameraBitmap = Bitmap.createBitmap(iw, ih, Bitmap.Config.ARGB_8888);
                                    }
                                    if (srcBitmap.getWidth() != previewWidth || srcBitmap.getHeight() != previewHeight) {
                                        srcImgView.setImageBitmap(null);
                                        srcBitmap.recycle();
                                        srcBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
                                        srcPixels = new int[previewWidth * previewHeight];
                                        srcBitmap.getPixels(srcPixels, 0, previewWidth, 0, 0, previewWidth, previewHeight);

                                        cameraBitmap.recycle();
                                        cameraBitmap = Bitmap.createBitmap(iw, ih, Bitmap.Config.ARGB_8888);
                                    }
                                }

                                System.arraycopy(data,0,previewData,0,data.length);

                                int finalIw = iw;
                                int finalIh = ih;
                                subHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        boolean isDetect = mBodyDetector.predictFromStream(previewData, previewWidth, previewHeight, mRotate, true, true, maskPixels);
                                        mainHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (mIsCountFps) {
                                                    mFpsCounter.end("ObjectDetect");
                                                    double fps = mFpsCounter.getFps("ObjectDetect");
//                                                    String monitorResult = "device: ";

//                                                    if (mUseGPU) {
//                                                        monitorResult += "opencl\n";
//                                                    } else if (mUseHuaweiNpu) {
//                                                        monitorResult += "huawei_npu\n";
//                                                    } else {
//                                                        monitorResult += "arm\n";
//                                                    }
                                                    String monitorResult = "fps: " + String.format("%.02f", fps);
                                                    String finalMonitorResult = monitorResult;
                                                    monitor_result_view.setText(finalMonitorResult);
                                                }

                                                if (isDetect && maskBitmap!=null && (!maskBitmap.isRecycled())) {
                                                    maskBitmap.setPixels(maskPixels, 0, finalIw, 0, 0, finalIw, finalIh);

                                                    maskImgView.setVisibility(View.VISIBLE);
                                                    maskImgView.setImageBitmap(maskBitmap);
                                                    if (isAsyncImage && srcBitmap!=null && (!srcBitmap.isRecycled())) {
                                                        AccessorySegment.YUVtoARBG(previewData, previewWidth, previewHeight, srcPixels);
                                                        srcBitmap.setPixels(srcPixels, 0, previewWidth, 0, 0, previewWidth, previewHeight);
                                                        Canvas canvas = new Canvas(cameraBitmap);
                                                        Matrix mk = new Matrix();
                                                        if (cameraRotate == 90) {
                                                            float dx = (previewWidth - previewHeight) / 2.0f;
                                                            mk.postRotate(cameraRotate, previewWidth / 2.0f, previewHeight / 2.0f);
                                                            //mk.postScale(1, -1);
                                                            mk.postTranslate(-dx, dx);

                                                            //float scale = (float)previewLayoutWidth / (float) previewHeight;
                                                            //mk.postScale(scale,scale);
                                                        } else if (cameraRotate == 270) {
                                                            float dx = (previewWidth - previewHeight) / 2.0f;
                                                            mk.postRotate(cameraRotate, previewWidth / 2.0f, previewHeight / 2.0f);
                                                            mk.postScale(-1, 1);
                                                            mk.postTranslate(dx + previewHeight, dx);
                                                            //float scale = (float)previewLayoutWidth / (float) previewHeight;
                                                            //mk.postScale(scale,scale);
                                                        } else {
                                                            //float scale = (float)previewLayoutHeight / (float) previewWidth;
                                                            //mk.postScale(scale,scale);
                                                        }
                                                        canvas.drawBitmap(srcBitmap, mk, null);

                                                        srcImgView.setImageBitmap(cameraBitmap);
                                                        srcImgView.setVisibility(View.VISIBLE);
                                                    }
                                                } else {
                                                    maskImgView.setVisibility(View.INVISIBLE);
                                                    srcImgView.setVisibility(View.INVISIBLE);
                                                    Log.e("Test", "Detect Result: Not Found!");
                                                }
                                                isSkip.set(false);
                                            }
                                        });
                                    }
                                });
                            }
                        }
                    }
                });
                mOpenedCamera.setPreviewDisplay(surfaceHolder);
                mOpenedCamera.startPreview();
                mSurfaceHolder = surfaceHolder;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    public void closeCamera() {
        Log.i(TAG, "closeCamera");
        mIsDetectingObject = false;
        if (mOpenedCamera != null) {
            try {
                mOpenedCamera.stopPreview();
                mOpenedCamera.setPreviewCallback(null);
                Log.i(TAG, "stop preview, not previewing");
            } catch (Exception e) {
                e.printStackTrace();
                Log.i(TAG, "Error setting camera preview: " + e.toString());
            }
            try {
                mOpenedCamera.release();
                mOpenedCamera = null;
            } catch (Exception e) {
                e.printStackTrace();
                Log.i(TAG, "Error setting camera preview: " + e.toString());
            } finally {
                mOpenedCamera = null;
            }
        }
        if(mBodyDetector!=null) {
            mBodyDetector.deinit();
            // mBodyDetector = null;
        }

        maskImgView.setImageBitmap(null);
        if(maskBitmap!=null && (!maskBitmap.isRecycled())){
            maskBitmap.recycle();
        }
        maskBitmap = null;
    }

}
