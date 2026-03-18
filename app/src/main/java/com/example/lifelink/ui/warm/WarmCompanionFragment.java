package com.example.lifelink.ui.warm;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.lifelink.R;
import com.google.android.material.button.MaterialButton;

public class WarmCompanionFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_warm_companion, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 绑定“开启对谈”按钮
        MaterialButton btnVoiceChat = view.findViewById(R.id.btn_voice_chat);
        if (btnVoiceChat != null) {
            btnVoiceChat.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), VoiceChatActivity.class);
                startActivity(intent);
            });
        }
        
        // 绑定底部的“按住说话”按钮 (也可以跳转到通话页面，或者实现另一种简单的弹出对话)
        View btnPressToTalk = view.findViewById(R.id.btn_press_to_talk);
        if (btnPressToTalk != null) {
            btnPressToTalk.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), VoiceChatActivity.class);
                startActivity(intent);
            });
        }
    }
}
