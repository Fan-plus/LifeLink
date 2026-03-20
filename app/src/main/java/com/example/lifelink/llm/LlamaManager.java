package com.example.lifelink.llm;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

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
     * 核心接口：优化 OCR 识别出的文本
     */
    public void refineOcrText(String rawText, OnResultCallback callback) {
        if (!isInitialized || modelHandle == 0) {
            callback.onResult(rawText); // 未就绪则返回原图文字
            return;
        }

        new Thread(() -> {
            String prompt = "<|im_start|>system\n你是一个药品专家，请修正并精简以下识别有误的药品信息，只保留药名、功效和核心用法，去除乱码。\n<|im_end|>\n"
                    + "<|im_start|>user\n内容如下：\n" + rawText + "\n<|im_end|>\n<|im_start|>assistant\n";
            
            String refined = bridge.nativeInference(modelHandle, prompt);
            callback.onResult(refined != null ? refined.trim() : rawText);
        }).start();
    }

    /**
     * 生成个人回忆录：基于用户存储的所有文字和语音内容
     */
    public void generateMemoir(String allMemoriesText, OnResultCallback callback) {
        if (!isInitialized || modelHandle == 0) {
            callback.onResult("AI 正在深度思考中，请稍后再试...");
            return;
        }

        new Thread(() -> {
            String prompt = "<|im_start|>system\n你是一位充满智慧和慈爱的回忆录作家。请根据用户提供的这些零散的生活碎片、心情和记录，写一篇温情、优美、富有文学色彩的简短总结（约300字）。\n" +
                    "要求：语气要亲切、带有慰藉感，让老人感受到岁月的温柔和生命的价值。请给出一个诗意的标题。\n<|im_end|>\n"
                    + "<|im_start|>user\n生活片段记录：\n" + allMemoriesText + "\n<|im_end|>\n<|im_start|>assistant\n";
            
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
