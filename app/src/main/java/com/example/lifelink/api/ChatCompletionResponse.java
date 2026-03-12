package com.example.lifelink.api;

import java.util.List;

public class ChatCompletionResponse {
    private List<Choice> choices;

    public String getFirstAnswer() {
        if (choices != null && !choices.isEmpty()) {
            return choices.get(0).message.content;
        }
        return "AI 暂时没有回应";
    }

    public static class Choice {
        private Message message;
    }

    public static class Message {
        private String content;
    }
}
