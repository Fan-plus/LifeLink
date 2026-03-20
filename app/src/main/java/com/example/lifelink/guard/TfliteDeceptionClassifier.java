package com.example.lifelink.guard;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;
import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.metadata.MetadataExtractor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class TfliteDeceptionClassifier {
    private static final String TAG = "TFLiteClassifier";
    private Interpreter tflite;
    private Map<String, Integer> vocab = new HashMap<>();
    private Map<Integer, String> labelMap = new HashMap<>();
    private final int MAX_LENGTH = 64;

    public TfliteDeceptionClassifier(Context context) {
        try {
            // 1. 加载模型
            MappedByteBuffer modelBuffer = loadModelFile(context, "sentinel_guard.tflite");
            tflite = new Interpreter(modelBuffer);
            
            boolean internalLoaded = false;

            // 策略 A: 尝试使用官方 MetadataExtractor (规范加载)
            try {
                MetadataExtractor extractor = new MetadataExtractor(modelBuffer);
                try (InputStream vIs = extractor.getAssociatedFile("vocab.json");
                     InputStream lIs = extractor.getAssociatedFile("label_map.json")) {
                    if (vIs != null && lIs != null) {
                        parseVocab(vIs);
                        parseLabelMap(lIs);
                        Log.i(TAG, "✓ 策略 A: 从模型 Metadata 内部加载成功");
                        internalLoaded = true;
                    }
                }
            } catch (Exception ignored) {}

            // 策略 B: 复刻 Python 逻辑 (搜寻 PK\x03\x04 ZIP 数据段直接读取)
            if (!internalLoaded) {
                internalLoaded = manualExtractFromZip(modelBuffer);
                if (internalLoaded) Log.i(TAG, "✓ 策略 B: 通过搜寻 ZIP 标志位从内部加载成功");
            }

            // 策略 C: 最后退避到 Assets 外部加载
            if (!internalLoaded) {
                Log.w(TAG, "⚠️ 策略 C: 模型内部无配置，改用外部 Assets 文件");
                try (InputStream vIs = context.getAssets().open("vocab.json");
                     InputStream lIs = context.getAssets().open("label_map.json")) {
                    parseVocab(vIs);
                    parseLabelMap(lIs);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "FATAL: 初始化失败: " + e.getMessage());
        }
    }

    /**
     * 手动搜索并提取 ZIP 内容 (复刻 Python: data.find(b"PK\x03\x04"))
     */
    private boolean manualExtractFromZip(MappedByteBuffer buf) {
        buf.rewind();
        byte[] magic = {0x50, 0x4B, 0x03, 0x04}; // PK\x03\x04
        int startOffset = -1;
        for (int i = 0; i < buf.limit() - 4; i++) {
            if (buf.get(i) == magic[0] && buf.get(i+1) == magic[1] &&
                buf.get(i+2) == magic[2] && buf.get(i+3) == magic[3]) {
                startOffset = i;
                break;
            }
        }
        if (startOffset == -1) return false;

        try {
            buf.position(startOffset);
            ZipInputStream zis = new ZipInputStream(new ByteBufferInputStream(buf));
            ZipEntry entry;
            boolean vOk = false, lOk = false;
            while ((entry = zis.getNextEntry()) != null) {
                if ("vocab.json".equals(entry.getName())) {
                    parseVocab(zis); vOk = true;
                } else if ("label_map.json".equals(entry.getName())) {
                    parseLabelMap(zis); lOk = true;
                }
                zis.closeEntry();
            }
            return vOk && lOk;
        } catch (Exception e) {
            return false;
        }
    }

    private MappedByteBuffer loadModelFile(Context context, String modelPath) throws IOException {
        AssetFileDescriptor fd = context.getAssets().openFd(modelPath);
        FileInputStream fis = new FileInputStream(fd.getFileDescriptor());
        return fis.getChannel().map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
    }

    private void parseVocab(InputStream is) throws Exception {
        String json = new Scanner(is).useDelimiter("\\A").next();
        JSONObject obj = new JSONObject(json);
        vocab.clear();
        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            vocab.put(k, obj.getInt(k));
        }
    }

    private void parseLabelMap(InputStream is) throws Exception {
        String json = new Scanner(is).useDelimiter("\\A").next();
        JSONObject obj = new JSONObject(json);
        labelMap.clear();
        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            labelMap.put(Integer.parseInt(k), obj.getString(k));
        }
    }

    public AntiDeceptionManager.RiskLevel predict(String text) {
        if (tflite == null || text == null || vocab.isEmpty()) return AntiDeceptionManager.RiskLevel.SAFE;
        
        // 1. 预处理 (字符索引 + Padding)
        int[][] input = new int[1][MAX_LENGTH];
        for (int i = 0; i < MAX_LENGTH; i++) {
            input[0][i] = (i < text.length()) ? vocab.getOrDefault(String.valueOf(text.charAt(i)), 1) : 0;
        }
        
        // 2. 推理
        float[][] output = new float[1][labelMap.size()];
        tflite.run(input, output);
        
        // 3. 解析结果 (Argmax + Confidence)
        int maxIdx = 0;
        float maxVal = -1f;
        for (int i = 0; i < labelMap.size(); i++) {
            if (output[0][i] > maxVal) {
                maxVal = output[0][i];
                maxIdx = i;
            }
        }
        
        String label = labelMap.get(maxIdx);
        Log.d(TAG, String.format("【AI模型研判】文本: %s -> 标签: %s (置信度: %.2f%%)", text, label, maxVal * 100));

        if ("DANGER".equalsIgnoreCase(label)) return AntiDeceptionManager.RiskLevel.DANGER;
        if ("SUSPECT".equalsIgnoreCase(label)) return AntiDeceptionManager.RiskLevel.SUSPECT;
        return AntiDeceptionManager.RiskLevel.SAFE;
    }

    /**
     * 辅助类：将 ByteBuffer 转换为 InputStream
     */
    private static class ByteBufferInputStream extends InputStream {
        private final MappedByteBuffer buf;
        public ByteBufferInputStream(MappedByteBuffer buf) { this.buf = buf; }
        @Override public int read() { return buf.hasRemaining() ? (buf.get() & 0xFF) : -1; }
        @Override public int read(byte[] b, int off, int len) {
            if (!buf.hasRemaining()) return -1;
            int count = Math.min(len, buf.remaining());
            buf.get(b, off, count);
            return count;
        }
    }
}
