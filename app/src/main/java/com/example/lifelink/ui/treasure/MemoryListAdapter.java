package com.example.lifelink.ui.treasure;

import android.media.MediaPlayer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lifelink.R;
import com.example.lifelink.data.treasure.TreasureDbHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MemoryListAdapter extends RecyclerView.Adapter<MemoryListAdapter.ViewHolder> {

    private List<TreasureDbHelper.MemoryEntry> memories = new ArrayList<>();
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.CHINA);
    private SimpleDateFormat displayFormat = new SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA);
    private MediaPlayer mediaPlayer = null;

    public void setData(List<TreasureDbHelper.MemoryEntry> data) {
        this.memories = data;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_memory_entry, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TreasureDbHelper.MemoryEntry entry = memories.get(position);
        
        boolean isAudio = "audio".equals(entry.type);
        holder.ivIcon.setImageResource(isAudio ? R.drawable.ic_mic : R.drawable.ic_book);
        holder.tvDate.setText(dateFormat.format(new Date(entry.timestamp)));
        
        if (isAudio) {
            holder.tvPreview.setText("点击播放留声片段");
            holder.itemView.setOnClickListener(v -> playAudio(entry.content, v.getContext()));
        } else {
            holder.tvPreview.setText(entry.content);
            holder.itemView.setOnClickListener(v -> showTextDetail(entry, v.getContext()));
        }
    }

    private void showTextDetail(TreasureDbHelper.MemoryEntry entry, android.content.Context context) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_memory_detail, null);
        TextView tvDate = dialogView.findViewById(R.id.tv_detail_date);
        TextView tvContent = dialogView.findViewById(R.id.tv_detail_content);
        
        tvDate.setText(displayFormat.format(new Date(entry.timestamp)));
        tvContent.setText(entry.content);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(dialogView)
                .create();

        dialogView.findViewById(R.id.btn_close_dialog).setOnClickListener(v -> dialog.dismiss());
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        dialog.show();
    }

    private void playAudio(String path, android.content.Context context) {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(path);
            mediaPlayer.prepare();
            mediaPlayer.start();
            Toast.makeText(context, "正在回放这段留声记忆...", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(context, "这段记忆似乎有些模糊了 (文件读取失败)", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        return memories.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvPreview, tvDate;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_type_icon);
            tvPreview = itemView.findViewById(R.id.tv_memory_preview);
            tvDate = itemView.findViewById(R.id.tv_memory_date);
        }
    }
}
