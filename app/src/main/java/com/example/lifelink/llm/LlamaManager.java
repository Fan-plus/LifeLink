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
    public void refineOcrText(String rawText, OnRefineCallback callback) {
        if (!isInitialized || modelHandle == 0) {
            callback.onResult(rawText); // 未就绪则返回原图文字
            return;
        }

        new Thread(() -> {
            // 构建纠错提示词
            String prompt = "<|im_start|>system\n你是一个药品专家，请修正并精简以下识别有误的药品信息，只保留药名、功效和核心用法，去除乱码。\n<|im_end|>\n"
                    + "<|im_start|>user\n内容如下：\n" + rawText + "\n<|im_end|>\n<|im_start|>assistant\n";
            
            String refined = bridge.nativeInference(modelHandle, prompt);
            callback.onResult(refined != null ? refined.trim() : rawText);
        }).start();
    }

    public interface OnRefineCallback {
        void onResult(String refinedText);
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
