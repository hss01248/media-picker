package com.hss01248.media_picker.internal;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.TextUtils;

import com.blankj.utilcode.util.LogUtils;
import com.hss01248.media_picker.MediaCopyUtil;

/**
 * Photo Picker 内部过滤用的 Uri 元数据读取。
 */
final class MediaUriHelper {

    private MediaUriHelper() {
    }

    static boolean isVideo(Context context, Uri uri) {
        return MediaCopyUtil.isVideo(context, uri);
    }

    static boolean isImage(Context context, Uri uri) {
        return MediaCopyUtil.isImage(context, uri);
    }

    /**
     * @return 文件大小字节，未知返回 -1
     */
    static long getFileSize(Context context, Uri uri) {
        ContentResolver resolver = context.getContentResolver();
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0 && !cursor.isNull(idx)) {
                    long size = cursor.getLong(idx);
                    if (size >= 0) {
                        return size;
                    }
                }
            }
        } catch (Exception e) {
            LogUtils.w(e);
        }
        try {
            android.content.res.AssetFileDescriptor afd =
                    resolver.openAssetFileDescriptor(uri, "r");
            if (afd != null) {
                long length = afd.getLength();
                afd.close();
                if (length >= 0) {
                    return length;
                }
            }
        } catch (Exception e) {
            LogUtils.w(e);
        }
        return -1;
    }

    /**
     * @return 视频时长毫秒，失败返回 -1
     */
    static long getVideoDurationMs(Context context, Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (TextUtils.isEmpty(duration)) {
                return -1;
            }
            return Long.parseLong(duration);
        } catch (Exception e) {
            LogUtils.w(e);
            return -1;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }
}
