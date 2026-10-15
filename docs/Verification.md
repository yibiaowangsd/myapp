# 我 · Android 1.0.2 验证结果

日期：2026-10-15T09:41:38.819635+08:00

| 检查 | 实际结果 |
|---|---|
| 版本 / 包名 | 1.0.2 (3) / com.personal.wo；Room V1 |
| 用户启动诊断 | 与 1.0.1 离线包 R8 映射哈希完全一致，定位 onOpen 的 busy_timeout execSQL |
| 修复前回调契约回归 | 2 项失败，符合预期；它们是 JVM 数据库接口测试，不冒充设备运行 |
| 修复后 JVM 回归 | 45 项通过、0 失败/错误：领域/加密 35，启动处理 8，数据库打开回调 2 |
| 真实宿主 SQLite | busy_timeout 返回 timeout=5000；sqlite3_step 返回 SQLITE_ROW (100)；记录与完整性保持 |
| 两个 Release 构建 | 通过，Gradle 退出码 0 |
| Release 已编译回调 | 使用 query、moveToFirst、closeFinally，不含 execSQL 调用；混淆映射与 APK 内标识匹配 |
| Android Lint | 两个变体 0 错误；保留非阻断兼容/弃用告警，详情见 XML |
| 签名与覆盖升级 | 两个包签名验证通过，证书与 1.0.0 / 1.0.1 相同 |
| 离线网络权限 | 无 INTERNET |
| APK / 原生对齐 | zipalign -P16、ARM64 ELF PT_LOAD 至少 16 KiB 静态检查通过 |
| Room schema | 17 实体、来源唯一键、FTS4、完整性检查通过 |
| Android 设备测试 | 4 项已编译，未执行；包括生产回调重开加密库且保留记录 |
| 用户手机上的 1.0.2 启动 | 待覆盖安装确认；不把宿主 SQLite 或接口替身测试当作 SQLCipher 设备运行 |
| 耗电 / 7 日稳定性 / 200% 字体 / 16 KiB 设备运行 | 未测量 |

修复详情见 [数据库启动修复](Database_Startup_Fix_1.0.2.md)。测试、原生 SQLite 验证与字节码检查在 [reports/v1.0.2/](reports/v1.0.2/)；旧调用的预期失败在 [before-fix/](reports/v1.0.2/before-fix/)。历史验证保存在 [archive/](archive/)。

仅修改数据库打开回调的调用方式，没有清库、重置密钥或升级 schema。完整备份条数限制、独立救援入口、较早健康记录撤回与媒体关联等其他边界仍然存在，详见 README。
