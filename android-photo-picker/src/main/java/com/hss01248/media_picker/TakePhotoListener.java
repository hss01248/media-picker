package com.hss01248.media_picker;

import java.util.List;

public interface TakePhotoListener {

    void onSuccess(List<String> paths);

    default void onFail(String path, String msg) {
    }

    default void onCancel() {
    }
}
