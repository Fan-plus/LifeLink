package com.example.lifelink.api;

import java.util.List;

public class TaskStatusResponse {
    private int status; // 接口返回的 HTTP 状态码或业务码
    private String message;
    private TaskStatusData data;

    public int getStatus() { return status; }
    public String getMessage() { return message; }
    public TaskStatusData getData() { return data; }

    public static class TaskStatusData {
        private int state; // ⭐ 关键：1 代表完成，0 代表进行中
        private int progress; // 进度值
        private List<String> videos; // ⭐ 关键：完成后的视频地址列表
        private List<String> combined_videos;

        public int getState() { return state; }
        public int getProgress() { return progress; }
        public List<String> getVideos() { return videos; }
        public List<String> getCombined_videos() { return combined_videos; }
    }
}
