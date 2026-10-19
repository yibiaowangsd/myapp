# 华为运动健康 Android 正式接入准备

核对日期：2026-10-19。基线：1.7.1（`8dd3ddc`）。目标仍为HUAWEI HBN-AL80、HarmonyOS 4.2 / Android 12 API31的Android APK，不采用HarmonyOS NEXT的ArkTS接口。

## 当前结论与个人资格

**代码接入准备已实现，真实账号接入尚未完成。** personalEnhanced已声明官方HMS Health Android SDK，包含真实授权、授权核验、只读查询、撤权和来源隔离。尚未取得本人的真实App ID、服务开通结果、获批范围和真机授权证据。默认显示“待平台开通”，不会调用账号授权或健康读取接口。

**个人资质有实质限制。** 华为《应用开发者申请资质说明》（2026-01-23更新）要求个人开发者应用上架华为应用市场；未上架的个人应用暂不开放运动健康数据。个人不得有个人信用不良记录，只能申请基础开放等级的数据。睡眠等高阶数据暂不向个人开发者开放。因此“仅自己使用、直接安装APK”不能视为满足当前申请资格。坚持个人侧载时应向华为官方核实是否有适用通道，不承诺豁免、不伪造企业主体。本次未执行应用上架或主体变更。

企业主体也并非注册即可获取高级数据：官方列有实缴资金、存续与成立时间要求，并区分事业单位/政府机构、社会团体。申请必须使用真实符合条件的主体。

打开运动健康、Android身体活动权限、本机SensorManager、Health Connect权限和华为账号授权均是不同事项，不能互相代替。

## SDK及服务

| 项目 | 本次选择/核验 |
|---|---|
| 原生Android基础能力SDK | `com.huawei.hms:health:6.16.0.300`；从华为官方Maven实际获取POM/AAR，按真实类签名编译，版本和SHA-256进入依赖锁/校验 |
| Maven | `https://developer.huawei.com/repo/`，限定`com.huawei.*`组 |
| 华为帐号服务 | 平台需登记/开通；Health POM依赖`com.huawei.hms:hwid:6.11.0.300` |
| App ID | 按官方Android示例配置Manifest的`com.huawei.hms.client.appid`；不在APK放client secret |
| Health Service Kit | 申请运动健康服务，选择实际Android应用和最小数据读取范围，通过平台审核后还需用户授权 |
| 本次不使用 | 扩展能力`com.huawei.hihealth:hihealthkit`、云侧REST、云事件订阅、后台账号轮询；无需新增AGConnect构建插件 |

版本核对边界：公开Android版本历史页本次未能稳定返回正文，官方Maven未提供可读版本列表。本次**可核验、实际解析的是6.16.0.300**，不宣称已穷尽证明其为2026-10-19全站最新原生Android发行。Flutter包`6.17.0+300`与REST变更`6.26.6.200`不能作为Android Maven SDK最新版依据。正式发布前应在开发者控制台的Android基础能力SDK下载/版本说明再次核定并保留公告，升级后重跑全部检查，不使用动态版本。

项目仍为minSdk29、targetSdk36、JDK17。目标设备的HMS Core、运动健康App版本与地区支持须按所选SDK官方要求实测，不把旧codelab的EMUI版本和非华为限制当作最新矩阵。

## 可直接登记的应用信息

| 登记项 | 本项目值/待补内容 |
|---|---|
| 应用名称 | 我 |
| 产品类型 | Android手机应用；健康/生活记录，最终分类按控制台实际选项 |
| Android包名/applicationId | `com.personal.wo`，两变体相同，无applicationIdSuffix |
| 当前源码版本 | versionName `1.7.1`、versionCode `12`；本次未制作新个人签名发行包 |
| 发布证书SHA-256（连续） | `c9b24de261dc30ebbc92da22f3f7826beb2c3015141c7b43d63b1ca27de9cb58` |
| 发布证书SHA-256（冒号） | `C9:B2:4D:E2:61:DC:30:EB:BC:92:DA:22:F3:F7:82:6B:EB:2C:30:15:14:1C:7B:43:D6:3B:1C:A2:7D:E9:CB:58` |
| 签名依据 | 沿用原有文档记录；本次未取得证书/私钥进行实物复核，不更换签名 |
| 接入变体 | `personalEnhancedRelease`；debug证书不同，须单独登记，不能冒充发布证书 |
| App ID、项目ID | 待本人在平台真实创建/确认，不填示例数字 |
| 申请主体、联系资料、分发地区 | 本人按真实资料填写，不从GitHub用户名推定实名认证信息 |
| 应用市场上架链接、公开隐私政策URL | 尚未取得；GitHub仓库不能代替应用市场上架证明 |
| 服务开通、测试/正式数据权限结果 | 尚未取得，须保留官方结果及逐项范围 |

请在保管原发行包的本机用`apksigner verify --print-certs <个人签名APK>`复核实际SHA-256。不要用debug指纹覆盖个人发布指纹。原有`WO_KEYSTORE_PATH`、`WO_KEYSTORE_PASSWORD`、`WO_KEY_ALIAS`、`WO_KEY_PASSWORD`签名配置保持原样。不向聊天、源码、PR或日志提供密码、私钥、keystore、client secret或访问令牌。

## 只读权限申请表

| 用途 | 平台读取范围 | Android Scope常量及值 | 资格与边界 |
|---|---|---|---|
| 每日步数 | 日常活动/步数，只读 | `HEALTHKIT_STEP_READ`：`https://www.huawei.com/healthkit/step.read` | 基础数据；个人须先满足上架等条件并获批 |
| 单次运动起止、类型 | 锻炼记录/运动记录，只读 | `HEALTHKIT_ACTIVITY_RECORD_READ`：`https://www.huawei.com/healthkit/activityrecord.read` | 本次仅导入ID、起止、类型/名称；具体主体可申请范围按数据总览与批准结果确认，不自动包含轨迹、心率或采样详情 |
| 睡眠区间 | 睡眠记录/睡眠数据，只读 | `HEALTHKIT_SLEEP_READ`：`https://www.huawei.com/healthkit/sleep.read` | 高阶数据，个人不可直接申请；代码保留但未符合资质且获批前不得配置`SLEEP` |
| 授权前一周历史 | 历史数据开放：周，组合获批数据类型 | `HEALTHKIT_HISTORYDATA_OPEN_WEEK`：`https://www.huawei.com/healthkit/historydata.open.week` | 单独申请且需用户授予，不代替数据类型权限；本实现最多查询含今天的7个自然日 |

`HEALTHKIT_ACTIVITY_READ`（`activity.read`）是运动关联详情等权限，与`activityrecord.read`不同，本次不请求。月/年历史、写/读写、位置、心率、个人资料均不在本次最小申请中。申请表中不得把“拟申请”写成“已批准”。

`android.permission.ACTIVITY_RECOGNITION`只用于本机传感器；`android.permission.health.READ_*`对应Health Connect。这些系统权限不能授予华为OAuth权限。只读账号数据不以本机GPS、联系人权限作为前提。

可用于申请材料的用途说明（按实际批准项目删减）：

> 应用“我”在用户主动连接华为账号并选择允许的数据类型后，将获准的步数日聚合与单次运动起止信息用于个人时间轴和每日统计。历史查询仅补充最近七日回顾，不与手机硬件步数重复累加。数据保存在本机加密数据库；此接入路径不上传开发者服务器，不用于广告或转售。用户可以在应用内撤销授权并清理对应导入数据，也可在华为官方授权管理撤销。睡眠为后续独立申请范围，仅在真实主体符合高阶资质且审核通过后启用；未获批前不请求、不读取。

这段文字描述拟申请功能，不代表已经获批或读取真实数据。个人/企业申请材料清单、数据使用表、隐私政策、功能界面/视频、应用市场证明等应按平台当前表单完整提供，不能虚填服务开通截图或账号数据截图。

## 正式申请与配置步骤

1. 完成开发者实名认证，核实个人上架门槛与主体资格；未满足时保持“待平台开通”，本机计步照常使用。
2. 在官方管理中心/AGC登记Android应用、`com.personal.wo`及实际个人发布证书指纹，取得真实App ID并开通华为帐号服务。
3. 申请Health Service Kit，选择真实应用，申请上表必要只读项与周历史；个人申请不勾选睡眠。不以“已提交”代替“已通过”。
4. 等待官方测试权限审核；区分测试权限、用户授权和正式权限。官方资料说明测试阶段有用户数限制，开发验证后仍需申请验证/正式范围。审核时长及限制以本人控制台通知为准，不沿用旧codelab的1–2日承诺。
5. 在本机Gradle用户属性（例如`~/.gradle/gradle.properties`）设置`wo.huawei.appId`为真实数字；`wo.huawei.approvedScopes`只填写实际批准的`STEPS,EXERCISE,HISTORY_WEEK`子集。这些名称仅说明格式，不表示已批准。`SLEEP`仅限符合资质并获批后添加。
6. `wo.huawei.certificateSha256`默认原个人发布指纹。debug联调须先登记实际debug指纹，再在本机覆盖此属性。运行时检查当前APK签名，防止错误证书被误认为可接入。
7. 安装增强包，在“设置 → 应用与来源 → 应用连接 → 来源与后台”发起“华为账号授权”。SDK拉起官方授权流程；回调后重新验证账号实际Scope和运动健康数据开放状态。打开运动健康本身没有授权效果。
8. 真机核对数据及失败场景，按官方要求提交真实验证材料获得正式权限。完成前不标记“历史数据接入成功”。

```bash
./gradlew --no-parallel --max-workers=1 \
  :core:test :app:testPersonalOfflineDebugUnitTest :app:testPersonalEnhancedDebugUnitTest \
  :app:lintPersonalOfflineDebug :app:lintPersonalEnhancedDebug \
  :app:assemblePersonalOfflineDebug :app:assemblePersonalEnhancedDebug
./gradlew :app:assemblePersonalEnhancedRelease
```

Release签名仅由本人构建环境提供。没有原keystore时的构建仅验证编译/R8，不能代替个人签名覆盖升级验收。本次未向仓库提交真实平台配置或账号凭据。

## 代码行为与数据口径

- `personalEnhanced/.../health/HmsHuaweiGateway.kt`调用真实SDK：`requestAuthorizationIntent`、`parseHealthKitAuthResultFromIntent`、`silentSignIn`、`getHealthAppAuthorization`、`readDailySummation`、`getActivityRecord`、`getHealthRecord`、`cancelAuthorization`。不以启动第三方应用代替授权。
- `main/.../health/HuaweiHealth.kt`管理状态及最小Scope集合；可读取范围取平台配置和真实用户Scope的交集。每次前台同步重新核验，不从本地持久化标记恢复“已授权”。令牌交由SDK管理，不持久化或记录token。
- 最近7个归档自然日采用时区真实日界，支持23/25小时夏令时日。未获周历史时，不查询授权前范围；首个不完整授权日不伪装为完整日步数。空数据不补0，真实0保留。SDK若返回其他时区日界则不导入成本归档日。
- 每次请求上限200条，超限拒绝整页，不静默截断。原生运动/睡眠接口分页及跨设备完整性尚未获真机确认，结果标为`complete=false`，按稳定账号/类型/原生ID幂等更新，不把空页当作全部删除。**全量历史与远端删除同步尚未完成**；接口留出完整快照标志供后续验证后扩展。
- 华为和Health Connect仍使用既有`HEALTH`大类，通过payload.provider隔离；旧V1无provider行按Health Connect识别。快照只对账本provider/类型，不覆盖另一个平台。Room表结构/版本保持V1，无破坏性迁移。
- 同日有效日聚合优先级：华为 → Health Connect → 本机硬件增量。总数不相加、不以最大值替代来源选择；同来源最新修正可下降或为0。统计和时间轴采用同一选择。本机原始计步继续保存，华为撤回后可以回退。
- 本地撤权先使在途响应失效，停止读取并清理华为原始观察、派生片段和日快照，再向华为撤权。未获官方确认时保留“已停止读取，撤权待重试”，进程重启不自动重连，不删除华为源账号数据。外部撤权在下次前台核验发现，断网不冒充已确认撤权。
- 账号切换清理上个账号的华为导入；部分权限撤回清理对应类型。暂停、停止、删除防重导继续使用原generation与排除规则。仅前台按已有同步节奏读取，不增加常驻服务或后台授权轮询。

## 尚未完成及验收

| 项目 | 状态 |
|---|---|
| App ID、帐号服务开通、签名平台登记 | 未取得本人平台结果；指纹沿用已有登记文档 |
| 个人应用上架、Health服务/数据权限审核 | 未完成；个人侧载不满足公开上架前提 |
| 睡眠高阶资格及批准 | 未完成；个人资质不默认满足 |
| HBN-AL80官方授权、各类真实数据读取 | 未执行，自动化测试不能替代 |
| 拒绝、取消、部分授权、外部撤权、撤权重试 | 代码/自动化测试覆盖；真实系统与账号待验收 |
| 历史可见范围、SDK日界、穿戴融合、地区限制 | 待获批后逐项与运动健康App对照 |
| 原生分页完整性及远端删改对账 | 仅有界读取/幂等准备；完整性与远端删除同步未完成 |
| 个人签名覆盖安装、原数据库保留、长期耗电 | 配置保持；无私钥/设备，未执行实机验收 |

真实验收记录设备/系统、HMS Core/运动健康版本、App ID、APK指纹、获批Scope、用户选择、查询时区/日期、返回数量和错误码，不记录密码、token、私钥及完整健康明细。状态须区分“已授权”和“暂无数据”，不能用测试替身、mock或样例截图当接入证据。

## 官方依据

- [申请资质说明](https://developer.huawei.com/consumer/cn/doc/HMSCore-Guides/application-qualification-description-0000001425636570)：个人上架、基础/高阶和主体要求，2026-01-23更新。
- [数据开放总览](https://developer.huawei.com/consumer/cn/doc/HMSCore-Guides/data_description-0000001467889369)：类型和开放等级，按实际审核结果确认。
- [Android申请服务](https://developer.huawei.com/consumer/cn/doc/HMSCore-Guides/apply-kitservice-0000001050071707)、[申请帐号服务](https://developer.huawei.com/consumer/cn/doc/HMScore-Guides/apply-id-0000001050069756)：应用与服务开通。
- [Android版本说明](https://developer.huawei.com/consumer/cn/doc/HMSCore-Guides/version-change-history-0000001057072287)、[SDK集成](https://developer.huawei.com/consumer/cn/doc/HMSCore-Guides/integrating-sdk-0000001050069724)：本次正文获取不稳定，核验边界见上文。
- [实际解析的官方6.16.0.300 POM](https://developer.huawei.com/repo/com/huawei/hms/health/6.16.0.300/health-6.16.0.300.pom)：AAR SHA-256为`ac614821a2c7360535d252c3ba3a6a8ba6d3c93bb02128bdbe0e8eb03fa51a8d`。
- [Scopes API](https://developer.huawei.com/consumer/jp/doc/HMSCore-References/scopes-0000001050092713)：步数、运动记录、睡眠和周/月/年历史；本次也核对6.16 AAR实际常量。
- [官方Android Kotlin示例](https://github.com/HMS-Core/hms-health-demo-kotlin)：授权、App ID、取消授权、读取控制器。旧示例版本不是本应用接入凭据。
- [基础能力FAQ](https://developer.huawei.com/consumer/cn/doc/HMSCore-Guides/faq-0000001477339981)：平台审核与用户授权不能互相替代。
- [第三方数据同步](https://consumer.huawei.com/cn/support/content/zh-cn01115094/)、[第三方计步排查](https://consumer.huawei.com/cn/support/content/zh-cn00420068/)：目标系统授权与后台说明。

本机持续计步仍需系统允许记录服务后台运行，后台冻结或强制停止后不保证补回。账号历史补采也受资格、授权、穿戴同步与平台可见范围限制。
