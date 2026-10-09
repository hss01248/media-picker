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
import java.util.regex.Pattern;

/**
 * 将 content/file Uri 拷贝到应用外部私有目录的工具类，可独立于 Photo Picker 使用。
 */
public final class MediaCopyUtil {

    /** 拷贝子目录：{Pictures|Movies}/media_picker/ */
    public static final String COPY_SUB_DIR = "media_picker";

    private static final int BUFFER_SIZE = 8192;
    private static final Pattern SAFE_EXT = Pattern.compile("^\\.[a-z0-9]{1,10}$");

    private MediaCopyUtil() {
    }

    /**
     * 将 Uri 拷贝到应用外部私有目录，返回绝对路径。
     */
    public static String copyUriToExternalPrivateDir(Context context, Uri uri) throws Exception {
        return copyUriToExternalPrivateDir(context, uri, 0L);
    }

    /**
     * 将 Uri 拷贝到应用外部私有目录。
     *
     * @param maxBytes 最大字节数，&lt;=0 表示不限制；超限会删除半截文件并抛异常
     */
    public static String copyUriToExternalPrivateDir(Context context, Uri uri, long maxBytes)
            throws Exception {
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
        // 二次校验：禁止目标逃出目录
        if (!isUnderDir(dest, dir)) {
            throw new IllegalStateException("unsafe destination path: " + dest.getAbsolutePath());
        }

        ContentResolver resolver = context.getContentResolver();
        try (InputStream in = resolver.openInputStream(uri);
             OutputStream out = new FileOutputStream(dest)) {
            if (in == null) {
                throw new IllegalStateException("openInputStream returned null: " + uri);
            }
            byte[] buf = new byte[BUFFER_SIZE];
            long written = 0;
            int read;
            while ((read = in.read(buf)) != -1) {
                written += read;
                if (maxBytes > 0 && written > maxBytes) {
                    throw new IllegalStateException(
                            "file too large while copying: " + written + " > " + maxBytes);
                }
                out.write(buf, 0, read);
            }
            out.flush();
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            dest.delete();
            throw e;
        }
        return dest.getAbsolutePath();
    }

    /**
     * @param uriString Uri.toString()，如 content://...
     */
    public static String copyUriToExternalPrivateDir(Context context, String uriString)
            throws Exception {
        return copyUriToExternalPrivateDir(context, uriString, 0L);
    }

    public static String copyUriToExternalPrivateDir(Context context, String uriString, long maxBytes)
            throws Exception {
        if (TextUtils.isEmpty(uriString)) {
            throw new IllegalArgumentException("uriString is empty");
        }
        return copyUriToExternalPrivateDir(context, Uri.parse(uriString), maxBytes);
    }

    /**
     * 批量拷贝，跳过失败项；失败打日志。
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

    /**
     * 判断绝对路径是否位于应用的 media_picker 拷贝目录内。
     */
    public static boolean isCopiedMediaPath(Context context, String path) {
        if (context == null || TextUtils.isEmpty(path)) {
            return false;
        }
        if (path.startsWith("content:") || path.startsWith("file:")) {
            return false;
        }
        try {
            File target = new File(path).getCanonicalFile();
            for (File root : copyRoots(context)) {
                if (root != null && isUnderDir(target, root)) {
                    return true;
                }
            }
        } catch (Exception e) {
            LogUtils.w(e);
        }
        return false;
    }

    static File[] copyRoots(Context context) {
        return new File[]{
                subDir(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)),
                subDir(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)),
                subDir(context.getFilesDir())
        };
    }

    private static File subDir(File base) {
        return base == null ? null : new File(base, COPY_SUB_DIR);
    }

    static boolean isUnderDir(File file, File dir) {
        if (file == null || dir == null) {
            return false;
        }
        try {
            String filePath = file.getCanonicalPath();
            String dirPath = dir.getCanonicalPath();
            if (!dirPath.endsWith(File.separator)) {
                dirPath = dirPath + File.separator;
            }
            return filePath.equals(dir.getCanonicalPath()) || filePath.startsWith(dirPath);
        } catch (Exception e) {
            return false;
        }
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
                if (ext.matches("[a-z0-9]{1,10}")) {
                    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                }
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
        if (!TextUtils.isEmpty(displayName)
                && !displayName.contains("/")
                && !displayName.contains("\\")
                && !displayName.contains("..")) {
            int dot = displayName.lastIndexOf('.');
            if (dot >= 0 && dot < displayName.length() - 1) {
                String ext = displayName.substring(dot).toLowerCase(Locale.US);
                if (SAFE_EXT.matcher(ext).matches()) {
                    return ext;
                }
            }
        }
        if (!TextUtils.isEmpty(mime)) {
            String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
            if (!TextUtils.isEmpty(ext) && ext.matches("[a-z0-9]{1,10}")) {
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
