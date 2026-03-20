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
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_MEMORIES = "memories";
    public static final String COL_ID = "_id";
    public static final String COL_TYPE = "type"; // "text" or "audio"
    public static final String COL_CONTENT = "content"; // text content or audio file path
    public static final String COL_TIMESTAMP = "timestamp";

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
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MEMORIES);
        onCreate(db);
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
}
