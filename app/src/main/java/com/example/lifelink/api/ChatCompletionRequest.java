package com.example.lifelink.api;

import java.util.ArrayList;
import java.util.List;

public class ChatCompletionRequest {
    private String model;
    private List<Message> messages;

    public ChatCompletionRequest(String model, String userPrompt) {
        this.model = model;
        this.messages = new ArrayList<>();
        // 系统提示词，定义 AI 的身份
        this.messages.add(new Message("system", "你是一个温暖的健康守护助手，专门陪伴老年人。请用简短、亲切、易懂的中文回答。"));
        this.messages.add(new Message("user", userPrompt));
    }

    public static class Message {
        private String role;
        private String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}
