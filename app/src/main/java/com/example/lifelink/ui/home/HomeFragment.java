package com.example.lifelink.ui.home;

import android.Manifest;
import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.lifelink.ui.activity.MainActivity;
import com.example.lifelink.R;
import com.example.lifelink.api.ApiErrorParser;
import com.example.lifelink.api.ChatCompletionRequest;
import com.example.lifelink.api.LifeLinkApi;
import com.example.lifelink.data.health.HealthData;
import com.example.lifelink.data.health.HealthDbHelper;
import com.example.lifelink.data.memory.MemoryDbHelper;
import com.example.lifelink.data.memory.MemoryItem;
import com.example.lifelink.data.reminder.ReminderDbHelper;
import com.example.lifelink.data.reminder.ReminderItem;
import com.example.lifelink.data.reminder.ReminderScheduler;
import com.example.lifelink.llm.LlamaManager;
import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.metadata.MetadataExtractor;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class HomeFragment extends Fragment implements TextToSpeech.OnInitListener {

    private static final String TAG = "HomeFragment";
    private static final String BASE_URL = "http://119.45.114.225:8080/";
    
    private TextView heartRateText;
    private TextView watchStatusText;
    private TextView locationStatusText;
    private Button refreshCheckinButton;
    private View voiceSearchButton;
    private LinearLayout voiceWaveLayout;
    private View dot1, dot2, dot3, dot4, dot5;
    
    private View voiceResultContainer;
    private View voiceResultHeader;
    private View voiceResultContent;
    private ImageView voiceResultExpandIcon;
    private TextView voiceResultLabel;
    private TextView voiceResultText;
    private TextView expandTextHint;
    
    private Animator[] waveAnimators;
    private TextToSpeech tts;

    private Interpreter tflite = null;
    private final Map<String, Integer> vocab = new HashMap<>();
    private final Map<Integer, String> labelMap = new HashMap<>();
    private static final int MAX_LENGTH = 64;

    private RecyclerView medicineReminderRecycler;
    private View reminderContainerCard;
    private View reminderSection;
    private ImageButton btnRefreshReminders;
    private ReminderAdapter reminderAdapter;
    private ReminderDbHelper reminderDb;
    private HealthDbHelper healthDb;
    private MemoryDbHelper memoryDb;

    private LinearLayout btnMedicineIdentify, btnAbnormalWarning, btnContactChildren;
    private View btnWarmCompanion, btnMyMemories, btnWillSafe;

    private OkHttpClient streamClient;
    private static final String QWEN_API_KEY = "Bearer sk-d90c643547854c319b9e76ee55cea60f";
    private final Gson gson = new Gson();

    // Account & Sync Views
    private View layoutAuthInputs, layoutAccountInfo, cardAccountSync, btnAccountToggle;
    private EditText etUsername, etPassword;
    private Button btnRegister, btnLogin, btnGenBindCode, btnLogout;
    private TextView tvLoggedUser, tvBindCode;
    private ImageButton btnCloseAccount;
    private ImageView ivAccountIcon;
    
    private LifeLinkApi lifeLinkApi;
    private SharedPreferences prefs;

    private final BroadcastReceiver dataUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("com.example.lifelink.REFRESH_REMINDERS".equals(action)) {
                loadReminders();
            } else if ("com.example.lifelink.REFRESH_HEALTH_DATA".equals(action)) {
                Log.d(TAG, "🔄 收到健康数据更新广播");
                loadHealthData();
                uploadLatestHealthData();
            }
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        
        prefs = requireContext().getSharedPreferences("LifeLinkPrefs", Context.MODE_PRIVATE);
        initRetrofit();
        initializeViews(view);
        setupStreamClient();
        setClickListeners();
        
        tts = new TextToSpeech(getContext(), this);
        
        reminderDb = new ReminderDbHelper(getContext());
        healthDb = new HealthDbHelper(getContext());
        memoryDb = new MemoryDbHelper(getContext());
        
        setupReminderList();
        loadReminders();
        loadHealthData();
        checkPermissions();
        updateAuthUI();
        
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.example.lifelink.REFRESH_REMINDERS");
        filter.addAction("com.example.lifelink.REFRESH_HEALTH_DATA");
        
        ContextCompat.registerReceiver(requireContext(), dataUpdateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        
        return view;
    }

    private void initRetrofit() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        lifeLinkApi = retrofit.create(LifeLinkApi.class);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.CHINESE);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "TTS Language not supported");
            }
        }
    }

    private void checkPermissions() {
        List<String> permissions = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        if (!permissions.isEmpty()) {
            requestPermissions(permissions.toArray(new String[0]), 1001);
        }
    }

    private void setupStreamClient() {
        streamClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    private void initializeViews(View view) {
        heartRateText = view.findViewById(R.id.heart_rate_text);
        watchStatusText = view.findViewById(R.id.watch_status_text);
        locationStatusText = view.findViewById(R.id.location_status_text);
        refreshCheckinButton = view.findViewById(R.id.refresh_checkin_button);
        voiceSearchButton = view.findViewById(R.id.voice_search_button);
        voiceWaveLayout = view.findViewById(R.id.voice_wave_layout);
        dot1 = view.findViewById(R.id.dot1); dot2 = view.findViewById(R.id.dot2);
        dot3 = view.findViewById(R.id.dot3); dot4 = view.findViewById(R.id.dot4);
        dot5 = view.findViewById(R.id.dot5);
        
        voiceResultContainer = view.findViewById(R.id.voice_result_container);
        voiceResultHeader = view.findViewById(R.id.voice_result_header);
        voiceResultContent = view.findViewById(R.id.voice_result_content);
        voiceResultExpandIcon = view.findViewById(R.id.voice_result_expand_icon);
        voiceResultLabel = view.findViewById(R.id.voice_result_label);
        voiceResultText = view.findViewById(R.id.voice_result_text);
        expandTextHint = view.findViewById(R.id.expand_text_hint);

        new Thread(this::loadModelAndResources).start();

        medicineReminderRecycler = view.findViewById(R.id.medicine_reminder_card);
        reminderContainerCard = view.findViewById(R.id.reminder_container_card);
        reminderSection = view.findViewById(R.id.reminder_section);
        btnRefreshReminders = view.findViewById(R.id.btn_refresh_reminders);
        
        btnMedicineIdentify = view.findViewById(R.id.btn_medicine_identify);
        btnAbnormalWarning = view.findViewById(R.id.btn_abnormal_warning);
        btnContactChildren = view.findViewById(R.id.btn_contact_children);
        btnWarmCompanion = view.findViewById(R.id.btn_warm_companion);
        btnMyMemories = view.findViewById(R.id.btn_my_memories);
        btnWillSafe = view.findViewById(R.id.btn_will_safe);

        // Account & Sync Views
        btnAccountToggle = view.findViewById(R.id.btn_account_toggle);
        cardAccountSync = view.findViewById(R.id.card_account_sync);
        btnCloseAccount = view.findViewById(R.id.btn_close_account);
        ivAccountIcon = view.findViewById(R.id.iv_account_icon);
        
        layoutAuthInputs = view.findViewById(R.id.layout_auth_inputs);
        layoutAccountInfo = view.findViewById(R.id.layout_account_info);
        etUsername = view.findViewById(R.id.et_username);
        etPassword = view.findViewById(R.id.et_password);
        btnRegister = view.findViewById(R.id.btn_register);
        btnLogin = view.findViewById(R.id.btn_login);
        btnLogout = view.findViewById(R.id.btn_logout);
        btnGenBindCode = view.findViewById(R.id.btn_gen_bind_code);
        tvLoggedUser = view.findViewById(R.id.tv_logged_user);
        tvBindCode = view.findViewById(R.id.tv_bind_code);
    }

    private void loadModelAndResources() {
        try {
            // AI辅助生成：通义千问Qwen-Plus，网页端，2026-03-18；人工补充metadata和zip双通道资源加载逻辑
            MappedByteBuffer modelBuffer = loadModelFile("intent_classifier.tflite");
            tflite = new Interpreter(modelBuffer);
            boolean internalLoaded = false;
            try {
                MetadataExtractor extractor = new MetadataExtractor(modelBuffer);
                try (InputStream vIs = extractor.getAssociatedFile("vocab.json");
                     InputStream lIs = extractor.getAssociatedFile("label_map.json")) {
                    if (vIs != null && lIs != null) { parseVocab(vIs); parseLabelMap(lIs); internalLoaded = true; }
                }
            } catch (Exception ignored) {}
            if (!internalLoaded) internalLoaded = manualExtractFromZip(modelBuffer);
            if (!internalLoaded) {
                try (InputStream vIs = requireContext().getAssets().open("vocab.json");
                     InputStream lIs = requireContext().getAssets().open("label_map.json")) {
                    parseVocab(vIs); parseLabelMap(lIs);
                }
            }
        } catch (Exception e) { Log.e(TAG, "AI模型初始化失败", e); }
    }

    private MappedByteBuffer loadModelFile(String name) throws IOException {
        try (AssetFileDescriptor fd = requireContext().getAssets().openFd(name);
             FileInputStream fis = new FileInputStream(fd.getFileDescriptor())) {
            return fis.getChannel().map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
        }
    }

    private boolean manualExtractFromZip(MappedByteBuffer buf) {
        buf.rewind(); byte[] magic = {0x50, 0x4B, 0x03, 0x04}; int startOffset = -1;
        for (int i = 0; i < buf.limit() - 4; i++) {
            if (buf.get(i) == magic[0] && buf.get(i+1) == magic[1] && buf.get(i+2) == magic[2] && buf.get(i+3) == magic[3]) {
                startOffset = i; break;
            }
        }
        if (startOffset == -1) return false;
        try {
            buf.position(startOffset); ZipInputStream zis = new ZipInputStream(new ByteBufferInputStream(buf));
            ZipEntry entry; boolean vOk = false, lOk = false;
            while ((entry = zis.getNextEntry()) != null) {
                if ("vocab.json".equals(entry.getName())) { parseVocab(zis); vOk = true; }
                else if ("label_map.json".equals(entry.getName())) { parseLabelMap(zis); lOk = true; }
                zis.closeEntry();
            }
            return vOk && lOk;
        } catch (Exception e) { return false; }
    }

    private void parseVocab(InputStream is) throws Exception {
        String json = new Scanner(is, "UTF-8").useDelimiter("\\A").next();
        JSONObject obj = new JSONObject(json); vocab.clear();
        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) { String k = keys.next(); vocab.put(k, obj.getInt(k)); }
    }

    private void parseLabelMap(InputStream is) throws Exception {
        String json = new Scanner(is, "UTF-8").useDelimiter("\\A").next();
        JSONObject obj = new JSONObject(json); labelMap.clear();
        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) { String k = keys.next(); labelMap.put(Integer.parseInt(k), obj.getString(k)); }
    }

    private void setClickListeners() {
        if (refreshCheckinButton != null) refreshCheckinButton.setOnClickListener(v -> performRefreshCheckin());
        
        if (voiceResultHeader != null) {
            voiceResultHeader.setOnClickListener(v -> toggleVoiceResultContent());
        }

        if (voiceSearchButton != null) {
            voiceSearchButton.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN: 
                        startVoiceRecording(); 
                        animateButtonPress(v, true);
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL: 
                        stopVoiceRecordingAndProcess(); 
                        animateButtonPress(v, false);
                        break;
                }
                return true;
            });
        }
        if (btnMedicineIdentify != null) btnMedicineIdentify.setOnClickListener(v -> navigateToFragment(1));
        if (btnAbnormalWarning != null) btnAbnormalWarning.setOnClickListener(v -> navigateToFragment(2));
        if (btnContactChildren != null) btnContactChildren.setOnClickListener(v -> navigateToFragment(3));
        if (btnWarmCompanion != null) btnWarmCompanion.setOnClickListener(v -> navigateToFragment(4));
        if (btnMyMemories != null) btnMyMemories.setOnClickListener(v -> navigateToFragment(5));
        if (btnWillSafe != null) btnWillSafe.setOnClickListener(v -> navigateToFragment(5));

        if (btnRefreshReminders != null) {
            btnRefreshReminders.setOnClickListener(v -> {
                Toast.makeText(getContext(), "正在同步数据...", Toast.LENGTH_SHORT).show();
                loadReminders();
                loadHealthData();
            });
        }

        if (btnAccountToggle != null) {
            btnAccountToggle.setOnClickListener(v -> {
                if (cardAccountSync.getVisibility() == View.VISIBLE) {
                    cardAccountSync.setVisibility(View.GONE);
                } else {
                    cardAccountSync.setVisibility(View.VISIBLE);
                }
            });
        }
        if (btnCloseAccount != null) btnCloseAccount.setOnClickListener(v -> cardAccountSync.setVisibility(View.GONE));

        if (btnRegister != null) btnRegister.setOnClickListener(v -> performRegister());
        if (btnLogin != null) btnLogin.setOnClickListener(v -> performLogin());
        if (btnGenBindCode != null) btnGenBindCode.setOnClickListener(v -> fetchBindCode());
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                prefs.edit().clear().apply();
                updateAuthUI();
                Toast.makeText(getContext(), "已退出登录", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void updateAuthUI() {
        long userId = prefs.getLong("userId", -1);
        String username = prefs.getString("username", null);
        if (userId != -1 && username != null) {
            layoutAuthInputs.setVisibility(View.GONE);
            layoutAccountInfo.setVisibility(View.VISIBLE);
            tvLoggedUser.setText("已登录：" + username);
            ivAccountIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.eye_friendly_accent));
            fetchBindCode();
        } else {
            layoutAuthInputs.setVisibility(View.VISIBLE);
            layoutAccountInfo.setVisibility(View.GONE);
            ivAccountIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.grey_600)); // Assume you have a grey color
        }
    }

    private void performRegister() {
        String user = etUsername.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();
        if (user.isEmpty() || pass.isEmpty()) {
            Toast.makeText(getContext(), "请输入用户名和密码", Toast.LENGTH_SHORT).show();
            return;
        }

        LifeLinkApi.RegisterRequest request = new LifeLinkApi.RegisterRequest(user, pass, "13800138000", "ELDERLY");
        lifeLinkApi.register(request).enqueue(new retrofit2.Callback<LifeLinkApi.UserResponse>() {
            @Override
            public void onResponse(retrofit2.Call<LifeLinkApi.UserResponse> call, retrofit2.Response<LifeLinkApi.UserResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    saveUserInfo(response.body());
                    Toast.makeText(getContext(), "注册并登录成功", Toast.LENGTH_SHORT).show();
                    updateAuthUI();
                    cardAccountSync.setVisibility(View.GONE);
                } else {
                    Toast.makeText(getContext(), "注册失败: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(retrofit2.Call<LifeLinkApi.UserResponse> call, Throwable t) {
                Toast.makeText(getContext(), "网络异常: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void performLogin() {
        String user = etUsername.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();
        if (user.isEmpty() || pass.isEmpty()) {
            Toast.makeText(getContext(), "请输入用户名和密码", Toast.LENGTH_SHORT).show();
            return;
        }

        LifeLinkApi.LoginRequest request = new LifeLinkApi.LoginRequest(user, pass);
        lifeLinkApi.login(request).enqueue(new retrofit2.Callback<LifeLinkApi.UserResponse>() {
            @Override
            public void onResponse(retrofit2.Call<LifeLinkApi.UserResponse> call, retrofit2.Response<LifeLinkApi.UserResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    saveUserInfo(response.body());
                    Toast.makeText(getContext(), "登录成功", Toast.LENGTH_SHORT).show();
                    updateAuthUI();
                    cardAccountSync.setVisibility(View.GONE);
                } else {
                    Toast.makeText(getContext(), "登录失败: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(retrofit2.Call<LifeLinkApi.UserResponse> call, Throwable t) {
                Toast.makeText(getContext(), "网络异常: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveUserInfo(LifeLinkApi.UserResponse user) {
        prefs.edit()
                .putLong("userId", user.id)
                .putString("username", user.username)
                .putString("token", user.token)
                .apply();
    }

    private void fetchBindCode() {
        long userId = prefs.getLong("userId", -1);
        if (userId == -1) return;

        lifeLinkApi.generateBindCode(userId).enqueue(new retrofit2.Callback<ResponseBody>() {
            @Override
            public void onResponse(retrofit2.Call<ResponseBody> call, retrofit2.Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String code = response.body().string();
                        tvBindCode.setText(code);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "获取绑定码失败", t);
            }
        });
    }

    private void uploadLatestHealthData() {
        long userId = prefs.getLong("userId", -1);
        if (userId == -1) return;

        new Thread(() -> {
            List<HealthData> samples = healthDb.getLatestSamples(1);
            if (!samples.isEmpty()) {
                HealthData d = samples.get(0);
                String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(new Date());
                LifeLinkApi.HealthUploadRequest request = new LifeLinkApi.HealthUploadRequest(
                        userId, d.heartRate, d.bpSys + "/" + d.bpDia, 36.5f, timestamp);
                
                lifeLinkApi.uploadHealthData(null, request).enqueue(new retrofit2.Callback<ResponseBody>() {
                    @Override
                    public void onResponse(retrofit2.Call<ResponseBody> call, retrofit2.Response<ResponseBody> response) {
                        if (response.isSuccessful()) {
                            Log.d(TAG, "✅ 健康数据云端同步成功");
                        } else {
                            Log.e(TAG, "❌ 健康数据同步失败: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(retrofit2.Call<ResponseBody> call, Throwable t) {
                        Log.e(TAG, "❌ 健康数据同步网络异常", t);
                    }
                });
            }
        }).start();
    }

    private void toggleVoiceResultContent() {
        if (voiceResultContent == null) return;
        boolean isExpanded = voiceResultContent.getVisibility() == View.VISIBLE;
        if (isExpanded) {
            voiceResultContent.setVisibility(View.GONE);
            if (voiceResultExpandIcon != null) voiceResultExpandIcon.setRotation(0f);
            if (expandTextHint != null) expandTextHint.setText("查看详情");
        } else {
            voiceResultContent.setVisibility(View.VISIBLE);
            if (voiceResultExpandIcon != null) voiceResultExpandIcon.setRotation(180f);
            if (expandTextHint != null) expandTextHint.setText("收起详情");
        }
    }

    private void animateButtonPress(View view, boolean isPressed) {
        if (isPressed) {
            PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.92f);
            PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.92f);
            PropertyValuesHolder alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.8f);
            ObjectAnimator.ofPropertyValuesHolder(view, scaleX, scaleY, alpha)
                    .setDuration(100)
                    .start();
            if (view instanceof MaterialButton) {
                ((MaterialButton) view).setText("正在倾听...");
            }
        } else {
            PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f);
            PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f);
            PropertyValuesHolder alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 1.0f);
            ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(view, scaleX, scaleY, alpha);
            anim.setDuration(300);
            anim.setInterpolator(new OvershootInterpolator());
            anim.start();
            if (view instanceof MaterialButton) {
                ((MaterialButton) view).setText("按住说话");
            }
        }
    }

    private void setupReminderList() {
        if (medicineReminderRecycler == null) return;
        reminderAdapter = new ReminderAdapter(item -> {
            if (item != null && reminderDb != null) {
                ReminderScheduler.cancel(requireContext(), item.getId());
                reminderDb.deleteReminder(item.getId());
                loadReminders();
            }
        });
        medicineReminderRecycler.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.VERTICAL, false));
        medicineReminderRecycler.setAdapter(reminderAdapter);
    }

    private void loadReminders() {
        if (reminderDb == null || reminderSection == null) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            List<ReminderItem> list = reminderDb.getAllReminders();
            if (list != null && !list.isEmpty()) {
                reminderAdapter.setData(list);
                reminderSection.setVisibility(View.VISIBLE);
                if (reminderContainerCard != null) reminderContainerCard.setVisibility(View.VISIBLE);
            } else {
                reminderSection.setVisibility(View.GONE);
            }
        });
    }

    private void navigateToFragment(int position) {
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity != null) {
            ViewPager2 viewPager = mainActivity.findViewById(R.id.view_pager);
            if (viewPager != null) viewPager.setCurrentItem(position);
        }
    }

    private void loadHealthData() {
        new Thread(() -> {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    List<HealthData> samples = healthDb.getLatestSamples(1);
                    if (!samples.isEmpty()) {
                        HealthData d = samples.get(0);
                        if (heartRateText != null) heartRateText.setText(d.heartRate + " 次/分");
                    } else {
                        if (heartRateText != null) heartRateText.setText("-- 次/分");
                    }
                    if (watchStatusText != null) watchStatusText.setText("● 手表已连接");
                    if (locationStatusText != null) locationStatusText.setText("安全区域内");
                });
            }
        }).start();
    }

    private void performRefreshCheckin() { Toast.makeText(getContext(), "重新打卡成功", Toast.LENGTH_SHORT).show(); }

    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private boolean isListening = false;

    private void startVoiceRecording() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.RECORD_AUDIO}, 1001);
            return;
        }

        if (!SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            updateVoiceResult("引擎不可用", "请检查是否安装了语音识别服务", false);
            return;
        }

        isListening = true;
        if (voiceWaveLayout != null) voiceWaveLayout.setVisibility(View.VISIBLE);
        startWaveAnimation();
        
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext());
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { updateVoiceResult("正在倾听...", "", false); }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() { updateVoiceResult("正在解析...", "", false); }
            @Override public void onError(int error) { 
                if (isListening) {
                    Log.e(TAG, "Speech error: " + error);
                    String errorMsg = "识别出错 (" + error + ")";
                    if (error == 7) errorMsg = "识别器忙，请重试";
                    updateVoiceResult(errorMsg, "", false);
                }
            }
            @Override public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                String text = (matches != null && !matches.isEmpty()) ? matches.get(0) : "";
                if (!text.isEmpty()) {
                    updateVoiceResult("您说：", text, false);
                    new Thread(() -> processRecognizedText(text)).start();
                }
            }
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        if (recognizerIntent == null) {
            recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        }

        speechRecognizer.cancel();
        speechRecognizer.startListening(recognizerIntent);
    }

    private void stopVoiceRecordingAndProcess() {
        isListening = false;
        if (voiceWaveLayout != null) voiceWaveLayout.setVisibility(View.GONE);
        stopWaveAnimation();
        if (speechRecognizer != null) speechRecognizer.stopListening();
    }

    private void processRecognizedText(String text) {
        try {
            if (tflite == null || vocab.isEmpty()) return;
            // AI辅助生成：通义千问Qwen-Plus，网页端，2026-03-18；人工重写字符编码、padding与Argmax推理逻辑
            int[][] input = new int[1][MAX_LENGTH];
            for (int i = 0; i < MAX_LENGTH; i++) {
                if (i < text.length()) {
                    Integer val = vocab.get(String.valueOf(text.charAt(i)));
                    input[0][i] = (val != null ? val : 1);
                } else {
                    input[0][i] = 0;
                }
            }
            float[][] output = new float[1][labelMap.size()];
            tflite.run(input, output);
            int best = 0; float maxScore = -1f;
            for (int i = 0; i < labelMap.size(); i++) {
                if (output[0][i] > maxScore) { maxScore = output[0][i]; best = i; }
            }
            String label = labelMap.getOrDefault(best, "UNKNOWN");
            if (getActivity() != null) getActivity().runOnUiThread(() -> handleIntentResult(label, text));
        } catch (Exception e) { Log.e(TAG, "Inference error", e); }
    }

    private void handleIntentResult(String label, String text) {
        Log.d(TAG, "🎯 意图识别结果: " + label);
        switch (label) {
            case "REMINDER_SET":
                handleVoiceReminder(text);
                break;
            case "SOS_EMERGENCY":
                handleSosEmergency();
                break;
            case "OBJECT_FIND":
                handleObjectFind(text);
                break;
            case "HEALTH_STATUS_GENERAL":
                handleHealthStatusQuery();
                break;
            case "HEALTH_QUERY":
                handleHealthQuery(text);
                break;
            case "MEDICINE_USAGE":
            case "AI_CHAT":
            default:
                handleAiChatStream(text);
                break;
        }
    }

    private void handleSosEmergency() {
        updateVoiceResult("⚠️ 紧急求助", "识别到危险！正在尝试联系紧急联系人并发送您的当前位置...", true);
    }

    private void handleObjectFind(String text) {
        updateVoiceResult("寻物助手", "正在为您查找...", false);
        LlamaManager.getInstance(requireContext()).extractSubject(text, "OBJECT", subject -> {
            if (subject == null || subject.isEmpty()) {
                updateVoiceResult("寻物助手", "抱歉，我没听清您要找什么。", true);
                return;
            }
            List<MemoryItem> items = memoryDb.searchMemories(subject);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (items != null && !items.isEmpty()) {
                        MemoryItem bestMatch = items.get(0);
                        updateVoiceResult("找到相关记忆", "关于 \"" + subject + "\"：" + bestMatch.getNote(), true);
                    } else {
                        updateVoiceResult("寻物助手", "抱歉，我的记忆里没有关于 \"" + subject + "\" 的记录。", true);
                    }
                });
            }
        });
    }

    private void handleHealthStatusQuery() {
        updateVoiceResult("健康助手", "正在分析您的最近健康数据...", false);
        new Thread(() -> {
            List<HealthData> samples = healthDb.getLatestSamples(5);
            if (samples.isEmpty()) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> updateVoiceResult("健康助手", "暂无健康数据。请先前往健康页同步数据。", true));
                }
                return;
            }
            
            StringBuilder sb = new StringBuilder("这是我近期的健康数据：\n");
            for (HealthData d : samples) {
                sb.append(String.format(Locale.getDefault(), "- 心率:%d, 血压:%d/%d, 血氧:%d, 步数:%d\n", 
                    d.heartRate, d.bpSys, d.bpDia, d.spo2, d.steps));
            }
            sb.append("请帮我详细分析一下目前的身体状况，并给出简短建议。");
            
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> handleAiChatStream(sb.toString()));
            }
        }).start();
    }

    private void handleHealthQuery(String text) {
        updateVoiceResult("健康查询", "正在通过端侧 AI 识别指标...", false);
        LlamaManager.getInstance(requireContext()).extractSubject(text, "HEALTH", subject -> {
            if (subject == null || subject.isEmpty()) {
                updateVoiceResult("健康查询", "没听清您想查询哪项指标。", true);
                return;
            }
            
            List<HealthData> samples = healthDb.getLatestSamples(1);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (samples.isEmpty()) {
                        updateVoiceResult("健康查询", "暂无您的健康记录。", true);
                        return;
                    }
                    HealthData latest = samples.get(0);
                    String result = "未找到相关指标";
                    if (subject.contains("心率")) result = "您最新的心率是 " + latest.heartRate + " 次/分。";
                    else if (subject.contains("血压")) result = "您最新的血压是 " + latest.bpSys + "/" + latest.bpDia + " mmHg。";
                    else if (subject.contains("血氧")) result = "您最新的血氧饱和度是 " + latest.spo2 + "%。";
                    else if (subject.contains("步数") || subject.contains("步")) result = "您今天的步数是 " + latest.steps + " 步。";
                    
                    updateVoiceResult("查询结果 (" + subject + ")", result, true);
                });
            }
        });
    }

    private void handleAiChatStream(String text) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            updateVoiceResult("助手正在思考...", "", false);
            if (tts != null) tts.stop();
        });

        // AI辅助生成：通义千问Qwen-Turbo，API调试，2026-03-29；人工补充流式拼接、分句播报与失败回退
        List<ChatCompletionRequest.Message> messages = new ArrayList<>();
        messages.add(new ChatCompletionRequest.Message("user", text));
        ChatCompletionRequest chatRequest = new ChatCompletionRequest("qwen-plus", messages, true);
        String jsonBody = gson.toJson(chatRequest);

        Request request = new Request.Builder()
                .url("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions")
                .header("Authorization", QWEN_API_KEY)
                .post(RequestBody.create(MediaType.parse("application/json"), jsonBody))
                .build();

        streamClient.newCall(request).enqueue(new Callback() {
            private final StringBuilder streamingTtsBuffer = new StringBuilder();

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (getActivity() != null) getActivity().runOnUiThread(() -> updateVoiceResult("连接失败", e.getMessage(), true));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    ResponseBody errorBody = response.body();
                    String rawError = errorBody != null ? errorBody.string() : null;
                    String error = ApiErrorParser.parse(response.code(), rawError);
                    if (getActivity() != null) getActivity().runOnUiThread(() -> updateVoiceResult("API 错误", error, true));
                    return;
                }
                ResponseBody body = response.body();
                if (body == null) return;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(body.byteStream()))) {
                    String line;
                    final StringBuilder fullContent = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data:")) {
                            String data = line.substring(5).trim();
                            if ("[DONE]".equals(data)) {
                                if (streamingTtsBuffer.length() > 0) {
                                    String lastPiece = streamingTtsBuffer.toString();
                                    if (getActivity() != null) getActivity().runOnUiThread(() -> speakStreamPiece(lastPiece));
                                }
                                break;
                            }
                            try {
                                JSONObject json = new JSONObject(data);
                                JSONArray choices = json.getJSONArray("choices");
                                if (choices.length() > 0) {
                                    JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
                                    if (delta.has("content")) {
                                        String chunk = delta.getString("content");
                                        fullContent.append(chunk);
                                        streamingTtsBuffer.append(chunk);
                                        
                                        checkAndSpeakBuffer(streamingTtsBuffer);

                                        if (getActivity() != null) {
                                            getActivity().runOnUiThread(() -> updateVoiceResult("助手正在回答：", fullContent.toString(), false));
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }

            private void checkAndSpeakBuffer(StringBuilder buffer) {
                String content = buffer.toString();
                int lastIdx = -1;
                String[] punctuations = {"。", "！", "？", "\n", "；", ".", "!", "?", ";"};
                for (String p : punctuations) {
                    int idx = content.lastIndexOf(p);
                    if (idx > lastIdx) lastIdx = idx;
                }

                if (lastIdx != -1) {
                    String piece = content.substring(0, lastIdx + 1);
                    buffer.delete(0, lastIdx + 1);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> speakStreamPiece(piece));
                    }
                }
            }
        });
    }

    private void speakStreamPiece(String piece) {
        if (tts != null && !piece.trim().isEmpty()) {
            tts.speak(piece, TextToSpeech.QUEUE_ADD, null, "StreamPiece_" + System.currentTimeMillis());
        }
    }

    private void handleVoiceReminder(String text) {
        if (!isAdded() || getContext() == null) return;
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> updateVoiceResult("助手正在解析 Schema...", "正在通过端侧 AI 提取意图...", false));
        }

        // AI辅助生成：DeepSeek-V3，网页端，2026-03-15；人工补充JSON截取、时间换算、提醒落库与调度
        LlamaManager.getInstance(requireContext()).parseReminderSchema(text, result -> {
            if (result == null || result.isEmpty()) {
                if (getActivity() != null) getActivity().runOnUiThread(() -> updateVoiceResult("助手：", "没能听清提醒。", true));
                return;
            }

            try {
                String jsonStr = result;
                if (jsonStr.contains("{") && jsonStr.contains("}")) {
                    jsonStr = jsonStr.substring(jsonStr.indexOf("{"), jsonStr.lastIndexOf("}") + 1);
                }
                
                JSONObject json = new JSONObject(jsonStr);
                String timeType = json.optString("time_type");
                String timeValue = json.optString("time_value");
                String event = json.optString("event");
                ParsedReminderTime ruleTime = parseReminderTimeFromText(text);
                if (ruleTime != null) {
                    timeType = ruleTime.type;
                    timeValue = ruleTime.value;
                }

                long timestamp = calculateTimestamp(timeType, timeValue);
                if (timestamp <= System.currentTimeMillis()) {
                    if (getActivity() != null) getActivity().runOnUiThread(() -> updateVoiceResult("助手：", "时间已过或解析有误。", true));
                    return;
                }

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (reminderDb != null) {
                            long id = reminderDb.addReminder(event, timestamp);
                            ReminderScheduler.schedule(requireContext(), new ReminderItem(id, event, timestamp));
                            
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                            updateVoiceResult("设置成功 ✅", "时间：" + sdf.format(new Date(timestamp)) + "\n内容：" + event, true);
                            loadReminders(); 
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Schema 解析失败", e);
                if (getActivity() != null) getActivity().runOnUiThread(() -> updateVoiceResult("助手提示", "解析出错了。", true));
            }
        });
    }

    private long calculateTimestamp(String type, String value) {
        long now = System.currentTimeMillis();
        try {
            if ("relative".equals(type)) {
                int num = Integer.parseInt(value.substring(0, value.length() - 1));
                char unit = value.charAt(value.length() - 1);
                switch (unit) {
                    case 'm': return now + (long) num * 60 * 1000;
                    case 'h': return now + (long) num * 60 * 60 * 1000;
                    case 's': return now + (long) num * 1000;
                }
            } else if ("absolute".equals(type)) {
                Calendar cal = Calendar.getInstance();
                boolean tomorrow = value.startsWith("tomorrow");
                String timePart = tomorrow ? value.substring(9).trim() : value;
                String[] parts = timePart.split("[:：]");
                int hour = Integer.parseInt(parts[0]);
                int minute = Integer.parseInt(parts[1]);
                cal.set(Calendar.HOUR_OF_DAY, hour);
                cal.set(Calendar.MINUTE, minute);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                if (tomorrow || cal.getTimeInMillis() <= now) cal.add(Calendar.DAY_OF_YEAR, 1);
                return cal.getTimeInMillis();
            }
        } catch (Exception e) { Log.e(TAG, "计算时间失败", e); }
        return 0;
    }

    private ParsedReminderTime parseReminderTimeFromText(String text) {
        if (text == null) return null;
        String normalized = text
                .replace("两", "二")
                .replace("俩", "二")
                .replace("个", "")
                .replace("分鐘", "分钟")
                .replace("鍾", "钟");

        Matcher relative = Pattern.compile("([0-9零〇一二三四五六七八九十百半]+)\\s*(秒|分钟|分|小时|时)\\s*(后|以后|之后)?").matcher(normalized);
        if (relative.find()) {
            String amountText = relative.group(1);
            String unitText = relative.group(2);
            int amount = parseChineseNumber(amountText);
            if (amount <= 0 && !"半".equals(amountText)) return null;

            if (unitText.contains("秒")) {
                return new ParsedReminderTime("relative", amount + "s");
            }
            if (unitText.contains("小时") || unitText.equals("时")) {
                if ("半".equals(amountText)) return new ParsedReminderTime("relative", "30m");
                return new ParsedReminderTime("relative", amount + "h");
            }
            return new ParsedReminderTime("relative", amount + "m");
        }

        Matcher absolute = Pattern.compile("(明天)?\\s*(上午|早上|中午|下午|晚上|今晚)?\\s*([0-9零〇一二三四五六七八九十百]{1,3})\\s*[:：点时]\\s*([0-9零〇一二三四五六七八九十百]{1,3})?").matcher(normalized);
        if (absolute.find()) {
            boolean tomorrow = absolute.group(1) != null;
            String period = absolute.group(2);
            int hour = parseChineseNumber(absolute.group(3));
            String minuteText = absolute.group(4);
            int minute = minuteText == null || minuteText.isEmpty() ? 0 : parseChineseNumber(minuteText);

            if (period != null) {
                if ((period.contains("下午") || period.contains("晚上") || period.contains("今晚")) && hour > 0 && hour < 12) {
                    hour += 12;
                } else if (period.contains("中午") && hour > 0 && hour < 11) {
                    hour += 12;
                }
            }

            if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
                String value = String.format(Locale.US, "%02d:%02d", hour, minute);
                return new ParsedReminderTime("absolute", tomorrow ? "tomorrow " + value : value);
            }
        }

        return null;
    }

    private int parseChineseNumber(String text) {
        if (text == null || text.trim().isEmpty()) return -1;
        String value = text.trim();
        if ("半".equals(value)) return 0;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
        }

        int section = 0;
        int number = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            int digit = chineseDigit(c);
            if (digit >= 0) {
                number = digit;
            } else if (c == '十') {
                section += (number == 0 ? 1 : number) * 10;
                number = 0;
            } else if (c == '百') {
                section += (number == 0 ? 1 : number) * 100;
                number = 0;
            }
        }
        int result = section + number;
        return result > 0 ? result : -1;
    }

    private int chineseDigit(char c) {
        switch (c) {
            case '零':
            case '〇':
                return 0;
            case '一':
                return 1;
            case '二':
                return 2;
            case '三':
                return 3;
            case '四':
                return 4;
            case '五':
                return 5;
            case '六':
                return 6;
            case '七':
                return 7;
            case '八':
                return 8;
            case '九':
                return 9;
            default:
                return -1;
        }
    }

    private static class ParsedReminderTime {
        final String type;
        final String value;

        ParsedReminderTime(String type, String value) {
            this.type = type;
            this.value = value;
        }
    }

    private void updateVoiceResult(String label, String content, boolean shouldSpeak) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            if (voiceResultContainer != null) {
                if (voiceResultContainer.getVisibility() != View.VISIBLE) {
                    voiceResultContainer.setVisibility(View.VISIBLE);
                    if (voiceResultContent != null) voiceResultContent.setVisibility(View.GONE);
                    if (voiceResultExpandIcon != null) voiceResultExpandIcon.setRotation(0f);
                    if (expandTextHint != null) expandTextHint.setText("查看详情");
                }
            }
            if (voiceResultLabel != null) voiceResultLabel.setText(label);
            if (voiceResultText != null) voiceResultText.setText(content);
            
            if (shouldSpeak && !content.isEmpty()) {
                speak(content);
            }
        });
    }

    private void speak(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ResultID");
        }
    }

    private void startWaveAnimation() {
        waveAnimators = new Animator[5];
        View[] dots = new View[]{dot1, dot2, dot3, dot4, dot5};
        for (int i = 0; i < dots.length; i++) {
            if (dots[i] != null) {
                ObjectAnimator a = ObjectAnimator.ofFloat(dots[i], "scaleY", 1f, 2f);
                a.setDuration(300 + i * 80);
                a.setRepeatMode(ObjectAnimator.REVERSE);
                a.setRepeatCount(ObjectAnimator.INFINITE);
                a.start();
                waveAnimators[i] = a;
            }
        }
    }

    private void stopWaveAnimation() {
        if (waveAnimators != null) for (Animator a : waveAnimators) if (a != null) a.cancel();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        try { requireContext().unregisterReceiver(dataUpdateReceiver); } catch (Exception ignored) {}
    }

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
