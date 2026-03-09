package com.example.lifelink.api;

public class TaskResponse {
    private int status; // 文档里返回的是 status 而不是 code
    private String message;
    private TaskData data;

    public int getStatus() { return status; }
    public String getMessage() { return message; }
    public TaskData getData() { return data; }

    public static class TaskData {
        private String task_id;
        public String getTask_id() { return task_id; }
    }
}
