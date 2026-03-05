package com.example.lifelink.data.reminder;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class ReminderDbHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "reminders.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE = "reminders";
    public static final String COL_ID = "_id";
    public static final String COL_MESSAGE = "message";
    public static final String COL_TIMESTAMP = "timestamp";

    public ReminderDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COL_MESSAGE + " TEXT NOT NULL,"
                + COL_TIMESTAMP + " INTEGER NOT NULL"
                + ")";
        db.execSQL(sql);

        // insert some sample data the first time the DB is created (development/testing only)
        // these rows are only added when the table is created; deleting later will persist
        // across app restarts until the database file itself is cleared/uninstalled.
        ContentValues cv = new ContentValues();
        cv.put(COL_MESSAGE, "明天早晨5点吃药");
        cv.put(COL_TIMESTAMP, System.currentTimeMillis() + 24 * 60 * 60 * 1000); // +1 day
        db.insert(TABLE, null, cv);

        cv = new ContentValues();
        cv.put(COL_MESSAGE, "今晚9点服用降压药");
        cv.put(COL_TIMESTAMP, System.currentTimeMillis() + 12 * 60 * 60 * 1000); // +12 hours
        db.insert(TABLE, null, cv);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    public long addReminder(String message, long timestamp) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_MESSAGE, message);
        cv.put(COL_TIMESTAMP, timestamp);
        long id = db.insert(TABLE, null, cv);
        db.close();
        return id;
    }

    public boolean deleteReminder(long id) {
        SQLiteDatabase db = getWritableDatabase();
        int rows = db.delete(TABLE, COL_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
        return rows > 0;
    }

    public List<ReminderItem> getAllReminders() {
        List<ReminderItem> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE, null, null, null, null, null, COL_TIMESTAMP + " ASC");
        if (c != null) {
            while (c.moveToNext()) {
                long id = c.getLong(c.getColumnIndexOrThrow(COL_ID));
                String msg = c.getString(c.getColumnIndexOrThrow(COL_MESSAGE));
                long ts = c.getLong(c.getColumnIndexOrThrow(COL_TIMESTAMP));
                list.add(new ReminderItem(id, msg, ts));
            }
            c.close();
        }
        db.close();
        return list;
    }

    public ReminderItem getReminderById(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE, null, COL_ID + " = ?", new String[]{String.valueOf(id)}, null, null, null);
        ReminderItem item = null;
        if (c != null) {
            if (c.moveToFirst()) {
                String msg = c.getString(c.getColumnIndexOrThrow(COL_MESSAGE));
                long ts = c.getLong(c.getColumnIndexOrThrow(COL_TIMESTAMP));
                item = new ReminderItem(id, msg, ts);
            }
            c.close();
        }
        db.close();
        return item;
    }
}