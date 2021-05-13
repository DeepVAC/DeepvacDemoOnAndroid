#include <jni.h>
#include <memory>
#include <string>
#include <android/bitmap.h>
#include "libyuv.h"
#include "SceneClassify.h"

#define RGB565_B(p) ((((p) & 0xF800) >> 11) << 3)
#define RGB565_G(p) ((((p) & 0x7E0 ) >> 5)  << 2)
#define RGB565_R(p) ( ((p) & 0x1F  )        << 3)

#define RGBA_A(p) (((p) & 0xFF000000) >> 24)
#define RGBA_B(p) (((p) & 0x00FF0000) >> 16)
#define RGBA_G(p) (((p) & 0x0000FF00) >>  8)
#define RGBA_R(p)  ((p) & 0x000000FF)


#include <android/log.h>
#define  LOG_TAG1    "test=="
#define  LOGII(...)  __android_log_print(ANDROID_LOG_INFO, LOG_TAG1, __VA_ARGS__)


static std::shared_ptr<SceneClassify> g_scene_classify = nullptr;

extern "C"
JNIEXPORT jintArray JNICALL
Java_com_scls_library_SceneRecognition_sceneClsInference(JNIEnv *env, jobject thiz, jobject zBitmap) {
    AndroidBitmapInfo info;
    memset(&info, 0, sizeof(info));
    AndroidBitmap_getInfo(env, zBitmap, &info);

    int format = info.format;
    std::string format_str = std::to_string(format);
    const char* c_format_str = format_str.c_str();
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG1, "%s", c_format_str);

    // Check format, only RGB565 & RGBA are supported
    if (info.width <= 0 || info.height <= 0 ||
        (info.format != ANDROID_BITMAP_FORMAT_RGB_565 && info.format != ANDROID_BITMAP_FORMAT_RGBA_8888)) {
        jintArray jarr = env->NewIntArray(2);
        jint* arr = env->GetIntArrayElements(jarr, NULL);
        for(int i = 0; i < 2; ++i) {
            arr[i] = -1;
        }
        env->SetIntArrayRegion(jarr, 0, 2, arr);
        return jarr;
    }

    // Lock the bitmap to get the buffer
    void * pixels = NULL;
    int res = AndroidBitmap_lockPixels(env, zBitmap, &pixels);
    std::auto_ptr<unsigned char> vec_pixels(new unsigned char[info.width*info.height*3]);
    unsigned char* pixel_data = vec_pixels.get();

    int x = 0, y = 0;
    int index = 0;
    for (y = 0; y < info.height; ++y) {
        for (x = 0; x < info.width; ++x) {
            unsigned char a = 0, r = 0, g = 0, b = 0;
            void *pixel = NULL;
            if (info.format == ANDROID_BITMAP_FORMAT_RGB_565) {
                pixel = ((uint16_t *)pixels) + y * info.width + x;
                uint16_t v = *(uint16_t *)pixel;
                r = RGB565_R(v);
                g = RGB565_G(v);
                b = RGB565_B(v);
                pixel_data[index++] = r;
                pixel_data[index++] = g;
                pixel_data[index++] = b;
            } else if(info.format == ANDROID_BITMAP_FORMAT_RGBA_8888){// RGBA
                pixel = ((uint32_t *)pixels) + y * info.width + x;
                uint32_t v = *(uint32_t *)pixel;
                a = RGBA_A(v);
                r = RGBA_R(v);
                g = RGBA_G(v);
                b = RGBA_B(v);
                pixel_data[index++] = r;
                pixel_data[index++] = g;
                pixel_data[index++] = b;
            }
        }
    }

    AndroidBitmap_unlockPixels(env, zBitmap);

    std::vector<int> result = g_scene_classify->inference(pixel_data, info.width, info.height);
    int length = result.size();
    jintArray jarr = env->NewIntArray(length);
    jint* arr = env->GetIntArrayElements(jarr, NULL);
    for(int i = 0; i < length; ++i) {
        arr[i] = result[i];
    }
//    env->ReleaseIntArrayElements(jarr, arr, 0);
    env->SetIntArrayRegion(jarr, 0, length, arr);
    return jarr;
}

extern "C"
JNIEXPORT jintArray JNICALL
Java_com_scls_library_SceneRecognition_sceneClsInferenceYUV(JNIEnv *env, jobject thiz, jbyteArray bytes, jint width, jint height) {

    unsigned char *pBuf = (unsigned char *)env->GetByteArrayElements(bytes, 0);
    std::unique_ptr<unsigned char[]> bgr(new unsigned char[width*height*3]);
    unsigned char* bgr_data = bgr.get();
    unsigned char *ybase = pBuf;
    unsigned char *vubase = &pBuf[width * height];

    libyuv::NV21ToRGB24(ybase, width, vubase, width, bgr_data, width*3, width, height);

    std::vector<int> result = g_scene_classify->inference(bgr_data, width, height, true);
    int length = result.size();
    jintArray jarr = env->NewIntArray(length);
    jint* arr = env->GetIntArrayElements(jarr, NULL);
    for(int i = 0; i < length; ++i) {
        arr[i] = result[i];
    }

    env->SetIntArrayRegion(jarr, 0, length, arr);
    return jarr;
//    int length = result.size();
//    jintArray jarr = env->NewIntArray(length);
//    jint* arr = env->GetIntArrayElements(jarr, NULL);
//    for(int i = 0; i < length; ++i) {
//        arr[i] = result[i];
//    }
////    env->ReleaseIntArrayElements(jarr, arr, 0);
//    env->SetIntArrayRegion(jarr, 0, length, arr);
//    return jarr;
}


extern "C"
JNIEXPORT jintArray JNICALL
Java_com_scls_library_SceneRecognition_sceneClsInferenceBytes(JNIEnv *env, jobject thiz, jbyteArray bytes, jint width, jint height) {
    unsigned char *pBuf = (unsigned char *)env->GetByteArrayElements(bytes, 0);

    std::vector<int> result = g_scene_classify->inference(pBuf, width, height);
    int length = result.size();
    jintArray jarr = env->NewIntArray(length);
    jint* arr = env->GetIntArrayElements(jarr, NULL);
    for(int i = 0; i < length; ++i) {
        arr[i] = result[i];
    }
//    env->ReleaseIntArrayElements(jarr, arr, 0);
    env->SetIntArrayRegion(jarr, 0, length, arr);
    return jarr;
}




//extern "C"
//JNIEXPORT jfloatArray JNICALL
//Java_com_scls_library_SceneRecognition_sceneClsInference1(JNIEnv *env, jobject thiz, jobject zBitmap) {
//    AndroidBitmapInfo info;
//    memset(&info, 0, sizeof(info));
//    AndroidBitmap_getInfo(env, zBitmap, &info);
//    // Check format, only RGB565 & RGBA are supported
////    if (info.width <= 0 || info.height <= 0 ||
////        (info.format != ANDROID_BITMAP_FORMAT_RGB_565 && info.format != ANDROID_BITMAP_FORMAT_RGBA_8888)) {
////        return -1;
////    }
//
//    // Lock the bitmap to get the buffer
//    void * pixels = NULL;
//    int res = AndroidBitmap_lockPixels(env, zBitmap, &pixels);
//    std::auto_ptr<unsigned char> vec_pixels(new unsigned char[info.width*info.height*3]);
//    unsigned char* pixel_data = vec_pixels.get();
//
//    int x = 0, y = 0;
//    int index = 0;
//    for (y = 0; y < info.height; ++y) {
//        for (x = 0; x < info.width; ++x) {
//            unsigned char a = 0, r = 0, g = 0, b = 0;
//            void *pixel = NULL;
//            if (info.format == ANDROID_BITMAP_FORMAT_RGB_565) {
//                pixel = ((uint16_t *)pixels) + y * info.width + x;
//                uint16_t v = *(uint16_t *)pixel;
//                r = RGB565_R(v);
//                g = RGB565_G(v);
//                b = RGB565_B(v);
//                pixel_data[index++] = r;
//                pixel_data[index++] = g;
//                pixel_data[index++] = b;
//            } else {// RGBA
//                pixel = ((uint32_t *)pixels) + y * info.width + x;
//                uint32_t v = *(uint32_t *)pixel;
//                a = RGBA_A(v);
//                r = RGBA_R(v);
//                g = RGBA_G(v);
//                b = RGBA_B(v);
//                pixel_data[index++] = r;
//                pixel_data[index++] = g;
//                pixel_data[index++] = b;
//            }
//        }
//    }
//
//    AndroidBitmap_unlockPixels(env, zBitmap);
//
//    auto result = g_scene_classify->inference1(pixel_data, info.width, info.height);
//
//    int length = result.size();
//    jfloatArray jarr = env->NewFloatArray(length);
//    jfloat* arr = env->GetFloatArrayElements(jarr, NULL);
//    int m = 0;
//    for(auto v : result) {
//        arr[m++] = v;
//    }
//
//    env->SetFloatArrayRegion(jarr, 0, length, arr);
//    return jarr;
////    return result;
//
//}

extern "C"
JNIEXPORT void JNICALL
Java_com_scls_library_SceneRecognition_sceneClsInit(JNIEnv *env, jobject thiz, jstring model_path) {
    const char *c_model_path = env->GetStringUTFChars(model_path, NULL);
    g_scene_classify = std::make_shared<SceneClassify>(c_model_path);//new SceneClassify(c_model_path);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_scls_library_SceneRecognition_sceneRelease(JNIEnv *env, jobject thiz) {
    if(g_scene_classify!= nullptr){
        g_scene_classify->releaseRes();
        g_scene_classify = nullptr;
    }
}


extern "C"
JNIEXPORT jintArray JNICALL
Java_com_scls_library_SceneRecognition_sceneFrameExtract(JNIEnv *env, jobject thiz, jintArray frames, jint size, jint rate) {
    int *pBuf = (int *)env->GetIntArrayElements(frames, 0);
    auto all_frames = Strategy::frameExtract(pBuf, size, rate);
    int length = 0;
    for (auto& v : all_frames) {
        length += v.size();
    }
    length += all_frames.size();
    jintArray jarr = env->NewIntArray(length);
    jint* arr = env->GetIntArrayElements(jarr, NULL);
    jint index = 0;
    for(auto& v : all_frames) {
        for(auto& frame : v) {
            arr[index++] = frame;
        }
        arr[index++] = -1;
    }

    env->SetIntArrayRegion(jarr, 0, length, arr);
    return jarr;
}

extern "C"
JNIEXPORT jintArray JNICALL
Java_com_scls_library_SceneRecognition_sceneFrameExtractV1(JNIEnv *env, jobject thiz, jintArray frames, jint size, jint frameRate) {
    int *pBuf = (int *)env->GetIntArrayElements(frames, 0);
    auto all_frames = Strategy::frameExtractV1(pBuf, size, frameRate);
    int length = 0;
    for (auto& v : all_frames) {
        length += v.size();
    }
    length += all_frames.size();
    jintArray jarr = env->NewIntArray(length);
    jint* arr = env->GetIntArrayElements(jarr, NULL);
    jint index = 0;
    for(auto& v : all_frames) {
        for(auto& frame : v) {
            arr[index++] = frame;
        }
        arr[index++] = -1;
    }

    env->SetIntArrayRegion(jarr, 0, length, arr);
    return jarr;
}

extern "C"
JNIEXPORT jintArray JNICALL
Java_com_scls_library_SceneRecognition_sceneFindMax(JNIEnv *env, jobject thiz, jintArray frames, jint size) {
    int *pBuf = (int *)env->GetIntArrayElements(frames, 0);
    std::vector<std::vector<int>> indexes;
    std::vector<int> temp;
    for(int i = 0; i < size; ++i) {
        if(pBuf[i] == -1) {
            indexes.push_back(temp);
            temp.clear();
            continue;
        }
        temp.push_back(pBuf[i]);
    }

    auto result = Strategy::findMaxValue(indexes);

    int length = result.size();
    jintArray jarr = env->NewIntArray(length);
    jint* arr = env->GetIntArrayElements(jarr, NULL);
    jint index = 0;
    for(auto& v : result) {
        arr[index++] = v;
    }

    env->SetIntArrayRegion(jarr, 0, length, arr);
    return jarr;
}

