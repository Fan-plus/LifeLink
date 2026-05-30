package com.example.lifelink.data.treasure;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class TreasureDbHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "treasure.db";
    private static final int DATABASE_VERSION = 2;

    public static final String TABLE_MEMORIES = "memories";
    public static final String TABLE_WILL_SAFE = "will_safe";
    public static final String COL_ID = "_id";
    public static final String COL_TYPE = "type"; // "text" or "audio"
    public static final String COL_CONTENT = "content"; // text content or audio file path
    public static final String COL_TIMESTAMP = "timestamp";
    public static final String COL_IV = "iv";
    public static final String COL_UPDATED_AT = "updated_at";

    public TreasureDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String sql = "CREATE TABLE " + TABLE_MEMORIES + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COL_TYPE + " TEXT NOT NULL,"
                + COL_CONTENT + " TEXT NOT NULL,"
                + COL_TIMESTAMP + " INTEGER NOT NULL"
                + ")";
        db.execSQL(sql);
        createWillSafeTable(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            createWillSafeTable(db);
        }
    }

    private void createWillSafeTable(SQLiteDatabase db) {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE_WILL_SAFE + " ("
                + COL_ID + " INTEGER PRIMARY KEY CHECK (" + COL_ID + " = 1),"
                + COL_IV + " TEXT NOT NULL,"
                + COL_CONTENT + " TEXT NOT NULL,"
                + COL_UPDATED_AT + " INTEGER NOT NULL"
                + ")";
        db.execSQL(sql);
    }

    public void addMemory(String type, String content) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_TYPE, type);
        cv.put(COL_CONTENT, content);
        cv.put(COL_TIMESTAMP, System.currentTimeMillis());
        db.insert(TABLE_MEMORIES, null, cv);
        db.close();
    }

    public List<MemoryEntry> getAllMemories() {
        List<MemoryEntry> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_MEMORIES, null, null, null, null, null, COL_TIMESTAMP + " DESC");
        if (c != null) {
            while (c.moveToNext()) {
                long id = c.getLong(c.getColumnIndexOrThrow(COL_ID));
                String type = c.getString(c.getColumnIndexOrThrow(COL_TYPE));
                String content = c.getString(c.getColumnIndexOrThrow(COL_CONTENT));
                long ts = c.getLong(c.getColumnIndexOrThrow(COL_TIMESTAMP));
                list.add(new MemoryEntry(id, type, content, ts));
            }
            c.close();
        }
        db.close();
        return list;
    }

    public void saveEncryptedWill(String iv, String encryptedContent) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_ID, 1);
        cv.put(COL_IV, iv);
        cv.put(COL_CONTENT, encryptedContent);
        cv.put(COL_UPDATED_AT, System.currentTimeMillis());
        db.insertWithOnConflict(TABLE_WILL_SAFE, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
    }

    public WillSafeEntry getEncryptedWill() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_WILL_SAFE, null, COL_ID + " = 1", null, null, null, null);
        WillSafeEntry entry = null;
        if (c != null) {
            if (c.moveToFirst()) {
                entry = new WillSafeEntry(
                        c.getString(c.getColumnIndexOrThrow(COL_IV)),
                        c.getString(c.getColumnIndexOrThrow(COL_CONTENT)),
                        c.getLong(c.getColumnIndexOrThrow(COL_UPDATED_AT))
                );
            }
            c.close();
        }
        db.close();
        return entry;
    }

    public static class MemoryEntry {
        public long id;
        public String type;
        public String content;
        public long timestamp;

        public MemoryEntry(long id, String type, String content, long timestamp) {
            this.id = id;
            this.type = type;
            this.content = content;
            this.timestamp = timestamp;
        }
    }

    public static class WillSafeEntry {
        public String iv;
        public String encryptedContent;
        public long updatedAt;

        public WillSafeEntry(String iv, String encryptedContent, long updatedAt) {
            this.iv = iv;
            this.encryptedContent = encryptedContent;
            this.updatedAt = updatedAt;
        }
    }
}
