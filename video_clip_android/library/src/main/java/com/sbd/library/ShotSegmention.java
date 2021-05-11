package com.sbd.library;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class ShotSegmention {

    MediaExtractor extractor;
    private MediaCodec decoder;
    private final int decodeColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
    private static final long DEFAULT_TIMEOUT_US = 10000;
    private int outputImageFileType = 3;
    public static final int FILE_TypeI420 = 1;
    public static final int FILE_TypeNV21 = 2;
    public static final int FILE_TypeJPEG = 3;
    private static final int COLOR_FormatI420 = 1;
    private static final int COLOR_FormatNV21 = 2;
    private int frameRate = 0;

    static {
        System.loadLibrary("sbd-lib");
    }

    public ShotSegmention() {
        init();
    }

    public native int checkVideoFileValid(String path);
    public native int checkVideoContentValid(int width, int height, float durationS);
    public native void init();
    public native void reset();
    public native void cacheFeatureFromNV21(byte[] bytes, int width, int height);
    public native int[] getSbIdx();

    public int[] getSbdIndex(String videoPath, long cutStartMs, long cutEndMs) {
        int[] res = new int[]{-1};
        try {
            int exist = checkVideoFileValid(videoPath);
            if(exist != 0) {
                return res;
            }

            extractor = new MediaExtractor();
            extractor.setDataSource(videoPath);

            int trackIndex = selectTrack(extractor);
            if (trackIndex < 0) {
                return res;
            }

            extractor.selectTrack(trackIndex);
            MediaFormat mediaFormat = extractor.getTrackFormat(trackIndex);
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            int width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
            int height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
            long duration = mediaFormat.getLong(MediaFormat.KEY_DURATION);
            int valid = checkVideoContentValid(width, height , duration/1000.0f/1000.0f);
            if(valid != 0) return new int[]{-2};

            decoder = MediaCodec.createDecoderByType(mime);

            if(mediaFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                frameRate = mediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE);
            } else {
                frameRate = 25;
            }

            if (isColorFormatSupported(decodeColorFormat, decoder.getCodecInfo().getCapabilitiesForType(mime))) {
                mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, decodeColorFormat);
                Log.i("TEST", "set decode color format to type " + decodeColorFormat);
            } else {
                Log.i("TEST", "unable to set decode color format, color format type " + decodeColorFormat + " not supported");
            }

            res = decodeFramesToImage(decoder, extractor, mediaFormat, cutStartMs, cutEndMs);
            try {
                decoder.stop();
            }catch (Exception e){
                e.printStackTrace();
            }
            try {
                decoder.release();
            }catch (Exception e){
                e.printStackTrace();
            }
            try {
                extractor.release();
            }catch (Exception e){
                e.printStackTrace();
            }
        } catch(IOException e) {
            return new int[]{-1};
        }
        return res;
    }

    private int[] decodeFramesToImage(MediaCodec decoder, MediaExtractor extractor, MediaFormat mediaFormat, long cutStartMs, long cutEndMs) {
        long startTime = System.currentTimeMillis();

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;
        decoder.configure(mediaFormat, null, null, 0);
        decoder.start();
        final int width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
        final int height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);

        //extractor.seekTo(1,MediaExtractor.SEEK_TO_NEXT_SYNC);

        long duration = Long.MAX_VALUE;
        if(mediaFormat.containsKey(MediaFormat.KEY_DURATION)){
            duration = mediaFormat.getLong(MediaFormat.KEY_DURATION);
        }
        if(cutStartMs>=0){
            extractor.seekTo(cutStartMs*1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        }
        long firstTimeUs = cutStartMs * 1000;
        long lastTimeUs = cutEndMs*1000;
        if(lastTimeUs<=firstTimeUs || lastTimeUs > duration){
            lastTimeUs = duration + 200;
        }

//test time
        long dequeueInputBufferTime = 0;
        int dequeueInputBufferCount = 0;

        long queueInputBufferTime = 0;
        int queueInputBufferCount = 0;

        long dequeueOutputBufferTime = 0;
        int dequeueOutputBufferCount = 0;

        long getOutputImageTime = 0;
        int getOutputImageCount = 0;

        long appendFrameRGBTime = 0;

//----------

        int outputFrameCount = 0;
        int noDecodeInputBufferCnt = 0;
        int noDecodeOutputBufferCnt = 0;
        int maxLossCnt = 1000;
        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                long dequeueInputBufferStart = System.currentTimeMillis();
                int inputBufferId = decoder.dequeueInputBuffer(DEFAULT_TIMEOUT_US);
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
            int outputBufferId = decoder.dequeueOutputBuffer(info, DEFAULT_TIMEOUT_US);
            dequeueOutputBufferTime += (System.currentTimeMillis() - dequeueOutputBufferStart);
            dequeueOutputBufferCount += 1;

            if (outputBufferId >= 0) {
                noDecodeOutputBufferCnt = 0;
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true;
                }
                boolean doRender = (info.size != 0);
                //if((info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) !=0){
                //    Log.e("Test","Key Frame time:"+info.presentationTimeUs);
                //}
                if(info.presentationTimeUs > lastTimeUs){
                    decoder.releaseOutputBuffer(outputBufferId, true);
                    sawOutputEOS = true;
                } else {
                     //Log.e("Test","decode Frame time:"+info.presentationTimeUs);
                    if (doRender) {
                        if(info.presentationTimeUs < firstTimeUs){
                             //Log.e("Test","Skip decode Frame time:"+info.presentationTimeUs);
                            decoder.releaseOutputBuffer(outputBufferId, true);
                        }else {
                            outputFrameCount++;
                            Image image = decoder.getOutputImage(outputBufferId);
                            long imageStart = System.currentTimeMillis();
                            byte[] bytes = getDataFromImage(image, COLOR_FormatNV21);
                            getOutputImageTime += (System.currentTimeMillis() - imageStart);
                            getOutputImageCount += 1;

                            long appendTime = System.currentTimeMillis();
                            cacheFeatureFromNV21(bytes, width, height);
                            appendFrameRGBTime += (System.currentTimeMillis() - appendTime);

                            image.close();
                            decoder.releaseOutputBuffer(outputBufferId, true);
                        }
                    }
                }
            }else{
                noDecodeOutputBufferCnt++;
                if(noDecodeOutputBufferCnt > maxLossCnt){
                    sawOutputEOS = true;
                }
            }
        }
        if(noDecodeInputBufferCnt > maxLossCnt){
            Log.e("Test","Decode frame noDecodeInputBufferCnt > "+maxLossCnt);
        }
        if(noDecodeOutputBufferCnt > maxLossCnt){
            Log.e("Test","Decode frame noDecodeOutputBufferCnt > "+maxLossCnt);
        }
        Log.e("Test","Decode frame outputFrameCount:"+outputFrameCount);


        Log.e("TimeAna","dequeueInputBufferTime:"+ dequeueInputBufferTime + "call count : " + dequeueInputBufferCount);
        Log.e("TimeAna","queueInputBufferTime:"+ queueInputBufferTime + "call count : " + queueInputBufferCount);
        Log.e("TimeAna","dequeueOutputBufferTime:"+ dequeueOutputBufferTime + "call count : " + dequeueOutputBufferCount);
        Log.e("TimeAna","getOutputImageTime:"+ getOutputImageTime + "call count : " + getOutputImageCount);
        Log.e("TimeAna","appendFrameRGBTime:"+ appendFrameRGBTime);


        long getSbdIdxTime = System.currentTimeMillis();
        int[] video_key = getSbIdx();
        reset();
        Log.e("TimeAna","getSbdIdxTime:"+ (System.currentTimeMillis() - getSbdIdxTime) +"ms");

        long ShotSegmentionTime = System.currentTimeMillis() - startTime;
        Log.e("TimeAna","ShotSegmention Cost Time:"+ ShotSegmentionTime+"ms");

        return video_key;
    }

    public int[] getSbdIndex_detect_key_frame(String videoPath, long cutStartMs, long cutEndMs) {
        int[] res = new int[]{-1};
        try {
            int exist = checkVideoFileValid(videoPath);
            if(exist != 0) {
                return res;
            }

            extractor = new MediaExtractor();
            extractor.setDataSource(videoPath);

            int trackIndex = selectTrack(extractor);
            if (trackIndex < 0) {
                return res;
            }

            extractor.selectTrack(trackIndex);
            MediaFormat mediaFormat = extractor.getTrackFormat(trackIndex);
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            int width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
            int height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
            long duration = mediaFormat.getLong(MediaFormat.KEY_DURATION);
            int valid = checkVideoContentValid(width, height , duration/1000.0f/1000.0f);
            if(valid != 0) return new int[]{-2};

            decoder = MediaCodec.createDecoderByType(mime);

            if(mediaFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                frameRate = mediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE);
            } else {
                frameRate = 25;
            }

            if (isColorFormatSupported(decodeColorFormat, decoder.getCodecInfo().getCapabilitiesForType(mime))) {
                mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, decodeColorFormat);
                Log.i("TEST", "set decode color format to type " + decodeColorFormat);
            } else {
                Log.i("TEST", "unable to set decode color format, color format type " + decodeColorFormat + " not supported");
            }

            res = decodeFramesToImage_KeyFrame(decoder, extractor, mediaFormat, cutStartMs, cutEndMs);
            decoder.stop();
        } catch(IOException e) {
            return new int[]{-1};
        }

        return res;
    }

    private int[] decodeFramesToImage_KeyFrame(MediaCodec decoder, MediaExtractor extractor, MediaFormat mediaFormat, long cutStartMs, long cutEndMs) {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;
        decoder.configure(mediaFormat, null, null, 0);
        decoder.start();
        final int width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
        final int height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
        long duration = Long.MAX_VALUE;
        if(mediaFormat.containsKey(MediaFormat.KEY_DURATION)){
            duration = mediaFormat.getLong(MediaFormat.KEY_DURATION);
        }

        List<Long> keyList = new ArrayList<>();
        long timeDis = 100*1000;//100ms
        long lastKeyFrameTimeStamp = 0;
        keyList.add(new Long(0));
        int seekCnt = 0 ;
        long st = System.currentTimeMillis();
        if(cutStartMs>=0){
            extractor.seekTo(cutStartMs*1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        }
        long firstTimeUs = cutStartMs * 1000;
        long lastTimeUs = cutEndMs * 1000;
        if(lastTimeUs<=firstTimeUs){
            lastTimeUs = duration;
        }
        while(true){
            lastKeyFrameTimeStamp = lastKeyFrameTimeStamp + timeDis;
            extractor.seekTo(lastKeyFrameTimeStamp, MediaExtractor.SEEK_TO_NEXT_SYNC);
            long pt =  extractor.getSampleTime();
            if(pt >= lastKeyFrameTimeStamp){
                if(pt >= firstTimeUs) {
                    if(pt > lastTimeUs){
                        break;
                    }
                    lastKeyFrameTimeStamp = pt;
                    keyList.add(new Long(pt));
                    seekCnt = 0;
                }
            }else{
                seekCnt++;
                if(seekCnt>50 || lastKeyFrameTimeStamp >= duration){
                    break;
                }
            }
        }
        long ct = System.currentTimeMillis() - st;
        Log.e("Test","Find key frame "+keyList.size()+" coast "+ct+"ms");
        if(cutStartMs>=0){
            extractor.seekTo(cutStartMs*1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        }else {
            extractor.seekTo(0, MediaExtractor.SEEK_TO_NEXT_SYNC);
        }

        int outputFrameCount = 0;
        int keyIndex = 0;
        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                int inputBufferId = decoder.dequeueInputBuffer(DEFAULT_TIMEOUT_US);
                if (inputBufferId >= 0) {
                    long presentationTimeUs = extractor.getSampleTime();
                    if(presentationTimeUs<firstTimeUs){
                        extractor.advance();
                    }else {
                        ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferId);
                        int sampleSize = extractor.readSampleData(inputBuffer, 0);
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputBufferId, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            sawInputEOS = true;
                        } else {
                            presentationTimeUs = extractor.getSampleTime();
                            if(presentationTimeUs>lastTimeUs) { // 超出剪切的最大值
                                decoder.queueInputBuffer(inputBufferId, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                sawInputEOS = true;
                            }else{
                                decoder.queueInputBuffer(inputBufferId, 0, sampleSize, presentationTimeUs, 0); // 只发送关键帧无法正常解码~~~~
                                extractor.advance();
                            }
                        }
                    }
                }
            }
            int outputBufferId = decoder.dequeueOutputBuffer(info, DEFAULT_TIMEOUT_US);
            if (outputBufferId >= 0) {
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true;
                }
                boolean doRender = (info.size != 0);
                if (doRender) {
                    long presentationTimeUs = info.presentationTimeUs;
                    //Log.e("Test", "decode frame: "+presentationTimeUs);
                    if(keyIndex<keyList.size()) {
                        if (presentationTimeUs == keyList.get(keyIndex)) {
                            keyIndex++;
                            //Log.e("Test", "decode frame process:"+presentationTimeUs);
                            outputFrameCount++;
                            Image image = decoder.getOutputImage(outputBufferId);
                            byte[] bytes = getDataFromImage(image, COLOR_FormatNV21);
                            cacheFeatureFromNV21(bytes, width, height);
                            image.close();
                        }
                        decoder.releaseOutputBuffer(outputBufferId, true);
                    }else{
                        decoder.flush();
                        sawOutputEOS = true;
                    }
                }
            }
        }
        Log.e("Test","Decode frame outputFrameCount :"+outputFrameCount);
        int[] video_key = getSbIdx();
        reset();
        return video_key;
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

    private boolean isColorFormatSupported(int colorFormat, MediaCodecInfo.CodecCapabilities caps) {
        for (int c : caps.colorFormats) {
            if (c == colorFormat) {
                return true;
            }
        }
        return false;
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

}
