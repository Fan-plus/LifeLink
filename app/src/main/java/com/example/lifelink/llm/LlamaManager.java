package com.example.lifelink.llm;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LlamaManager {
    private static final String TAG = "LlamaManager";
    private static final String MODEL_NAME = "qwen.gguf";
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("\\{[\\s\\S]*}");
    private static final Pattern OBJECT_QUERY_PATTERN = Pattern.compile(
            "(?:\\u6211\\u7684|\\u6211\\u5BB6|\\u6211\\u90A3\\u4E2A|\\u90A3\\u4E2A|\\u8FD9\\u4E2A|\\u8FD9\\u53EA|\\u8FD9\\u4EF6|\\u90A3\\u53EA|\\u90A3\\u4EF6|\\u8FD9\\u628A|\\u90A3\\u628A)?([\\u4e00-\\u9fa5A-Za-z0-9]{1,12})(?:\\u5728\\u54EA|\\u5728\\u54EA\\u91CC|\\u5728\\u54EA\\u513F|\\u53BB\\u54EA\\u4E86|\\u653E\\u54EA\\u4E86|\\u653E\\u54EA\\u91CC\\u4E86|\\u627E\\u4E0D\\u5230\\u4E86|\\u5728\\u4EC0\\u4E48\\u5730\\u65B9)");
    private static final Pattern OBJECT_LOCATION_PATTERN = Pattern.compile(
            "(?:\\u6211\\u7684|\\u6211\\u628A|\\u628A|\\u5C06|\\u90A3\\u4E2A|\\u8FD9\\u4E2A|\\u8FD9\\u53EA|\\u8FD9\\u4EF6|\\u90A3\\u53EA|\\u90A3\\u4EF6)?([\\u4e00-\\u9fa5A-Za-z0-9]{1,12})(?:\\u653E\\u5728|\\u653E\\u5230\\u4E86|\\u653E\\u5230|\\u843D\\u5728|\\u7559\\u5728|\\u6401\\u5728|\\u6446\\u5728|\\u662F\\u5728|\\u5728)([^\\uFF0C\\u3002,.!?\\uFF1B;]{1,20})");
    private static final Pattern HEALTH_PATTERN = Pattern.compile(
            "(\\u5FC3\\u7387|\\u5FC3\\u8DF3|\\u8840\\u538B|\\u8840\\u7CD6|\\u8840\\u6C27|\\u6B65\\u6570|\\u7761\\u7720|\\u4F53\\u6E29)");

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
                    Log.d(TAG, "Deploying local GGUF model...");
                    copyAssetToStorage(context, MODEL_NAME, modelFile);
                }

                Log.d(TAG, "Initializing local Llama engine: " + modelFile.getAbsolutePath());
                modelHandle = bridge.nativeInit(modelFile.getAbsolutePath());

                if (modelHandle != 0) {
                    isInitialized = true;
                    Log.d(TAG, "Local LLM engine initialized");
                } else {
                    Log.e(TAG, "Failed to initialize local model");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize model", e);
            }
        }).start();
    }

    public void parseReminderSchema(String text, OnResultCallback callback) {
        if (!isInitialized || modelHandle == 0) {
            callback.onResult(null);
            return;
        }

        new Thread(() -> {
            String prompt = "<|im_start|>system\n"
                    + "Extract reminder information from a short Chinese utterance.\n"
                    + "Return exactly one JSON object and no extra text.\n"
                    + "{\"intent\":\"reminder\",\"time_type\":\"relative|absolute\",\"time_value\":\"\",\"event\":\"\"}\n"
                    + "relative examples: 10m, 2h, 30s\n"
                    + "absolute examples: 08:20, tomorrow 09:00\n"
                    + "<|im_end|>\n"
                    + "<|im_start|>user\n"
                    + text + "\n"
                    + "<|im_end|>\n"
                    + "<|im_start|>assistant\n";

            String result = bridge.nativeInference(modelHandle, prompt);
            callback.onResult(result != null ? result.trim() : null);
        }).start();
    }

    public void extractSubject(String text, String type, OnResultCallback callback) {
        extractStructuredInfo(text, type, result -> {
            if (result == null) {
                callback.onResult(null);
                return;
            }

            if ("HEALTH".equals(type)) {
                callback.onResult(result.metric);
            } else {
                callback.onResult(result.subject);
            }
        });
    }

    public void extractStructuredInfo(String text, String type, OnExtractionCallback callback) {
        if (!isInitialized || modelHandle == 0) {
            callback.onResult(fallbackExtraction(text, type, null));
            return;
        }

        new Thread(() -> {
            String prompt = buildExtractionPrompt(text, type);
            String rawResult = bridge.nativeInference(modelHandle, prompt);
            callback.onResult(parseExtractionResult(text, type, rawResult));
        }).start();
    }

    public void refineOcrText(String rawText, OnResultCallback callback) {
        if (!isInitialized || modelHandle == 0) {
            callback.onResult(rawText);
            return;
        }

        new Thread(() -> {
            String prompt = "<|im_start|>system\n"
                    + "You clean OCR text from medicine packaging.\n"
                    + "Fix obvious OCR errors and rewrite the result in concise Chinese.\n"
                    + "<|im_end|>\n"
                    + "<|im_start|>user\n"
                    + rawText + "\n"
                    + "<|im_end|>\n"
                    + "<|im_start|>assistant\n";

            String refined = bridge.nativeInference(modelHandle, prompt);
            callback.onResult(refined != null ? refined.trim() : rawText);
        }).start();
    }

    public void generateMemoir(String allMemoriesText, OnResultCallback callback) {
        if (!isInitialized || modelHandle == 0) {
            callback.onResult("AI is thinking, please try again soon.");
            return;
        }

        new Thread(() -> {
            String prompt = "<|im_start|>system\n"
                    + "Write a warm Chinese memoir from fragmented life notes.\n"
                    + "Use a title on the first line, then the body.\n"
                    + "<|im_end|>\n"
                    + "<|im_start|>user\n"
                    + allMemoriesText + "\n"
                    + "<|im_end|>\n"
                    + "<|im_start|>assistant\n";

            String result = bridge.nativeInference(modelHandle, prompt);
            callback.onResult(result != null ? result.trim() : "Those memories are waiting to be written.");
        }).start();
    }

    public interface OnResultCallback {
        void onResult(String text);
    }

    public interface OnExtractionCallback {
        void onResult(ExtractionResult result);
    }

    public static class ExtractionResult {
        public final String type;
        public final String rawText;
        public final String subject;
        public final String location;
        public final String metric;
        public final String category;

        public ExtractionResult(String type, String rawText, String subject, String location, String metric, String category) {
            this.type = type;
            this.rawText = rawText;
            this.subject = subject;
            this.location = location;
            this.metric = metric;
            this.category = category;
        }
    }

    private String buildExtractionPrompt(String text, String type) {
        String taskLine;
        if ("OBJECT".equals(type)) {
            taskLine = "Task: extract the object the user wants to find.";
        } else if ("HEALTH".equals(type)) {
            taskLine = "Task: extract the health metric the user asks about.";
        } else {
            taskLine = "Task: extract the object being remembered and its location.";
        }

        return "<|im_start|>system\n"
                + "You are an information extraction engine for short Chinese utterances.\n"
                + taskLine + "\n"
                + "Return exactly one JSON object and no extra text.\n"
                + "JSON schema:\n"
                + "{\"subject\":\"\",\"location\":\"\",\"metric\":\"\",\"category\":\"\"}\n"
                + "Rules:\n"
                + "1. Keep values short.\n"
                + "2. Remove filler words and pronouns.\n"
                + "3. Use category object, object_location, or health_metric.\n"
                + "4. Leave missing fields as empty strings.\n"
                + "<|im_end|>\n"
                + "<|im_start|>user\n"
                + "type=" + type + "\n"
                + "text=" + text + "\n"
                + "<|im_end|>\n"
                + "<|im_start|>assistant\n";
    }

    private ExtractionResult parseExtractionResult(String inputText, String type, String rawResult) {
        String raw = rawResult != null ? rawResult.trim() : "";
        String subject = "";
        String location = "";
        String metric = "";
        String category = "";

        try {
            Matcher matcher = JSON_BLOCK_PATTERN.matcher(raw);
            if (matcher.find()) {
                JSONObject json = new JSONObject(matcher.group());
                subject = sanitizeSubject(json.optString("subject"));
                location = sanitizeLocation(json.optString("location"));
                metric = normalizeMetric(json.optString("metric"));
                category = sanitizeCategory(json.optString("category"), type);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse extraction JSON: " + raw, e);
        }

        return fallbackExtraction(inputText, type, new ExtractionResult(type, raw, subject, location, metric, category));
    }

    private ExtractionResult fallbackExtraction(String inputText, String type, ExtractionResult parsed) {
        String subject = parsed != null ? parsed.subject : "";
        String location = parsed != null ? parsed.location : "";
        String metric = parsed != null ? parsed.metric : "";
        String category = parsed != null ? parsed.category : "";
        String raw = parsed != null ? parsed.rawText : "";

        if ("HEALTH".equals(type)) {
            if (metric.isEmpty()) {
                metric = extractMetricByRule(inputText);
            }
            category = "health_metric";
        } else if ("OBJECT_LOCATION".equals(type)) {
            if (subject.isEmpty() || location.isEmpty()) {
                Matcher matcher = OBJECT_LOCATION_PATTERN.matcher(normalizeInput(inputText));
                if (matcher.find()) {
                    if (subject.isEmpty()) {
                        subject = sanitizeSubject(matcher.group(1));
                    }
                    if (location.isEmpty()) {
                        location = sanitizeLocation(matcher.group(2));
                    }
                }
            }
            category = "object_location";
        } else {
            if (subject.isEmpty()) {
                Matcher matcher = OBJECT_QUERY_PATTERN.matcher(normalizeInput(inputText));
                if (matcher.find()) {
                    subject = sanitizeSubject(matcher.group(1));
                }
            }
            category = "object";
        }

        if (subject.isEmpty() && !"HEALTH".equals(type)) {
            subject = sanitizeSubject(inputText);
        }

        return new ExtractionResult(type, raw, subject, location, metric, category);
    }

    private String extractMetricByRule(String text) {
        Matcher matcher = HEALTH_PATTERN.matcher(normalizeInput(text));
        if (matcher.find()) {
            return normalizeMetric(matcher.group(1));
        }
        return "";
    }

    private String sanitizeSubject(String text) {
        if (text == null) {
            return "";
        }

        String cleaned = normalizeInput(text)
                .replaceAll("[\"'{}\\[\\]]", "")
                .replaceAll("^(\\u6211\\u7684|\\u6211\\u5BB6|\\u6211\\u628A|\\u6211\\u60F3\\u627E|\\u5E2E\\u6211\\u627E|\\u8BF7\\u5E2E\\u6211\\u627E|\\u90A3\\u4E2A|\\u8FD9\\u4E2A|\\u8FD9\\u53EA|\\u90A3\\u53EA|\\u8FD9\\u4EF6|\\u90A3\\u4EF6|\\u8FD9\\u628A|\\u90A3\\u628A)", "")
                .replaceAll("(\\u5728\\u54EA|\\u5728\\u54EA\\u91CC|\\u5728\\u54EA\\u513F|\\u53BB\\u54EA\\u4E86|\\u653E\\u54EA\\u4E86|\\u653E\\u54EA\\u91CC\\u4E86|\\u5728\\u4EC0\\u4E48\\u5730\\u65B9|\\u5462|\\u5440|\\u554A)$", "")
                .replaceAll("^(\\u662F|\\u53EB\\u505A)", "")
                .trim();

        if (cleaned.contains(" ")) {
            cleaned = cleaned.split("\\s+")[0];
        }
        if (cleaned.length() > 12) {
            cleaned = cleaned.substring(0, 12);
        }
        return cleaned;
    }

    private String sanitizeLocation(String text) {
        if (text == null) {
            return "";
        }

        String cleaned = normalizeInput(text)
                .replaceAll("[\"'{}\\[\\]]", "")
                .replaceAll("^(\\u5728|\\u653E\\u5728|\\u653E\\u5230\\u4E86|\\u653E\\u5230|\\u6401\\u5728|\\u6446\\u5728)", "")
                .replaceAll("(\\u91CC\\u9762|\\u91CC\\u8FB9)$", "\u91CC")
                .replaceAll("(\\u90A3\\u91CC|\\u8FD9\\u8FB9)$", "")
                .trim();

        if (cleaned.length() > 16) {
            cleaned = cleaned.substring(0, 16);
        }
        return cleaned;
    }

    private String normalizeMetric(String text) {
        String cleaned = normalizeInput(text);
        if (cleaned.contains("\u5FC3\u7387") || cleaned.contains("\u5FC3\u8DF3")) return "\u5FC3\u7387";
        if (cleaned.contains("\u8840\u538B")) return "\u8840\u538B";
        if (cleaned.contains("\u8840\u7CD6")) return "\u8840\u7CD6";
        if (cleaned.contains("\u8840\u6C27")) return "\u8840\u6C27";
        if (cleaned.contains("\u6B65\u6570")) return "\u6B65\u6570";
        if (cleaned.contains("\u7761\u7720")) return "\u7761\u7720";
        if (cleaned.contains("\u4F53\u6E29")) return "\u4F53\u6E29";
        return cleaned;
    }

    private String sanitizeCategory(String category, String type) {
        String cleaned = normalizeInput(category).toLowerCase(Locale.ROOT);
        if ("object".equals(cleaned) || "object_location".equals(cleaned) || "health_metric".equals(cleaned)) {
            return cleaned;
        }
        if ("HEALTH".equals(type)) return "health_metric";
        if ("OBJECT_LOCATION".equals(type)) return "object_location";
        return "object";
    }

    private String normalizeInput(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\n', ' ')
                .replace('\r', ' ')
                .replace('“', '"')
                .replace('”', '"')
                .replace('：', ':')
                .replace('，', ',')
                .trim();
    }

    private void copyAssetToStorage(Context context, String assetName, File outFile) throws Exception {
        try (InputStream is = context.getAssets().open(assetName);
             FileOutputStream os = new FileOutputStream(outFile)) {
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
            os.flush();
        }
    }
}
