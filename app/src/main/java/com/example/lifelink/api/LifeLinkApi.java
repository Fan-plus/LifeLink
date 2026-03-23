package com.example.lifelink.api;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface LifeLinkApi {

    @POST("/api/auth/register")
    Call<UserResponse> register(@Body RegisterRequest request);

    @POST("/api/auth/login")
    Call<UserResponse> login(@Body LoginRequest request);

    @POST("/api/family/bind-code")
    Call<ResponseBody> generateBindCode(@Query("elderlyId") long elderlyId);

    @POST("/api/health/upload")
    Call<ResponseBody> uploadHealthData(@Header("Authorization") String token, @Body HealthUploadRequest request);

    class RegisterRequest {
        public String username;
        public String password;
        public String phone;
        public String role;

        public RegisterRequest(String username, String password, String phone, String role) {
            this.username = username;
            this.password = password;
            this.phone = phone;
            this.role = role;
        }
    }

    class LoginRequest {
        public String username;
        public String password;

        public LoginRequest(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }

    class UserResponse {
        public Long id;
        public String username;
        public String password;
        public String phone;
        public String role;
        public String deviceToken;
        public String openId;
        public String token; // Assuming the backend might return a JWT token in future or use this for login status
    }

    class HealthUploadRequest {
        public Long userId;
        public Integer heartRate;
        public String bloodPressure;
        public Float temperature;
        public String timestamp;

        public HealthUploadRequest(Long userId, Integer heartRate, String bloodPressure, Float temperature, String timestamp) {
            this.userId = userId;
            this.heartRate = heartRate;
            this.bloodPressure = bloodPressure;
            this.temperature = temperature;
            this.timestamp = timestamp;
        }
    }
}
