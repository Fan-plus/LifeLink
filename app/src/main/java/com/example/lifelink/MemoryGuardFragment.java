package com.example.lifelink;

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

import androidx.fragment.app.Fragment;

public class MemoryGuardFragment extends Fragment implements View.OnClickListener {

    private EditText memoryInput;
    private Button addMemoryBtn;
    private Button shootMedicineBtn;
    private Button replayVoiceBtn;
    private Button regenVideoBtn;
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
        // 记忆库相关
        memoryInput = view.findViewById(R.id.memory_input);
        addMemoryBtn = view.findViewById(R.id.memory_add_btn);

        // 药品识别相关
        shootMedicineBtn = view.findViewById(R.id.medicine_shoot_btn);
        replayVoiceBtn = view.findViewById(R.id.medicine_replay_voice);
        regenVideoBtn = view.findViewById(R.id.medicine_regen_video);
        collectBtn = view.findViewById(R.id.medicine_collect);

        // AI 对话浮动按钮
        aiChatBtn = view.findViewById(R.id.ai_chat_fab);
    }

    private void setupListeners() {
        addMemoryBtn.setOnClickListener(this);
        shootMedicineBtn.setOnClickListener(this);
        replayVoiceBtn.setOnClickListener(this);
        regenVideoBtn.setOnClickListener(this);
        collectBtn.setOnClickListener(this);
        aiChatBtn.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.memory_add_btn) {
            handleAddMemory();
        } else if (v.getId() == R.id.medicine_shoot_btn) {
            handleShootMedicine();
        } else if (v.getId() == R.id.medicine_replay_voice) {
            handleReplayVoice();
        } else if (v.getId() == R.id.medicine_regen_video) {
            handleRegenVideo();
        } else if (v.getId() == R.id.medicine_collect) {
            handleCollect();
        } else if (v.getId() == R.id.ai_chat_fab) {
            handleAIChat();
        }
    }

    /**
     * 处理添加记忆物品
     */
    private void handleAddMemory() {
        String input = memoryInput.getText().toString().trim();
        if (TextUtils.isEmpty(input)) {
            Toast.makeText(getContext(), "请输入要记忆的物品", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(getContext(), "已添加：" + input, Toast.LENGTH_SHORT).show();
        memoryInput.setText("");
        // 这里可以扩展为实际的数据库操作或更新UI
    }

    /**
     * 处理拍照识别药品
     */
    private void handleShootMedicine() {
        // 启动相机应用
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getActivity().getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        } else {
            Toast.makeText(getContext(), "设备不支持拍照功能", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 处理重播语音解读
     */
    private void handleReplayVoice() {
        Toast.makeText(getContext(), "正在为您播放语音解读...", Toast.LENGTH_SHORT).show();
        // 这里可以扩展为实际的文字转语音(TTS)功能
        // 示例：playText("这是降压药硝苯地平缓释片，每日一次，早晨服用，不要和酒精一起服用");
    }

    /**
     * 处理重新生成 3D 视频
     */
    private void handleRegenVideo() {
        Toast.makeText(getContext(), "正在重新生成 3D 讲解视频...", Toast.LENGTH_SHORT).show();
        // 这里可以扩展为实际的视频生成或网络请求
    }

    /**
     * 处理收藏讲解内容
     */
    private void handleCollect() {
        Toast.makeText(getContext(), "讲解内容已收藏到您的记忆库", Toast.LENGTH_SHORT).show();
        // 这里可以扩展为实际的收藏逻辑
    }

    /**
     * 处理 AI 助手对话
     */
    private void handleAIChat() {
        Toast.makeText(getContext(), "AI 助手正在连接...", Toast.LENGTH_SHORT).show();
        // 这里可以扩展为启动对话界面或弹窗
        // 示例：打开对话弹窗或导航到对话页面
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == android.app.Activity.RESULT_OK) {
            // 处理拍照后的结果
            Toast.makeText(getContext(), "照片已获取，正在识别药品...", Toast.LENGTH_SHORT).show();
            // 这里可以扩展为实际的图像识别逻辑
        }
    }
}
