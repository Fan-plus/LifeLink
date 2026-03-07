package com.example.lifelink.ui.home;

import android.os.Bundle;
import android.Manifest;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.animation.ObjectAnimator;
import android.animation.Animator;
import android.animation.AnimatorSet;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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
    private com.google.android.material.button.MaterialButton voiceSearchButton;
    private LinearLayout voiceWaveLayout;
    private View dot1, dot2, dot3, dot4, dot5;
    private LinearLayout voiceResultContainer;
    private TextView voiceResultLabel;
    private TextView voiceResultText;
    private Animator[] waveAnimators;

    // TFLite
    private Interpreter tflite = null;
    private Map<String, Integer> charToIdx = new HashMap<>();
    private Map<String, String> labelMap = new HashMap<>();
    private int maxLength = 128;
    private int numClasses = 0;

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
        voiceWaveLayout = view.findViewById(R.id.voice_wave_layout);
        dot1 = view.findViewById(R.id.dot1);
        dot2 = view.findViewById(R.id.dot2);
        dot3 = view.findViewById(R.id.dot3);
        dot4 = view.findViewById(R.id.dot4);
        dot5 = view.findViewById(R.id.dot5);
        voiceResultContainer = view.findViewById(R.id.voice_result_container);
        voiceResultLabel = view.findViewById(R.id.voice_result_label);
        voiceResultText = view.findViewById(R.id.voice_result_text);

        // load tflite/model resources asynchronously
        new Thread(this::loadModelAndResources).start();

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

        // cancel button removed; result container has its own lifecycle

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

    // Speech recognition
    private static final int REQUEST_RECORD_AUDIO = 1001;
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private boolean isListening = false;

    private void startVoiceRecording() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            return;
        }
        startListeningInternal();
    }

    private void startListeningInternal() {
        // update UI: listening
        voiceSearchButton.setText("松开结束 · 正在倾听...");
        try { voiceSearchButton.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.purple_700)); } catch (Exception ignored) {}
        // show waveform
        if (voiceWaveLayout != null) voiceWaveLayout.setVisibility(View.VISIBLE);
        startWaveAnimation();

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext());
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {
                    requireActivity().runOnUiThread(() -> {
                        if (voiceResultContainer != null) {
                            voiceResultContainer.setVisibility(View.VISIBLE);
                            voiceResultLabel.setText("正在倾听...");
                            voiceResultText.setText("");
                        }
                        if (voiceWaveLayout != null) { voiceWaveLayout.setVisibility(View.VISIBLE); startWaveAnimation(); }
                    });
                }

                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float rmsdB) { }
                @Override public void onBufferReceived(byte[] buffer) { }
                @Override public void onEndOfSpeech() { }
                @Override public void onError(int error) {
                    requireActivity().runOnUiThread(() -> {
                        String msg = getErrorText(error);
                        if (voiceResultContainer != null) {
                            voiceResultContainer.setVisibility(View.VISIBLE);
                            voiceResultLabel.setText("识别出错");
                            voiceResultText.setText(String.format("%s (错误码: %d)", msg, error));
                        }
                        Log.w("HomeFragment", "SpeechRecognizer error: " + error + " msg: " + msg);
                        // ensure UI resets after short delay
                        voiceSearchButton.setEnabled(true);
                        if (voiceResultContainer != null) {
                            voiceResultContainer.postDelayed(() -> resetVoiceUI(), 2500);
                        } else {
                            resetVoiceUI();
                        }
                    });
                }

                @Override public void onResults(Bundle results) {
                    java.util.ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    String text = (matches != null && !matches.isEmpty()) ? matches.get(0) : "";
                    requireActivity().runOnUiThread(() -> {
                        if (text.isEmpty()) {
                            if (voiceResultContainer != null) {
                                voiceResultContainer.setVisibility(View.VISIBLE);
                                voiceResultLabel.setText("识别结果：");
                                voiceResultText.setText("未识别到语音，请重试");
                            }
                            voiceSearchButton.setEnabled(true);
                            if (voiceResultContainer != null) {
                                voiceResultContainer.postDelayed(() -> resetVoiceUI(), 2000);
                            } else {
                                resetVoiceUI();
                            }
                            return;
                        }
                        // 立即显示识别文本
                        if (voiceResultContainer != null) {
                            voiceResultContainer.setVisibility(View.VISIBLE);
                            voiceResultLabel.setText("识别结果：");
                            voiceResultText.setText(text);
                        }
                        // 允许再次按键（UI 反馈），同时异步触发模型推理以更新结果
                        voiceSearchButton.setEnabled(true);
                        new Thread(() -> processRecognizedText(text)).start();
                    });
                }

                @Override public void onPartialResults(Bundle partialResults) {
                    java.util.ArrayList<String> partial = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (partial != null && !partial.isEmpty()) {
                        final String p = partial.get(0);
                        requireActivity().runOnUiThread(() -> {
                            if (voiceResultContainer != null) {
                                voiceResultContainer.setVisibility(View.VISIBLE);
                                voiceResultLabel.setText("识别中（部分）:");
                                voiceResultText.setText(p);
                            }
                        });
                    }
                }
                @Override public void onEvent(int eventType, Bundle params) { }
            });
        }

        if (recognizerIntent == null) {
            recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            // prefer Chinese recognition; allow partial results and extend silence timeouts
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, requireContext().getPackageName());
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        }

        // start listening
        isListening = true;
        try { speechRecognizer.startListening(recognizerIntent); } catch (Exception e) { resetVoiceUI(); }
    }

    private void stopVoiceRecordingAndProcess() {
        // stop listening, hide waveform, wait for results
        if (isListening && speechRecognizer != null) {
            try { speechRecognizer.stopListening(); } catch (Exception ignored) {}
        }
        isListening = false;
        stopWaveAnimation();
        if (voiceWaveLayout != null) voiceWaveLayout.setVisibility(View.GONE);
        voiceSearchButton.setEnabled(false);
    }

    private void cancelRecognitionAndReset() {
        if (speechRecognizer != null) {
            try { speechRecognizer.cancel(); } catch (Exception ignored) {}
        }
        resetVoiceUI();
    }

    // ----- waveform animation -----
    private void startWaveAnimation() {
        try {
            waveAnimators = new Animator[5];
            View[] dots = new View[]{dot1, dot2, dot3, dot4, dot5};
            for (int i = 0; i < dots.length; i++) {
                ObjectAnimator a = ObjectAnimator.ofFloat(dots[i], "scaleY", 1f, 2f);
                a.setDuration(300 + i * 80);
                a.setRepeatMode(ObjectAnimator.REVERSE);
                a.setRepeatCount(ObjectAnimator.INFINITE);
                a.start();
                waveAnimators[i] = a;
            }
        } catch (Exception ignored) {}
    }

    private void stopWaveAnimation() {
        if (waveAnimators != null) {
            for (Animator a : waveAnimators) {
                try { a.cancel(); } catch (Exception ignored) {}
            }
        }
    }

    // ----- model loading and inference -----
    private void loadModelAndResources() {
        try {
            Log.d("HomeFragment", "Starting to load model and resources...");
            
            // load vocab.json
            InputStream is = requireContext().getAssets().open("vocab.json");
            java.util.Scanner s = new java.util.Scanner(is, "UTF-8").useDelimiter("\\A");
            String vocabJson = s.hasNext() ? s.next() : "";
            is.close();
            org.json.JSONObject vm = new org.json.JSONObject(vocabJson);
            // vocab.json maps idx->char
            for (int i = 0; ; i++) {
                String key = String.valueOf(i);
                if (!vm.has(key)) break;
                String ch = vm.getString(key);
                charToIdx.put(ch, i);
            }
            Log.d("HomeFragment", "Loaded vocab with " + charToIdx.size() + " characters");

            // load label_map.json
            InputStream is2 = requireContext().getAssets().open("label_map.json");
            java.util.Scanner s2 = new java.util.Scanner(is2, "UTF-8").useDelimiter("\\A");
            String labelJson = s2.hasNext() ? s2.next() : "";
            is2.close();
            org.json.JSONObject lm = new org.json.JSONObject(labelJson);
            java.util.Iterator<String> keys = lm.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                labelMap.put(k, lm.getString(k));
            }
            Log.d("HomeFragment", "Loaded label map with " + labelMap.size() + " labels");

            // load config - IMPORTANT: must succeed for numClasses to be set
            numClasses = 0;  // reset first
            try {
                InputStream is3 = requireContext().getAssets().open("config.json");
                java.util.Scanner s3 = new java.util.Scanner(is3, "UTF-8").useDelimiter("\\A");
                String cfg = s3.hasNext() ? s3.next() : "";
                is3.close();
                org.json.JSONObject cj = new org.json.JSONObject(cfg);
                maxLength = cj.optInt("max_length", 128);
                numClasses = cj.optInt("num_classes", 4);
                Log.d("HomeFragment", "Loaded config: maxLength=" + maxLength + ", numClasses=" + numClasses);
            } catch (Exception e) {
                Log.e("HomeFragment", "Failed to load config.json: " + e.getMessage());
                // fallback values
                maxLength = 128;
                numClasses = 4;
            }

            // load tflite model from assets
            try {
                MappedByteBuffer mb = loadModelFile("intent_classifier.tflite");
                if (mb != null) {
                    tflite = new Interpreter(mb);
                    Log.d("HomeFragment", "TFLite model loaded successfully");
                } else {
                    Log.e("HomeFragment", "Failed to load model file: ByteBuffer is null");
                }
            } catch (Exception e) {
                Log.e("HomeFragment", "TFLite load failed: " + e.getMessage(), e);
            }
        } catch (Exception e) {
            Log.e("HomeFragment", "Failed to load resources: " + e.getMessage(), e);
        }
    }

    private MappedByteBuffer loadModelFile(String filename) throws IOException {
        Log.d("HomeFragment", "Attempting to load model file: " + filename);
        try {
            AssetFileDescriptor fileDescriptor = requireContext().getAssets().openFd(filename);
            Log.d("HomeFragment", "Asset descriptor opened successfully");
            Log.d("HomeFragment", "Declared length: " + fileDescriptor.getDeclaredLength() + " bytes");
            
            FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            
            Log.d("HomeFragment", "Mapping file - offset: " + startOffset + ", length: " + declaredLength);
            MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
            
            if (buffer != null) {
                Log.d("HomeFragment", "MappedByteBuffer created successfully, capacity: " + buffer.capacity());
                return buffer;
            } else {
                Log.e("HomeFragment", "MappedByteBuffer is null");
                return null;
            }
        } catch (java.io.FileNotFoundException e) {
            Log.e("HomeFragment", "Model file not found in assets: " + filename);
            Log.e("HomeFragment", "Available assets:");
            try {
                String[] assets = requireContext().getAssets().list("");
                for (String asset : assets) {
                    Log.e("HomeFragment", "  - " + asset);
                }
            } catch (Exception ignored) {}
            throw e;
        } catch (Exception e) {
            Log.e("HomeFragment", "Error loading model file: " + e.getClass().getSimpleName() + " - " + e.getMessage(), e);
            throw new IOException("Failed to load " + filename, e);
        }
    }

    private void processRecognizedText(String text) {
        // show brief output animation (reuse wave area as simple indicator)
        requireActivity().runOnUiThread(() -> {
            if (voiceResultContainer != null) {
                voiceResultContainer.setVisibility(View.VISIBLE);
                voiceResultLabel.setText("识别中：");
                voiceResultText.setText(text);
            }
        });

        new Thread(() -> {
            String predictedLabel = "模型不可用";
            String answer = "抱歉，无法获得分类结果。";
            StringBuilder debugInfo = new StringBuilder();
            
            try {
                debugInfo.append("=== 语音识别结果 ===\n");
                debugInfo.append("原始文本: ").append(text).append("\n");
                debugInfo.append("文本长度: ").append(text.length()).append("\n\n");
                
                debugInfo.append("=== 模型状态检查 ===\n");
                debugInfo.append("TFLite加载: ").append(tflite != null ? "✓" : "✗").append("\n");
                debugInfo.append("maxLength: ").append(maxLength).append("\n");
                debugInfo.append("numClasses: ").append(numClasses).append("\n");
                debugInfo.append("charToIdx大小: ").append(charToIdx.size()).append("\n");
                debugInfo.append("labelMap大小: ").append(labelMap.size()).append("\n\n");
                
                if (tflite == null) {
                    answer = "✗ TFLite 模型未加载";
                    Log.e("HomeFragment", "TFLite interpreter is null");
                } else if (numClasses <= 0) {
                    answer = "✗ 分类数无效 (numClasses=" + numClasses + ")";
                    Log.e("HomeFragment", "Invalid numClasses: " + numClasses);
                } else if (charToIdx.isEmpty()) {
                    answer = "✗ 字符词表为空";
                    Log.e("HomeFragment", "charToIdx is empty");
                } else {
                    // prepare input
                    debugInfo.append("=== 字符编码过程 ===\n");
                    int[][] input = new int[1][maxLength];
                    for (int i = 0; i < maxLength; i++) input[0][i] = 0;
                    
                    StringBuilder charEncode = new StringBuilder();
                    for (int i = 0; i < Math.min(text.length(), maxLength); i++) {
                        String ch = String.valueOf(text.charAt(i));
                        Integer idx = charToIdx.get(ch);
                        int inputIdx = (idx == null) ? 0 : idx;
                        input[0][i] = inputIdx;
                        charEncode.append(ch).append("->").append(inputIdx).append(" ");
                        if (i % 5 == 4) charEncode.append("\n");
                    }
                    debugInfo.append(charEncode.toString()).append("\n\n");
                    
                    // run inference
                    debugInfo.append("=== 模型推理 ===\n");
                    float[][] output = new float[1][numClasses];
                    long startTime = System.currentTimeMillis();
                    tflite.run(input, output);
                    long inferenceTime = System.currentTimeMillis() - startTime;
                    debugInfo.append("推理耗时: ").append(inferenceTime).append("ms\n\n");
                    
                    // find best match
                    debugInfo.append("=== 分类分数 ===\n");
                    int best = 0;
                    float bestScore = -1f;
                    for (int i = 0; i < output[0].length; i++) {
                        String labelName = labelMap.getOrDefault(String.valueOf(i), "未知");
                        float score = output[0][i];
                        debugInfo.append(String.format("[%d] %s: %.4f\n", i, labelName, score));
                        if (score > bestScore) { best = i; bestScore = score; }
                    }
                    predictedLabel = labelMap.getOrDefault(String.valueOf(best), String.valueOf(best));
                    
                    debugInfo.append("\n=== 最优结果 ===\n");
                    debugInfo.append("分类: ").append(predictedLabel).append("\n");
                    debugInfo.append("置信度: ").append(String.format("%.4f", bestScore)).append("\n");
                    
                    Log.d("HomeFragment", debugInfo.toString());
                    
                    // canned answer by label
                    switch (predictedLabel) {
                        case "QUERY_OBJECT": answer = "✓ QUERY_OBJECT\n我正在为您查找物品位置..."; break;
                        case "PRIVACY_QUERY": answer = "✓ PRIVACY_QUERY\n此类问题涉及隐私，请谨慎处理。"; break;
                        case "HEALTH_STATUS": answer = "✓ HEALTH_STATUS\n正在查询健康状态信息..."; break;
                        case "MEDICINE_USAGE": answer = "✓ MEDICINE_USAGE\n这是药品使用建议，请遵医嘱。"; break;
                        default: answer = "✓ " + predictedLabel;
                    }
                }
            } catch (Exception e) {
                Log.e("HomeFragment", "Inference error: " + e.getMessage(), e);
                debugInfo.append("\n✗ 推理异常: ").append(e.getMessage()).append("\n");
                for (StackTraceElement el : e.getStackTrace()) {
                    debugInfo.append(el.toString()).append("\n");
                }
                Log.d("HomeFragment", debugInfo.toString());
                answer = "✗ 推理异常：" + e.getMessage();
            }

            final String pl = predictedLabel;
            final String pa = answer;
            final String dbg = debugInfo.toString();
            requireActivity().runOnUiThread(() -> {
                if (voiceResultContainer != null) {
                    voiceResultLabel.setText("识别: \"" + text + "\"");
                    voiceResultText.setText(pa + "\n\n--- 调试信息 ---\n" + dbg);
                }
                // hide button disable after showing result
                voiceSearchButton.setEnabled(true);
                // auto-hide result after 5s (给用户更多时间看日志)
                voiceResultContainer.postDelayed(() -> {
                    if (voiceResultContainer != null) voiceResultContainer.setVisibility(View.GONE);
                    resetVoiceUI();
                }, 5000);
            });
        }).start();
    }

    private String getErrorText(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO: return "音频录制错误";
            case SpeechRecognizer.ERROR_CLIENT: return "客户端错误";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "权限不足";
            case SpeechRecognizer.ERROR_NETWORK: return "网络错误";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "网络超时";
            case SpeechRecognizer.ERROR_NO_MATCH: return "未匹配到结果";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "识别服务正忙";
            case SpeechRecognizer.ERROR_SERVER: return "服务器错误";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "未检测到语音（超时）";
            default: return "未知错误";
        }
    }

    private void resetVoiceUI() {
        // restore initial button text and style
        voiceSearchButton.setText("按住说话 · 比如「我的老花镜在哪」「降压药怎么吃」");
        try { voiceSearchButton.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.eye_friendly_accent)); } catch (Exception ignored) {}
        voiceSearchButton.setEnabled(true);
        if (voiceWaveLayout != null) voiceWaveLayout.setVisibility(View.GONE);
        if (voiceResultContainer != null) voiceResultContainer.setVisibility(View.GONE);
        isListening = false;
    }

    // (no longer using legacy overlay)

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListeningInternal();
            } else {
                Toast.makeText(getContext(), "需要录音权限才能使用语音识别", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            try { speechRecognizer.destroy(); } catch (Exception ignored) {}
            speechRecognizer = null;
        }
    }
}
