package com.hss01248.media_picker.internal;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.hss01248.media_picker.R;

import java.util.Locale;

/**
 * 单项被过滤的原因。
 */
enum FilterRejectReason {
    FILE_TOO_LARGE(R.string.media_picker_filter_reason_too_large),
    VIDEO_TOO_LONG(R.string.media_picker_filter_reason_too_long),
    EXCEED_MAX_VIDEO(R.string.media_picker_filter_reason_max_video),
    EXCEED_MAX_SELECT(R.string.media_picker_filter_reason_max_select),
    OTHER(R.string.media_picker_filter_reason_other);

    @StringRes
    final int shortLabelRes;

    FilterRejectReason(@StringRes int shortLabelRes) {
        this.shortLabelRes = shortLabelRes;
    }

    @NonNull
    String formatShortLabel(@NonNull Context context, @NonNull PhotoPickerConfig config) {
        switch (this) {
            case FILE_TOO_LARGE:
                return context.getString(shortLabelRes, formatMb(config.maxFileSizeMb));
            case VIDEO_TOO_LONG:
                return context.getString(shortLabelRes, config.videoMaxSecond);
            case EXCEED_MAX_VIDEO:
            case EXCEED_MAX_SELECT:
            case OTHER:
            default:
                return context.getString(shortLabelRes);
        }
    }

    private static String formatMb(float mb) {
        if (mb == (long) mb) {
            return String.valueOf((long) mb);
        }
        return String.format(Locale.US, "%.1f", mb);
    }
}
