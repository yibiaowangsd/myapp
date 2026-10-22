# 2.2.0 · DeepSeek 与持久记忆

版本号16，沿用 `com.personal.wo` 与数据库V1。只保留增强版、首页与成就两个底部入口。

新增「设置 → DeepSeek 与记忆」：使用本机加密保管的 API Key，读取结构化生活记录、本人偏好和历史观察生成回顾；可选每日自动分析。记忆跨重启保留，纳入加密备份，可清空，并经既有私有 GitHub 数据互通提供给 ChatGPT。未新增 App 内会话。

具体范围、上下文预算、删除语义、接口依据及配置见 [DeepSeek 与长期记忆](DeepSeek_Memory.md)。Android 验证工作流执行核心与 App 回归测试、Lint、Debug / Release 构建及数据库兼容检查。有效密钥的真实请求需在手机上配置后验证。

正式覆盖安装需要原发布签名；CI 未签名 Release 不能作为覆盖升级安装包。
