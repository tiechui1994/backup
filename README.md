# Android 照片备份服务

一个完整的 Android 后台定时备份照片文件的服务，采用 WorkManager + 前台服务通知的最佳实践。

## 功能特性

- ✅ **WorkManager + 前台服务通知**：使用 WorkManager 执行后台任务，前台服务显示备份进度
- ✅ **可配置备份文件夹**：支持设置要备份的照片文件夹路径
- ✅ **增量备份**：使用 SQLite (Room) 记录已上传图片的 MD5，避免重复备份
- ✅ **异常处理**：处理文件 IO 错误、权限错误等异常情况
- ✅ **权限管理**：动态申请 READ_MEDIA_IMAGES 权限

## 项目结构

```
app/src/main/java/com/example/photobackup/
├── data/                    # 数据层
│   ├── BackedUpPhoto.kt    # 已备份照片实体
│   ├── BackedUpPhotoDao.kt # 数据访问对象
│   └── PhotoBackupDatabase.kt # Room 数据库
├── worker/                  # WorkManager Worker
│   └── PhotoBackupWorker.kt # 备份任务执行器
├── service/                 # 服务层
│   └── PhotoBackupForegroundService.kt # 前台服务
├── manager/                 # 管理类
│   └── PhotoBackupManager.kt # 备份服务管理器
├── util/                    # 工具类
│   ├── FileHashUtil.kt     # 文件 MD5 计算
│   └── PermissionHelper.kt # 权限管理
├── api/                     # API 层
│   └── LocalBackupApi.kt   # 本地文件备份 API
└── MainActivity.kt         # 主界面
```

## 使用说明

### 1. 权限申请

在调用 `setupPeriodicBackup` 前，需要动态申请 `READ_MEDIA_IMAGES` 权限（Android 13+）或 `READ_EXTERNAL_STORAGE` 权限（Android 12 及以下）。

```kotlin
if (!PermissionHelper.hasReadMediaImagesPermission(context)) {
    // 请求权限
    permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
}
```

### 2. 配置并启动备份

```kotlin
val backupManager = PhotoBackupManager.getInstance(context)

val config = PhotoBackupManager.BackupConfig(
    backupFolder = "/storage/emulated/0/DCIM/Camera", // 要备份的照片源目录
    backupDestination = "/storage/emulated/0/PhotoBackup", // 备份目标目录
    intervalHours = 24, // 24 小时执行一次
    requiresNetwork = false, // 本地备份不需要网络
    requiresCharging = false // 是否仅在充电时执行
)

backupManager.setupPeriodicBackup(config)
```

### 3. 立即触发备份（测试用）

```kotlin
backupManager.triggerBackupNow(config)
```

### 4. 停止备份

```kotlin
backupManager.cancelPeriodicBackup()
```

## 增量备份机制

- 使用文件的 MD5 值作为唯一标识
- 备份前检查数据库中是否已存在该 MD5
- 已备份的文件会被跳过，只备份新文件
- 备份成功后，将文件信息（MD5、路径、大小等）保存到数据库

## 异常处理与重试

WorkManager 会自动处理以下情况的重试：

- **IO 错误**：文件读写错误时会记录日志
- **权限错误**：目录权限不足时会返回失败
- **文件已存在**：如果目标文件已存在，会自动添加时间戳避免覆盖

本地文件备份通常不需要重试，因为文件系统错误通常是永久性的。重试逻辑在 `PhotoBackupWorker.isRetryableError()` 中实现。

## 前台服务通知

- 备份任务执行时，会启动前台服务并显示通知
- 通知显示备份进度（已处理文件数/总文件数）
- 备份完成后，通知会显示统计信息（成功、跳过、失败数量）
- 3 秒后自动停止前台服务

## 注意事项

1. **最小间隔**：PeriodicWorkRequest 的最小间隔是 15 分钟，即使设置更小的值也会被系统调整为 15 分钟
2. **备份逻辑**：当前测试版本将文件备份到用户自定义的本地目录，使用 `LocalBackupApi.backupPhoto()` 实现文件复制
3. **权限**：确保在 AndroidManifest.xml 中声明了所有必要的权限，并且目标目录有写入权限
4. **前台服务类型**：Android 14+ 需要指定前台服务类型（dataSync）
5. **文件冲突**：如果目标目录中已存在同名文件，会自动添加时间戳避免覆盖
6. **存储空间**：确保目标目录有足够的存储空间

## 依赖库

- WorkManager: 后台任务调度
- Room: 本地数据库存储
- Coroutines: 协程支持
- Material Components: UI 组件

## CI/CD

项目配置了 GitHub Actions 自动构建工作流：

### 工作流说明

1. **CI Workflow** (`.github/workflows/ci.yml`)
   - 自动运行 Lint 检查、单元测试和构建
   - 在 Push 或 Pull Request 时触发
   - 生成 Debug 和 Release APK

2. **Android Build Workflow** (`.github/workflows/android_build.yml`)
   - 构建 Debug 和 Release APK
   - 支持手动触发

3. **Signed Release Workflow** (`.github/workflows/android_build_signed.yml`)
   - 构建并签名 Release APK 和 AAB
   - 在推送版本标签时触发（如 `v1.0.0`）
   - 自动创建 GitHub Release

### 使用 GitHub Actions

1. **查看构建状态**：在 GitHub 仓库的 Actions 标签页查看构建历史
2. **下载 APK**：构建完成后，在 Actions 页面下载构建产物
3. **发布版本**：创建并推送版本标签即可自动构建签名版本
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```

详细说明请参考 [.github/workflows/README.md](.github/workflows/README.md)

## 开发环境

- Android Studio Hedgehog | 2023.1.1+
- Kotlin 1.9.20+
- Gradle 8.2.0+
- Min SDK: 24
- Target SDK: 34


