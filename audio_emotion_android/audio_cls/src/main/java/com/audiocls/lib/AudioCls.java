package com.audiocls.lib;

import android.content.Context;

import com.audio_cls.lib.R;

import java.util.Vector;

public class AudioCls {

    static {
        System.loadLibrary("audio_cls");
    }

    private static boolean isCopyRes = false;
    private AudioDecode audioDecode;

    public AudioCls(Context context) {
        audioDecode = new AudioDecode();
        String cachePath = FileStorageHelper.getDiskCacheDir(context);
        String modelPath = cachePath + "/model";
        FileStorageHelper.copyFilesFromRaw(context, R.raw.audio_bin, "audio.bin", modelPath, isCopyRes);
        FileStorageHelper.copyFilesFromRaw(context, R.raw.audio_param, "audio.param", modelPath, isCopyRes);

        isCopyRes = true;
        audioEmotionInit(modelPath);
    }

    public AudioCls(Context context, String paramPath, String binPath) {
        audioDecode = new AudioDecode();
        isCopyRes = true;
        audioEmotionInit2(paramPath, binPath);
    }


    public native void audioEmotionInit(String modelPath);
    public native void audioEmotionInit2(String paramPath, String binPath);
    public native int []audioEmotionInference(byte[] bytes, int size, int samplerate, int channel_count);
    public native void audioEmotionRelease();

    public void release(){
        audioEmotionRelease();
    }
    public Vector inference(String video_path, long cutStartMs, long cutEndMs) {
        Vector vec = new Vector();

        AudioInfo result = audioDecode.getPCMFromVideo(video_path, cutStartMs, cutEndMs);
        if(result.singles==null){
            // 没有音频或者是出错了
            return vec;
        }
        int[] r = audioEmotionInference(result.singles, result.singles.length, result.sampleRate, result.channelCount);

        if(r.length == 0) return vec;
        vec.add(r[0]);
        int prev = r[0];

        for(int i = 1; i < r.length; ++i) {
            if(r[i] == prev) {
                continue;
            } {
                vec.add(r[i]);
                prev = r[i];
            }
        }

        return vec;
    }
}
