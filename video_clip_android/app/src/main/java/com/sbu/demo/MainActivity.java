package com.sbu.demo;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import com.sbd.library.ShotSegmention;
import com.sbu.videoclip.R;

public class MainActivity extends AppCompatActivity {

    private ImageView iv_pic;

    private String videoPath = "";

    private LoadingDialog loadingDialog;

    private DealAsyncTask dealAsyncTask;

    private String OUTPUT_DIR = "/storage/emulated/0/T2/";

    private TextView result_text;

    ShotSegmention shotSegmention;

    // Used to load the 'native-lib' library on application startup.
//    static {
//        System.loadLibrary("sbd-lib");
//    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main_1);


        iv_pic = findViewById(R.id.iv_pic);
        loadingDialog = new LoadingDialog(this);

        try {
            setSaveFrames(OUTPUT_DIR, 3);
        } catch(IOException e) {

        }

        result_text = (TextView) findViewById(R.id.result_text);
        result_text.setMovementMethod(ScrollingMovementMethod.getInstance());

        shotSegmention = new ShotSegmention();
    }


    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */


    public native int checkVideoFileValid(String path);
    public native int checkVideoContentValid(int width, int height, int duration);
    public native void init(int width, int height);
    public native void appendFrameRGB(byte[] bytes);
    public native int[] getSbdIdx();

    public void onPicSelect(View view) {
        Intent intent = new Intent();
        intent.setType("video/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(intent,3);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // 选取图片的返回值
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 3) {
            //
            if (resultCode == RESULT_OK) {
                Uri uri = data.getData();
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

            final String selection = "_id=?";
            final String[] selectionArgs = new String[] { split[1] };

                Uri contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;

                Cursor cursor = null;
                final String column = "_data";
                final String[] projection = {column};

                cursor = this.getContentResolver().query(contentUri, projection, selection , selectionArgs,
                        null);
                cursor.moveToFirst();

                final int column_index = cursor.getColumnIndexOrThrow(column);
                videoPath = cursor.getString(column_index);
                cursor.close();
            }
        }
    }

    public void onPicDeal(View view) {
        dealAsyncTask = new DealAsyncTask();
        dealAsyncTask.execute();
    }


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
    private class DealAsyncTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... voids) {
            if(videoPath == "") return false;
            try {
                MediaExtractor extractor = new MediaExtractor();
                extractor.setDataSource(videoPath);
                int trackIndex = selectTrack(extractor);
                    if (trackIndex < 0) {
                        return false;
                    }

                extractor.selectTrack(trackIndex);
                MediaFormat mediaFormat = extractor.getTrackFormat(trackIndex);

                if(mediaFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                    frameRate = mediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE);
                } else {
                    frameRate = 25;
                }

            } catch (IOException e) {
                e.printStackTrace();
            }

            MediaMetadataRetriever mMetadataRetriever = new MediaMetadataRetriever();
            mMetadataRetriever.setDataSource(videoPath);
            String strDuration = mMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            int duration = Integer.parseInt(strDuration) / 1000;
            int frameCount = duration * frameRate;
            long startTime = System.currentTimeMillis(); // 获取开始时间
            long cutEnd = 10000;
            if(cutEnd > duration){
                cutEnd = -1;
            }
            final int result[] = shotSegmention.getSbdIndex(videoPath, 0, cutEnd);
            long endTime = System.currentTimeMillis();

            String text = String.valueOf(result[0]);
            for(int i = 1; i < result.length; ++i) {
                text += ",";
                text += String.valueOf(result[i]);
            }

            text += "  推理时间：" + String.valueOf(endTime-startTime) + "ms 平均推理时间： " + String.valueOf((endTime-startTime) / frameCount) + "ms";

            final String finalText = text;
            if(result.length!= 0 && result[0] < 0) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        result_text.setText(String.valueOf(result[0]));
                        result_text.setText(finalText);
                    }
                });
                return false;
            }else{
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        result_text.setText(finalText);
                    }
                });
            }


//            decodeFramesToImage1(result);
//             decodeFrameToImage2(result, videoPath, 25);
            return true;
//
//            try {
//                extractor = new MediaExtractor();
//                extractor.setDataSource(videoPath);
//
//
//                int trackIndex = selectTrack(extractor);
//                if (trackIndex < 0) {
//                    throw new RuntimeException("No video track found in " + videoPath);
//                }
//
//                extractor.selectTrack(trackIndex);
//                MediaFormat mediaFormat = extractor.getTrackFormat(trackIndex);
//                String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
//                decoder = MediaCodec.createDecoderByType(mime);
//
//                if(mediaFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
//                    frameRate = mediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE);
//                } else {
//                    frameRate = 25;
//                }
//
//                if (isColorFormatSupported(decodeColorFormat, decoder.getCodecInfo().getCapabilitiesForType(mime))) {
//                    mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, decodeColorFormat);
//                    Log.i("TEST", "set decode color format to type " + decodeColorFormat);
//                } else {
//                    Log.i("TEST", "unable to set decode color format, color format type " + decodeColorFormat + " not supported");
//                }
//
//                decodeFramesToImage(decoder, extractor, mediaFormat);
//                decoder.stop();
//            } catch(IOException e) {
//
//            }
//
//            if (decoder == null) {
//                Log.e("DecodeActivity", "Can't find video info!");
//                return true;
//            }
//
//
//            return true;

        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);
            loadingDialog.dismiss();
            if (aBoolean) {
                Toast.makeText(MainActivity.this, "分割完成 ", Toast.LENGTH_SHORT).show();
                //launchModelRendererActivity(Uri.parse("file://" + objPath));
            } else {
                Toast.makeText(MainActivity.this, "生成失败", Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
             loadingDialog.show("分割中。。。");
        }

        @Override
        protected void onProgressUpdate(Void... values) {
            super.onProgressUpdate(values);
        }

    }

    private void decodeFramesToImage(MediaCodec decoder, MediaExtractor extractor, MediaFormat mediaFormat) {

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;
        decoder.configure(mediaFormat, null, null, 0);
        decoder.start();
        final int width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
        final int height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);

//        init(width, height);

        int outputFrameCount = 0;
        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                int inputBufferId = decoder.dequeueInputBuffer(DEFAULT_TIMEOUT_US);
                if (inputBufferId >= 0) {
                    ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferId);
                    int sampleSize = extractor.readSampleData(inputBuffer, 0);
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputBufferId, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        sawInputEOS = true;
                    } else {
                        long presentationTimeUs = extractor.getSampleTime();
                        decoder.queueInputBuffer(inputBufferId, 0, sampleSize, presentationTimeUs, 0);
                        extractor.advance();
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
                    outputFrameCount++;
//                    Image image = decoder.getOutputImage(outputBufferId);
//                    byte[] bytes = getDataFromImage(image, COLOR_FormatNV21);
//                    appendFrameRGB(bytes);
//                    image.close();
                    decoder.releaseOutputBuffer(outputBufferId, true);
                }
            }
        }

        int end = 10;

//        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
//        boolean sawInputEOS = false;
//        boolean sawOutputEOS = false;
//        decoder.configure(mediaFormat, null, null, 0);
//        decoder.start();
//        final int width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
//        final int height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
//
////        init(width, height);
//
//        int outputFrameCount = 0;
//        while (!sawOutputEOS) {
//            if (!sawInputEOS) {
//                int inputBufferId = decoder.dequeueInputBuffer(DEFAULT_TIMEOUT_US);
//                if (inputBufferId >= 0) {
//                    ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferId);
//                    int sampleSize = extractor.readSampleData(inputBuffer, 0);
//                    if (sampleSize < 0) {
//                        decoder.queueInputBuffer(inputBufferId, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
//                        sawInputEOS = true;
//                    } else {
//                        long presentationTimeUs = extractor.getSampleTime();
//                        decoder.queueInputBuffer(inputBufferId, 0, sampleSize, presentationTimeUs, 0);
//                        extractor.advance();
//                    }
//                }
//            }
//            int outputBufferId = decoder.dequeueOutputBuffer(info, DEFAULT_TIMEOUT_US);
//            if (outputBufferId >= 0) {
//                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
//                    sawOutputEOS = true;
//                }
//                boolean doRender = (info.size != 0);
//                if (doRender) {
//                    outputFrameCount++;
////                    Image image = decoder.getOutputImage(outputBufferId);
////                    byte[] bytes = getDataFromImage(image, COLOR_FormatNV21);
////                    appendFrameRGB(bytes);
////                    image.close();
////                    decoder.releaseOutputBuffer(outputBufferId, true);
//                }
//            }
//        }
//
//        int end = 10;
//        int[] result = getSbdIdx();

//        decodeFramesToImage1(result);
    }

    private void decodeFrameToImage2(int[] result, String videoPath, int frameRate) {
        MediaMetadataRetriever mMetadataRetriever = new MediaMetadataRetriever();
        //mPath本地视频地址
        mMetadataRetriever.setDataSource(videoPath);
        //这个时候就可以通过mMetadataRetriever来获取这个视频的一些视频信息了
        String duration = mMetadataRetriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);//时长(毫秒)
        String width = mMetadataRetriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);//宽
        String height = mMetadataRetriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);//高
//        int total_count = mMetadataRetriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT);


        int frameDuration = 1000 / frameRate;

        //上面三行代码可以获取这个视频的宽高和播放总时长
        //下面这行代码才是关键，用来获取当前视频某一时刻(毫秒*1000)的一帧
//        Bitmap bitmap = mMetadataRetriever.getFrameAtTime(mCovervideoview.getCurrentPosition()*1000, MediaMetadataRetriever.OPTION_CLOSEST);
        //这时就可以获取这个视频的某一帧的bitmap了
        for(int i = 0; i < result.length; ++i) {
            try {
                String fileName = OUTPUT_DIR + String.valueOf(i) + ".jpg";//String.format("frame_%05d_I420_%dx%d.yuv", i, width, height);
                Bitmap bitmap = mMetadataRetriever.getFrameAtTime(frameDuration*result[i]*1000, MediaMetadataRetriever.OPTION_CLOSEST);
                File file = new File(fileName + ".jpg");
                FileOutputStream out = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                out.flush();
                out.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void decodeFramesToImage1(int[] result) {
//        decoder.stop();

        try {
            extractor = new MediaExtractor();
            extractor.setDataSource(videoPath);


            int trackIndex = selectTrack(extractor);
            if (trackIndex < 0) {
                throw new RuntimeException("No video track found in " + videoPath);
            }

            extractor.selectTrack(trackIndex);
            MediaFormat mediaFormat = extractor.getTrackFormat(trackIndex);
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
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



        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;
        decoder.configure(mediaFormat, null, null, 0);
        decoder.start();
        final int width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
        final int height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
        int length = result.length;
        int currentIndex = 0;
        int outputFrameCount = 0;
        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                int inputBufferId = decoder.dequeueInputBuffer(DEFAULT_TIMEOUT_US);
                if (inputBufferId >= 0) {
                    ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferId);
                    int sampleSize = extractor.readSampleData(inputBuffer, 0);
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputBufferId, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        sawInputEOS = true;
                    } else {
                        long presentationTimeUs = extractor.getSampleTime();
                        decoder.queueInputBuffer(inputBufferId, 0, sampleSize, presentationTimeUs, 0);
                        extractor.advance();
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

                    Image image = decoder.getOutputImage(outputBufferId);

                    System.out.println("image format: " + image.getFormat());
                    if (outputImageFileType != -1 && outputFrameCount == result[currentIndex]) {
                        String fileName;
                        switch (outputImageFileType) {
                            case FILE_TypeI420:
                                fileName = OUTPUT_DIR + String.format("frame_%05d_I420_%dx%d.yuv", outputFrameCount, width, height);
                                dumpFile(fileName, getDataFromImage(image, COLOR_FormatI420));
                                break;
                            case FILE_TypeNV21:
                                fileName = OUTPUT_DIR + String.format("frame_%05d_NV21_%dx%d.yuv", outputFrameCount, width, height);
                                dumpFile(fileName, getDataFromImage(image, COLOR_FormatNV21));
                                break;
                            case FILE_TypeJPEG:
                                fileName = OUTPUT_DIR + String.format("frame_%05d.jpg", outputFrameCount);
                                compressToJpeg(fileName, image);
                                break;
                        }
                        currentIndex++;
                    }
                    image.close();
                    decoder.releaseOutputBuffer(outputBufferId, true);
                    outputFrameCount++;
                    if(currentIndex == length) {
                        return;
                    }
                }
            }
        }

        } catch(IOException e) {

        }
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




    public void setSaveFrames(String dir, int fileType) throws IOException {
        if (fileType != FILE_TypeI420 && fileType != FILE_TypeNV21 && fileType != FILE_TypeJPEG) {
            throw new IllegalArgumentException("only support FILE_TypeI420 " + "and FILE_TypeNV21 " + "and FILE_TypeJPEG");
        }
        outputImageFileType = fileType;
        File theDir = new File(dir);
        if (!theDir.exists()) {
            theDir.mkdirs();
        } else if (!theDir.isDirectory()) {
            throw new IOException("Not a directory");
        }
        OUTPUT_DIR = theDir.getAbsolutePath() + "/";
    }

    private static void dumpFile(String fileName, byte[] data) {
        FileOutputStream outStream;
        try {
            outStream = new FileOutputStream(fileName);
        } catch (IOException ioe) {
            throw new RuntimeException("Unable to create output file " + fileName, ioe);
        }
        try {
            outStream.write(data);
            outStream.close();
        } catch (IOException ioe) {
            throw new RuntimeException("failed writing data to file " + fileName, ioe);
        }
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
//            if (VERBOSE) {
//                Log.v(TAG, "pixelStride " + pixelStride);
//                Log.v(TAG, "rowStride " + rowStride);
//                Log.v(TAG, "width " + width);
//                Log.v(TAG, "height " + height);
//                Log.v(TAG, "buffer size " + buffer.remaining());
//            }
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
//            if (VERBOSE) Log.v(TAG, "Finished reading data from plane " + i);
        }
        return data;
    }

    private void compressToJpeg(String fileName, Image image) {
        FileOutputStream outStream;
        try {
            outStream = new FileOutputStream(fileName);
        } catch (IOException ioe) {
            throw new RuntimeException("Unable to create output file " + fileName, ioe);
        }
        Rect rect = image.getCropRect();
        YuvImage yuvImage = new YuvImage(getDataFromImage(image, COLOR_FormatNV21), ImageFormat.NV21, rect.width(), rect.height(), null);
        yuvImage.compressToJpeg(rect, 100, outStream);
    }
}
