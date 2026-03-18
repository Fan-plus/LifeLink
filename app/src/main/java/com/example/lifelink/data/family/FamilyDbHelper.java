package com.example.lifelink.data.family;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class FamilyDbHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "family_connection.db";
    private static final int DATABASE_VERSION = 1;

    // 表 1：守护者信息
    public static final String TABLE_GUARDIANS = "guardians";
    public static final String COL_G_ID = "_id";
    public static final String COL_G_NAME = "name";
    public static final String COL_G_PHONE = "phone";
    public static final String COL_G_EMAIL = "email";
    public static final String COL_G_RELATION = "relation";
    public static final String COL_G_PERMISSION = "permission_level";

    // 表 2：打卡记录
    public static final String TABLE_CHECKINS = "checkins";
    public static final String COL_C_ID = "_id";
    public static final String COL_C_TIMESTAMP = "timestamp";
    public static final String COL_C_STATUS = "status"; // 1: 成功, 0: 失败/未打卡
    public static final String COL_C_TYPE = "type"; // "manual" 或 "auto"

    public FamilyDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 创建守护者表
        String createGuardians = "CREATE TABLE " + TABLE_GUARDIANS + " ("
                + COL_G_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COL_G_NAME + " TEXT NOT NULL,"
                + COL_G_PHONE + " TEXT,"
                + COL_G_EMAIL + " TEXT,"
                + COL_G_RELATION + " TEXT,"
                + COL_G_PERMISSION + " INTEGER DEFAULT 0)";
        db.execSQL(createGuardians);

        // 创建打卡记录表
        String createCheckins = "CREATE TABLE " + TABLE_CHECKINS + " ("
                + COL_C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COL_C_TIMESTAMP + " INTEGER NOT NULL,"
                + COL_C_STATUS + " INTEGER,"
                + COL_C_TYPE + " TEXT)";
        db.execSQL(createCheckins);

        // ⭐ 默认预置两个守护者数据，方便你演示
        presetGuardians(db);
    }

    private void presetGuardians(SQLiteDatabase db) {
        insertGuardianDirect(db, "张三", "19176567826", "son@example.com", "儿子", 1);
        insertGuardianDirect(db, "张四", "19176567826", "daughter@example.com", "女儿", 0);
    }

    private void insertGuardianDirect(SQLiteDatabase db, String name, String phone, String email, String relation, int perm) {
        ContentValues cv = new ContentValues();
        cv.put(COL_G_NAME, name);
        cv.put(COL_G_PHONE, phone);
        cv.put(COL_G_EMAIL, email);
        cv.put(COL_G_RELATION, relation);
        cv.put(COL_G_PERMISSION, perm);
        db.insert(TABLE_GUARDIANS, null, cv);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    // --- 业务方法 ---

    // 记录一次打卡
    public long addCheckin(long timestamp, int status, String type) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_C_TIMESTAMP, timestamp);
        cv.put(COL_C_STATUS, status);
        cv.put(COL_C_TYPE, type);
        long id = db.insert(TABLE_CHECKINS, null, cv);
        db.close();
        return id;
    }

    // 获取最后一次打卡时间
    public long getLastCheckinTime() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_CHECKINS, new String[]{COL_C_TIMESTAMP}, null, null, null, null, COL_C_TIMESTAMP + " DESC", "1");
        long time = 0;
        if (c != null && c.moveToFirst()) {
            time = c.getLong(0);
            c.close();
        }
        db.close();
        return time;
    }

    // 获取所有守护者
    public List<Guardian> getAllGuardians() {
        List<Guardian> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_GUARDIANS, null, null, null, null, null, null);
        if (c != null) {
            while (c.moveToNext()) {
                list.add(new Guardian(
                        c.getLong(c.getColumnIndexOrThrow(COL_G_ID)),
                        c.getString(c.getColumnIndexOrThrow(COL_G_NAME)),
                        c.getString(c.getColumnIndexOrThrow(COL_G_PHONE)),
                        c.getString(c.getColumnIndexOrThrow(COL_G_EMAIL)),
                        c.getString(c.getColumnIndexOrThrow(COL_G_RELATION)),
                        c.getInt(c.getColumnIndexOrThrow(COL_G_PERMISSION))
                ));
            }
            c.close();
        }
        db.close();
        return list;
    }
}
