# 2.0 交互与实现参考

本轮以现有原生工程为基础，所有新界面、胶片插画、回忆卡和交互代码均在本仓库实现；没有复制未经许可的第三方产品源码或图标。

## 采用的实践

- Android Compose 官方性能建议：景深与位移在 graphicsLayer / 绘制阶段读取，避免逐帧重新整理事件；继续使用原有有界缩略图调度器。https://developer.android.com/develop/ui/compose/performance/bestpractices
- Android 官方 JetLagged 示例：参考自定义图形承载生活节奏的表达方式；本应用使用独立的水平事件胶片和原生Canvas回忆卡。https://github.com/android/compose-samples/tree/main/JetLagged
- Android 权限指导：先操作功能，再申请对应权限；不要求首次完成所有来源授权。https://developer.android.com/training/permissions/requesting
- Android 14 部分照片访问：照片权限支持用户仅开放部分媒体；访问范围由系统决定。https://developer.android.com/about/versions/14/changes/partial-photo-video-access
- Room 官方全文搜索：沿用FTS4与已有中文分词，日期条件和分类在数据库中筛选。https://developer.android.com/training/data-storage/room/defining-data
- Android 系统分享：通过FileProvider临时授予单个回忆卡URI访问，再由用户选择目标应用。https://developer.android.com/training/secure-file-sharing/share-file

## 产品取舍

自动回顾首先完成“导入已有照片—看见自己的时间轴—搜索找回—修正收藏”闭环。保留应用已有的运动、足迹、日历与成就，作为按需增强。照片内容尚未使用视觉模型识别，搜索只匹配记录文本与日期；不将关键词搜索标为通用AI理解。
