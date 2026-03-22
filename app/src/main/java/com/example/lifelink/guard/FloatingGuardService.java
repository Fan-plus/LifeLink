package com.example.lifelink.guard;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Vibrator;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.lifelink.R;

import java.util.ArrayList;
import java.util.Locale;

public class FloatingGuardService extends Service {
    private static final String TAG = "GuardService";
    private WindowManager windowManager;
    private View floatingView;
    private View statusDot;
    private TextView tvStatus;
    private TextView tvDetected;

    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;
    private AntiDeceptionManager manager;
    private Vibrator vibrator;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isListening = false;

    private static final String CHANNEL_ID = "GuardServiceChannel";
    private static final int NOTIF_ID = 101;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "🔔 Service onCreate - 准备开启哨兵");
        
        startForegroundWithNotification();

        manager = AntiDeceptionManager.getInstance(this);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            initFloatingWindow();
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            initSpeechRecognizer();
        } else {
            Log.e(TAG, "❌ 关键错误：缺少录音权限！");
        }
    }

    private void startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "AI Guard", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("AI 语音哨兵运行中")
                .setContentText("正在后台实时守护通话安全")
                .setSmallIcon(R.drawable.ic_main)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // 检查是否拥有麦克风前台服务所需的权限
                boolean hasMicPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
                boolean hasFgsMicPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_MICROPHONE) == PackageManager.PERMISSION_GRANTED;
                
                if (hasMicPermission && hasFgsMicPermission) {
                    startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
                } else {
                    Log.w(TAG, "⚠️ 缺少麦克风权限，降级启动普通前台服务");
                    startForeground(NOTIF_ID, notification);
                }
            } else {
                startForeground(NOTIF_ID, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "前台服务启动失败: " + e.getMessage());
            // 最后的保命手段：尝试不带类型的启动
            try {
                startForeground(NOTIF_ID, notification);
            } catch (Exception ignored) {}
        }
    }

    private void initFloatingWindow() {
        mainHandler.post(() -> {
            try {
                windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
                Context themeContext = new ContextThemeWrapper(this, R.style.Theme_LifeLink);
                floatingView = LayoutInflater.from(themeContext).inflate(R.layout.layout_floating_guard, null);

                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ?
                                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT);

                params.gravity = Gravity.TOP | Gravity.START;
                params.x = 50; params.y = 200;

                statusDot = floatingView.findViewById(R.id.status_dot);
                tvStatus = floatingView.findViewById(R.id.tv_guard_status);
                tvDetected = floatingView.findViewById(R.id.tv_detected_text);

                AlphaAnimation blink = new AlphaAnimation(0.3f, 1.0f);
                blink.setDuration(1200);
                blink.setRepeatMode(Animation.REVERSE);
                blink.setRepeatCount(Animation.INFINITE);
                statusDot.startAnimation(blink);

                floatingView.setOnTouchListener(new View.OnTouchListener() {
                    private int initialX, initialY;
                    private float initialTouchX, initialTouchY;
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                initialX = params.x; initialY = params.y;
                                initialTouchX = event.getRawX(); initialTouchY = event.getRawY();
                                return true;
                            case MotionEvent.ACTION_MOVE:
                                params.x = initialX + (int) (event.getRawX() - initialTouchX);
                                params.y = initialY + (int) (event.getRawY() - initialTouchY);
                                windowManager.updateViewLayout(floatingView, params);
                                return true;
                        }
                        return false;
                    }
                });
                windowManager.addView(floatingView, params);
            } catch (Exception e) {
                Log.e(TAG, "悬浮窗加载异常: " + e.getMessage());
            }
        });
    }

    private void initSpeechRecognizer() {
        mainHandler.post(() -> {
            try {
                if (speechRecognizer != null) speechRecognizer.destroy();
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
                speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
                speechIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

                speechRecognizer.setRecognitionListener(new RecognitionListener() {
                    @Override
                    public void onReadyForSpeech(Bundle params) {
                        Log.d(TAG, "🎙️ 麦克风已就绪，正在等待说话...");
                        isListening = true;
                    }

                    @Override
                    public void onBeginningOfSpeech() {
                        Log.d(TAG, "🎤 检测到用户开始说话");
                    }

                    @Override
                    public void onRmsChanged(float rmsdB) {}

                    @Override
                    public void onResults(Bundle results) {
                        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) handleText(matches.get(0));
                        isListening = false;
                        restartListening();
                    }

                    @Override
                    public void onError(int error) {
                        Log.e(TAG, "⚠️ 识别中断，错误代码: " + error);
                        isListening = false;
                        restartListening();
                    }

                    @Override public void onPartialResults(Bundle partialResults) {
                        ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) handleText(matches.get(0));
                    }

                    @Override public void onBufferReceived(byte[] buffer) {}
                    @Override public void onEndOfSpeech() { Log.d(TAG, "🎤 说话结束"); isListening = false; }
                    @Override public void onEvent(int eventType, Bundle params) {}
                });
                startListening();
            } catch (Exception e) {
                Log.e(TAG, "SpeechRecognizer 初始化失败: " + e.getMessage());
            }
        });
    }

    private void handleText(String text) {
        Log.d(TAG, "📝 识别结果: " + text);
        mainHandler.post(() -> {
            if (tvDetected != null) {
                tvDetected.setVisibility(View.VISIBLE);
                tvDetected.setText(text);
            }
            manager.analyzeSpeech(text, this::updateUI);
        });
    }

    private void startListening() {
        if (isListening) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "startListening 失败: 缺少 RECORD_AUDIO 权限");
            return;
        }
        try {
            if (speechRecognizer != null) {
                speechRecognizer.startListening(speechIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "startListening 失败: " + e.getMessage());
        }
    }

    private void restartListening() {
        mainHandler.postDelayed(this::startListening, 1000);
    }

    private void updateUI(AntiDeceptionManager.RiskLevel level, String message) {
        mainHandler.post(() -> {
            if (tvStatus == null || statusDot == null) return;
            tvStatus.setText(message);
            int color = 0xFF4CAF50;
            if (level == AntiDeceptionManager.RiskLevel.SUSPECT) {
                color = 0xFFFFC107;
                vibrator.vibrate(200);
            } else if (level == AntiDeceptionManager.RiskLevel.DANGER) {
                color = 0xFFF44336;
                vibrator.vibrate(new long[]{0, 500, 200, 500}, -1);
            }
            statusDot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (windowManager != null && floatingView != null) windowManager.removeView(floatingView);
        if (speechRecognizer != null) speechRecognizer.destroy();
    }
}
