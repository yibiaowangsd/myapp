# 1.7.2 性能优化验证（2026-10-19）

应用版本1.7.2 / 13，增强版Release使用原个人证书签名。六项实现与复测命令见[性能说明](Performance_1.7.2.md)，结构化结果见[Verification.json](Verification.json)。

| 项目 | 本轮结果 |
| --- | --- |
| 核心与增强版测试 | 232个不同用例，跨变体355次执行，失败0、错误0、跳过0；核心104、增强版128、离线版123 |
| 增强版Release | R8、资源压缩、Baseline Profile编译、Startup Profile合并与APK打包通过 |
| 双Debug与Release Lint | 每个变体均为0错误、37警告 |
| 数据库兼容 | Room V1仍为17个实体；结构、唯一性、FTS4和完整性检查通过 |
| 安装包静态检查 | 原签名匹配、版本1.7.2 / 13、非调试、arm64-v8a、minSdk29 / targetSdk36；ZIP与ELF 16KB对齐通过 |
| 启动配置 | Android 34模拟器两条路径采集通过，采集Baseline应用规则2955条、Startup规则2142条；合并种子后源码分别2971/2142条，由R8处理进入APK |
| 性能测量模块 | 编译通过；提供冷/温启动、完整首屏、时间轴转动、切日和切换页签路径 |
| 目标手机 | 未执行真机覆盖安装、耗时/帧率和耗电测量，不据此声称具体提速幅度 |

本轮查询回归确认：未修改日期及当天仅推进时钟均不重新读取原始观察；重新初始化后仍复用有效快照；手机时钟回拨会重建。成就缓存命中不读取完整日归档，单日内容变化仅重新读取该日。重复观察、范围失效、删除/撤权、策略代次、跨日去重及动态首页背景继续按原规则验证。异步聚合画面测试在时间轴就绪后取样。

签名证书SHA-256：`c9b24de261dc30ebbc92da22f3f7826beb2c3015141c7b43d63b1ca27de9cb58`。APK摘要及大小记录在结构化结果中；安装包单独交付，不提交到源码仓库。

[采集原始结果](https://github.com/yibiaowangsd/myapp/actions/runs/39235347350) · [代码与持续检查](https://github.com/yibiaowangsd/myapp/pull/4) · [路径采集工作流](https://github.com/yibiaowangsd/myapp/actions/workflows/performance-profiles.yml)

以下保留历史验证记录。

# 1.7.1 华为接入准备验证（2026-10-19）

本轮216个不同用例、跨变体327次执行全部通过，双变体Lint零错误，双Debug与增强Release/R8构建通过。平台审核、真实账号授权和个人签名覆盖安装未执行。完整范围与原始证据见[本轮验证](Huawei_Health_Verification_20261019.md)。

以下保留原始历史验证记录，不作为本轮实机结论。

# 1.6.0 构建与已有验证记录

整理时间：2026-10-16T09:39:55.581204+08:00。应用版本1.6.0 / 10，包名`com.personal.wo`，数据库Room V1。

本次仅构建并交付增强版Release，沿用原个人签名配置。Gradle构建成功；仓库同步整理已有代码、文档和结果，没有重新构建或测试。结构化记录见[历史 Verification.json](archive/v1.6.0/Verification.json)。

## 已完成的增强版构建

```bash
./gradlew :app:assemblePersonalEnhancedRelease -x :app:lintVitalPersonalEnhancedRelease --max-workers=1 --no-parallel --console=plain
```

- 产物：`Wo_Android_v1.6.0_Enhanced.apk`，已单独交付；安装包不提交到源码仓库。
- [原始构建日志](reports/v1.6.0/build-personalEnhancedRelease.txt)记录`BUILD SUCCESSFUL`，退出码0。
- 按交付要求跳过最终Release Lint及额外安装包校验；本报告不沿用1.5.0的签名、对齐、R8映射或Lint检查作为1.6.0结论。
- 离线变体源码保留，本次未交付1.6.0离线安装包。

## 此前已执行的自动测试

已有128项测试，失败0、错误0、跳过0。各测试的实际时间保存在原始XML及结构化记录中；这里的文档整理时间不是新的测试执行时间。

| 测试组 | 数量 | 失败/错误 |
|---|---:|---:|
| CollectionEfficiencyTest | 10 | 0 |
| DatabaseOpenCallbackTest | 2 | 0 |
| DayDrumUiTest | 17 | 0 |
| DirectPermissionPlanTest | 3 | 0 |
| StartupRecoveryTest | 8 | 0 |
| StepSensorsTest | 4 | 0 |
| UiFlowTest | 18 | 0 |
| DailyRecapTest | 5 | 0 |
| DayChaptersTest | 12 | 0 |
| DayMomentsTest | 4 | 0 |
| DayPresentationTest | 6 | 0 |
| EngineTest | 33 | 0 |
| StepReadingsTest | 4 | 0 |
| StreamingCryptoTest | 2 | 0 |

原始报告见[reports/v1.6.0](reports/v1.6.0/)。其中12项大事件核心测试覆盖实际时间边界、长应用门槛、照片聚类、有限合并、独占证据、空档和夏令时；17项首页UI测试覆盖手势、双侧景深、日期、详情、短记录入口、随浏览时间变化的背景及文字对比度，另保留18项原生UI回归。

## 已有原生画面

共36张PNG、37帧转动记录，来自生产Compose组件、Robolectric API35 native graphics与Android View.draw/Skia。

[大事件首页](ui/v1.6.0/22-chapter-home.png) · [左右景深](ui/v1.6.0/24-both-sides-visible.png) · [大字体深色](ui/v1.6.0/26-chapter-large-dark.png) · [横屏](ui/v1.6.0/29-chapter-landscape.png) · [事件详情](ui/v1.6.0/32-chapter-details.png) · [短记录](ui/v1.6.0/34-small-fragments.png) · [白昼](ui/v1.6.0/35-event-daylight.png) · [夜色](ui/v1.6.0/36-event-night.png) · [连续帧](ui/v1.6.0/frames/) · [设计检查](../design-qa.md)

普通画面412×915dp，窄屏360×800dp及1.5倍字体，横屏800×360dp。所有示例记录及SAMPLE IMAGE均为测试夹具，没有用户原始照片。01—21为保留组件回归，不代表当前默认首页布局。

## 设备与历史记录边界

未在HUAWEI HBN-AL80上执行本版的真实权限、图库URI、计步连续性、GPU帧率或后台耗电验收。已有4项AndroidTest未在设备执行；历史编译结果不视为本版设备通过。华为Health Kit平台接入状态未变。

本版没有数据库迁移、新权限或新后台任务，保留SQLCipher打开回调的查询及游标关闭修复。背景颜色只表达归档时间，不表示真实天气或当地日出日落。上一版完整检查见[1.5.0归档](archive/v1.5.0/)。
