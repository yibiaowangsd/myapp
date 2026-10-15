# 1.5.0 实际验证报告

验证时间：2026-10-16T03:12:00.782135+08:00。应用：com.personal.wo，versionName 1.5.0，versionCode 9，Room V1。

## 结论

两个原个人签名Release包构建通过。120项自动测试通过，其中31项原生Compose交互/渲染测试；两个变体Lint均为0错误。33张原生画面和25帧连续转动图覆盖时段汇总、双侧景深、轴内影像、时段详情、日期选择、时长门槛、大字体深色与横屏。结构化证据见[机器报告](Verification.json)。

没有在HUAWEI HBN-AL80上执行真实权限、图库URI、计步、GPU帧率或后台耗电测试。4项AndroidTest仅编译；不把Robolectric渲染当作真机截图。华为账号Health Kit接入状态没有改变。

## 测试

| 测试组 | 数量 | 失败/错误 |
|---|---:|---:|
| DailyRecapTest | 5 | 0 |
| DayChaptersTest | 8 | 0 |
| DayMomentsTest | 4 | 0 |
| DayPresentationTest | 6 | 0 |
| EngineTest | 33 | 0 |
| StepReadingsTest | 4 | 0 |
| StreamingCryptoTest | 2 | 0 |
| CollectionEfficiencyTest | 10 | 0 |
| DatabaseOpenCallbackTest | 2 | 0 |
| DayDrumUiTest | 13 | 0 |
| DirectPermissionPlanTest | 3 | 0 |
| StartupRecoveryTest | 8 | 0 |
| StepSensorsTest | 4 | 0 |
| UiFlowTest | 18 | 0 |

原始报告：[reports/v1.5.0](reports/v1.5.0/)。新核心用例验证短打开不凑门槛、恰好达到门槛、跨时段会话、重叠并集和实际空档、截止时间/计划/未知/时钟冲突、不同照片URI、步数单次归属和夏令时边界。

13项新首页UI测试验证按时段展示、实际横向手势、时间刻度、时段展开、原片段点击、恢复及动态增减、空历史日、窄屏大字体深色、横屏、统计、日期/今天/日期边界、门槛选择、全部影像与短记录。双侧像素断言检查真实画面左边和右边都有卡片；正负偏移属性对称断言和25帧原生渲染共同检查转动。原有18项UI流程和其他兼容性回归均保留。

首轮画面检查发现带透明度的图层平移在原生软件渲染中受裁剪；最终用共享画布加布局offset确定位置，图层只处理透视、缩放和淡出，在整块视口边缘裁切。也修复了跳转后旧回调/滚动状态判断吞掉点击。相关回归在最终构建重新执行。

## 安装包与兼容检查

- 两个包均为1.5.0 / 9，minSdk29、targetSdk36，非debuggable，只有ARM64 Release。
- 原签名证书SHA-256：`c9b24de261dc30ebbc92da22f3f7826beb2c3015141c7b43d63b1ca27de9cb58`。签名连续，可以覆盖原个人签名版本。
- 离线版没有INTERNET；增强版保留手动网络增强，两个版本首页相同。
- APK ZIP完整性、签名、ZIP 16KiB对齐和原生ELF 16KiB对齐通过；R8映射哈希与各APK的DEX标识匹配。
- Room V1与密钥结构未改动；17实体、来源唯一键、FTS4和完整性检查通过。SQLCipher打开回调继续使用PRAGMA查询并消费/关闭游标；SQLite检查验证记录保留。
- 构建采用串行任务，避免不同变体的KAPT输出与Lint读取冲突，没有关闭Lint门禁。

- [Wo_Android_v1.5.0_Offline.apk检查](Wo_Android_v1.5.0_Offline.apk.checks.json)：9466293字节；SHA-256：`335fb78dc5adc2ff7de47c9d62e4b3aeac7714e83570f07aa84645d57643fa3e`。
- [Wo_Android_v1.5.0_Enhanced.apk检查](Wo_Android_v1.5.0_Enhanced.apk.checks.json)：9531853字节；SHA-256：`8b685120c4ba490b7eb24b620b7cb8bb2eda4f3775a9d9cf1240d2bef8cf816b`。

构建输入指纹：`10c75f6e495db96432b51bcc7f92942281a8164cf7e5080eef95979fb09df0f9`。输入范围包括应用/核心源码、Gradle配置和锁、契约与验证脚本；不含报告自身、SDK路径、签名材料或生成目录。

## 原生画面与限制

[首页](ui/v1.5.0/22-chapter-home.png) · [两侧同时可见](ui/v1.5.0/24-both-sides-visible.png) · [大字体深色](ui/v1.5.0/26-chapter-large-dark.png) · [横屏](ui/v1.5.0/29-chapter-landscape.png) · [日历](ui/v1.5.0/30-chapter-calendar.png) · [时长门槛](ui/v1.5.0/31-chapter-filter.png) · [时段详情](ui/v1.5.0/32-chapter-details.png) · [连续帧](ui/v1.5.0/frames/) · [设计检查](../design-qa.md)

画面使用生产Compose组件、Robolectric API35 native graphics和Android View.draw/Skia。普通画面412×915dp，窄屏360×800dp/1.5倍字体，横屏800×360dp；日历与选项捕获实际顶层对话框窗口。样本记录及标有SAMPLE IMAGE的几何影像为测试夹具，没有用户原始照片。01–21为保留组件回归，不代表默认首页仍堆叠这些模块。

没有执行真实设备性能或耗电对照，不提供优化百分比或帧率承诺。真实照片清晰度、系统权限、计步连续性和后台存活仍需目标手机验收。上一版本报告见[1.4.0归档](archive/v1.4.0/)。
