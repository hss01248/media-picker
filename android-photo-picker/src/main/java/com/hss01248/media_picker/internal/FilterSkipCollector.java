package com.hss01248.media_picker.internal;

import android.content.Context;
import android.text.TextUtils;

import com.hss01248.media_picker.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 汇总被过滤项，用于一次 Toast 提示。
 */
final class FilterSkipCollector {

    int fileTooLarge;
    int videoTooLong;
    int exceedMaxVideo;
    int exceedMaxSelect;
    int other;

    void fileTooLarge() {
        fileTooLarge++;
    }

    void videoTooLong() {
        videoTooLong++;
    }

    void exceedMaxVideo() {
        exceedMaxVideo++;
    }

    void exceedMaxSelect() {
        exceedMaxSelect++;
    }

    void other() {
        other++;
    }

    boolean hasSkips() {
        return fileTooLarge + videoTooLong + exceedMaxVideo + exceedMaxSelect + other > 0;
    }

    boolean hasExceedCountSkips() {
        return exceedMaxVideo + exceedMaxSelect > 0;
    }

    String buildSummaryMessage(Context context, PhotoPickerConfig config) {
        if (!hasSkips()) {
            return "";
        }
        List<String> parts = new ArrayList<>(5);
        if (fileTooLarge > 0) {
            parts.add(context.getString(
                    R.string.media_picker_skip_file_too_large,
                    fileTooLarge,
                    formatMb(config.maxFileSizeMb)));
        }
        if (videoTooLong > 0) {
            parts.add(context.getString(
                    R.string.media_picker_skip_video_too_long,
                    videoTooLong,
                    config.videoMaxSecond));
        }
        if (exceedMaxVideo > 0) {
            parts.add(context.getString(
                    R.string.media_picker_skip_max_video,
                    exceedMaxVideo,
                    config.maxVideoSelectNum));
        }
        if (exceedMaxSelect > 0) {
            parts.add(context.getString(
                    R.string.media_picker_skip_max_select,
                    exceedMaxSelect,
                    config.maxSelectNum));
        }
        if (other > 0) {
            parts.add(context.getString(R.string.media_picker_skip_other, other));
        }
        return TextUtils.join("; ", parts);
    }

    private static String formatMb(float mb) {
        if (mb == (long) mb) {
            return String.valueOf((long) mb);
        }
        return String.format(Locale.US, "%.1f", mb);
    }
}
