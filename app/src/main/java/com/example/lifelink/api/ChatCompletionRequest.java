package com.example.lifelink.api;

import java.util.ArrayList;
import java.util.List;

public class ChatCompletionRequest {
    private String model;
    private List<Message> messages;
    private boolean stream; // 新增流式开关

    public ChatCompletionRequest(String model, List<Message> messages) {
        this.model = model;
        this.messages = messages;
        this.stream = false;
    }

    public ChatCompletionRequest(String model, List<Message> messages, boolean stream) {
        this.model = model;
        this.messages = messages;
        this.stream = stream;
    }

    public static class Message {
        private String role;
        private String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }
    }

    public void setStream(boolean stream) {
        this.stream = stream;
    }
}
