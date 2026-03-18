package com.example.lifelink.ui.warm;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.lifelink.R;
import com.example.lifelink.data.reminder.ReminderDbHelper;
import com.example.lifelink.data.reminder.ReminderItem;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class WarmCompanionFragment extends Fragment {

    private ReminderDbHelper dbHelper;
    private TextView tvCurrentVoice;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_warm_companion, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        dbHelper = new ReminderDbHelper(requireContext());

        initViews(view);
        setupListeners(view);
    }

    private void initViews(View view) {
        tvCurrentVoice = view.findViewById(R.id.tv_current_voice);
    }

    private void setupListeners(View view) {
        // 1. 开启对谈
        view.findViewById(R.id.btn_voice_chat).setOnClickListener(v -> startVoiceChat(null, null));
        
        // 2. 设置提醒 (读取数据库并播报)
        view.findViewById(R.id.btn_set_reminder).setOnClickListener(v -> {
            List<ReminderItem> reminders = dbHelper.getAllReminders();
            if (reminders.isEmpty()) {
                startVoiceChat(null, "爷爷奶奶，您目前没有设置任何提醒哦。需要我帮您记下什么吗？");
            } else {
                StringBuilder sb = new StringBuilder("爷爷奶奶，为您查询到以下提醒：");
                for (ReminderItem item : reminders) {
                    sb.append(item.getMessage()).append("；");
                }
                startVoiceChat(null, sb.toString());
            }
        });

        // 3. 快捷功能：吃药提醒 (针对性查询)
        view.findViewById(R.id.card_quick_med).setOnClickListener(v -> {
            List<ReminderItem> reminders = dbHelper.getAllReminders();
            String medInfo = "暂无吃药记录";
            for (ReminderItem item : reminders) {
                if (item.getMessage().contains("药")) {
                    medInfo = "为您找到吃药提醒：" + item.getMessage();
                    break;
                }
            }
            startVoiceChat(null, "好的，爷爷奶奶。" + medInfo);
        });

        // 4. 快捷功能：讲个故事
        view.findViewById(R.id.card_quick_story).setOnClickListener(v -> {
            String prompt = "请给老人讲一个简短、温馨的民间小故事，字数在100字左右。";
            startVoiceChat(prompt, "好的，爷爷奶奶，豆豆给您讲个故事。");
        });

        // 5. 快捷功能：日常唠嗑
        view.findViewById(R.id.card_quick_chat).setOnClickListener(v -> {
            String prompt = "作为一个贴心的孙辈，主动发起一个温馨的话题陪老人聊天，比如问问晚餐想吃什么，或者关心一下天气。";
            startVoiceChat(prompt, "爷爷奶奶，豆豆想陪您聊聊天。");
        });

        // 6. 音色定制相关 (暂未实现后端)
        view.findViewById(R.id.btn_change_voice).setOnClickListener(v -> 
            Toast.makeText(getContext(), "正在为您匹配更多萌萌的音色...", Toast.LENGTH_SHORT).show());
        
        view.findViewById(R.id.btn_upload_voice).setOnClickListener(v -> 
            Toast.makeText(getContext(), "请联系子女在‘亲属互联’中上传您的原声...", Toast.LENGTH_SHORT).show());

        // 7. 底部大按钮：按住说话
        view.findViewById(R.id.btn_press_to_talk).setOnClickListener(v -> startVoiceChat(null, null));
    }

    private void startVoiceChat(String initialPrompt, String initialSpeech) {
        Intent intent = new Intent(getActivity(), VoiceChatActivity.class);
        if (initialPrompt != null) intent.putExtra(VoiceChatActivity.EXTRA_INITIAL_PROMPT, initialPrompt);
        if (initialSpeech != null) intent.putExtra(VoiceChatActivity.EXTRA_INITIAL_SPEECH, initialSpeech);
        startActivity(intent);
    }
}
