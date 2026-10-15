# 1.4.0 实际验证报告

验证时间：2026-10-15T21:00:50.732838+08:00。应用：com.personal.wo，versionName 1.4.0，versionCode 8，Room V1。

## 结论

两个原个人签名Release包构建结果以 [机器报告](Verification.json) 为准。107项测试通过，其中26项原生Compose交互/渲染测试。两个变体Lint均为0错误。29张生产组件渲染图覆盖转筒首页、中间透视状态、独立统计、窄屏大字体深色、横屏、空状态，以及既有权限与连接流程。另保留13帧生产组件转动示例，使用虚构记录。

没有在HUAWEI HBN-AL80上执行系统权限、真实计步、图库URI或后台耗电测试。4项AndroidTest仅完成编译。华为账号Health Kit接入状态未改变。

## 测试

| 测试组 | 数量 | 失败 |
|---|---:|---:|
| DayMomentsTest | 4 | 0 |
| DayDrumUiTest | 8 | 0 |
| DailyRecapTest | 5 | 0 |
| DayPresentationTest | 6 | 0 |
| EngineTest | 33 | 0 |
| StepReadingsTest | 4 | 0 |
| StreamingCryptoTest | 2 | 0 |
| DatabaseOpenCallbackTest | 2 | 0 |
| DirectPermissionPlanTest | 3 | 0 |
| StartupRecoveryTest | 8 | 0 |
| StepSensorsTest | 4 | 0 |
| CollectionEfficiencyTest | 10 | 0 |
| UiFlowTest | 18 | 0 |

原始报告见[reports/v1.4.0](reports/v1.4.0/)。新测试检查：默认首页只有转筒、统计显式进入、横向拖动、时间刻度跳转、片段点击、时段切换、保存恢复、记录插入及删除后位置安全、空状态与真实来源入口、360dp/1.5倍字体深色、800×360dp横屏，以及中间偏移的原生透视帧。核心展示投影检查时间边界、计划/未知排除、步数只出现一次和缺口保留。原有日统计、小时图区间并集、23/25小时、权限、计步、连接等回归保留。

图像由生产Compose组件经Android View.draw/Skia生成，Robolectric API35，不是真机截图。生活记录与应用列表为虚构测试状态；测试环境未安装第三方应用，图标使用正常不可用回退；照片URI刻意不可访问以检查错误显示与详情点击。用户原始截图、照片和实际生活记录不进入仓库。

最终构建使用串行工作器，避免不同flavor的KAPT输出与Lint读取互相覆盖；没有关闭Lint门禁。首轮记录插入测试发现Pager页数与旧key闭包不一致导致越界，已改为key和内容读取最新快照；最终检查同时覆盖插入及删除。大字体和转动中间态经过原生渲染复核。

## 安装包与数据检查

- 两个包均为1.4.0 / 8，minSdk29、targetSdk36，仅ARM64 Release。
- 原签名证书SHA-256：c9b24de261dc30ebbc92da22f3f7826beb2c3015141c7b43d63b1ca27de9cb58。
- 离线版无INTERNET；增强版保留手动网络增强，两者首页完全相同。
- APK ZIP完整性、签名、ZIP 16KiB对齐和原生ELF 16KiB对齐通过。
- R8映射哈希与各APK的DEX标识一致，对应符号归档独立保存。
- Room V1与密钥结构不变；17实体、来源唯一键、FTS4与完整性检查通过。数据库打开回调仍以PRAGMA查询消费并关闭游标，记录保留检查通过。

- [Wo_Android_v1.4.0_Offline.apk检查](./Wo_Android_v1.4.0_Offline.apk.checks.json)：9095509字节；SHA-256：8fb48e7f2d337cd4d7ea2e17ac9885c5d0df3e07e1644ee9c54ae485484d7920。
- [Wo_Android_v1.4.0_Enhanced.apk检查](./Wo_Android_v1.4.0_Enhanced.apk.checks.json)：9177453字节；SHA-256：47d0b3d7558959c11e096e0ad2843fde67003b869f614efd962fb7849ddb863c。

## 可视检查与边界

[首页](ui/v1.4.0/22-drum-home.png) · [影像卡](ui/v1.4.0/23-drum-photo.png) · [大字体深色](ui/v1.4.0/26-drum-large-dark.png) · [中间透视状态](ui/v1.4.0/28-drum-depth.png) · [横屏](ui/v1.4.0/29-drum-landscape.png) · [全部渲染](ui/v1.4.0/) · [设计检查](../design-qa.md)

转筒常规412×915dp/2倍密度；大字体360×800dp/1.5倍字体；横屏800×360dp。中间透视图为单独的转筒生产组件，顶部明确标注虚构示例。01–21是继承组件回归，其中此前分时段回顾不再是默认首页。

历史报告见[1.3.1归档](archive/v1.3.1/)与[1.3.0归档](archive/v1.3.0/)与[1.2.0归档](archive/v1.2.0/)。此次没有性能/耗电设备对照，不提供优化百分比。真实照片锐度、华为系统授权、连续计步和后台存活仍待真机验收。
