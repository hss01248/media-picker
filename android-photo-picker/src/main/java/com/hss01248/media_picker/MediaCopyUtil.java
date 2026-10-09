package com.hss01248.media_picker;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import com.blankj.utilcode.util.LogUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 将 content/file Uri 拷贝到应用外部私有目录的工具类，可独立于 Photo Picker 使用。
 */
public final class MediaCopyUtil {

    /** 拷贝子目录：{Pictures|Movies}/media_picker/ */
    public static final String COPY_SUB_DIR = "media_picker";

    private static final int BUFFER_SIZE = 8192;

    private MediaCopyUtil() {
    }

    /**
     * 将 Uri 拷贝到应用外部私有目录，返回绝对路径。
     * 图片 → {@link Environment#DIRECTORY_PICTURES}/media_picker/
     * 视频 → {@link Environment#DIRECTORY_MOVIES}/media_picker/
     */
    public static String copyUriToExternalPrivateDir(Context context, Uri uri) throws Exception {
        if (context == null) {
            throw new IllegalArgumentException("context is null");
        }
        if (uri == null) {
            throw new IllegalArgumentException("uri is null");
        }
        boolean video = isVideo(context, uri);
        File base = context.getExternalFilesDir(
                video ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES);
        if (base == null) {
            base = context.getFilesDir();
        }
        File dir = new File(base, COPY_SUB_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("cannot create dir: " + dir.getAbsolutePath());
        }

        String mime = getMimeType(context, uri);
        String ext = guessExtension(mime, displayName(context, uri));
        String fileName = "media_" + System.currentTimeMillis() + "_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8)
                + ext;
        File dest = new File(dir, fileName);

        ContentResolver resolver = context.getContentResolver();
        try (InputStream in = resolver.openInputStream(uri);
             OutputStream out = new FileOutputStream(dest)) {
            if (in == null) {
                throw new IllegalStateException("openInputStream returned null: " + uri);
            }
            byte[] buf = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
            }
            out.flush();
        }
        return dest.getAbsolutePath();
    }

    /**
     * @param uriString Uri.toString()，如 content://...
     */
    public static String copyUriToExternalPrivateDir(Context context, String uriString) throws Exception {
        if (TextUtils.isEmpty(uriString)) {
            throw new IllegalArgumentException("uriString is empty");
        }
        return copyUriToExternalPrivateDir(context, Uri.parse(uriString));
    }

    /**
     * 批量拷贝，跳过失败项；失败打日志。
     *
     * @return 成功拷贝后的绝对路径列表
     */
    public static List<String> copyUrisToExternalPrivateDir(Context context, Collection<Uri> uris) {
        List<String> result = new ArrayList<>();
        if (context == null || uris == null || uris.isEmpty()) {
            return result;
        }
        for (Uri uri : uris) {
            if (uri == null) {
                continue;
            }
            try {
                result.add(copyUriToExternalPrivateDir(context, uri));
            } catch (Exception e) {
                LogUtils.e("copy failed: " + uri, e);
            }
        }
        return result;
    }

    /**
     * 批量拷贝 Uri 字符串。
     */
    public static List<String> copyUriStringsToExternalPrivateDir(Context context,
                                                                   Collection<String> uriStrings) {
        List<String> result = new ArrayList<>();
        if (context == null || uriStrings == null || uriStrings.isEmpty()) {
            return result;
        }
        for (String uriString : uriStrings) {
            if (TextUtils.isEmpty(uriString)) {
                continue;
            }
            try {
                result.add(copyUriToExternalPrivateDir(context, uriString));
            } catch (Exception e) {
                LogUtils.e("copy failed: " + uriString, e);
            }
        }
        return result;
    }

    public static boolean isVideo(Context context, Uri uri) {
        String mime = getMimeType(context, uri);
        return mime != null && mime.toLowerCase(Locale.US).startsWith("video/");
    }

    public static boolean isImage(Context context, Uri uri) {
        String mime = getMimeType(context, uri);
        return mime != null && mime.toLowerCase(Locale.US).startsWith("image/");
    }

    public static String getMimeType(Context context, Uri uri) {
        String mime = context.getContentResolver().getType(uri);
        if (!TextUtils.isEmpty(mime)) {
            return mime;
        }
        String path = uri.getPath();
        if (path != null) {
            int dot = path.lastIndexOf('.');
            if (dot >= 0 && dot < path.length() - 1) {
                String ext = path.substring(dot + 1).toLowerCase(Locale.US);
                return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            }
        }
        return null;
    }

    private static String displayName(Context context, Uri uri) {
        ContentResolver resolver = context.getContentResolver();
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String name = cursor.getString(idx);
                    if (!TextUtils.isEmpty(name)) {
                        return name;
                    }
                }
            }
        } catch (Exception e) {
            LogUtils.w(e);
        }
        return uri.getLastPathSegment();
    }

    private static String guessExtension(String mime, String displayName) {
        if (!TextUtils.isEmpty(displayName)) {
            int dot = displayName.lastIndexOf('.');
            if (dot >= 0 && dot < displayName.length() - 1) {
                return displayName.substring(dot).toLowerCase(Locale.US);
            }
        }
        if (!TextUtils.isEmpty(mime)) {
            String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
            if (!TextUtils.isEmpty(ext)) {
                return "." + ext;
            }
            if (mime.startsWith("image/")) {
                return ".jpg";
            }
            if (mime.startsWith("video/")) {
                return ".mp4";
            }
        }
        return "";
    }
}
