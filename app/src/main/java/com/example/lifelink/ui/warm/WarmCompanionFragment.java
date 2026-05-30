package com.example.lifelink.ui.warm;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
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
import com.example.lifelink.guard.FloatingGuardService;
import com.example.lifelink.ui.activity.MainActivity;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WarmCompanionFragment extends Fragment {

    private MaterialSwitch guardSwitch;
    private TextView guardDesc;
    private static final int REQUEST_OVERLAY_PERMISSION = 1234;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_warm_companion, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        guardSwitch = view.findViewById(R.id.switch_ai_guard);
        guardDesc = view.findViewById(R.id.tv_guard_desc);

        guardSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                checkOverlayPermission();
            } else {
                stopGuardService();
            }
        });

        // 绑定语音对话
        View voiceBtn = view.findViewById(R.id.btn_voice_chat);
        if (voiceBtn != null) {
            voiceBtn.setOnClickListener(v -> {
                openVoiceChat(null, null);
            });
        }

        View setReminderBtn = view.findViewById(R.id.btn_set_reminder);
        if (setReminderBtn != null) {
            setReminderBtn.setOnClickListener(v -> {
                switchToHome();
                Toast.makeText(getContext(), "请在首页按住语音按钮，说出提醒时间和内容", Toast.LENGTH_LONG).show();
            });
        }

        View quickMed = view.findViewById(R.id.card_quick_med);
        if (quickMed != null) {
            quickMed.setOnClickListener(v -> handleMedicineReminderClick());
        }

        View quickStory = view.findViewById(R.id.card_quick_story);
        if (quickStory != null) {
            quickStory.setOnClickListener(v -> openVoiceChat(
                    "请给老人讲一个温暖、简短、适合睡前或休息时听的小故事。要求：中文，语气像亲切的小孙辈，故事积极安心，控制在300字以内，讲完后自然问一句还想不想再听。",
                    null
            ));
        }

        View quickChat = view.findViewById(R.id.card_quick_chat);
        if (quickChat != null) {
            quickChat.setOnClickListener(v -> openVoiceChat(
                    "老人点击了“日常唠嗑”。请像亲切的小孙辈一样主动开启一段轻松聊天，先问候今天过得怎么样，再给一个容易回答的话题。回答简短、温暖、自然。",
                    null
            ));
        }
    }

    private void handleMedicineReminderClick() {
        if (getContext() == null) return;

        ReminderDbHelper db = new ReminderDbHelper(requireContext());
        List<ReminderItem> reminders = db.getAllReminders();

        if (reminders != null && !reminders.isEmpty()) {
            ReminderItem next = reminders.get(0);
            String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(next.getTimestamp()));
            switchToHome();
            Toast.makeText(getContext(), "已切到首页，最近提醒：" + time + " " + next.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        openVoiceChat(
                "老人点击了“吃药提醒”，但本地提醒列表里暂时没有任何待办提醒。请通过远程AI给出温暖、负责的中文回答：先说明目前没有查到已设置的吃药提醒，再建议老人核对药盒/医嘱，必要时请家人帮忙设置提醒。不要编造具体药名或剂量，语气亲切简短。",
                null
        );
    }

    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(requireContext())) {
                Toast.makeText(getContext(), "开启反诈哨兵需要悬浮窗权限", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + requireActivity().getPackageName()));
                startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
                guardSwitch.setChecked(false);
            } else {
                startGuardService();
            }
        } else {
            startGuardService();
        }
    }

    private void startGuardService() {
        Intent intent = new Intent(getActivity(), FloatingGuardService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireActivity().startForegroundService(intent);
        } else {
            requireActivity().startService(intent);
        }
        guardDesc.setText("🛡️ AI 哨兵已开启：实时守护通话安全");
        guardDesc.setTextColor(getResources().getColor(R.color.memory_health_green));
    }

    private void stopGuardService() {
        Intent intent = new Intent(getActivity(), FloatingGuardService.class);
        requireActivity().stopService(intent);
        guardDesc.setText("点击开启通话实时守护");
        guardDesc.setTextColor(0xFF666666);
    }

    private void openVoiceChat(String prompt, String openingSpeech) {
        Intent intent = new Intent(requireContext(), VoiceChatActivity.class);
        if (prompt != null) intent.putExtra(VoiceChatActivity.EXTRA_INITIAL_PROMPT, prompt);
        if (openingSpeech != null) intent.putExtra(VoiceChatActivity.EXTRA_INITIAL_SPEECH, openingSpeech);
        startActivity(intent);
    }

    private void switchToHome() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).switchToTab(0);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(getContext())) {
                guardSwitch.setChecked(true);
            }
        }
    }
}
