package com.example.lifelink.ui.treasure;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lifelink.R;
import com.example.lifelink.data.treasure.TreasureDbHelper;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.List;

public class MemoryListActivity extends AppCompatActivity {

    private RecyclerView rvMemories;
    private MemoryListAdapter adapter;
    private TreasureDbHelper dbHelper;
    private String memoryType; // "text" or "audio"

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_memory_list);

        memoryType = getIntent().getStringExtra("memory_type");
        dbHelper = new TreasureDbHelper(this);

        initViews();
    }

    private void initViews() {
        // 更新标题
        TextView tvTitle = findViewById(R.id.tv_list_title);
        if (tvTitle != null) {
            tvTitle.setText("text".equals(memoryType) ? "岁月文字" : "留声往事");
        }

        // 处理自定义关闭按钮
        findViewById(R.id.btn_close_list).setOnClickListener(v -> finish());

        rvMemories = findViewById(R.id.rv_memories);
        rvMemories.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MemoryListAdapter();
        rvMemories.setAdapter(adapter);

        ExtendedFloatingActionButton fab = findViewById(R.id.fab_add_memory);
        fab.setOnClickListener(v -> {
            Intent intent;
            if ("text".equals(memoryType)) {
                intent = new Intent(this, TextMemoryActivity.class);
            } else {
                intent = new Intent(this, AudioMemoryActivity.class);
            }
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMemories();
    }

    private void loadMemories() {
        List<TreasureDbHelper.MemoryEntry> all = dbHelper.getAllMemories();
        List<TreasureDbHelper.MemoryEntry> filtered = new java.util.ArrayList<>();
        for (TreasureDbHelper.MemoryEntry e : all) {
            if (e.type.equals(memoryType)) {
                filtered.add(e);
            }
        }
        adapter.setData(filtered);
    }
}
