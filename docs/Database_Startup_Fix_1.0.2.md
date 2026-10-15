# 1.0.2 · 数据库打开失败修复

更新日期：2026-10-15（+08:00）。版本 1.0.2 / versionCode 3，包名 `com.personal.wo`，Room V1 与原个人签名保持一致。

## 已确认的原因

用户回传 1.0.1、Android 12 / API 31 的启动诊断。堆栈中的 R8 映射标识与交付离线包完全一致：

`cbb28332ba9ee297b858cd2b63211d424a935d2a33794c7bc293578db7286263`

该映射将 `c7.a.a(...:8)` 还原为 `com.personal.wo.Graph$1.onOpen()`，对应原 `WoApplication.kt` 第 65 行。此回调唯一的 SQL 调用为：

```kotlin
db.execSQL("PRAGMA busy_timeout=5000")
```

这条 PRAGMA 即使在赋值时也返回 `timeout` 列。真实 SQLite 原生接口验证得到 `sqlite3_step() == SQLITE_ROW (100)`，结果值为 5000。Android/SQLCipher 的 execSQL 使用不接收结果行的更新/删除执行路径；用户堆栈中的 `nativeExecuteForChangedRowCount` 与此吻合。固定依赖 SQLCipher 4.10.0 的原生库也包含“应使用 query/rawQuery”的错误契约文本。

因此失败发生在数据库打开回调，早于首页载入完成。原来的异常边界让错误能够显示，但没有修正这条初始化 SQL 的调用方式。

## 修改

将数据库回调独立为 `DatabaseOpenCallback`，由生产 Graph 与设备测试共同引用：

```kotlin
db.query("PRAGMA busy_timeout=5000").use { cursor ->
    cursor.moveToFirst()
}
```

查询游标可能延迟执行，必须读取首行才能确保语句被执行；`use` 在成功和读取异常时都关闭游标。没有重建数据库、改变 schema、重置密钥或清理生活记录。

两个构建变体同步提升为 1.0.2 (3)，使用原证书，可覆盖升级。

## 验证边界

- 新增 2 项生产回调的 JVM 契约测试：旧 execSQL 实现下两项失败，修正为查询后通过；覆盖结果消费及读取失败时游标释放。
- `scripts/verify_pragmas.py` 使用真实宿主 SQLite 验证结果列、5000ms 设置、原生 SQLITE_ROW 返回、已有记录保持和完整性；它不冒充 Android SQLCipher 设备运行测试。
- 原 43 项 JVM 测试继续运行，总数为 45 项；最终构建结果见 [Verification.md](Verification.md)。
- Android 数据库/备份测试现在共 4 项，加入生产回调、WAL 与加密数据库重新打开保留记录的测试。只完成编译，尚未在目标手机执行。
- 1.0.2 的实际设备打开效果仍需覆盖安装后确认。诊断功能与原有隐私边界保留。

旧调用下的预期失败记录在 [before-fix/](reports/v1.0.2/before-fix/)，修复后的结果在 [reports/v1.0.2/](reports/v1.0.2/)。已发布构建的混淆映射另行归档，用于后续诊断还原。

## 参考

- [SQLite busy_timeout 文档](https://www.sqlite.org/pragma.html#pragma_busy_timeout)
- [Android SQLiteDatabase.execSQL 契约](https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase#execSQL(java.lang.String))
