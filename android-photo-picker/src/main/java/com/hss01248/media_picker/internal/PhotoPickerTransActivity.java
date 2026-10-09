package com.hss01248.media_picker.internal;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import com.blankj.utilcode.util.LogUtils;
import com.hss01248.media_picker.MediaCopyUtil;
import com.hss01248.media_picker.R;
import com.hss01248.media_picker.TakePhotoListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 透明 Activity：承载 Photo Picker 的 Activity Result，并完成拷贝/过滤。
 */
public class PhotoPickerTransActivity extends ComponentActivity {

    private static final String EXTRA_CONFIG = "extra_photo_picker_config";
    private static final String EXTRA_REQUEST_ID = "extra_photo_picker_request_id";
    private static final String STATE_LAUNCHED = "state_picker_launched";

    private PhotoPickerConfig config;
    private String requestId;
    private TakePhotoListener listener;
    private boolean launched;
    private boolean finishedWithCallback;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ActivityResultLauncher<PickVisualMediaRequest> singleLauncher;
    private ActivityResultLauncher<PickVisualMediaRequest> multiLauncher;

    public static void start(Context context, PhotoPickerConfig config, TakePhotoListener listener) {
        String requestId = PhotoPickerCallbacks.put(listener);
        Intent intent = new Intent(context, PhotoPickerTransActivity.class);
        intent.putExtra(EXTRA_CONFIG, config);
        intent.putExtra(EXTRA_REQUEST_ID, requestId);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            context.startActivity(intent);
        } catch (RuntimeException e) {
            PhotoPickerCallbacks.remove(requestId);
            throw e;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestId = getIntent().getStringExtra(EXTRA_REQUEST_ID);
        config = (PhotoPickerConfig) getIntent().getSerializableExtra(EXTRA_CONFIG);
        listener = PhotoPickerCallbacks.get(requestId);

        if (listener == null || config == null) {
            LogUtils.e("PhotoPickerTransActivity: missing listener or config, requestId=" + requestId);
            PhotoPickerCallbacks.remove(requestId);
            finish();
            return;
        }

        // PickMultipleVisualMedia 要求 maxItems > 1，单选时不能注册该 contract
        if (config.maxSelectNum <= 1) {
            singleLauncher = registerForActivityResult(
                    new ActivityResultContracts.PickVisualMedia(),
                    uri -> handleUris(uri == null ? new ArrayList<>() : singleton(uri)));
        } else {
            int maxItems = resolveMaxItems();
            multiLauncher = registerForActivityResult(
                    new ActivityResultContracts.PickMultipleVisualMedia(maxItems),
                    uris -> handleUris(uris == null ? new ArrayList<>() : uris));
        }

        if (savedInstanceState != null) {
            launched = savedInstanceState.getBoolean(STATE_LAUNCHED, false);
        }
        if (!launched) {
            launchPicker();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_LAUNCHED, launched);
    }

    private void launchPicker() {
        if (launched) {
            return;
        }
        launched = true;
        PickVisualMediaRequest request = new PickVisualMediaRequest.Builder()
                .setMediaType(resolveMediaType())
                .build();
        if (config.maxSelectNum <= 1) {
            singleLauncher.launch(request);
        } else {
            multiLauncher.launch(request);
        }
    }

    private ActivityResultContracts.PickVisualMedia.VisualMediaType resolveMediaType() {
        if (config.withImage && config.withVideo) {
            return ActivityResultContracts.PickVisualMedia.ImageAndVideo.INSTANCE;
        }
        if (config.withVideo) {
            return ActivityResultContracts.PickVisualMedia.VideoOnly.INSTANCE;
        }
        return ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE;
    }

    private int resolveMaxItems() {
        int max = Math.max(2, config.maxSelectNum);
        return Math.min(max, 100);
    }

    private void handleUris(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) {
            deliverCancel();
            return;
        }
        ioExecutor.execute(() -> processOnBackground(uris));
    }

    private void processOnBackground(List<Uri> uris) {
        FilterSkipCollector skips = new FilterSkipCollector();
        // 1) 先过滤，只保留符合条件的 Uri（此阶段不拷贝）
        List<Uri> accepted = filterAccepted(uris, skips);

        if (accepted.isEmpty()) {
            String summaryMsg = skips.buildToastMessage(this, config);
            String failMsg = TextUtils.isEmpty(summaryMsg)
                    ? getString(R.string.media_picker_fail_all_filtered)
                    : summaryMsg;
            notifyFailAndFinish("", failMsg);
            return;
        }

        // 2) 仅对过滤后的项拷贝（或直接返回 Uri 字符串）
        List<String> successPaths = new ArrayList<>(accepted.size());
        for (Uri uri : accepted) {
            try {
                if (config.copyToPath) {
                    successPaths.add(MediaCopyUtil.copyUriToExternalPrivateDir(this, uri));
                } else {
                    successPaths.add(uri.toString());
                }
            } catch (Exception e) {
                LogUtils.e(e);
                skips.other();
            }
        }

        String summaryMsg = skips.buildToastMessage(this, config);
        if (successPaths.isEmpty()) {
            String failMsg = TextUtils.isEmpty(summaryMsg)
                    ? getString(R.string.media_picker_fail_all_filtered)
                    : summaryMsg;
            notifyFailAndFinish("", failMsg);
            return;
        }
        deliverSuccess(successPaths, summaryMsg);
    }

    /**
     * 按配置过滤，返回符合条件的 Uri 列表；超限/不合规项计入 {@code skips}，不触发拷贝。
     */
    private List<Uri> filterAccepted(List<Uri> uris, FilterSkipCollector skips) {
        List<Uri> accepted = new ArrayList<>();
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
                boolean video = MediaUriHelper.isVideo(this, uri);
                boolean image = MediaUriHelper.isImage(this, uri);

                if (video && !config.withVideo) {
                    skips.other();
                    continue;
                }
                if (image && !config.withImage) {
                    skips.other();
                    continue;
                }
                if (!video && !image) {
                    skips.other();
                    continue;
                }

                if (video) {
                    if (videoKept >= maxVideo) {
                        skips.exceedMaxVideo();
                        continue;
                    }
                    if (maxDurationMs > 0) {
                        long duration = MediaUriHelper.getVideoDurationMs(this, uri);
                        if (duration < 0) {
                            skips.other();
                            continue;
                        }
                        if (duration > maxDurationMs) {
                            skips.videoTooLong();
                            continue;
                        }
                    }
                }

                if (maxBytes > 0) {
                    long size = MediaUriHelper.getFileSize(this, uri);
                    if (size < 0) {
                        // 大小未知，无法在过滤阶段判定，跳过（不边拷边限）
                        skips.other();
                        continue;
                    }
                    if (size > maxBytes) {
                        skips.fileTooLarge();
                        continue;
                    }
                }

                if (accepted.size() >= config.maxSelectNum) {
                    skips.exceedMaxSelect();
                    continue;
                }

                accepted.add(uri);
                if (video) {
                    videoKept++;
                }
            } catch (Exception e) {
                LogUtils.e(e);
                skips.other();
            }
        }
        return accepted;
    }

    private void notifyFailAndFinish(String path, String msg) {
        mainHandler.post(() -> {
            if (finishedWithCallback) {
                return;
            }
            finishedWithCallback = true;
            TakePhotoListener l = takeListener();
            if (l != null) {
                l.onFail(path, msg);
            }
            finishSafely();
        });
    }

    private void deliverSuccess(List<String> paths, String toastMsg) {
        mainHandler.post(() -> {
            if (finishedWithCallback) {
                return;
            }
            finishedWithCallback = true;
            showFilterToast(toastMsg);
            TakePhotoListener l = takeListener();
            if (l != null) {
                l.onSuccess(paths);
            }
            finishSafely();
        });
    }

    private void showFilterToast(String toastMsg) {
        if (TextUtils.isEmpty(toastMsg)) {
            return;
        }
        Toast.makeText(getApplicationContext(), toastMsg, Toast.LENGTH_LONG).show();
    }

    private void deliverCancel() {
        mainHandler.post(() -> {
            if (finishedWithCallback) {
                return;
            }
            finishedWithCallback = true;
            TakePhotoListener l = takeListener();
            if (l != null) {
                l.onCancel();
            }
            finishSafely();
        });
    }

    private TakePhotoListener takeListener() {
        TakePhotoListener l = PhotoPickerCallbacks.remove(requestId);
        if (l != null) {
            listener = null;
        }
        return l != null ? l : listener;
    }

    private void finishSafely() {
        if (!isFinishing()) {
            finish();
        }
    }

    private static List<Uri> singleton(Uri uri) {
        List<Uri> list = new ArrayList<>(1);
        list.add(uri);
        return list;
    }

    @Override
    protected void onDestroy() {
        ioExecutor.shutdownNow();
        // 异常销毁且尚未回调时清理，避免 Map 泄漏；尽量通知取消
        if (isFinishing() && !finishedWithCallback && requestId != null) {
            finishedWithCallback = true;
            TakePhotoListener l = PhotoPickerCallbacks.remove(requestId);
            if (l != null) {
                try {
                    l.onCancel();
                } catch (Exception e) {
                    LogUtils.w(e);
                }
            }
        }
        super.onDestroy();
    }
}
