# “我”Android个人生活记录应用详细设计说明书

版本：V1.0（开发基线定稿）  
日期：2026年10月15日  
适用对象：产品、Android开发、算法实现、测试及本人验收  
交付性质：工程设计与开发契约；不是已经编译、安装或经过真机验收的APK。

## 1. 项目定义与冻结决策

### 1.1 产品目标

“我”自动整理本人一天在手机及现实环境中留下的可观测痕迹，打开应用即可看到当天的时间结构、去过的地点、移动、数字活动、媒体播放、生活线索和照片，并能按时间回放、查看依据、纠正与删除。用户无需写日记、启动每段活动计时或每天补填内容。

产品的主对象是“已经发生的一天”，不是笔记、任务清单、聊天机器人或效率评分。全授权用于扩大可观察范围，不等于所有传感器持续运行。设计优先级固定为：正常使用手机 > 数据正确性 > 连续记录 > 记录细节 > 文案丰富度。

### 1.2 本版必须实现的范围

| 编号 | 冻结决定 | 开发含义 |
|---|---|---|
| D01 | Android原生、本人自用、侧载签名APK | 不做账号、社交、广告、运营后台或商店审核流程 |
| D02 | 默认本地采集、本地分析、本地存储 | 未开启联网增强时，数据处理没有业务网络依赖 |
| D03 | 一次配置，长期自动运行 | 系统授权完成后，无需每天启动记录；恢复机制见第8、16章 |
| D04 | 核心页面为“今天、回看、规律” | 今天直接显示结果，不先显示输入框或空白编辑器 |
| D05 | 充分使用有价值的授权 | 定位、使用统计、通知、媒体、照片、日历、活动与健康纳入成品 |
| D06 | 不持续录音、录像、截屏、扫描屏幕全文 | 避免麦克风争用、明显耗电、存储膨胀和操作干扰 |
| D07 | 不依赖Root、设备所有者、隐藏API或保活漏洞 | 普通Android应用身份运行；不替换默认短信、电话或桌面 |
| D08 | 本地确定性事件引擎为事实源 | 大模型不参与计时、不直接写入事实表，不是运行必需条件 |
| D09 | 缺失与推测可见 | 禁止把没有收到数据解释为没有活动；禁止补造路线与经历 |
| D10 | 资源预算为发布门槛 | 超预算自动降级，不以“权限已经开启”为理由持续高负载 |

本版包含真正可工作的无Google服务基础路径：Android框架定位、系统使用统计、通知、MediaStore、日历与硬件传感器。Health Connect、活动分类增强等按能力启用。不能运行Android APK的设备不属于本版部署目标；品牌相同不代表系统能力相同。

### 1.3 可行性结论与必要边界

在正常授权、系统服务可用且未被用户强制停止的条件下，主要地点、应用活动、通知线索、照片与媒体片段可以组成自动日视图。Android提供相关公开接口，但沙箱仍隔离其他应用私有数据；“全部可授予权限”不是系统签名权限，也不是读取完整聊天数据库的能力。[S06][S07][S08][S10][S13][S29]

成品承诺是“持续整理可取得的生活证据”，不是完整记录人的所有行为。手机放在桌上不等于本人始终在桌旁；电话未使用不等于睡眠；日历会议不等于实际参会。所有页面、算法与测试均采用这一事实边界。

### 1.4 明确不纳入V1.0

不实现全天环境音转写、通话录音、键盘记录、剪贴板监听、屏幕视频、微信/支付宝私库读取、自动心理诊断、交易账本、电脑客户端、多人监控与云端账号同步。这些不是默认关闭但暗中采集的功能，而是不在本版代码路径中实现。短信与通话日志仅保留能力适配接口，基础成品不依赖它们。

## 2. 成品体验与功能需求

### 2.1 完整使用闭环

首次启动：展示明确标注的演示日视图 → 说明自动记录机制 → 分组完成系统授权 → 自动探测可用来源 → 可选标记家与办公地点 → 点击“开始记录”。此后以静默状态通知表示记录正在进行，不反复提醒补日记。

日常：数据自动到达 → 增量整理片段 → 当天视图不断更新 → 用户打开时先显示已有结果，再补采与重算。跨日自动创建新一天，昨日状态从“进行中”转为“已整理”；迟到数据到达后仍可修订，保留版本号。

纠正：点某个片段 → 选择更正类型、地点或边界 → 立即更新该片段及相关统计。删除：选择时间范围或单个片段 → 预览影响 → 统一删除并阻止再次导入。

### 2.2 功能需求清单

| 编号 | 成品功能 | 必须满足的结果 |
|---|---|---|
| FR01 | 今日概览 | 显示截至时间、2—4句事实摘要、主要地点及当日片段 |
| FR02 | 多轨时间轴 | 地点、活动、数字使用、生活片段共享一条时间坐标 |
| FR03 | 足迹 | 显示停留与已采到的轨迹；缺失处断线，不伪装导航路线 |
| FR04 | 数字活动 | 应用会话、屏幕交互区间、使用总时长、切换事件可追溯 |
| FR05 | 生活线索 | 从通知中提取订单、行程、会议提醒等候选事件，保留来源口径 |
| FR06 | 媒体片段 | 记录可用媒体会话的播放区间、标题、来源，不干预播放 |
| FR07 | 照片关联 | 自动索引新增照片，按真实拍摄时间关联，不按扫描时间虚构拍摄 |
| FR08 | 活动与健康 | 步数、运动、睡眠来自可用来源；来源缺失则明确隐藏或标缺失 |
| FR09 | 日程叠加 | 计划与事实分层，日程取消或修改能同步反映 |
| FR10 | 一天回放 | 时间游标驱动片段与足迹变化，无需生成或播放一段视频 |
| FR11 | 回看与搜索 | 日历跳转、日期范围、地点、类型及中英文关键词搜索 |
| FR12 | 规律 | 仅基于覆盖足够的历史样本生成有限的个人变化描述 |
| FR13 | 修正与溯源 | 每条结论可看依据；修正不破坏原始观察记录 |
| FR14 | 暂停与排除 | 一键暂停；支持时段、应用和来源排除；补采不能绕过 |
| FR15 | 导出与恢复 | 加密完整备份、可读日摘要导出、恢复预检及回滚 |
| FR16 | 数据健康 | 来源可用性、最近成功时间、缺失区间、资源状态可查看 |
| FR17 | 长期运行 | 重启、升级、掉权限、断网、时钟变化有确定处理路径 |
| FR18 | 不干扰使用 | 无悬浮层、无声音、无振动、无抢焦点、无后台拉起页面 |

### 2.3 展示示例与语言口径

示例数据必须使用独立演示数据库并明显标注“演示”，不得混入实际记录。以下文案仅为产品样例：

“截至21:30，今天主要在家与办公地点附近活动。记录到两段移动和一段28分钟的步行候选；晚间媒体播放约35分钟。下午有一段位置数据不足。”

允许：“检测到支付成功通知”“有会议安排”“设备记录到步行”“某应用处于前台”。不允许：“你已用餐”“你开车回家”“你完成工作8小时”“你今天焦虑”，除非用户明确确认了对应事实，且标注为“本人确认”。

## 3. 信息架构与界面规格

### 3.1 页面与导航

| 页面ID | 页面 | 内容与导航规则 |
|---|---|---|
| UI01 | 今天 | 顶部日期与状态；摘要；四轨总览；3—8个重点片段；查看全部 |
| UI02 | 日详情 | 全日时间轴、足迹、数字活动明细；可切换任何日期 |
| UI03 | 片段详情 | 时间、类型、地点、来源、证据状态、关联照片、修正与删除 |
| UI04 | 回看 | 月历和日摘要列表；点日期进入UI02；记住上次滚动位置 |
| UI05 | 搜索 | 输入后300ms防抖；默认近90天；支持全历史主动查询 |
| UI06 | 规律 | 最多3条可解释发现，每条显示样本、比较期、缺失情况 |
| UI07 | 数据与设置 | 来源、排除、存储、导出、隐私、资源预算、诊断 |
| UI08 | 首次设置 | 授权进度、来源检测、开始记录；中途退出可继续 |
| UI09 | 记录状态 | 正在记录、降频、暂停、缺权限、等待解锁或等待用户启动 |

一级底栏固定为“今天 / 回看 / 规律”；设置由右上入口进入。详情页返回必须恢复来源页的日期、筛选条件及滚动位置。外部分享进入应用时不改变原有记录状态。

### 3.2 今天页布局

宽度小于600dp采用单列。页面左右16dp，内容区最大840dp并居中；顶部日期28sp，主标题22sp，正文16sp，辅助文字13sp。控件触摸区域至少48dp。正文支持系统字体放大到200%，不通过固定高度裁掉标题与按钮。

上方顺序固定：日期和状态 → 截至时间 → 摘要 → 时间总览 → 重点片段。摘要最多4句；重要缺失提示紧邻总览，不用红色大面积警报。未授权或不支持的来源展示原因，不显示误导性的“0”。

四轨总览高度约200—240dp，只负责看结构；过短片段以密度或标记表示，不硬塞文字。点击总览进入可缩放日详情。底部重点片段以纵向叙事列表展示，避免一屏十几张互不相关的统计卡片。

### 3.3 时间轴与手势

日详情采用纵向时间坐标。默认1分钟对应0.8dp，可放大到2dp或缩小到0.4dp；缩放锚点为手指对应时间。时间标签宽48dp，其后依次是地点、活动、数字、生活轨道。单轨不足时通过横向切换，不压缩到无法点击。

只渲染视窗及前后各一屏。数据切成小时桶，LazyColumn的key固定为日期、时区与桶起点；桶内使用Canvas绘制。绘图对象外提供语义节点与文本替代，不能让TalkBack只读到“一张图”。

回放由用户点击后开始，提供播放/暂停、速度、拖动游标。默认一次播放约45秒，静音、不振动。页面不可见立即停止帧循环；系统减少动画设置开启时默认采用静态游标跳转。

### 3.4 视觉系统与状态

基础采用浅色纸白背景、深色正文、低饱和轨道颜色，支持暗色模式。轨道除颜色外同时用图标、线型和文字区分：实线代表来源记录，虚线代表推测，空白阴影代表缺失，空心标记代表计划。

所有列表必须有加载、空数据、权限受限、数据缺失、部分成功和错误重试状态。首页缓存存在时禁止整屏转圈；只在受影响组件显示刷新状态。任何后台修订不得把用户正在查看的日期切回今天。

## 4. 总体技术架构

### 4.1 分层与依赖方向

数据源适配器 → 采集控制与排除过滤 → 本地观察记录 → 事件分段与关联 → 指标/摘要/检索索引 → 只读页面模型 → Compose界面。

数据源不直接更新界面；大模型不直接写入事件；界面不直接查询系统Provider。全部来源采用统一时钟、质量标记、去重与删除规则。单进程、单数据库写入通道，避免多个常驻进程增加内存与锁争用。

| 模块 | 责任 | 禁止事项 |
|---|---|---|
| :app | 应用入口、Manifest、前台服务、系统回调、手动依赖装配 | 不写事件识别规则 |
| :core:model | ID、时间、来源、片段、状态与错误类型 | 不依赖Android UI |
| :core:data | Room、SQLCipher、DAO、游标、事务、迁移 | 不执行模型推理 |
| :core:engine | 清洗、分段、融合、指标、摘要与来源解释 | 不请求系统权限 |
| :core:policy | 暂停、排除、资源预算、保留期、处理代次 | 不由来源自行绕过 |
| :collector | 各系统来源适配器、权限探测 | 不修改其他应用数据 |
| :feature:today/history/insight/settings | 页面状态、交互、展示 | 不在主线程解析大批数据 |
| :test-support | 虚拟时钟、来源回放、固定样本、性能脚本 | 不打包真实个人数据 |

首个工程可先采用同一app模块内的同名包结构；对外接口必须保持，避免为了模块数量过早拆分。

### 4.2 线程与队列

系统回调仅提取必要字段并投递任务，主线程回调目标P95小于5ms。数据库写入由单一协程actor串行完成；规则计算并发度1，媒体处理并发度1。默认不建立常驻大模型实例。

内存队列上限512条；普通来源批量100条或5秒提交一次，通知正文批次最多停留2秒。队列达到80%时合并可覆盖的状态更新；不可重放的通知仍保留最新必要记录。无法保存时生成“丢弃计数＋原因”，不能悄悄宣称完整。数据库错误不得导致主线程阻塞或无限重试。

## 5. 工程基线与构建规范

### 5.1 固定版本

本版选择下列已发布版本作为兼容性基线，不宣称它们全部是最新版本。compileSdk和targetSdk固定为36，Android 17纳入运行兼容测试；不为追逐新SDK在首版引入额外接口。AGP 8.13系列的支持范围、Gradle与JDK要求见官方说明。[S20]

| 项目 | 固定值 | 说明 |
|---|---|---|
| applicationId / namespace | com.personal.wo | 安装与升级保持不变 |
| minSdk / compileSdk / targetSdk | 29 / 36 / 36 | Android 10及以上，运行验证覆盖API 29—37 |
| Java / JVM target | 17 / 17 | 开发机JDK 17 |
| Android Gradle Plugin / Gradle | 8.13.2 / 8.13 | 使用Gradle Wrapper并提交校验配置 |
| Kotlin / Compose编译插件 | 2.2.21 / 2.2.21 | 版本一致；不叠加旧Compose compiler配置 |
| Compose BOM | 2025.10.01 | UI库由BOM统一管理 |
| Room | 2.8.4 | 使用KAPT编译器，避免混用两套代码生成器 |
| WorkManager | 2.10.5 | 非精确增量补采与维护 |
| Health Connect | 1.1.0 | 运行期检测Provider与feature可用性 |
| SQLCipher Android | 4.10.0 | net.zetetic:sqlcipher-android；16KB真机/模拟器验收 |
| androidx.sqlite | 2.5.2 | Room 2的SupportSQLite接入路径 |
| Activity / Lifecycle / Navigation | 1.11.0 / 2.9.4 / 2.9.5 | 原生Activity、ViewModel和Compose导航 |
| Coroutines / Serialization JSON | 1.10.2 / 1.9.0 | 长度受限JSON与结构化并发 |
| Tink Android | 1.18.0 | 流式认证加密备份；不自行设计加密格式原语[S37] |

Kotlin、Compose、Room、WorkManager和Health Connect版本记录分别见[S21]—[S24]、[S32]。SQLCipher选用新的sqlcipher-android包，不使用旧android-database-sqlcipher包；其Room集成方式见[S16]，16KB兼容性以最终APK检查为准。[S31][S33]

依赖升级必须单独提交并重跑迁移、16KB、耗电和7日稳定性测试。发布前扫描依赖已知漏洞；有适用的高风险缺陷时升级后重新锁定。不得使用“+”“latest.release”或未锁定的远程脚本。

### 5.2 构建变体

personalOffline：默认成品，移除INTERNET权限，不包含联网地图、云端模型或Google服务SDK；使用框架定位和本地算法。Health Connect调用是本机Provider交互，不要求本App自行联网。

personalEnhanced：保留同一核心能力，增加INTERNET权限和用户配置的模型端点；联网只能由明确任务触发。Google服务增强适配器属于独立可替换实现，不是完成离线版本的先决条件。两个变体使用同一签名与数据格式，安装覆盖前验证迁移。

生产包启用R8、资源压缩，禁用debuggable，不申请largeHeap。发布arm64-v8a APK；x86_64仅用于测试。个人设备若只有32位运行环境，应另外构建armeabi-v7a，不能把模拟器通过当作设备兼容。

## 6. 权限与能力接入

### 6.1 一次配置与运行时能力表

应用按照本人愿意充分授权设计。首次设置将可用权限按系统要求顺序申请，完成后自动启用相应来源；不在每天启动时重复索取。授权是否存在、接口是否可用、数据是否存在是三个独立状态。

| 来源 | Android声明或入口 | 授权后用途与失败降级 |
|---|---|---|
| 前台/后台定位 | ACCESS_COARSE_LOCATION、ACCESS_FINE_LOCATION、ACCESS_BACKGROUND_LOCATION | 前台位置先申请，后台位置按系统设置授权；仅大致位置时降低空间精度 |
| 前台服务 | FOREGROUND_SERVICE、FOREGROUND_SERVICE_LOCATION、FOREGROUND_SERVICE_HEALTH | 只启动与实际位置/步数记录匹配的类型 |
| 状态通知 | POST_NOTIFICATIONS（API 33+） | 一条低重要性记录状态通知；不播放提示音 |
| 应用使用 | PACKAGE_USAGE_STATS与ACTION_USAGE_ACCESS_SETTINGS | 查询UsageEvents；未开时该轨缺失，不伪造前台应用 |
| 其他应用通知 | 在服务上声明BIND_NOTIFICATION_LISTENER_SERVICE | 用户打开通知使用权；不是READ_SMS，也不是完整聊天历史 |
| 活动/步数 | ACTIVITY_RECOGNITION | 硬件计步与低功耗运动信号；无传感器时不进行加速度计全天采样 |
| 照片/视频索引 | API 33+ READ_MEDIA_IMAGES/VIDEO；API 29—32 READ_EXTERNAL_STORAGE | 自动扫描授权媒体；选定照片模式只索引可访问部分 |
| 图片位置 | ACCESS_MEDIA_LOCATION | 需要照片原始位置时读取；无该权限仍可按时间关联 |
| 选定媒体访问 | READ_MEDIA_VISUAL_USER_SELECTED（API 34+） | 正确识别部分图库授权，不能当作全图库 |
| 日历 | READ_CALENDAR | 查询Events/Instances；只读，不修改日程 |
| 健康 | health.READ_STEPS、READ_EXERCISE、READ_SLEEP | 检查Health Connect状态和数据类型；不要求有手表 |
| 健康后台读取 | health.READ_HEALTH_DATA_IN_BACKGROUND | feature可用且单独授权才后台读，否则打开应用时读 |
| 窗口切换增强 | 在服务上声明BIND_ACCESSIBILITY_SERVICE | 仅包名/窗口状态元数据；不读控件树，不执行手势或自动点击 |
| 重启恢复 | RECEIVE_BOOT_COMPLETED | 首次解锁后恢复状态；遵守前台服务启动条件 |
| 电池与连接状态 | ACCESS_NETWORK_STATE；系统电池/热状态接口 | 调整调度，不主动扫描Wi-Fi/蓝牙 |
| 联网增强变体 | INTERNET | 用户配置的模型请求；离线变体不存在此权限 |

BIND_*属于系统绑定服务时检查的权限，不能作为普通uses-permission申请。位置、媒体和健康能力的具体限制分别见[S01][S12][S13][S26]。附带的Manifest是组件与权限参考文件，类与资源需要按工程接口实现，不是完整App。

### 6.2 特殊访问处理

使用统计、通知使用权、无障碍与后台启动设置由引导页逐项跳转，返回时重新探测。用户打开无障碍增强后，配置canRetrieveWindowContent=false，不监听文本变化；其作用是减少UsageEvents刷新前的实时延迟，不是读取应用正文。[S14]

系统若对侧载应用的特殊访问设置增加确认，显示系统入口说明，不编写自动点击或绕过逻辑。READ_CALL_LOG和READ_SMS是受安装器允许列表影响的限制权限，不把一次普通授权当作必然可用；本版不更改默认电话或短信角色来获取数据。[S27]

### 6.3 来源状态定义

source_state必须分别保存：capability（SUPPORTED/UNSUPPORTED）、authorization（GRANTED/PARTIAL/DENIED）、runtime（RUNNING/PAUSED/WAITING_UNLOCK/STOPPED/ERROR）、freshness（FRESH/STALE/NO_DATA）。例如“已授权但无睡眠来源”应显示NO_DATA，而不是ERROR或0小时。

每次进入前台、来源抛出SecurityException、应用升级、系统启动后重新探测。权限丢失立即撤销对应订阅并产生覆盖缺口，其他来源继续工作。

## 7. 数据采集器详细设计

### 7.1 共用采集契约

每条观察至少包含sourceId、sourceKey、kind、startMs、endMs、receivedMs、deviceId、bootId、elapsedRealtimeNs（能获取时）、eventZoneId、quality、schemaVersion和payload。所有区间使用[start,end)，瞬时观察使用start=end；展示片段必须为正时长或显式point类型。

sourceKey优先来自系统原生记录标识；没有稳定标识时，对来源、事件时间、包名、类型和规范化载荷做带本地密钥的HMAC。数据库对(source_id,source_key)唯一约束。重复扫描只更新同一观察的版本或忽略，不重复计数。

采集器需实现probe、start、stop、pullSince；不能历史补采的来源将pullSince明确返回UNSUPPORTED。每次写入先检查暂停区间、排除规则和处理代次generation。载荷字段由source_payloads.schema.json限定；复杂睡眠阶段等超长细节优先省略并标DETAIL_TRUNCATED，保留来源的session摘要，不能突破单条8KB或编造缺失阶段。

### 7.2 定位采集器

默认FrameworkLocationProvider使用LocationManager，先检测可用Provider；网络定位可用时承担低功耗采样，PASSIVE_PROVIDER接收其他应用已经产生的位置。GPS只在需要且资源预算允许时短时启动。无网络定位不代表GPS不可用；但室内没有有效位置时不得无限等待。[S06]

| 模式 | 请求策略（非准点保证） | 额外限制 |
|---|---|---|
| 已确认停留 | 网络/低功耗位置约15分钟、最小位移250m | 保持被动订阅；不持续GNSS |
| 停留候选 | 约3分钟、最小位移50m，最多12分钟 | 用于确认新地点，超时后降频 |
| 移动 | 低功耗位置约2分钟、最小位移100m | 允许5—10分钟批量回调 |
| 位置过旧且有移动证据 | 单次GNSS最长20秒 | 两次至少10分钟；每小时120秒、每日600秒硬预算 |
| 低电/高温 | 被动位置＋已有缓存，主动请求降至约30分钟 | 不启用GNSS补点 |
| 精细回放需求 | 用户在前台主动刷新当前位置 | 不自动升级为全天高精度追踪 |

LocationManager旧API没有请求批量参数时，使用其公开兼容接口，不能编造统一的批处理能力；统一的是策略意图与返回模型。请求间隔并不保证实际回调频率。合理选择精度、频率和批量策略是定位耗电控制基础。[S05]

过滤规则：无效经纬度、非正精度、异常未来时间、明显不合理跳点进入隔离/低质量状态。记录精度米数、Provider、位置年龄及mock标记。测试模拟位置只进入debug样本；普通模式遇到mock位置标记来源，不直接作为真实行程。

轨迹只连接时间差不超过5分钟且质量合格的点；更稀疏的位置用于地点关联或以虚线标识“路径未知”。默认不计算道路里程，不能将两点直线距离称为实际行驶里程。坐标保存coordinateSystem字段，框架/GNSS路径按WGS84处理，未知Provider不可盲目假定与国内地图坐标一致。

### 7.3 步数与活动采集器

有TYPE_STEP_COUNTER时保存原始累计值与bootId；差分只在同一设备、同一启动周期且前后值单调时成立。跨重启、计数器变小、停采后首次读数只设置新基线，不生成负步数或补出暂停期间步数。[S11]

TYPE_SIGNIFICANT_MOTION存在时用一次性运动触发信号帮助从停留切换到移动候选，触发后按策略重新注册。没有低功耗传感器时不使用50Hz加速度计兜底。无Google服务的基础版只提供移动/可能步行，不强求跑步、骑车与乘车精分类；Google活动识别属于可替换增强。[S30]

步数仅可精确归属于被记录的采样区间。跨午夜长区间无法可靠拆分时优先采用健康聚合值；没有聚合来源则标“跨日待分配步数”，不能按时长均分后称为精确步数。

### 7.4 应用使用与屏幕活动采集器

UsageStatsManager增量查询cursor前5分钟至now，重放重叠窗口保证事件边界恢复；首次仅查询当天及前一日必要边界，不全量扫描几个月。每次最大查询窗口6小时，逐窗口推进，只有事务提交成功才更新游标。[S07]

关注ACTIVITY_RESUMED、ACTIVITY_PAUSED、ACTIVITY_STOPPED、SCREEN_INTERACTIVE、SCREEN_NON_INTERACTIVE、KEYGUARD_SHOWN/HIDDEN及设备启停事件。包内Activity变化不能算应用切换；系统桌面、输入法和本应用单独分类。启动后尚未首次解锁时，UsageEvents可能不可读，记WAITING_UNLOCK，不推进游标。[S07]

分屏与画中画可能同时存在多个活动。分别存储app处于RESUMED状态的已观察活动区间，用户“手机使用总时长”取屏幕交互且解锁区间内的所有应用区间并集；不得累加出超过屏幕使用时间的总数。没有足够屏幕事件时标为“已观察应用活动时长”，不冒充精确屏幕时长。

无障碍增强只记录窗口包名和时间，500ms合并同包重复事件；UsageEvents补采后进行时间对齐，不双重计数。无法确定焦点的分屏区间显示“同时使用”，不随意指定唯一主应用。

### 7.5 通知与生活线索采集器

NotificationListenerService监听已发布、更新、移除事件。只读取必要的包名、postTime、key、category、groupKey、标题和文本；不读取RemoteViews位图，不触发PendingIntent，不清除、回复或改变其他应用通知。[S08]

通知原文处理上限：标题256个Unicode字符、正文2048字符，规范化后每条不超过8KB。排除系统分组摘要、自身状态通知、下载进度高频更新；同key相同内容更新去重。MessagingStyle可提供消息片段但不保证完整，默认“通知片段数”而非“真实消息总数”。

生活线索解析采用本地有界规则：交通出票/出发提醒、物流送达、预约提醒、会议提醒、交易成功提示。初次授权向导要求本人勾选用于线索解析的来源应用，未选择的来源只记录通知元数据，不解析正文类别。每条规则声明来源包允许列表、关键短语、时间/金额字段约束与输出类型。通知可写“收到送达通知”，不能直接写“已取快递”；交易提示不是经核验账本。支付、行程等日期均先解析显式日期，无日期才按通知时间解释并标低确定性。

验证码、口令、完整卡号与身份号码在持久化前过滤；遇到平台脱敏结果只保留来源与事件类型，不绕过。Android 15对不受信任通知监听器提供的部分OTP内容进行脱敏。[S09] 短文本解析失败保留通知元数据，不触发云端重试。

### 7.6 媒体会话采集器

在通知使用权已开启时，通过MediaSessionManager获取允许访问的活动会话并注册MediaController.Callback。记录STATE_PLAYING、暂停、停止、标题、作者和来源包；不申请音频焦点，不调用播放控制，也不轮询播放进度。[S10]

只计算处于PLAYING的观察区间，缓冲和暂停不计入。媒体服务消失或进程中断时截断为边界不确定，不把最后一首歌延长至下一次启动。界面使用“媒体播放”，不保证用户确实收听、看完或专注。

### 7.7 照片与视频索引采集器

MediaStore的ContentObserver只作为“有变化”信号，合并后增量查询；它不是进程死亡后的历史日志。API支持时组合MediaStore版本与generation游标，Provider版本变化则重建索引；不支持时采用DATE_MODIFIED重叠窗口加定期有限对账。[S13]

初次仅扫描最近7天元数据，按200条分页；历史补索引由用户选择日期触发，充电空闲执行。日常每批最多200条。只存content URI、原生ID、时间、尺寸、方向、媒体类型与必要质量信息，不复制全图库。

拍摄时间优先使用可信EXIF日期及offset，其次DATE_TAKEN；来源只有导入/修改时间时标“添加的图片”，不推断拍摄时刻。URI访问失败显示“原文件不可访问”，保留合法的片段文字并允许解除关联。

缩略图在用户查看或充电空闲时生成，最长边512px，并发1，磁盘缓存最多200MB、内存最多24MB；不默认解析全部视频帧，不开启全图库OCR。照片聚类按20分钟时间窗口并结合可用位置；连拍相似图用缩略图dHash去重，展示每组最多3张。

### 7.8 日历采集器

使用CalendarContract.Instances查询已选日历在指定时间范围内的实例，保留原事件ID、实例起点、时区、全天标记、状态和更新哈希。初次范围为前7日至后7日，之后按变化与每日对账更新。[S28]

普通事件使用绝对时刻；全天事件保留日期语义，不能按UTC偏移误移到前一天。取消或删除日程移除计划层，并使相关摘要失效。日程只生成PLANNED事件，与实际观察通过relation关联，不自动升级为CONFIRMED。

### 7.9 健康采集器

使用Health Connect读取StepsRecord、ExerciseSessionRecord与SleepSessionRecord，后台读取必须先检查feature与独立授权；不可用时改为打开应用时同步。[S12][S32]

采用变更游标记录更新和删除，游标过期时重扫最近7天；读取不到更早历史时不无限重试。日步数优先使用平台聚合及其来源去重机制；不再叠加本地步数。平台聚合不可用时使用用户选定主来源，不把多设备原始记录直接相加。

睡眠保留来源、完整session及阶段可用性；显示“记录的睡眠区间”或来源提供的睡眠指标。不得用session的起止跨度冒充去除清醒后的实际睡眠时长。没有睡眠来源，只允许出现“手机长时间未交互”，不写“睡眠”。

### 7.10 采集时间与迟到数据

存储UTC毫秒及事件所在时区。默认归档时区在首次设置时取设备时区并固定；旅行时显示“当前按X时区归档”，用户可主动切换视图时区。时区变更不修改原始时间。

同一boot内用elapsedRealtime辅助检测系统时钟跳变；绝对时间用于跨来源关联，单调时间用于同设备短区间持续时间。无法校正的时间冲突进入质量缺口，不生成负时长。归档窗口采用LocalDate.atStartOfDay(zone)到下一日开始，不硬编码一天86400000ms。

## 8. 后台生命周期、调度与保活

### 8.1 前台记录服务

RecordingService只在真实位置记录或硬件步数记录开启时运行，按实际用途传入location/health类型；二者均关闭时停止，不借用location类型单纯保活。API 34+声明并检查对应类型权限，低版本使用兼容分支。[S01]

用户在前台点击“开始记录”后启动服务，onCreate立即建立低重要性notification channel并及时startForeground，再异步打开数据库、注册来源。通知固定显示“我正在整理今天”，提供“暂停”和“查看今天”；不显示敏感地点、通知正文、声音、振动或全屏意图。

服务存在并不等于CPU全天运行。禁止永久PARTIAL_WAKE_LOCK、空音频保活、1像素Activity、重复拉起服务、精确闹钟保活。Android的后台启动限制和服务类型时限需遵守；尤其不能把dataSync服务当作24小时记录服务。[S02][S03]

### 8.2 状态机

| 当前状态 | 触发 | 下一状态与动作 |
|---|---|---|
| STOPPED | 前台开始且能力可用 | STARTING → RUNNING；注册来源并执行增量补采 |
| RUNNING | 用户暂停 | PAUSING → PAUSED；写入排除起点、generation+1、取消任务并释放来源 |
| PAUSED | 用户恢复 | 关闭排除区间；generation+1；重新订阅；不补回暂停期间 |
| RUNNING | 低电/高温/预算耗尽 | DEGRADED；保留被动来源，关闭主动GNSS和重任务 |
| DEGRADED | 条件恢复并稳定10分钟 | RUNNING；渐进恢复，不能补发所有过期任务 |
| 任意 | 权限失效 | 对应来源STOPPED；其他来源继续；记录覆盖缺口 |
| 任意 | 尚未首次解锁 | WAITING_UNLOCK；不读取凭据加密库，不采个人正文 |
| 意外退出 | 下次合法启动 | RECOVERING；补可重放来源，不可重放区间记缺失 |
| 任意 | 用户强制停止 | OS_STOPPED；等待用户重新打开，不承诺自动突破强停 |

### 8.3 周期工作与唯一任务

| Worker | 调度意图 | 约束与上限 |
|---|---|---|
| IncrementalPullWorker | 约30分钟一次；打开应用时可追加一次 | UNIQUE KEEP；每次最多20秒计算，超量续接游标 |
| DayBuildWorker | 有有效变更后合并触发，至少间隔5分钟 | 重算脏时间范围；每次最多2秒CPU目标 |
| MediaIndexWorker | 媒体变化合并；定期兜底对账 | 每批200条，只读元数据；缩略图重活另调度 |
| HealthPullWorker | 有后台授权时约60分钟 | 无后台权限则仅前台同步 |
| MaintenanceWorker | 每日一次机会性执行 | 充电、电池不低、存储不低；10分钟内中断保存进度 |
| InsightWorker | 每周样本充分时执行 | 充电空闲；统计先行，不调用模型做聚合 |
| OptionalSummaryWorker | 日结构变化且用户已配置增强 | 每日最多3次、单次30秒超时；网络故障不影响本地结果 |

WorkManager周期任务最短间隔15分钟，实际执行并不精确，本方案的30/60分钟均为调度意图。[S04] 不使用它保证准点采样或午夜准点生成。只要有唤醒事件或用户打开，就检查需要归档的日期并生成缺少的日视图。

### 8.4 退出与重启恢复

保存recordingRequested与最小调度状态。BOOT_COMPLETED到达后，未解锁只记待恢复状态；首次解锁后检查权限、来源和当前系统允许的启动路径。满足合法前台服务启动条件时恢复，否则发布可见的“点此继续记录”，由用户点击启动，不能抛异常后循环重试。[S02]

START_STICKY只作为系统允许的重建提示，不视为保证。onTaskRemoved不连环重启。升级后重新探测能力、迁移数据库、重注册监听。应用被用户强停期间，定位和已消失通知没有可靠补采途径；恢复时将该段标缺失。

## 9. 事件分段、融合与质量规则

### 9.1 处理流水线

按“排除过滤 → 标准化 → 去重 → 时间修复 → 来源内分段 → 跨来源关联 → 指标 → 摘要 → 展示模型”执行。每一步记录engineVersion、输入范围、输入摘要哈希与输出revision，重复输入必须得到相同规范化结果。

同一观察可以支持多个不同维度，但同维度计时必须去重。地点停留、应用使用、媒体播放可以同时发生，不组成一个能相加为24小时的饼图。

### 9.2 停留与移动算法

合格位置定义为accuracy不超过200m且时间有效；粗定位可保留，但不用于细粒度停留判断。候选停留至少3个点、跨度至少10分钟、相邻有效点间隔不超过20分钟，聚类半径R=max(80m,2×该组精度中位数)，最大300m。采用加权质心，权重为1/max(accuracy,20)^2。

连续两点处于R之外且间隔不超过5分钟才退出停留，抑制边缘漂移。停留起止保存可见边界与uncertaintyMs；缺少边界点时不能把10分钟精度标成精确到秒。离开同地点不足3分钟且无可靠移动证据可合并，合并过程保留漂移标记。

移动为两个停留之间有位移证据的区间；无有效中间点时只标“地点间移动，路线未知”。步行标签需要连续活动识别或可归属于该区间的步数与低速位移共同支持；仅速度不能稳定区分步行、骑行和拥堵乘车。

以上阈值是V1.0默认工程参数，集中于default_config.json并写入算法版本，不能散落在多个类中。调参必须重跑固定样本并记录差异，不宣称未经验证的准确率。

### 9.3 应用会话构造

仅使用公开的packageName、className、eventType和timeStamp维护活动状态；不调用非公开getInstanceId或反射隐藏字段。每个包名＋className保存RESUMED/PAUSED/STOPPED状态，重复同状态不增加计数，PAUSED后再STOPPED不二次扣减。包内无RESUMED活动才结束已观察活动会话；同包Activity跳转不会增加跨应用切换次数。相同className的多实例无法可靠区分时标INSTANCE_AMBIGUOUS，不宣称准确识别每个窗口。PAUSED不等于完全不可见，因此本指标称“应用活动”而非所有可见时长。[S38] 屏幕锁定则结束可交互区间。时长使用半开区间；恰好相接的同包片段可以合并，不跨屏幕锁定合并。

缺失结束事件时，只能截断到可靠的屏幕非交互、设备关闭或相邻已知边界；仍不确定则PROVISIONAL并显示“结束时间不确定”。不得把24小时前的前台事件直接延续到现在。应用总时长与手机总时长分别计算：前者可以与其他应用重叠，后者使用并集。

### 9.4 事实、推测与用户确认

| 状态 | 判定 | 允许的文字 |
|---|---|---|
| RECORDED | 明确来源提供了该类型记录 | “媒体播放”“通知提示”“健康来源记录” |
| INFERRED | 由可解释规则组合得到 | “可能步行”“办公地点附近停留” |
| CONFIRMED | 用户明确确认 | “本人确认：在开会” |
| PLANNED | 来自日历/预约计划 | “会议安排”“预约提醒” |
| UNKNOWN | 证据不足或互相冲突 | “信息不足”，不自动填其他标签 |

内部confidenceScore用于规则排序，不是经过统计校准的概率，界面不显示“95%确定”。每个推测保存ruleId、supportingEvidence、conflictingEvidence及不确定边界。日历与位置相符可以建立关联，但不能直接将会议标为事实。

### 9.5 生活片段排序与主题关联

重点片段由确定性评分选出：本人收藏+100、本人确认+40、新地点+20、独立活动≥20分钟+15、关联照片+15、与其他来源相符+10；重复普通通知-20、低质量-30。每天最多8个，至少分布于不同时间区间，不能全被晚间消息占满。

同地点、相近时间（默认±20分钟）的照片和通知可以关联，但关联关系不得改变源事件事实含义。例如“地点附近收到餐饮通知”不是“已在那里吃饭”。照片没有拍摄时间则不参加精确时间关联。

### 9.6 覆盖率与缺失

覆盖率按来源和维度分别计算，不生成一个虚假的“生活记录完整率”。应用来源采用成功查询并可恢复状态的区间；位置采用满足质量和最大间隔条件的支撑区间；通知采用监听连接区间，描述为“监听在线覆盖”而不是消息获取率。

分母是归档窗口开始至asOf，扣除明确未启用和用户暂停区间后的期望采集时长。覆盖分母为0返回null而不是100%。连续性推断跨越排除区间必须截断；查询成功但返回空并不能自动支持地点或睡眠结论。

### 9.7 迟到、修正与稳定ID

默认重算脏窗口前后各30分钟，跨午夜片段同时使相邻日期变脏。旧片段与新片段在同来源/类型、时间交并比≥0.5且无冲突时复用ID并revision+1；否则建立新ID和replaces关系。用户修正先按稳定ID匹配，再按来源键与时间锚点回附，无法唯一匹配时保留待确认，不能错误套给另一段生活。

删除、排除和权限撤回优先于所有重算；新摘要提交前必须验证generation与输入revision未改变。

## 10. 数据库与持久化设计

### 10.1 数据库结构

数据库采用Room 2＋SQLCipher，名称wo.db。附件contracts/schema.sql是规范DDL；Room Entity与Migration必须逐项对应，不能在Room创建后再次执行另一套建表脚本。日期视图是派生数据，原始观察与用户修正是重建依据。

| 表 | 主键/关键索引 | 内容与用途 |
|---|---|---|
| app_meta | key | 数据库版本、算法版本、设备标识、归档时区 |
| recording_policy | singleton_id=1 | requested、mode、generation、配置JSON |
| source_state | source_id | 能力、授权、运行状态、游标、最近成功、错误 |
| exclusion | id；source/start/end索引 | 暂停、删除时段、应用排除；防止重新导入 |
| observation | id；source＋source_key唯一；kind/start索引 | 原始标准化观察、质量、采集时间与保留期限 |
| place | id | 本人地点名称、坐标、半径、坐标系与确认状态 |
| episode | id；lane/start/end索引 | 来源内分段及跨来源展示片段、revision、事实状态 |
| episode_evidence | episode＋evidence_key | 证据关联、最小证据快照、原始记录可用性 |
| episode_relation | parent＋child＋relation | 照片/计划/通知关联及replaces关系 |
| annotation | id；episode_id索引 | 用户确认、改名、边界修正及撤销关系 |
| media_ref | id；source＋native_key唯一 | URI、媒体时间和索引状态，不存原图 |
| daily_metric | date＋zone＋metric_key | 有单位、范围、覆盖说明的确定性指标 |
| daily_summary | date＋zone | 摘要、事实引用、输入hash、生成状态、asOf与revision |
| source_coverage | id；source/start索引 | 在线、缺失、暂停、可重放范围 |
| event_search | FTS4虚表 | 规范化中文双字词与英文词元，不保存未过滤原文 |
| operation_log | id | 无正文的内部操作结果、删除代次与错误代码 |
| dirty_window | id | 待重算的时间范围、原因、generation |

### 10.2 字段与时间约束

ID使用随机UUID字符串，观察自增ID只作为本库关系标识；导入导出使用稳定sourceKey与external ID去重。所有毫秒值使用64位整数；时长、步数、字节数不能使用浮点累加。daily_metric用value_int保存整数指标，用value_real保存比值等非整数指标，两者不能同时非空；未知时两者均为NULL。金额若作为通知字段保存，使用币种＋最小货币单位整数，不能当作实际账本。

payload_json必须有schemaVersion、kind与限定字段，单条上限8KB；质量字段与事实断言状态分开。空间字段使用经纬度浮点与accuracy_m，禁止把GCJ-02与WGS84混合计算。day_date为YYYY-MM-DD，zone_id为IANA时区名，二者共同确定归档窗口。

### 10.3 事务规则

写入一批观察、更新来源游标和插入dirty_window必须同一事务成功；失败时游标不能先行。事件重算先计算候选，再以当前generation及输入revision为条件提交。摘要读取对应同一事务快照，避免显示新事件配旧统计。

所有写入串行；只在必要时开启数据库。开启WAL并设置busy_timeout，业务不在UI线程访问数据库。不做每次写入后VACUUM；空闲充电时checkpoint及分批清理。SQLCipher密钥初始化按库文档完成，不能把未经设置密钥的SQLite连接指向加密数据库。[S16]

### 10.4 保留期限与规模控制

| 数据 | 默认保留 | 到期动作 |
|---|---|---|
| 精细位置、应用原始状态事件 | 30天 | 保留整理后的片段与最小证据说明，删除原始细节 |
| 通知原始正文 | 7天 | 删除正文，保留已过滤的事件类型、必要标题及来源说明 |
| 日历缓存与健康原始缓存 | 30天 | 继续保留日指标和必要session摘要 |
| 活动片段、修正、关联 | 730天 | 到期前可导出；收藏片段不自动过期 |
| 每日摘要与汇总指标 | 长期 | 用户主动删除或明确变更期限 |
| 缩略图缓存 | LRU，最大200MB | 可重新生成，不影响事实记录 |
| 诊断日志 | 7天，最大10MB | 环形覆盖；无通知正文、精确坐标与密钥 |

到期清理与用户删除不是一回事。到期可保留经过最小化的证据快照，并标“原始明细已到期”；用户删除则必须删除相关快照、指标、摘要及索引。事件关联使用可空observation_id，是为了处理正常到期，不能依靠外键SET NULL代替用户删除的业务级清理。

数据库软阈值800MB，总业务空间预算约1GB。达到85%先清理可重建缓存和已过期原始细节；达到上限停止保存可选原始正文与新增缩略图，继续保存有限的基础状态与缺失说明。不得无提示删除收藏或把存储不足当作完整记录。

## 11. 接口、数据契约与错误码

### 11.1 应用内接口

本版不建立本地HTTP服务器，不开放局域网端口。模块间用Kotlin接口和Flow，外部仅有明确的文件导入导出与用户配置的增强请求。附件android/CoreContracts.kt给出可编译的纯Kotlin领域模型、时间工具与接口骨架。

| 接口 | 方法 | 输入/输出与行为 |
|---|---|---|
| SourceAdapter | probe、start、stop、pullSince | 返回能力及标准化观察；不能自行绕过policy |
| ObservationRepository | ingestBatch | 原子写入、幂等去重、更新游标、产生脏窗口 |
| DayRepository | getDay、refreshDay | 返回DayView；已有缓存优先，刷新异步 |
| EpisodeRepository | getEpisode、getEvidence | 返回同revision片段及来源解释 |
| RecordingController | start、pause、resume、stop | 命令串行，状态可观测，持久化后返回成功 |
| CorrectionService | apply、undo | 使用expectedRevision避免覆盖并发修改 |
| DeletionService | preview、execute | 先返回影响数，执行时校验generation及预览版本 |
| SearchRepository | search | 日期、类型、地点、关键词；分页50条，上限200 |
| BackupService | export、inspect、restore | 异步进度，取消后清理临时文件，完成后才提交 |
| SummaryComposer | compose | 输入确定性事实，输出带引用的摘要，不创建原始事件 |

### 11.2 DayView数据结构

DayView必须包含schemaVersion、dayDate、zoneId、windowStartMs、windowEndMs、asOfMs、revision、status、headline、sentences、facts、metrics、episodes、coverage、warnings。status为IN_PROGRESS/READY/PARTIAL/EMPTY/PAUSED之一，不使用“完成”掩盖数据不足。

facts数组中的每项包含id、type、displayValue、sourceRefs和assertion，Sentence的factIds必须全部存在。每个Metric包含key、value（未知为null）、unit、assertion、evidenceIds；每个Sentence包含text、factIds，显示值由renderer生成。每个Episode包含id、revision、startMs、endMs、lane、kind、assertion、title、isFavorite、confidenceScore、qualityFlags与evidenceIds。JSON完整结构和示例见contracts/day_view.schema.json与sample_day_view.json。

示例中的日期、地点、应用与通知均为虚构测试数据。开发禁止把演示数据作为正式空状态兜底。

### 11.3 错误码及重试

| 错误码 | 含义 | 处理 |
|---|---|---|
| P001 | 权限未授予或撤回 | 停止该来源，不后台弹授权；前台显示入口 |
| P002 | Provider或硬件不支持 | 标UNSUPPORTED，不重试 |
| L001 | 尚未首次解锁 | 等待解锁，不推进游标 |
| L002 | 系统不允许当前启动前台服务 | 停止启动尝试，等待用户合法入口 |
| S001 | 来源暂时不可达/服务断开 | 1、5、30分钟机会性退避，最多3次 |
| S002 | 历史游标失效 | 有界重扫最近7天，重新去重 |
| D001 | 数据库空间不足 | 降级清理；不推进失败写入游标 |
| D002 | 数据库损坏或密钥不可用 | 停写、保留文件、展示恢复入口；禁止自动清库 |
| C001 | generation或revision冲突 | 丢弃过期结果，按新范围重算 |
| E001 | 导入认证/格式校验失败 | 不写正式库，清理暂存文件 |
| N001 | 可选模型网络失败/限流 | 使用本地摘要；不连环重试 |

Worker只有瞬时错误才返回retry；权限、硬件不支持和用户暂停返回成功结束并记录状态。重复命令携带requestId去重；删除与恢复等重操作不得因界面重组执行两次。

## 12. 每日总结与个人规律

### 12.1 确定性摘要引擎

先生成Fact集合，再选句式。Fact对象包含id、type、displayValue、sourceRefs、timeRange、assertion和qualityFlags。数值与单位由代码格式化；分钟采用区间求和后统一四舍五入，不能各段四舍五入后再累加。

摘要选择顺序：主要地点/移动 → 有来源的身体活动 → 明显数字活动特征或媒体片段 → 重点照片/生活线索 → 必要缺失说明。每类最多一句，整段通常2—4句。没有对应事实就跳过，不为凑字数生成空泛评价。

句式例：“今天主要在{地点A}与{地点B}附近活动”；“记录到{时长}的媒体播放”；“手机使用更多集中在{时间段}”；“有{日程标题}的安排，实际出席未确认”。地点未知时使用“两个主要停留地点”，不自动反向查询商户名。

### 12.2 可选模型增强

成品默认不运行手机端大语言模型，不在后台占用数GB内存。personalEnhanced允许用户配置HTTPS端点、访问密钥与模型标识；只有结构化日事实变化时才申请增强，默认每日最多3次、单次输入不超过12KB、30秒超时。离线版本所有页面与搜索均可独立完成。

模型仅返回“事实ID排序＋句式ID”的受限组织计划，不接受自行生成的新数值、人物、地点或事件。最终中文由本地renderer根据事实和句式生成。若输出包含未知ID、额外字段、过量条目或冲突时间词，直接回退本地结果。

发送内容默认只有地点别名、时间段、聚合指标和已过滤的片段类型，不含精确坐标、照片、通知原文、健康原始记录或联系人身份。输入中的任何文字都视为数据，模型无工具调用、文件访问、发消息和删除能力。摘要输出不能再次作为未来的事实源。

### 12.3 规律规则

仅实现3类可验证规则：主要地点停留时段变化、晚间手机使用变化、活动/媒体时段分布变化。基准为最近28天内至少10个覆盖充分的同类日期，与最近7天至少4个有效日期比较；工作日与周末分开。有效性阈值默认对应维度覆盖≥80%。

采用中位数与四分位范围，不输出心理、健康诊断或因果结论。变化同时达到绝对30分钟或相对20%之一才进入候选，并显示样本数、两个时间范围、数据缺失。样本不足显示“记录积累中”，不为了填满页面生成所谓洞察。

## 13. 可视化、地图与搜索实现

### 13.1 图形数据与绘制边界

绘图层只读取裁切后的DayView。所有时间坐标以(windowEnd-windowStart)实际跨度计算；跨夏令时的23或25小时日仍正确呈现。日期切换使用新的窗口，跨日事件保留相同ID并展示裁切部分，不能改变原始起止。

主页总览将时间压缩成288个逻辑桶（普通24小时日每桶约5分钟；其他日按实际跨度均分），按轨道绘制密度。详情提供精确区间。缺失、暂停与未启用显示不同纹理，不能全部涂成“休息”。

### 13.2 足迹显示

V1.0离线版实现自绘足迹平面图：停留点、地点标签、有效轨迹、时间游标、缩放与适配视窗；不加载在线地图瓦片，不显示虚构街道。小范围使用局部投影，大范围使用经度解包后的投影；跨180°经线不能画出横跨整个画布的长线。

已知坐标转换必须明确声明与测试。无底图时标“足迹示意”，不是道路导航图。个人地点通过长按停留点命名，后续自动匹配。真实道路底图属于增强适配器，不影响核心日视图验收；不得未经配置调用网络地图服务。

### 13.3 搜索

首版采用SQLite FTS4与应用侧分词：中文生成双字词元，英文小写规范化并按词及连字符扩展，写入专用索引；原始长通知正文不进入长期索引。查询多字中文时组合双字词，结果再对规范化文本验证连续匹配，防止词元碰巧分散命中。

单字中文与短前缀采用受日期范围限制的LIKE检索；参数绑定并转义%、_和反斜线，默认近90天、50条一页。单次最多处理10000个候选，不在主线程全库扫描。用户选择“所有时间”后分批返回并可取消。

结果显示日期、片段标题、匹配位置与来源；删除后同事务移除FTS索引并作重算。照片搜不到OCR正文是明确能力边界，不能声称搜索覆盖全部屏幕与图片内容。

## 14. 性能预算与自动降级

### 14.1 基准场景与验收指标

标准场景定义：6GB RAM及以上的arm64手机，正常电池健康，固定亮度与刷新率；24小时中约2小时户外移动、4小时正常屏幕使用、1000条通知、30张新增照片；其余主要室内停留。不运行可选模型，不进行首次历史索引。具体机型、系统、电池温度与应用组合必须写入测试报告。

下表是发布验收目标，不是已经在用户手机上测得的结果；机型差异不能通过虚构精确耗电承诺掩盖。

| 指标 | 发布目标 | 采集与判定 |
|---|---|---|
| 额外日耗电 | 同条件对照，中位数≤3个百分点/24h；单日>5个百分点必须排查 | 至少3组A/B或ABBA对照；不使用系统电池页占比冒充增量 |
| 8小时静置增量 | ≤1个百分点为目标 | 固定网络、亮屏、温度；多次测量抵消整数电量误差 |
| 后台内存 | 稳定PSS≤100MB目标，压力上限150MB | 不打开UI时，观察30分钟稳态及7日增长 |
| 前台内存 | 普通日详情PSS≤220MB目标 | 缩略图缓存24MB；退出UI及时释放绘图/图片缓存 |
| 后台CPU | 平均≤0.2%单核；单次规则计算CPU时间≤2秒目标 | 按进程CPU时间/墙钟时间统一口径 |
| 主线程 | 单回调P95≤5ms，无磁盘/网络操作 | Trace、StrictMode、系统跟踪 |
| 首屏 | 缓存热启动P95≤1秒，冷启动P95≤2秒 | Macrobenchmark；至少30次 |
| 滚动与回放 | 60Hz下P95帧耗时≤16.7ms目标 | 真实1000片段数据；不以空白页面测试 |
| 原始元数据增长 | 标准场景≤5MB/天目标 | 不含缩略图；超量按来源降级而非无限增长 |
| GNSS | 常规模式≤120秒/小时、≤600秒/天 | 应用实际订阅持续时间计入预算 |
| 业务网络 | 离线变体0业务请求 | 本App不含INTERNET；系统定位Provider自身联网不在该承诺内 |

优先使用系统跟踪、Perfetto/Power Profiler与Macrobenchmark；Battery Historian不作为主工具，其官方页面已说明不再积极维护。[S18][S19] Android 17还引入按设备条件限制应用内存的机制，兼容验证不能只在大内存开发机上完成。[S25]

### 14.2 降级策略与恢复迟滞

电量≤20%且未充电，进入ECONOMY；≤10%进入PASSIVE；热状态达到MODERATE时停止图片分析与模型请求，达到SEVERE时关闭主动GNSS并缩减规则批次。恢复需要电量高于25%或充电，且热状态低于MODERATE稳定10分钟。

单小时GNSS预算耗尽后本小时只使用低功耗/被动定位，不能换一个Service名字重置。出现CPU持续超预算或队列积压，优先停止缩略图、可选通知正文提取与非必要规律计算；保留源状态与必要计时。预算跨进程重启保存，不能靠重启清零。

### 14.3 零干扰约束

不得获取音频焦点、自动调节音量/亮度、关闭Wi-Fi、占用相机、启动屏幕投影、显示悬浮窗、主动弹出Activity、发送自动回复或拦截系统输入。无障碍服务不请求手势、不读输入控件、不监听文本变化。

后台通知只更新同一个ID，更新频率不高于每5分钟一次（暂停等用户操作除外），不触发heads-up。缺权限、断网和短时来源失效只影响状态页，不持续打扰。系统自身显示的定位/服务指示保留，不隐藏。

## 15. 安全、暂停、删除与备份

### 15.1 本地密钥与数据库

首次初始化生成32字节随机数据库秘密，经Android Keystore中的AES-GCM包装密钥加密后存于noBackupFilesDir；包装nonce随机且不得复用。数据库秘密转换为固定编码的高熵口令交给SQLCipher，不写入源码、普通偏好或日志。[S15][S16]

为支持首次解锁后锁屏期间自动采集，包装密钥不要求每次生物认证。应用界面可启用系统BiometricPrompt（USE_BIOMETRIC，API 29原生接口）；会话超过60秒不在前台后重新认证，失败可走系统凭据回退，缺少设备锁时提示先设置系统锁屏。API 29使用setDeviceCredentialAllowed(true)，API 30+使用setAllowedAuthenticators的允许组合，不能同时设置与凭据回退冲突的negative button。[S39] 该功能只锁住查看入口，不暂停后台采集。生物识别锁是额外的查看门槛，不宣称能阻止已获得应用进程或Root权限的人。重启后首次解锁前不读取凭据加密库，不缓冲个人正文。

禁用系统自动备份并配置Android 12+数据提取规则同时排除云备份和设备迁移中的敏感数据，避免只写allowBackup=false却忽略设备迁移差异。[S17] 用户需要迁移时走本App加密导出。

### 15.2 暂停与删除的原子性

暂停命令先在同一事务内generation+1、写入开放结束时间的exclusion、设置PAUSED，再取消来源与后台任务。已在内存队列中的旧generation记录在提交前丢弃；正在进行的远程请求结果也因generation失效而不落库。

stop命令同样持久化一个开放的停采排除区间，并将requested置false；再次start时关闭该区间，不能补回本人主动停止期间的记录。

恢复时关闭暂停区间，重新建立传感器基线；任何历史补采、相册扫描和健康同步均应用相同排除条件。可安全拆分的应用区间在排除边界切开；不能拆分的健康累计量或跨时段敏感正文，整条跳过并说明缺失，不能擅自按比例分配。

删除时段先创建永久排除记录，再找出受影响观察、片段、快照、摘要、指标、修正附件、索引与缓存并删除/重算；删除单个片段则保存来源键或片段指纹的排除规则，防止重导。该过程必须有operationId、崩溃恢复和重复执行幂等性。

普通删除保证应用不再展示、检索或重新导入，不能宣称闪存物理安全擦除。用户已另外导出的文件无法由本App撤回。设备上缓存的已解密内存不可能保证逐字节无残留，应缩短存活时间并关闭日志。

### 15.3 备份格式与实现

完整备份使用.woarchive扩展名，逻辑内容为manifest.json、observations.jsonl、episodes.jsonl、annotations.jsonl、places.json、policy.json与daily.jsonl；默认不嵌入照片原图。文件清单记录格式版本、条目数、内容哈希、归档时区及导出范围。ZIP仅允许上述7个固定文件名；50000是解包器通用硬上限，本格式实际最多7项。

使用Tink Streaming AEAD的AES256_GCM_HKDF_1MB加密归档流，避免一次把整个历史数据库加载到内存。该原语面向大文件流式认证加密，不能自行替换为“ZIP加密码”或自制异或算法。[S35]

episodes.jsonl的每行包含片段本体、最小证据快照及关联的稳定ID；policy.json包含归档时区、排除规则和本人配置，不包含旧设备授权状态。用于无原生ID来源去重的sourceKeySeed作为秘密数据仅写入已加密归档，恢复后重新在本机保护；它不是数据库包装密钥，也不能写入外层header。daily.jsonl包含日期视图及生成版本；observations.jsonl采用sourceId＋sourceKey引用，不直接搬运本库自增ID。导入时先建立ID映射，再恢复关系并逐条验证契约。

每次导出生成独立Streaming AEAD keyset与32字节随机恢复秘密。恢复秘密以Base64URL形式单独交给本人保存；使用该秘密构造AES-GCM AEAD包装keyset，nonce由库正确生成。外层固定头为WOARCH01＋32位大端无符号header长度＋UTF-8 JSON header；header包含version、encryptedKeyset、算法标识，不包含恢复秘密。流式载荷的associatedData绑定完整外层头字节。

恢复先检查头长度≤64KB、格式版本与算法允许列表，使用恢复秘密解包keyset并流式解密到应用私有暂存区；只有读到认证成功的完整结尾并校验清单后才允许导入。限制最多50000个条目、展开后512MB，拒绝路径穿越、重复路径、未知可执行内容和ZIP炸弹。

恢复采用新库导入、校验、关闭连接、原子替换；失败保留旧库。外部URI在新手机上通常不能直接复用，媒体关联先标待重新匹配，禁止访问错误的同ID照片。恢复完成后重新建立Keystore包装密钥和本机数据源游标，不恢复旧设备监听状态。

### 15.4 日摘要导出

支持Markdown/JSON格式的单日或日期范围导出，明确这是本人主动生成的可读明文副本。默认不包含通知正文、精确坐标和未经确认的身份信息；导出前展示内容开关。导出使用系统文件选择器，不要求全盘文件管理权限。

## 16. Android兼容性与异常场景

### 16.1 版本矩阵

| 版本 | 实现检查点 | 验收动作 |
|---|---|---|
| Android 10 / API 29 | 后台位置、活动权限、分区存储 | 验证位置授权、计步、重启、MediaStore旧增量路径 |
| Android 11 / API 30 | 包可见性、后台授权路径、首次解锁 | 包标签取不到时显示包名；禁止因空查询清空历史 |
| Android 12 / API 31—32 | 后台FGS启动限制、精确/大致位置 | 从合法前台入口启动；省电和位置降精度实测 |
| Android 13 / API 33 | 通知权限、媒体细分权限 | 状态通知、照片/视频访问分别测试 |
| Android 14 / API 34 | FGS类型权限、部分照片访问 | 错误权限顺序不崩溃；仅选择照片不误标全图库 |
| Android 15 / API 35 | FGS时限、OTP脱敏、16KB设备 | 不用dataSync保活；脱敏文本不进入补救抓屏路径 |
| Android 16 / API 36 | Job配额、edge-to-edge、返回导航 | Worker迟到有补偿；状态栏/手势栏不遮挡内容 |
| Android 17 / API 37运行环境 | 应用内存限制等全应用变化 | 内存压力、后台冻结与ApplicationExitInfo诊断 |

版本事实与约束见[S01]—[S04][S09][S13][S25][S26][S31][S36]。targetSdk为36不代表可忽略新系统对全部应用生效的变更。所有SDK调用按API级别或feature gate防护，不仅在注解中写RequiresApi。

### 16.2 国产系统与无Google服务

启动时实际探测Provider、传感器与授权入口，不按手机品牌直接判断。无Google服务时FrameworkLocationProvider为正式实现，通知、使用统计、MediaStore和日历不依赖Google账号；Health Connect缺失时保持不可用状态而不是不断安装提示。

系统对后台启动和省电有自定义设置时，给出标准应用设置页与一次性说明；不硬编码可能失效的厂商私有Activity作为唯一入口。后台被清理的机型必须在真机报告中列出实际缺失，不伪装为已经解决全部保活问题。

### 16.3 关键异常决策

断网：所有离线基础功能继续；模型增强使用本地摘要，不弹错误。定位关闭：停止位置订阅并记录缺口；其他轨道继续。没有携带手机：不自行推断人在哪，允许本人修正。

系统时间向前或向后跳变：建立clock segment，保留原时间与校正说明；无法纠正的区间不计精确时长。跨时区：按选定归档时区生成日窗口，事件详情保留发生地时区。夏令时重复小时：时间标签附offset区分，不挤压到同一时刻。

用户清空图库或删除照片：移除缩略图引用，不崩溃；过往文字摘要是否保留按删除范围决定。健康/日历源撤回记录：更新派生结果并标来源变化。磁盘已满：停新增可选细节、保留错误状态，不丢游标。电池极低：只保留已存在低功耗/系统来源，不能为保证轨迹导致关机更早。

### 16.4 应用升级与数据迁移

每次release保持签名、applicationId、数据库版本演进。提供每一版本到下一版本的显式Migration与旧库fixture。禁止fallbackToDestructiveMigration。大规模重建索引可延期，旧摘要先可读；加密参数升级应在备份、连接关闭和空间充足时执行。

迁移失败进入只读诊断/恢复状态，不自动删库重来。签名密钥丢失通常影响覆盖升级，因此个人发布密钥与恢复秘密应分开离线保管，不提交Git仓库。

## 17. 测试设计与验收用例

### 17.1 测试层次

领域单元测试覆盖时间、区间、过滤、去重、状态机和摘要口径。数据库测试覆盖DDL、外键、唯一约束、迁移、删除闭环与事务原子性。Android集成测试覆盖真实系统来源和权限变化。性能测试使用release或profileable变体，不以debug结果作为最终结论。

附件中的Python校验器验证设计附件的一致性、SQL约束、JSON样本与区间算法；纯Kotlin工具可单独编译运行。这不代替Android工程编译、设备权限测试、SQLCipher Android运行和耗电验收。

### 17.2 功能与正确性用例

| ID | 场景 | 预期结果 |
|---|---|---|
| T01 | 首次安装并全部授权 | 自动探测来源，点击一次开始后可持续采集 |
| T02 | 无GMS且有网络定位 | 基础来源正常，不因Google组件缺失崩溃 |
| T03 | 无GMS且无网络定位 | 按预算短时GNSS，室内缺失，不无限搜索 |
| T04 | 仅大致位置 | 地点精度下降，界面不显示假精确路线 |
| T05 | 仅部分照片授权 | 只索引可访问照片，状态显示PARTIAL |
| T06 | 同批UsageEvents重复导入两次 | 观察数量和所有指标不变 |
| T07 | 同包两个Activity切换 | 不增加跨应用切换次数，不重复计时 |
| T08 | 分屏A用10分钟、B重叠8分钟 | 手机并集≤10分钟，单App统计允许重叠 |
| T09 | RESUMED后屏幕锁定 | 可交互时长在锁定处结束 |
| T10 | RESUMED缺失结束且设备重启 | 不无限延长；不确定边界可见 |
| T11 | 23:50—00:10跨日活动 | 两日各显示10分钟，源事件仍为20分钟 |
| T12 | 夏令时23/25小时归档日 | 窗口和统计按真实跨度，禁止固定24小时 |
| T13 | 时钟回拨30分钟 | 无负时长，冲突区间有说明 |
| T14 | 位置连续漂移在同楼附近 | 不生成频繁出门/返回事件 |
| T15 | 两点相隔60分钟 | 不画可靠连续路线，不自动插值精细足迹 |
| T16 | 只有日历会议、无实际活动证据 | 仅PLANNED，不写参加会议 |
| T17 | 快递送达通知 | “收到送达提示”，不是“已取件” |
| T18 | 通知进度更新100次 | 不生成100个生活片段 |
| T19 | 系统已脱敏验证码通知 | 不保存验证码、不启动替代屏幕读取 |
| T20 | 播放—暂停—播放 | 只合并有效PLAYING区间，暂停不计 |
| T21 | 步数计数器重启归零 | 不产生负数，不把跨启动差值当步数 |
| T22 | 手机+手表重复步数 | 使用聚合或主来源，不双倍相加 |
| T23 | 无睡眠来源但8小时未亮屏 | 不生成8小时睡眠 |
| T24 | 导入旧照片 | 按拍摄日期或“添加图片”口径，不冒充今天拍摄 |
| T25 | 原照片URI失效 | 显示不可访问，占位可操作，无崩溃 |
| T26 | 暂停时内存队列有未提交记录 | 旧generation记录不在暂停后落库 |
| T27 | 暂停后恢复并补采全部历史 | 暂停时段仍为空，健康累计量不按比例伪分配 |
| T28 | 删除片段后下一轮补采 | 删除结果不被重新导入，摘要与搜索同步清理 |
| T29 | 原始证据正常过期 | 摘要可读，标明明细到期，不伪称原始数据仍存在 |
| T30 | 模型返回未出现的地点/事实ID | 整份增强结果拒绝，保留本地摘要 |
| T31 | 摘要生成中用户删除该时段 | 过期结果因generation不符不能提交 |
| T32 | 中文关键词、英文大小写、%和_ | 搜索正确，不发生SQL拼接或范围失控 |
| T33 | 修改昨天片段 | 昨日摘要revision更新，当前页面不跳转 |
| T34 | 数据库写入中异常退出 | 游标与观察一致，重启可幂等补采 |
| T35 | 恢复文件被截断/篡改/错误恢复码 | 不导入任何正式数据，旧库保留 |
| T36 | 加密备份跨手机恢复 | 文字记录恢复，旧URI待重新匹配，密钥重新包装 |
| T37 | 旧版数据库升级 | Migration通过，历史记录和个人修正不丢失 |
| T38 | 200%字体、深色模式、旋转/分屏 | 无文字遮挡，操作可达，返回状态正确 |
| T39 | 非24小时数据覆盖 | 显示截至与分维度缺失，不给100%生活完整度 |
| T40 | 正式首次打开但尚无数据 | 真正空状态，不偷偷展示演示记录 |

### 17.3 后台与性能用例

至少执行：8小时锁屏静置、24小时标准场景、7天持续安装运行、15%低电开始、系统省电模式、定位开关切换、应用被系统清理、用户强停、设备重启后不解锁、正常升级、上万条来源事件回放及缩略图压力测试。

7日测试要求0次可归因于本App的ANR/崩溃；后台PSS没有持续单调增长；记录中断可解释；无可感知输入卡顿、音频争用和焦点抢占。强停不列为“必须自动恢复”，但用户重新打开后的补采与缺失标识必须正确。

地点算法采用本人真实标注样本：至少50个持续20分钟以上且定位质量合格的停留，目标精确率≥90%、召回率≥80%；另报告边界绝对误差P50/P95与被排除样本数。指标不在没有有效位置的区间上伪造成功。

### 17.4 测试报告必须记录的内容

设备型号、系统构建号、API、RAM、存储空闲、电池健康与温度、网络条件、权限状态、定位Provider、App版本、依赖锁版本、算法配置hash、测试输入、起止时间与计量口径。每个失败绑定问题编号、复现步骤和日志片段；日志先过滤个人正文与精确位置。

## 18. 开发任务、集成顺序与交付物

### 18.1 开发顺序（同一成品版本，不是功能路线图）

| 顺序 | 实现任务 | 必须完成后才能进入下一步 |
|---|---|---|
| 1 | 工程、加密数据库、配置、时钟、来源契约 | clean构建、schema测试、初始化/关闭/重开通过 |
| 2 | 暂停/排除/generation/事务机制 | T26—T29、T31、T34通过，避免先采集后补隐私控制 |
| 3 | Usage、通知、媒体和基础状态来源 | 去重、屏幕边界、来源断开与恢复通过 |
| 4 | RecordingService、框架定位、硬件步数 | 合法启动、资源预算、锁屏和无GMS测试通过 |
| 5 | 照片、日历、健康来源 | 增量/删除/无数据/部分授权均有闭环 |
| 6 | 停留/移动/会话引擎与证据关联 | 领域固定样本、跨日与冲突测试通过 |
| 7 | 确定性指标、摘要、DayView | UI所需字段完整，不依赖临时硬编码数据 |
| 8 | 今天、详情、回看、搜索、规律与设置 | 导航、200%字体、缓存刷新、修正删除可用 |
| 9 | 加密备份、导入恢复与迁移 | 认证失败不提交、跨设备恢复、旧版本迁移通过 |
| 10 | 性能优化、真机7日验收、签名发布 | 满足第14、17、19章，留存报告与签名产物 |

可选联网增强在第7步之后实现，不阻塞离线成品。任何模块完成都必须能用固定事件源回放验证，不能只靠在开发者手机上手工点几次。

### 18.2 工程与开发附件使用方式

附件目录中contracts/schema.sql用于生成/核对Room Entity与Migration；day_view.schema.json用于UI与引擎联调；observation.schema.json与source_payloads.schema.json约束统一观察及各来源载荷；default_config.json是唯一默认参数源；sample_day_view.json与interval_cases.json用于测试，不用于正式回退。

android/CoreContracts.kt提供纯Kotlin领域契约与区间工具；AndroidManifest.reference.xml、offline_manifest.xml、enhanced_manifest.xml、accessibility_service_config.xml、backup_rules.xml、data_extraction_rules.xml用于组件声明参考；versions.toml与build.gradle.kts.reference固定基础依赖；notification_rules.json给出本地通知规则初值与匹配优先级。它们是开发附件，不是可直接安装的应用源码工程。

创建工程后使用Gradle Wrapper构建并执行以下门槛；首次联网解析依赖后提交依赖锁和verification-metadata，离线开发需另行准备Android SDK与Gradle/Maven缓存。不要把本设计包误当作SDK离线安装包。

```bash
./gradlew clean test lint assemblePersonalOfflineDebug
./gradlew connectedPersonalOfflineDebugAndroidTest
./gradlew assemblePersonalOfflineRelease
adb install -r app-personalOffline-release.apk
adb shell dumpsys meminfo com.personal.wo
adb shell dumpsys batterystats com.personal.wo
adb shell dumpsys jobscheduler
```

最终APK文件位置按工程实际输出目录确定；上面adb install中的名称是发布重命名约定。耗电测试前后使用同一脚本和系统跟踪口径，不把adb充电状态影响忽略掉。

### 18.3 发布交付物

发布包包括签名APK、源代码与依赖锁、Room schema与Migration、默认配置及算法版本、功能测试报告、性能/耗电报告、7日稳定性报告、数据导出恢复说明和本人首次配置说明。签名私钥不与APK或公开代码一起交付。

用户首次配置说明必须明确“一条静默状态通知”“首次解锁后的恢复行为”“应用强停期间不会神奇补回所有数据”“照片原文件不随默认备份迁移”。不要求本人每天启动计时或填写当天内容。

## 19. 成品验收与最终判定

### 19.1 必须全部通过的发布门槛

G01：本人完成一次授权与配置后，正常使用手机即可自动形成日视图，不写日记也能看到真实片段。G02：至少应用使用、通知与位置/活动中的可用来源形成完整链路；硬件/Provider不存在的项明确显示不可用，不能以空壳页面假装功能完成。

G03：今天、回看、搜索、修正、删除、暂停、导出、恢复及来源状态均可操作，关键数字可追溯。G04：重复采集、跨午夜、分屏、迟到数据、计划与事实分离的测试通过。G05：暂停与删除不会被补采、缓存或模型结果复活。

G06：无全天录音、连续截屏、高频传感器兜底或干预其他应用的行为。G07：第14章资源目标经本人目标机型测试，出现超标已修复或经明确调整预算并重测；不能用另一台手机的结果替代。G08：7日稳定性、迁移、16KB和备份认证失败测试通过。

### 19.2 最终技术结论

本设计具备普通Android应用层的实现路径：用系统公开数据源建立观察层，通过确定性规则重建片段，再以多轨时间轴和受约束摘要呈现。全授权带来的信息丰富度与低干扰目标通过“事件触发、增量补采、低功耗定位、无后台大模型、严格资源预算”协调。

直接开发的范围、默认参数、数据契约、组件职责、异常路径和验收标准已在本文冻结。实际手机上的耗电、系统后台行为、来源准确性与最终APK兼容性必须由上述测试证明；任何文档都不能替代未进行的真机测量。

## 附录A：核心参数速查

| 参数 | 默认值 | 使用位置 |
|---|---|---|
| archiveZone | 首次设备时区，固定保存 | 归档与日期窗口 |
| usageOverlapMinutes / maxPullHours | 5 / 6 | UsageEvents补采 |
| stayMinPoints / stayMinMinutes | 3 / 10 | 停留识别 |
| stayMaxGapMinutes / stayRadiusMaxM | 20 / 300 | 聚类质量控制 |
| routeMaxLinkGapMinutes | 5 | 轨迹断线 |
| gnssBurstSeconds / cooldownMinutes | 20 / 10 | 高精度定位 |
| gnssHourlySeconds / dailySeconds | 120 / 600 | 硬资源预算 |
| ingestQueue / batchSize / flushSeconds | 512 / 100 / 5 | 写入与内存 |
| notificationBodyChars / flushSeconds | 2048 / 2 | 通知正文处理 |
| metadataPageSize / thumbnailLongEdgePx | 200 / 512 | 媒体索引 |
| thumbnailMemoryMB / diskMB | 24 / 200 | 缓存 |
| lowBattery / passiveBattery / recovery | 20% / 10% / 25% | 降级与恢复 |
| rawRetentionDays / noticeTextDays | 30 / 7 | 数据保留 |
| episodeRetentionDays | 730 | 长期片段 |
| databaseSoftLimitMB / logLimitMB | 800 / 10 | 空间预算 |
| summaryMaxSentences / highlights | 4 / 8 | 首页信息密度 |
| optionalModelDailyCalls / timeoutSeconds | 3 / 30 | 可选增强 |

这些值是设计默认值与验收目标，不是系统API保证的频率或已经测得的产品指标。代码读取配置后校验范围，非法配置回退到本基线并记录操作日志，不崩溃或无限增加资源使用。

## 附录B：来源事实、设计决定与验证边界

[Sxx]指向于2026年10月14日至15日核查的官方开发文档或维护方资料。引用用于支持平台能力、限制及依赖接口；采样参数、UI规格、算法规则、资源目标、开发流程属于本项目设计，不应误解为Android官方承诺。

随包的契约测试报告只覆盖SQL/JSON与纯算法附件。没有运行完整Android App，也没有获得本人目标手机进行耗电、后台存活和系统权限实测；最终成品以第19章发布门槛为准。

## 参考资料


以下资料用于核查平台事实与接口，不代表本项目已获得真机测试结果。查阅日期：2026年10月14日至15日。依赖版本是本文固定的开发基线，不将链接中的后续版本自动替换进工程。

[S01] Android Developers：Foreground service types。

https://developer.android.com/develop/background-work/services/fgs/service-types

[S02] Android Developers：Restrictions on starting a foreground service from the background。

https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start

[S03] Android Developers：Foreground service timeouts。

https://developer.android.com/develop/background-work/services/fgs/timeout

[S04] Android Developers：Define work requests。

https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work

[S05] Android Developers：Optimize location for battery。

https://developer.android.com/develop/sensors-and-location/location/battery

[S06] Android API Reference：LocationManager。

https://developer.android.com/reference/android/location/LocationManager

[S07] Android API Reference：UsageStatsManager。

https://developer.android.com/reference/android/app/usage/UsageStatsManager

[S08] Android API Reference：NotificationListenerService。

https://developer.android.com/reference/android/service/notification/NotificationListenerService

[S09] Android Developers：Android 15 behavior changes — all apps。

https://developer.android.com/about/versions/15/behavior-changes-all

[S10] Android API Reference：MediaSessionManager。

https://developer.android.com/reference/android/media/session/MediaSessionManager

[S11] Android Developers：Motion sensors。

https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion

[S12] Android Developers：Read data from Health Connect。

https://developer.android.com/health-and-fitness/health-connect/read-data

[S13] Android Developers：Access media files from shared storage。

https://developer.android.com/training/data-storage/shared/media

[S14] Android Developers：Create your own accessibility service。

https://developer.android.com/guide/topics/ui/accessibility/service

[S15] Android Developers：Android Keystore system。

https://developer.android.com/privacy-and-security/keystore

[S16] SQLCipher维护方：sqlcipher-android仓库与Room集成。

https://github.com/sqlcipher/sqlcipher-android

[S17] Android Developers：Back up user data with Auto Backup。

https://developer.android.com/identity/data/autobackup

[S18] Android Developers：Macrobenchmark overview。

https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview

[S19] Android Developers：Profile battery usage with Batterystats and Battery Historian。

https://developer.android.com/topic/performance/power/setup-battery-historian

[S20] Android Developers：Android Gradle Plugin 8.13 release notes。

https://developer.android.com/build/releases/agp-8-13-0-release-notes

[S21] AndroidX：Room release notes。

https://developer.android.com/jetpack/androidx/releases/room

[S22] AndroidX：WorkManager release notes。

https://developer.android.com/jetpack/androidx/releases/work

[S23] Android Developers：Compose BOM mapping。

https://developer.android.com/develop/ui/compose/bom/bom-mapping

[S24] Kotlin官方：Kotlin releases。

https://kotlinlang.org/docs/releases.html

[S25] Android Developers：Android 17 behavior changes — all apps。

https://developer.android.com/about/versions/17/behavior-changes-all

[S26] Android Developers：Request background location。

https://developer.android.com/develop/sensors-and-location/location/permissions/background

[S27] Android API Reference：Manifest.permission。

https://developer.android.com/reference/android/Manifest.permission

[S28] Android Developers：Calendar provider overview。

https://developer.android.com/identity/providers/calendar-provider

[S29] Android Open Source Project：Application Sandbox。

https://source.android.com/docs/security/app-sandbox

[S30] Google for Developers：Activity Recognition API。

https://developers.google.com/location-context/activity-recognition

[S31] Android Developers：Support 16 KB page sizes。

https://developer.android.com/guide/practices/page-sizes

[S32] AndroidX：Health Connect release notes。

https://developer.android.com/jetpack/androidx/releases/health-connect

[S33] SQLCipher维护方：4.10.0的16KB页兼容验证讨论。

https://github.com/sqlcipher/sqlcipher-android/issues/71

[S34] AndroidX：Stable channel及基础依赖版本索引。

https://developer.android.com/jetpack/androidx/versions/stable-channel

[S35] Google Tink：Streaming AEAD。

https://developers.google.com/tink/streaming-aead

[S36] Android Developers：Android 16 behavior changes — apps targeting Android 16。

https://developer.android.com/about/versions/16/behavior-changes-16

[S37] Google Tink维护方：tink-java版本发布记录。

https://github.com/tink-crypto/tink-java/releases

[S38] Android API Reference：UsageEvents.Event公开字段与生命周期事件。

https://developer.android.com/reference/android/app/usage/UsageEvents.Event

[S39] Android API Reference：BiometricPrompt.Builder凭据回退。

https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt.Builder

