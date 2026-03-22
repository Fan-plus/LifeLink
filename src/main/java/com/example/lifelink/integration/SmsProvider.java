package com.example.lifelink.integration;

public interface SmsProvider {
    void send(String phone, String content);
}
