package com.example.lifelink.llm;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                    "- “一分钟后提醒我吃药” -> {\"intent\":\"reminder\", \"time_type\":\"relative\", \"time_value\":\"1m\", \"event\":\"吃药\"}\n" +
                    "- “十分钟后提醒我吃药” -> {\"intent\":\"reminder\", \"time_type\":\"relative\", \"time_value\":\"10m\", \"event\":\"吃药\"}\n" +
                    "- “半小时后提醒我量血压” -> {\"intent\":\"reminder\", \"time_type\":\"relative\", \"time_value\":\"30m\", \"event\":\"量血压\"}\n" +
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
        new Thread(() -> {
            String subject = extractSubjectByRules(text, type);
            if (!subject.isEmpty()) {
                callback.onResult(subject);
                return;
            }

            if (!isInitialized || modelHandle == 0) {
                callback.onResult(null);
                return;
            }

            String prompt = "<|im_start|>system\n" + buildSubjectPrompt(type) + "<|im_end|>\n" +
                    "<|im_start|>user\n用户说：\"" + text + "\"\n<|im_end|>\n<|im_start|>assistant\n";
            
            String result = bridge.nativeInference(modelHandle, prompt);
            subject = cleanSubjectResult(result, type);
            callback.onResult(subject.isEmpty() ? null : subject);
        }).start();
    }

    private String buildSubjectPrompt(String type) {
        String commonRules = "你只做短语抽取，不回答问题。\n"
                + "输出要求：只返回一个最核心的名词短语；不要加解释、标点、引号、前缀或换行；无法判断时返回空字符串。\n"
                + "不要返回“我、我的、帮我、请问、在哪里、在哪、查询、寻找”等功能词。\n";

        if ("OBJECT".equals(type)) {
            return commonRules
                    + "任务：从用户寻物请求中提取要找的物品名。\n"
                    + "例子：\n"
                    + "我的眼镜在哪 -> 眼镜\n"
                    + "帮我找一下备用钥匙 -> 备用钥匙\n"
                    + "我把手机放哪了 -> 手机\n"
                    + "遥控器是不是在客厅 -> 遥控器";
        } else if ("HEALTH".equals(type)) {
            return commonRules
                    + "任务：从健康查询中提取指标，只能返回：心率、血压、血氧、步数。\n"
                    + "同义词映射：脉搏=心率，氧饱和度=血氧，走了多少步=步数。\n"
                    + "例子：\n"
                    + "我的血压怎么样 -> 血压\n"
                    + "今天走了多少步 -> 步数\n"
                    + "查一下氧饱和度 -> 血氧\n"
                    + "脉搏是多少 -> 心率";
        } else if ("OBJECT_LOCATION".equals(type)) {
            return commonRules
                    + "任务：从用户保存位置的描述中提取被存放的核心物品名。\n"
                    + "例子：\n"
                    + "我的电脑在桌子上 -> 电脑\n"
                    + "备用钥匙在门口鞋柜里 -> 备用钥匙\n"
                    + "我把医保卡放在床头柜抽屉 -> 医保卡\n"
                    + "老花镜收在电视柜第二层 -> 老花镜";
        }
        return commonRules;
    }

    private String extractSubjectByRules(String text, String type) {
        if (text == null) return "";
        String input = text.trim();
        if (input.isEmpty()) return "";

        if ("HEALTH".equals(type)) {
            return normalizeHealthSubject(input);
        }

        String subject = "";
        if ("OBJECT_LOCATION".equals(type)) {
            subject = firstRegexGroup(input,
                    "(?:我把|把|将)?(.{1,20}?)(?:放在|放到|放进|搁在|搁到|收在|藏在|存在|放|在)");
        } else if ("OBJECT".equals(type)) {
            subject = firstRegexGroup(input,
                    "(?:找一下|找找|帮我找|帮忙找|寻找|查找|找|看看)(.{1,20}?)(?:在哪|在哪里|放哪|放在哪里|位置|$)");
            if (subject.isEmpty()) {
                subject = firstRegexGroup(input, "(.{1,20}?)(?:在哪|在哪里|放哪了|放哪里了|放在什么地方|位置)");
            }
            if (subject.isEmpty()) {
                subject = firstRegexGroup(input, "(.{1,20}?)(?:是不是在|在不在|是否在|还在)");
            }
        }

        return cleanObjectPhrase(subject);
    }

    private String normalizeHealthSubject(String text) {
        if (text.contains("血压") || text.contains("高压") || text.contains("低压")) return "血压";
        if (text.contains("血氧") || text.contains("氧饱和") || text.contains("氧气饱和")) return "血氧";
        if (text.contains("心率") || text.contains("脉搏") || text.contains("心跳")) return "心率";
        if (text.contains("步数") || text.contains("走了多少步") || text.contains("多少步") || text.contains("步")) return "步数";
        return "";
    }

    private String cleanSubjectResult(String result, String type) {
        if (result == null) return "";
        String value = result
                .replace("<|im_end|>", "")
                .replace("<|im_start|>", "")
                .replace("assistant", "")
                .trim();

        int newline = value.indexOf('\n');
        if (newline >= 0) value = value.substring(0, newline).trim();
        value = value.replaceAll("^(答案|结果|物品名称|物品|指标名称|指标|主语)[:：]\\s*", "");

        if ("HEALTH".equals(type)) {
            return normalizeHealthSubject(value);
        }
        return cleanObjectPhrase(value);
    }

    private String firstRegexGroup(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private String cleanObjectPhrase(String value) {
        if (value == null) return "";
        String cleaned = value.trim()
                .replaceAll("^[\"“”'‘’《》\\s]+|[\"“”'‘’《》。！？!?，,、；;：:\\s]+$", "")
                .replaceAll("^(我想|我要|我需要|我把|把|将|请|麻烦|帮我|帮忙|给我|替我|找一下|找找|寻找|查找|找|看看|一下)", "")
                .replaceAll("^(我的|我那|那个|这个|这台|那台|这部|那部|这张|那张|这把|那把|这串|那串|一个|一只|一副|一张|一把|一串)", "")
                .replaceAll("(在哪|在哪里|放哪了|放哪里了|放在哪里|位置|呢|啊|呀|吗)$", "")
                .trim();
        if (cleaned.length() > 12) return "";
        if (cleaned.matches(".*(什么|哪里|怎么|帮我|查询|寻找|位置).*")) return "";
        return cleaned;
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
