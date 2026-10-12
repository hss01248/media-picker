package com.hss01248.media_picker;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.hss01248.media_picker.internal.PhotoPickerConfig;
import com.hss01248.media_picker.internal.PhotoPickerLauncher;

/**
 * Android 官方 Photo Picker 封装。
 * <pre>
 * MediaPicker.create(activity)
 *     .maxSelectNum(9)
 *     .maxVideoSelectNum(9)
 *     .queryMaxFileSize(20)
 *     .videoMaxSecond(15)
 *     .withVideo(true)
 *     .withImage(true)
 *     .copyToPath(false) // 默认 false：回调 Uri 字符串；true：拷贝后返回绝对路径
 *     .forResult(listener);
 * </pre>
 */
public class MediaPicker {

    private final PhotoPickerConfig config = new PhotoPickerConfig();
    private final Context context;

    private MediaPicker(Context context) {
        this.context = context;
    }

    public static MediaPicker create(@NonNull Activity activity) {
        return new MediaPicker(activity);
    }

    public static MediaPicker create(@NonNull FragmentActivity activity) {
        return new MediaPicker(activity);
    }

    public static MediaPicker create(@NonNull Context context) {
        return new MediaPicker(context);
    }

    /**
     * 最多可选数量（图片+视频合计）。默认 1。
     */
    public MediaPicker maxSelectNum(@IntRange(from = 1) int maxSelectNum) {
        config.maxSelectNum = Math.max(1, maxSelectNum);
        return this;
    }

    /**
     * 最多可选视频数量。默认 1；0 表示不单独限制视频数量。
     * 官方 Photo Picker 无法在 UI 层区分图片/视频上限；超出项选择后过滤，规则同文件大小/时长限制。
     */
    public MediaPicker maxVideoSelectNum(@IntRange(from = 0) int maxVideoSelectNum) {
        config.maxVideoSelectNum = Math.max(0, maxVideoSelectNum);
        return this;
    }

    /**
     * 单文件大小上限，单位 MB。0 表示不限制。
     * 超出项会被过滤：若仍有合法项则 Toast 汇总并 {@link TakePhotoListener#onSuccess}；
     * 若全部被过滤则汇总 msg 经 {@link TakePhotoListener#onFail} 返回（不 Toast）。
     */
    public MediaPicker queryMaxFileSize(@FloatRange(from = 0) float maxFileSizeMb) {
        config.maxFileSizeMb = Math.max(0f, maxFileSizeMb);
        return this;
    }

    /**
     * 视频时长上限，单位秒。0 表示不限制。
     * 超出项会被过滤：若仍有合法项则 Toast 汇总并 {@link TakePhotoListener#onSuccess}；
     * 若全部被过滤则汇总 msg 经 {@link TakePhotoListener#onFail} 返回（不 Toast）。
     */
    public MediaPicker videoMaxSecond(@IntRange(from = 0) int videoMaxSecond) {
        config.videoMaxSecond = Math.max(0, videoMaxSecond);
        return this;
    }

    /**
     * 是否允许选视频。默认 true。
     */
    public MediaPicker withVideo(boolean withVideo) {
        config.withVideo = withVideo;
        return this;
    }

    /**
     * 是否允许选图片。默认 true。
     */
    public MediaPicker withImage(boolean withImage) {
        config.withImage = withImage;
        return this;
    }

    /**
     * 是否将所选媒体拷贝到应用外部私有目录。默认 false。
     * 若开启，会在过滤完成后再拷贝，仅拷贝符合条件的项。
     * <ul>
     *   <li>false：{@link TakePhotoListener#onSuccess} 返回 Uri 字符串（如 content://...）</li>
     *   <li>true：拷贝后返回本地绝对路径；也可单独用 {@link MediaCopyUtil}</li>
     * </ul>
     */
    public MediaPicker copyToPath(boolean copyToPath) {
        config.copyToPath = copyToPath;
        return this;
    }

    /**
     * 启动官方 Photo Picker。
     */
    public void forResult(@NonNull TakePhotoListener listener) {
        if (!config.withImage && !config.withVideo) {
            listener.onFail("", "withImage and withVideo cannot both be false");
            return;
        }
        PhotoPickerLauncher.launch(context, config, listener);
    }
}
