package com.example.lifelink.ui.treasure;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.lifelink.R;
import com.example.lifelink.data.treasure.TreasureDbHelper;
import com.example.lifelink.llm.LlamaManager;

import java.util.List;

public class MemoirDisplayActivity extends AppCompatActivity {

    private TextView tvTitle;
    private TextView tvContent;
    private View loadingLayout;
    private LlamaManager llamaManager;
    private TreasureDbHelper dbHelper;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_memoir_display);

        tvTitle = findViewById(R.id.tv_memoir_title);
        tvContent = findViewById(R.id.tv_memoir_content);
        loadingLayout = findViewById(R.id.loading_layout);

        llamaManager = LlamaManager.getInstance(this);
        dbHelper = new TreasureDbHelper(this);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_re_generate).setOnClickListener(v -> startGeneration());

        startGeneration();
    }

    private void startGeneration() {
        loadingLayout.setVisibility(View.VISIBLE);
        tvTitle.setText("");
        tvContent.setText("");
        
        new Thread(() -> {
            List<TreasureDbHelper.MemoryEntry> memories = dbHelper.getAllMemories();
            if (memories.isEmpty()) {
                runOnUiThread(() -> {
                    loadingLayout.setVisibility(View.GONE);
                    tvContent.setText("时光静好，您还没有留下太多痕迹。多记录一些文字或语音吧，我会为您编织最美的回忆。");
                });
                return;
            }

            StringBuilder sb = new StringBuilder();
            for (TreasureDbHelper.MemoryEntry entry : memories) {
                if ("text".equals(entry.type)) {
                    sb.append("- ").append(entry.content).append("\n");
                } else {
                    sb.append("- [一段深情的语音记忆]\n");
                }
            }

            llamaManager.generateMemoir(sb.toString(), text -> {
                runOnUiThread(() -> {
                    loadingLayout.setVisibility(View.GONE);
                    // 假设 AI 返回的第一行是标题，或者我们简单处理
                    String[] parts = text.split("\n", 2);
                    String title = parts[0].replace("#", "").trim();
                    String content = parts.length > 1 ? parts[1].trim() : "";
                    
                    tvTitle.setText(title);
                    startTypewriterEffect(content);
                });
            });
        }).start();
    }

    private void startTypewriterEffect(String fullText) {
        tvContent.setText("");
        final int[] index = {0};
        handler.removeCallbacksAndMessages(null);
        
        Runnable typewriterRunnable = new Runnable() {
            @Override
            public void run() {
                if (index[0] < fullText.length()) {
                    tvContent.append(String.valueOf(fullText.charAt(index[0])));
                    index[0]++;
                    handler.postDelayed(this, 60);
                }
            }
        };
        handler.post(typewriterRunnable);
    }
}
