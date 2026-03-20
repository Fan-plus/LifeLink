package com.example.lifelink.ui.home;

import android.Manifest;
import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
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

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private View voiceSearchButton; // 改为 View 以兼容 FAB 或 Button
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
    private View btnWarmCompanion; // 使用 View 以防 ID 缺失
    private View btnMyMemories;
    private View btnWillSafe;

    private MoneyPrinterApi qwenApi;
    // ⭐ 请在此处替换为您真实的阿里云 API Key
    private static final String QWEN_API_KEY = "Bearer sk-e9c20847634d42fe8ce27fa52997c13b";

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
        if (refreshCheckinButton != null) {
            refreshCheckinButton.setOnClickListener(v -> performRefreshCheckin());
        }

        if (voiceSearchButton != null) {
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
        }

        if (btnMedicineIdentify != null) btnMedicineIdentify.setOnClickListener(v -> navigateToFragment(1));
        if (btnAbnormalWarning != null) btnAbnormalWarning.setOnClickListener(v -> navigateToFragment(2));
        if (btnContactChildren != null) btnContactChildren.setOnClickListener(v -> navigateToFragment(3));
        if (btnWarmCompanion != null) btnWarmCompanion.setOnClickListener(v -> navigateToFragment(4));
        if (btnMyMemories != null) btnMyMemories.setOnClickListener(v -> navigateToFragment(5));
        if (btnWillSafe != null) btnWillSafe.setOnClickListener(v -> navigateToFragment(5));
    }

    private void setupReminderList() {
        if (medicineReminderRecycler == null) return;
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
        if (reminderDb == null || medicineReminderRecycler == null) return;
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
                    if (heartRateText != null) heartRateText.setText("72 次/分");
                    if (watchStatusText != null) watchStatusText.setText("● 手表已连接");
                    if (locationStatusText != null) locationStatusText.setText("安全区域内");
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
        if (voiceWaveLayout != null) voiceWaveLayout.setVisibility(View.VISIBLE);
        startWaveAnimation();

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext());
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {
                    if (voiceResultContainer != null) voiceResultContainer.setVisibility(View.VISIBLE);
                    if (voiceResultLabel != null) voiceResultLabel.setText("正在倾听...");
                    if (voiceResultText != null) voiceResultText.setText("");
                }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onError(int error) {
                    if (!isListening) return;
                    if (voiceResultLabel != null) voiceResultLabel.setText("识别结束");
                }
                @Override public void onResults(Bundle results) {
                    java.util.ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    String text = (matches != null && !matches.isEmpty()) ? matches.get(0) : "";
                    if (!text.isEmpty()) {
                        if (voiceResultLabel != null) voiceResultLabel.setText("您说：");
                        if (voiceResultText != null) voiceResultText.setText(text);
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
        if (voiceWaveLayout != null) voiceWaveLayout.setVisibility(View.GONE);
        stopWaveAnimation();
        
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }
    }

    private void startWaveAnimation() {
        waveAnimators = new Animator[5];
        View[] dots = new View[]{dot1, dot2, dot3, dot4, dot5};
        for (int i = 0; i < dots.length; i++) {
            if (dots[i] == null) continue;
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
            for (Animator a : waveAnimators) if (a != null) a.cancel();
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
            if (voiceResultLabel != null) voiceResultLabel.setText("云端 Qwen 正在思考...");
            if (voiceResultText != null) voiceResultText.setText("...");

            // ⭐ 调用通义千问云端 API
            List<ChatCompletionRequest.Message> messages = new ArrayList<>();
            messages.add(new ChatCompletionRequest.Message("user", originalText));
            ChatCompletionRequest request = new ChatCompletionRequest("qwen-plus", messages);
            
            qwenApi.chatCompletions(QWEN_API_KEY, request).enqueue(new Callback<ChatCompletionResponse>() {
                @Override
                public void onResponse(Call<ChatCompletionResponse> call, Response<ChatCompletionResponse> response) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (response.isSuccessful() && response.body() != null) {
                                if (voiceResultLabel != null) voiceResultLabel.setText("Qwen 的回复：");
                                if (voiceResultText != null) voiceResultText.setText(response.body().getFirstAnswer());
                            } else {
                                if (voiceResultLabel != null) voiceResultLabel.setText("API 响应异常");
                                if (voiceResultText != null) voiceResultText.setText("状态码：" + response.code());
                            }
                        });
                    }
                }

                @Override
                public void onFailure(Call<ChatCompletionResponse> call, Throwable t) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (voiceResultLabel != null) voiceResultLabel.setText("网络请求失败");
                            if (voiceResultText != null) voiceResultText.setText(t.getMessage());
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
            if (voiceResultLabel != null) voiceResultLabel.setText("识别结果：");
            if (voiceResultText != null) voiceResultText.setText(answer);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) speechRecognizer.destroy();
    }
}
