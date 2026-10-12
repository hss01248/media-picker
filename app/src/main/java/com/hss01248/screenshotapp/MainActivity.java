package com.hss01248.screenshotapp;

import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.blankj.utilcode.util.LogUtils;
import com.hss01248.media_picker.MediaCopyUtil;
import com.hss01248.media_picker.MediaPicker;
import com.hss01248.media_picker.MediaPickerFileUtil;
import com.hss01248.media_picker.TakePhotoListener;
import com.hss01248.picker_camera_wrapper.PickerCameraUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView tvResult;
    private final List<String> lastResults = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvResult = findViewById(R.id.tv_result);

        findViewById(R.id.btn_wrapper_bottom_sheet).setOnClickListener(v ->
                PickerCameraUtil.startPickOneWithDialog(this, listener("wrapper-弹窗")));

        findViewById(R.id.btn_pick_uri_image).setOnClickListener(v ->
                MediaPicker.create(this)
                        .maxSelectNum(1)
                        .withImage(true)
                        .withVideo(false)
                        .copyToPath(false)
                        .forResult(listener("单选图片-Uri")));

        findViewById(R.id.btn_pick_uri_multi).setOnClickListener(v ->
                MediaPicker.create(this)
                        .maxSelectNum(9)
                        .maxVideoSelectNum(9)
                        .withImage(true)
                        .withVideo(true)
                        .copyToPath(false)
                        .forResult(listener("多选-Uri")));

        findViewById(R.id.btn_pick_copy_image).setOnClickListener(v ->
                MediaPicker.create(this)
                        .maxSelectNum(1)
                        .withImage(true)
                        .withVideo(false)
                        .copyToPath(true)
                        .forResult(listener("单选图片-Path")));

        findViewById(R.id.btn_pick_copy_multi).setOnClickListener(v ->
                MediaPicker.create(this)
                        .maxSelectNum(9)
                        .maxVideoSelectNum(2)
                        .queryMaxFileSize(20)
                        .videoMaxSecond(15)
                        .withImage(true)
                        .withVideo(true)
                        .copyToPath(true)
                        .forResult(listener("限制多选-Path")));

        // FilterTest: ok_under_20mb + bad_over_20mb（设备上已扫入 MediaStore）
        findViewById(R.id.btn_filter_partial).setOnClickListener(v ->
                runFilterSelfTest("过滤-部分不合格", filterTestUris(true, true)));
        findViewById(R.id.btn_filter_all_reject).setOnClickListener(v ->
                runFilterSelfTest("过滤-全部不合格", filterTestUris(false, true)));
        findViewById(R.id.btn_filter_all_ok).setOnClickListener(v ->
                runFilterSelfTest("过滤-全部合格", filterTestUris(true, false)));

        findViewById(R.id.btn_pick_single_video).setOnClickListener(v ->
                MediaPicker.create(this)
                        .maxSelectNum(1)
                        .withImage(false)
                        .withVideo(true)
                        .copyToPath(false)
                        .forResult(listener("单选视频-Uri")));

        findViewById(R.id.btn_manual_copy).setOnClickListener(v -> {
            if (lastResults.isEmpty()) {
                toast("请先选图拿到 Uri");
                return;
            }
            List<String> copied = MediaCopyUtil.copyUriStringsToExternalPrivateDir(this, lastResults);
            lastResults.clear();
            lastResults.addAll(copied);
            String msg = "MediaCopyUtil 拷贝完成: " + copied.size();
            appendResult(msg + "\n" + join(copied));
            toast(msg);
        });

        findViewById(R.id.btn_delete_last).setOnClickListener(v -> {
            if (lastResults.isEmpty()) {
                toast("没有可删除的路径");
                return;
            }
            int n = MediaPickerFileUtil.deleteCopiedFiles(this, lastResults);
            String msg = "删除本地路径: " + n + "/" + lastResults.size();
            lastResults.clear();
            appendResult(msg);
            toast(msg);
        });

        findViewById(R.id.btn_clear_all).setOnClickListener(v -> {
            int n = MediaPickerFileUtil.clearAllCopiedFiles(this);
            lastResults.clear();
            String msg = "清空全部拷贝文件: " + n + " 个";
            appendResult(msg);
            toast(msg);
        });
    }

    private void runFilterSelfTest(String tag, List<Uri> uris) {
        if (uris.isEmpty()) {
            toast("FilterTest 媒体未入库，请确认 DCIM/FilterTest");
            appendResult("[" + tag + "] missing FilterTest media");
            return;
        }
        MediaPicker.create(this)
                .maxSelectNum(9)
                .maxVideoSelectNum(2)
                .queryMaxFileSize(20)
                .videoMaxSecond(15)
                .withImage(true)
                .withVideo(true)
                .copyToPath(true)
                .forResultWithUris(uris, listener(tag));
    }

    /**
     * 自测素材放在应用外部私有目录，避免 MediaStore 权限导致偶发无法识别。
     * 路径：Android/data/.../files/FilterTest/
     */
    private List<Uri> filterTestUris(boolean includeOk, boolean includeBad) {
        List<Uri> out = new ArrayList<>(2);
        if (includeOk) {
            Uri ok = filterTestFileUri("ok_under_20mb.jpg");
            if (ok != null) {
                out.add(ok);
            }
        }
        if (includeBad) {
            Uri bad = filterTestFileUri("bad_over_20mb.jpg");
            if (bad != null) {
                out.add(bad);
            }
        }
        return out;
    }

    @Nullable
    private Uri filterTestFileUri(String name) {
        File dir = new File(getExternalFilesDir(null), "FilterTest");
        File file = new File(dir, name);
        if (!file.isFile() || file.length() <= 0) {
            LogUtils.w("FilterTest missing: " + file.getAbsolutePath());
            return null;
        }
        // 同应用内自测用 file Uri；扩展名用于 mime / 大小可读
        return Uri.fromFile(file);
    }

    private TakePhotoListener listener(String tag) {
        return new TakePhotoListener() {
            @Override
            public void onSuccess(List<String> paths) {
                lastResults.clear();
                if (paths != null) {
                    lastResults.addAll(paths);
                }
                String body = "[" + tag + "] onSuccess, count=" + lastResults.size()
                        + "\n" + join(lastResults);
                appendResult(body);
                toast(tag + " 成功: " + lastResults.size());
                LogUtils.d(tag, lastResults);
            }

            @Override
            public void onFail(String path, String msg) {
                appendResult("[" + tag + "] onFail: " + path + " | " + msg);
                toast("fail: " + msg);
                LogUtils.w(tag, path, msg);
            }

            @Override
            public void onCancel() {
                appendResult("[" + tag + "] onCancel");
                toast(tag + " 取消");
            }
        };
    }

    private static String join(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            sb.append(s).append('\n');
        }
        return sb.toString();
    }

    private void appendResult(String text) {
        CharSequence old = tvResult.getText();
        tvResult.setText(text + "\n\n" + old);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
