# 2.1.0

应用标识 `com.personal.wo`，versionCode 15。只保留 personalEnhanced 构建配置；原包名与 V1 数据库结构保留。

## 界面

- 底部仅首页、成就，设置位于两页右上角。
- 成就页直接展示勋章，移除“把日常，收成勋章”头部；筛选和自定义管理收进选项。
- 设置页使用直接的功能名称，保留记录来源、应用连接、生活趋势和备份能力。

## 数据互通

新增面向已连接 GitHub 的 ChatGPT 数据读取通道，详见 [ChatGPT_Data_Link.md](ChatGPT_Data_Link.md)。
使用单独的私有数据仓库，App 内没有新增聊天、OpenAI API 密钥或另一套对话记忆。

## 验证

- 本地数据库结构检查：通过，17个实体。
- 性能配置导入脚本：2项通过。
- Kotlin 协议、界面回归、Lint、APK 编译：由本分支 GitHub Actions 执行，见 PR 检查记录。
- 私有仓库真实记录联通：需要手机端配置私有数据仓库与令牌，并授权 ChatGPT GitHub 连接访问后验收。
- 本地环境无法下载 Gradle 与 Android SDK，因此不以本机静态检查替代 Android 编译结果。
