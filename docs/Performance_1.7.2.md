# 1.7.2 性能优化

应用版本1.7.2，versionCode13。保留首页、成就、设置三个入口，以及原有双侧景深、横向时间轴、照片、历史日期、大事件门槛和记录口径。Room结构仍为V1；优化产生的日统计保存在现有SQLCipher数据库内，不增加明文生活记录文件。

## 六项改动

| 范围 | 实现 |
| --- | --- |
| 日期缓存 | 来源显示状态与内容失效分开。原始观察按标识和实际内容去重，重复查询只更新同步状态。数据变化仅使相关日期及事件计算依赖的相邻日期失效；历史快照在有效策略标记下可跨进程复用。当天时钟推进使用只读投影，不重复扫描原始观察、重写日汇总，也不把未知结束时间扩展成已发生时长。策略、删除、撤权、时钟倒退和新生效的排除仍触发必要重建。 |
| 首页聚合 | `HomeProjectionCache`在Default调度器中生成大事件，最多缓存三项。首屏同时发布日视图和聚合；门槛、日期、数据版本或可见时间变化后重新计算。详情复用相同结果，界面不再同步运行`dayChapters()`。可选华为组件在首屏之后初始化。 |
| 成就 | 每天保存精简贡献值，读取归档索引中的版本与时间，仅重新解析有变化的日视图。跨日照片、收藏、地点去重、连续天数和不足一分钟的累计仍由原规则处理。历史解码与计算不持有仓库锁或写事务；提交时重新核对策略、设置及归档索引，发生并发变化会重试。删除、健康撤权和恢复会清除相关派生缓存。 |
| 来源探测 | 普通前台恢复的探测共用30秒冷却；手动刷新、权限结果、策略代次变化立即探测。来源探测并行隔离，来源同步分别设定等待上限。过期策略的探测结果不会覆盖新状态。系统自身的同步Binder调用仍受设备实现影响，不能将协程超时解释为强制终止系统服务。 |
| 图片 | 缩略图按实际显示尺寸分档，最高1024像素；当前卡片优先，相邻卡片预取，远处卡片暂缓。两个工作线程、有相同请求的互斥合并、可取消队列及平台CancellationSignal共同限制重复工作。尺寸和媒体版本参与缓存键，媒体更新仅使相应URI失效；保留24MB总上限和内存压力回收。 |
| 发布与测量 | 发布包包含应用专属Baseline Profile和更窄的Startup Profile，由R8处理并写入APK。源码中首批规则按实际调用路径选取，明确区别于设备采集结果。独立benchmark模块可采集真实启动、时间轴转动、切日和成就/设置切换路径；普通发布构建直接消费已保存规则，不要求连接手机。 |

## 性能配置与复测

配置文件位于`app/src/main/baselineProfiles/`。`benchmark`只在显式传入`-Pwo.benchmarks=true`时启用，不进入正式APK。`benchmark`应用变体使用独立调试证书，适合专用测试设备，不用于覆盖个人签名安装包。

采集时关闭该变体的R8，保持非调试与可分析；测量时使用开启R8的同一业务实现。性能测试先通过公开系统入口配置专用设备，并只操作明确标识的虚构演示时间轴，不访问本人生活记录。

```bash
# 编译性能测试APK
./gradlew -Pwo.benchmarks=true -Pwo.profileGeneration=true :benchmark:assemblePersonalOfflineBenchmark
# 在专用Android 34+设备采集实际路径
./gradlew -Pwo.benchmarks=true -Pwo.profileGeneration=true :benchmark:connectedPersonalOfflineBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.personal.wo.benchmark.BaselineProfileGenerator
python3 scripts/import_profiles.py benchmark/build/outputs
# 使用真实手机测量冷/温启动与转动帧耗时
./gradlew -Pwo.benchmarks=true :benchmark:connectedPersonalOfflineBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.personal.wo.benchmark.StartupAndTimelineBenchmark
```

MainActivity通过`ReportDrawnWhen`标记首页数据和事件聚合已经可用的时刻，因此启动测试可以区分初次绘制与完整首屏，而不是把加载提示出现视为首页准备完毕。冷启动、短暂后台返回、长时间离开后同步分别统计中位数与P95；后台同步完成时间与首屏可操作时间分别报告。模拟器用于采集和交互回归，耗时、帧率和耗电结论以目标手机为准。

## 一致性与验证

新增回归覆盖：未修改日期不读取原始观察；重复观察不改写内容；进程重新初始化后复用有效快照；过期权限探测不恢复旧状态；图片按URI和尺寸失效；成就缓存命中不读取完整归档、单日修正只重新读取一天；策略变更、撤销证据、跨日去重和时间边界继续遵循原规则。

完整测试和Release检查完成后记录在[验证报告](Verification.md)。没有目标手机的实测数据，不填写虚构的提速百分比、启动秒数或耗电成绩。
