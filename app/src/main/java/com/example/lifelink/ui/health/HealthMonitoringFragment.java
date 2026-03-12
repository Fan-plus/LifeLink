package com.example.lifelink.ui.health;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.lifelink.R;
import com.example.lifelink.api.ChatCompletionRequest;
import com.example.lifelink.api.ChatCompletionResponse;
import com.example.lifelink.api.MoneyPrinterApi;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class HealthMonitoringFragment extends Fragment {

    private TextView stepCountText;
    private ProgressBar stepProgressBar;
    private TextView hrValueText;
    private TextView gasValueText;
    private TextView bpValueText;
    private TextView spo2ValueText;
    private LineChart healthChart;
    private ChipGroup trendChipGroup;
    private ExtendedFloatingActionButton fabSos;
    
    private MoneyPrinterApi qwenApi;
    private static final String QWEN_API_KEY = "Bearer sk-e9c20847634d42fe8ce27fa52997c13b";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private boolean isSimulating = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_health_monitoring, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupQwenApi();
        initializeViews(view);
        setupChart();
        setupListeners();
        startDataSimulation();
    }

    private void setupQwenApi() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        qwenApi = retrofit.create(MoneyPrinterApi.class);
    }

    private void initializeViews(View view) {
        stepCountText = view.findViewById(R.id.step_count_value);
        stepProgressBar = view.findViewById(R.id.step_progress);
        hrValueText = view.findViewById(R.id.hr_value);
        gasValueText = view.findViewById(R.id.gas_value);
        bpValueText = view.findViewById(R.id.bp_value);
        spo2ValueText = view.findViewById(R.id.spo2_value);
        healthChart = view.findViewById(R.id.health_line_chart);
        trendChipGroup = view.findViewById(R.id.trend_chip_group);
        fabSos = view.findViewById(R.id.fab_sos);
        
        view.findViewById(R.id.btn_ai_report).setOnClickListener(v -> generateAiHealthReport());
    }

    private void setupChart() {
        if (healthChart == null) return;
        healthChart.getDescription().setEnabled(false);
        healthChart.getLegend().setEnabled(false);
        healthChart.getAxisRight().setEnabled(false);
        
        XAxis xAxis = healthChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(0xFF64748B);

        YAxis leftAxis = healthChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(0xFFF1F5F9);
        leftAxis.setTextColor(0xFF64748B);

        // 默认显示心率
        updateChartData("心率", 70, 20, 0xFFE11D48, 0xFFFFF1F2, 60, 120);
    }

    private void setupListeners() {
        // ⭐ 修正点击监听：明确处理每一个 Chip 的点击
        trendChipGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chip_hr) {
                updateChartData("心率", 70, 15, 0xFFE11D48, 0xFFFFF1F2, 60, 120);
            } else if (checkedId == R.id.chip_bp) {
                updateChartData("血压", 110, 20, 0xFF7C3AED, 0xFFF5F3FF, 80, 160);
            } else if (checkedId == R.id.chip_spo2) {
                updateChartData("血氧", 96, 3, 0xFF059669, 0xFFECFDF5, 90, 100);
            }
        });

        if (fabSos != null) {
            fabSos.setOnClickListener(v -> 
                Toast.makeText(getContext(), "紧急求救信号已发送至预设联系人！", Toast.LENGTH_LONG).show());
        }
    }

    private void updateChartData(String label, int base, int range, int color, int fillColor, float min, float max) {
        if (healthChart == null) return;

        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            entries.add(new Entry(i, base + random.nextInt(range)));
        }

        LineDataSet dataSet = new LineDataSet(entries, label);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setColor(color);
        dataSet.setLineWidth(3f);
        dataSet.setDrawCircles(true);
        dataSet.setCircleColor(color);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawValues(false);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(fillColor);
        dataSet.setFillAlpha(150);

        healthChart.getAxisLeft().setAxisMinimum(min);
        healthChart.getAxisLeft().setAxisMaximum(max);
        
        LineData lineData = new LineData(dataSet);
        healthChart.setData(lineData);
        healthChart.animateX(500); // 缩短动画时间，响应更快
        healthChart.invalidate();
    }

    private void generateAiHealthReport() {
        String steps = stepCountText.getText().toString();
        String hr = hrValueText.getText().toString();
        String bp = bpValueText.getText().toString();
        String spo2 = spo2ValueText.getText().toString();

        String prompt = String.format("你是健康助手。分析数据：步数%s，心率%s，血压%s，血氧%s。请给老人一句温馨建议（30字内）。",
                steps, hr, bp, spo2);

        Toast.makeText(getContext(), "AI 正在分析您的趋势...", Toast.LENGTH_SHORT).show();

        ChatCompletionRequest request = new ChatCompletionRequest("qwen-plus", prompt);
        qwenApi.chatCompletions(QWEN_API_KEY, request).enqueue(new Callback<ChatCompletionResponse>() {
            @Override
            public void onResponse(Call<ChatCompletionResponse> call, Response<ChatCompletionResponse> response) {
                if (getActivity() != null && response.isSuccessful() && response.body() != null) {
                    new MaterialAlertDialogBuilder(getContext())
                            .setTitle("✨ AI 健康建议")
                            .setMessage(response.body().getFirstAnswer())
                            .setPositiveButton("收到", null)
                            .show();
                }
            }
            @Override
            public void onFailure(Call<ChatCompletionResponse> call, Throwable t) {
                Toast.makeText(getContext(), "分析失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startDataSimulation() {
        isSimulating = true;
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!isSimulating || getContext() == null) return;

                int currentSteps = 8432 + random.nextInt(10);
                stepCountText.setText(String.format(Locale.getDefault(), "%,d", currentSteps));
                stepProgressBar.setProgress(currentSteps / 100);

                int hr = 70 + random.nextInt(8);
                hrValueText.setText(String.valueOf(hr));

                float gas = 0.01f + random.nextFloat() * 0.01f;
                gasValueText.setText(String.format(Locale.getDefault(), "%.2f", gas));

                handler.postDelayed(this, 5000);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isSimulating = false;
        handler.removeCallbacksAndMessages(null);
    }
}
