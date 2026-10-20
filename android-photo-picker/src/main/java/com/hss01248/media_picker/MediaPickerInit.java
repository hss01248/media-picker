package com.hss01248.media_picker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.startup.Initializer;

import com.blankj.utilcode.util.LogUtils;
import com.blankj.utilcode.util.ThreadUtils;

import java.util.Collections;
import java.util.List;

/**
 * 应用启动时延迟清理选图拷贝目录（{@link MediaCopyUtil#COPY_SUB_DIR}）。
 */
public class MediaPickerInit implements Initializer<Boolean> {

    private static final long CLEAR_DELAY_MS = 5_000L;

    @NonNull
    @Override
    public Boolean create(@NonNull Context context) {
        final Context appContext = context.getApplicationContext();
        ThreadUtils.getIoPool().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(CLEAR_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                int count = MediaPickerFileUtil.clearAllCopiedFiles(appContext);
                LogUtils.d("MediaPickerInit cleared copied files: " + count);
            }
        });
        return Boolean.TRUE;
    }

    @NonNull
    @Override
    public List<Class<? extends Initializer<?>>> dependencies() {
        return Collections.emptyList();
    }
}
