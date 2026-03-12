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
import com.example.lifelink.api.ChatCompletionRequest;
import com.example.lifelink.api.ChatCompletionResponse;
import com.example.lifelink.api.MoneyPrinterApi;
import com.example.lifelink.data.reminder.ReminderDbHelper;
import com.example.lifelink.data.reminder.ReminderItem;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class HomeFragment extends Fragment {

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

    private Interpreter tflite = null;
    private Map<String, Integer> charToIdx = new HashMap<>();
    private Map<String, String> labelMap = new HashMap<>();
    private int maxLength = 128;
    private int numClasses = 0;

    private RecyclerView medicineReminderRecycler;
    private ReminderAdapter reminderAdapter;
    private ReminderDbHelper reminderDb;

    private LinearLayout btnMedicineIdentify;
    private LinearLayout btnAbnormalWarning;
    private LinearLayout btnContactChildren;
    private LinearLayout btnWarmCompanion;
    private LinearLayout btnMyMemories;
    private LinearLayout btnWillSafe;

    private MoneyPrinterApi qwenApi;
    // ⭐ 请在此处替换为您真实的阿里云 API Key
    private static final String QWEN_API_KEY = "sk-e9c20847634d42fe8ce27fa52997c13b";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        initializeViews(view);
        setupQwenApi();
        setClickListeners();
        loadHealthData();
        reminderDb = new ReminderDbHelper(getContext());
        setupReminderList();
        loadReminders();
        return view;
    }

    private void setupQwenApi() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        qwenApi = retrofit.create(MoneyPrinterApi.class);
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
                loadReminders();
            }
        });
        medicineReminderRecycler.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        medicineReminderRecycler.setAdapter(reminderAdapter);
    }

    private void loadReminders() {
        if (reminderDb == null) return;
        List<ReminderItem> list = reminderDb.getAllReminders();
        if (list != null && !list.isEmpty()) {
            reminderAdapter.setData(list);
            medicineReminderRecycler.setVisibility(View.VISIBLE);
        } else {
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
        new Thread(() -> {
            try { Thread.sleep(1000); } catch (InterruptedException e) { e.printStackTrace(); }
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    heartRateText.setText("当前心率: 72 次/分");
                    watchStatusText.setText("佩戴状态: 已佩戴 OPPO 手表");
                    locationStatusText.setText("定位状态: 已开启 · 安全区域");
                });
            }
        }).start();
    }

    private void performRefreshCheckin() {
        Toast.makeText(getContext(), "正在重新打卡...", Toast.LENGTH_SHORT).show();
    }

    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private boolean isListening = false;

    private void startVoiceRecording() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.RECORD_AUDIO}, 1001);
            return;
        }
        
        isListening = true;
        voiceSearchButton.setText("正在倾听 · 松开识别");
        voiceSearchButton.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.purple_700));
        voiceWaveLayout.setVisibility(View.VISIBLE);
        startWaveAnimation();

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext());
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {
                    voiceResultContainer.setVisibility(View.VISIBLE);
                    voiceResultLabel.setText("正在倾听...");
                    voiceResultText.setText("");
                }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onError(int error) {
                    if (!isListening) return;
                    voiceResultLabel.setText("识别结束");
                    voiceSearchButton.setEnabled(true);
                }
                @Override public void onResults(Bundle results) {
                    java.util.ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    String text = (matches != null && !matches.isEmpty()) ? matches.get(0) : "";
                    if (!text.isEmpty()) {
                        voiceResultLabel.setText("您说：");
                        voiceResultText.setText(text);
                        new Thread(() -> processRecognizedText(text)).start();
                    }
                }
                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle params) {}
            });
        }

        if (recognizerIntent == null) {
            recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        }
        speechRecognizer.startListening(recognizerIntent);
    }

    private void stopVoiceRecordingAndProcess() {
        isListening = false;
        voiceSearchButton.setText("按住说话");
        voiceSearchButton.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.eye_friendly_accent));
        voiceWaveLayout.setVisibility(View.GONE);
        stopWaveAnimation();
        
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }
    }

    private void startWaveAnimation() {
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
    }

    private void stopWaveAnimation() {
        if (waveAnimators != null) {
            for (Animator a : waveAnimators) a.cancel();
        }
    }

    private void loadModelAndResources() {
        try {
            InputStream is = requireContext().getAssets().open("vocab.json");
            String vocabJson = new java.util.Scanner(is, "UTF-8").useDelimiter("\\A").next();
            is.close();
            org.json.JSONObject vm = new org.json.JSONObject(vocabJson);
            for (int i = 0; ; i++) {
                if (!vm.has(String.valueOf(i))) break;
                charToIdx.put(vm.getString(String.valueOf(i)), i);
            }

            InputStream is2 = requireContext().getAssets().open("label_map.json");
            String labelJson = new java.util.Scanner(is2, "UTF-8").useDelimiter("\\A").next();
            is2.close();
            org.json.JSONObject lm = new org.json.JSONObject(labelJson);
            java.util.Iterator<String> keys = lm.keys();
            while (keys.hasNext()) { String k = keys.next(); labelMap.put(k, lm.getString(k)); }

            numClasses = 5;
            AssetFileDescriptor fd = requireContext().getAssets().openFd("intent_classifier.tflite");
            FileInputStream fis = new FileInputStream(fd.getFileDescriptor());
            tflite = new Interpreter(fis.getChannel().map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength()));
        } catch (Exception e) { Log.e("HomeFragment", "Load resources error", e); }
    }

    private void processRecognizedText(String text) {
        try {
            if (tflite == null) return;
            int[][] input = new int[1][maxLength];
            for (int i = 0; i < Math.min(text.length(), maxLength); i++) {
                input[0][i] = charToIdx.getOrDefault(String.valueOf(text.charAt(i)), 0);
            }
            float[][] output = new float[1][numClasses];
            tflite.run(input, output);
            int best = 0; float maxScore = -1f;
            for (int i = 0; i < output[0].length; i++) {
                if (output[0][i] > maxScore) { maxScore = output[0][i]; best = i; }
            }
            String predictedLabel = labelMap.getOrDefault(String.valueOf(best), "未知");
            
            requireActivity().runOnUiThread(() -> handleIntentResult(predictedLabel, text));
        } catch (Exception e) { Log.e("HomeFragment", "Inference error", e); }
    }

    private void handleIntentResult(String label, String originalText) {
        if ("ai_chat".equals(label)) {
            voiceResultLabel.setText("云端 Qwen 正在思考...");
            voiceResultText.setText("...");

            // ⭐ 调用通义千问云端 API
            ChatCompletionRequest request = new ChatCompletionRequest("qwen-plus", originalText);
            
            qwenApi.chatCompletions(QWEN_API_KEY, request).enqueue(new Callback<ChatCompletionResponse>() {
                @Override
                public void onResponse(Call<ChatCompletionResponse> call, Response<ChatCompletionResponse> response) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (response.isSuccessful() && response.body() != null) {
                                voiceResultLabel.setText("Qwen 的回复：");
                                voiceResultText.setText(response.body().getFirstAnswer());
                            } else {
                                voiceResultLabel.setText("API 响应异常");
                                voiceResultText.setText("状态码：" + response.code());
                            }
                        });
                    }
                }

                @Override
                public void onFailure(Call<ChatCompletionResponse> call, Throwable t) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            voiceResultLabel.setText("网络请求失败");
                            voiceResultText.setText(t.getMessage());
                        });
                    }
                }
            });
        } else {
            String answer;
            switch (label) {
                case "QUERY_OBJECT": answer = "我正在为您查找物品位置..."; break;
                case "PRIVACY_QUERY": answer = "此类问题涉及隐私，请谨慎处理。"; break;
                case "HEALTH_STATUS": answer = "正在查询您的健康状态信息..."; break;
                case "MEDICINE_USAGE": answer = "这是药品使用建议，请遵循医嘱。"; break;
                default: answer = "我听到了：" + originalText; break;
            }
            voiceResultLabel.setText("识别结果：");
            voiceResultText.setText(answer);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) speechRecognizer.destroy();
    }
}
