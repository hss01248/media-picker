package com.hss01248.screenshotapp;

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
