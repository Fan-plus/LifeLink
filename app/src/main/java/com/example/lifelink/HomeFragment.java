package com.example.lifelink;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

public class HomeFragment extends Fragment {

    // UI组件
    private TextView heartRateText;
    private TextView watchStatusText;
    private TextView locationStatusText;
    private Button refreshCheckinButton;
    private Button voiceSearchButton;

    // 健康数据接口回调
    public interface HealthDataCallback {
        void onHealthDataReceived(HealthData healthData);
        void onDataError(String errorMessage);
    }

    // 健康数据模型
    public static class HealthData {
        private int heartRate;
        private boolean isWatchConnected;
        private boolean isLocationEnabled;
        private boolean isInSafeZone;

        // 构造函数、getter和setter方法
        public HealthData(int heartRate, boolean isWatchConnected, boolean isLocationEnabled, boolean isInSafeZone) {
            this.heartRate = heartRate;
            this.isWatchConnected = isWatchConnected;
            this.isLocationEnabled = isLocationEnabled;
            this.isInSafeZone = isInSafeZone;
        }

        public int getHeartRate() { return heartRate; }
        public void setHeartRate(int heartRate) { this.heartRate = heartRate; }
        public boolean isWatchConnected() { return isWatchConnected; }
        public void setWatchConnected(boolean watchConnected) { isWatchConnected = watchConnected; }
        public boolean isLocationEnabled() { return isLocationEnabled; }
        public void setLocationEnabled(boolean locationEnabled) { isLocationEnabled = locationEnabled; }
        public boolean isInSafeZone() { return isInSafeZone; }
        public void setInSafeZone(boolean inSafeZone) { isInSafeZone = inSafeZone; }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // 初始化UI组件
        initializeViews(view);

        // 设置点击事件
        setClickListeners();

        // 模拟加载健康数据
        loadHealthData();

        return view;
    }

    // 初始化UI组件
    private void initializeViews(View view) {
        heartRateText = view.findViewById(R.id.heart_rate_text);
        watchStatusText = view.findViewById(R.id.watch_status_text);
        locationStatusText = view.findViewById(R.id.location_status_text);
        refreshCheckinButton = view.findViewById(R.id.refresh_checkin_button);
        voiceSearchButton = view.findViewById(R.id.voice_search_button);
        
        // 初始化功能卡片
        btnMedicineIdentify = view.findViewById(R.id.btn_medicine_identify);
        btnAbnormalWarning = view.findViewById(R.id.btn_abnormal_warning);
        btnContactChildren = view.findViewById(R.id.btn_contact_children);
        btnWarmCompanion = view.findViewById(R.id.btn_warm_companion);
        btnMyMemories = view.findViewById(R.id.btn_my_memories);
        btnWillSafe = view.findViewById(R.id.btn_will_safe);
    }

    // 设置点击事件
    private void setClickListeners() {
        // 一键重新打卡按钮
        refreshCheckinButton.setOnClickListener(v -> {
            performRefreshCheckin();
        });
    
        // 语音搜索按钮
        voiceSearchButton.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    // 按下时开始录音
                    startVoiceRecording();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    // 松开时停止录音并处理
                    stopVoiceRecordingAndProcess();
                    break;
            }
            return true;
        });
    
        // 功能卡片点击事件
        btnMedicineIdentify.setOnClickListener(v -> navigateToFragment(1)); // 药品识别 → 记忆守护页
        btnAbnormalWarning.setOnClickListener(v -> navigateToFragment(2)); // 异常预警 → 健康监测页
        btnContactChildren.setOnClickListener(v -> navigateToFragment(3)); // 联系子女 → 亲属互联页
        btnWarmCompanion.setOnClickListener(v -> navigateToFragment(4)); // 暖心陪伴 → 暖心陪伴页
        btnMyMemories.setOnClickListener(v -> navigateToFragment(5)); // 我的回忆录 → 时光珍藏页
        btnWillSafe.setOnClickListener(v -> navigateToFragment(5)); // 遗嘱保险箱 → 时光珍藏页
    }

    // 页面跳转方法
    private void navigateToFragment(int position) {
        // 获取父Activity的ViewPager2并设置当前项
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity != null) {
            ViewPager2 viewPager = mainActivity.findViewById(R.id.view_pager);
            if (viewPager != null) {
                viewPager.setCurrentItem(position);
            }
        }
    }

    // 添加私有成员变量
    private LinearLayout btnMedicineIdentify;
    private LinearLayout btnAbnormalWarning;
    private LinearLayout btnContactChildren;
    private LinearLayout btnWarmCompanion;
    private LinearLayout btnMyMemories;
    private LinearLayout btnWillSafe;

    // 加载健康数据（预留接口）
    private void loadHealthData() {
        // 这里模拟从服务器或设备获取健康数据
        // 实际项目中可以替换为真实的API调用
        simulateHealthDataFetching(new HealthDataCallback() {
            @Override
            public void onHealthDataReceived(HealthData healthData) {
                updateHealthUI(healthData);
            }

            @Override
            public void onDataError(String errorMessage) {
                Toast.makeText(getContext(), errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // 模拟获取健康数据
    private void simulateHealthDataFetching(HealthDataCallback callback) {
        // 模拟网络延迟
        new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // 创建模拟健康数据
            HealthData mockData = new HealthData(
                    72,  // 心率
                    true,  // 手表已连接
                    true,  // 定位已开启
                    true   // 在安全区域内
            );

            // 切换到UI线程更新数据
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    callback.onHealthDataReceived(mockData);
                });
            }
        }).start();
    }

    // 更新健康数据UI
    private void updateHealthUI(HealthData healthData) {
        if (healthData != null) {
            heartRateText.setText(String.format("当前心率: %d 次/分", healthData.getHeartRate()));
            watchStatusText.setText(healthData.isWatchConnected() ? 
                    "佩戴状态: 已佩戴 OPPO 手表" : "佩戴状态: 未佩戴手表");
            locationStatusText.setText(healthData.isLocationEnabled() ? 
                    (healthData.isInSafeZone() ? "定位状态: 已开启 · 安全区域" : "定位状态: 已开启 · 非安全区域") :
                    "定位状态: 未开启");
        }
    }

    // 执行重新打卡
    private void performRefreshCheckin() {
        // 模拟重新打卡操作
        Toast.makeText(getContext(), "正在重新打卡...", Toast.LENGTH_SHORT).show();

        // 实际项目中可以替换为真实的打卡API调用
        new Thread(() -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "重新打卡成功！", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    // 开始语音录音
    private void startVoiceRecording() {
        // 这里实现开始录音的逻辑
        Toast.makeText(getContext(), "开始录音...", Toast.LENGTH_SHORT).show();
    }

    // 停止语音录音并处理
    private void stopVoiceRecordingAndProcess() {
        // 这里实现停止录音并处理语音的逻辑
        Toast.makeText(getContext(), "停止录音，正在处理...", Toast.LENGTH_SHORT).show();
    }
}