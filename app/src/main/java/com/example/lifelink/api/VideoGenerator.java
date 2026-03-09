package com.example.lifelink.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class VideoGenerator {
    private static final String TAG = "VideoGenerator";
    private static final String SERVER_IP = "192.168.197.122"; // 您的服务器 IP
    private static final String BASE_URL = "http://" + SERVER_IP + ":8080/";
    private final MoneyPrinterApi api;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public interface VideoCallback {
        void onStarted(String taskId);
        void onProgress(int progress); // 改为 int 以对应文档
        void onSuccess(String videoUrl);
        void onError(String message);
    }

    public VideoGenerator() {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.MINUTES)
                .writeTimeout(10, TimeUnit.MINUTES)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        api = retrofit.create(MoneyPrinterApi.class);
    }

    public void startGenerateVideo(String script, VideoCallback callback) {
        VideoRequest request = new VideoRequest(script);
        api.generateVideo(request).enqueue(new Callback<TaskResponse>() {
            @Override
            public void onResponse(Call<TaskResponse> call, Response<TaskResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getStatus() == 200) {
                    String taskId = response.body().getData().getTask_id();
                    callback.onStarted(taskId);
                    checkTaskStatus(taskId, callback);
                } else {
                    callback.onError("生成请求失败: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<TaskResponse> call, Throwable t) {
                callback.onError("网络错误: " + t.getMessage());
            }
        });
    }

    private void checkTaskStatus(String taskId, VideoCallback callback) {
        api.getTaskStatus(taskId).enqueue(new Callback<TaskStatusResponse>() {
            @Override
            public void onResponse(Call<TaskStatusResponse> call, Response<TaskStatusResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    TaskStatusResponse.TaskStatusData data = response.body().getData();
                    
                    // 1. 获取进度
                    callback.onProgress(data.getProgress());

                    // 2. 根据 state 判断是否完成 (1 为完成)
                    if (data.getState() == 1) {
                        List<String> videos = data.getVideos();
                        if (videos != null && !videos.isEmpty()) {
                            String rawUrl = videos.get(0);
                            // ⭐ 关键：将 URL 中的 localhost/127.0.0.1 替换为手机能访问的实际 IP
                            String finalUrl = rawUrl.replace("localhost", SERVER_IP).replace("127.0.0.1", SERVER_IP);
                            callback.onSuccess(finalUrl);
                        } else {
                            callback.onError("状态已完成但未找到视频地址");
                        }
                    } else if (data.getState() == -1) { // 假设 -1 为失败
                        callback.onError("服务器生成任务失败");
                    } else {
                        // 还在生成中，2秒后继续轮询
                        handler.postDelayed(() -> checkTaskStatus(taskId, callback), 2000);
                    }
                }
            }

            @Override
            public void onFailure(Call<TaskStatusResponse> call, Throwable t) {
                handler.postDelayed(() -> checkTaskStatus(taskId, callback), 5000);
            }
        });
    }
}
