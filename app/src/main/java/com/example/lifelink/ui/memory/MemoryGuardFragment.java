package com.example.lifelink.ui.memory;

import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.fragment.app.Fragment;

import java.util.Locale;

import com.example.lifelink.R;
import com.example.lifelink.data.memory.MemoryDbHelper;
import com.example.lifelink.data.memory.MemoryItem;
import com.example.lifelink.ui.memory.MemoryAdapter;

public class MemoryGuardFragment extends Fragment implements View.OnClickListener {

    private EditText memoryInput;
    private Button addMemoryBtn;
    private RecyclerView memoryRecycler;
    private MemoryAdapter memoryAdapter;
    private MemoryDbHelper dbHelper;
    private Button shootMedicineBtn;
    private Button collectBtn;
    private Button aiChatBtn;

    private static final int REQUEST_IMAGE_CAPTURE = 1;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_memory_guard, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeViews(view);
        setupListeners();
    }

    private void initializeViews(View view) {
        memoryInput = view.findViewById(R.id.memory_input);
        addMemoryBtn = view.findViewById(R.id.memory_add_btn);
        memoryRecycler = view.findViewById(R.id.memory_recycler);

        dbHelper = new MemoryDbHelper(getContext());
        memoryAdapter = new MemoryAdapter(getContext(), dbHelper.getAllMemories());
        GridLayoutManager glm = new GridLayoutManager(getContext(), 2);
        memoryRecycler.setLayoutManager(glm);
        memoryRecycler.setAdapter(memoryAdapter);

        shootMedicineBtn = view.findViewById(R.id.medicine_shoot_btn);
        collectBtn = view.findViewById(R.id.medicine_collect);

        aiChatBtn = view.findViewById(R.id.ai_chat_fab);
    }

    private void setupListeners() {
        addMemoryBtn.setOnClickListener(this);
        shootMedicineBtn.setOnClickListener(this);
        collectBtn.setOnClickListener(this);
        aiChatBtn.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.memory_add_btn) handleAddMemory();
        else if (v.getId() == R.id.medicine_shoot_btn) handleShootMedicine();
        else if (v.getId() == R.id.medicine_collect) handleCollect();
        else if (v.getId() == R.id.ai_chat_fab) handleAIChat();
    }

    private void handleAddMemory() {
        String input = memoryInput.getText().toString().trim();
        if (TextUtils.isEmpty(input)) { Toast.makeText(getContext(), "请输入要记忆的物品", Toast.LENGTH_SHORT).show(); return; }
        String[] parts = parseTitleAndNote(input);
        String title = parts[0];
        String note = parts[1];

        MemoryItem exact = dbHelper.getMemoryByTitle(title);
        if (exact != null) {
            boolean ok = dbHelper.updateMemory(exact.getId(), title, note);
            if (ok) { memoryAdapter.setData(dbHelper.getAllMemories()); memoryInput.setText(""); Toast.makeText(getContext(), "已更新：" + title, Toast.LENGTH_SHORT).show(); }
            else Toast.makeText(getContext(), "更新失败，请重试", Toast.LENGTH_SHORT).show();
            return;
        }

        String normTitle = normalizeTitle(title);
        MemoryItem similar = null;
        for (MemoryItem mi : dbHelper.getAllMemories()) {
            String otherNorm = normalizeTitle(mi.getTitle());
            if (otherNorm.equals(normTitle) || otherNorm.contains(normTitle) || normTitle.contains(otherNorm)) { similar = mi; break; }
        }

        if (similar != null) {
            final MemoryItem similarFound = similar;
            String msg = "检测到已存在类似记忆：\n\n'" + similarFound.getTitle() + "' → " + similarFound.getNote() + "\n\n是否将位置/备注更新为：\n\n'" + note + "' ?";
            new AlertDialog.Builder(getContext())
                    .setTitle("更新已存在记忆?")
                    .setMessage(msg)
                    .setPositiveButton("更新", (dialog, which) -> {
                        boolean ok = dbHelper.updateMemory(similarFound.getId(), title, note);
                        if (ok) { memoryAdapter.setData(dbHelper.getAllMemories()); memoryInput.setText(""); Toast.makeText(getContext(), "已更新：" + title, Toast.LENGTH_SHORT).show(); }
                        else Toast.makeText(getContext(), "更新失败，请重试", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", (dialog, which) -> {})
                    .show();
        } else {
            long id = dbHelper.addMemory(title, note);
            if (id > 0) { memoryAdapter.setData(dbHelper.getAllMemories()); memoryInput.setText(""); Toast.makeText(getContext(), "已添加：" + title, Toast.LENGTH_SHORT).show(); }
            else Toast.makeText(getContext(), "添加失败，请重试", Toast.LENGTH_SHORT).show();
        }
    }

    private String[] parseTitleAndNote(String input) {
        if (input == null) return new String[]{"",""};
        input = input.trim(); if (input.isEmpty()) return new String[]{"",""};
        int nl = input.indexOf('\n'); if (nl > 0) return new String[]{input.substring(0,nl).trim(), input.substring(nl+1).trim()};
        int idxBracket = input.indexOf('('); if (idxBracket < 0) idxBracket = input.indexOf('（');
        if (idxBracket > 0) return new String[]{input.substring(0, idxBracket).trim(), input.substring(idxBracket).trim()};
        String[] locPhrases = new String[]{"存放在", "放在", "放着", "放于", "位于", "放入", "放进", "放到", "在"};
        for (String p : locPhrases) { int i = input.indexOf(p); if (i > 0) return new String[]{input.substring(0,i).trim(), input.substring(i).trim()}; }
        String[] infoPhrases = new String[]{"用法", "用途", "用", "注意", "禁忌", "说明", "说明书"};
        for (String p : infoPhrases) { int i = input.indexOf(p); if (i > 0) return new String[]{input.substring(0,i).trim(), input.substring(i).trim()}; }
        String[] verbPhrases = new String[]{"是", "有", "属于"};
        for (String p : verbPhrases) { int i = input.indexOf(p); if (i > 0) return new String[]{input.substring(0,i).trim(), input.substring(i).trim()}; }
        String[] delims = new String[]{":", "：", "|", " - ", "—", "-", "，", ","};
        for (String d : delims) { int idx = input.indexOf(d); if (idx > 0) return new String[]{input.substring(0, idx).trim(), input.substring(idx + d.length()).trim()}; }
        return new String[]{input, ""};
    }

    private String normalizeTitle(String s) {
        if (s == null) return "";
        String t = s.trim().toLowerCase();
        t = t.replaceAll("[\\p{Punct}\\u3000-\\u303F\\uFF00-\\uFFEF]", "");
        String[] stop = new String[]{"个", "只", "片", "盒", "本", "条", "瓶", "把", "张", "台"};
        for (String sp : stop) t = t.replace(sp, "");
        t = t.replaceAll("\\s+", " ").trim();
        return t;
    }

    private void handleShootMedicine() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getActivity().getPackageManager()) != null) startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        else Toast.makeText(getContext(), "设备不支持拍照功能", Toast.LENGTH_SHORT).show();
    }

    private void handleReplayVoice() { Toast.makeText(getContext(), "正在为您播放语音解读...", Toast.LENGTH_SHORT).show(); }
    private void handleRegenVideo() { Toast.makeText(getContext(), "正在重新生成 3D 讲解视频...", Toast.LENGTH_SHORT).show(); }
    private void handleCollect() { Toast.makeText(getContext(), "讲解内容已收藏到您的记忆库", Toast.LENGTH_SHORT).show(); }

    private void handleAIChat() {
        // voice-chat button placeholder; actual speech code to be added later
        Toast.makeText(getContext(), "语音对话功能（待实现）", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroy() { super.onDestroy(); }

    @Override
    public void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == android.app.Activity.RESULT_OK) {
            Toast.makeText(getContext(), "照片已获取，正在识别药品...", Toast.LENGTH_SHORT).show();
        }
    }
}
