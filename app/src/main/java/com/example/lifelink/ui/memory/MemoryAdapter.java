package com.example.lifelink.ui.memory;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import com.example.lifelink.R;
import com.example.lifelink.data.memory.MemoryItem;

public class MemoryAdapter extends RecyclerView.Adapter<MemoryAdapter.VH> {

    private final List<MemoryItem> items = new ArrayList<>();
    private final LayoutInflater inflater;

    public MemoryAdapter(Context ctx, List<MemoryItem> data) {
        this.inflater = LayoutInflater.from(ctx);
        if (data != null) items.addAll(data);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = inflater.inflate(R.layout.item_memory_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        MemoryItem it = items.get(position);
        holder.title.setText(it.getTitle());
        holder.note.setText(it.getNote());
        holder.icon.setImageResource(R.drawable.ic_memory);
    }

    @Override
    public int getItemCount() { return items.size(); }

    public void setData(List<MemoryItem> data) { items.clear(); if (data != null) items.addAll(data); notifyDataSetChanged(); }

    static class VH extends RecyclerView.ViewHolder {
        CardView card;
        ImageView icon;
        TextView title;
        TextView note;

        VH(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.memory_card_root);
            icon = itemView.findViewById(R.id.memory_card_icon);
            title = itemView.findViewById(R.id.memory_card_title);
            note = itemView.findViewById(R.id.memory_card_note);
        }
    }
}
