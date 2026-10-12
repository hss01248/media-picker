package com.hss01248.media_picker.internal;

import android.content.Intent;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

/**
 * 挂在宿主 Activity 上的隐形 Fragment，用 Activity Result API 启动 Picker。
 * <p>
 * 不用 {@code StartActivityResultHelper}：其 {@code forkIntent} 会丢掉 {@link Intent#getClipData()}，
 * 而系统 Photo Picker 多选结果只在 ClipData 里，导致重新选择后解析为空并误回调 onCancel。
 */
public final class PickerResultFragment extends Fragment {

    private static final String TAG = "media_picker_result_fragment";

    public interface ResultCallback {
        void onActivityResult(int resultCode, @Nullable Intent data);

        void onLaunchFailed(@NonNull Throwable error);
    }

    private ActivityResultLauncher<Intent> launcher;
    @Nullable
    private ResultCallback pendingCallback;

    @Override
    public void onCreate(@Nullable android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        launcher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::deliver);
    }

    private void deliver(@NonNull ActivityResult result) {
        ResultCallback cb = pendingCallback;
        pendingCallback = null;
        if (cb != null) {
            // 直接传递原始 Intent，保留 ClipData 与临时读权限
            cb.onActivityResult(result.getResultCode(), result.getData());
        }
    }

    void launch(@NonNull Intent intent, @NonNull ResultCallback callback) {
        pendingCallback = callback;
        try {
            launcher.launch(intent);
        } catch (Throwable t) {
            pendingCallback = null;
            callback.onLaunchFailed(t);
        }
    }

    @NonNull
    static PickerResultFragment get(@NonNull FragmentActivity activity) {
        FragmentManager fm = activity.getSupportFragmentManager();
        Fragment existing = fm.findFragmentByTag(TAG);
        if (existing instanceof PickerResultFragment) {
            return (PickerResultFragment) existing;
        }
        PickerResultFragment fragment = new PickerResultFragment();
        fm.beginTransaction()
                .add(fragment, TAG)
                .commitNowAllowingStateLoss();
        return fragment;
    }
}
