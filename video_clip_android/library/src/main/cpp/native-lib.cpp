#include <jni.h>
#include <memory>

//#include "include/deepvac_sb.h"
#include "libyuv.h"
#include "util.h"
#include "deepvac_sb.h"


std::shared_ptr<DeepvacSB> video_clip;
int g_width;
int g_height;

extern "C"
JNIEXPORT int JNICALL
Java_com_sbd_library_ShotSegmention_checkVideoFileValid(JNIEnv *env, jobject thiz, jstring img_path) {
    const char* c_img_path = env->GetStringUTFChars(img_path, NULL);
    return util::CheckVideoFileValid(c_img_path);
}

extern "C"
JNIEXPORT int JNICALL
Java_com_sbd_library_ShotSegmention_checkVideoContentValid(JNIEnv *env, jobject thiz, jint width, jint height, jfloat durationS) {
    return util::CheckVideoContentValid(width, height, durationS);
}


extern "C"
JNIEXPORT void JNICALL
Java_com_sbd_library_ShotSegmention_init(JNIEnv *env, jobject thiz) {
    video_clip = std::make_shared<DeepvacSB>();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_sbd_library_ShotSegmention_reset(JNIEnv *env, jobject thiz) {
    video_clip->reset();
}


extern "C"
JNIEXPORT void JNICALL
Java_com_sbd_library_ShotSegmention_cacheFeatureFromNV21(JNIEnv *env, jobject thiz, jbyteArray bytes, jint width, jint height) {
    unsigned char *pBuf = (unsigned char *)env->GetByteArrayElements(bytes, 0);
    std::unique_ptr<unsigned char[]> bgr(new unsigned char[width*height*3]);
    unsigned char* bgr_data = bgr.get();
    unsigned char *ybase = pBuf;
    unsigned char *vubase = &pBuf[width * height];
    libyuv::NV21ToRGB24(ybase, width, vubase, width, bgr_data, width*3, width, height);
    video_clip->cacheFeatureFromHwcBgrFrame(bgr_data, width, height);
}


extern "C"
JNIEXPORT jintArray JNICALL
Java_com_sbd_library_ShotSegmention_getSbIdx(JNIEnv *env, jobject thiz) {
    std::vector<unsigned int> indexes = video_clip->getSbIdx();
    int length = indexes.size();
    jintArray jarr = env->NewIntArray(length);
    jint* arr = env->GetIntArrayElements(jarr, NULL);
    for(int i = 0; i < length; ++i) {
        arr[i] = indexes[i];
    }
    env->SetIntArrayRegion(jarr, 0, length, arr);
    return jarr;
}
