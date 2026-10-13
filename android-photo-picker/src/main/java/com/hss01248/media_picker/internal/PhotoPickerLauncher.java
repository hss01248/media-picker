package com.hss01248.media_picker.internal;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.blankj.utilcode.util.ActivityUtils;
import com.blankj.utilcode.util.LogUtils;
import com.hss01248.media_picker.MediaCopyUtil;
import com.hss01248.media_picker.R;
import com.hss01248.media_picker.TakePhotoListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 启动官方 Photo Picker 并处理结果（经 {@link PickerResultFragment}，保留 ClipData）。
 */
public final class PhotoPickerLauncher {

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private PhotoPickerLauncher() {
    }

    /**
     * 调试/自测：跳过系统 Picker，直接对给定 Uri 列表走过滤与确认弹窗。
     */
    public static void processUris(@NonNull Context context,
                                   @NonNull PhotoPickerConfig config,
                                   @NonNull List<Uri> uris,
                                   @NonNull TakePhotoListener listener) {
        if (uris == null || uris.isEmpty()) {
            listener.onCancel();
            return;
        }
        IO.execute(() -> processOnBackground(context, config, listener, uris));
    }

    public static void launch(@NonNull Context context,
                              @NonNull PhotoPickerConfig config,
                              @NonNull TakePhotoListener listener) {
        FragmentActivity activity = resolveActivity(context);
        if (activity == null) {
            listener.onFail("", "FragmentActivity required");
            return;
        }

        final boolean multi = config.maxSelectNum > 1;
        final Intent intent;
        // 系统 / OEM / GMS Photo Picker 可用时走官方 contract；不可用时勿再调
        // PickVisualMedia.createIntent（其内部仍会落 ACTION_OPEN_DOCUMENT，且 ImageOnly
        // 只设 type=image MIME，Android 9 DocumentsUI 常显示空的「近期图片」）。
        if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(activity)) {
            final ActivityResultContracts.PickVisualMedia.VisualMediaType mediaType =
                    resolveMediaType(config);
            final PickVisualMediaRequest request = new PickVisualMediaRequest.Builder()
                    .setMediaType(mediaType)
                    .build();
            if (multi) {
                int maxItems = Math.min(Math.max(2, config.maxSelectNum), 100);
                intent = new ActivityResultContracts.PickMultipleVisualMedia(maxItems)
                        .createIntent(activity, request);
            } else {
                intent = new ActivityResultContracts.PickVisualMedia()
                        .createIntent(activity, request);
            }
        } else {
            intent = createOpenDocumentIntent(config, multi);
        }

        final Runnable start = () -> {
            try {
                PickerResultFragment.get(activity).launch(intent, new PickerResultFragment.ResultCallback() {
                    @Override
                    public void onActivityResult(int resultCode, Intent data) {
                        if (resultCode != Activity.RESULT_OK) {
                            listener.onCancel();
                            return;
                        }
                        List<Uri> uris = parseUris(multi, data);
                        if (uris.isEmpty()) {
                            listener.onCancel();
                            return;
                        }
                        IO.execute(() -> processOnBackground(activity, config, listener, uris));
                    }

                    @Override
                    public void onLaunchFailed(@NonNull Throwable e) {
                        LogUtils.e(e);
                        listener.onFail("", e.getMessage() != null
                                ? e.getMessage()
                                : "Photo Picker not found");
                    }
                });
            } catch (Throwable e) {
                LogUtils.e(e);
                listener.onFail("", e.getMessage() != null
                        ? e.getMessage()
                        : "Photo Picker not found");
            }
        };
        // 重新选择时 Dialog 刚 dismiss，延后一帧再 launch，避免与窗口切换抢生命周期
        MAIN.post(start);
    }

    private static List<Uri> parseUris(boolean multi, Intent data) {
        if (multi) {
            List<Uri> parsed = new ActivityResultContracts.PickMultipleVisualMedia()
                    .parseResult(Activity.RESULT_OK, data);
            return parsed == null ? new ArrayList<>() : parsed;
        }
        Uri uri = new ActivityResultContracts.PickVisualMedia()
                .parseResult(Activity.RESULT_OK, data);
        return uri == null ? new ArrayList<>() : singleton(uri);
    }

    private static FragmentActivity resolveActivity(Context context) {
        if (context instanceof FragmentActivity) {
            return (FragmentActivity) context;
        }
        Activity top = ActivityUtils.getTopActivity();
        if (top instanceof FragmentActivity) {
            return (FragmentActivity) top;
        }
        return null;
    }

    private static ActivityResultContracts.PickVisualMedia.VisualMediaType resolveMediaType(
            PhotoPickerConfig config) {
        if (config.withImage && config.withVideo) {
            return ActivityResultContracts.PickVisualMedia.ImageAndVideo.INSTANCE;
        }
        if (config.withVideo) {
            return ActivityResultContracts.PickVisualMedia.VideoOnly.INSTANCE;
        }
        return ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE;
    }

    /**
     * 自行拼 SAF {@link Intent#ACTION_OPEN_DOCUMENT}，替代 AndroidX Photo Picker 的低版本回退。
     * <p>
     * 原因：{@code PickVisualMedia.createIntent} 在 Photo Picker 不可用时也会落到
     * OPEN_DOCUMENT，但对 ImageOnly 只设置 {@code Intent.setType("image/...")}（单一 MIME）。
     * Android 9 等机型的 DocumentsUI 会据此进入「手机上的近期图片」分支，列表经常为空；
     * 而同时选图+视频时官方回退使用通配 type + {@link Intent#EXTRA_MIME_TYPES}，界面正常。
     * 因此不可用时不再调用官方 API，统一自建：通配 type + EXTRA_MIME_TYPES 限定目标类型。
     * 选完后的过滤、确认弹窗、拷贝仍由 {@link #processOnBackground} 等现有逻辑处理。
     */
    @NonNull
    private static Intent createOpenDocumentIntent(@NonNull PhotoPickerConfig config,
                                                   boolean multi) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // 必须用通配 type；目标类型放在 EXTRA_MIME_TYPES，勿单独 setType(image/...)
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, resolveMimeTypes(config));
        if (multi) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        return intent;
    }

    /** 按 withImage / withVideo 生成 EXTRA_MIME_TYPES 数组。 */
    @NonNull
    private static String[] resolveMimeTypes(@NonNull PhotoPickerConfig config) {
        if (config.withImage && config.withVideo) {
            return new String[]{"image/*", "video/*"};
        }
        if (config.withVideo) {
            return new String[]{"video/*"};
        }
        return new String[]{"image/*"};
    }

    private static void processOnBackground(Context context,
                                            PhotoPickerConfig config,
                                            TakePhotoListener listener,
                                            List<Uri> uris) {
        FilterResult result = filter(context, config, uris);

        if (!result.hasRejected()) {
            deliverAccepted(context, config, listener, result.accepted);
            return;
        }

        MAIN.post(() -> showFilterConfirm(context, config, listener, result));
    }

    private static void showFilterConfirm(Context context,
                                          PhotoPickerConfig config,
                                          TakePhotoListener listener,
                                          FilterResult result) {
        FragmentActivity activity = resolveActivity(context);
        if (activity == null || activity.isFinishing()) {
            notifyFail(listener, "", context.getString(R.string.media_picker_fail_all_filtered));
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= 17 && activity.isDestroyed()) {
            notifyFail(listener, "", context.getString(R.string.media_picker_fail_all_filtered));
            return;
        }

        FilterConfirmDialog.show(activity, config, result, new FilterConfirmDialog.Callback() {
            @Override
            public void onCancel() {
                listener.onCancel();
            }

            @Override
            public void onReselect() {
                // 不在此同步 launch：交由 launch() 内部 MAIN.post，避开 Dialog dismiss 竞态
                launch(activity, config, listener);
            }

            @Override
            public void onConfirm() {
                IO.execute(() -> deliverAccepted(activity, config, listener, result.accepted));
            }
        });
    }

    private static void deliverAccepted(Context context,
                                        PhotoPickerConfig config,
                                        TakePhotoListener listener,
                                        List<Uri> accepted) {
        if (accepted.isEmpty()) {
            notifyFail(listener, "", context.getString(R.string.media_picker_fail_all_filtered));
            return;
        }

        FilterSkipCollector copySkips = new FilterSkipCollector();
        List<String> successPaths = new ArrayList<>(accepted.size());
        for (Uri uri : accepted) {
            try {
                if (config.copyToPath) {
                    successPaths.add(MediaCopyUtil.copyUriToExternalPrivateDir(context, uri));
                } else {
                    successPaths.add(uri.toString());
                }
            } catch (Exception e) {
                LogUtils.e(e);
                copySkips.other();
            }
        }

        if (successPaths.isEmpty()) {
            String summaryMsg = copySkips.buildSummaryMessage(context, config);
            String failMsg = TextUtils.isEmpty(summaryMsg)
                    ? context.getString(R.string.media_picker_fail_all_filtered)
                    : summaryMsg;
            notifyFail(listener, "", failMsg);
            return;
        }
        MAIN.post(() -> listener.onSuccess(successPaths));
    }

    private static FilterResult filter(Context context,
                                       PhotoPickerConfig config,
                                       List<Uri> uris) {
        List<Uri> accepted = new ArrayList<>();
        List<RejectedItem> rejected = new ArrayList<>();
        FilterSkipCollector skips = new FilterSkipCollector();
        int videoKept = 0;
        int maxVideo = config.maxVideoSelectNum <= 0 ? Integer.MAX_VALUE : config.maxVideoSelectNum;
        long maxBytes = config.maxFileSizeMb > 0
                ? (long) (config.maxFileSizeMb * 1024f * 1024f)
                : 0L;
        long maxDurationMs = config.videoMaxSecond > 0
                ? config.videoMaxSecond * 1000L
                : 0L;

        for (Uri uri : uris) {
            if (uri == null) {
                continue;
            }
            try {
                boolean video = MediaUriHelper.isVideo(context, uri);
                boolean image = MediaUriHelper.isImage(context, uri);

                if (video && !config.withVideo) {
                    reject(rejected, skips, uri, FilterRejectReason.OTHER);
                    continue;
                }
                if (image && !config.withImage) {
                    reject(rejected, skips, uri, FilterRejectReason.OTHER);
                    continue;
                }
                if (!video && !image) {
                    reject(rejected, skips, uri, FilterRejectReason.OTHER);
                    continue;
                }

                if (video) {
                    if (videoKept >= maxVideo) {
                        reject(rejected, skips, uri, FilterRejectReason.EXCEED_MAX_VIDEO);
                        continue;
                    }
                    if (maxDurationMs > 0) {
                        long duration = MediaUriHelper.getVideoDurationMs(context, uri);
                        if (duration < 0) {
                            reject(rejected, skips, uri, FilterRejectReason.OTHER);
                            continue;
                        }
                        if (duration > maxDurationMs) {
                            reject(rejected, skips, uri, FilterRejectReason.VIDEO_TOO_LONG);
                            continue;
                        }
                    }
                }

                if (maxBytes > 0) {
                    long size = MediaUriHelper.getFileSize(context, uri);
                    if (size < 0) {
                        reject(rejected, skips, uri, FilterRejectReason.OTHER);
                        continue;
                    }
                    if (size > maxBytes) {
                        reject(rejected, skips, uri, FilterRejectReason.FILE_TOO_LARGE);
                        continue;
                    }
                }

                if (accepted.size() >= config.maxSelectNum) {
                    reject(rejected, skips, uri, FilterRejectReason.EXCEED_MAX_SELECT);
                    continue;
                }

                accepted.add(uri);
                if (video) {
                    videoKept++;
                }
            } catch (Exception e) {
                LogUtils.e(e);
                reject(rejected, skips, uri, FilterRejectReason.OTHER);
            }
        }
        return new FilterResult(accepted, rejected, skips);
    }

    private static void reject(List<RejectedItem> rejected,
                               FilterSkipCollector skips,
                               Uri uri,
                               FilterRejectReason reason) {
        rejected.add(new RejectedItem(uri, reason));
        switch (reason) {
            case FILE_TOO_LARGE:
                skips.fileTooLarge();
                break;
            case VIDEO_TOO_LONG:
                skips.videoTooLong();
                break;
            case EXCEED_MAX_VIDEO:
                skips.exceedMaxVideo();
                break;
            case EXCEED_MAX_SELECT:
                skips.exceedMaxSelect();
                break;
            case OTHER:
            default:
                skips.other();
                break;
        }
    }

    private static void notifyFail(TakePhotoListener listener, String path, String msg) {
        MAIN.post(() -> listener.onFail(path, msg));
    }

    private static List<Uri> singleton(Uri uri) {
        List<Uri> list = new ArrayList<>(1);
        list.add(uri);
        return list;
    }
}
