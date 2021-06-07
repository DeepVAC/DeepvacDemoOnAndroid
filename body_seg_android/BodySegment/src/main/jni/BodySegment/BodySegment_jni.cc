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


#include "FaceDetect.h"
#include "tnn_sdk_sample.h"
#include "kannarotate.h"
#include "yuv420sp_to_rgb_fast_asm.h"
#include <jni.h>
#include "helper_jni.h"
#include <android/bitmap.h>
#include "tnn/core/status.h"
#include <time.h>
#include <thread>

#include "BodySegment_jni.h"
#include "HumanDetect.h"
#include "BodyDetect.h"
#include "HeadDetect.h"

#define IS_SHOW_LOG false
#define MODE_INPUT_SIZE1 384  // 416 320 384 512
#define MODE_INPUT_SIZE2 320  // 416 320
#define HUMMAN_DETECT_THRESHOLD 0.3 // 0.5
#define KEY_POINT_THRESHOLD 0.25 // 0.3


#include <chrono>


static std::shared_ptr<TNN_NS::HumanDetect> humanDetector;
static std::shared_ptr<TNN_NS::BodyDetect> bodyDetector;
static std::shared_ptr<TNN_NS::FaceDetect> faceDetector;
static std::shared_ptr<TNN_NS::HeadDetect> headDetector;

static int gComputeUnitType = 0; // 0 is cpu, 1 is gpu, 2 is huawei_npu

static unsigned char *yuvData = NULL;
static long yuvDataLen = 0;
static unsigned char *bgrData = NULL;
static unsigned char *rgbData = NULL;
static long bgrDataLen = 0;
static u_char * faceData = NULL;

JNIEXPORT JNICALL jint TNN_BODY_SEGMENT(init)(JNIEnv *env, jobject thiz, jstring modelPath, jint computeUnitType) {
    bodyDetector = NULL;
    faceDetector = NULL;
    headDetector = NULL;
    humanDetector = NULL;

    // Reset bench description
    setBenchResult("");
    std::string modelPathStr(jstring2string(env, modelPath));

    gComputeUnitType = computeUnitType;
    //modify
    gComputeUnitType = 1;
    ///

    int threadCnt = std::thread::hardware_concurrency();
    LOGE("TNN_BODY_SEGMENT init threadCnt:%d",threadCnt);


    humanDetector = std::make_shared<TNN_NS::HumanDetect>();
    std::string humProtoContent, humModelContent;
    humProtoContent = fdLoadFile(modelPathStr + "/human.tnnproto");
    humModelContent = fdLoadFile(modelPathStr + "/human.tnnmodel");
    LOGI("humman dectect proto content size %d model content size %d", humProtoContent.length(), humModelContent.length());
    TNN_NS::Status status = TNN_NS::TNN_OK;
    auto humOption = std::make_shared<TNN_NS::HumanDetectOption>();
    humOption->library_path = "";
    humOption->proto_content = humProtoContent;
    humOption->model_content = humModelContent;
    humOption->input_width = MODE_INPUT_SIZE1;
    humOption->input_height = MODE_INPUT_SIZE1;
    humOption->num_thread = threadCnt;
    humOption->mode = 0; //1;
    if (gComputeUnitType == 1) {
        LOGE("TNN_BODY_SEGMENT init TNNComputeUnitsGPU");
        humOption->compute_units = TNN_NS::TNNComputeUnitsGPU;
        status = humanDetector->Init(humOption);
    } else if (gComputeUnitType == 2) {
        //add for huawei_npu store the om file
        humOption->compute_units = TNN_NS::TNNComputeUnitsHuaweiNPU;
        LOGE("TNN_Human_SEGMENT init TNNComputeUnitsHuaweiNPU");
        humanDetector->setNpuModelPath(modelPathStr + "/");
        humanDetector->setCheckNpuSwitch(false);
        status = humanDetector->Init(humOption);
    } else {
        LOGE("TNN_HUMAN_SEGMENT init TNNComputeUnitsCPU");
        humOption->compute_units = TNN_NS::TNNComputeUnitsCPU;
        status = humanDetector->Init(humOption);
    }

    if (status != TNN_NS::TNN_OK) {
        LOGE("human detector init failed %d", (int)status);
        return -1;
    }


    bodyDetector = std::make_shared<TNN_NS::BodyDetect>();
    std::string protoContent, modelContent;
    protoContent = fdLoadFile(modelPathStr + "/body.tnnproto");
    modelContent = fdLoadFile(modelPathStr + "/body.tnnmodel");
    LOGI("body dectect proto content size %d model content size %d", protoContent.length(), modelContent.length());

    auto option = std::make_shared<TNN_NS::BodyDetectOption>();
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

    // -----------------------------------------------------------------
    faceDetector = std::make_shared<TNN_NS::FaceDetect>();
    std::string faceProtoContent, faceModelContent;
    faceProtoContent = fdLoadFile(modelPathStr + "/face.tnnproto");
    faceModelContent = fdLoadFile(modelPathStr + "/face.tnnmodel");
    LOGE("Face model path:%s_____%s__", faceProtoContent.c_str(), faceModelContent.c_str());
    LOGI("face_dectect proto content size %d model content size %d", faceProtoContent.length(), faceModelContent.length());

    gComputeUnitType = computeUnitType;

    auto faceOption = std::make_shared<TNN_NS::FaceDetectOption>();
    faceOption->library_path = "";
    faceOption->proto_content = faceProtoContent;
    faceOption->model_content = faceModelContent;
    faceOption->input_width = MODE_INPUT_SIZE2;
    faceOption->input_height = MODE_INPUT_SIZE2;
    faceOption->num_thread = threadCnt;
    faceOption->mode = 1;
    if (gComputeUnitType == 1) {
        faceOption->compute_units = TNN_NS::TNNComputeUnitsGPU;
        status = faceDetector->Init(faceOption);
    } else if (gComputeUnitType == 2) {
        //add for huawei_npu store the om file
        faceOption->compute_units = TNN_NS::TNNComputeUnitsHuaweiNPU;
        faceDetector->setNpuModelPath(modelPathStr + "/");
        faceDetector->setCheckNpuSwitch(false);
        status = faceDetector->Init(faceOption);
    } else {
        faceOption->compute_units = TNN_NS::TNNComputeUnitsCPU;
        status = faceDetector->Init(faceOption);
    }

    if (status != TNN_NS::TNN_OK) {
        LOGE("face detector init failed %d", (int)status);
        return -1;
    }
    // -----------------------------------------------------------------
    headDetector = std::make_shared<TNN_NS::HeadDetect>();
    std::string headProtoContent, headModelContent;
    headProtoContent = fdLoadFile(modelPathStr + "/head.tnnproto");
    headModelContent = fdLoadFile(modelPathStr + "/head.tnnmodel");
    LOGI("head_dectect proto content size %d model content size %d", headProtoContent.length(), headModelContent.length());
    auto headOption = std::make_shared<TNN_NS::HeadDetectOption>();
    headOption->compute_units = TNN_NS::TNNComputeUnitsHuaweiNPU;
    headOption->library_path = "";
    headOption->proto_content = headProtoContent;
    headOption->model_content = headModelContent;
    headOption->input_height = MODE_INPUT_SIZE2;
    headOption->input_width = MODE_INPUT_SIZE2;
    headOption->num_thread = threadCnt;
    headOption->mode = 1;
    if (gComputeUnitType == 1) {
        headOption->compute_units = TNN_NS::TNNComputeUnitsGPU;
        status = headDetector->Init(headOption);
    } else if (gComputeUnitType == 2) {
        //add for huawei_npu store the om file
        headOption->compute_units = TNN_NS::TNNComputeUnitsHuaweiNPU;
        headDetector->setNpuModelPath(modelPathStr + "/");
        headDetector->setCheckNpuSwitch(false);
        status = headDetector->Init(headOption);
    } else {
        headOption->compute_units = TNN_NS::TNNComputeUnitsCPU;
        status = headDetector->Init(headOption);
    }
    if (status != TNN_NS::TNN_OK) {
        LOGE("head detector init failed %d", (int)status);
        return -1;
    }
    // -----------------------------------------------------------------
    return 0;
}

JNIEXPORT JNICALL jboolean TNN_BODY_SEGMENT(checkNpu)(JNIEnv *env, jobject thiz, jstring modelPath) {
    return false;


    TNN_NS::BodyDetect humanDetector;

    std::string modelPathStr(jstring2string(env, modelPath));

    std::string protoContent, modelContent;
    protoContent = fdLoadFile(modelPathStr + "/body.tnnproto");
    modelContent = fdLoadFile(modelPathStr + "/body.tnnmodel");
    auto option = std::make_shared<TNN_NS::BodyDetectOption>();
    option->compute_units = TNN_NS::TNNComputeUnitsHuaweiNPU;
    option->library_path = "";
    option->proto_content = protoContent;
    option->model_content = modelContent;
    option->input_height = MODE_INPUT_SIZE1;
    option->input_width = MODE_INPUT_SIZE1;
    humanDetector.setNpuModelPath(modelPathStr + "/");
    humanDetector.setCheckNpuSwitch(true);
    TNN_NS::Status ret = humanDetector.Init(option);

    if(ret != TNN_NS::TNN_OK){
        LOGE("TNN_BODY_SEGMENT checkNpu init body detect failure!");
    }


    // ----------------------------------------------------------------------
    TNN_NS::FaceDetect faceDetect;
    std::string faceProtoContent, faceModelContent;
    faceProtoContent = fdLoadFile(modelPathStr + "/face.tnnproto");
    faceModelContent = fdLoadFile(modelPathStr + "/face.tnnmodel");
    auto faceOption = std::make_shared<TNN_NS::FaceDetectOption>();
    faceOption->compute_units = TNN_NS::TNNComputeUnitsHuaweiNPU;
    faceOption->library_path = "";
    faceOption->proto_content = faceProtoContent;
    faceOption->model_content = faceModelContent;
    faceOption->input_height = MODE_INPUT_SIZE2;
    faceOption->input_width = MODE_INPUT_SIZE2;
    faceDetect.setNpuModelPath(modelPathStr + "/");
    faceDetect.setCheckNpuSwitch(true);
    ret = faceDetect.Init(faceOption);

    if(ret != TNN_NS::TNN_OK){
        LOGE("TNN_face_SEGMENT checkNpu init face detect failure!");
    }

    // ----------------------------------------------------------------------
    TNN_NS::FaceDetect headDetect;
    std::string headProtoContent, headModelContent;
    headProtoContent = fdLoadFile(modelPathStr + "/head.tnnproto");
    headModelContent = fdLoadFile(modelPathStr + "/head.tnnmodel");
    auto headOption = std::make_shared<TNN_NS::HeadDetectOption>();
    headOption->compute_units = TNN_NS::TNNComputeUnitsHuaweiNPU;
    headOption->library_path = "";
    headOption->proto_content = headProtoContent;
    headOption->model_content = headModelContent;
    headOption->input_height = MODE_INPUT_SIZE2;
    headOption->input_width = MODE_INPUT_SIZE2;
    headDetect.setNpuModelPath(modelPathStr + "/");
    headDetect.setCheckNpuSwitch(true);
    ret = headDetect.Init(headOption);
    if(ret != TNN_NS::TNN_OK){
        LOGE("TNN_face_SEGMENT checkNpu init head detect failure!");
    }
    return ret == TNN_NS::TNN_OK;
}

JNIEXPORT JNICALL jint TNN_BODY_SEGMENT(deinit)(JNIEnv *env, jobject thiz) {
    LOGE("TNN_BODY_SEGMENT deinit");
    humanDetector = nullptr;
    bodyDetector = nullptr;
    headDetector = nullptr;
    faceDetector = nullptr;
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
    if(rgbData!=NULL){
        free(rgbData);
        rgbData = NULL;
    }
    if(faceData!=NULL){
        free(faceData);
        faceData = NULL;
    }
    return 0;
}

JNIEXPORT JNICALL jint TNN_BODY_SEGMENT(setOFD)(JNIEnv *env, jobject thiz, jboolean ofd) {
    auto asyncRefBodyDector = bodyDetector;
    asyncRefBodyDector->setOFDStatus(ofd);
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
        if (rgbData != NULL) {
            free(rgbData);
            rgbData = NULL;
            bgrDataLen = 0;
        }
    }
    if(rgbData==NULL) {
        bgrDataLen = height * width * 3;
        rgbData = (unsigned char*)malloc(sizeof(unsigned char) * bgrDataLen);
    }
    //unsigned char *bgrData = new unsigned char[height * width * 3];
    //yuv420sp_to_rgb_fast_asm((const unsigned char *) yuvData, width, height, (unsigned char *) rgbData);
    yuv420sp_to_bgr_fast_asm((const unsigned char *) yuvData, width, height, (unsigned char *) rgbData);

    TNN_NS::DeviceType dt = TNN_NS::DEVICE_ARM;
    TNN_NS::DimsVector target_dims = {1, 3, height, width}; // 转成3通道的
    auto rgbTNN = std::make_shared<TNN_NS::Mat>(dt, TNN_NS::N8UC3, target_dims, rgbData);
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
    if(detectHuman){
        auto asyncRefHumanDector = humanDetector;
        asyncRefHumanDector->srcInputWidth = width;
        asyncRefHumanDector->srcInputHeight = height;
        //asyncRefHumanDector->maskData = mask;
        std::string outputResult = "";
        std::shared_ptr<TNN_NS::TNNSDKInput> input = std::make_shared<TNN_NS::TNNSDKInput>(rgbTNN);
        std::shared_ptr<TNN_NS::TNNSDKOutput> output = asyncRefHumanDector->CreateSDKOutput();
        auto t1=std::chrono::steady_clock::now();
        TNN_NS::Status status = asyncRefHumanDector->Predict(input, output);
        if(IS_SHOW_LOG) {
            auto t2=std::chrono::steady_clock::now();
            double dr_s=std::chrono::duration<double>(t2-t1).count(); //秒
            //double dr_ms=std::chrono::duration<double,std::milli>(t2-t1).count();//毫秒级
            LOGE("Human Predict and ProcessSDKOutput coast:%fs", dr_s);
        }
        //asyncRefHumanDector->maskData = NULL;
        if (status == TNN_NS::TNN_OK) {
            if(humanDetector->cropWidth>0 && humanDetector->cropHeight>0) {
                isFound = true;
                cropX = humanDetector->cropX;
                cropY = humanDetector->cropY;
                cropWidth = humanDetector->cropWidth;
                cropHeight = humanDetector->cropHeight;
            }
        } else {
            LOGE("Human Detect Predict failed with:%s\n", status.description().c_str());
        }
    }else{
        isFound = true;
    }

    if(isFound && detectBody) {
        auto asyncRefBodyDector = bodyDetector;
        asyncRefBodyDector->humRectLeft = cropX;
        asyncRefBodyDector->humRectTop = cropY;
        asyncRefBodyDector->humRectWidth = cropWidth;
        asyncRefBodyDector->humRectHeight = cropHeight;
        asyncRefBodyDector->maskData = mask;
        std::string outputResult = "";
        std::shared_ptr<TNN_NS::TNNSDKInput> input = std::make_shared<TNN_NS::TNNSDKInput>(rgbTNN);
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
    }

//    detectHead=false;
    if(detectHead){
//        if(bgrData==NULL) {
//            bgrDataLen = height * width * 3;
//            bgrData = (unsigned char*)malloc(sizeof(unsigned char) * bgrDataLen);
//        }
//        bgr_to_rgb_fast_asm(rgbData, width, height ,bgrData);
//        //yuv420sp_to_bgr_fast_asm((const unsigned char *) yuvData, width, height, (unsigned char *) bgrData);
//        TNN_NS::DeviceType dt = TNN_NS::DEVICE_ARM;
//        TNN_NS::DimsVector target_dims = {1, 3, height, width}; // 转成3通道的
//        auto bgrTNN = std::make_shared<TNN_NS::Mat>(dt, TNN_NS::N8UC3, target_dims, bgrData);

        auto asyncRefFaceDector = faceDetector;
        std::shared_ptr<TNN_NS::TNNSDKInput> input = std::make_shared<TNN_NS::TNNSDKInput>(rgbTNN);
        std::shared_ptr<TNN_NS::TNNSDKOutput> output = asyncRefFaceDector->CreateSDKOutput();
        auto t1=std::chrono::steady_clock::now();
        TNN_NS::Status status = asyncRefFaceDector->Predict(input, output);
        if(IS_SHOW_LOG) {
            auto t2 = std::chrono::steady_clock::now();
            double dr_s = std::chrono::duration<double>(t2 - t1).count(); //秒
            //double dr_ms=std::chrono::duration<double,std::milli>(t2-t1).count();//毫秒级
            LOGE("Face Predict and ProcessSDKOutput coast:%fs", dr_s);
        }
        if (status == TNN_NS::TNN_OK) {
            std::vector<tnn::FaceInfo> faceList = asyncRefFaceDector->faceList;
            if(faceList.size() > 0) {
                if(IS_SHOW_LOG) {
                    LOGE("Found face count:%d",faceList.size());
                }
                auto asyncRefHeadDector = headDetector;
                //int HEAD_INPUT_SIZE = 256;
                int modelInputWidth = asyncRefHeadDector->modelInputWidth;
                int modelInputHeight = asyncRefHeadDector->modelInputHeight;
                auto t1 = std::chrono::steady_clock::now();
                int faceNum = faceList.size();
                //u_char * faceData = new u_char[HEAD_INPUT_BATCH*modelInputWidth*modelInputHeight*3];
                if(HEAD_INPUT_BATCH > 1) { // HEAD_INPUT_BATCH==1个的时候才会使用
                    if(faceData==NULL){
                        faceData = (u_char *)malloc(sizeof(u_char)*HEAD_INPUT_BATCH*modelInputWidth*modelInputHeight*3);
                    }
                    memset(faceData, 0, sizeof(u_char) * HEAD_INPUT_BATCH * modelInputWidth * modelInputHeight * 3);
                }
                int totalProc = 0;
                std::vector<tnn::FaceInfo> procFaceList;
                void *command_queue = nullptr;
                status = asyncRefFaceDector->GetCommandQueue(&command_queue);
                if (status == TNN_NS::TNN_OK) {
                    if(HEAD_INPUT_BATCH==1){
                        asyncRefHeadDector->maskData = mask;
                        asyncRefHeadDector->srcInputWidth = width;
                        asyncRefHeadDector->srcInputHeight = height;
                        asyncRefHeadDector->isDetectedBody = isFound;

                        for (int i = 0; i < faceNum; ++i) {
                            //将所有数据放到一个batch里
                            tnn::FaceInfo faceInfo = faceList.at(i);

                            TNN_NS::DimsVector crop_dims = {1, 3, faceInfo.h, faceInfo.w};
                            tnn::CropParam param;
                            param.top_left_x = faceInfo.l;
                            param.top_left_y = faceInfo.t;
                            param.width = faceInfo.w;
                            param.height = faceInfo.h;
                            // LOGE("l:%d, t:%d, w:%d, h:%d", faceInfo.l, faceInfo.t, faceInfo.w, faceInfo.h);

                            auto crop_mat = std::make_shared<TNN_NS::Mat>(rgbTNN->GetDeviceType(),
                                                                          rgbTNN->GetMatType(),
                                                                          crop_dims);
                            status = tnn::MatUtils::Crop(*(rgbTNN.get()), *(crop_mat.get()), param,
                                                         command_queue);
                            if (status != TNN_NS::TNN_OK) {
                                LOGE("%d crop failed with:%s, x:%d y:%d w:%d h:%d\n", i,
                                     status.description().c_str(), faceInfo.l, faceInfo.t,
                                     faceInfo.w, faceInfo.h);
                                continue;
                            }

                            tnn::ResizeParam scaleParam;
                            scaleParam.type = tnn::INTERP_TYPE_LINEAR;
                            scaleParam.scale_w = modelInputWidth / static_cast<float>(faceInfo.w);
                            scaleParam.scale_h = modelInputHeight / static_cast<float>(faceInfo.h);
                            TNN_NS::DimsVector resize_dims = {1, 3, modelInputHeight,
                                                              modelInputWidth}; // 转成3通道的
                            auto target_mat = std::make_shared<TNN_NS::Mat>(rgbTNN->GetDeviceType(),
                                                                            rgbTNN->GetMatType(),
                                                                            resize_dims);
                            status = tnn::MatUtils::Resize(*(crop_mat.get()), *(target_mat.get()),
                                                           scaleParam, command_queue);
                            if (status != TNN_NS::TNN_OK) {
                                LOGE("%d resize failed with:%s, x:%d y:%d w:%d h:%d\n", i,
                                     status.description().c_str(), faceInfo.l, faceInfo.t,
                                     faceInfo.w, faceInfo.h);
                                continue;
                            }
                            //procFaceList.push_back(faceInfo);
                            asyncRefHeadDector->faceList.clear();
                            asyncRefHeadDector->faceList.push_back(faceInfo);


                            std::shared_ptr<TNN_NS::TNNSDKInput> headInput = std::make_shared<TNN_NS::TNNSDKInput>(target_mat);
                            std::shared_ptr<TNN_NS::TNNSDKOutput> headOutput = asyncRefHeadDector->CreateSDKOutput();

                            TNN_NS::Status status = asyncRefHeadDector->Predict(headInput,headOutput);
                            if (status == TNN_NS::TNN_OK) {
                                asyncRefHeadDector->isDetectedBody = true; // 设置一下，否则只能出来一张人脸
                                isFound = true;
                            } else {
                                LOGE("Head Detect %d Predict failed with:%s\n", i, status.description().c_str());
                            }
                        }
                        if(IS_SHOW_LOG) {
                            auto t2 = std::chrono::steady_clock::now();
                            double dr_s = std::chrono::duration<double>(t2 - t1).count(); //秒
                            //double dr_ms=std::chrono::duration<double,std::milli>(t2-t1).count();//毫秒级
                            LOGE("Head Predict and ProcessSDKOutput coast:%fs", dr_s);
                        }
                        asyncRefHeadDector->maskData = NULL;
                    }else {
                        for (int i = 0; i < faceNum; ++i) {
                            //将所有数据放到一个batch里
                            tnn::FaceInfo faceInfo = faceList.at(i);

                            TNN_NS::DimsVector crop_dims = {1, 3, faceInfo.h, faceInfo.w};
                            tnn::CropParam param;
                            param.top_left_x = faceInfo.l;
                            param.top_left_y = faceInfo.t;
                            param.width = faceInfo.w;
                            param.height = faceInfo.h;
                            auto crop_mat = std::make_shared<TNN_NS::Mat>(rgbTNN->GetDeviceType(),
                                                                          rgbTNN->GetMatType(),
                                                                          crop_dims);
                            status = tnn::MatUtils::Crop(*(rgbTNN.get()), *(crop_mat.get()), param,
                                                         command_queue);
                            if (status != TNN_NS::TNN_OK) {
                                LOGE("%d crop failed with:%s, x:%d y:%d w:%d h:%d\n", i,
                                     status.description().c_str(), faceInfo.l, faceInfo.t,
                                     faceInfo.w, faceInfo.h);
                                continue;
                            }

                            tnn::ResizeParam scaleParam;
                            scaleParam.type = tnn::INTERP_TYPE_LINEAR;
                            scaleParam.scale_w = modelInputWidth / static_cast<float>(faceInfo.w);
                            scaleParam.scale_h = modelInputHeight / static_cast<float>(faceInfo.h);
                            TNN_NS::DimsVector resize_dims = {1, 3, modelInputHeight,
                                                              modelInputWidth}; // 转成3通道的
                            auto target_mat = std::make_shared<TNN_NS::Mat>(rgbTNN->GetDeviceType(),
                                                                            rgbTNN->GetMatType(),
                                                                            resize_dims);
                            status = tnn::MatUtils::Resize(*(crop_mat.get()), *(target_mat.get()),
                                                           scaleParam, command_queue);
                            if (status != TNN_NS::TNN_OK) {
                                LOGE("%d resize failed with:%s, x:%d y:%d w:%d h:%d\n", i,
                                     status.description().c_str(), faceInfo.l, faceInfo.t,
                                     faceInfo.w, faceInfo.h);
                                continue;
                            }
                            procFaceList.push_back(faceInfo);
                            memcpy(faceData + (procFaceList.size() - 1) * 3 * modelInputWidth *
                                                      modelInputHeight,
                                   (u_char *) (target_mat.get()->GetData()),
                                   3 * modelInputWidth * modelInputHeight);
                        }
                        if(IS_SHOW_LOG) {
                            auto t3 = std::chrono::steady_clock::now();
                            double dr_s3 = std::chrono::duration<double>(t3 - t1).count(); //秒
                            //double dr_ms=std::chrono::duration<double,std::milli>(t2-t1).count();//毫秒级
                            LOGE("Head PreProcess coast:%fs procFaceList size:%d", dr_s3,
                                 procFaceList.size());
                            //LOGE("procFaceList size:%d", procFaceList.size());
                        }

                        if (procFaceList.size() > 0) {
                            int cnt = procFaceList.size();
                            TNN_NS::DimsVector head_dims = {HEAD_INPUT_BATCH, 3, modelInputHeight,
                                                            modelInputWidth}; // 转成3通道的, 固定3个batch,202012目前tnn只支持固定batch的处理
                            auto headBgrTNN = std::make_shared<TNN_NS::Mat>(dt, TNN_NS::N8UC3,
                                                                            head_dims, faceData);

                            asyncRefHeadDector->faceList = procFaceList;
                            asyncRefHeadDector->maskData = mask;
                            asyncRefHeadDector->srcInputWidth = width;
                            asyncRefHeadDector->srcInputHeight = height;
                            asyncRefHeadDector->isDetectedBody = isFound;
                            std::shared_ptr<TNN_NS::TNNSDKInput> headInput = std::make_shared<TNN_NS::TNNSDKInput>(
                                    headBgrTNN);
                            std::shared_ptr<TNN_NS::TNNSDKOutput> headOutput = asyncRefHeadDector->CreateSDKOutput();

                            TNN_NS::Status status = asyncRefHeadDector->Predict(headInput,
                                                                                headOutput);
                            auto t2 = std::chrono::steady_clock::now();
                            double dr_s = std::chrono::duration<double>(t2 - t1).count(); //秒
                            //double dr_ms=std::chrono::duration<double,std::milli>(t2-t1).count();//毫秒级
                            LOGE("Head Predict and ProcessSDKOutput coast:%fs", dr_s);
                            asyncRefHeadDector->maskData = NULL;
                            if (status == TNN_NS::TNN_OK) {
                                isFound = true;
                            } else {
                                LOGE("Head Detect Predict failed with:%s\n", status.description().c_str());
                            }
                        } else {
                            LOGE("asyncRefFaceDector Proc Face Size is 0 !");
                        }
                    }
                }else{
                    LOGE("asyncRefFaceDector getCommandQueue failed with:%s\n", status.description().c_str());
                }
            }else{
                LOGE("asyncRefFaceDector Face Size is 0 !");
            }
        } else {
            LOGE("Face Detect Predict failed with:%s\n", status.description().c_str());
        }
    }else{
        if(faceData!=NULL){
            free(faceData);
            faceData = NULL;
        }
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

