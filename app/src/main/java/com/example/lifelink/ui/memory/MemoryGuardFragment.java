package com.example.lifelink.ui.memory;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lifelink.R;
import com.example.lifelink.api.VideoGenerator;
import com.example.lifelink.data.memory.MemoryDbHelper;
import com.example.lifelink.llm.LlamaManager;
import com.example.lifelink.ocr.SimpleOcrRecognizer;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MemoryGuardFragment extends Fragment implements View.OnClickListener {

    private EditText memoryInput;
    private Button addMemoryBtn;
    private RecyclerView memoryRecycler;
    private MemoryAdapter memoryAdapter;
    private MemoryDbHelper dbHelper;
    private Button shootMedicineBtn;
    private Button collectBtn;
    private FloatingActionButton aiChatBtn;

    // 状态 UI 组件
    private LinearLayout ocrStatusPlaceholder;
    private TextView ocrStatusLabel;
    private ProgressBar ocrProgressBar;
    private ImageView ocrStatusIcon;

    // 播放器组件
    private VideoView videoView;
    private View videoPlaceholder;
    private ProgressBar videoLoading;
    private ImageButton btnFullScreen;
    private String currentVideoUrl;

    private SimpleOcrRecognizer ocrRecognizer;
    private boolean isOcrInitialized = false;
    private VideoGenerator videoGenerator;
    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;
    private boolean isVoiceInputActive = false;

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_GALLERY_PICK = 2;
    private static final int REQUEST_RECORD_AUDIO = 1003;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_memory_guard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeViews(view);
        setupListeners();
        videoGenerator = new VideoGenerator();
        // 预热 Llama 引擎
        LlamaManager.getInstance(getContext());
    }

    private void initializeViews(View view) {
        memoryInput = view.findViewById(R.id.memory_input);
        addMemoryBtn = view.findViewById(R.id.memory_add_btn);
        memoryRecycler = view.findViewById(R.id.memory_recycler);

        dbHelper = new MemoryDbHelper(getContext());
        refreshMemoryList();

        shootMedicineBtn = view.findViewById(R.id.medicine_shoot_btn);
        collectBtn = view.findViewById(R.id.medicine_collect);
        aiChatBtn = view.findViewById(R.id.ai_chat_fab);

        ocrStatusPlaceholder = view.findViewById(R.id.ocr_status_container);
        ocrStatusLabel = view.findViewById(R.id.ocr_status_label);
        ocrProgressBar = view.findViewById(R.id.ocr_status_progress);
        ocrStatusIcon = view.findViewById(R.id.ocr_status_icon);

        videoView = view.findViewById(R.id.video_player_view);
        videoPlaceholder = view.findViewById(R.id.video_placeholder);
        videoLoading = view.findViewById(R.id.video_loading_spinner);
        btnFullScreen = view.findViewById(R.id.btn_full_screen);

        initializeOcr();
    }

    private void refreshMemoryList() {
        if (memoryRecycler == null) return;
        memoryAdapter = new MemoryAdapter(getContext(), dbHelper.getAllMemories(), item -> {
            if (item == null) return;
            dbHelper.deleteMemory(item.getId());
            refreshMemoryList();
            Toast.makeText(getContext(), "已删除记忆：" + item.getTitle(), Toast.LENGTH_SHORT).show();
        });
        memoryRecycler.setLayoutManager(new GridLayoutManager(getContext(), 2));
        memoryRecycler.setAdapter(memoryAdapter);
    }

    private void initializeOcr() {
        ocrRecognizer = SimpleOcrRecognizer.getInstance(getActivity());
        ocrRecognizer.ensureInitialized();
        isOcrInitialized = true;
    }

    private void setupListeners() {
        addMemoryBtn.setOnClickListener(this);
        addMemoryBtn.setOnLongClickListener(v -> {
            startVoiceMemoryInput();
            return true;
        });
        addMemoryBtn.setOnTouchListener((v, event) -> {
            if (isVoiceInputActive && (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL)) {
                stopVoiceMemoryInput();
                return true;
            }
            return false;
        });
        shootMedicineBtn.setOnClickListener(this);
        collectBtn.setOnClickListener(this);
        aiChatBtn.setOnClickListener(this);
        if (btnFullScreen != null) btnFullScreen.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.memory_add_btn) handleAddMemory();
        else if (v.getId() == R.id.medicine_shoot_btn) handleShootMedicine();
        else if (v.getId() == R.id.btn_full_screen) openFullScreenVideo();
        else if (v.getId() == R.id.ai_chat_fab) handleAIChat();
    }

    private void handleAddMemory() {
        String input = memoryInput.getText().toString().trim();
        if (TextUtils.isEmpty(input)) {
            Toast.makeText(getContext(), "请输入记忆内容", Toast.LENGTH_SHORT).show();
            return;
        }

        addMemoryBtn.setEnabled(false);
        addMemoryBtn.setText("存储中...");

        // 调用端侧 AI 提取主语和位置
        // AI辅助生成：DeepSeek-V3，网页端，2026-03-16；人工补充标题清洗、默认值和数据库写入流程
        LlamaManager.getInstance(getContext()).extractSubject(input, "OBJECT_LOCATION", result -> {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                try {
                    String title = "";
                    String note = input; // 默认备注为完整输入

                    if (result != null && !result.isEmpty()) {
                        // 尝试解析 AI 返回的格式，如果 AI 直接返回物品名
                        title = result.trim();
                    }

                    if (TextUtils.isEmpty(title)) {
                        title = "未命名物品";
                    }

                    // 存入数据库
                    dbHelper.addMemory(title, note);
                    
                    // 清空输入并刷新列表
                    memoryInput.setText("");
                    refreshMemoryList();
                    Toast.makeText(getContext(), "记忆已存入：" + title, Toast.LENGTH_SHORT).show();

                } catch (Exception e) {
                    Log.e("MemoryGuard", "存储失败", e);
                    Toast.makeText(getContext(), "存储失败，请重试", Toast.LENGTH_SHORT).show();
                } finally {
                    addMemoryBtn.setEnabled(true);
                    addMemoryBtn.setText("存入");
                }
            });
        });
    }

    private void startVoiceMemoryInput() {
        if (getContext() == null) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            Toast.makeText(getContext(), "请先授予麦克风权限", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            Toast.makeText(getContext(), "语音识别服务不可用", Toast.LENGTH_SHORT).show();
            return;
        }

        isVoiceInputActive = true;
        addMemoryBtn.setText("松手存入");
        Toast.makeText(getContext(), "请说出要保存的位置记忆", Toast.LENGTH_SHORT).show();

        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext());
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}

            @Override
            public void onError(int error) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    resetVoiceMemoryButton();
                    Toast.makeText(getContext(), "没听清，请再长按试一次", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                String text = matches != null && !matches.isEmpty() ? matches.get(0).trim() : "";
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    resetVoiceMemoryButton();
                    if (TextUtils.isEmpty(text)) {
                        Toast.makeText(getContext(), "没听清，请再长按试一次", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    memoryInput.setText(text);
                    memoryInput.setSelection(memoryInput.getText().length());
                    handleAddMemory();
                });
            }

            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        if (speechIntent == null) {
            speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        }
        speechRecognizer.cancel();
        speechRecognizer.startListening(speechIntent);
    }

    private void stopVoiceMemoryInput() {
        isVoiceInputActive = false;
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }
        addMemoryBtn.setText("识别中...");
    }

    private void resetVoiceMemoryButton() {
        isVoiceInputActive = false;
        if (addMemoryBtn != null && addMemoryBtn.isEnabled()) {
            addMemoryBtn.setText("存入");
        }
    }

    private void handleAIChat() {
        Toast.makeText(getContext(), "本地 LLM (Llama.cpp) 已就绪", Toast.LENGTH_SHORT).show();
    }

    private void handleShootMedicine() {
        new MaterialAlertDialogBuilder(getContext())
                .setTitle("选择来源")
                .setItems(new String[]{"📷 拍照", "🖼️ 相册"}, (dialog, which) -> {
                    if (which == 0) openCamera();
                    else openGallery();
                }).show();
    }

    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
    }

    private void openGallery() {
        Intent pickPhotoIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(pickPhotoIntent, REQUEST_GALLERY_PICK);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == android.app.Activity.RESULT_OK && data != null) {
            if (requestCode == REQUEST_IMAGE_CAPTURE) {
                Bundle extras = data.getExtras();
                if (extras != null && extras.get("data") instanceof Bitmap) {
                    startAsyncOcrFlow((Bitmap) extras.get("data"));
                }
            } else if (requestCode == REQUEST_GALLERY_PICK && data.getData() != null) {
                try {
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(getActivity().getContentResolver(), data.getData());
                    startAsyncOcrFlow(bitmap);
                } catch (Exception e) { e.printStackTrace(); }
            }
        }
    }

    private void startAsyncOcrFlow(Bitmap bitmap) {
        ocrStatusIcon.setVisibility(View.GONE);
        ocrProgressBar.setVisibility(View.VISIBLE);
        ocrProgressBar.setIndeterminate(true);
        ocrStatusLabel.setText("正在解析文字内容...");

        // AI辅助生成：通义千问Qwen-Max，网页端，2026-03-23；人工补充OCR状态切换与错误分支处理
        ocrRecognizer.recognizeTextAsync(bitmap, new SimpleOcrRecognizer.OcrCallback() {
            @Override
            public void onSuccess(String text) {
                if (!TextUtils.isEmpty(text)) {
                    // ⭐ 关键点：将原始 OCR 文本交给本地 Llama 引擎提炼
                    startLocalLlamaRefinement(text);
                } else {
                    resetOcrStatus("未识别到文字，请重试");
                }
            }
            @Override
            public void onError(String error) {
                resetOcrStatus("识别失败: " + error);
            }
        });
    }

    /**
     * 调用本地 Llama.cpp 引擎提炼 OCR 文本
     */
    private void startLocalLlamaRefinement(String rawText) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            ocrStatusLabel.setText("本地 AI 正在提炼精简内容...");
            ocrProgressBar.setIndeterminate(true);
        });

        // AI辅助生成：通义千问Qwen-Max，网页端，2026-03-23；人工改造成“OCR识别-语义精炼-视频生成”串联流程
        LlamaManager.getInstance(getContext()).refineOcrText(rawText, refinedText -> {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    // 提炼完成，进入视频生成流程
                    startAsyncVideoFlow(refinedText);
                });
            }
        });
    }

    private void startAsyncVideoFlow(String script) {
        ocrStatusLabel.setText("正在为您生成 3D 讲解视频...");
        ocrProgressBar.setIndeterminate(false);
        ocrProgressBar.setProgress(0);

        // AI辅助生成：通义千问Qwen-Max，网页端，2026-03-24；人工补充任务轮询、进度更新和播放器衔接
        videoGenerator.startGenerateVideo(script, new VideoGenerator.VideoCallback() {
            @Override
            public void onStarted(String taskId) {
                ocrStatusLabel.setText("视频合成中 (ID: " + taskId + ")");
            }

            @Override
            public void onProgress(int progress) {
                ocrProgressBar.setProgress(progress);
                ocrStatusLabel.setText("生成进度: " + progress + "%");
            }

            @Override
            public void onSuccess(String videoUrl) {
                resetOcrStatus("提炼完成，视频已就绪");
                playInternalVideo(videoUrl);
            }

            @Override
            public void onError(String message) {
                resetOcrStatus("视频生成失败: " + message);
            }
        });
    }

    private void playInternalVideo(String videoUrl) {
        this.currentVideoUrl = videoUrl;
        videoPlaceholder.setVisibility(View.GONE);
        videoLoading.setVisibility(View.VISIBLE);
        videoView.setVisibility(View.VISIBLE);
        if (btnFullScreen != null) btnFullScreen.setVisibility(View.VISIBLE);

        MediaController mediaController = new MediaController(getContext());
        mediaController.setAnchorView(videoView);
        videoView.setMediaController(mediaController);

        Uri uri = Uri.parse(videoUrl);
        videoView.setVideoURI(uri);

        videoView.setOnPreparedListener(mp -> {
            videoLoading.setVisibility(View.GONE);
            mp.setLooping(false); 
            videoView.start();
        });

        videoView.setOnErrorListener((mp, what, extra) -> {
            videoLoading.setVisibility(View.GONE);
            videoPlaceholder.setVisibility(View.VISIBLE);
            if (btnFullScreen != null) btnFullScreen.setVisibility(View.GONE);
            Toast.makeText(getContext(), "播放失败，请检查网络", Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    private void openFullScreenVideo() {
        if (TextUtils.isEmpty(currentVideoUrl)) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(currentVideoUrl), "video/*");
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(getContext(), "无法打开全屏播放器", Toast.LENGTH_SHORT).show();
        }
    }

    private void resetOcrStatus(String message) {
        ocrProgressBar.setVisibility(View.GONE);
        ocrStatusIcon.setVisibility(View.VISIBLE);
        ocrStatusLabel.setText(message);
    }

    @Override
    public void onDestroy() {
        if (videoView != null) videoView.stopPlayback();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        super.onDestroy();
    }
}
