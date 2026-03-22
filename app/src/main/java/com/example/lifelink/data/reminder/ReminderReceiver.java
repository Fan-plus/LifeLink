package com.example.lifelink.data.reminder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.lifelink.R;

import java.util.Locale;

public class ReminderReceiver extends BroadcastReceiver {
    private static final String TAG = "ReminderReceiver";
    private static TextToSpeech tts;

    @Override
    public void onReceive(Context context, Intent intent) {
        String message = intent.getStringExtra("message");
        long id = intent.getLongExtra("id", -1);

        Log.d(TAG, "🔔 收到闹钟广播! ID: " + id + ", 内容: " + message);

        // 1. 从数据库中删除已触发的提醒
        if (id != -1) {
            ReminderDbHelper db = new ReminderDbHelper(context);
            db.deleteReminder(id);
            Log.d(TAG, "🗑️ 已从数据库删除过期提醒 ID: " + id);
            
            // 发送广播通知 UI 刷新列表
            Intent refreshIntent = new Intent("com.example.lifelink.REFRESH_REMINDERS");
            context.sendBroadcast(refreshIntent);
        }

        // 2. 发送高优先级通知
        showNotification(context, message);
        
        // 3. 语音播报
        speakMessage(context, message);
    }

    private void showNotification(Context context, String message) {
        String channelId = "reminder_channel_high";
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, 
                    "生活助手紧急提醒", 
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 500, 200, 500});
            
            Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmSound == null) alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build();
            channel.setSound(alarmSound, audioAttributes);
            nm.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_main)
                .setContentTitle("【生活助手】到时间啦！")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true);

        nm.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void speakMessage(Context context, String message) {
        String speechText = "提醒时间到了：" + message;
        if (tts == null) {
            tts = new TextToSpeech(context.getApplicationContext(), status -> {
                if (status == TextToSpeech.SUCCESS) {
                    tts.setLanguage(Locale.CHINESE);
                    tts.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "reminder_tts");
                }
            });
        } else {
            tts.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "reminder_tts");
        }
    }
}
