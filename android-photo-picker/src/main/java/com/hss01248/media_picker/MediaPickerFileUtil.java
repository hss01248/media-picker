package com.hss01248.media_picker;

import android.content.Context;
import android.os.Environment;
import android.text.TextUtils;

import com.blankj.utilcode.util.LogUtils;

import java.io.File;
import java.util.Collection;

/**
 * 管理拷贝到应用外部私有目录的文件（删除/清空）。
 */
public final class MediaPickerFileUtil {

    private MediaPickerFileUtil() {
    }

    /**
     * 删除单个拷贝文件。
     *
     * @return true 表示文件已不存在或删除成功
     */
    public static boolean deleteCopiedFile(String path) {
        if (TextUtils.isEmpty(path)) {
            return false;
        }
        // Uri 字符串不是本地路径，直接跳过
        if (path.startsWith("content:") || path.startsWith("file:")) {
            return false;
        }
        File file = new File(path);
        if (!file.exists()) {
            return true;
        }
        boolean ok = file.delete();
        if (!ok) {
            LogUtils.w("deleteCopiedFile failed: " + path);
        }
        return ok;
    }

    /**
     * 批量删除拷贝文件，返回成功删除（或不存在）的数量。
     */
    public static int deleteCopiedFiles(Collection<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String path : paths) {
            if (deleteCopiedFile(path)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 清空 Pictures/Movies 下 media_picker 目录中的所有拷贝文件。
     *
     * @return 删除的文件数
     */
    public static int clearAllCopiedFiles(Context context) {
        if (context == null) {
            return 0;
        }
        int count = 0;
        count += clearDir(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES));
        count += clearDir(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES));
        count += clearDir(context.getFilesDir());
        return count;
    }

    private static int clearDir(File base) {
        if (base == null) {
            return 0;
        }
        File dir = new File(base, MediaCopyUtil.COPY_SUB_DIR);
        if (!dir.exists() || !dir.isDirectory()) {
            return 0;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        int count = 0;
        for (File file : files) {
            if (file.isFile() && file.delete()) {
                count++;
            }
        }
        return count;
    }
}
