# “我” Android 开发说明

文档更新：2026-10-15（Asia/Shanghai）

对应应用版本：1.4.0；数据库版本：Room V1；包名：`com.personal.wo`。

本仓库包含 1.0.0 基础功能、1.0.1 启动异常处理、1.0.2 数据库打开回调修复、1.1.0界面重构、1.2.0直接授权/计步兼容/每日总结，以及1.3.0时间线首页/应用连接/采集提效。最新构建结果见 Verification.md，原 1.0.0 验证记录保存在 archive/v1.0.0/。修复范围和实际设备验证边界见 [HarmonyOS 启动修复说明](HarmonyOS_4.2.0_Startup_Fix.md)。

新增1.4.0转筒时间线：`DayMoments.kt`负责真实片段的展示投影；`DayDrumUi.kt`使用原生Compose Pager和graphicsLayer连续旋转、缩放、透明度与圆弧位移。统计移至独立页面。实现说明与边界见[1.4.0](Drum_Home_1.4.0.md)。

## 1. 阅读入口

- [当前实现设计](Design.md)：页面、来源、两个版本、数据处理与限制。
- [原始设计基线](Design_Baseline.md)：完整原始需求及 G01—G08 验收标准。
- [验证报告](Verification.md) / [结构化结果](Verification.json)：已执行与未执行的检查。
- [原始测试与 Lint 报告](reports/)：本轮源码对应的自动检查证据。

## 2. 环境与固定依赖

| 项目 | 工程固定配置 |
|---|---|
| JDK / JVM target | 17 |
| Android compileSdk / targetSdk / minSdk | 36 / 36 / 29 |
| Android Build Tools | 36.0.0 |
| Gradle Wrapper / Android Gradle Plugin | 8.13 / 8.13.2 |
| Kotlin / Compose compiler plugin | 2.2.21 |
| Compose BOM | 2025.10.01 |
| Room + KAPT | 2.8.4 |
| WorkManager | 2.10.5 |
| Health Connect | 1.1.0 |
| SQLCipher Android / AndroidX SQLite | 4.10.0 / 2.5.2 |
| Coroutines / Serialization JSON | 1.10.2 / 1.9.0 |
| Tink Android / JVM test Tink | 1.18.0 |

实际完整依赖以 Gradle 文件及 `app/gradle.lockfile`、`core/gradle.lockfile` 为准。Wrapper 分发包有 SHA-256 校验，`gradle/verification-metadata.xml` 保存依赖校验。首次解析依赖需要网络；源码不含 Android SDK 或完整 Maven 缓存。固定版本保持原构建组合，不因上传仓库而更新依赖。

安装 JDK 17 和上述 Android SDK 后，使用 Android Studio 打开仓库根目录。SDK 路径可由本机 `local.properties` 或环境配置提供；`local.properties` 不进入 Git。

## 3. 模块与主要文件

| 文件/目录 | 职责 |
|---|---|
| `core/.../Models.kt` | 来源、观察、片段、证据、断言、指标、日视图与策略契约 |
| `core/.../Intervals.kt` | 半开区间、并集/扣除、排除过滤、计步基线、距离与投影 |
| `core/.../DayEngine.kt` | 应用会话、停留/移动、媒体、生活片段、摘要与规律 |
| `core/.../NoticeRules.kt` | 通知敏感信息脱敏及文本分词 |
| `core/.../ArchiveRules.kt` | 归档文件名和头部限制 |
| `app/.../WoApplication.kt` | IO 初始化、Graph、SQLCipher 打开、有限队列批处理 |
| `app/.../collector/PullCollectors.kt` | UsageEvents、MediaStore、Calendar、Health Connect 补采 |
| `app/.../collector/PassiveListeners.kt` | 通知、MediaSession 与窗口元数据回调 |
| `app/.../collector/RecordingService.kt` | 框架定位、计步、静默前台服务、GNSS 与降级预算 |
| `app/.../collector/Capabilities.kt` | 来源权限、Provider 与功能可用性检查 |
| `app/.../collector/WorkScheduling.kt` | 唯一任务、维护、启动/时钟/时区恢复与暂停入口 |
| `app/.../data/WoRepository.kt` | 事务写入、策略/游标、缓存重建、搜索、修正、删除与清理 |
| `app/.../data/KeyVault.kt` | Keystore 包装、数据库秘密、HMAC 来源键、保护配置 |
| `app/.../data/BackupService.kt` | 流式归档、预检、暂存恢复、每日文本导出 |
| `app/.../data/EnhancedSummary.kt` | 可选 HTTPS 事实排序协议与额度 |
| `app/.../MainActivity.kt` | 权限/设置入口、身份验证、文件选择与生命周期 |
| `app/.../StartupRecovery.kt` / `StartupDiagnostics.kt` | 可恢复错误边界、逐项来源隔离及不依赖数据库的诊断 |
| `app/.../ui/StartupScreens.kt` | 加载、失败、重试与本人主动复制诊断 |
| `app/.../ui/` | ViewModel、Compose 页面、直接授权、图文总结、生活时间线、足迹与缩略图 |
| `app/src/main/assets/default_config.json` | 默认算法与预算参数 |
| `app/schemas/` / `contracts/schema.sql` | 实际 Room V1 导出结构与生成的可读 DDL |

表中的 Kotlin 路径省略了 `src/main/kotlin/com/personal/wo/` 前缀；core 领域文件位于该前缀下的 `core/` 包。

## 4. 数据流与修改约束

采集器把公开接口结果规范化为 Observation，生成稳定 sourceKey，连同当前 generation 进入仓库。回调通道容量 512，最多合并 100 条；拉取来源的观察、游标、覆盖和 dirty_window 在同一事务更新，避免只推进游标而没有落数据。

DayEngine 在归档时区内按半开时间区间生成 DayView；数据发生变化时更新 revision，缓存可先显示，重建在后台进行。UI 与网络增强应用结果之前检查日期/revision 或处理代次，避免把旧结果覆盖新视图。

修改领域逻辑时保留以下约束：

1. 原生同一来源记录重复读取保持幂等，不能凭到达次数累计时长或步数。
2. 多应用重叠按区间并集计算手机活动；缺失终止边界保留未知。
3. 暂停/停止/删除先持久化 generation 和排除，旧批次及历史补采仍受过滤。
4. 计划、通知线索与系统记录不混淆；未知不是数值 0；本人确认保留断言来源。
5. 设备与时钟边界切断计步/会话连续性；不把旧媒体会话延伸到进程恢复。
6. 归档恢复失败不得部分替换正式数据；密钥故障不得自动清库。
7. 发布包的离线网络权限和原生对齐需独立检查，不能仅凭源码推断。

## 5. 数据库表

以 `app/schemas/com.personal.wo.data.WoDatabase/1.json` 为权威结构。

| 表 | 用途 |
|---|---|
| app_meta | 版本、额度等小型元数据 |
| recording_policy | 单例记录策略与处理代次 |
| source_state | 来源能力、状态与游标 |
| observation | 原始观察；sourceId + sourceKey 唯一 |
| exclusion | 暂停、停止、应用/时段与删除排除 |
| place | 本人命名的地点 |
| episode | 片段、revision、标题及时间边界 |
| episode_evidence | 最小来源证据；随片段删除级联 |
| episode_relation | 片段间关系 |
| annotation | 本人修正、确认与收藏 |
| media_ref | 本机媒体 URI 索引 |
| daily_metric | 日期/时区/指标聚合 |
| daily_summary | 日视图缓存 |
| source_coverage | 来源覆盖与缺口 |
| event_search | FTS4 检索索引 |
| operation_log | 有界诊断日志 |
| dirty_window | 等待重建的数据时间窗口 |

当前没有需要兼容的旧发布 schema。后续修改数据库必须提升版本、添加明确 Migration 并导出新 schema；不得使用破坏性迁移兜底。可运行 `python3 scripts/verify_schema.py` 检查现有结构，该脚本同时更新可读 DDL。

## 6. 构建与测试

在仓库根目录执行；Windows 使用 `gradlew.bat`。源码不含签名私钥，Debug 使用标准调试签名。

```bash
./gradlew :core:test :app:testPersonalOfflineDebugUnitTest
./gradlew :app:lintPersonalOfflineDebug :app:lintPersonalEnhancedDebug
./gradlew :app:assemblePersonalOfflineDebug :app:assemblePersonalEnhancedDebug
./gradlew :app:assemblePersonalOfflineDebugAndroidTest
python3 scripts/verify_schema.py
python3 scripts/verify_pragmas.py
```

连接 Android 10+ 的测试手机或已正常启动的兼容模拟器后，执行设备测试：

```bash
./gradlew :app:connectedPersonalOfflineDebugAndroidTest
```

Debug 支持 arm64-v8a 和 x86_64。标准 APK 输出在 `app/build/outputs/apk/<flavor>/<buildType>/`；本地单元测试报告在 `core/build/test-results/test/`；Lint 报告在 `app/build/reports/`。

当前包含事件引擎、流式加密、应用时长口径、每日回顾、硬件计步事件、系统权限计划、传感器注册回退、启动恢复、数据库回调和原生UI流程测试。最终测试数量与通过状态见Verification.md。UI测试覆盖点击即请求、统一申请入口、照片不可访问、计步重连、小屏大字体、深色、时间线和日期选择。AndroidTest 4项只编译，尚未在设备执行；本次报告保存在docs/reports/v1.4.0/。

## 7. Release 与升级签名

Release 只包含 arm64-v8a，启用 R8 和资源压缩。使用个人签名备份，在本机安全提供以下环境变量：

| 环境变量 | 含义 |
|---|---|
| WO_KEYSTORE_PATH | 私有 keystore 的本机路径 |
| WO_KEYSTORE_PASSWORD | keystore 密码 |
| WO_KEY_ALIAS | 签名别名，未指定时默认 wo |
| WO_KEY_PASSWORD | 私钥密码 |

```bash
./gradlew :app:assemblePersonalOfflineRelease :app:assemblePersonalEnhancedRelease
```

未提供签名配置时，构建可能生成未签名产物；不能将其当作可覆盖安装的个人 Release。升级必须复用原签名并提升 versionCode。离线/增强共用包名，切换时可覆盖；Debug 与已有个人签名 Release 冲突时，请使用独立测试设备或先明确备份再选择测试方式。

静态检查脚本需要 APK 路径和 Android SDK 路径：

```bash
python3 scripts/inspect_apk.py /absolute/path/Wo_Android_v1.4.0_Offline.apk /absolute/path/android-sdk
```

脚本检查签名、min/targetSdk、离线 INTERNET 移除、APK zipalign 以及原生 ELF 16 KiB 对齐。它不能替代 16 KiB Android 设备上的运行验证。离线 APK 文件名应含 `Offline` 或 `personalOffline`，因为脚本据此选择网络权限断言。

## 8. 增强端点开发协议

此协议是应用自定义的事实排序协议。端点用 HTTPS，访问密钥（若提供）通过 `Authorization: Bearer ...` 发送，在手机 Keystore 保护的配置中保存。

请求示例：

```json
{
  "task": "arrangeFacts",
  "model": "optional-model",
  "facts": [
    {"id": "steps", "type": "STEPS", "value": "4280步"}
  ],
  "outputContract": "Return only a JSON object with factIds, a unique ordered list drawn from the provided IDs. Do not generate text or new facts."
}
```

有效响应：

```json
{"factIds":["steps"]}
```

服务端只返回已给定事实 ID 的次序，不返回文案、数字或新增事实。应用限制请求体 12 KiB、响应体 16 KiB、连接超时 10 秒、读取超时 20 秒；最多 4 个唯一且已知的事实 ID。未知字段按严格序列化契约拒绝；失败后保留本地摘要。

当前实际归档日最多请求 3 次，额度在发出网络请求之前事务记录，失败请求也占用额度。没有自动后台模型任务，也没有在仓库内提供对应服务端。

## 9. 备份开发注意事项

内部固定七项是 `manifest.json`、`observations.jsonl`、`episodes.jsonl`、`annotations.jsonl`、`places.json`、`policy.json`、`daily.jsonl`。实际规范由 ArchiveRules 与 BackupService 共同约束。

独立恢复码不写入归档。整个外层头绑定为关联数据，先完全认证加密流，再解析 ZIP 和业务记录。恢复在加密暂存数据库检查后，以正式数据库单事务替换；失败回滚，成功后保持停止并重置来源授权状态。恢复来源键种子，但不继承旧设备标识和媒体访问权限。

当前每类 JSONL 50,000 条、展开 512 MiB 的限额是首版边界。扩展备份时需同时完善导出入口、计数/限额、认证失败处理和大规模性能验证，不能只去掉限制。

## 10. 继续开发与真机验收

| 优先事项 | 完成标准 |
|---|---|
| 实际安装与来源授权 | 目标 Android/OEM 上逐项授权、撤销、再次授予；来源状态与缺口符合实际 |
| 数据库与恢复设备测试 | 运行现有 4 项 AndroidTest；错误恢复码/截断后旧数据仍完整 |
| 后台与日耗电 | 记录 24 小时对照、熄屏/重启/省电/温度/进程中断及 7 日稳定性结果 |
| 显示兼容 | 小屏、大字体 200%、深色模式、辅助功能与 16 KiB 系统运行 |
| 初始化救援恢复 | 密钥/数据库初始化失败时仍能进入独立恢复流程并保留原文件 |
| 健康与媒体对账 | Health Connect 变更令牌、更早撤回、媒体删除和跨设备重新关联 |
| 大规模备份/迁移 | 范围完整备份入口、真实规模内存与时间、明确 schema Migration |

评估结果按 [原设计第 17–19 节](Design_Baseline.md)记录设备、系统、授权、电量条件与结论；不得把未执行的测试填成通过。

## 11. 仓库与资料更新

`docs/Source_SHA256.json` 记录除自身外的仓库交付文件 SHA-256，用于比对来源。更新文件后重新生成清单，保留原始设计和历史验证时间；新增验证应明确实际执行日期和环境。

仓库不包含安装包、原始生活数据、恢复码、签名私钥、API 密钥或本机 SDK 配置。个人签名备份需由本人另行保管。1.4.0 提升 versionCode 到 8，复用原 Release 签名与数据库结构，可覆盖升级。

## 12. 启动兼容约束

WorkManager 采用 Configuration.Provider 按需初始化，Manifest 只移除默认 WorkManagerInitializer 元数据。所有调用仍使用 WorkManager.getInstance(context)，无需重复手动 initialize。

初始化、前台恢复及系统监听入口处理 Exception 与 LinkageError；CancellationException 必须继续传播，虚拟机故障不作普通错误吞掉。可选来源按来源分别探测，权限拒绝保留 UNKNOWN 与可授权入口，接口/类确实不可用才标记不支持。Health Connect 权限契约延后到本人授权时创建。

失败诊断独立于数据库，保存在私有 noBackup 目录。不要将原始异常中的业务消息、通知内容、密钥或数据库查询数据写入诊断。重试关闭失败 Graph 的通道与作用域后重新初始化，不清除或迁移原始数据库。

## 13. 返回结果的数据库配置语句

生产 Graph 与 AndroidTest 共用 data/DatabaseOpenCallback.kt。busy_timeout 即使赋值也返回一行，必须通过 query 并读取游标触发执行；execSQL 的更新/删除路径会拒绝结果行。游标统一使用 use 关闭。禁止将这类配置错误通过清库、重建 Keystore 或破坏性 Migration 兜底。

DatabaseOpenCallbackTest 使用严格的数据库接口替身测试生产回调契约；verify_pragmas.py 使用真实宿主 SQLite 验证返回结果。Android SQLCipher 的打开与重开检查位于 DatabaseBackupTest，尚待设备执行。源码与已有数据库版本均保持 V1。

## 1.2.0 总结、授权与计步代码

- `ui/DailyRecapUi.kt`：今日回顾、四项统计、分时段图文时间线、应用区间与应用图标（IO加载、有界缓存）。
- `core/DailyRecap.kt`：事实范围、分时段回顾、保留空档的应用区间；排除计划、未知、未来和时钟冲突。
- `ui/PermissionsUi.kt`：单项点击直达系统与统一申请入口，没有说明确认弹层。
- `collector/DirectPermissionPlan.kt` / `MainActivity.kt`：一次普通权限请求＋去重的特殊访问队列；保存队列恢复状态；首次/升级只自动发起一轮；拒绝不循环。
- `collector/StepSensors.kt`：遍历累计计步/单步检测以及唤醒/非默认设备，检查registerListener结果，批量注册失败立即重试，全部失败明确标记ERROR。未有身体活动授权时不将来源永久标为不支持。
- `collector/RecordingService.kt`：45秒没有首个计步事件时尝试单步检测流；任何时刻只计一个硬件流。事件按5秒合并进入已有4.5秒有界队列，无全天加速度计采样。注册成功但未收到数据是WAITING，不伪造连接正常或步数。
- `core/StepReadings.kt`：用传感器单调时间还原事件时刻，累计首样本建基线，拒绝重复、无效和未来事件；单步检测每条合法事件只加1。重启/重连不导入开机以来总数。
- `UiFlowTest`：Robolectric API35原生图形模式渲染生产Compose组件，输出到`app/build/ui-previews/`。不是系统授权弹窗或华为实机传感器验证。

运行`./gradlew :app:testPersonalOfflineDebugUnitTest :core:test`；首次需要下载Robolectric的Android运行时。测试依赖不进入Release包。系统权限与硬件兼容性必须在HBN-AL80上确认；本工程未配置华为Health Kit平台凭据，也不声称已同步华为运动健康历史数据。


## 1.3.0 时间线、连接与效率

- `ui/AppUi.kt`：底部四页；默认 `TodayTimeline`，汇总独立为 summary 路由。`TimelinePage` 延续日期/类别/位置分布，片段显示真实开始和结束时间。
- `collector/AppConnections.kt`：从可见 launcher 和已有记录包名发现安装应用，后台读取名称，不申请 QUERY_ALL_PACKAGES。常用应用分组仅帮助浏览，不代表厂商账号/API接入。`connectionStatus` 同时检查策略、排除、来源启用和系统授权。
- `ui/ConnectionsUi.kt`：应用搜索、展开配置、直接通知授权；来源与后台分页内显示 Health Connect 和华为运动健康的不同状态。通知正文选择采用当前策略增量修改，避免快速点击覆盖其他应用选择。
- `collector/PullGate.kt`：Mutex 串行化补采，使用 elapsedRealtime 进行30秒成功冷却；手动刷新/授权完成可以强制拉取。失败/取消不更新时间；前台请求不会被可能跳过健康读取的后台请求冷却。每个来源原有超时、游标和隔离逻辑保留。
- `data/WoRepository.kt`：probe 合并为一次事务和一次通知，旧游标、成功时间和新鲜度在同一个锁内保留；不变状态不重复写入。数据库版本和表结构保持 V1。
- `ui/WoViewModel.kt`：reload 串行避免多个日重建同时执行；进入历史相关页面才读取90天摘要和计算规律；日期切换不重复拉取系统数据。位置展示只查询 LOCATION 行。
- `collector/RecordingPlan.kt` / `RecordingService.kt`：根据当前记录策略、来源和真实权限决定传感器运行计划。日历、通知等无关变更保持计步基线；位置权限/精度、传感器启用变化或显式重连才重建服务。启动失败写入诊断。无步数/位置时停掉前台记录服务；step-only 不注册显著运动触发器。

`CollectionEfficiencyTest` 验证并发补采、冷却/强制刷新、异常取消重试、后台服务必要性、权限变化及应用连接状态；原生UI测试覆盖默认时间线、应用搜索/选择/系统请求回调、华为未连接、后台说明及窄屏大字体深色。

这是减少重复工作次数的代码改动，没有实机CPU/耗电/启动时长对比，不能声明提升百分比。Huawei Health Kit 仍缺应用开通和数据审核配置，未加入不可工作的SDK或虚构AppID；完整实施清单见[华为接入准备](Huawei_Health_Integration.md)。


构建环境提示：本轮增量构建遇到一次Lint的Kotlin分析器引用另一个变体KAPT临时stub、而该文件正在重新生成的FileNotFoundException。将最终测试/Lint/Release命令加上 `--max-workers=1 --no-parallel` 串行执行，保留所有检查，不关闭Lint。该问题属于构建工具并发，不是设备运行诊断。


## 历史：1.3.1 图文首页与日图表

以下保留上一版实现说明，默认路由已由1.4.0替换。

- `AppUi.kt`：默认 tab 0 直接调用 `TodayDashboard`；“全部片段”打开 `day` 路由，详情返回保存父路由，首页底栏名为“今天”。保留旧 summary 路由以兼容状态恢复。
- `DailyRecapUi.kt`：先显示 `recapParts`，再显示统计与图表。两列照片默认取前两项，展开访问全部；`rememberSaveable(part.startMs)` 保留展开状态，时段标题用 FlowRow 适应大字体；时间线连接线根据卡片实际高度绘制。
- `DayCharts.kt`：`DayUsageChart` 使用现有 `hourlyUsage`，不会修改或增加数据库数据。Canvas 绘制小时柱图，点击及前后按钮选择小时；TalkBack 提供所选时间值和前后操作。未知用量与未到的小时保留为空。
- `UiFlowTest`：默认首页顺序、完整片段入口、影像展开与点击、小时图总量及选择、无数据图表、360dp/1.5倍字体深色。其余连接与权限测试保持。

最终发布检查使用 `--max-workers=1 --no-parallel` 串行执行双变体 Lint、单元测试和签名构建，避免不同 flavor 的 KAPT stub 在 Lint 读取期间重建；不关闭 Lint 门禁。签名、Room V1、应用 ID 保持原有值，安装包 versionName 1.3.1 / versionCode 7。

## 1.4.0 转筒首页

- `core/DayMoments.kt`：按实际片段和截止时间建立展示序列；步数在区间结束出现一次；按归档时区定位。原始Episode不变。
- `ui/DayDrumUi.kt`：Pager连续手势、圆弧坐标、Y轴旋转、缩放、透明度；稳定ID与最新快照避免刷新导致越界。时间轴选择最近真实片段，没有插入模拟事件或自动播放。
- `AppUi.kt`：tab 0调用DayDrumHome，summary路由调用DayStatisticsPage。独立的dayParent使统计→全部片段→详情能按层返回；保存的首页状态保留正在查看的位置。
- DayMomentsTest检查日界/asOf、未来与计划过滤、步数只出现一次、时间轴缺口；DayDrumUiTest检查滑动、时间选择、详情、统计入口、状态恢复、记录增减、空态、大字体深色、横屏与连续透视帧。
- 生产组件渲染位于`docs/ui/v1.4.0/`；22–29为新增转筒/统计检查，其余为继承组件检查，其中旧分时段回顾不再是默认首页。
- 实现沿用固定Compose版本，不新增3D引擎、权限或后台任务。图片沿用原缩略图缓存。实际手势帧率与OEM耗电需真机测量。

官方API参考：[Pager](https://developer.android.com/develop/ui/compose/layouts/pager)、[graphicsLayer](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers)。
