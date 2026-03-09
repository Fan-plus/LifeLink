package com.example.lifelink.ui.memory;

import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
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
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lifelink.R;
import com.example.lifelink.api.VideoGenerator;
import com.example.lifelink.data.memory.MemoryDbHelper;
import com.example.lifelink.ocr.SimpleOcrRecognizer;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

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

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_GALLERY_PICK = 2;

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
    }

    private void initializeViews(View view) {
        memoryInput = view.findViewById(R.id.memory_input);
        addMemoryBtn = view.findViewById(R.id.memory_add_btn);
        memoryRecycler = view.findViewById(R.id.memory_recycler);

        dbHelper = new MemoryDbHelper(getContext());
        memoryAdapter = new MemoryAdapter(getContext(), dbHelper.getAllMemories());
        memoryRecycler.setLayoutManager(new GridLayoutManager(getContext(), 2));
        memoryRecycler.setAdapter(memoryAdapter);

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

    private void initializeOcr() {
        ocrRecognizer = SimpleOcrRecognizer.getInstance(getActivity());
        ocrRecognizer.ensureInitialized();
        isOcrInitialized = true;
    }

    private void setupListeners() {
        addMemoryBtn.setOnClickListener(this);
        shootMedicineBtn.setOnClickListener(this);
        collectBtn.setOnClickListener(this);
        aiChatBtn.setOnClickListener(this);
        btnFullScreen.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.medicine_shoot_btn) handleShootMedicine();
        else if (v.getId() == R.id.btn_full_screen) openFullScreenVideo();
        // 其他点击处理...
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

        ocrRecognizer.recognizeTextAsync(bitmap, new SimpleOcrRecognizer.OcrCallback() {
            @Override
            public void onSuccess(String text) {
                if (!TextUtils.isEmpty(text)) {
                    startAsyncVideoFlow(text);
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

    private void startAsyncVideoFlow(String script) {
        ocrStatusLabel.setText("正在为您生成 3D 讲解视频...");
        ocrProgressBar.setIndeterminate(false);
        ocrProgressBar.setProgress(0);

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
                resetOcrStatus("识别完成，视频已生成");
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
        btnFullScreen.setVisibility(View.VISIBLE);

        // 1. 添加播放控制器（允许暂停、进度调节）
        MediaController mediaController = new MediaController(getContext());
        mediaController.setAnchorView(videoView);
        videoView.setMediaController(mediaController);

        Uri uri = Uri.parse(videoUrl);
        videoView.setVideoURI(uri);

        videoView.setOnPreparedListener(mp -> {
            videoLoading.setVisibility(View.GONE);
            // 2. 取消自动循环，允许用户控制
            mp.setLooping(false); 
            videoView.start();
        });

        videoView.setOnErrorListener((mp, what, extra) -> {
            videoLoading.setVisibility(View.GONE);
            videoPlaceholder.setVisibility(View.VISIBLE);
            btnFullScreen.setVisibility(View.GONE);
            Toast.makeText(getContext(), "播放失败，请检查网络", Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    /**
     * 4. 实现横屏放大播放逻辑
     */
    private void openFullScreenVideo() {
        if (TextUtils.isEmpty(currentVideoUrl)) return;
        
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(currentVideoUrl), "video/*");
            // 调用系统专业播放器，支持旋转、缩放等
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
        super.onDestroy();
    }
}
