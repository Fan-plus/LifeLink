package com.example.lifelink.data.reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

public class ReminderScheduler {
    private static final String TAG = "ReminderScheduler";

    public static void schedule(Context context, ReminderItem item) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        // 检查精准闹钟权限 (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!am.canScheduleExactAlarms()) {
                Log.w(TAG, "未授予精准闹钟权限，跳转设置并降级处理");
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    Toast.makeText(context, "请授予精准闹钟权限以确保提醒准时", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Log.e(TAG, "无法跳转到设置页面", e);
                }
                // 如果没权限，降级使用 setAndAllowWhileIdle，虽然不保证 100% 精准，但不会崩溃
                scheduleNonExact(context, item);
                return;
            }
        }

        scheduleExact(context, item);
    }

    private static void scheduleExact(Context context, ReminderItem item) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = getPendingIntent(context, item);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.getTimestamp(), pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, item.getTimestamp(), pi);
            }
            Log.d(TAG, "✅ [Exact] 已设置闹钟 ID: " + item.getId() + "，时间戳: " + item.getTimestamp());
        } catch (SecurityException se) {
            Log.e(TAG, "设置精准闹钟失败 (SecurityException)，尝试降级", se);
            scheduleNonExact(context, item);
        }
    }

    private static void scheduleNonExact(Context context, ReminderItem item) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = getPendingIntent(context, item);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.getTimestamp(), pi);
        } else {
            am.set(AlarmManager.RTC_WAKEUP, item.getTimestamp(), pi);
        }
        Log.d(TAG, "⚠️ [Non-Exact] 已降级设置非精准闹钟 ID: " + item.getId());
    }

    private static PendingIntent getPendingIntent(Context context, ReminderItem item) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("message", item.getMessage());
        intent.putExtra("id", item.getId());
        
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, (int) item.getId(), intent, flags);
    }

    public static void cancel(Context context, long id) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, ReminderReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pi = PendingIntent.getBroadcast(context, (int) id, intent, flags);
        am.cancel(pi);
        Log.d(TAG, "已取消闹钟 ID: " + id);
    }
}
