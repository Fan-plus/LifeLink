package com.example.lifelink;

import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.fragment.app.Fragment;

public class MemoryGuardFragment extends Fragment implements View.OnClickListener {

    private EditText memoryInput;
    private Button addMemoryBtn;
    private RecyclerView memoryRecycler;
    private MemoryAdapter memoryAdapter;
    private MemoryDbHelper dbHelper;
    private Button shootMedicineBtn;
    private Button replayVoiceBtn;
    private Button regenVideoBtn;
    private Button collectBtn;
    private Button aiChatBtn;

    private static final int REQUEST_IMAGE_CAPTURE = 1;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_memory_guard, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeViews(view);
        setupListeners();
    }

    private void initializeViews(View view) {
        // 记忆库相关
        memoryInput = view.findViewById(R.id.memory_input);
        addMemoryBtn = view.findViewById(R.id.memory_add_btn);
        memoryRecycler = view.findViewById(R.id.memory_recycler);

        dbHelper = new MemoryDbHelper(getContext());
        memoryAdapter = new MemoryAdapter(getContext(), dbHelper.getAllMemories());
        GridLayoutManager glm = new GridLayoutManager(getContext(), 2);
        memoryRecycler.setLayoutManager(glm);
        memoryRecycler.setAdapter(memoryAdapter);

        // 药品识别相关
        shootMedicineBtn = view.findViewById(R.id.medicine_shoot_btn);
        replayVoiceBtn = view.findViewById(R.id.medicine_replay_voice);
        regenVideoBtn = view.findViewById(R.id.medicine_regen_video);
        collectBtn = view.findViewById(R.id.medicine_collect);

        // AI 对话浮动按钮
        aiChatBtn = view.findViewById(R.id.ai_chat_fab);
    }

    private void setupListeners() {
        addMemoryBtn.setOnClickListener(this);
        shootMedicineBtn.setOnClickListener(this);
        replayVoiceBtn.setOnClickListener(this);
        regenVideoBtn.setOnClickListener(this);
        collectBtn.setOnClickListener(this);
        aiChatBtn.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.memory_add_btn) {
            handleAddMemory();
        } else if (v.getId() == R.id.medicine_shoot_btn) {
            handleShootMedicine();
        } else if (v.getId() == R.id.medicine_replay_voice) {
            handleReplayVoice();
        } else if (v.getId() == R.id.medicine_regen_video) {
            handleRegenVideo();
        } else if (v.getId() == R.id.medicine_collect) {
            handleCollect();
        } else if (v.getId() == R.id.ai_chat_fab) {
            handleAIChat();
        }
    }

    /**
     * 处理添加记忆物品
     */
    private void handleAddMemory() {
        String input = memoryInput.getText().toString().trim();
        if (TextUtils.isEmpty(input)) {
            Toast.makeText(getContext(), "请输入要记忆的物品", Toast.LENGTH_SHORT).show();
            return;
        }
        // 解析 title / note
        String[] parts = parseTitleAndNote(input);
        String title = parts[0];
        String note = parts[1];

        // 查找相似或相同的已有记录（先按精确，再按归一化相似）
        MemoryItem exact = dbHelper.getMemoryByTitle(title);
        if (exact != null) {
            // 精确匹配直接更新
            boolean ok = dbHelper.updateMemory(exact.getId(), title, note);
            if (ok) {
                memoryAdapter.setData(dbHelper.getAllMemories());
                memoryInput.setText("");
                Toast.makeText(getContext(), "已更新：" + title, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "更新失败，请重试", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // 尝试按归一化或前缀匹配查找相似项
        String normTitle = normalizeTitle(title);
        MemoryItem similar = null;
        for (MemoryItem mi : dbHelper.getAllMemories()) {
            String otherNorm = normalizeTitle(mi.getTitle());
            if (otherNorm.equals(normTitle) || otherNorm.contains(normTitle) || normTitle.contains(otherNorm)) {
                similar = mi;
                break;
            }
        }

        if (similar != null) {
            // 将 similar 复制为 final 局部变量以供 lambda 使用
            final MemoryItem similarFound = similar;
            // 弹窗确认是否覆盖/更新
            String msg = "检测到已存在类似记忆：\n\n'" + similarFound.getTitle() + "' → " + similarFound.getNote() + "\n\n是否将位置/备注更新为：\n\n'" + note + "' ?";
            new AlertDialog.Builder(getContext())
                    .setTitle("更新已存在记忆?")
                    .setMessage(msg)
                    .setPositiveButton("更新", (dialog, which) -> {
                        boolean ok = dbHelper.updateMemory(similarFound.getId(), title, note);
                        if (ok) {
                            memoryAdapter.setData(dbHelper.getAllMemories());
                            memoryInput.setText("");
                            Toast.makeText(getContext(), "已更新：" + title, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getContext(), "更新失败，请重试", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("取消", (dialog, which) -> {
                        // 用户取消，不做操作
                    })
                    .show();
        } else {
            long id = dbHelper.addMemory(title, note);
            if (id > 0) {
                memoryAdapter.setData(dbHelper.getAllMemories());
                memoryInput.setText("");
                Toast.makeText(getContext(), "已添加：" + title, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "添加失败，请重试", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 解析输入为 title 和 note，优先按换行，其次中文/英文常见分隔符（含“在”）
    private String[] parseTitleAndNote(String input) {
        if (input == null) return new String[]{"", ""};
        input = input.trim();
        if (input.isEmpty()) return new String[]{"", ""};

        // 优先按换行
        int nl = input.indexOf('\n');
        if (nl > 0) {
            String t = input.substring(0, nl).trim();
            String n = input.substring(nl + 1).trim();
            return new String[]{t, n};
        }

        // 括号优先：例如 "钥匙(客厅抽屉)" 或 "钥匙（客厅抽屉）"
        int idxBracket = input.indexOf('(');
        if (idxBracket < 0) idxBracket = input.indexOf('（');
        if (idxBracket > 0) {
            String t = input.substring(0, idxBracket).trim();
            String n = input.substring(idxBracket).trim();
            return new String[]{t, n};
        }

        // 一组常见定位短语（长词先匹配）
        String[] locPhrases = new String[]{"存放在", "放在", "放着", "放于", "位于", "放入", "放进", "放到", "在"};
        for (String p : locPhrases) {
            int i = input.indexOf(p);
            if (i > 0) {
                String t = input.substring(0, i).trim();
                String n = input.substring(i).trim(); // keep the phrase
                return new String[]{t, n};
            }
        }

        // 说明/用法类短语
        String[] infoPhrases = new String[]{"用法", "用途", "用", "注意", "禁忌", "说明", "说明书"};
        for (String p : infoPhrases) {
            int i = input.indexOf(p);
            if (i > 0) {
                String t = input.substring(0, i).trim();
                String n = input.substring(i).trim();
                return new String[]{t, n};
            }
        }

        // 连系词/描述类："是","有","属于"
        String[] verbPhrases = new String[]{"是", "有", "属于"};
        for (String p : verbPhrases) {
            int i = input.indexOf(p);
            if (i > 0) {
                String t = input.substring(0, i).trim();
                String n = input.substring(i).trim();
                return new String[]{t, n};
            }
        }

        // 其他分隔符（冒号、竖线、破折号、逗号等）
        String[] delims = new String[]{":", "：", "|", " - ", "—", "-", "，", ","};
        for (String d : delims) {
            int idx = input.indexOf(d);
            if (idx > 0) {
                String t = input.substring(0, idx).trim();
                String n = input.substring(idx + d.length()).trim();
                return new String[]{t, n};
            }
        }

        // 无明显分隔：全部当 title
        return new String[]{input, ""};
    }

    // 简单归一化标题：小写、去标点、去常见量词、trim
    private String normalizeTitle(String s) {
        if (s == null) return "";
        String t = s.trim().toLowerCase();
        // 去掉标点
        t = t.replaceAll("[\\p{Punct}\\u3000-\\u303F\\uFF00-\\uFFEF]", "");
        // 去掉常见量词/助词
        String[] stop = new String[]{"个", "只", "片", "盒", "本", "条", "瓶", "把", "张", "台"};
        for (String sp : stop) {
            t = t.replace(sp, "");
        }
        t = t.replaceAll("\\s+", " ").trim();
        return t;
    }

    /**
     * 处理拍照识别药品
     */
    private void handleShootMedicine() {
        // 启动相机应用
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getActivity().getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        } else {
            Toast.makeText(getContext(), "设备不支持拍照功能", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 处理重播语音解读
     */
    private void handleReplayVoice() {
        Toast.makeText(getContext(), "正在为您播放语音解读...", Toast.LENGTH_SHORT).show();
        // 这里可以扩展为实际的文字转语音(TTS)功能
        // 示例：playText("这是降压药硝苯地平缓释片，每日一次，早晨服用，不要和酒精一起服用");
    }

    /**
     * 处理重新生成 3D 视频
     */
    private void handleRegenVideo() {
        Toast.makeText(getContext(), "正在重新生成 3D 讲解视频...", Toast.LENGTH_SHORT).show();
        // 这里可以扩展为实际的视频生成或网络请求
    }

    /**
     * 处理收藏讲解内容
     */
    private void handleCollect() {
        Toast.makeText(getContext(), "讲解内容已收藏到您的记忆库", Toast.LENGTH_SHORT).show();
        // 这里可以扩展为实际的收藏逻辑
    }

    /**
     * 处理 AI 助手对话
     */
    private void handleAIChat() {
        Toast.makeText(getContext(), "AI 助手正在连接...", Toast.LENGTH_SHORT).show();
        // 这里可以扩展为启动对话界面或弹窗
        // 示例：打开对话弹窗或导航到对话页面
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == android.app.Activity.RESULT_OK) {
            // 处理拍照后的结果
            Toast.makeText(getContext(), "照片已获取，正在识别药品...", Toast.LENGTH_SHORT).show();
            // 这里可以扩展为实际的图像识别逻辑
        }
    }
}
