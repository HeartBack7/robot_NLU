# RobotSample 开发者说明文档

## 1. 文档目的

这份文档面向 Android 开发新手，帮助你快速看懂当前项目的结构，并知道:

- 项目是怎么启动的
- 每个目录大概负责什么
- 如果你想新增一个功能，通常该改哪些文件
- 如果你想删除一个功能，通常该删哪些文件
- 如果你想修改现有功能，应该从哪里下手

这个项目本质上是一个机器人 SDK 示例工程，采用的是:

- 单模块工程: 只有 `app`
- 单 Activity 架构: 只有 `MainActivity`
- 多 Fragment 页面切换: 每个功能基本都是一个 `Fragment`

所以你可以先记住一句最重要的话:

**以后改功能，大多数时候都在 `app/src/main/java/com/ainirobot/robotos/fragment/` 和 `app/src/main/res/layout/` 这两个地方动手。**

---

## 2. 当前项目结构

下面是当前目录下真正值得关注的结构。像 `build/`、`.idea/` 这类构建产物或 IDE 文件，不建议手改。

```text
RobotSample-11.3/
├── app/
│   ├── build.gradle
│   ├── proguard-rules.pro
│   ├── libs/
│   │   └── robotservice.jar
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/ainirobot/robotos/
│       │   │   ├── MainActivity.java
│       │   │   ├── LogTools.java
│       │   │   ├── application/
│       │   │   ├── audio/
│       │   │   ├── fragment/
│       │   │   ├── maputils/
│       │   │   └── view/
│       │   └── res/
│       │       ├── layout/
│       │       ├── drawable/
│       │       ├── values/
│       │       └── mipmap-*/
│       ├── test/
│       └── androidTest/
├── gradle/
│   └── wrapper/
├── build.gradle
├── settings.gradle
├── gradlew
├── gradlew.bat
├── README.md
├── PROJECT_ANALYSIS.md
└── DEVELOPER_GUIDE.md
```

---

## 3. 先理解这个项目的核心设计

如果你以前做过普通 Android App，可能会先找很多 `Activity`。但这个项目不是那种结构。

它的页面组织方式是:

1. `AndroidManifest.xml` 指定入口为 `MainActivity`
2. `MainActivity` 里只有一个页面容器 `container_content`
3. 真正的每个业务功能页面都写成一个 `Fragment`
4. 点击主菜单按钮后，通过 `switchFragment(...)` 切换到不同功能页

也就是说:

- `MainActivity` 更像一个“页面容器”
- `MainFragment` 更像“功能菜单首页”
- 各种 `XXXFragment` 才是真正的功能实现页面

这会直接影响你以后改代码的方式:

- 改主菜单: 看 `MainFragment.java` 和 `fragment_main_layout.xml`
- 改具体功能: 看对应的 `XXXFragment.java` 和 `fragment_xxx_layout.xml`
- 改应用启动或 SDK 初始化: 看 `RobotOSApplication.java`、`MainActivity.java`、`AndroidManifest.xml`

---

## 4. 重要目录说明

## 4.1 根目录

### `build.gradle`

项目级 Gradle 配置。这里主要是:

- 配置仓库源
- 配置 Android Gradle Plugin

你通常不会在这里写业务逻辑。

### `settings.gradle`

当前内容只有:

- `include ':app'`

说明这是一个**单模块项目**。

### `README.md`

项目原始说明，内容比较简略，适合作为快速索引。

### `PROJECT_ANALYSIS.md`

现有的项目分析文档，偏项目解析。

---

## 4.2 `app/`

这是最重要的模块，几乎所有开发工作都在这里完成。

### `app/build.gradle`

模块级构建配置。这里负责:

- `compileSdkVersion`
- `minSdkVersion`
- `targetSdkVersion`
- 第三方依赖
- 本地 JAR 依赖

当前你需要知道的重点:

- 包名是 `com.ainirobot.robotos`
- 依赖了 `libs/robotservice.jar`
- 依赖了 AndroidX 和 Gson

如果以后你要:

- 新增第三方库
- 调整 SDK 版本
- 修改应用包名相关配置

就先看这个文件。

---

## 4.3 `app/src/main/AndroidManifest.xml`

这是 Android 清单文件，作用非常关键。

当前它负责:

- 声明应用入口 `MainActivity`
- 指定 `Application` 类为 `RobotOSApplication`
- 声明权限
- 声明启动方式和 Scheme

你应该重点记住两个点:

### 1. 应用入口

```xml
android:name=".application.RobotOSApplication"
```

表示应用启动时，会先进入 `RobotOSApplication` 做全局初始化。

### 2. 页面入口

```xml
<activity android:name=".MainActivity" ...>
```

表示 App 启动后进入 `MainActivity`。

### 什么情况下要改这个文件

如果你要做下面这些事，通常要改 `AndroidManifest.xml`:

- 新增权限，比如网络、录音、蓝牙、存储等
- 新增 `Activity`
- 修改应用的启动方式
- 增加新的 `intent-filter`
- 修改 `Application` 配置

### 什么情况下不用改

如果你只是:

- 新增一个 Fragment 页面
- 删除一个 Fragment 页面
- 修改某个功能页 UI

通常都**不用改**这个文件。

---

## 4.4 `app/src/main/java/com/ainirobot/robotos/`

这是 Java 源码主目录。

里面最重要的是这几个部分。

### `MainActivity.java`

这是应用主 Activity，也是整个项目的页面容器。

它主要负责:

- 启动后显示 Splash
- 检查机器人 API 是否连接成功
- 决定进入 `MainFragment` 还是 `FailedFragment`
- 承担所有 Fragment 的切换

你可以把它理解成:

**“整个 App 的舞台”**

所有功能页都在这个舞台上切换显示。

### 什么时候改 `MainActivity.java`

通常在这些情况下会改:

- 修改启动流程
- 修改 Splash 逻辑
- 修改初始化检查逻辑
- 修改 Fragment 切换逻辑
- 修改全局页面容器行为

如果你只是改某个业务功能，一般不用先动它。

---

## 4.5 `application/`

路径:

- `app/src/main/java/com/ainirobot/robotos/application/`

这个包负责应用级初始化和机器人服务连接。

包含:

- `RobotOSApplication.java`
- `ModuleCallback.java`
- `SpeechCallback.java`

### `RobotOSApplication.java`

这是全局 Application 入口。

它主要负责:

- 应用启动时初始化
- 连接 `RobotApi`
- 设置模块回调
- 初始化 `SkillApi`
- 注册语音相关回调

如果你以后遇到这种需求，先看这个文件:

- App 启动时就要初始化某个 SDK
- 要接入机器人系统服务
- 要改全局回调注册逻辑
- 要处理应用级资源初始化

### `ModuleCallback.java`

用于处理 CoreService 层的模块回调。

如果某个机器人模块能力是通过系统回调返回的，你可能需要看它。

### `SpeechCallback.java`

用于处理语音相关回调。

如果你要改:

- 语音识别结果处理
- 语音技能回调
- NLU/语音事件联动

要优先检查这个文件。

---

## 4.6 `fragment/`

路径:

- `app/src/main/java/com/ainirobot/robotos/fragment/`

这是本项目最核心的业务目录。

这里几乎每个文件都代表一个功能页面。

当前主要文件有:

- `BaseFragment.java`
- `MainFragment.java`
- `FailedFragment.java`
- `SportFragment.java`
- `NavigationFragment.java`
- `LocationFragment.java`
- `ChargeFragment.java`
- `SpeechFragment.java`
- `VisionFragment.java`
- `LeadFragment.java`
- `AudioFragment.java`
- `ElectricDoorControlFragment.java`
- `ElectricDoorActionControlFragment.java`
- `BodyFollowFragment.java`
- `NluTestFragment.java`
- `NavFragment.java`

### `BaseFragment.java`

这是所有功能页最重要的基类。

它做了三件事:

1. 加载公共骨架布局 `fragment_basic_layout.xml`
2. 自动放入返回区域 `BackView`
3. 自动放入结果展示区域 `ResultView`
4. 再把子类真正的业务布局塞进内容区域

这意味着:

**你新增功能页时，最推荐的做法是继承 `BaseFragment`。**

这样你就不用自己重复写:

- 返回按钮
- 结果显示区域
- 公共容器布局

### `MainFragment.java`

这是功能菜单首页。

它负责:

- 绑定首页上的每个按钮
- 点击后跳转到对应功能 Fragment
- 控制哪些功能入口显示在主菜单上

如果你要:

- 新增一个功能入口按钮
- 删除一个功能入口按钮
- 修改首页按钮点击行为

你几乎一定要改这个文件。

### 其他 `XXXFragment.java`

每个 `Fragment` 基本对应一个实际功能。

例如:

- `SportFragment.java`: 运动控制
- `NavigationFragment.java`: 导航
- `LocationFragment.java`: 定位与点位
- `ChargeFragment.java`: 充电
- `SpeechFragment.java`: 语音
- `VisionFragment.java`: 视觉
- `AudioFragment.java`: 音频
- `BodyFollowFragment.java`: 人体跟随
- `NluTestFragment.java`: NLU 测试

所以一个非常实用的定位思路是:

**你要改哪个功能，就先找同名的 `XXXFragment.java`。**

---

## 4.7 `view/`

路径:

- `app/src/main/java/com/ainirobot/robotos/view/`

这个目录放的是可复用自定义控件。

包含:

- `BackView.java`
- `ResultView.java`
- `MapView.java`

### `BackView.java`

负责返回主菜单。

点击后会直接切回:

- `MainFragment.newInstance()`

所以如果你以后想改“返回按钮的行为”，应该看这里。

### `ResultView.java`

用于显示功能执行结果或日志输出。

如果你感觉某个功能页里“结果展示方式”要统一调整，可以看这个文件。

### `MapView.java`

地图相关展示控件。

导航、定位、地图点位等功能可能会依赖它。

---

## 4.8 `maputils/`

路径:

- `app/src/main/java/com/ainirobot/robotos/maputils/`

这里是工具类和数据类目录，主要偏地图、点位、弹窗、常量、JSON 处理。

常见文件包括:

- `SpecialPlaceUtil.java`
- `RoverMap.java`
- `MapInfo.java`
- `PoseBean.java`
- `Pose2d.java`
- `MapppUtils.java`
- `GlobalData.java`
- `Constant.java`
- `DialogUtils.java`
- `GsonUtil.java`

如果你改的是:

- 地图点位数据处理
- 导航点名称或特殊点位判断
- 坐标、姿态、地图结构
- 公共常量

通常会在这里改。

如果你只是改按钮文案或页面布局，一般不用先看它。

---

## 4.9 `audio/`

路径:

- `app/src/main/java/com/ainirobot/robotos/audio/`

这里是音频相关辅助类。

如果你要扩展:

- 音频采集
- 音频头处理
- 音频播放或格式适配

可以从这里开始看。

---

## 4.10 `res/`

路径:

- `app/src/main/res/`

这是资源目录。

里面最常改的是:

### `layout/`

保存页面布局 XML。

当前很重要的布局有:

- `activity_main.xml`: `MainActivity` 的容器布局
- `splash_layout.xml`: 启动页
- `fragment_main_layout.xml`: 主菜单页面
- `fragment_basic_layout.xml`: 所有业务 Fragment 的公共壳布局
- `fragment_sport_layout.xml`
- `fragment_navigation_layout.xml`
- `fragment_location_layout.xml`
- `fragment_charge_layout.xml`
- `fragment_speech_layout.xml`
- `fragment_vision_layout.xml`
- `fragment_audio_layout.xml`
- `fragment_body_follow_layout.xml`
- `fragment_nlu_test_layout.xml`
- `fragment_failed_layout.xml`
- `fragment_electric_door_action_control_layout.xml`
- `fragment_electric_door_control_layout.xml`

### `values/`

保存公共资源定义:

- `strings.xml`: 文案
- `colors.xml`: 颜色
- `styles.xml`: 样式
- `special_place_arrays.xml`: 特殊点位相关数组

### `drawable/`

保存按钮背景、形状、图标背景等资源。

如果你要改页面外观，通常会改:

- `layout/*.xml`
- `values/strings.xml`
- `values/colors.xml`
- `drawable/*.xml`

---

## 5. 项目启动流程

理解启动流程后，你会更容易知道“改哪里才不会改偏”。

### 第一步: 应用启动

系统先读取 `AndroidManifest.xml`，发现:

- `Application` 是 `RobotOSApplication`
- 启动页 `Activity` 是 `MainActivity`

### 第二步: 执行 `RobotOSApplication`

应用进程启动后，先执行 `RobotOSApplication.onCreate()`。

这里会:

- 初始化回调对象
- 创建回调线程
- 连接 `RobotApi`
- 初始化语音 `SkillApi`

也就是说，**机器人 SDK 的接入基础是在这里完成的**。

### 第三步: 进入 `MainActivity`

`MainActivity` 启动后:

- 默认先显示 `splash_layout.xml`
- 稍微延迟后再切换到 `activity_main.xml`
- 然后调用 `checkInit()`

### 第四步: 检查 SDK 是否可用

`checkInit()` 会判断:

- `RobotApi.getInstance().isApiConnectedService()`
- `RobotApi.getInstance().isActive()`

如果成功:

- 进入 `MainFragment`

如果失败:

- 进入 `FailedFragment`

### 第五步: 进入主菜单

`MainFragment` 是主菜单页，里面有很多按钮。

点击某个按钮后，会跳转到对应功能页，比如:

- `SportFragment`
- `NavigationFragment`
- `SpeechFragment`
- `NluTestFragment`

---

## 6. 你以后最常见的改动场景

下面是最实用的部分。

## 6.1 场景一: 新增一个功能

这是你以后最常做的事情。

假设你要新增一个“人脸打招呼”功能，推荐按下面步骤做。

### 第 1 步: 新建 Fragment

在下面目录新建一个页面类:

- `app/src/main/java/com/ainirobot/robotos/fragment/YourFeatureFragment.java`

建议:

- 继承 `BaseFragment`
- 参考现有 `SportFragment.java`、`SpeechFragment.java`、`NluTestFragment.java`

这样你就能直接复用:

- 返回按钮
- 结果显示区域
- 页面公共框架

### 第 2 步: 新建布局文件

在下面目录新建布局:

- `app/src/main/res/layout/fragment_your_feature_layout.xml`

这个布局就是你功能页的 UI。

通常你会在 `YourFeatureFragment.java` 里这样加载:

- `mInflater.inflate(R.layout.fragment_your_feature_layout, null, false)`

### 第 3 步: 在主菜单里增加入口

你需要改两个地方。

#### 位置 A: `fragment_main_layout.xml`

在:

- `app/src/main/res/layout/fragment_main_layout.xml`

里加一个按钮，例如:

- 新增 `Button`
- 给它一个新的 `id`
- 文案尽量放到 `strings.xml`

#### 位置 B: `MainFragment.java`

在:

- `app/src/main/java/com/ainirobot/robotos/fragment/MainFragment.java`

里:

- `findViewById(...)` 找到你刚加的按钮
- 给按钮设置 `setOnClickListener(...)`
- 点击时 `switchFragment(YourFeatureFragment.newInstance())`

### 第 4 步: 增加字符串资源

在:

- `app/src/main/res/values/strings.xml`

中增加:

- 按钮文字
- 提示文案
- 页面标题

不要把中文或英文提示直接硬编码在 Java 里，优先放在 `strings.xml`。

### 第 5 步: 如果需要新权限，再改 `AndroidManifest.xml`

只有当你的新功能需要额外权限时，才去改:

- `app/src/main/AndroidManifest.xml`

例如:

- 录音
- 存储
- 网络
- 蓝牙
- 前台服务

### 第 6 步: 如果依赖新库，再改 `app/build.gradle`

如果你的新功能需要:

- 新 SDK
- 新三方库
- 新本地 JAR

就改:

- `app/build.gradle`

### 新增功能时最常改的文件清单

- `app/src/main/java/com/ainirobot/robotos/fragment/YourFeatureFragment.java`
- `app/src/main/res/layout/fragment_your_feature_layout.xml`
- `app/src/main/java/com/ainirobot/robotos/fragment/MainFragment.java`
- `app/src/main/res/layout/fragment_main_layout.xml`
- `app/src/main/res/values/strings.xml`

可选:

- `app/src/main/AndroidManifest.xml`
- `app/build.gradle`

---

## 6.2 场景二: 删除一个功能

假设你要删除 `NluTestFragment` 功能。

你不要只删一个 Java 文件，否则很容易残留无效代码或资源。

正确做法通常是下面这样。

### 第 1 步: 删除菜单入口

先改:

- `app/src/main/res/layout/fragment_main_layout.xml`

把对应按钮删掉。

然后改:

- `app/src/main/java/com/ainirobot/robotos/fragment/MainFragment.java`

把:

- 按钮绑定
- 点击事件
- `switchFragment(NluTestFragment.newInstance())`

全部删掉。

### 第 2 步: 删除功能页代码

删除:

- `app/src/main/java/com/ainirobot/robotos/fragment/NluTestFragment.java`

### 第 3 步: 删除对应布局

删除:

- `app/src/main/res/layout/fragment_nlu_test_layout.xml`

### 第 4 步: 清理字符串资源

检查并清理:

- `app/src/main/res/values/strings.xml`

中与这个页面相关的文案，比如:

- `nlu_test`
- `nlu_input_hint`
- `nlu_query`
- `nlu_clear`

### 第 5 步: 全局搜索残留引用

删除功能后，最好全局搜索以下内容，确认没有残留:

- Fragment 类名
- 布局名
- 按钮 id
- 字符串资源名

例如:

- `NluTestFragment`
- `fragment_nlu_test_layout`
- `nlu_test`

### 删除功能时最常改的文件清单

- `app/src/main/java/com/ainirobot/robotos/fragment/MainFragment.java`
- `app/src/main/res/layout/fragment_main_layout.xml`
- `app/src/main/java/com/ainirobot/robotos/fragment/待删除功能页.java`
- `app/src/main/res/layout/对应布局.xml`
- `app/src/main/res/values/strings.xml`

---

## 6.3 场景三: 修改现有功能

这是你最频繁遇到的情况。

最简单的判断方式是:

### 如果你要改“页面行为”

先看对应的:

- `XXXFragment.java`

例如:

- 改导航逻辑: 看 `NavigationFragment.java`
- 改语音逻辑: 看 `SpeechFragment.java`
- 改人体跟随逻辑: 看 `BodyFollowFragment.java`

### 如果你要改“页面长相”

先看对应的:

- `fragment_xxx_layout.xml`

例如:

- 改导航页面 UI: 看 `fragment_navigation_layout.xml`
- 改充电页面 UI: 看 `fragment_charge_layout.xml`

### 如果你要改“主菜单上的入口”

要同时看:

- `app/src/main/java/com/ainirobot/robotos/fragment/MainFragment.java`
- `app/src/main/res/layout/fragment_main_layout.xml`

### 如果你要改“公共返回按钮”

看:

- `app/src/main/java/com/ainirobot/robotos/view/BackView.java`

### 如果你要改“公共结果显示区域”

看:

- `app/src/main/java/com/ainirobot/robotos/view/ResultView.java`

### 如果你要改“应用启动或 SDK 初始化”

看:

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/ainirobot/robotos/application/RobotOSApplication.java`
- `app/src/main/java/com/ainirobot/robotos/MainActivity.java`

### 如果你要改“地图、点位、导航数据处理”

看:

- `app/src/main/java/com/ainirobot/robotos/maputils/`

---

## 7. 具体功能与文件对应关系

为了让你以后查找更快，下面给你一个“功能找文件”的思路。

### 主菜单页

- Java: `app/src/main/java/com/ainirobot/robotos/fragment/MainFragment.java`
- 布局: `app/src/main/res/layout/fragment_main_layout.xml`

### 启动页 / 容器页

- Java: `app/src/main/java/com/ainirobot/robotos/MainActivity.java`
- 布局: `app/src/main/res/layout/activity_main.xml`
- 启动页布局: `app/src/main/res/layout/splash_layout.xml`

### 运动功能

- Java: `app/src/main/java/com/ainirobot/robotos/fragment/SportFragment.java`
- 布局: `app/src/main/res/layout/fragment_sport_layout.xml`

### 导航功能

- Java: `app/src/main/java/com/ainirobot/robotos/fragment/NavigationFragment.java`
- 布局: `app/src/main/res/layout/fragment_navigation_layout.xml`

### 定位功能

- Java: `app/src/main/java/com/ainirobot/robotos/fragment/LocationFragment.java`
- 布局: `app/src/main/res/layout/fragment_location_layout.xml`

### 充电功能

- Java: `app/src/main/java/com/ainirobot/robotos/fragment/ChargeFragment.java`
- 布局: `app/src/main/res/layout/fragment_charge_layout.xml`

### 语音功能

- Java: `app/src/main/java/com/ainirobot/robotos/fragment/SpeechFragment.java`
- 布局: `app/src/main/res/layout/fragment_speech_layout.xml`

### 视觉功能

- Java: `app/src/main/java/com/ainirobot/robotos/fragment/VisionFragment.java`
- 布局: `app/src/main/res/layout/fragment_vision_layout.xml`

### 音频功能

- Java: `app/src/main/java/com/ainirobot/robotos/fragment/AudioFragment.java`
- 布局: `app/src/main/res/layout/fragment_audio_layout.xml`

### 电动门功能

- Java: `app/src/main/java/com/ainirobot/robotos/fragment/ElectricDoorActionControlFragment.java`
- 布局: `app/src/main/res/layout/fragment_electric_door_action_control_layout.xml`

项目里还存在:

- `ElectricDoorControlFragment.java`
- `fragment_electric_door_control_layout.xml`

但主菜单当前绑定的是 `ElectricDoorActionControlFragment`。

### 人体跟随功能

- Java: `app/src/main/java/com/ainirobot/robotos/fragment/BodyFollowFragment.java`
- 布局: `app/src/main/res/layout/fragment_body_follow_layout.xml`

### NLU 测试功能

- Java: `app/src/main/java/com/ainirobot/robotos/fragment/NluTestFragment.java`
- 布局: `app/src/main/res/layout/fragment_nlu_test_layout.xml`

### 失败页

- Java: `app/src/main/java/com/ainirobot/robotos/fragment/FailedFragment.java`
- 布局: `app/src/main/res/layout/fragment_failed_layout.xml`

---

## 8. 新手最容易改错的地方

下面这些点非常值得注意。

### 1. 只改了布局，没改点击逻辑

例如你在 `fragment_main_layout.xml` 里加了按钮，但没有去 `MainFragment.java` 里写点击事件。

结果就是:

- 按钮能看到
- 但点了没反应

### 2. 只删了 Java 文件，没删主菜单入口

结果可能会出现:

- 编译报错
- 页面点击崩溃
- 资源残留

### 3. 文案写死在 Java 里

推荐把文本统一放到:

- `app/src/main/res/values/strings.xml`

这样更利于维护和国际化。

### 4. 忘记权限或依赖配置

如果你新功能需要:

- 录音
- 文件读写
- 新 SDK

除了写页面代码，还要记得看:

- `AndroidManifest.xml`
- `app/build.gradle`

### 5. 搞混“主菜单页”和“功能页”

请记住:

- `MainFragment.java` 是菜单页
- 其他 `XXXFragment.java` 才是功能页

所以“新增一个功能”通常不是去改 `MainActivity`，而是:

1. 新建一个 `XXXFragment`
2. 新建一个布局 XML
3. 在 `MainFragment` 里接入入口

### 6. 忽略公共基类 `BaseFragment`

如果你直接自己写一个普通 `Fragment`，可能就没有:

- 返回按钮
- 公共结果区
- 统一页面结构

除非你很明确知道自己要脱离当前框架，否则建议沿用 `BaseFragment`。

---

## 9. 给新手的建议: 改功能时的推荐顺序

如果你打算修改或新增一个页面，推荐按下面顺序看代码:

### 先找入口

看:

- `MainFragment.java`
- `fragment_main_layout.xml`

先确认这个功能是从哪个按钮进去的。

### 再找页面类

看对应的:

- `XXXFragment.java`

先理解:

- 页面初始化在哪
- 按钮绑定在哪
- API 调用在哪

### 再看布局

看对应的:

- `fragment_xxx_layout.xml`

确认页面控件和 Java 代码里的 `id` 是否对应。

### 最后看工具类或回调

如果发现这个功能还依赖:

- 地图工具
- 全局回调
- 语音回调

再继续去看:

- `maputils/`
- `application/`
- `view/`

---

## 10. 一句话总结“加功能”和“删功能”该改哪里

### 如果你想加一个新功能

优先改:

- `fragment/` 下新增一个 `XXXFragment.java`
- `layout/` 下新增一个 `fragment_xxx_layout.xml`
- `MainFragment.java`
- `fragment_main_layout.xml`
- `strings.xml`

必要时再改:

- `AndroidManifest.xml`
- `app/build.gradle`

### 如果你想删一个现有功能

优先改:

- `MainFragment.java`
- `fragment_main_layout.xml`
- 对应 `XXXFragment.java`
- 对应 `fragment_xxx_layout.xml`
- `strings.xml`

必要时再清理:

- `drawable/`
- `maputils/`
- 其他仅该功能使用的辅助类

---

## 11. 适合你现在的阅读顺序

如果你是第一次接手这个项目，建议按下面顺序阅读:

1. `README.md`
2. `app/src/main/AndroidManifest.xml`
3. `app/src/main/java/com/ainirobot/robotos/application/RobotOSApplication.java`
4. `app/src/main/java/com/ainirobot/robotos/MainActivity.java`
5. `app/src/main/java/com/ainirobot/robotos/fragment/BaseFragment.java`
6. `app/src/main/java/com/ainirobot/robotos/fragment/MainFragment.java`
7. 任选一个功能页，比如:
   - `SportFragment.java`
   - `SpeechFragment.java`
   - `NluTestFragment.java`
8. 再去看对应的布局 XML

按这个顺序，你会更容易建立整体认知。

---

## 12. 最后结论

这个项目最重要的开发规律只有三条:

1. **应用级初始化看 `RobotOSApplication.java`**
2. **页面容器和启动流程看 `MainActivity.java`**
3. **具体功能基本都看 `fragment/` 下的 `XXXFragment.java` 加对应 `layout/`**

如果你之后要让我继续帮你做，我可以基于这份文档继续给你补两类内容:

- 一份“新增功能模板”，直接告诉你新建 Fragment 和布局时该怎么写
- 一份“功能修改速查表”，按按钮、页面、资源、权限四个维度告诉你怎么改最快
