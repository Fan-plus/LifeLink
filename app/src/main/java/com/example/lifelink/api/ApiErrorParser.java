package com.example.lifelink.api;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.ResponseBody;
import retrofit2.Response;

public final class ApiErrorParser {
    private ApiErrorParser() {}

    public static String parse(Response<?> response) {
        String raw = null;
        ResponseBody errorBody = response.errorBody();
        if (errorBody != null) {
            try {
                raw = errorBody.string();
            } catch (IOException ignored) {
            }
        }
        return parse(response.code(), raw);
    }

    public static String parse(int httpCode, String rawBody) {
        String prefix = "API错误 Code: " + httpCode;
        if (rawBody == null || rawBody.trim().isEmpty()) {
            return prefix;
        }

        try {
            JSONObject root = new JSONObject(rawBody);
            JSONObject error = root.optJSONObject("error");
            if (error != null) {
                String message = error.optString("message", "").trim();
                String type = error.optString("type", "").trim();
                String code = error.optString("code", "").trim();

                StringBuilder builder = new StringBuilder(prefix);
                if (!message.isEmpty()) builder.append("\n").append(message);
                if (!type.isEmpty()) builder.append("\n类型: ").append(type);
                if (!code.isEmpty()) builder.append("\n错误码: ").append(code);
                return builder.toString();
            }
        } catch (Exception ignored) {
        }

        return prefix + "\n" + rawBody;
    }
}
