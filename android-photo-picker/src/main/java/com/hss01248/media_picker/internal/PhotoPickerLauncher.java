package com.hss01248.media_picker.internal;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.blankj.utilcode.util.ActivityUtils;
import com.blankj.utilcode.util.LogUtils;
import com.hss01248.activityresult2.TheActivityCallback;
import com.hss01248.activityresult2.androidx.StartActivityResultHelper;
import com.hss01248.media_picker.MediaCopyUtil;
import com.hss01248.media_picker.R;
import com.hss01248.media_picker.TakePhotoListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 通过 {@link StartActivityResultHelper#startForResult} 启动官方 Photo Picker 并处理结果。
 */
public final class PhotoPickerLauncher {

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private PhotoPickerLauncher() {
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
        final ActivityResultContracts.PickVisualMedia.VisualMediaType mediaType = resolveMediaType(config);
        final PickVisualMediaRequest request = new PickVisualMediaRequest.Builder()
                .setMediaType(mediaType)
                .build();

        final Intent intent;
        if (multi) {
            int maxItems = Math.min(Math.max(2, config.maxSelectNum), 100);
            intent = new ActivityResultContracts.PickMultipleVisualMedia(maxItems)
                    .createIntent(activity, request);
        } else {
            intent = new ActivityResultContracts.PickVisualMedia()
                    .createIntent(activity, request);
        }

        StartActivityResultHelper.startForResult(activity, intent, new TheActivityCallback() {
            @Override
            protected void onResultOK(Intent data) {
                List<Uri> uris;
                if (multi) {
                    List<Uri> parsed = new ActivityResultContracts.PickMultipleVisualMedia()
                            .parseResult(Activity.RESULT_OK, data);
                    uris = parsed == null ? new ArrayList<>() : parsed;
                } else {
                    Uri uri = new ActivityResultContracts.PickVisualMedia()
                            .parseResult(Activity.RESULT_OK, data);
                    uris = uri == null ? new ArrayList<>() : singleton(uri);
                }
                if (uris.isEmpty()) {
                    listener.onCancel();
                    return;
                }
                IO.execute(() -> processOnBackground(activity, config, listener, uris));
            }

            @Override
            protected void onResultCancel(Intent data) {
                listener.onCancel();
            }

            @Override
            public void onActivityNotFound(Throwable e) {
                LogUtils.e(e);
                listener.onFail("", e != null && e.getMessage() != null
                        ? e.getMessage()
                        : "Photo Picker not found");
            }
        });
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

    private static void processOnBackground(Context context,
                                            PhotoPickerConfig config,
                                            TakePhotoListener listener,
                                            List<Uri> uris) {
        FilterSkipCollector skips = new FilterSkipCollector();
        List<Uri> accepted = filterAccepted(context, config, uris, skips);

        if (accepted.isEmpty()) {
            String summaryMsg = skips.buildToastMessage(context, config);
            String failMsg = TextUtils.isEmpty(summaryMsg)
                    ? context.getString(R.string.media_picker_fail_all_filtered)
                    : summaryMsg;
            notifyFail(listener, "", failMsg);
            return;
        }

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
                skips.other();
            }
        }

        String summaryMsg = skips.buildToastMessage(context, config);
        if (successPaths.isEmpty()) {
            String failMsg = TextUtils.isEmpty(summaryMsg)
                    ? context.getString(R.string.media_picker_fail_all_filtered)
                    : summaryMsg;
            notifyFail(listener, "", failMsg);
            return;
        }
        deliverSuccess(context, listener, successPaths, summaryMsg);
    }

    private static List<Uri> filterAccepted(Context context,
                                            PhotoPickerConfig config,
                                            List<Uri> uris,
                                            FilterSkipCollector skips) {
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
                boolean video = MediaUriHelper.isVideo(context, uri);
                boolean image = MediaUriHelper.isImage(context, uri);

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
                        long duration = MediaUriHelper.getVideoDurationMs(context, uri);
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
                    long size = MediaUriHelper.getFileSize(context, uri);
                    if (size < 0) {
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

    private static void notifyFail(TakePhotoListener listener, String path, String msg) {
        MAIN.post(() -> listener.onFail(path, msg));
    }

    private static void deliverSuccess(Context context,
                                       TakePhotoListener listener,
                                       List<String> paths,
                                       String toastMsg) {
        MAIN.post(() -> {
            if (!TextUtils.isEmpty(toastMsg)) {
                Toast.makeText(context.getApplicationContext(), toastMsg, Toast.LENGTH_LONG).show();
            }
            listener.onSuccess(paths);
        });
    }

    private static List<Uri> singleton(Uri uri) {
        List<Uri> list = new ArrayList<>(1);
        list.add(uri);
        return list;
    }
}
