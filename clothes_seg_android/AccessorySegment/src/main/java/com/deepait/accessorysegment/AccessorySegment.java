package com.deepait.accessorysegment;

public class AccessorySegment {
    public native int init(String modelPath, int computeType);
    public native boolean checkNpu(String modelPath);
    public native int deinit();
    public native boolean predictFromStream(byte[] yuv420sp, int width, int height, int rotate, boolean detectBody, boolean detectHead,int[] outputData);

    public static native void YUVtoARBG(byte yuv420sp[], int width, int height, int argbOut[]);
}