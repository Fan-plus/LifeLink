package com.example.lifelink.data.health;

import android.content.Context;

import java.util.List;

public class HealthRepository {
    private final HealthDbHelper dbHelper;

    public HealthRepository(Context ctx) {
        dbHelper = new HealthDbHelper(ctx.getApplicationContext());
    }

    /**
     * 插入完整的健康样本
     */
    public long insertSample(long timestamp, int hr, int bps, int bpd, int spo2, float gas, int steps) {
        return dbHelper.addSample(timestamp, hr, bps, bpd, spo2, gas, steps);
    }

    public List<HealthData> getLatest(int limit) {
        return dbHelper.getLatestSamples(limit);
    }
}
