package com.hss01248.media_picker.internal;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Size;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.blankj.utilcode.util.LogUtils;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 从 content Uri 加载缩略图，强制 subsample，避免大图进内存。
 */
final class MediaThumbLoader {

    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** 目标边长 px，按 3 列缩略图足够 */
    private static final int DEFAULT_SIZE_PX = 240;

    private MediaThumbLoader() {
    }

    static void loadInto(@NonNull Context context,
                         @NonNull Uri uri,
                         @NonNull ImageView imageView) {
        loadInto(context, uri, imageView, DEFAULT_SIZE_PX);
    }

    static void loadInto(@NonNull Context context,
                         @NonNull Uri uri,
                         @NonNull ImageView imageView,
                         int sizePx) {
        final Object token = uri;
        imageView.setTag(token);
        imageView.setImageDrawable(null);
        final Context app = context.getApplicationContext();
        IO.execute(() -> {
            Bitmap bitmap = decodeSampled(app, uri, sizePx);
            MAIN.post(() -> {
                if (token.equals(imageView.getTag())) {
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap);
                    } else {
                        imageView.setImageDrawable(null);
                    }
                } else if (bitmap != null) {
                    bitmap.recycle();
                }
            });
        });
    }

    @Nullable
    private static Bitmap decodeSampled(@NonNull Context context, @NonNull Uri uri, int sizePx) {
        ContentResolver resolver = context.getContentResolver();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return resolver.loadThumbnail(uri, new Size(sizePx, sizePx), null);
            } catch (Exception e) {
                LogUtils.w(e);
            }
        }
        Bitmap fromStream = decodeFromStream(resolver, uri, sizePx);
        if (fromStream != null) {
            return fromStream;
        }
        return decodeVideoFrame(context, uri, sizePx);
    }

    @Nullable
    private static Bitmap decodeFromStream(@NonNull ContentResolver resolver,
                                           @NonNull Uri uri,
                                           int sizePx) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream is = resolver.openInputStream(uri)) {
                if (is == null) {
                    return null;
                }
                BitmapFactory.decodeStream(is, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }
            int sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, sizePx, sizePx);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            try (InputStream is = resolver.openInputStream(uri)) {
                if (is == null) {
                    return null;
                }
                return BitmapFactory.decodeStream(is, null, opts);
            }
        } catch (Exception e) {
            LogUtils.w(e);
            return null;
        }
    }

    @Nullable
    private static Bitmap decodeVideoFrame(@NonNull Context context,
                                           @NonNull Uri uri,
                                           int sizePx) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            Bitmap frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frame == null) {
                return null;
            }
            int w = frame.getWidth();
            int h = frame.getHeight();
            int sample = calculateInSampleSize(w, h, sizePx, sizePx);
            if (sample <= 1) {
                return frame;
            }
            Bitmap scaled = Bitmap.createScaledBitmap(frame, w / sample, h / sample, true);
            if (scaled != frame) {
                frame.recycle();
            }
            return scaled;
        } catch (Exception e) {
            LogUtils.w(e);
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    private static int calculateInSampleSize(int width, int height, int reqWidth, int reqHeight) {
        int inSampleSize = 1;
        if (width <= 0 || height <= 0) {
            return inSampleSize;
        }
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return Math.max(1, inSampleSize);
    }
}
