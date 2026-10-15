# HarmonyOS 4.2.0 启动兼容修复 · 1.0.1

日期：2026-10-15（+08:00）。包名仍为 `com.personal.wo`，versionCode 从 1 升为 2；数据库仍为 Room V1，复用原个人签名。

## 现象与诊断边界

本人反馈 HarmonyOS 4.2.0 启动崩溃，暂未提供机型、底层 Android API、错误堆栈或目标设备。因此不能把某个系统组件称为该手机已经确认的唯一根因，也不能宣称已在该版本 HarmonyOS 真机验证通过。

源码检查确认：原 `WoViewModel.foreground()` 在 `viewModelScope.launch` 中直接等待 Graph、探测来源和恢复后台任务，没有异常处理。初始化 Deferred 失败后，该异常还会在前台恢复协程重新抛出，导致进程退出；仅在另一条初始化协程捕获 Exception 并不能保护这个入口。原通知/媒体等系统恢复入口也需要相同的错误边界。

## 1.0.1 的处理

| 修改 | 行为 |
|---|---|
| 初始化与前台恢复错误边界 | 处理 Exception 和 LinkageError，保留协程取消语义；失败显示诊断页，不自动清库 |
| 逐项来源探测 | 一个来源的权限/接口/类加载异常不会中断其他来源；缺失数据保留未知 |
| Health Connect 权限入口 | 主页面注册时使用延迟契约；实际授权时再创建可选 Provider 契约 |
| WorkManager | 按官方 Configuration.Provider 方式按需初始化，移出启动前的默认 WorkManagerInitializer 路径 |
| 调度、系统恢复与记录服务 | 对可恢复错误记录诊断并结束当前操作；不无限重启 |
| 窗口适配与温度读取 | 兼容接口异常保留标准窗口；温度接口失败时采取被动低功耗策略 |
| 诊断与重试 | 失败页可重试或本人主动复制诊断；保留原数据库、密钥和生活记录 |

错误处理不吞掉 OutOfMemoryError 等虚拟机故障，也不把来源查询失败展示为 0。诊断只保留应用版本、设备/API、异常类型和有限调用位置；一般异常消息不导出，避免夹带原始生活记录。LinkageError 允许保留有限的类/符号/库加载信息，不采集通知、照片、坐标或密钥。诊断保存在应用私有 noBackup 目录，只有一个有界的最近记录文件，没有自动网络上报。

## 安装与验证

1. 使用 1.0.1 的相应 Release APK 覆盖安装原个人签名版本，保留应用数据。两个变体共用包名，离线版仍无 INTERNET。
2. 首次打开应能进入首页，或看到明确的初始化失败诊断页。日常来源仍通过系统公开授权入口配置。
3. 如显示诊断页，点击“复制诊断”提供该段文字；其中的 API、错误类型和位置能缩小实际设备问题。
4. 如仍在出现诊断页之前直接退出，请提供手机型号以及开发者工具捕获的 crash buffer；不能从 HarmonyOS 的版本名称推断所有 Android 接口都存在。

可在本机连接手机后收集：

```bash
adb logcat -b crash -d -v threadtime > wo-crash.txt
```

此命令读取系统 crash buffer，输出可能含其他应用信息；只需要提供 `com.personal.wo` 对应的异常段。开发者不要求卸载应用、清除数据或重置密钥。

## 回归与后续

新增 8 项 JVM 回归覆盖初始化失败、UnsatisfiedLinkError、取消传播、可选 Provider 缺失、来源权限异常、已关闭来源不初始化、诊断去除一般异常消息、虚拟机错误不被隐藏。原 35 项领域/加密测试也重新执行通过。构建、Lint、签名和 APK 对齐的最终记录见 [Verification.md](Verification.md)。

没有可用 HarmonyOS 目标手机；现有 Android 数据库/备份设备测试只完成编译。该兼容修复仍需要本人手机安装验证，并据实际日志继续处理设备特定问题。

## 依据

- [Android 官方：WorkManager 按需初始化](https://developer.android.com/develop/background-work/background-tasks/persistent/configuration/custom-configuration)
- [Android 官方：Health Connect 可用性与系统版本](https://developer.android.com/health-and-fitness/health-connect/get-started)

接口按可用性和授权判定，不硬编码把所有华为设备或所有可选来源关闭。
