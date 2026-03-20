package com.example.lifelink.guard;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.lifelink.api.ChatCompletionRequest;
import com.example.lifelink.api.ChatCompletionResponse;
import com.example.lifelink.api.MoneyPrinterApi;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class AntiDeceptionManager {
    private static final String TAG = "AntiDeception";
    private static AntiDeceptionManager instance;
    private final TfliteDeceptionClassifier tfliteClassifier;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 云端配置 (使用通义千问 Qwen API)
    private static final String BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/";
    private static final String API_KEY = "Bearer sk-e9c20847634d42fe8ce27fa52997c13b"; 
    private final MoneyPrinterApi cloudApi;

    private AntiDeceptionManager(Context context) {
        // 1. 初始化本地端侧分类器
        tfliteClassifier = new TfliteDeceptionClassifier(context);

        // 2. 初始化云端 API 客户端
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        cloudApi = retrofit.create(MoneyPrinterApi.class);
    }

    public static synchronized AntiDeceptionManager getInstance(Context context) {
        if (instance == null) instance = new AntiDeceptionManager(context);
        return instance;
    }

    /**
     * 核心分析逻辑：
     * 1. 关键词拦截 -> 直接 DANGER
     * 2. 本地 TFLite 研判 -> 如果是 DANGER 或 SAFE，直接返回结果，不再发起云端请求
     * 3. 只有当本地判定为 SUSPECT (不确定/可疑) 时，才通过云端 Qwen 进行最终定性
     */
    public void analyzeSpeech(String speechText, RiskCallback callback) {
        if (speechText == null || speechText.trim().isEmpty()) return;
        Log.d(TAG, "🔍 开始多重风险研判: " + speechText);

        // --- 第一层：关键词硬拦截 (最快响应) ---
        if (speechText.contains("验证码") || speechText.contains("监管账户") || speechText.contains("转账")) {
            Log.w(TAG, "🚩 命中高危关键词黑名单");
            mainHandler.post(() -> callback.onResult(RiskLevel.DANGER, "【核心预警】检测到敏感转账指令！"));
            return;
        }

        // --- 第二层：本地 TFLite 端侧研判 (低延迟) ---
        RiskLevel localLevel = tfliteClassifier.predict(speechText);
        Log.i(TAG, "🤖 本地端侧研判结果: " + localLevel);

        if (localLevel == RiskLevel.DANGER) {
            // 本地已经确认是高风险，直接报警
            mainHandler.post(() -> callback.onResult(RiskLevel.DANGER, "【AI哨兵】检测到欺诈语义特征！"));
            return;
        } else if (localLevel == RiskLevel.SAFE) {
            // 本地确认安全，直接返回，节省云端开销
            mainHandler.post(() -> callback.onResult(RiskLevel.SAFE, "AI 守护中"));
            return;
        }

        // --- 第三层：云端 Qwen 深度研判 (仅当本地结果为 SUSPECT 时触发) ---
        if (localLevel == RiskLevel.SUSPECT && speechText.length() > 1) {
            analyzeByCloud(speechText, localLevel, callback);
        } else {
            // 长度太短不足以发起云端分析时，维持本地的 SUSPECT 提示
            mainHandler.post(() -> callback.onResult(RiskLevel.SUSPECT, "【本地提醒】当前通话存在疑点"));
        }
    }

    private void analyzeByCloud(String text, RiskLevel localHint, RiskCallback callback) {
        List<ChatCompletionRequest.Message> messages = new ArrayList<>();
        messages.add(new ChatCompletionRequest.Message("system", 
            "你是一个反诈骗分析专家。请分析用户的通话内容，判断是否有诈骗风险。 " +
            "要求：如果确定是诈骗，回复 DANGER；如果是疑似诈骗（如诱导、索要信息），回复 SUSPECT；如果是安全的日常对话，回复 SAFE。" +
            "注意：只返回一个单词，不要解释。"));
        messages.add(new ChatCompletionRequest.Message("user", "通话内容为：\"" + text + "\""));

        ChatCompletionRequest request = new ChatCompletionRequest("qwen-turbo", messages);

        Log.d(TAG, "☁️ 正在发起云端 Qwen 专家系统研判 (因本地结果为 SUSPECT)... ");
        cloudApi.chatCompletions(API_KEY, request).enqueue(new Callback<ChatCompletionResponse>() {
            @Override
            public void onResponse(Call<ChatCompletionResponse> call, Response<ChatCompletionResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String answer = response.body().getFirstAnswer().toUpperCase();
                    Log.i(TAG, "✅ 云端 Qwen 研判结论: " + answer);

                    RiskLevel cloudLevel = RiskLevel.SAFE;
                    String message = "AI 守护中";

                    if (answer.contains("DANGER")) {
                        cloudLevel = RiskLevel.DANGER;
                        message = "【专家系统预警】识别到极高欺诈风险！";
                    } else if (answer.contains("SUSPECT")) {
                        cloudLevel = RiskLevel.SUSPECT;
                        message = "【云端提醒】当前对话存在可疑诱导，请警惕";
                    }

                    // 以云端研判结论为准
                    final RiskLevel finalLevel = cloudLevel;
                    final String finalMsg = message;

                    mainHandler.post(() -> callback.onResult(finalLevel, finalMsg));
                }
            }

            @Override
            public void onFailure(Call<ChatCompletionResponse> call, Throwable t) {
                Log.e(TAG, "❌ 云端请求失败: " + t.getMessage());
                // 云端请求失败时，降级回到本地判断结果
                mainHandler.post(() -> callback.onResult(localHint, "【端侧模式】当前通话存在疑点"));
            }
        });
    }

    public enum RiskLevel { SAFE, SUSPECT, DANGER }

    public interface RiskCallback {
        void onResult(RiskLevel level, String message);
    }
}
