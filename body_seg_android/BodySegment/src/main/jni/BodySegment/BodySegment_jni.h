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

#ifndef ANDROID_HUMMANKEYPOINT_JNI_H_
#define ANDROID_HUMMANKEYPOINT_JNI_H_

#include <jni.h>
#define TNN_BODY_SEGMENT(sig) Java_com_deepait_bodysegment_BodySegment_##sig
#ifdef __cplusplus
extern "C" {
#endif
JNIEXPORT JNICALL jint TNN_BODY_SEGMENT(init)(JNIEnv *env, jobject thiz, jstring modelPath, jint computeUnitType);
JNIEXPORT JNICALL jint TNN_BODY_SEGMENT(deinit)(JNIEnv *env, jobject thiz);
JNIEXPORT JNICALL jboolean TNN_BODY_SEGMENT(checkNpu)(JNIEnv *env, jobject thiz, jstring modelPath);
JNIEXPORT JNICALL jboolean TNN_BODY_SEGMENT(predictFromStream)(JNIEnv *env, jobject thiz, jbyteArray yuv420sp, jint width, jint height, jint rotate, jboolean detectBody, jboolean detectHead, jintArray outputData);
JNIEXPORT void JNICALL TNN_BODY_SEGMENT(YUVtoARBG)(JNIEnv * env, jclass obj, jbyteArray yuv420sp, jint width, jint height, jintArray argbOut);
JNIEXPORT JNICALL jint TNN_BODY_SEGMENT(setOFD)(JNIEnv *env, jobject thiz, jboolean ofd);
#ifdef __cplusplus
}
#endif

#endif // ANDROID_HUMMANKEYPOINT_JNI_H_
