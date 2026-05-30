package com.example.lifelink.ui.warm;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.lifelink.R;
import com.example.lifelink.api.ChatCompletionRequest;
import com.example.lifelink.api.ChatCompletionResponse;
import com.example.lifelink.api.MoneyPrinterApi;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * 暖心陪伴语音对谈页面
 * 采用“智能轮替”模式：AI 说话时关闭监听，彻底解决回声干扰问题。
 */
public class VoiceChatActivity extends AppCompatActivity {

    private static final String TAG = "VoiceChatActivity";
    private static final int PERMISSION_RECORD_AUDIO = 1;
    public static final String EXTRA_INITIAL_PROMPT = "extra_initial_prompt";
    public static final String EXTRA_INITIAL_SPEECH = "extra_initial_speech";

    private TextView tvStatus, tvSubtitle;
    private FloatingActionButton btnEndChat, btnMute;
    private android.view.View viewRipple;

    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private MoneyPrinterApi api;
    
    // 请替换为您真实的 API KEY
    private static final String API_KEY = "Bearer sk-e9c20847634d42fe8ce27fa52997c13b";
    private static final String BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/"; 

    private boolean isAiSpeaking = false;
    private List<ChatCompletionRequest.Message> messageHistory = new ArrayList<>();
    private String initialPrompt;
    private String initialSpeech;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_chat);

        initialPrompt = getIntent().getStringExtra(EXTRA_INITIAL_PROMPT);
        initialSpeech = getIntent().getStringExtra(EXTRA_INITIAL_SPEECH);

        initViews();
        initSpeech();
        initRetrofit();
        initHistory();
        checkPermissions();
    }

    private void initViews() {
        tvStatus = findViewById(R.id.tv_chat_status);
        tvSubtitle = findViewById(R.id.tv_subtitle);
        btnEndChat = findViewById(R.id.btn_end_chat);
        btnMute = findViewById(R.id.btn_mute);
        viewRipple = findViewById(R.id.view_ripple);

        btnEndChat.setOnClickListener(v -> finish());
        
        viewRipple.setOnClickListener(v -> {
            if (isAiSpeaking) {
                stopAiSpeaking();
                startListening();
            }
        });

        AlphaAnimation anim = new AlphaAnimation(0.2f, 0.6f);
        anim.setDuration(1200);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setRepeatCount(Animation.INFINITE);
        viewRipple.startAnimation(anim);
    }

    private void initRetrofit() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        api = retrofit.create(MoneyPrinterApi.class);
    }

    private void initHistory() {
        messageHistory.add(new ChatCompletionRequest.Message("system", "你是一个温暖的健康守护助手，名叫豆豆，专门陪伴老年人。请用简短、亲切、易懂的中文回答。"));
    }

    private void initSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.CHINESE);
                if (initialPrompt != null) {
                    sendToAi(initialPrompt);
                } else {
                    speak(initialSpeech != null ? initialSpeech : "爷爷奶奶好，我是豆豆，很高兴见到您！");
                }
            }
        });

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) { 
                if (!isAiSpeaking) tvStatus.setText("请说话，我在听..."); 
            }
            
            @Override
            public void onBeginningOfSpeech() { 
                if (!isAiSpeaking) tvSubtitle.setText(""); 
            }

            @Override
            public void onRmsChanged(float rmsdB) {
                if (!isAiSpeaking) {
                    float scale = 1.0f + (rmsdB / 15f);
                    viewRipple.setScaleX(Math.min(scale, 1.4f));
                    viewRipple.setScaleY(Math.min(scale, 1.4f));
                }
            }

            @Override public void onEndOfSpeech() { if (!isAiSpeaking) tvStatus.setText("正在思考..."); }

            @Override
            public void onError(int error) {
                if (!isAiSpeaking && !isFinishing()) {
                    startListening(); 
                }
            }

            @Override
            public void onResults(Bundle results) {
                if (isAiSpeaking) return; 

                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String userText = matches.get(0);
                    tvSubtitle.setText(userText);
                    sendToAi(userText);
                } else {
                    startListening();
                }
            }

            @Override public void onPartialResults(Bundle partialResults) {
                if (isAiSpeaking) return;
                ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) tvSubtitle.setText(matches.get(0));
            }

            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void stopAiSpeaking() {
        if (textToSpeech != null) textToSpeech.stop();
        isAiSpeaking = false;
        runOnUiThread(() -> {
            tvStatus.setText("我在听，您请说...");
            viewRipple.setScaleX(1.0f);
            viewRipple.setScaleY(1.0f);
        });
    }

    private void startListening() {
        if (isAiSpeaking || isFinishing()) return;
        runOnUiThread(() -> {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            speechRecognizer.startListening(intent);
        });
    }

    private void sendToAi(String text) {
        messageHistory.add(new ChatCompletionRequest.Message("user", text));
        if (messageHistory.size() > 15) messageHistory.subList(1, 3).clear();

        ChatCompletionRequest request = new ChatCompletionRequest("qwen-turbo", new ArrayList<>(messageHistory));
        
        api.chatCompletions(API_KEY, request).enqueue(new Callback<ChatCompletionResponse>() {
            @Override
            public void onResponse(Call<ChatCompletionResponse> call, Response<ChatCompletionResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String aiAnswer = response.body().getFirstAnswer();
                    messageHistory.add(new ChatCompletionRequest.Message("assistant", aiAnswer));
                    speak(aiAnswer);
                } else {
                    startListening();
                }
            }
            @Override public void onFailure(Call<ChatCompletionResponse> call, Throwable t) { startListening(); }
        });
    }

    private void speak(String text) {
        if (isFinishing()) return;
        
        if (speechRecognizer != null) speechRecognizer.stopListening();
        
        isAiSpeaking = true;
        runOnUiThread(() -> {
            tvStatus.setText("豆豆正在说话...");
            tvSubtitle.setText(text);
        });

        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "AI_VOICE");
        
        new Thread(() -> {
            try {
                Thread.sleep(500);
                while (textToSpeech.isSpeaking() && !isFinishing()) {
                    Thread.sleep(200);
                }
                if (isAiSpeaking) {
                    isAiSpeaking = false;
                    runOnUiThread(this::startListening);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    protected void onPause() { super.onPause(); stopAiSpeaking(); }

    @Override
    protected void onDestroy() {
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (textToSpeech != null) { textToSpeech.stop(); textToSpeech.shutdown(); }
        super.onDestroy();
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_RECORD_AUDIO);
        } else {
            startListeningIfNoOpeningMessage();
        }
    }

    private void startListeningIfNoOpeningMessage() {
        if (initialPrompt == null && initialSpeech == null) {
            startListening();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_RECORD_AUDIO && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListeningIfNoOpeningMessage();
        } else {
            finish();
        }
    }
}
