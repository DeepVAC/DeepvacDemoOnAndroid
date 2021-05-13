### 简介
android版本音频情绪识别demo(包含aar的生成)</br>

### 建议使用的NDK版本

Version 20.0.5594570

### 下载ncnn和yuv库

1. https://github.com/DeepVAC/DeepvacDemoOnAndroid/releases</br>

### 准备
1. 将essentia库 audio_cls/src/main/jniLibs的对应文件夹下面
2. gradle - scene_cls - scls_lib - Tasks - other - assembleRelase, aar包生成在 audio_cls/build/output/aar下面 </br>
3. 将aar拷贝到app/libs下面</br>