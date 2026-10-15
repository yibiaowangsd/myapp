# 1.1.0 实际验证报告

验证时间：2026-10-15T13:52:48.727770+08:00。应用：com.personal.wo，versionName 1.1.0，versionCode 4，Room V1。

## 结论

两个个人签名Release包构建成功，59项JVM测试通过（其中8项为原生Compose交互/渲染测试）；两个变体Lint均无错误。已保存10张真实组件渲染图，并检查欢迎页、权限页、首页、图表、空状态、小屏大字体、深色、时间线、月历和我的入口。

本次未在HUAWEI HBN-AL80执行系统授权与后台运行测试；AndroidTest 4项只完成编译。Robolectric中的权限返回是受控状态，不能视为真实系统弹窗通过。

## 测试

| 测试组 | 数量 | 失败 |
|---|---:|---:|
| DayPresentationTest | 6 | 0 |
| EngineTest | 33 | 0 |
| StreamingCryptoTest | 2 | 0 |
| DatabaseOpenCallbackTest | 2 | 0 |
| StartupRecoveryTest | 8 | 0 |
| UiFlowTest | 8 | 0 |

报告见[reports/v1.1.0](../../reports/v1.1.0/)。图表测试覆盖重叠去重、分屏、截止时间裁剪、计划/未知/时钟冲突排除、部分授权和25小时日期。UI测试覆盖确认前不请求权限、拒绝可继续、状态更新后可开始、跳过、图表滚动与地点点击、空状态、360dp/1.5倍字体、深色、筛选、日期选择和设置入口。

## 安装包检查

- 两个包均为1.1.0 / 4，minSdk 29、targetSdk 36，仅ARM64 Release。
- 复用原个人签名，证书SHA-256为 c9b24de261dc30ebbc92da22f3f7826beb2c3015141c7b43d63b1ca27de9cb58。
- 离线版无INTERNET；增强版保留其手动增强接口。
- APK ZIP完整性、签名验证、ZIP 16KiB对齐和原生库ELF 16KiB对齐通过。
- 新的R8映射哈希已与对应APK DEX中的标识核对，映射另行归档用于诊断。
- Room V1和密钥结构不变；生成DDL、17个实体、FTS4、来源唯一键和完整性检查通过。1.0.2的数据库打开回调修复保留，其契约测试继续通过。

- [Wo_Android_v1.1.0_Offline.apk检查](../../Wo_Android_v1.1.0_Offline.apk.checks.json)：9127701字节；SHA-256：de097e8ca970aacb6e8156264a955bbd3176fb02081521ecd821b407374788cc。
- [Wo_Android_v1.1.0_Enhanced.apk检查](../../Wo_Android_v1.1.0_Enhanced.apk.checks.json)：9193253字节；SHA-256：5e64ead20826e3856e2dbd87ee75687f87618725d035affcffc4c0a3bfc705cc。

## 可视检查

图像来自生产Compose组件的Android View.draw/Skia渲染，API35。正常尺寸412×915dp，小屏尺寸360×800dp、大字体1.5倍。不是网页原型或重绘效果图，图中生活记录为明确标注的虚构样本。部分截图为内容组件，应用实际外层会包含顶部导航与页面边距。

[首页](../../ui/v1.1.0/03-today.png) · [授权页](../../ui/v1.1.0/02-permissions.png) · [图表与时间线](../../ui/v1.1.0/04-visuals.png) · [大字体](../../ui/v1.1.0/06-large-text.png) · [深色](../../ui/v1.1.0/07-dark.png) · [所有截图](../../ui/v1.1.0/)

原1.0.2验证报告归档在[archive/v1.0.2](../v1.0.2/)。其他首版边界（50,000条归档限制、系统后台策略、真实耗电与七日稳定性）见Design.md和Development.md，未因界面重构而声明完成。
