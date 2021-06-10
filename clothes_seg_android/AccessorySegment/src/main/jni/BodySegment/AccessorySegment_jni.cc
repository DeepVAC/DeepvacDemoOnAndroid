// Tencent is pleased to support the open source community by making TNN available.
//
// Copyright (C) 2020 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.


#include "tnn_sdk_sample.h"
#include "kannarotate.h"
#include "yuv420sp_to_rgb_fast_asm.h"
#include <jni.h>
#include "helper_jni.h"
#include <android/bitmap.h>
#include "tnn/core/status.h"
#include <time.h>
#include <thread>

#include "AccessorySegment_jni.h"
#include "AccessoryDetect.h"

#define IS_SHOW_LOG true
#define MODE_INPUT_SIZE1 384

static std::shared_ptr<TNN_NS::AccessoryDetect> bodyDetector;

static int gComputeUnitType = 0; // 0 is cpu, 1 is gpu, 2 is huawei_npu

static unsigned char *yuvData = NULL;
static long yuvDataLen = 0;
static unsigned char *bgrData = NULL;
static long bgrDataLen = 0;
static u_char * faceData = NULL;

JNIEXPORT JNICALL jint TNN_BODY_SEGMENT(init)(JNIEnv *env, jobject thiz, jstring modelPath, jint computeUnitType) {
    bodyDetector = NULL;

    // Reset bench description
    setBenchResult("");
    std::string modelPathStr(jstring2string(env, modelPath));

    gComputeUnitType = computeUnitType;

    //modify
    gComputeUnitType = 1;
    //

    int threadCnt = std::thread::hardware_concurrency();
    LOGE("TNN_BODY_SEGMENT init threadCnt:%d",threadCnt);


    TNN_NS::Status status = TNN_NS::TNN_OK;
    //auto humOption = std::make_shared<TNN_NS::HumanDetectOption>();

    bodyDetector = std::make_shared<TNN_NS::AccessoryDetect>();
    std::string protoContent, modelContent;
    protoContent = fdLoadFile(modelPathStr + "/clothes.tnnproto");
    modelContent = fdLoadFile(modelPathStr + "/clothes.tnnmodel");
    LOGI("body dectect proto content size %d model content size %d", protoContent.length(), modelContent.length());

    auto option = std::make_shared<TNN_NS::AccessoryDetectOption>();
    option->library_path = "";
    option->proto_content = protoContent;
    option->model_content = modelContent;
    option->input_width = MODE_INPUT_SIZE1;
    option->input_height = MODE_INPUT_SIZE1;
    option->num_thread = threadCnt;
    option->mode = 0; //1;
    if (gComputeUnitType == 1) {
        LOGE("TNN_BODY_SEGMENT init TNNComputeUnitsGPU");
        option->compute_units = TNN_NS::TNNComputeUnitsGPU;
        status = bodyDetector->Init(option);
    } else if (gComputeUnitType == 2) {
        //add for huawei_npu store the om file
        option->compute_units = TNN_NS::TNNComputeUnitsHuaweiNPU;
        LOGE("TNN_BODY_SEGMENT init TNNComputeUnitsHuaweiNPU");
        bodyDetector->setNpuModelPath(modelPathStr + "/");
        bodyDetector->setCheckNpuSwitch(false);
        status = bodyDetector->Init(option);
    } else {
        LOGE("TNN_BODY_SEGMENT init TNNComputeUnitsCPU");
	    option->compute_units = TNN_NS::TNNComputeUnitsCPU;
    	status = bodyDetector->Init(option);
    }

    if (status != TNN_NS::TNN_OK) {
        LOGE("body detector init failed %d", (int)status);
        return -1;
    }
    return 0;
}

JNIEXPORT JNICALL jint TNN_BODY_SEGMENT(setOFD)(JNIEnv *env, jobject thiz, jboolean ofd) {
    auto asyncRefBodyDector = bodyDetector;
    asyncRefBodyDector->setOFDStatus(ofd);
    return 0;
}


JNIEXPORT JNICALL jboolean TNN_BODY_SEGMENT(checkNpu)(JNIEnv *env, jobject thiz, jstring modelPath) {
    return false;
    TNN_NS::AccessoryDetect humanDetector;

    std::string modelPathStr(jstring2string(env, modelPath));

    std::string protoContent, modelContent;
    protoContent = fdLoadFile(modelPathStr + "/clothes.tnnproto");
    modelContent = fdLoadFile(modelPathStr + "/clothes.tnnmodel");
    auto option = std::make_shared<TNN_NS::TNNSDKOption>();
    option->compute_units = TNN_NS::TNNComputeUnitsHuaweiNPU;
    option->library_path = "";
    option->proto_content = protoContent;
    option->model_content = modelContent;
    humanDetector.setNpuModelPath(modelPathStr + "/");
    humanDetector.setCheckNpuSwitch(true);
    TNN_NS::Status ret = humanDetector.Init(option);

    if(ret != TNN_NS::TNN_OK){
        LOGE("TNN_BODY_SEGMENT checkNpu init body detect failure!");
    }

    return ret == TNN_NS::TNN_OK;
}

JNIEXPORT JNICALL jint TNN_BODY_SEGMENT(deinit)(JNIEnv *env, jobject thiz) {
    LOGE("TNN_BODY_SEGMENT deinit");
    bodyDetector = nullptr;
    if(yuvData!=NULL){
        free(yuvData);
        yuvData = NULL;
        yuvDataLen = 0;
    }
    if(bgrData!=NULL){
        free(bgrData);
        bgrData = NULL;
        bgrDataLen = 0;
    }
    if(faceData!=NULL){
        free(faceData);
        faceData = NULL;
    }
    return 0;
}

JNIEXPORT JNICALL jboolean TNN_BODY_SEGMENT(predictFromStream)(JNIEnv *env, jobject thiz, jbyteArray yuv420sp, jint width, jint height, jint rotate,
                                                               jboolean detectBody, jboolean detectHead, jintArray outputData) {

    if(!detectBody && !detectHead){
        LOGE("-----------Not Select Detect Mode-----------");
        return false;
    }
    if(IS_SHOW_LOG) {
        LOGE("-----------Start-----------");
        LOGE("inputWidth:%d inputHeight:%d rotate:%d", width, height, rotate);
    }

    long startTime = clock();
    // --------------------------Input Process--------------------------
    // Convert yuv to rgb
    //LOGI("detect from stream %d x %d r %d", width, height, rotate);
    if(yuvData!=NULL && yuvDataLen!=height * width * 3 / 2){
        free(yuvData);
        yuvData = NULL;
        yuvDataLen = 0;
    }
    if(yuvData==NULL) {
        yuvDataLen = height * width * 3 / 2;
        yuvData = (unsigned char*)malloc(sizeof(unsigned char) * yuvDataLen);
    }
    auto p1=std::chrono::steady_clock::now();
    jbyte *yuvDataRef = env->GetByteArrayElements(yuv420sp, 0);
    int ret = kannarotate_yuv420sp((const unsigned char *) yuvDataRef, (int) width, (int) height,
                                   (unsigned char *) yuvData, (int) rotate);
    if (ret != 0) {
        LOGE("kannarotate_yuv420sp error !");
    }
    env->ReleaseByteArrayElements(yuv420sp, yuvDataRef, 0);
    if (rotate >= 5 &&
        rotate <= 8) { // 将width和height转为正常的方向, 和正常的角度不一样，是经过转换的，方便kannarotate_yuv420sp中使用
        width = width + height;
        height = width - height;
        width = width - height;
    }

    if(bgrDataLen!=height * width * 3) {
        if (bgrData != NULL) {
            free(bgrData);
            bgrData = NULL;
            bgrDataLen = 0;
        }
        if (bgrData != NULL) {
            free(bgrData);
            bgrData = NULL;
            bgrDataLen = 0;
        }
    }
    if(bgrData==NULL) {
        bgrDataLen = height * width * 3;
        bgrData = (unsigned char*)malloc(sizeof(unsigned char) * bgrDataLen);
    }
    //unsigned char *bgrData = new unsigned char[height * width * 3];
    //yuv420sp_to_rgb_fast_asm((const unsigned char *) yuvData, width, height, (unsigned char *) rgbData);
    yuv420sp_to_bgr_fast_asm((const unsigned char *) yuvData, width, height, (unsigned char *) bgrData);

    TNN_NS::DeviceType dt = TNN_NS::DEVICE_ARM;
    TNN_NS::DimsVector target_dims = {1, 3, height, width}; // 转成3通道的
    auto bgrTNN = std::make_shared<TNN_NS::Mat>(dt, TNN_NS::N8UC3, target_dims, bgrData);
    //tnn::DimsVector dimsVector = rgbTNN.get()->GetDims();
    // -----------------------Body Process-----------------------
    auto mask = env->GetIntArrayElements(outputData, nullptr);
    memset(mask, 0, sizeof(int) * width * height);

    bool isFound = false;
    int cropX = 0;
    int cropY = 0;
    int cropWidth = width;
    int cropHeight = height;
    bool detectHuman = false;

    auto p2=std::chrono::steady_clock::now();
    double p_s=std::chrono::duration<double>(p2-p1).count(); //秒
    LOGE("Preprocess coast:%f",p_s);

    auto asyncRefBodyDector = bodyDetector;
    asyncRefBodyDector->maskData = mask;
    std::string outputResult = "";
    std::shared_ptr<TNN_NS::TNNSDKInput> input = std::make_shared<TNN_NS::TNNSDKInput>(bgrTNN);
    std::shared_ptr<TNN_NS::TNNSDKOutput> output = asyncRefBodyDector->CreateSDKOutput();
    auto t1=std::chrono::steady_clock::now();
    TNN_NS::Status status = asyncRefBodyDector->Predict(input, output);
    if(IS_SHOW_LOG) {
        auto t2=std::chrono::steady_clock::now();
        double dr_s=std::chrono::duration<double>(t2-t1).count(); //秒
        //double dr_ms=std::chrono::duration<double,std::milli>(t2-t1).count();//毫秒级
        LOGE("Body Predict and ProcessSDKOutput coast:%fs", dr_s);
    }
    asyncRefBodyDector->maskData = NULL;
    if (status == TNN_NS::TNN_OK) {
        isFound = true;
    } else {
        LOGE("Body Detect Predict failed with:%s\n", status.description().c_str());
    }

    env->ReleaseIntArrayElements(outputData, mask, 0);

    return isFound;
}

JNIEXPORT void JNICALL TNN_BODY_SEGMENT(YUVtoARBG)(JNIEnv * env, jclass obj, jbyteArray yuv420sp, jint width, jint height, jintArray argbOut)
{

    int             sz;
    int             i;
    int             j;
    int             Y;
    int             Cr = 0;
    int             Cb = 0;
    int             pixPtr = 0;
    int             jDiv2 = 0;
    int             R = 0;
    int             G = 0;
    int             B = 0;
    int             cOff;
    int w = width;
    int h = height;
    sz = w * h;

    jint *rgbData = (jint*) (env->GetPrimitiveArrayCritical( argbOut, 0));
    jbyte* yuv = (jbyte*) (env->GetPrimitiveArrayCritical( yuv420sp, 0));

    for(j = 0; j < h; j++) {
        pixPtr = j * w;
        jDiv2 = j >> 1;
        for(i = 0; i < w; i++) {
            Y = yuv[pixPtr];
            if(Y < 0) Y += 255;
            if((i & 0x1) != 1) {
                cOff = sz + jDiv2 * w + (i >> 1) * 2;
                Cb = yuv[cOff];
                if(Cb < 0) Cb += 127; else Cb -= 128;
                Cr = yuv[cOff + 1];
                if(Cr < 0) Cr += 127; else Cr -= 128;
            }
            R = Y + Cr + (Cr >> 2) + (Cr >> 3) + (Cr >> 5);
            if(R < 0) R = 0; else if(R > 255) R = 255;
            G = Y - (Cb >> 2) + (Cb >> 4) + (Cb >> 5) - (Cr >> 1) + (Cr >> 3) + (Cr >> 4) + (Cr >> 5);
            if(G < 0) G = 0; else if(G > 255) G = 255;
            B = Y + Cb + (Cb >> 1) + (Cb >> 2) + (Cb >> 6);
            if(B < 0) B = 0; else if(B > 255) B = 255;
            rgbData[pixPtr++] = 0xff000000 + (B << 16) + (G << 8) + R;
        }
    }

    env->ReleasePrimitiveArrayCritical( argbOut, rgbData, 0);
    env->ReleasePrimitiveArrayCritical( yuv420sp, yuv, 0);
}

