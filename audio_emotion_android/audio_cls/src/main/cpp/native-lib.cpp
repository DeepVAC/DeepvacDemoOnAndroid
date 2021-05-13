#include <jni.h>
#include <string>
#include <memory>

#include <android/bitmap.h>

#include <fstream>
#include <iostream>
using namespace std;


#include "audio_emotion_detect.h"
#import "essentia/syszux_vggish.h"

// 一般定义在公共文件
#include <android/log.h>
#define  LOG_TAG    "test===="
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)


static std::shared_ptr<AudioEmotionDetect> g_audio_emiton_detect = nullptr;
static const int g_single_audio_dur = 4;

extern "C" JNIEXPORT jstring JNICALL
Java_com_audiocls_lib_AudioCls_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";

    return env->NewStringUTF(hello.c_str());
}
extern "C"
JNIEXPORT jintArray JNICALL
Java_com_audiocls_lib_AudioCls_audioEmotionInference(JNIEnv *env, jobject thiz, jbyteArray bytes, jint size, jint samplerate, jint channel_count) {

    signed char *pBuf = env->GetByteArrayElements(bytes, 0);
    int jump = 2 * channel_count;
    std::vector<float> signal;
    for(int i = 0; i < size; i += jump) {
        int v = (int)(pBuf[i+1] << 8 | pBuf[i] & 0xff);
        double p = v / 32768.;
        signal.push_back(p);
    }


    std::vector<int> r;
    SyszuxVggish sv;
    auto result11 = sv.getVggishInput(signal,samplerate);
    for(int i = 0; i < result11.size(); ++i) {
        r.push_back(g_audio_emiton_detect->inference(result11[i].get(), 319, 64, true));
    }

//    const unsigned char* rgb = result11[0].get();

    int length = r.size();
    jintArray jarr = env->NewIntArray(length);
    jint* arr = env->GetIntArrayElements(jarr, NULL);
    int m = 0;
    for(auto v : r) {
        arr[m++] = v;
    }

    env->SetIntArrayRegion(jarr, 0, length, arr);
    return jarr;

}

extern "C"
JNIEXPORT void JNICALL
Java_com_audiocls_lib_AudioCls_audioEmotionInit(JNIEnv *env, jobject thiz, jstring model_path) {
    const char *c_model_path = env->GetStringUTFChars(model_path, NULL);
//    g_scene_classify = new SceneClassify(c_model_path);

    g_audio_emiton_detect = std::make_shared<AudioEmotionDetect>(c_model_path);//new AudioEmotionDetectt(c_model_path);

    env->ReleaseStringUTFChars(model_path, c_model_path);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_audiocls_lib_AudioCls_audioEmotionInit2(JNIEnv *env, jobject thiz, jstring paramPath, jstring binPath) {
    const char *c_param_path = env->GetStringUTFChars(paramPath, NULL);
    const char *c_bin_path = env->GetStringUTFChars(binPath, NULL);
//    g_scene_classify = new SceneClassify(c_model_path);

    g_audio_emiton_detect = std::make_shared<AudioEmotionDetect>(c_param_path, c_bin_path);//new AudioEmotionDetectt(c_model_path);

    env->ReleaseStringUTFChars(paramPath, c_param_path);
    env->ReleaseStringUTFChars(binPath, c_bin_path);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_audiocls_lib_AudioCls_audioEmotionRelease(JNIEnv *env, jobject thiz) {
    if(g_audio_emiton_detect!=nullptr) {
        g_audio_emiton_detect->release();
        g_audio_emiton_detect = nullptr;
    }
}

//
//extern "C"
//JNIEXPORT void JNICALL
//Java_com_face3d_demo_MainActivity_sceneClsDel(JNIEnv *env, jobject thiz) {
////    if(g_scene_classify)
////        delete g_scene_classify;
//}




