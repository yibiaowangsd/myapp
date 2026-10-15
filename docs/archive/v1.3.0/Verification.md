# 1.3.0 实际验证报告

验证时间：2026-10-15T15:33:11.423299+08:00。应用：com.personal.wo，versionName 1.3.0，versionCode 6，Room V1。

## 结论

两个原个人签名Release包构建成功，91项测试全部通过，其中14项为原生Compose交互/渲染测试。两个变体Lint均为0错误。17张生产组件渲染图包含原有总结、权限、空状态与照片用例，以及默认时间线、应用连接、按应用授权、华为未接入状态、后台说明与窄屏大字体深色。

没有在HUAWEI HBN-AL80上执行系统权限、真实计步、三方媒体或后台耗电测试。4项AndroidTest仅完成编译。华为账号Health Kit缺应用配置与审核，未集成，不能据此宣称已连接华为运动健康历史数据。

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
| UiFlowTest | 14 | 0 |

原始报告见[reports/v1.3.0](../../reports/v1.3.0/)。新增验证覆盖：两个自动补采并发只运行一轮；手动绕过30秒冷却；取消/失败可重试；前台健康读取不被后台同步冷却；通知/日历选择不改变传感器计划；关闭传感器、暂停与精度变化；应用状态必须同时满足来源开启和系统授权，不能把安装当账号授权。

UI检查验证默认时间线与总结入口、应用搜索、通知选择直接触发系统请求回调、华为账号未接入状态与后台入口可见性、360dp/1.5倍字体下的深色应用开关。继承原有图文总结、日期、筛选和权限流程测试。

图像由生产Compose组件经Android View.draw/Skia生成，Robolectric API35，不是真机截图。生活记录与应用列表是虚构测试状态；测试环境未安装第三方应用，图标使用正常不可用回退，不包含用户原始照片。

## 安装包与数据检查

- 两个包均为1.3.0 / 6，minSdk29、targetSdk36，仅ARM64 Release。
- 复用原签名，证书SHA-256：c9b24de261dc30ebbc92da22f3f7826beb2c3015141c7b43d63b1ca27de9cb58。
- 离线版无INTERNET；增强版保留手动增强接口，两者都没有虚构的华为账号授权配置。
- APK ZIP完整性、签名验证、ZIP 16KiB对齐和原生ELF 16KiB对齐通过。
- R8映射哈希与各APK的DEX标识一致，另行保存对应符号归档。
- Room V1与密钥结构不变；17实体、来源唯一键、FTS4与完整性检查通过。1.0.2数据库打开回调修复保留，PRAGMA查询/游标关闭及记录保留检查通过。

- [Wo_Android_v1.3.0_Offline.apk检查](../../Wo_Android_v1.3.0_Offline.apk.checks.json)：9013589字节；SHA-256：707502ef723f0e1148e594922be301a86e054f36655da53ebe1e729ee38d5574。
- [Wo_Android_v1.3.0_Enhanced.apk检查](../../Wo_Android_v1.3.0_Enhanced.apk.checks.json)：9095533字节；SHA-256：d03679dd8dcd0d0222bb81e6979cd914d9ea4954efd6f9399e1999821f44319b。

## 可视检查与边界

[默认时间线](../../ui/v1.3.0/12-timeline-home.png) · [应用连接](../../ui/v1.3.0/13-connections.png) · [应用选择](../../ui/v1.3.0/14-app-connection.png) · [华为状态](../../ui/v1.3.0/15-huawei-status.png) · [后台说明](../../ui/v1.3.0/16-background-help.png) · [窄屏大字体深色](../../ui/v1.3.0/17-connections-large-dark.png) · [全部渲染图](../../ui/v1.3.0/)

部分图像为独立内容组件，实际外层还有顶部导航与边距。历史报告见[1.2.0归档](../../archive/v1.2.0/)。尚未做CPU/启动时长/耗电的设备对照，因此没有性能百分比结论；华为后台存活、权限界面、连续计步和7日稳定性仍待真机验收。
