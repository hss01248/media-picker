package com.hss01248.media_picker.internal;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 过滤结果：可用项 + 被拒项 + 汇总计数。
 */
final class FilterResult {

    @NonNull
    final List<Uri> accepted;
    @NonNull
    final List<RejectedItem> rejected;
    @NonNull
    final FilterSkipCollector skips;

    FilterResult(@NonNull List<Uri> accepted,
                 @NonNull List<RejectedItem> rejected,
                 @NonNull FilterSkipCollector skips) {
        this.accepted = Collections.unmodifiableList(new ArrayList<>(accepted));
        this.rejected = Collections.unmodifiableList(new ArrayList<>(rejected));
        this.skips = skips;
    }

    boolean hasRejected() {
        return !rejected.isEmpty();
    }
}
