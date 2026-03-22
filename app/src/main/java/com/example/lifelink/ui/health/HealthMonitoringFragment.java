package com.example.lifelink.ui.health;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.lifelink.R;
import com.example.lifelink.api.ChatCompletionRequest;
import com.example.lifelink.api.ChatCompletionResponse;
import com.example.lifelink.api.MoneyPrinterApi;
import com.example.lifelink.data.health.HealthData;
import com.example.lifelink.data.health.HealthDbHelper;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class HealthMonitoringFragment extends Fragment implements TextToSpeech.OnInitListener {

    private TextView stepCountText, hrValueText, gasValueText, bpValueText, spo2ValueText;
    private ProgressBar stepProgressBar;
    private LineChart healthChart;
    private ChipGroup trendChipGroup;
    private ExtendedFloatingActionButton fabSos;
    private View btnScan;
    private View btnAiReport;
    private View aiReportResultCard;
    private TextView aiReportContent;
    private MaterialButton btnTtsPlay;
    
    private HealthDbHelper dbHelper;
    private MoneyPrinterApi qwenApi;
    private static final String QWEN_API_KEY = "Bearer sk-e9c20847634d42fe8ce27fa52997c13b";

    private TextToSpeech tts;
    private boolean isTtsInitialized = false;
    private boolean isSpeaking = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    
    private int currentSteps = 0;
    private int currentHr = 0;
    private String currentBp = "--/--";
    private int currentSpo2 = 0;
    private float currentGas = 0.0f;

    private final ActivityResultLauncher<Intent> scanLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String scanData = result.getData().getStringExtra("SCAN_RESULT");
                    processScanResult(scanData);
                }
            }
    );

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_health_monitoring, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        dbHelper = new HealthDbHelper(requireContext());
        tts = new TextToSpeech(requireContext(), this, "com.google.android.tts");
        setupQwenApi();
        initializeViews(view);
        loadAndEnsureDataSync(); // 关键修改：加载并同步
        setupChart();
        setupListeners();
    }

    private void loadAndEnsureDataSync() {
        new Thread(() -> {
            List<HealthData> samples = dbHelper.getLatestSamples(1);
            if (samples.isEmpty()) {
                Log.d("HealthFragment", "📊 数据库为空，正在初始化模拟数据...");
                long now = System.currentTimeMillis();
                // 插入 5 条模拟数据
                for (int i = 0; i < 5; i++) {
                    dbHelper.addSample(now - (long) i * 3600000, 
                        72 + random.nextInt(10), 120 + random.nextInt(10), 80 + random.nextInt(5), 
                        98, 0.02f, 5000 + random.nextInt(3000));
                }
                // 重新获取最新一条
                samples = dbHelper.getLatestSamples(1);
                // 💡 通知首页：数据已生产
                if (getContext() != null) {
                    getContext().sendBroadcast(new Intent("com.example.lifelink.REFRESH_HEALTH_DATA"));
                }
            }

            if (!samples.isEmpty()) {
                HealthData latest = samples.get(0);
                currentHr = latest.heartRate;
                currentBp = latest.bpSys + "/" + latest.bpDia;
                currentSpo2 = latest.spo2;
                currentSteps = latest.steps;
                currentGas = latest.gasLevel;
                
                if (getActivity() != null) {
                    getActivity().runOnUiThread(this::updateDisplayUI);
                }
            }
        }).start();
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.CHINESE);
            isTtsInitialized = true;
        }
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
        btnScan = view.findViewById(R.id.btn_scan_qr);
        btnAiReport = view.findViewById(R.id.btn_ai_report);
        aiReportResultCard = view.findViewById(R.id.ai_report_result_card);
        aiReportContent = view.findViewById(R.id.ai_report_content);
        btnTtsPlay = view.findViewById(R.id.btn_tts_play);
    }

    private void setupListeners() {
        btnScan.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), BarcodeScanActivity.class);
            scanLauncher.launch(intent);
        });

        trendChipGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chip_hr) {
                updateChartData("心率", 70, 15, 0xFFE11D48, 0xFFFFF1F2, 60, 120);
            } else if (checkedId == R.id.chip_bp) {
                updateChartData("血压", 110, 20, 0xFF7C3AED, 0xFFF5F3FF, 80, 160);
            } else if (checkedId == R.id.chip_spo2) {
                updateChartData("血氧", 96, 3, 0xFF059669, 0xFFECFDF5, 90, 100);
            }
        });

        btnAiReport.setOnClickListener(v -> generateAiHealthReport());
        fabSos.setOnClickListener(v -> Toast.makeText(getContext(), "紧急求助已发送！", Toast.LENGTH_SHORT).show());
        btnTtsPlay.setOnClickListener(v -> toggleTts());
    }

    private void toggleTts() {
        if (!isTtsInitialized) return;
        if (isSpeaking) stopTts();
        else startTts();
    }

    private void startTts() {
        String text = aiReportContent.getText().toString();
        if (text.isEmpty() || text.contains("正在加载")) return;
        int result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "HealthReportTTS");
        if (result == TextToSpeech.SUCCESS) {
            isSpeaking = true;
            btnTtsPlay.setIconResource(android.R.drawable.ic_media_pause);
            new Thread(() -> {
                while (isSpeaking && tts != null && tts.isSpeaking()) {
                    try { Thread.sleep(500); } catch (InterruptedException e) {}
                }
                if (getActivity() != null) getActivity().runOnUiThread(this::stopTts);
            }).start();
        }
    }

    private void stopTts() {
        if (tts != null) tts.stop();
        isSpeaking = false;
        if (btnTtsPlay != null) btnTtsPlay.setIconResource(android.R.drawable.ic_lock_silent_mode_off);
    }

    private void processScanResult(String data) {
        try {
            JSONObject json = new JSONObject(data);
            currentHr = json.optInt("hr", currentHr);
            int bps = json.optInt("bps", 120);
            int bpd = json.optInt("bpd", 80);
            currentBp = bps + "/" + bpd;
            currentSpo2 = json.optInt("spo2", currentSpo2);
            currentGas = (float) json.optDouble("gas", currentGas);
            currentSteps = json.optInt("steps", currentSteps);

            dbHelper.addSample(System.currentTimeMillis(), currentHr, bps, bpd, currentSpo2, currentGas, currentSteps);
            
            updateDisplayUI();
            updateChartData("心率", 70, 15, 0xFFE11D48, 0xFFFFF1F2, 60, 120);
            Toast.makeText(getContext(), "数据同步成功！", Toast.LENGTH_SHORT).show();
            
            if (getContext() != null) {
                getContext().sendBroadcast(new Intent("com.example.lifelink.REFRESH_HEALTH_DATA"));
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), "格式错误", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateDisplayUI() {
        if (stepCountText == null) return;
        stepCountText.setText(String.format(Locale.getDefault(), "%,d", currentSteps));
        stepProgressBar.setProgress(Math.min(100, currentSteps / 100));
        hrValueText.setText(String.valueOf(currentHr));
        bpValueText.setText(currentBp);
        spo2ValueText.setText(currentSpo2 + "");
        gasValueText.setText(String.format(Locale.getDefault(), "%.2f", currentGas));
    }

    private void setupChart() {
        if (healthChart == null) return;
        healthChart.getDescription().setEnabled(false);
        healthChart.getLegend().setEnabled(false);
        healthChart.getAxisRight().setEnabled(false);
        XAxis xAxis = healthChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        updateChartData("心率", 70, 20, 0xFFE11D48, 0xFFFFF1F2, 60, 120);
    }

    private void updateChartData(String label, int base, int range, int color, int fillColor, float min, float max) {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < 7; i++) entries.add(new Entry(i, base + random.nextInt(range)));
        LineDataSet ds = new LineDataSet(entries, label);
        ds.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        ds.setColor(color);
        ds.setDrawFilled(true);
        ds.setFillColor(fillColor);
        ds.setDrawValues(false);
        healthChart.getAxisLeft().setAxisMinimum(min);
        healthChart.getAxisLeft().setAxisMaximum(max);
        healthChart.setData(new LineData(ds));
        healthChart.invalidate();
    }

    private void generateAiHealthReport() {
        stopTts();
        List<HealthData> samples = dbHelper.getLatestSamples(10);
        StringBuilder historyData = new StringBuilder();
        if (samples.isEmpty()) {
            historyData.append(String.format("当前实时数据：步数 %d，心率 %d，血压 %s，血氧 %d%%，环境煤气 %.2f%%。",
                    currentSteps, currentHr, currentBp, currentSpo2, currentGas));
        } else {
            historyData.append("最近 10 次监测记录如下：\n");
            for (HealthData data : samples) {
                historyData.append(String.format("- 步数:%d, 心率:%d, 血压:%d/%d, 血氧:%d, 煤气:%.2f\n",
                        data.steps, data.heartRate, data.bpSys, data.bpDia, data.spo2, data.gasLevel));
            }
        }

        String prompt = "你是一位专业的家庭医生。请根据以下健康数据给出简短建议：\n" + historyData.toString();
        aiReportResultCard.setVisibility(View.VISIBLE);
        aiReportContent.setText("✨ AI 正在分析数据...");
        btnAiReport.setEnabled(false);

        List<ChatCompletionRequest.Message> messages = new ArrayList<>();
        messages.add(new ChatCompletionRequest.Message("user", prompt));

        qwenApi.chatCompletions(QWEN_API_KEY, new ChatCompletionRequest("qwen-plus", messages)).enqueue(new Callback<ChatCompletionResponse>() {
            @Override
            public void onResponse(Call<ChatCompletionResponse> call, Response<ChatCompletionResponse> response) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    btnAiReport.setEnabled(true);
                    if (response.isSuccessful() && response.body() != null) {
                        aiReportContent.setText(response.body().getFirstAnswer());
                    } else {
                        aiReportContent.setText("分析失败。");
                    }
                });
            }
            @Override
            public void onFailure(Call<ChatCompletionResponse> call, Throwable t) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    btnAiReport.setEnabled(true);
                    aiReportContent.setText("连接 AI 失败。");
                });
            }
        });
    }

    @Override
    public void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
