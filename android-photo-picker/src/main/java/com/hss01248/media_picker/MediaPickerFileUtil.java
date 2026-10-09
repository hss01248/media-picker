package com.hss01248.media_picker;

import android.content.Context;
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
     * 删除单个拷贝文件。仅允许删除 {@link MediaCopyUtil#COPY_SUB_DIR} 目录内的文件。
     *
     * @return true 表示文件已不存在或删除成功
     */
    public static boolean deleteCopiedFile(Context context, String path) {
        if (context == null || TextUtils.isEmpty(path)) {
            return false;
        }
        if (path.startsWith("content:") || path.startsWith("file:")) {
            return false;
        }
        if (!MediaCopyUtil.isCopiedMediaPath(context, path)) {
            LogUtils.w("deleteCopiedFile rejected (not under media_picker): " + path);
            return false;
        }
        File file = new File(path);
        if (!file.exists()) {
            return true;
        }
        if (!file.isFile()) {
            return false;
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
    public static int deleteCopiedFiles(Context context, Collection<String> paths) {
        if (context == null || paths == null || paths.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String path : paths) {
            if (deleteCopiedFile(context, path)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 清空 Pictures/Movies/files 下 media_picker 目录中的所有拷贝文件。
     *
     * @return 删除的文件数
     */
    public static int clearAllCopiedFiles(Context context) {
        if (context == null) {
            return 0;
        }
        int count = 0;
        for (File root : MediaCopyUtil.copyRoots(context)) {
            count += clearDir(root);
        }
        return count;
    }

    private static int clearDir(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
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
