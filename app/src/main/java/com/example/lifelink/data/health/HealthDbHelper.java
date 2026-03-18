package com.example.lifelink.data.health;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class HealthDbHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "health.db";
    private static final int DATABASE_VERSION = 2;

    public static final String TABLE = "health_samples";
    public static final String COL_ID = "_id";
    public static final String COL_TIMESTAMP = "timestamp";
    public static final String COL_HR = "heart_rate";
    public static final String COL_BPS = "bp_sys";
    public static final String COL_BPD = "bp_dia";
    public static final String COL_SPO2 = "spo2";
    public static final String COL_GAS = "gas_level";
    public static final String COL_STEPS = "steps";

    public HealthDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COL_TIMESTAMP + " INTEGER NOT NULL,"
                + COL_HR + " INTEGER,"
                + COL_BPS + " INTEGER,"
                + COL_BPD + " INTEGER,"
                + COL_SPO2 + " INTEGER,"
                + COL_GAS + " REAL,"
                + COL_STEPS + " INTEGER"
                + ")";
        db.execSQL(sql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_GAS + " REAL DEFAULT 0.0");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_STEPS + " INTEGER DEFAULT 0");
        }
    }

    public long addSample(long timestamp, int hr, int bps, int bpd, int spo2, float gas, int steps) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_TIMESTAMP, timestamp);
        cv.put(COL_HR, hr);
        cv.put(COL_BPS, bps);
        cv.put(COL_BPD, bpd);
        cv.put(COL_SPO2, spo2);
        cv.put(COL_GAS, gas);
        cv.put(COL_STEPS, steps);
        long id = db.insert(TABLE, null, cv);
        db.close();
        return id;
    }

    public List<HealthData> getLatestSamples(int limit) {
        List<HealthData> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE, null, null, null, null, null, COL_TIMESTAMP + " ASC", String.valueOf(limit));
        if (c != null) {
            while (c.moveToNext()) {
                long id = c.getLong(c.getColumnIndexOrThrow(COL_ID));
                long ts = c.getLong(c.getColumnIndexOrThrow(COL_TIMESTAMP));
                int hr = c.getInt(c.getColumnIndexOrThrow(COL_HR));
                int bps = c.getInt(c.getColumnIndexOrThrow(COL_BPS));
                int bpd = c.getInt(c.getColumnIndexOrThrow(COL_BPD));
                int spo2 = c.getInt(c.getColumnIndexOrThrow(COL_SPO2));
                float gas = c.getFloat(c.getColumnIndexOrThrow(COL_GAS));
                int steps = c.getInt(c.getColumnIndexOrThrow(COL_STEPS));
                // ⭐ 修正：匹配 HealthData(long, long, int, int, int, int, float, int)
                list.add(new HealthData(id, ts, hr, bps, bpd, spo2, gas, steps));
            }
            c.close();
        }
        db.close();
        return list;
    }
}
