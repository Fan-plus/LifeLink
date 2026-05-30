package com.example.lifelink.llm;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LlamaManager {
    private static final String TAG = "LlamaManager";
    private static final String MODEL_NAME = "qwen.gguf";
    private static LlamaManager instance;
    private final LlamaBridge bridge;
    private long modelHandle = 0;
    private boolean isInitialized = false;

    private LlamaManager(Context context) {
        bridge = new LlamaBridge();
        initAsync(context.getApplicationContext());
    }

    public static synchronized LlamaManager getInstance(Context context) {
        if (instance == null) {
            instance = new LlamaManager(context);
        }
        return instance;
    }

    private void initAsync(Context context) {
        new Thread(() -> {
            try {
                File modelFile = new File(context.getFilesDir(), MODEL_NAME);
                if (!modelFile.exists()) {
                    Log.d(TAG, "正在部署 GGUF 模型...");
                    copyAssetToStorage(context, MODEL_NAME, modelFile);
                }

                Log.d(TAG, "正在初始化 Llama 引擎，路径: " + modelFile.getAbsolutePath());
                modelHandle = bridge.nativeInit(modelFile.getAbsolutePath());
                
                if (modelHandle != 0) {
                    isInitialized = true;
                    Log.d(TAG, "✅ 本地 LLM 引擎初始化成功");
                } else {
                    Log.e(TAG, "❌ 模型加载失败，句柄为 0");
                }
            } catch (Exception e) {
                Log.e(TAG, "初始化异常: " + e.getMessage());
            }
        }).start();
    }

    /**
     * 极简解析提醒：采用统一 Schema 格式
     */
    // AI辅助生成：DeepSeek-V3，网页端，2026-03-15；人工补充JSON字段约束并重写异常处理
    public void parseReminderSchema(String text, OnResultCallback callback) {
        if (!isInitialized || modelHandle == 0) {
            callback.onResult(null);
            return;
        }

        new Thread(() -> {
            String prompt = "<|im_start|>system\n" +
                    "你是一个时间提醒解析助手。请分析用户的输入，提取意图、时间类型、时间值和事件内容。\n" +
                    "必须返回以下 JSON 格式：\n" +
                    "{\n" +
                    " \"intent\": \"reminder\",\n" +
                    " \"time_type\": \"relative\" | \"absolute\",\n" +
                    " \"time_value\": \"\",\n" +
                    " \"event\": \"\"\n" +
                    "}\n" +
                    "规则：\n" +
                    "1. relative (相对时间): time_value 格式为 数字+单位(m代表分钟, h代表小时, s代表秒)。例如: 10m, 2h。\n" +
                    "2. absolute (绝对时间): time_value 格式为 HH:mm 或 tomorrow HH:mm。例如: 08:20, tomorrow 09:00。\n" +
                    "3. event: 提取具体的事件内容。\n" +
                    "示例：\n" +
                    "- “十分钟后提醒我吃药” -> {\"intent\":\"reminder\", \"time_type\":\"relative\", \"time_value\":\"10m\", \"event\":\"吃药\"}\n" +
                    "- “8:20提醒我吃药” -> {\"intent\":\"reminder\", \"time_type\":\"absolute\", \"time_value\":\"08:20\", \"event\":\"吃药\"}\n" +
                    "<|im_end|>\n" +
                    "<|im_start|>user\n用户说：\"" + text + "\"\n<|im_end|>\n<|im_start|>assistant\n";
            
            String result = bridge.nativeInference(modelHandle, prompt);
            callback.onResult(result != null ? result.trim() : null);
        }).start();
    }

    /**
     * 提取主语（用于寻物或特定健康指标查询）
     */
    // AI辅助生成：DeepSeek-V3，网页端，2026-03-16；人工扩展为寻物、健康、记忆条目三类提取
    public void extractSubject(String text, String type, OnResultCallback callback) {
        if (!isInitialized || modelHandle == 0) {
            callback.onResult(null);
            return;
        }

        new Thread(() -> {
            String systemPrompt = "";
            if ("OBJECT".equals(type)) {
                systemPrompt = "你是一个寻物助手。提取用户想要寻找的物品名称。只返回物品名称，不要其他文字。例如：输入“我的眼镜在哪”，返回“眼镜”。";
            } else if ("HEALTH".equals(type)) {
                systemPrompt = "你是一个健康助手。提取用户想要查询的健康指标名称（如：心率、血压、血氧、步数）。只返回指标名称，不要其他文字。";
            } else if ("OBJECT_LOCATION".equals(type)) {
                systemPrompt = "你是一个记忆存储助手。提取用户描述中的核心物品名称。只返回物品名称，不要其他文字。例如：输入“我的电脑在桌子上”，返回“电脑”；输入“备用钥匙在门口鞋柜里”，返回“备用钥匙”。";
            }

            String prompt = "<|im_start|>system\n" + systemPrompt + "<|im_end|>\n" +
                    "<|im_start|>user\n用户说：\"" + text + "\"\n<|im_end|>\n<|im_start|>assistant\n";
            
            String result = bridge.nativeInference(modelHandle, prompt);
            callback.onResult(result != null ? result.trim() : null);
        }).start();
    }

    // AI辅助生成：通义千问Qwen-Max，网页端，2026-03-23；人工压缩提示词并适配药盒OCR纠错场景
    public void refineOcrText(String rawText, OnResultCallback callback) {
        if (!isInitialized || modelHandle == 0) {
            callback.onResult(rawText);
            return;
        }
        new Thread(() -> {
            String prompt = "<|im_start|>system\n你是一个药品专家，请修正并精简以下识别有误的药品信息。\n<|im_end|>\n"
                    + "<|im_start|>user\n内容如下：\n" + rawText + "\n<|im_end|>\n<|im_start|>assistant\n";
            String refined = bridge.nativeInference(modelHandle, prompt);
            callback.onResult(refined != null ? refined.trim() : rawText);
        }).start();
    }

    // AI辅助生成：DeepSeek-V3，网页端，2026-03-27；人工调整文风、标题拆分和输出长度
    public void generateMemoir(String allMemoriesText, OnResultCallback callback) {
        if (!isInitialized || modelHandle == 0) {
            callback.onResult("AI 正在深度思考中，请稍后再试...");
            return;
        }
        new Thread(() -> {
            String prompt = "<|im_start|>system\n你是一位回忆录作家。\n<|im_end|>\n"
                    + "<|im_start|>user\n内容：\n" + allMemoriesText + "\n<|im_end|>\n<|im_start|>assistant\n";
            String result = bridge.nativeInference(modelHandle, prompt);
            callback.onResult(result != null ? result.trim() : "回忆是时光留下的最美礼物。");
        }).start();
    }

    public interface OnResultCallback {
        void onResult(String text);
    }

    private void copyAssetToStorage(Context context, String assetName, File outFile) throws Exception {
        try (InputStream is = context.getAssets().open(assetName);
             FileOutputStream os = new FileOutputStream(outFile)) {
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = is.read(buffer)) != -1) { os.write(buffer, 0, read); }
            os.flush();
        }
    }
}
