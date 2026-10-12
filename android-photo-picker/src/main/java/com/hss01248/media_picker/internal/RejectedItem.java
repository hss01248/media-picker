package com.hss01248.media_picker.internal;

import android.net.Uri;

import androidx.annotation.NonNull;

/**
 * 被过滤的单项。
 */
final class RejectedItem {

    @NonNull
    final Uri uri;
    @NonNull
    final FilterRejectReason reason;

    RejectedItem(@NonNull Uri uri, @NonNull FilterRejectReason reason) {
        this.uri = uri;
        this.reason = reason;
    }
}
