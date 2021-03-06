package com.audiocls.lib;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;


public class AudioDecode {

    //"/storage/emulated/0/gemfield_hk.MP4"

    final static int TIMEOUT_USEC = 0;

    public AudioInfo getPCMFromVideo(String videoPath, long cutStartMs, long cutEndMs) {

        Vector vec = new Vector();
        List<byte[]> list_byte = new ArrayList<byte[]>();
        AudioInfo audio = new AudioInfo();

        final MediaExtractor extractor = new MediaExtractor();
        int audioTrack = -1;
        boolean hasAudio = false;
        MediaCodec audioCodec = null;
        try {
            extractor.setDataSource(videoPath);
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat trackFormat = extractor.getTrackFormat(i);
                String mime = trackFormat.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("audio/")) {
                    audioTrack = i;
                    hasAudio = true;
                    break;
                }
            }
            if (hasAudio) {
                extractor.selectTrack(audioTrack);
                final int finalAudioTrack = audioTrack;
                MediaFormat trackFormat = extractor.getTrackFormat(finalAudioTrack);
                audioCodec = MediaCodec.createDecoderByType(trackFormat.getString(MediaFormat.KEY_MIME));

                long duration = Long.MAX_VALUE-1;
                if (trackFormat.containsKey(MediaFormat.KEY_DURATION)) {
                    duration = trackFormat.getLong(MediaFormat.KEY_DURATION);
                }

                if (cutStartMs >= 0L) {
                    extractor.seekTo(cutStartMs * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                }

                long firstTimeUs = cutStartMs * 1000L;
                long lastTimeUs = cutEndMs * 1000L;
                if (lastTimeUs <= firstTimeUs || lastTimeUs > duration) {
                    lastTimeUs = duration + 200L;
                }

//                int bit_rate = trackFormat.getInteger(MediaFormat.KEY_PCM_ENCODING);
                int sample_rate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                int channel_count = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                audio.sampleRate = sample_rate;
                audio.channelCount = channel_count;
                audioCodec.configure(trackFormat, null, null, 0);
                audioCodec.start();

                ByteBuffer[] inputBuffers = audioCodec.getInputBuffers();
                ByteBuffer[] outputBuffers = audioCodec.getOutputBuffers();
                MediaCodec.BufferInfo decodeBufferInfo = new MediaCodec.BufferInfo();
                MediaCodec.BufferInfo inputInfo = new MediaCodec.BufferInfo();
                boolean codeOver = false;
                boolean inputDone = false;//????????????????????????

                while (!codeOver) {
                    if (!inputDone) {
                        for (int i = 0; i < inputBuffers.length; i++) {
                            //???????????????????????? ??????????????????????????? ????????????????????????
                            int inputIndex = audioCodec.dequeueInputBuffer(TIMEOUT_USEC);
                            if (inputIndex >= 0) {
                                /**??????????????????????????? ??????????????? */
                                ByteBuffer inputBuffer = inputBuffers[inputIndex];//??????inputBuffer
                                inputBuffer.clear();//??????????????????inputBuffer????????????
                                int sampleSize = extractor.readSampleData(inputBuffer, 0);//MediaExtractor???????????????inputBuffer???

                                if (sampleSize < 0) {
                                    audioCodec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                    inputDone = true;
                                } else {

                                    inputInfo.offset = 0;
                                    inputInfo.size = sampleSize;
                                    inputInfo.flags = MediaCodec.BUFFER_FLAG_SYNC_FRAME;
                                    inputInfo.presentationTimeUs = extractor.getSampleTime();
                                    //Log.e("hero","????????????????????????---?????????????????????----"+inputInfo.presentationTimeUs);

                                    audioCodec.queueInputBuffer(inputIndex, inputInfo.offset, sampleSize, inputInfo.presentationTimeUs, 0);//??????MediaDecode???????????????????????????
                                    extractor.advance();//MediaExtractor????????????????????????
                                }
                            }
                        }
                    }

                    boolean decodeOutputDone = false;
                    byte[] chunkPCM;
                    while (!decodeOutputDone) {
                        int outputIndex = audioCodec.dequeueOutputBuffer(decodeBufferInfo, TIMEOUT_USEC);
                        if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            /**????????????????????????output*/
                            decodeOutputDone = true;
                        } else if (outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                            outputBuffers = audioCodec.getOutputBuffers();
                        } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            MediaFormat newFormat = audioCodec.getOutputFormat();
                        } else if (outputIndex < 0) {
                        } else {
                            if (decodeBufferInfo.presentationTimeUs > lastTimeUs) {
                                // Log.e("Test", "decodeBufferInfo.presentationTimeUs:"+decodeBufferInfo.presentationTimeUs+" finish!");
                                audioCodec.releaseOutputBuffer(outputIndex, false);
                                codeOver = true;
                                decodeOutputDone = true;
                            }else {
                                if (decodeBufferInfo.presentationTimeUs < firstTimeUs) {
                                    // Log.e("Test", "decodeBufferInfo.presentationTimeUs:"+decodeBufferInfo.presentationTimeUs+" Skip!");
                                    audioCodec.releaseOutputBuffer(outputIndex, false);
                                } else {
                                    // Log.e("Test", "decodeBufferInfo.presentationTimeUs:"+decodeBufferInfo.presentationTimeUs);
                                    ByteBuffer outputBuffer;
                                    if (Build.VERSION.SDK_INT >= 21) {
                                        outputBuffer = audioCodec.getOutputBuffer(outputIndex);
                                    } else {
                                        outputBuffer = outputBuffers[outputIndex];
                                    }
                                    chunkPCM = new byte[decodeBufferInfo.size];
                                    outputBuffer.get(chunkPCM);
                                    outputBuffer.clear();
                                    if (decodeBufferInfo.size != 0) {
                                        list_byte.add(chunkPCM);
                                    }
                                    audioCodec.releaseOutputBuffer(outputIndex, false);

                                    if ((decodeBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                        codeOver = true;
                                        decodeOutputDone = true;
                                    }
                                }
                            }
                        }
                    }
                }
                audio.singles = byteMerger(list_byte);
            }else {
                Log.e("hero", " extractor failed !!!! ??????????????????");
            }
        }catch (Exception e){
            e.printStackTrace();
            Log.e("hero", " extractor failed !!!!");
        }finally {
            /**
             * ??????????????????????????????????????????
             * */
            if(audioCodec!=null){
                try {
                    audioCodec.stop();
                }catch (Exception e){
                    e.printStackTrace();
                }
                try {
                    audioCodec.release();
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
            try {
                extractor.release();
            }catch (Exception e){
                e.printStackTrace();
            }
        }

        return audio;
    }

    private byte[] byteMerger(List<byte[]> listByte) {
        int lengthByte = 0;
        for (int i = 0; i < listByte.size(); i++) {
            lengthByte += listByte.get(i).length;
        }
        byte[] allByte = new byte[lengthByte];
        int countLength = 0;
        for (int i = 0; i < listByte.size(); i++) {
            byte[] b = listByte.get(i);
            System.arraycopy(b, 0, allByte, countLength, b.length);
            countLength += b.length;
        }
        return allByte;
    }

}
