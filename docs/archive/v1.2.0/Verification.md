# 1.2.0 实际验证报告

验证时间：2026-10-15T14:38:03.644945+08:00。应用：com.personal.wo，versionName 1.2.0，versionCode 5，Room V1。

## 结论

两个原个人签名Release包构建成功，77项测试全部通过，其中10项为原生Compose交互/渲染测试。两个变体Lint均为0错误。已保存并检查11张原生组件渲染图，覆盖总结首页、时段列表、授权管理、小屏大字体、深色、空状态、照片不可访问和计步重连等状态。

本次没有在HUAWEI HBN-AL80上执行系统权限弹窗、实际走动计步或后台运行测试。4项AndroidTest只完成编译。硬件注册回退和权限计划使用受控测试输入，不能据此宣称华为实机问题已全部解决。

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
| UiFlowTest | 10 | 0 |

报告见[reports/v1.2.0](../../reports/v1.2.0/)。新增验证覆盖：授权前传感器不可见时仍允许申请身体活动；普通权限按系统版本组织；通知/媒体共用授权去重；批量注册失败重试；累计计步失败回退单步检测；重复、未来与非法事件过滤；首样本不计为当天总步数；实际事件时间；计划/未知/未来不进入事实回顾；跨时段步数不虚分；应用会话之间保留空档；照片按时段归属。

原生UI验证包括点击单项即调用系统请求回调、统一申请入口、总结数据、应用时段滚动、地点点击、空状态、360dp/1.5倍字体、深色、筛选、日期选择、照片不可访问与计步重连。图像由生产Compose组件经Android View.draw/Skia生成，API35；不是真机截图。生活记录为虚构测试数据，照片测试为不可访问状态，不包含用户原始照片。

## 安装包与数据检查

- 两个包均为1.2.0 / 5，minSdk29、targetSdk36，仅ARM64 Release。
- 复用原签名，证书SHA-256：c9b24de261dc30ebbc92da22f3f7826beb2c3015141c7b43d63b1ca27de9cb58。
- 离线版无INTERNET；增强版保留手动增强接口。
- APK ZIP完整性、签名验证、ZIP 16KiB对齐和原生ELF 16KiB对齐通过。
- R8映射哈希与各APK的DEX标识一致，另行提供对应符号归档。
- Room V1和密钥结构不变；17实体、来源唯一键、FTS4与完整性检查通过。1.0.2数据库打开回调修复保留，PRAGMA查询契约及保留记录检查通过。

- [Wo_Android_v1.2.0_Offline.apk检查](../.././Wo_Android_v1.2.0_Offline.apk.checks.json)：8980789字节；SHA-256：a6f84c7678642fd280b00cd4e8490278d37541e9496f3611b5b39bb5f297a2ff。
- [Wo_Android_v1.2.0_Enhanced.apk检查](../.././Wo_Android_v1.2.0_Enhanced.apk.checks.json)：9046349字节；SHA-256：8930b008147d47325a519ce77abaaca8b9ce0f079886d062567c991121bd7fce。

## 可视检查

[总结首页](../../ui/v1.2.0/03-today.png) · [应用时段和时间线](../../ui/v1.2.0/04-visuals.png) · [权限页](../../ui/v1.2.0/01-permissions.png) · [大字体](../../ui/v1.2.0/06-large-text.png) · [深色](../../ui/v1.2.0/07-dark.png) · [计步重连](../../ui/v1.2.0/11-step-retry.png) · [全部截图](../../ui/v1.2.0/)

部分图像为独立内容组件，实际应用外层包含顶部导航与边距。历史报告保存在[archive/v1.1.0](../../archive/v1.1.0/)。真实权限、华为硬件、锁屏恢复、16KiB设备运行、日耗电与7日稳定性仍待设备验收。
