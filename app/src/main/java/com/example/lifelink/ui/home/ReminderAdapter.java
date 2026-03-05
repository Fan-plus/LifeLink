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

import java.util.ArrayList;
import java.util.List;

public class ReminderAdapter extends RecyclerView.Adapter<ReminderAdapter.VH> {
    public interface Listener { void onDelete(ReminderItem item); }

    private List<ReminderItem> data = new ArrayList<>();
    private Listener listener;

    public ReminderAdapter(Listener l) { this.listener = l; }

    public void setData(List<ReminderItem> list) { data.clear(); if (list != null) data.addAll(list); notifyDataSetChanged(); }

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
        holder.del.setOnClickListener(v -> { if (listener != null) listener.onDelete(item); });
    }

    @Override
    public int getItemCount() { return data.size(); }

    public static class VH extends RecyclerView.ViewHolder {
        TextView msg;
        ImageView del;
        public VH(@NonNull View itemView) {
            super(itemView);
            msg = itemView.findViewById(R.id.reminder_message);
            del = itemView.findViewById(R.id.reminder_delete);
        }
    }
}
