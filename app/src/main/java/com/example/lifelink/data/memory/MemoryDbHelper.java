package com.example.lifelink.data.memory;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class MemoryDbHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "memories.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE = "memories";
    public static final String COL_ID = "_id";
    public static final String COL_TITLE = "title";
    public static final String COL_NOTE = "note";

    public MemoryDbHelper(Context context) { super(context, DATABASE_NAME, null, DATABASE_VERSION); }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String sql = "CREATE TABLE " + TABLE + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COL_TITLE + " TEXT NOT NULL,"
                + COL_NOTE + " TEXT"
                + ")";
        db.execSQL(sql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    public long addMemory(String title, String note) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_TITLE, title);
        cv.put(COL_NOTE, note == null ? "" : note);
        long id = db.insert(TABLE, null, cv);
        db.close();
        return id;
    }

    /**
     * 精确查找
     */
    public MemoryItem getMemoryByTitle(String title) {
        if (title == null) return null;
        SQLiteDatabase db = getReadableDatabase();
        String selection = COL_TITLE + " = ?";
        String[] args = new String[]{ title };
        Cursor c = db.query(TABLE, null, selection, args, null, null, null);
        MemoryItem item = null;
        if (c != null) {
            if (c.moveToFirst()) {
                long id = c.getLong(c.getColumnIndexOrThrow(COL_ID));
                String t = c.getString(c.getColumnIndexOrThrow(COL_TITLE));
                String note = c.getString(c.getColumnIndexOrThrow(COL_NOTE));
                item = new MemoryItem(id, t, note);
            }
            c.close();
        }
        db.close();
        return item;
    }

    /**
     * 模糊搜索：支持在标题和备注中搜索关键词
     */
    public List<MemoryItem> searchMemories(String keyword) {
        List<MemoryItem> list = new ArrayList<>();
        if (keyword == null || keyword.isEmpty()) return list;
        
        SQLiteDatabase db = getReadableDatabase();
        // 匹配标题或备注包含关键词的记录
        String selection = COL_TITLE + " LIKE ? OR " + COL_NOTE + " LIKE ?";
        String[] args = new String[]{ "%" + keyword + "%", "%" + keyword + "%" };
        
        Cursor c = db.query(TABLE, null, selection, args, null, null, null);
        if (c != null) {
            while (c.moveToNext()) {
                long id = c.getLong(c.getColumnIndexOrThrow(COL_ID));
                String title = c.getString(c.getColumnIndexOrThrow(COL_TITLE));
                String note = c.getString(c.getColumnIndexOrThrow(COL_NOTE));
                list.add(new MemoryItem(id, title, note));
            }
            c.close();
        }
        db.close();
        return list;
    }

    public boolean updateMemory(long id, String title, String note) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_TITLE, title);
        cv.put(COL_NOTE, note == null ? "" : note);
        int rows = db.update(TABLE, cv, COL_ID + " = ?", new String[]{ String.valueOf(id) });
        db.close();
        return rows > 0;
    }

    public List<MemoryItem> getAllMemories() {
        List<MemoryItem> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE, null, null, null, null, null, COL_ID + " DESC");
        if (c != null) {
            while (c.moveToNext()) {
                long id = c.getLong(c.getColumnIndexOrThrow(COL_ID));
                String title = c.getString(c.getColumnIndexOrThrow(COL_TITLE));
                String note = c.getString(c.getColumnIndexOrThrow(COL_NOTE));
                list.add(new MemoryItem(id, title, note));
            }
            c.close();
        }
        db.close();
        return list;
    }
}
