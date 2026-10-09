# media-picker

基于 Android 官方 [Photo Picker](https://developer.android.com/training/data-storage/shared/photopicker) 的图片/视频选择封装。无需申请存储权限，支持数量、大小、时长过滤，以及可选拷贝到应用私有目录。

## 特性

- 封装官方 `PickVisualMedia` / `PickMultipleVisualMedia`，无需 READ_EXTERNAL_STORAGE
- 链式 API：单选/多选、仅图片/仅视频/图视混合
- 选择后过滤：单文件大小上限、视频时长上限、视频数量上限
- `copyToPath`：可选将 content Uri 拷贝到应用外部私有目录并返回绝对路径
- 独立工具：`MediaCopyUtil`（拷贝）、`MediaPickerFileUtil`（删除/清空）

## 环境要求

| 项 | 要求 |
| --- | --- |
| minSdk | 21 |
| compileSdk | 34 |
| Java | 8（库本身）；JitPack 构建使用 JDK 17 |

官方 Photo Picker 在 Android 13+ 为系统组件；低版本由 Google Play 服务 / AndroidX 回退实现。

## 接入

### 1. 仓库

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}
```

### 2. 依赖

```gradle
dependencies {
    implementation 'com.github.hss01248.media-picker:android-photo-picker:1.0.1'
}
```

> Tag / 版本以 [Releases](https://github.com/hss01248/media-picker/releases) 或 JitPack 为准。本地调试可用 `implementation project(':android-photo-picker')`。

## 快速使用

```java
MediaPicker.create(activity)
    .maxSelectNum(9)          // 最多可选数量（图+视合计），默认 1
    .maxVideoSelectNum(2)     // 最多可选视频数；0 表示不单独限制视频数
    .queryMaxFileSize(20)     // 单文件上限，单位 MB；0 不限制
    .videoMaxSecond(15)       // 视频时长上限，单位秒；0 不限制
    .withImage(true)          // 是否可选图片，默认 true
    .withVideo(true)          // 是否可选视频，默认 true
    .copyToPath(false)        // false：返回 Uri 字符串；true：拷贝后返回绝对路径
    .forResult(new TakePhotoListener() {
        @Override
        public void onSuccess(List<String> paths) {
            // copyToPath=false → content://...
            // copyToPath=true  → /storage/emulated/0/Android/data/.../media_picker/...
        }

        @Override
        public void onFail(String path, String msg) {
            // 全部被过滤、拷贝失败等
        }

        @Override
        public void onCancel() {
            // 用户取消
        }
    });
```

`withImage` 与 `withVideo` 不能同时为 `false`。

## API 说明

### MediaPicker

| 方法 | 说明 | 默认 |
| --- | --- | --- |
| `maxSelectNum(n)` | 最多可选数量（图片+视频合计） | 1 |
| `maxVideoSelectNum(n)` | 最多可选视频数量；`0` 不单独限制 | 1 |
| `queryMaxFileSize(mb)` | 单文件大小上限（MB）；`0` 不限制 | 0 |
| `videoMaxSecond(sec)` | 视频时长上限（秒）；`0` 不限制 | 0 |
| `withImage(bool)` | 是否允许选图片 | true |
| `withVideo(bool)` | 是否允许选视频 | true |
| `copyToPath(bool)` | 是否拷贝到私有目录并返回绝对路径 | false |
| `forResult(listener)` | 启动官方 Photo Picker | — |

### TakePhotoListener

```java
void onSuccess(List<String> paths);
default void onFail(String path, String msg) {}
default void onCancel() {}
```

### 过滤与回调规则

官方 Photo Picker 无法在 UI 层限制「视频数量 / 文件大小 / 时长」，因此在选择完成后过滤：

1. **先过滤，再拷贝**（开启 `copyToPath` 时只拷贝合法项）
2. 部分超限、仍有合法项：Toast 汇总超限说明，合法项走 `onSuccess`
3. 全部被过滤：汇总信息经 `onFail` 返回（不 Toast）
4. 用户取消：`onCancel`

> `maxVideoSelectNum` 与大小/时长限制一样，属于选择后过滤，而非系统选择器 UI 限制。

### MediaCopyUtil（可独立使用）

将 `content://` / `file://` Uri 拷贝到应用外部私有目录：

- 图片 → `{externalFilesDir}/Pictures/media_picker/`
- 视频 → `{externalFilesDir}/Movies/media_picker/`

```java
// 单个
String path = MediaCopyUtil.copyUriToExternalPrivateDir(context, uri);
String path = MediaCopyUtil.copyUriToExternalPrivateDir(context, "content://...");

// 批量（跳过失败项）
List<String> paths = MediaCopyUtil.copyUriStringsToExternalPrivateDir(context, uriStrings);

// 是否为本库拷贝目录内的路径
boolean ok = MediaCopyUtil.isCopiedMediaPath(context, path);
```

### MediaPickerFileUtil

仅允许操作本库 `media_picker` 拷贝目录内的文件：

```java
MediaPickerFileUtil.deleteCopiedFile(context, path);
MediaPickerFileUtil.deleteCopiedFiles(context, paths);
MediaPickerFileUtil.clearAllCopiedFiles(context); // 清空全部拷贝
```

## 演示 App

模块 `:app`（应用名 media-picker）提供常见场景按钮：单选/多选 Uri、拷贝 Path、大小与时长限制、手动拷贝、删除与清空。

```bash
./gradlew :app:installDebug
```

## 模块结构

```
media-picker/
├── android-photo-picker/   # 库模块（Photo Picker 封装，JitPack 发布）
├── screenshoot/            # 辅助库模块
└── app/                    # 演示应用
```

## License

[MIT](LICENSE)
