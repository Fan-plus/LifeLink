package com.example.lifelink.integration;

public interface PushNotificationService {
    void sendNotification(String deviceToken, String title, String message);
}
