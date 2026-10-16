# 首页横轴轮转与光效优化

## 交互目标

按 2026-10-16 的草图：中间是一条横向时间轴，重要事件卡片通过短连线连接在轴的上下两侧。左右拖动时，卡片连续改变远近、角度和透明度。不是竖直列表，也不是每次只能看到一张的大卡片翻页。

继续使用已有的大事件聚合、应用时长门槛、照片、历史日期和详情入口；不新增记录权限、数据采集、后台任务或网络请求。没有修改原始记录、事件聚合及数据库结构。

## 改动

- `TimelineWheel.kt`：共享画布绘制横轴和节点，上下交错事件卡片；相邻两侧采用同一套景深函数。低矮横屏和大字体空间不足时使用单排，优先保留标题和时间。画面中的卡片、连线、节点使用同一连续位置。
- `TimelineMotion.kt`：单调的圆柱投影和五次平滑淡出；没有原先在偏移 1.5 处由可见直接归零的跳变。远处事件不会转回来打乱时间顺序，上下位置不随焦点或拖动方向翻转。
- `DayDrumUi.kt`：保留原生 Pager 惯性；吸附改为无回弹弹簧（阻尼比 1，刚度 240）。时间滑条在按住时映射到分数页，松手后才吸附最近的大事件，避免反复整页跳转。
- `DayAtmosphere.kt`：采用归档日期/时区中的真实事件时间；全天调色节点间用平滑曲线过渡，渐变背景和宽幅弱光使用可中断的有限弹簧动画。卡片底色及文字明暗由系统主题决定，不再随事件时间跨阈值黑白翻转。浅色模式夜间呈较冷、较暗的柔和色调，深色模式保留低亮度。
- 点击依据 `LayoutCoordinates.localPositionOf` 映射到实际变换后的卡片，不再让未显示卡片的空白区域成为可点击区域。拖动会取消点击，侧卡先转到前面，再次轻触打开。

卡片景深读数放在 `graphicsLayer`，轴线放在绘制阶段；同时最多挂载七个邻近事件。光效不使用无限循环、全屏模糊、粒子或逐帧图片解码。没有宣称未经真机测量的帧率或功耗数值。

## 参考设计与落地方式

1. Apple — Designing Fluid Interfaces（WWDC 2018）：响应、随时打断/反向、空间一致性、惯性与平滑停止。用于交互行为参考，没有复制其视觉资源。
   https://developer.apple.com/videos/play/wwdc2018/803/
2. Android — Pager in Compose：使用原生 Pager 的连续偏移与惯性行为，限制一次快速滑动最多三个事件。
   https://developer.android.com/develop/ui/compose/layouts/pager
3. Android — Customize animations：用可重新定向的弹簧保持动画连续性，避免重启固定时长动画。
   https://developer.android.com/develop/ui/compose/animation/customize
4. Android — Carousel with MotionLayout：参考共享空间中的卡片位移和尺寸变化；本项目继续使用已有 Compose，不引入 MotionLayout。
   https://developer.android.com/develop/ui/views/animations/motionlayout/carousel

本次使用以上公开设计及官方文档作为参考，并未安装或宣称调用未经检索的第三方 skill。

## 验证

已在本次修改环境中用 Kotlin/JVM 编译并执行生产 `TimelineMotion.kt` 的检查脚本：

```sh
bash scripts/check-timeline-motion.sh
```

结果：7057 个几何/时间采样检查通过，另含边界与重复时间断言。覆盖左右对称、透明度连续、不回折、分页半页交接、上下分行、时间正反插值和越界约束。

`TimelineWheelTest.kt` 新增 Compose/Robolectric 检查：上下位置保持、空白不可误触、实际投影卡片可点击、拖动不误开详情及平滑/插值回归。原有日期、照片、大事件门槛和详情测试继续保留。

当前本地环境没有 Android SDK/Gradle 依赖，不能把数学检查等同于 Android 整包编译或真机验证。新增的 GitHub Actions 会在具备权限的仓库中执行完整编译和回归测试；是否通过以该提交对应的 Actions 结果为准。

真机重点复查：连续左右反向拖动、按住中断惯性、日出/日落过渡、拖动时间滑条、照片加载、深浅主题、较窄屏幕及系统关闭动画。此说明不表示已完成真机验收。
