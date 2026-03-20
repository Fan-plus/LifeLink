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
import com.example.lifelink.guard.FloatingGuardService;
import com.google.android.material.materialswitch.MaterialSwitch;

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
                startActivity(new Intent(getActivity(), VoiceChatActivity.class));
            });
        }
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
