package com.example.lifelink.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

/**
 * 简化版 OCR 识别器
 * 使用 Google ML Kit 进行本地 OCR 识别
 */
public class SimpleOcrRecognizer {
    private static final String TAG = "SimpleOcrRecognizer";
    private static SimpleOcrRecognizer instance;
    private TextRecognizer textRecognizer;
    private Context context;
    private boolean isInitialized = false;

    private SimpleOcrRecognizer(Context context) {
        this.context = context;
    }

    /**
     * 获取单例
     */
    public static synchronized SimpleOcrRecognizer getInstance(Context context) {
        if (instance == null) {
            instance = new SimpleOcrRecognizer(context);
        }
        return instance;
    }

    /**
     * 初始化 OCR 识别器（可安全多次调用）
     */
    public synchronized void ensureInitialized() {
        if (isInitialized) {
            return;
        }

        try {
            // 使用中文文字识别器
            ChineseTextRecognizerOptions options = new ChineseTextRecognizerOptions.Builder()
                    .build();
            textRecognizer = TextRecognition.getClient(options);
            isInitialized = true;
            Log.d(TAG, "Google ML Kit OCR 引擎初始化成功");
        } catch (Exception e) {
            Log.e(TAG, "OCR 初始化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 同步识别（会阻塞线程，需在后台线程调用）
     */
    public String recognizeTextSync(Bitmap bitmap) throws Exception {
        ensureInitialized();

        if (bitmap == null) {
            throw new IllegalArgumentException("Bitmap 不能为空");
        }

        InputImage image = InputImage.fromBitmap(bitmap, 0);
        Text result = textRecognizer.process(image).getResult();

        return extractTextFromResult(result);
    }

    /**
     * 异步识别（推荐使用）
     */
    public void recognizeTextAsync(Bitmap bitmap, OcrCallback callback) {
        ensureInitialized();

        if (bitmap == null) {
            if (callback != null) {
                callback.onError("图片不能为空");
            }
            return;
        }

        InputImage image = InputImage.fromBitmap(bitmap, 0);

        textRecognizer.process(image)
                .addOnSuccessListener(new OnSuccessListener<Text>() {
                    @Override
                    public void onSuccess(Text result) {
                        String extractedText = extractTextFromResult(result);
                        if (callback != null) {
                            callback.onSuccess(extractedText);
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Log.e(TAG, "OCR 识别失败", e);
                        if (callback != null) {
                            callback.onError("识别失败: " + e.getMessage());
                        }
                    }
                });
    }

    /**
     * 从 ML Kit 结果中提取文本
     */
    private String extractTextFromResult(Text result) {
        if (result == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (Text.TextBlock block : result.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                sb.append(line.getText()).append("\n");
            }
        }
        return sb.toString().trim();
    }

    /**
     * 释放资源
     */
    public synchronized void release() {
        if (textRecognizer != null) {
            textRecognizer.close();
            textRecognizer = null;
        }
        isInitialized = false;
    }

    /**
     * 识别回调
     */
    public interface OcrCallback {
        void onSuccess(String text);
        void onError(String errorMessage);
    }
}