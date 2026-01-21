package com.example.lifelink.data.memory;

public class MemoryItem {
    private long id;
    private String title;
    private String note;

    public MemoryItem(long id, String title, String note) { this.id = id; this.title = title; this.note = note; }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getNote() { return note; }
}
