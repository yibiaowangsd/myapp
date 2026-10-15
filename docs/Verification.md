# 1.3.1 实际验证报告

验证时间：2026-10-15T17:02:06.190838+08:00。应用：com.personal.wo，versionName 1.3.1，versionCode 7，Room V1。

## 结论

两个原个人签名Release包构建结果以 [机器报告](Verification.json) 为准。95项测试通过，其中18项原生Compose交互/渲染测试。两个变体Lint均为0错误。21张生产组件渲染图覆盖分时段首页、统计、小时图、应用时段、空状态、照片不可读、窄屏大字体深色，以及既有权限与连接流程。

没有在HUAWEI HBN-AL80上执行系统权限、真实计步、图库URI或后台耗电测试。4项AndroidTest仅完成编译。华为账号Health Kit接入状态未改变。

## 测试

| 测试组 | 数量 | 失败 |
|---|---:|---:|
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

原始报告见[reports/v1.3.1](reports/v1.3.1/)。新的UI检查验证首页先出现图文时间线、全部片段入口、更多照片展开/收起与第5张打开、小时图区间并集总量、所选小时切换、缺失数据不画假零柱，以及360dp/1.5倍字体深色操作可达。继承权限直接请求、计步重连、连接选择、历史日期和分类测试。小时数据沿用已测试的日界/asOf裁剪、重叠去重和23/25小时处理。

图像由生产Compose组件经Android View.draw/Skia生成，Robolectric API35，不是真机截图。生活记录与应用列表为虚构测试状态；测试环境未安装第三方应用，图标使用正常不可用回退；照片URI刻意不可访问以检查错误显示与详情点击。用户原始截图、照片和实际生活记录不进入仓库。

最终构建使用串行工作器，避免不同flavor的KAPT输出与Lint读取互相覆盖；没有关闭Lint门禁。首轮照片测试遇到异步缩略图尚未结束，已改为等待明确的加载完成状态，最终重跑验证。

## 安装包与数据检查

- 两个包均为1.3.1 / 7，minSdk29、targetSdk36，仅ARM64 Release。
- 原签名证书SHA-256：c9b24de261dc30ebbc92da22f3f7826beb2c3015141c7b43d63b1ca27de9cb58。
- 离线版无INTERNET；增强版保留手动网络增强，两者首页完全相同。
- APK ZIP完整性、签名、ZIP 16KiB对齐和原生ELF 16KiB对齐通过。
- R8映射哈希与各APK的DEX标识一致，对应符号归档独立保存。
- Room V1与密钥结构不变；17实体、来源唯一键、FTS4与完整性检查通过。数据库打开回调仍以PRAGMA查询消费并关闭游标，记录保留检查通过。

- [Wo_Android_v1.3.1_Offline.apk检查](./Wo_Android_v1.3.1_Offline.apk.checks.json)：9029973字节；SHA-256：e838657bc178ccebef10d1e47178c6a53b6e751be613c12beba5df5c0f478d78。
- [Wo_Android_v1.3.1_Enhanced.apk检查](./Wo_Android_v1.3.1_Enhanced.apk.checks.json)：9111917字节；SHA-256：0a9749796138095f1c119e0184db241545d3eb9f98d38042b395c088cfcb760f。

## 可视检查与边界

[首页](ui/v1.3.1/12-timeline-home.png) · [图表在下方](ui/v1.3.1/18-day-chart.png) · [小时详情](ui/v1.3.1/19-hour-detail.png) · [大字体深色首页](ui/v1.3.1/20-recap-large-dark.png) · [大字体深色图表](ui/v1.3.1/21-chart-large-dark.png) · [全部渲染图](ui/v1.3.1/) · [设计检查](../design-qa.md)

图文首页使用360×812dp/2倍密度；大字体360×800dp/1.5倍字体；其余为412×915dp。部分图像是独立内容组件，真实外层另有顶部导航。设计复核修正了首轮大字体零散断行，详情见设计检查。

历史报告见[1.3.0归档](archive/v1.3.0/)与[1.2.0归档](archive/v1.2.0/)。此次没有性能/耗电设备对照，不提供优化百分比。真实照片锐度、华为系统授权、连续计步和后台存活仍待真机验收。
