package com.example.lifelink.ui.treasure;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.lifelink.R;
import com.example.lifelink.api.ChatCompletionRequest;
import com.example.lifelink.api.ChatCompletionResponse;
import com.example.lifelink.api.MoneyPrinterApi;
import com.example.lifelink.data.treasure.TreasureDbHelper;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class TextMemoryActivity extends AppCompatActivity {

    private EditText etContent;
    private TextView tvAiStatus;
    private TreasureDbHelper dbHelper;
    private MoneyPrinterApi qwenApi;
    private static final String QWEN_API_KEY = "Bearer sk-e9c20847634d42fe8ce27fa52997c13b";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_memory);

        dbHelper = new TreasureDbHelper(this);
        initQwenApi();
        
        etContent = findViewById(R.id.et_memory_content);
        tvAiStatus = findViewById(R.id.tv_ai_status);

        findViewById(R.id.btn_close).setOnClickListener(v -> finish());

        findViewById(R.id.btn_ai_polish).setOnClickListener(v -> polishText());

        findViewById(R.id.btn_save_text).setOnClickListener(v -> {
            String content = etContent.getText().toString().trim();
            if (content.isEmpty()) {
                Toast.makeText(this, "请写下您的回忆再封存哦", Toast.LENGTH_SHORT).show();
            } else {
                dbHelper.addMemory("text", content);
                Toast.makeText(this, "这段记忆已安全封存", Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void initQwenApi() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        qwenApi = retrofit.create(MoneyPrinterApi.class);
    }

    private void polishText() {
        String fullText = etContent.getText().toString();
        int start = etContent.getSelectionStart();
        int end = etContent.getSelectionEnd();
        
        String textToPolish;
        String prompt;
        boolean isPartial = false;

        if (start != end && start != -1 && end != -1) {
            textToPolish = fullText.substring(start, end).trim();
            isPartial = true;
            // ⭐ 优化点：提供全文背景，让局部润色更自然
            prompt = "你是一位极具同理心的文学家，专门负责润色老人的回忆录。\n" +
                    "【全文背景】：" + fullText + "\n" +
                    "【需润色片段】：" + textToPolish + "\n" +
                    "【要求】：请在保持全文语境连贯、情感真挚的前提下，将“需润色片段”修改得更加细腻、传神。要多用一些描写感触和画面的词汇，避开AI味。只需返回润色后的该片段正文，严禁输出任何解释、引号或前缀。";
        } else {
            textToPolish = fullText.trim();
            prompt = "你是一位极具同理心的文学家，专门负责润色老人的回忆录。\n" +
                    "【原文】：" + textToPolish + "\n" +
                    "【要求】：请将整段话润色得更具诗意和画面感，仿佛老照片在眼前缓缓展开。要保留那种历经沧桑后的淡然与温情。只需返回润色后的全文正文，严禁输出任何解释或多余符号。";
        }

        if (textToPolish.length() < 2) {
            Toast.makeText(this, "请先输入或选择一段话", Toast.LENGTH_SHORT).show();
            return;
        }

        tvAiStatus.setVisibility(View.VISIBLE);
        tvAiStatus.setText(isPartial ? "✨ 正在理解上下文并润色片段..." : "✨ 正在全篇升华中...");
        findViewById(R.id.btn_ai_polish).setEnabled(false);

        List<ChatCompletionRequest.Message> messages = new ArrayList<>();
        messages.add(new ChatCompletionRequest.Message("user", prompt));

        final boolean finalIsPartial = isPartial;
        final int finalStart = start;
        final int finalEnd = end;

        qwenApi.chatCompletions(QWEN_API_KEY, new ChatCompletionRequest("qwen-plus", messages)).enqueue(new Callback<ChatCompletionResponse>() {
            @Override
            public void onResponse(Call<ChatCompletionResponse> call, Response<ChatCompletionResponse> response) {
                runOnUiThread(() -> {
                    tvAiStatus.setVisibility(View.GONE);
                    findViewById(R.id.btn_ai_polish).setEnabled(true);
                    if (response.isSuccessful() && response.body() != null) {
                        String polished = response.body().getFirstAnswer().trim();
                        // 过滤掉AI可能自带的引号
                        polished = polished.replaceAll("^\"|\"$", "");
                        
                        if (finalIsPartial) {
                            etContent.getText().replace(finalStart, finalEnd, polished);
                            Toast.makeText(TextMemoryActivity.this, "片段已完美融合", Toast.LENGTH_SHORT).show();
                        } else {
                            etContent.setText(polished);
                            Toast.makeText(TextMemoryActivity.this, "全篇已升华", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(TextMemoryActivity.this, "润色失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onFailure(Call<ChatCompletionResponse> call, Throwable t) {
                runOnUiThread(() -> {
                    tvAiStatus.setVisibility(View.GONE);
                    findViewById(R.id.btn_ai_polish).setEnabled(true);
                    Toast.makeText(TextMemoryActivity.this, "连接失败", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
}
