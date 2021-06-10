### 简介
android版本配饰分割demo</br>

### 建议使用的NDK版本

Version r21e


###  如何更换模型，生成APK

1. 用android studio打开clothes_seg_android项目
2. 将配饰分割tnn模型放入到 DeepvacDemoOnAndroid/clothes_seg_android/AccessorySegment/src/main/assets/ 下面
3. 打开 DeepvacDemoOnAndroid/clothes_seg_android/AccessorySegment/src/main/java/com/deepait/accessorysegment/AccessorySegmentFragment.java，修改initModel()函数中以modelPathsDetector作为变量名的字符串数组，用最新的tnn模型名称替换掉里面的2行。
4. 如果要直接在手机上调试，请先开启手机上的开发者模式，然后用数据线连接至电脑，点击android studio中右上方的绿色箭头即可。
5. 如果要打包成apk，需要先取消AccessorySegmentFragment.java中第246，247行注释，作用是隐藏调试按钮。如何没有密钥则首先需要创建密钥（参考 https://www.jianshu.com/p/aae17a5e9e59） 。然后点击android studio 菜单栏的 Build-Generate Signed Bundle or APK- 选择apk - next - 填入密钥地址等信息 - next - 选择release，v1和v2打钩 - finish. Apk会生成到 DeepvacDemoOnAndroid/clothes_seg_android/AccessorySegment/release/ 下。


