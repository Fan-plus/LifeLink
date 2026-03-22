package com.example.lifelink.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lifelink.R;
import com.example.lifelink.data.reminder.ReminderItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReminderAdapter extends RecyclerView.Adapter<ReminderAdapter.VH> {
    public interface Listener { void onDelete(ReminderItem item); }

    private List<ReminderItem> data = new ArrayList<>();
    private Listener listener;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA);

    public ReminderAdapter(Listener l) { this.listener = l; }

    public void setData(List<ReminderItem> list) { 
        data.clear(); 
        if (list != null) data.addAll(list); 
        notifyDataSetChanged(); 
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reminder, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        ReminderItem item = data.get(position);
        holder.msg.setText(item.getMessage());
        
        // ⭐ 动态显示提醒时间，不再是死代码
        String timeStr = timeFormat.format(new Date(item.getTimestamp()));
        if (holder.timeText != null) {
            holder.timeText.setText(timeStr);
        }

        holder.del.setOnClickListener(v -> { 
            if (listener != null) listener.onDelete(item); 
        });
    }

    @Override
    public int getItemCount() { return data.size(); }

    public static class VH extends RecyclerView.ViewHolder {
        TextView msg;
        TextView timeText; // ⭐ 新增时间显示组件
        ImageView del;
        public VH(@NonNull View itemView) {
            super(itemView);
            msg = itemView.findViewById(R.id.reminder_message);
            timeText = itemView.findViewById(R.id.reminder_time);
            del = itemView.findViewById(R.id.reminder_delete);
        }
    }
}
