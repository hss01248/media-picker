package com.hss01248.media_picker.internal;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import com.blankj.utilcode.util.LogUtils;
import com.hss01248.media_picker.MediaCopyUtil;
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

    private static TakePhotoListener sListener;
    private static PhotoPickerConfig sConfig;

    private PhotoPickerConfig config;
    private TakePhotoListener listener;
    private boolean launched;
    private boolean finishedWithCallback;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ActivityResultLauncher<PickVisualMediaRequest> singleLauncher;
    private ActivityResultLauncher<PickVisualMediaRequest> multiLauncher;

    public static void start(Context context, PhotoPickerConfig config, TakePhotoListener listener) {
        sListener = listener;
        sConfig = config;
        Intent intent = buildIntent(context, config);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    static Intent buildIntent(Context context, PhotoPickerConfig config) {
        Intent intent = new Intent(context, PhotoPickerTransActivity.class);
        intent.putExtra(EXTRA_CONFIG, config);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        listener = sListener;
        sListener = null;
        config = sConfig != null ? sConfig : (PhotoPickerConfig) getIntent().getSerializableExtra(EXTRA_CONFIG);
        sConfig = null;

        if (listener == null || config == null) {
            LogUtils.e("PhotoPickerTransActivity: missing listener or config");
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

        if (savedInstanceState == null) {
            launchPicker();
        }
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
        // PickMultipleVisualMedia 要求 > 1
        int max = Math.max(2, config.maxSelectNum);
        // 系统 Photo Picker 通常上限为 100
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
        List<String> successPaths = new ArrayList<>();
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
                    notifyFail(uri.toString(), "video not allowed");
                    continue;
                }
                if (image && !config.withImage) {
                    notifyFail(uri.toString(), "image not allowed");
                    continue;
                }
                if (!video && !image) {
                    notifyFail(uri.toString(), "unsupported media type");
                    continue;
                }

                if (video) {
                    if (videoKept >= maxVideo) {
                        notifyFail(uri.toString(), "exceed maxVideoSelectNum: " + maxVideo);
                        continue;
                    }
                    if (maxDurationMs > 0) {
                        long duration = MediaUriHelper.getVideoDurationMs(this, uri);
                        if (duration > maxDurationMs) {
                            notifyFail(uri.toString(),
                                    "video too long: " + (duration / 1000) + "s > " + config.videoMaxSecond + "s");
                            continue;
                        }
                    }
                }

                if (maxBytes > 0) {
                    long size = MediaUriHelper.getFileSize(this, uri);
                    if (size >= 0 && size > maxBytes) {
                        notifyFail(uri.toString(),
                                "file too large: " + size + " > " + maxBytes + " bytes");
                        continue;
                    }
                }

                if (successPaths.size() >= config.maxSelectNum) {
                    notifyFail(uri.toString(), "exceed maxSelectNum: " + config.maxSelectNum);
                    continue;
                }

                if (config.copyToPath) {
                    String path = MediaCopyUtil.copyUriToExternalPrivateDir(this, uri);
                    successPaths.add(path);
                } else {
                    successPaths.add(uri.toString());
                }
                if (video) {
                    videoKept++;
                }
            } catch (Exception e) {
                LogUtils.e(e);
                notifyFail(uri.toString(), e.getMessage() == null ? "process failed" : e.getMessage());
            }
        }

        if (successPaths.isEmpty()) {
            notifyFailMain("", "no valid media after filter");
            finishSafely();
            return;
        }
        deliverSuccess(successPaths);
    }

    private void notifyFail(String path, String msg) {
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onFail(path, msg);
            }
        });
    }

    private void notifyFailMain(String path, String msg) {
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onFail(path, msg);
            }
            finishSafely();
        });
    }

    private void deliverSuccess(List<String> paths) {
        mainHandler.post(() -> {
            if (finishedWithCallback) {
                return;
            }
            finishedWithCallback = true;
            if (listener != null) {
                listener.onSuccess(paths);
            }
            finishSafely();
        });
    }

    private void deliverCancel() {
        if (finishedWithCallback) {
            return;
        }
        finishedWithCallback = true;
        if (listener != null) {
            listener.onCancel();
        }
        finishSafely();
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
        super.onDestroy();
    }
}
