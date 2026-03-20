package com.example.lifelink.ui.treasure;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.lifelink.R;
import com.example.lifelink.data.treasure.TreasureDbHelper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.IOException;

public class AudioMemoryActivity extends AppCompatActivity {

    private static final String TAG = "AudioMemoryActivity";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    private MediaRecorder recorder = null;
    private String fileName = null;
    private TreasureDbHelper dbHelper;

    private FloatingActionButton btnRecord;
    private TextView tvStatus;
    private View viewRipple;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_memory);

        dbHelper = new TreasureDbHelper(this);
        btnRecord = findViewById(R.id.btn_record_audio);
        tvStatus = findViewById(R.id.tv_record_status);
        viewRipple = findViewById(R.id.view_ripple_audio);

        // 自定义关闭按钮
        findViewById(R.id.btn_close_audio).setOnClickListener(v -> finish());

        fileName = getExternalCacheDir().getAbsolutePath() + "/audiomemory_" + System.currentTimeMillis() + ".3gp";

        btnRecord.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (checkPermission()) {
                        startRecording();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    stopRecording();
                    return true;
            }
            return false;
        });
    }

    private boolean checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
            return false;
        }
        return true;
    }

    private void startRecording() {
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setOutputFile(fileName);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try {
            recorder.prepare();
            recorder.start();
            tvStatus.setText("正在倾听您的述说...");
            viewRipple.animate().scaleX(1.5f).scaleY(1.5f).alpha(0.3f).setDuration(500).start();
        } catch (IOException e) {
            Log.e(TAG, "prepare() failed");
        }
    }

    private void stopRecording() {
        if (recorder != null) {
            try {
                recorder.stop();
                recorder.release();
                recorder = null;
                dbHelper.addMemory("audio", fileName);
                tvStatus.setText("长按开始录制");
                viewRipple.animate().scaleX(1.0f).scaleY(1.0f).alpha(0.1f).setDuration(300).start();
                Toast.makeText(this, "声音记忆已封存", Toast.LENGTH_LONG).show();
                finish();
            } catch (Exception e) {
                Log.e(TAG, "stop failed");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "权限已获取", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
