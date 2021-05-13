### 简介
android版本场景分类demo(包含aar的生成)</br>

### 建议使用的NDK版本

Version 20.0.5594570

### 下载ncnn和yuv库

1. https://github.com/DeepVAC/DeepvacDemoOnAndroid/releases</br>

### 准备
1. 将ncnn库和yuv库放到 scls_lib/src/main/jniLibs的对应文件夹下面
2. 将镜头分割的aar拷贝到app/libs下面和scls_lib/libs下面（注：镜头分割aar生成方法见video_clip文件夹下的README.md）
3. gradle - scene_cls - scls_lib - Tasks - other - assembleRelase, aar包生成在 scls/build/output/aar下面 </br>
4. 将aar拷贝到app/libs下面</br>