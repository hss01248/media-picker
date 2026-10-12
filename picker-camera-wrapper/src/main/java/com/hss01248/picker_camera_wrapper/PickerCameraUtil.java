package com.hss01248.picker_camera_wrapper;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.hss01248.activityresult2.TheActivityCallback;
import com.hss01248.activityresult2.androidx.StartActivityResultHelper;
import com.hss01248.camerax.CameraxCallback;
import com.hss01248.camerax.CameraxCaptureUtil;
import com.hss01248.camerax.CameraxRecordVideoActivity;
import com.hss01248.media_picker.MediaPicker;
import com.hss01248.media_picker.TakePhotoListener;

import java.util.Collections;

/**
 * 底部弹窗：拍照 / 拍视频 / 从相册选择。
 * UI 参考 takephoto 的 {@code TakePhotoUtil.startPickOneWitchDialog}。
 */
public final class PickerCameraUtil {

    private PickerCameraUtil() {
    }

    /**
     * 展示底部选择弹窗（拍照、拍视频、相册）。
     */
    public static void startPickOneWithDialog(@NonNull FragmentActivity activity,
                                              @NonNull TakePhotoListener listener) {
        startPickOneWithDialog(activity, new Options(), listener);
    }

    /**
     * 展示底部选择弹窗，可配置录像时长、相册选项等。
     */
    public static void startPickOneWithDialog(@NonNull final FragmentActivity activity,
                                              @NonNull final Options options,
                                              @NonNull final TakePhotoListener listener) {
        try {
            final Dialog dialog = new Dialog(activity);
            dialog.setContentView(R.layout.pcw_dialog_select_media);
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                WindowManager.LayoutParams params = window.getAttributes();
                params.width = activity.getResources().getDisplayMetrics().widthPixels;
                params.gravity = Gravity.BOTTOM;
                window.setAttributes(params);
            }
            dialog.show();

            dialog.findViewById(R.id.pcw_btn_take_photo).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    takePhoto(activity, options, listener);
                }
            });

            dialog.findViewById(R.id.pcw_btn_take_video).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    takeVideo(activity, options, listener);
                }
            });

            dialog.findViewById(R.id.pcw_btn_pick_album).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    pickAlbum(activity, options, listener);
                }
            });

            dialog.findViewById(R.id.pcw_btn_cancel).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    listener.onCancel();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            listener.onFail("", e.getMessage() != null ? e.getMessage() : "dialog error");
        }
    }

    public static void takePhoto(@NonNull FragmentActivity activity,
                                 @NonNull TakePhotoListener listener) {
        takePhoto(activity, new Options(), listener);
    }

    public static void takePhoto(@NonNull FragmentActivity activity,
                                 @NonNull Options options,
                                 @NonNull final TakePhotoListener listener) {
        CameraxCaptureUtil.takePhoto(activity, new CameraxCallback() {
            @Override
            public void onSuccess(String filePath) {
                listener.onSuccess(Collections.singletonList(filePath));
            }

            @Override
            public void onCancel() {
                listener.onCancel();
            }

            @Override
            public void onError(String message) {
                listener.onFail("", message);
            }
        }, options.preferFrontCamera);
    }

    public static void takeVideo(@NonNull FragmentActivity activity,
                                 @NonNull TakePhotoListener listener) {
        takeVideo(activity, new Options(), listener);
    }

    public static void takeVideo(@NonNull FragmentActivity activity,
                                 @NonNull Options options,
                                 @NonNull final TakePhotoListener listener) {
        Intent intent = new Intent(activity, CameraxRecordVideoActivity.class);
        if (options.videoMaxDurationSeconds > 0) {
            intent.putExtra(CameraxRecordVideoActivity.EXTRA_MAX_DURATION_SECONDS,
                    options.videoMaxDurationSeconds);
        }
        intent.putExtra(CameraxRecordVideoActivity.EXTRA_PREFER_FRONT_CAMERA,
                options.preferFrontCamera);

        StartActivityResultHelper.startForResult(activity, intent, new TheActivityCallback() {
            @Override
            protected void onResultOK(Intent data) {
                String path = data != null
                        ? data.getStringExtra(CameraxRecordVideoActivity.EXTRA_VIDEO_PATH)
                        : null;
                if (path != null && !path.isEmpty()) {
                    listener.onSuccess(Collections.singletonList(path));
                } else {
                    listener.onFail("", "empty video path");
                }
            }

            @Override
            protected void onResultCancel(Intent data) {
                listener.onCancel();
            }

            @Override
            public void onActivityNotFound(Throwable e) {
                listener.onFail("", e != null && e.getMessage() != null
                        ? e.getMessage()
                        : "record video activity not found");
            }
        });
    }

    public static void pickAlbum(@NonNull FragmentActivity activity,
                                 @NonNull TakePhotoListener listener) {
        pickAlbum(activity, new Options(), listener);
    }

    public static void pickAlbum(@NonNull FragmentActivity activity,
                                 @NonNull Options options,
                                 @NonNull TakePhotoListener listener) {
        MediaPicker.create(activity)
                .maxSelectNum(options.albumMaxSelectNum)
                .maxVideoSelectNum(options.albumMaxVideoSelectNum)
                .queryMaxFileSize(options.albumMaxFileSizeMb)
                .videoMaxSecond(options.albumVideoMaxSecond)
                .withImage(options.albumWithImage)
                .withVideo(options.albumWithVideo)
                .copyToPath(options.albumCopyToPath)
                .forResult(listener);
    }

    /**
     * 可选配置。未设置项使用默认值。
     */
    public static class Options {
        /** 拍照/录像是否优先前置摄像头 */
        public boolean preferFrontCamera = false;
        /** 录像最大时长（秒），&lt;=0 表示不限制 */
        public int videoMaxDurationSeconds = 0;

        /** 相册最多可选数量 */
        public int albumMaxSelectNum = 1;
        /** 相册最多可选视频数；0 表示不单独限制 */
        public int albumMaxVideoSelectNum = 1;
        /** 相册单文件大小上限 MB；0 不限制 */
        public float albumMaxFileSizeMb = 0f;
        /** 相册视频时长上限秒；0 不限制 */
        public int albumVideoMaxSecond = 0;
        public boolean albumWithImage = true;
        public boolean albumWithVideo = true;
        public boolean albumCopyToPath = false;
    }
}
