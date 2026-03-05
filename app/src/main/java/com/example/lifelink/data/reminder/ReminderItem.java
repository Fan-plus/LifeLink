package com.example.lifelink.data.reminder;

public class ReminderItem {
    private long id;
    private String message;
    private long timestamp; // when reminder should fire, epoch millis

    public ReminderItem(long id, String message, long timestamp) {
        this.id = id;
        this.message = message;
        this.timestamp = timestamp;
    }

    public long getId() { return id; }
    public String getMessage() { return message; }
    public long getTimestamp() { return timestamp; }

    public void setMessage(String message) { this.message = message; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}