package com.scls.library;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

import com.sbd.library.BuildConfig;
import com.sbd.library.ShotSegmention;

import javax.security.auth.login.LoginException;

import static java.lang.Math.min;
import static java.lang.Math.max;


public class SceneRecognition {
    private String model_path_;
    private Context context_;

    private List<String> resultLabel = Arrays.asList("travel","sport","dinner","coffee","face","pet","playground","office","car","car_interior");//new ArrayList<>();
    private String[] validFileSuffix = {"jpg", "jpeg", "png"};

    private int validImgMinSize = 224;
    private int validImgMaxSize = 4096;
    private static final int COLOR_FormatI420 = 1;
    private static final int COLOR_FormatNV21 = 2;

    static {
        System.loadLibrary("scene_recognition-lib");
    }


    public native void sceneClsInit(String modelPath);
    public native int[] sceneClsInference(Bitmap bitmap);
    //旧版本的抽帧规则，每个镜头抽取rate帧
    public native int[] sceneFrameExtract(int[] frames, int length, int rate);
    //最新版本的抽帧规则，每秒抽取2帧
    public native int[] sceneFrameExtractV1(int[] frames, int length, int frameRate);
    public native int[] sceneFindMax(int[] indexes, int length);
    public native int[] sceneClsInferenceBytes(byte[] rgb, int width, int height);
    public native int[] sceneClsInferenceYUV(byte[] yuv, int width, int height);
    public native void sceneRelease();

//    public native float[] sceneClsInference1(Bitmap bitmap);

    public void release(){
        sceneRelease();
    }
    public SceneRecognition(Context context) {
        context_ = context;
        String cachePath = FileStorageHelper.getDiskCacheDir(context_);
        model_path_ = cachePath + "/model";
        FileStorageHelper.copyFilesFromRaw(context_, R.raw.sls_bin, "sls.bin", model_path_);
        FileStorageHelper.copyFilesFromRaw(context_, R.raw.sls_param, "sls.param", model_path_);
        // BuildConfig.VERSION_CODE
        readCacheLabelFromLocalFile();
        sceneClsInit(model_path_);
    }

    public Vector inference_img(String path) {
        //check img suffix, only support jpg,jpeg,png
        String suffix = path.substring(path.lastIndexOf(".") + 1).toLowerCase();
        boolean valid = false;

        Vector vec = new Vector();
        for(int i = 0; i < validFileSuffix.length; ++i) {
            if(suffix.equals(validFileSuffix[i])) {
                valid = true;
                break;
            }
        }
        if(!valid) {
            vec.add("unsupport img format, only support jpg,jpeg,png");
            return vec;
        }

        try {
            FileInputStream fis = new FileInputStream(path);
            Bitmap bitmap  = BitmapFactory.decodeStream(fis);
            Bitmap.Config config = bitmap.getConfig();
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            boolean validSize = checkImgSizeValid(width, height);
            if(!validSize) {
                vec.add("unsupport img size");
                return vec;
            }

            int[] index;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if(config == Bitmap.Config.RGBA_F16) {
                    byte[] pixel = new byte[width*height*3];
                    int flag = 0;
                    for(int h = 0; h < height; ++h) {
                        for(int w = 0; w < width; ++w) {
                            int p = bitmap.getPixel(w,h);
                            int r = Color.red(p);
                            int g = Color.green(p);
                            int b = Color.blue(p);
                            pixel[flag++] = (byte)r;
                            pixel[flag++] = (byte)g;
                            pixel[flag++] = (byte)b;
                        }
                    }

                    index = sceneClsInferenceBytes(pixel, width, height);
                } else {
                    index = sceneClsInference(bitmap);
                }
            } else {
                index = sceneClsInference(bitmap);
            }

            if(index[1] == -1) {
                vec.add("something wrong, pls tell the developer");
                return vec;
            }

            vec.add(String.valueOf(index[0]));
            vec.add(resultLabel.get(index[1]));
            vec.add(index[2]);// score*1000
        } catch(IOException io) {
            vec.add("parse img error");
            return vec;
        }

        return vec;
    }

    public Vector inference_video(String path) {
        return inference_video(path, 0, -1);
    }
    public Vector inference_video(String path, long cutStartMs, long cutEndMs) {
        return inference_video(path, cutStartMs, cutEndMs, false);
    }

    public Vector inference_video(String path, long cutStartMs, long cutEndMs, boolean useKeyFrame) {
        Vector vec = new Vector();

        String suffix = path.substring(path.lastIndexOf(".") + 1).toLowerCase();
        if(!suffix.equals("mp4")) {
            vec.add("unsupport video format");
            return vec;
        }

        return inferenceVideoInternal_fast(path, cutStartMs, cutEndMs, useKeyFrame);
    }


    long minDifferenceValue(long a, long b, long c) {
        if (a == b) {
            return Math.min(a, c);
        }
        long f_a = Math.abs(a - c);
        long f_b = Math.abs(b - c);
        if (f_a == f_b) {
            return Math.min(a, b);
        }
        if (f_a < f_b) {
            return a;
        }
        return b;
    }


    public long getValidSampleTime(long time, long frameDuration, MediaExtractor extractor) {
        extractor.seekTo(time, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        long sampleTime = extractor.getSampleTime();
        long topTime = time + 2000000;
        boolean isFind = false;
        while (!isFind) {
            extractor.advance();
            long s = extractor.getSampleTime();
            if (s != -1L) {
                // 选取和目标时间差值最小的那个
                sampleTime = minDifferenceValue(sampleTime, s, time);
                isFind = Math.abs(sampleTime - time) < frameDuration;
            } else {
                isFind = true;
            }
        }


        return sampleTime;
    }


    private int findMaxIndex(int[] indexes) {
        int value = indexes[0];
        int index = 0;
        for(int i = 1; i < indexes.length; ++i) {
            if(indexes[i] > value) {
                value = indexes[i];
                index = i;
            }
        }
        return index;
    }

    private static int selectTrack(MediaExtractor extractor) {
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                return i;
            }
        }
        return -1;
    }

    //逻辑改变，不进行镜头分割操作，直接进行每秒2帧的场景识别检测
    private Vector inferenceVideoInternal_fast(String videoPath, long cutStartMs, long cutEndMs, boolean useKeyFrame) {
        Vector vec = new Vector();
        Vector scoreVec = new Vector();
        Vector timeVec = new Vector(); // 视频时间戳
        int frameRate = 0;

        long startTime = System.currentTimeMillis();

        MediaExtractor extractor;
        MediaFormat mediaFormat;
        String mime;
        int width;
        int height;

        try {
            extractor = new MediaExtractor();
            extractor.setDataSource(videoPath);
            int trackIndex = selectTrack(extractor);
            if (trackIndex < 0) {
                vec.add("unsupport video");
                return vec;
            }

            extractor.selectTrack(trackIndex);
            mediaFormat = extractor.getTrackFormat(trackIndex);
            width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
            height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
            mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            boolean validSize = checkImgSizeValid(width, height);
            if(!validSize) {
                vec.add("unsupport video resolution ratio");
                return vec;
            }

            if(mediaFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                frameRate = mediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE);
            } else {
                frameRate = 25;
            }

        } catch (IOException e) {
            e.printStackTrace();
            vec.add("parse video error");
            return vec;
        }

        MediaCodec decoder;
        try{
            decoder = MediaCodec.createDecoderByType(mime);
        } catch(IOException e) {
            return new Vector();
        }
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        decoder.configure(mediaFormat, null, null, 0);
        decoder.start();

        long duration = Long.MAX_VALUE;
        if(mediaFormat.containsKey(MediaFormat.KEY_DURATION)){
            duration = mediaFormat.getLong(MediaFormat.KEY_DURATION);
        }
        if(cutStartMs>=0){
            extractor.seekTo(cutStartMs*1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        }
        long firstTimeUs = cutStartMs * 1000;
        long lastTimeUs = cutEndMs*1000;
        if(lastTimeUs<=firstTimeUs || lastTimeUs > duration) {
            lastTimeUs = duration + 200;
        }

        int noDecodeInputBufferCnt = 0;
        int noDecodeOutputBufferCnt = 0;
        int maxLossCnt = 1000;

        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;

        long testclstime = System.currentTimeMillis();

        long infertime = 0;
        long dequeueInputBufferTime = 0;
        int dequeueInputBufferCount = 0;

        long queueInputBufferTime = 0;
        int queueInputBufferCount = 0;

        long dequeueOutputBufferTime = 0;
        int dequeueOutputBufferCount = 0;

        long getOutputImageTime = 0;
        int getOutputImageCount = 0;

        float duration_s = lastTimeUs / 1000.f / 1000.f;
        int frameCount = (int)(duration_s * frameRate);

        //每秒检测2帧
        int needCheckFrameCount = (int)(duration_s) * 2;//(int)(duration_s * 2);
        int jump = (int)((frameCount - 10) / (float)(needCheckFrameCount));
        int[] all_frames = new int[needCheckFrameCount];
        for (int i = 0; i < needCheckFrameCount; ++i) {
            all_frames[i] = 5 + i*jump;
        }

        long frameDuration_us = (long)((1000.f / frameRate) * 1000);
        int indexes[] = new int[255];
        Vector sceneIndexTemp = new Vector();
        Vector scoreTmp = new Vector();
        int frame_index = 0;
        int frameDuration_ms = 1000 / frameRate;

        int calltime = 0;
        while (!sawOutputEOS) {
            ++calltime;

            if (!sawInputEOS) {
                long dequeueInputBufferStart = System.currentTimeMillis();
                int inputBufferId = decoder.dequeueInputBuffer(10000);
                dequeueInputBufferTime += (System.currentTimeMillis() -  dequeueInputBufferStart);
                dequeueInputBufferCount += 1;

                if (inputBufferId >= 0) {
                    noDecodeInputBufferCnt = 0;
                    ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferId);
                    int sampleSize = extractor.readSampleData(inputBuffer, 0);
                    long presentationTimeUs = extractor.getSampleTime();
                    //Log.e("Test","extractor presentationTimeUs:"+presentationTimeUs);
                    if (sampleSize < 0) {
                        long queueInputBufferStart = System.currentTimeMillis();
                        decoder.queueInputBuffer(inputBufferId, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        queueInputBufferTime += (System.currentTimeMillis() - queueInputBufferStart);
                        queueInputBufferCount += 1;
                        sawInputEOS = true;
                    } else {
                        long queueInputBufferStart = System.currentTimeMillis();
                        decoder.queueInputBuffer(inputBufferId, 0, sampleSize, presentationTimeUs, 0);
                        extractor.advance();
                        queueInputBufferTime += (System.currentTimeMillis() - queueInputBufferStart);
                        queueInputBufferCount += 1;
                    }
                }else{
                    noDecodeInputBufferCnt++;
                    if(noDecodeInputBufferCnt > maxLossCnt){
                        sawInputEOS = true;
                    }
                }
            }

            long dequeueOutputBufferStart = System.currentTimeMillis();
            int outputBufferId = decoder.dequeueOutputBuffer(info, 10000);
            dequeueOutputBufferTime += (System.currentTimeMillis() - dequeueOutputBufferStart);
            dequeueOutputBufferCount += 1;

            if (outputBufferId >= 0) {
                noDecodeOutputBufferCnt = 0;
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true;
                }
                if(info.presentationTimeUs > lastTimeUs){
                    decoder.releaseOutputBuffer(outputBufferId, true);
                    sawOutputEOS = true;
                } else {
                    if(frame_index < all_frames.length) {
                        long needFindFrameTimeUs = frameDuration_us * all_frames[frame_index];

//                        Log.e("TimeAna","Abs duration:"+ (info.presentationTimeUs - needFindFrameTimeUs) + " needFindFrameTimeUs:" + needFindFrameTimeUs + " presentationTimeUs:" + info.presentationTimeUs + " index:" + frame_index);

                        if(Math.abs(info.presentationTimeUs - needFindFrameTimeUs) < (frameDuration_us)) {
                            timeVec.add(frameDuration_ms*all_frames[frame_index]);

                            long imageStart = System.currentTimeMillis();
                            Image image = decoder.getOutputImage(outputBufferId);
                            byte[] bytes = getDataFromImage(image, COLOR_FormatNV21);
                            getOutputImageTime += (System.currentTimeMillis() - imageStart);
                            getOutputImageCount += 1;

                            long startinfertime = System.currentTimeMillis();
                            int[] index = sceneClsInferenceYUV(bytes, width, height);
                            long endinfertime = System.currentTimeMillis();
                            infertime += (endinfertime - startinfertime);
                            scoreTmp.add(index[index.length-1]);
                            sceneIndexTemp.add(index[index.length-2]);
                            image.close();

                            ++frame_index;
                        }
                    }
                    decoder.releaseOutputBuffer(outputBufferId, true);
                }
            }else{
                noDecodeOutputBufferCnt++;
                if(noDecodeOutputBufferCnt > maxLossCnt){
                    sawOutputEOS = true;
                }
            }
        }
//
//        if (temp.size() != 0) {
//            vec.addElement(temp);
//            scoreVec.addElement(scoreTmp);
//        }

        long testclstimeend = System.currentTimeMillis() - testclstime;
        Log.e("TimeAna","cls Coast Time:"+ testclstimeend + "infer time:" + infertime + " all_frames:" + all_frames.length);
        Log.e("TimeAna","dequeueInputBufferTime:"+ dequeueInputBufferTime + "call count : " + dequeueInputBufferCount);
        Log.e("TimeAna","queueInputBufferTime:"+ queueInputBufferTime + "call count : " + queueInputBufferCount);
        Log.e("TimeAna","dequeueOutputBufferTime:"+ dequeueOutputBufferTime + "call count : " + dequeueOutputBufferCount);
        Log.e("TimeAna","getOutputImageTime:"+ getOutputImageTime + "call count : " + getOutputImageCount + " all" + calltime);


        long endTime = System.currentTimeMillis();
        int costTime = (int)(endTime - startTime);
        int averageTime = costTime / frameCount;

        Vector res_str = new Vector();
        res_str.add(String.valueOf(costTime));
        res_str.add(String.valueOf(averageTime));
        res_str.add(String.valueOf(frameCount));

        long allClsTime = System.currentTimeMillis() - startTime;
        Log.e("TimeAna","A Time:"+ costTime+"ms"+ " framecount:" + frameCount + " avg time:" + averageTime + "ms");
        Log.e("TimeAna","All Time:"+ allClsTime+"ms");

        for(int i = 0; i < sceneIndexTemp.size(); ++i) {
            //res_str.add(resultLabel.get(scene_index[i]) + "(" + result[i] + "-" + result[i+1] + ")");
            res_str.add("\"shot" + i +
                    "\":{\"confi\":" + String.format("%.2f", (int)(scoreTmp.get(i)) / 100.0f) +
                    ",\"scene\":\"" + resultLabel.get((int)(sceneIndexTemp.get(i))) + "\"}");
        }

        return res_str;
    }

    private Vector inferenceVideoInternal_efficient(String videoPath, long cutStartMs, long cutEndMs, boolean useKeyFrame) {
        Vector vec = new Vector();
        Vector scoreVec = new Vector();
        Vector timeVec = new Vector(); // 视频时间戳
        int frameRate = 0;

        long startTime = System.currentTimeMillis();

        ShotSegmention shotSegmention = new ShotSegmention();
        int result[];
        if(useKeyFrame) {
            result = shotSegmention.getSbdIndex_detect_key_frame(videoPath, cutStartMs, cutEndMs);
        }else {
            result = shotSegmention.getSbdIndex(videoPath, cutStartMs, cutEndMs);
        }
        if(result.length == 0) {
            long ShotSegmentionTime = System.currentTimeMillis() - startTime;
            Log.e("TimeAna","ShotSegmention Cost Time:"+ ShotSegmentionTime+"ms result length: 0");

            vec.add("unknown error");
            return vec;
        } else if(result[0] == -1) {
            vec.add("invalid video address");
            return vec;
        } else if(result[0] == -2) {
            vec.add("video duration nonstandard or video resolution ratio nonstandard");
        }

        long ShotSegmentionTime = System.currentTimeMillis() - startTime;
        Log.e("TimeAna","ShotSegmention Cost Time:"+ ShotSegmentionTime+"ms result length:"+result.length);

        MediaExtractor extractor;
        MediaFormat mediaFormat;
        String mime;
        long duration_us;
        int width;
        int height;

        try {
            extractor = new MediaExtractor();
            extractor.setDataSource(videoPath);
            int trackIndex = selectTrack(extractor);
            if (trackIndex < 0) {
                vec.add("unsupport video");
                return vec;
            }

            extractor.selectTrack(trackIndex);
            mediaFormat = extractor.getTrackFormat(trackIndex);
            width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
            height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
            duration_us = mediaFormat.getLong(MediaFormat.KEY_DURATION);
            mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            boolean validSize = checkImgSizeValid(width, height);
            if(!validSize) {
                vec.add("unsupport video resolution ratio");
                return vec;
            }

            if(mediaFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                frameRate = mediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE);
            } else {
                frameRate = 25;
            }

        } catch (IOException e) {
            e.printStackTrace();
            vec.add("parse video error");
            return vec;
        }

        MediaCodec decoder;
        try{
            decoder = MediaCodec.createDecoderByType(mime);
        } catch(IOException e) {
            return new Vector();
        }
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        decoder.configure(mediaFormat, null, null, 0);
        decoder.start();

        long duration = Long.MAX_VALUE;
        if(mediaFormat.containsKey(MediaFormat.KEY_DURATION)){
            duration = mediaFormat.getLong(MediaFormat.KEY_DURATION);
        }
        if(cutStartMs>=0){
            extractor.seekTo(cutStartMs*1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        }
        long firstTimeUs = cutStartMs * 1000;
        long lastTimeUs = cutEndMs*1000;
        if(lastTimeUs<=firstTimeUs || lastTimeUs > duration) {
            lastTimeUs = duration + 200;
        }

        int noDecodeInputBufferCnt = 0;
        int noDecodeOutputBufferCnt = 0;
        int maxLossCnt = 1000;

        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;

        long testclstime = System.currentTimeMillis();

        long infertime = 0;
        long dequeueInputBufferTime = 0;
        int dequeueInputBufferCount = 0;

        long queueInputBufferTime = 0;
        int queueInputBufferCount = 0;

        long dequeueOutputBufferTime = 0;
        int dequeueOutputBufferCount = 0;

        long getOutputImageTime = 0;
        int getOutputImageCount = 0;

        float duration_s = lastTimeUs / 1000.f / 1000.f;
        int frameCount = (int)(duration_s * frameRate);
        result = Arrays.copyOf(result, result.length+1) ;
        result[result.length-1] = frameCount;
        int[] all_frames = sceneFrameExtractV1(result, result.length, frameRate);

        long frameDuration_us = (long)((1000.f / frameRate) * 1000);
        int indexes[] = new int[255];
        Vector temp = new Vector();
        Vector scoreTmp = new Vector();
        int frame_index = 0;
        int frameDuration_ms = 1000 / frameRate;

        int calltime = 0;

        while (!sawOutputEOS) {
            ++calltime;

            if(frame_index < all_frames.length && all_frames[frame_index] == -1) {
                vec.addElement(temp);
                scoreVec.addElement(scoreTmp);
                temp = new Vector();
                scoreTmp = new Vector();
                ++frame_index;
            }

            if (!sawInputEOS) {
                long dequeueInputBufferStart = System.currentTimeMillis();
                int inputBufferId = decoder.dequeueInputBuffer(10000);
                dequeueInputBufferTime += (System.currentTimeMillis() -  dequeueInputBufferStart);
                dequeueInputBufferCount += 1;

                if (inputBufferId >= 0) {
                    noDecodeInputBufferCnt = 0;
                    ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferId);
                    int sampleSize = extractor.readSampleData(inputBuffer, 0);
                    long presentationTimeUs = extractor.getSampleTime();
                    //Log.e("Test","extractor presentationTimeUs:"+presentationTimeUs);
                    if (sampleSize < 0) {
                        long queueInputBufferStart = System.currentTimeMillis();
                        decoder.queueInputBuffer(inputBufferId, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        queueInputBufferTime += (System.currentTimeMillis() - queueInputBufferStart);
                        queueInputBufferCount += 1;
                        sawInputEOS = true;
                    } else {
                        long queueInputBufferStart = System.currentTimeMillis();
                        decoder.queueInputBuffer(inputBufferId, 0, sampleSize, presentationTimeUs, 0);
                        extractor.advance();
                        queueInputBufferTime += (System.currentTimeMillis() - queueInputBufferStart);
                        queueInputBufferCount += 1;
                    }
                }else{
                    noDecodeInputBufferCnt++;
                    if(noDecodeInputBufferCnt > maxLossCnt){
                        sawInputEOS = true;
                    }
                }
            }

            long dequeueOutputBufferStart = System.currentTimeMillis();
            int outputBufferId = decoder.dequeueOutputBuffer(info, 10000);
            dequeueOutputBufferTime += (System.currentTimeMillis() - dequeueOutputBufferStart);
            dequeueOutputBufferCount += 1;

            if (outputBufferId >= 0) {
                noDecodeOutputBufferCnt = 0;
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true;
                }
                if(info.presentationTimeUs > lastTimeUs){
                    decoder.releaseOutputBuffer(outputBufferId, true);
                    sawOutputEOS = true;
                } else {
                    if(frame_index < all_frames.length) {
                        long needFindFrameTimeUs = frameDuration_us * all_frames[frame_index];

//                        Log.e("TimeAna","Abs duration:"+ (info.presentationTimeUs - needFindFrameTimeUs) + " needFindFrameTimeUs:" + needFindFrameTimeUs + " presentationTimeUs:" + info.presentationTimeUs + " index:" + frame_index);

                        if(Math.abs(info.presentationTimeUs - needFindFrameTimeUs) < (frameDuration_us)) {
                            timeVec.add(frameDuration_ms*all_frames[frame_index]);

                            long imageStart = System.currentTimeMillis();
                            Image image = decoder.getOutputImage(outputBufferId);
                            byte[] bytes = getDataFromImage(image, COLOR_FormatNV21);
                            getOutputImageTime += (System.currentTimeMillis() - imageStart);
                            getOutputImageCount += 1;

                            long startinfertime = System.currentTimeMillis();
                            int[] index = sceneClsInferenceYUV(bytes, width, height);
                            long endinfertime = System.currentTimeMillis();
                            infertime += (endinfertime - startinfertime);
                            scoreTmp.add(index[index.length-1]);
                            temp.add(index[index.length-2]);
                            image.close();

                            ++frame_index;
                        }
                    }
                    decoder.releaseOutputBuffer(outputBufferId, true);
                }
            }else{
                noDecodeOutputBufferCnt++;
                if(noDecodeOutputBufferCnt > maxLossCnt){
                    sawOutputEOS = true;
                }
            }
        }

        if (temp.size() != 0) {
            vec.addElement(temp);
            scoreVec.addElement(scoreTmp);
        }

        long testclstimeend = System.currentTimeMillis() - testclstime;
        Log.e("TimeAna","cls Coast Time:"+ testclstimeend + "infer time:" + infertime + " all_frames:" + all_frames.length);
        Log.e("TimeAna","dequeueInputBufferTime:"+ dequeueInputBufferTime + "call count : " + dequeueInputBufferCount);
        Log.e("TimeAna","queueInputBufferTime:"+ queueInputBufferTime + "call count : " + queueInputBufferCount);
        Log.e("TimeAna","dequeueOutputBufferTime:"+ dequeueOutputBufferTime + "call count : " + dequeueOutputBufferCount);
        Log.e("TimeAna","getOutputImageTime:"+ getOutputImageTime + "call count : " + getOutputImageCount + " all" + calltime);


        long endTime = System.currentTimeMillis();
        int costTime = (int)(endTime - startTime);
        int averageTime = costTime / frameCount;
        int size = vec.size();
        for(int i = 0; i < vec.size(); ++i) {
            Object object = vec.get(i);
            if(object instanceof String) {
                // 出错了
                vec.clear();
                vec.add(object);
                return vec;
            }
            size += ((Vector) object).size();
        }
        int[] arr = new int[size];
        int q = 0;
        for(int i = 0; i < vec.size(); ++i) {
            Vector t = (Vector)vec.get(i);
            for(int k = 0; k < t.size(); ++k) {
                arr[q++] = (int)t.get(k);
            }
            arr[q++] = -1;
        }

        int[]  scene_index = sceneFindMax(arr, arr.length);
        int scnt = scene_index.length;
        int[] scene_score = new int[scnt];
        float[] scene_time = new float[scnt];


        Log.e("TimeAna","vec size:"+ arr.length + " " + scnt);

        // 获得最大index对应的分数
        int index = 0;
        for(int i = 0; i < scoreVec.size(); ++i) {
            Vector t = (Vector)scoreVec.get(i);
            Vector indexVector = (Vector)vec.get(i);
            int maxIndex = scene_index[i];
            int maxScore = -1;
            for(int k = 0; k < t.size(); ++k) {
                if((int)indexVector.get(k)==maxIndex){
                    if((int)t.get(k) > maxScore){
                        maxScore = (int)t.get(k);
                    }
                }
            }
            index = index + t.size();
            scene_score[i] = maxScore;
            scene_time[i] = (int)(timeVec.get(index-1))/1000.0f;
        }
        scene_time[scnt-1] = duration;

        Vector res_str = new Vector();
        res_str.add(String.valueOf(costTime));
        res_str.add(String.valueOf(averageTime));
        res_str.add(String.valueOf(frameCount));

        long allClsTime = System.currentTimeMillis() - startTime;
        Log.e("TimeAna","A Time:"+ costTime+"ms"+ " framecount:" + frameCount + " avg time:" + averageTime + "ms");
        Log.e("TimeAna","All Time:"+ allClsTime+"ms");

        for(int i = 0; i < scene_index.length; ++i) {
            //res_str.add(resultLabel.get(scene_index[i]) + "(" + result[i] + "-" + result[i+1] + ")");
            res_str.add("\"shot" + i +
                    "\":{\"confi\":" + String.format("%.2f", scene_score[i] / 100.0f) +
                    ",\"final\":" + String.format("%.2f",scene_time[i]) +
                    ",\"scene\":\"" + resultLabel.get(scene_index[i]) + "\"}");
        }

        return res_str;
    }


    private static byte[] getDataFromImage(Image image, int colorFormat) {
        if (colorFormat != COLOR_FormatI420 && colorFormat != COLOR_FormatNV21) {
            throw new IllegalArgumentException("only support COLOR_FormatI420 " + "and COLOR_FormatNV21");
        }
        if (!isImageFormatSupported(image)) {
            throw new RuntimeException("can't convert Image to byte array, format " + image.getFormat());
        }
        Rect crop = image.getCropRect();
        int format = image.getFormat();
        int width = crop.width();
        int height = crop.height();
        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];
//        if (VERBOSE) Log.v(TAG, "get data from " + planes.length + " planes");
        int channelOffset = 0;
        int outputStride = 1;
        for (int i = 0; i < planes.length; i++) {
            switch (i) {
                case 0:
                    channelOffset = 0;
                    outputStride = 1;
                    break;
                case 1:
                    if (colorFormat == COLOR_FormatI420) {
                        channelOffset = width * height;
                        outputStride = 1;
                    } else if (colorFormat == COLOR_FormatNV21) {
                        channelOffset = width * height + 1;
                        outputStride = 2;
                    }
                    break;
                case 2:
                    if (colorFormat == COLOR_FormatI420) {
                        channelOffset = (int) (width * height * 1.25);
                        outputStride = 1;
                    } else if (colorFormat == COLOR_FormatNV21) {
                        channelOffset = width * height;
                        outputStride = 2;
                    }
                    break;
            }
            ByteBuffer buffer = planes[i].getBuffer();
            int rowStride = planes[i].getRowStride();
            int pixelStride = planes[i].getPixelStride();

            int shift = (i == 0) ? 0 : 1;
            int w = width >> shift;
            int h = height >> shift;
            buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
            for (int row = 0; row < h; row++) {
                int length;
                if (pixelStride == 1 && outputStride == 1) {
                    length = w;
                    buffer.get(data, channelOffset, length);
                    channelOffset += length;
                } else {
                    length = (w - 1) * pixelStride + 1;
                    buffer.get(rowData, 0, length);
                    for (int col = 0; col < w; col++) {
                        data[channelOffset] = rowData[col * pixelStride];
                        channelOffset += outputStride;
                    }
                }
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length);
                }
            }
        }
        return data;
    }



    private void readCacheLabelFromLocalFile() {
        //try {
        //    AssetManager assetManager = context_.getApplicationContext().getAssets();
        //    BufferedReader reader = new BufferedReader(new InputStreamReader(assetManager.open("label.txt")));
        //    String readLine = null;
        //    while ((readLine = reader.readLine()) != null) {
        //        resultLabel.add(readLine);
        //    }
        //    reader.close();
        //} catch (Exception e) {
        //    Log.e("labelCache", "error " + e);
        //}
    }

    private boolean checkImgSizeValid(int width, int height) {
        int min = min(width, height);
        int max = max(width, height);

        return min >= validImgMinSize && max <= validImgMaxSize;
    }

    private static boolean isImageFormatSupported(Image image) {
        int format = image.getFormat();
        switch (format) {
            case ImageFormat.YUV_420_888:
            case ImageFormat.NV21:
            case ImageFormat.YV12:
                return true;
        }
        return false;
    }
}
