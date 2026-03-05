package com.example.lifelink.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.lifelink.ui.activity.MainActivity;
import com.example.lifelink.R;
import com.example.lifelink.data.reminder.ReminderDbHelper;
import com.example.lifelink.data.reminder.ReminderItem;

import java.util.List;

public class HomeFragment extends Fragment {

    // UI组件
    private TextView heartRateText;
    private TextView watchStatusText;
    private TextView locationStatusText;
    private Button refreshCheckinButton;
    private Button voiceSearchButton;

    // reminder
    private RecyclerView medicineReminderRecycler;
    private ReminderAdapter reminderAdapter;
    private ReminderDbHelper reminderDb;

    // shortcut buttons
    private LinearLayout btnMedicineIdentify;
    private LinearLayout btnAbnormalWarning;
    private LinearLayout btnContactChildren;
    private LinearLayout btnWarmCompanion;
    private LinearLayout btnMyMemories;
    private LinearLayout btnWillSafe;

    public interface HealthDataCallback {
        void onHealthDataReceived(HealthData healthData);
        void onDataError(String errorMessage);
    }

    public static class HealthData {
        private int heartRate;
        private boolean isWatchConnected;
        private boolean isLocationEnabled;
        private boolean isInSafeZone;

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
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        initializeViews(view);
        setClickListeners();
        loadHealthData();

        // reminders DB + load
        reminderDb = new ReminderDbHelper(getContext());
        setupReminderList();
        loadReminders();

        return view;
    }

    private void initializeViews(View view) {
        heartRateText = view.findViewById(R.id.heart_rate_text);
        watchStatusText = view.findViewById(R.id.watch_status_text);
        locationStatusText = view.findViewById(R.id.location_status_text);
        refreshCheckinButton = view.findViewById(R.id.refresh_checkin_button);
        voiceSearchButton = view.findViewById(R.id.voice_search_button);

        medicineReminderRecycler = view.findViewById(R.id.medicine_reminder_card);

        btnMedicineIdentify = view.findViewById(R.id.btn_medicine_identify);
        btnAbnormalWarning = view.findViewById(R.id.btn_abnormal_warning);
        btnContactChildren = view.findViewById(R.id.btn_contact_children);
        btnWarmCompanion = view.findViewById(R.id.btn_warm_companion);
        btnMyMemories = view.findViewById(R.id.btn_my_memories);
        btnWillSafe = view.findViewById(R.id.btn_will_safe);
    }

    private void setClickListeners() {
        refreshCheckinButton.setOnClickListener(v -> performRefreshCheckin());

        voiceSearchButton.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    startVoiceRecording();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    stopVoiceRecordingAndProcess();
                    break;
            }
            return true;
        });

        btnMedicineIdentify.setOnClickListener(v -> navigateToFragment(1));
        btnAbnormalWarning.setOnClickListener(v -> navigateToFragment(2));
        btnContactChildren.setOnClickListener(v -> navigateToFragment(3));
        btnWarmCompanion.setOnClickListener(v -> navigateToFragment(4));
        btnMyMemories.setOnClickListener(v -> navigateToFragment(5));
        btnWillSafe.setOnClickListener(v -> navigateToFragment(5));
    }

    private void setupReminderList() {
        reminderAdapter = new ReminderAdapter(item -> {
            if (item != null && reminderDb != null) {
                reminderDb.deleteReminder(item.getId());
                Toast.makeText(getContext(), "已取消提醒", Toast.LENGTH_SHORT).show();
                loadReminders();
            }
        });
        LinearLayoutManager lm = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false);
        medicineReminderRecycler.setLayoutManager(lm);
        medicineReminderRecycler.setAdapter(reminderAdapter);
    }

    private void loadReminders() {
        if (reminderDb == null) return;
        List<ReminderItem> list = reminderDb.getAllReminders();
        if (list != null && !list.isEmpty()) {
            reminderAdapter.setData(list);
            medicineReminderRecycler.setVisibility(View.VISIBLE);
        } else {
            reminderAdapter.setData(null);
            medicineReminderRecycler.setVisibility(View.GONE);
        }
    }

    private void navigateToFragment(int position) {
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity != null) {
            ViewPager2 viewPager = mainActivity.findViewById(R.id.view_pager);
            if (viewPager != null) viewPager.setCurrentItem(position);
        }
    }

    private void loadHealthData() {
        simulateHealthDataFetching(new HealthDataCallback() {
            @Override
            public void onHealthDataReceived(HealthData healthData) { updateHealthUI(healthData); }

            @Override
            public void onDataError(String errorMessage) { Toast.makeText(getContext(), errorMessage, Toast.LENGTH_SHORT).show(); }
        });
    }

    private void simulateHealthDataFetching(HealthDataCallback callback) {
        new Thread(() -> {
            try { Thread.sleep(1000); } catch (InterruptedException e) { e.printStackTrace(); }
            HealthData mockData = new HealthData(72, true, true, true);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> callback.onHealthDataReceived(mockData));
            }
        }).start();
    }

    private void updateHealthUI(HealthData healthData) {
        if (healthData != null) {
            heartRateText.setText(String.format("当前心率: %d 次/分", healthData.getHeartRate()));
            watchStatusText.setText(healthData.isWatchConnected() ? "佩戴状态: 已佩戴 OPPO 手表" : "佩戴状态: 未佩戴手表");
            locationStatusText.setText(healthData.isLocationEnabled() ? (healthData.isInSafeZone() ? "定位状态: 已开启 · 安全区域" : "定位状态: 已开启 · 非安全区域") : "定位状态: 未开启");
        }
    }

    private void performRefreshCheckin() {
        Toast.makeText(getContext(), "正在重新打卡...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try { Thread.sleep(1500); } catch (InterruptedException e) { e.printStackTrace(); }
            if (getActivity() != null) getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "重新打卡成功！", Toast.LENGTH_SHORT).show());
        }).start();
    }

    private void startVoiceRecording() { Toast.makeText(getContext(), "开始录音...", Toast.LENGTH_SHORT).show(); }
    private void stopVoiceRecordingAndProcess() { Toast.makeText(getContext(), "停止录音，正在处理...", Toast.LENGTH_SHORT).show(); }
}
