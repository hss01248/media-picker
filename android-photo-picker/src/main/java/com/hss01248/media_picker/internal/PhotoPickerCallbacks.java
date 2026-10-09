package com.hss01248.media_picker.internal;

import com.hss01248.media_picker.TakePhotoListener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 requestId 持有回调，避免静态单例互相覆盖；回调在结果交付后移除。
 */
final class PhotoPickerCallbacks {

    private static final Map<String, TakePhotoListener> LISTENERS = new ConcurrentHashMap<>();

    private PhotoPickerCallbacks() {
    }

    static String put(TakePhotoListener listener) {
        String id = UUID.randomUUID().toString();
        LISTENERS.put(id, listener);
        return id;
    }

    static TakePhotoListener get(String requestId) {
        if (requestId == null) {
            return null;
        }
        return LISTENERS.get(requestId);
    }

    static TakePhotoListener remove(String requestId) {
        if (requestId == null) {
            return null;
        }
        return LISTENERS.remove(requestId);
    }
}
