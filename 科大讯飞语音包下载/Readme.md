## 科大讯飞语音包更新
*记录时间：2018年4月5日 20:17:20*

### 1.前言
在进行HSMap的第二部分功能开发时，需要用到Bluetooth功能，将得到的5个点的经纬度信息发送到Smart Watch上，于是在Bluetooth部分代码考虑借鉴以前开发过的“驾驶员疲劳检测项目”中，APP的蓝牙发送接收功能。考虑到在Eclipse中尽心Android开发会出现很多奇怪的问题，于是将Client和Server端的代码转化到AS端，在转化之后，需要对测试APP的功能进行测试。在测试中Bluetooth发送数据功能正常，但是代码执行到语音播报功能的时候会出现崩溃，于是重新更新了AS端的Client的语音播报功能的SDK包，并将相关代码对应官方的API文档重写了一遍。

### 2.参考资料网址：

1. 科大讯飞资料库 [http://www.xfyun.cn/doccenter/awd](http://www.xfyun.cn/doccenter/awd "科大讯飞资料库")

2. 科大讯飞应用申请--控制台 [http://console.xfyun.cn/app/myapp?currPage=1&keyword=](http://console.xfyun.cn/app/myapp?currPage=1&keyword= "科大讯飞应用申请--控制台")

3. 科大讯飞API [http://mscdoc.xfyun.cn/android/api/](http://mscdoc.xfyun.cn/android/api/ "科大讯飞API")

### 3.官方SDK包引入工程

SDK中主要有三个文件夹libs、assets、res需要导入到项目中。
    
    .:
    │  readme.txt
    │  release.txt
    │  wordlist.txt
    │  
    ├─assets
    │  └─iflytek
    │  recognize.xml
    │  voice_bg.9.png
    │  voice_empty.png
    │  voice_full.png
    │  waiting.png
    │  warning.png
    │  
    ├─libs
    │  │  Msc.jar
    │  │  Sunflower.jar
    │  │  
    │  ├─arm64-v8a
    │  │  libmsc.so
    │  │  
    │  ├─armeabi
    │  │  libmsc.so
    │  │  
    │  ├─armeabi-v7a
    │  │  libmsc.so
    │  │  
    │  ├─mips
    │  │  libmsc.so
    │  │  
    │  ├─mips64
    │  │  libmsc.so
    │  │  
    │  ├─x86
    │  │  libmsc.so
    │  │  
    │  └─x86_64
    │  libmsc.so
    │  
    ├─res
    │  ├─asr
    │  ├─ivw
    │  │  5741698c.jet
    │  │  
    │  └─tts
    │  common.jet
    │  xiaofeng.jet
    │  xiaoyan.jet
    │  
    └─sample（可暂时忽略）

1. libs文件夹中*.jar文件复制到..\TireDectetionBTClient\app\libs中
2. libs文件夹中对应硬件型号的*.so动态库文件复制到C:\Users\pengpeng\AndroidStudioProjects\TireDectetionBTClient\app\src\main\jniLibs中
3. assets文件夹直接拷贝到C:\Users\pengpeng\AndroidStudioProjects\TireDectetionBTClient\app\src\main中
4. res文件夹中的所有文件直接复制到C:\Users\pengpeng\AndroidStudioProjects\TireDectetionBTClient\app\src\main\assets中

