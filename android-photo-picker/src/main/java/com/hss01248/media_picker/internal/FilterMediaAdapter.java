package com.hss01248.media_picker.internal;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.hss01248.media_picker.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 过滤确认弹窗缩略图网格。rejected 模式显示蒙版 + 下方原因文案。
 */
final class FilterMediaAdapter extends RecyclerView.Adapter<FilterMediaAdapter.Holder> {

    private final boolean rejectedMode;
    @Nullable
    private final PhotoPickerConfig config;
    private final List<Uri> uris = new ArrayList<>();
    private final List<RejectedItem> rejectedItems = new ArrayList<>();

    static FilterMediaAdapter forAccepted(@NonNull List<Uri> accepted) {
        FilterMediaAdapter adapter = new FilterMediaAdapter(false, null);
        adapter.uris.addAll(accepted);
        return adapter;
    }

    static FilterMediaAdapter forRejected(@NonNull List<RejectedItem> rejected,
                                          @NonNull PhotoPickerConfig config) {
        FilterMediaAdapter adapter = new FilterMediaAdapter(true, config);
        adapter.rejectedItems.addAll(rejected);
        for (RejectedItem item : rejected) {
            adapter.uris.add(item.uri);
        }
        return adapter;
    }

    private FilterMediaAdapter(boolean rejectedMode, @Nullable PhotoPickerConfig config) {
        this.rejectedMode = rejectedMode;
        this.config = config;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_filter_media, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Uri uri = uris.get(position);
        MediaThumbLoader.loadInto(holder.itemView.getContext(), uri, holder.ivThumb);
        if (rejectedMode && config != null) {
            holder.vMask.setVisibility(View.VISIBLE);
            holder.tvReason.setVisibility(View.VISIBLE);
            RejectedItem item = rejectedItems.get(position);
            holder.tvReason.setText(item.reason.formatShortLabel(holder.itemView.getContext(), config));
        } else {
            holder.vMask.setVisibility(View.GONE);
            holder.tvReason.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return uris.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView ivThumb;
        final View vMask;
        final TextView tvReason;

        Holder(@NonNull View itemView) {
            super(itemView);
            ivThumb = itemView.findViewById(R.id.iv_thumb);
            vMask = itemView.findViewById(R.id.v_mask);
            tvReason = itemView.findViewById(R.id.tv_reason);
        }
    }
}
