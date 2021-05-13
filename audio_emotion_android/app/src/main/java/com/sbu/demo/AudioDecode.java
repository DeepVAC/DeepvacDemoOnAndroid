package com.sbu.demo;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;


import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;


public class AudioDecode {

    //"/storage/emulated/0/gemfield_hk.MP4"

    final static int TIMEOUT_USEC = 0;

    public AudioInfo getPCMFromVideo(String videoPath) {

        Vector vec = new Vector();
        List<byte[]> list_byte = new ArrayList<byte[]>();
        AudioInfo audio = new AudioInfo();

        final MediaExtractor extractor = new MediaExtractor();
        int audioTrack = -1;
        boolean hasAudio = false;
        try {
            extractor.setDataSource(videoPath);
            for (int i = 0; i < extractor.getTrackCount(); i++) {

//                extractor.readSampleData();

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
                MediaCodec audioCodec = MediaCodec.createDecoderByType(trackFormat.getString(MediaFormat.KEY_MIME));


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
                boolean inputDone = false;//整体输入结束标志

                while (!codeOver) {
                    if (!inputDone) {
                        for (int i = 0; i < inputBuffers.length; i++) {
                            //遍历所以的编码器 然后将数据传入之后 再去输出端取数据
                            int inputIndex = audioCodec.dequeueInputBuffer(TIMEOUT_USEC);
                            if (inputIndex >= 0) {
                                /**从分离器中拿到数据 写入解码器 */
                                ByteBuffer inputBuffer = inputBuffers[inputIndex];//拿到inputBuffer
                                inputBuffer.clear();//清空之前传入inputBuffer内的数据
                                int sampleSize = extractor.readSampleData(inputBuffer, 0);//MediaExtractor读取数据到inputBuffer中

                                if (sampleSize < 0) {
                                    audioCodec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                    inputDone = true;
                                } else {

                                    inputInfo.offset = 0;
                                    inputInfo.size = sampleSize;
                                    inputInfo.flags = MediaCodec.BUFFER_FLAG_SYNC_FRAME;
                                    inputInfo.presentationTimeUs = extractor.getSampleTime();
                                    //Log.e("hero","往解码器写入数据---当前帧的时间戳----"+inputInfo.presentationTimeUs);

                                    audioCodec.queueInputBuffer(inputIndex, inputInfo.offset, sampleSize, inputInfo.presentationTimeUs, 0);//通知MediaDecode解码刚刚传入的数据
                                    extractor.advance();//MediaExtractor移动到下一取样处
                                }
                            }
                        }
                    }

                    boolean decodeOutputDone = false;
                    byte[] chunkPCM;
                    while (!decodeOutputDone) {
                        int outputIndex = audioCodec.dequeueOutputBuffer(decodeBufferInfo, TIMEOUT_USEC);
                        if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            /**没有可用的解码器output*/
                            decodeOutputDone = true;
                        } else if (outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                            outputBuffers = audioCodec.getOutputBuffers();
                        } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            MediaFormat newFormat = audioCodec.getOutputFormat();
                        } else if (outputIndex < 0) {
                        } else {
                            ByteBuffer outputBuffer;
                            if (Build.VERSION.SDK_INT >= 21) {
                                outputBuffer = audioCodec.getOutputBuffer(outputIndex);
                            } else {
                                outputBuffer = outputBuffers[outputIndex];
                            }

                            chunkPCM = new byte[decodeBufferInfo.size];
                            outputBuffer.get(chunkPCM);
                            outputBuffer.clear();

                            if(decodeBufferInfo.size != 0) {
                                list_byte.add(chunkPCM);
                            }
//                            fos.write(chunkPCM);//数据写入文件中
//                            fos.flush();
//                            Log.e("hero","---释放输出流缓冲区----:::"+outputIndex);
                            audioCodec.releaseOutputBuffer(outputIndex, false);


                            //test------------------------------
//                            for(int i = 0; i < decodeBufferInfo.size-1; i += 2) {
//                                int v = (int)(chunkPCM[i+1] << 8 | chunkPCM[i] & 0xff);
//                                vec.addElement(new Integer(v));
//                            }
                            //-----------------------------------



                            if ((decodeBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                /**
                                 * 解码结束，释放分离器和解码器
                                 * */
                                extractor.release();

                                audioCodec.stop();
                                audioCodec.release();
                                codeOver = true;
                                decodeOutputDone = true;
                            }
                        }

                    }
                }

//                for(int i = 0; i < list_byte.size(); ++i) {
//                    byte[] frame = list_byte.get(i);
//
//                    for(int j = 0; j < frame.length-1; j+=2) {
//                        int v = (int)(frame[j+1] << 8 | frame[j] & 0xff);
//                        vec.addElement(new Integer(v));
//                    }
//                }
////
////
//                Vector vec1 = new Vector();
//                for(int i = 0; i < vec.size(); i+=2) {
//                    vec1.addElement(vec.get(i));
//                }
//
//
//                String s2 = new String();
//                Vector vec2 = new Vector();
//                for(int i = 0; i < 20; ++i) {
//                    vec2.addElement(vec1.get(i));
//                    int m = (int)(vec1.get(i));
//                    s2 += String.valueOf(m) + " ";
//                }
//
//                String s3 = new String();
//                Vector vec3 = new Vector();
//                for(int i = 10000; i < 10021; ++i) {
//                    vec3.addElement(vec1.get(i));
//                    int m = (int)(vec1.get(i));
//                    s3 += String.valueOf(m) + " ";
//                }
//
//                String s4 = new String();
//                Vector vec4 = new Vector();
//                for(int i = 300000; i < 300021; ++i) {
//                    vec4.addElement(vec1.get(i));
//                    int m = (int)(vec1.get(i));
//                    s4 += String.valueOf(m) + " ";
//                }
//
//                String s5 = new String();
//                Vector vec5 = new Vector();
//                for(int i = vec1.size()-20; i < vec1.size(); ++i) {
//                    vec5.addElement(vec1.get(i));
//                    int m = (int)(vec1.get(i));
//                    s5 += String.valueOf(m) + " ";
//                }
//
//
//                 int zero0 = 0;
//                for(int i = 0; i < vec1.size(); ++i) {
//                    int t = (int)vec1.get(i);
//                    if(t==0) {
//                        ++zero0;
//                    } else{
//                        break;
//                    }
//                }
//
//                int zero1=0;
//                for(int i = 0; i < vec1.size(); ++i) {
//                    int t = (int)vec1.get(i);
//                    if(t==0) {
//                        ++zero1;
//                    }
//                }


//                FileOutputStream fileOutputStream;
//                BufferedWriter bufferedWriter;
//
//                File file = new File("/storage/emulated/0/test2.txt");
//                try {
//                    file.createNewFile();
//                    fileOutputStream = new FileOutputStream(file);
//                    bufferedWriter = new BufferedWriter(new OutputStreamWriter(fileOutputStream));
//
//                    for(int i = 0; i < vec1.size(); ++i) {
//                        int v = (int)vec1.get(i);
//                        String str = String.valueOf(v);
//                        str += "\n";
//                        bufferedWriter.write(str);
//                    }
//
//                    bufferedWriter.close();
//                } catch (IOException e) {
//                    e.printStackTrace();
//
//                }


                audio.singles = byteMerger(list_byte);

//                ByteBuffer byteBuffer = ByteBuffer.allocate(trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
//                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
//
//                extractor.readSampleData(byteBuffer, 0);
//                if (extractor.getSampleFlags() == MediaExtractor.SAMPLE_FLAG_SYNC) {
//                    extractor.advance();
//                }
//
//                int channel = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
//
//                while (true) {
//                    int readSampleSize = extractor.readSampleData(byteBuffer, 0);
//                    Log.e("hero","---读取音频数据，当前读取到的大小-----：：："+readSampleSize);
//                    if (readSampleSize < 0) {
//                        break;
//                    }
//
//
//                    extractor.advance();//移动到下一帧
//                }
//                mediaMuxer.release();
//                extractor.release();



            }else {
                Log.e("hero", " extractor failed !!!! 没有音频信道");
            }
        }catch (Exception e){
            e.printStackTrace();
            Log.e("hero", " extractor failed !!!!");
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
