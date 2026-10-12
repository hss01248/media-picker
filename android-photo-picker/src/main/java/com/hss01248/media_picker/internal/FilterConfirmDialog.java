package com.hss01248.media_picker.internal;

import android.app.Dialog;
import android.content.Context;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hss01248.media_picker.R;

/**
 * 过滤结果确认弹窗：不合格区在上，可用区在下；Back 禁用。
 */
final class FilterConfirmDialog {

    interface Callback {
        void onCancel();

        void onReselect();

        void onConfirm();
    }

    private FilterConfirmDialog() {
    }

    static void show(@NonNull Context context,
                     @NonNull PhotoPickerConfig config,
                     @NonNull FilterResult result,
                     @NonNull Callback callback) {
        View content = LayoutInflater.from(context).inflate(R.layout.dialog_filter_confirm, null);

        TextView tvRejectedTitle = content.findViewById(R.id.tv_rejected_title);
        TextView tvRejectedSummary = content.findViewById(R.id.tv_rejected_summary);
        RecyclerView rvRejected = content.findViewById(R.id.rv_rejected);
        LinearLayout layoutAccepted = content.findViewById(R.id.layout_accepted);
        TextView tvAcceptedTitle = content.findViewById(R.id.tv_accepted_title);
        RecyclerView rvAccepted = content.findViewById(R.id.rv_accepted);
        TextView btnCancel = content.findViewById(R.id.btn_cancel);
        TextView btnReselect = content.findViewById(R.id.btn_reselect);
        TextView btnConfirm = content.findViewById(R.id.btn_confirm);

        int rejectedCount = result.rejected.size();
        int acceptedCount = result.accepted.size();

        tvRejectedTitle.setText(context.getString(
                R.string.media_picker_filter_rejected_title, rejectedCount));

        String summary = result.skips.buildSummaryMessage(context, config);
        if (result.skips.hasExceedCountSkips()) {
            String orderHint = context.getString(R.string.media_picker_filter_keep_order_hint);
            summary = TextUtils.isEmpty(summary) ? orderHint : summary + "\n" + orderHint;
        }
        tvRejectedSummary.setText(summary);
        tvRejectedSummary.setVisibility(TextUtils.isEmpty(summary) ? View.GONE : View.VISIBLE);

        rvRejected.setLayoutManager(new GridLayoutManager(context, 3));
        rvRejected.setAdapter(FilterMediaAdapter.forRejected(result.rejected, config));
        rvRejected.setHasFixedSize(false);

        if (acceptedCount > 0) {
            layoutAccepted.setVisibility(View.VISIBLE);
            tvAcceptedTitle.setText(context.getString(
                    R.string.media_picker_filter_accepted_title, acceptedCount));
            rvAccepted.setLayoutManager(new GridLayoutManager(context, 3));
            rvAccepted.setAdapter(FilterMediaAdapter.forAccepted(result.accepted));
            rvAccepted.setHasFixedSize(false);
            btnConfirm.setVisibility(View.VISIBLE);
        } else {
            layoutAccepted.setVisibility(View.GONE);
            btnConfirm.setVisibility(View.GONE);
        }

        btnCancel.setText(R.string.media_picker_btn_cancel);
        btnReselect.setText(R.string.media_picker_btn_reselect);
        btnConfirm.setText(R.string.media_picker_btn_confirm);

        final Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(content);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnKeyListener((d, keyCode, event) ->
                keyCode == KeyEvent.KEYCODE_BACK);

        final int dialogWidth = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.92f);
        final int maxDialogHeight = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.75f);

        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = dialogWidth;
            // 先按内容自适应，show 后再按最大高度裁剪
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(lp);
        }

        btnCancel.setOnClickListener(v -> {
            dialog.dismiss();
            callback.onCancel();
        });
        btnReselect.setOnClickListener(v -> {
            dialog.dismiss();
            callback.onReselect();
        });
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            callback.onConfirm();
        });

        dialog.show();

        // 内容少时贴合高度；内容过多时限制最大高度并让内部滚动
        content.post(() -> {
            Window w = dialog.getWindow();
            if (w == null || !dialog.isShowing()) {
                return;
            }
            int measured = content.getMeasuredHeight();
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.width = dialogWidth;
            if (measured > maxDialogHeight) {
                lp.height = maxDialogHeight;
                View scroll = content.findViewById(R.id.scroll_content);
                if (scroll != null) {
                    LinearLayout.LayoutParams sp =
                            (LinearLayout.LayoutParams) scroll.getLayoutParams();
                    sp.height = 0;
                    sp.weight = 1f;
                    scroll.setLayoutParams(sp);
                }
            } else {
                lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            }
            w.setAttributes(lp);
        });
    }
}
