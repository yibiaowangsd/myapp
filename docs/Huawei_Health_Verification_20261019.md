# 1.7.1 华为运动健康接入准备验证

日期：2026-10-19。基于`8dd3ddc`，新增31个不同测试用例；同一通用用例在两个变体中分别执行。完整分组数据见[JSON记录](Huawei_Health_Verification_20261019.json)，新增用例原始XML见[报告目录](reports/huawei-20261019/)。

## 已验证的代码

| 范围 | 执行次数 | 失败/错误/跳过 |
|---|---:|---|
| core | 100 | 0/0/0 |
| personalOfflineDebug | 111 | 0/0/0 |
| personalEnhancedDebug（含5项真实SDK数据模型测试） | 116 | 0/0/0 |

合计327次测试执行，对应216个不同用例。状态控制测试使用明确的测试替身，检验取消、拒绝、部分授权、权限失效、账号切换、撤权失败重试和在途结果失效；不构成真实华为授权证据。原生SDK测试仅使用官方AAR里的数据模型与Scope常量，不使用真实账号。

数据库测试执行真实Room查询及事务，测试身份键与生产AndroidKeyStore分离；不把Robolectric当作SQLCipher或AndroidKeyStore实机验证。覆盖来源隔离、重复同步、下降修正、真实0、空页、跨日睡眠、撤权后派生缓存清理、暂停代次和删除防重导。

双变体Lint均为0错误；警告计数分别为离线43、增强39。17张Room V1表、DDL、唯一来源键、FTS4及SQLite完整性检查通过；导出的V1 schema和`contracts/schema.sql`与基线一致。合并Manifest检查确认：离线没有INTERNET和华为App ID元数据，增强有二者，包名均为`com.personal.wo`。现有三页导航和计步测试通过，来源页渲染显示“待平台开通”，未出现假授权状态。

## 构建

双变体Debug及增强Release均构建成功；增强Release完成R8、资源压缩与Lint Vital。Release产物为未签名验证包，没有使用原个人私钥，不作为可覆盖升级的个人发行包。本次未向源码仓库提交APK。

本机使用JDK17、Android SDK36、Build Tools36.0.0、Gradle8.13；Robolectric运行API35。最终便携命令为：

```bash
./gradlew --no-daemon --no-parallel --max-workers=1 \
  :core:test :app:testPersonalEnhancedDebugUnitTest :app:testPersonalOfflineDebugUnitTest \
  :app:lintPersonalOfflineDebug :app:lintPersonalEnhancedDebug \
  :app:assemblePersonalOfflineDebug :app:assemblePersonalEnhancedDebug \
  :app:assemblePersonalEnhancedRelease
python scripts/verify_schema.py
```

本次保持依赖锁与SHA-256校验。首次本机构建为新增官方SDK生成校验信息，完成后再次以默认严格模式复核6个生产编译/运行类路径。SDK AAR校验值见[接入文档](Huawei_Health_Integration.md)。

[GitHub双变体CI记录](https://github.com/yibiaowangsd/myapp/actions/runs/39214262708)验证实现提交`69d1bd9`的测试、Lint和Debug构建。后续配置另加入了增强Release门禁；上述CI运行不冒充已验证后加的Release配置。

## 明确未执行

未取得真实App ID、Health Service Kit开通/权限批准结果，未执行华为账号授权或读取任何真实步数、运动、睡眠数据；未验证官方平台实际最新SDK公告的完整版本列表。个人开发者应用上架及睡眠高阶资质限制见[申请说明](Huawei_Health_Integration.md)。

没有原个人签名私钥和目标手机，未执行个人签名覆盖安装、HBN-AL80上API31兼容性、实际部分授权/外部撤权、历史可见范围、穿戴融合、原生分页完整性、远端删除同步与耗电验收。Release编译不代替这些实机门槛。
