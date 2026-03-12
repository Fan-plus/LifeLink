package com.example.lifelink.api;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface MoneyPrinterApi {

    @POST("/api/v1/videos")
    Call<TaskResponse> generateVideo(@Body VideoRequest request);

    @GET("/api/v1/tasks/{taskId}")
    Call<TaskStatusResponse> getTaskStatus(@Path("taskId") String taskId);

    /**
     * 调用云端 OpenAI 兼容接口 (DeepSeek/Qwen/GPT)
     * @param apiKey 格式为 "Bearer YOUR_TOKEN"
     * @param request 对话请求体
     * @return 返回标准对话响应
     */
    @POST("chat/completions")
    Call<ChatCompletionResponse> chatCompletions(
            @Header("Authorization") String apiKey,
            @Body ChatCompletionRequest request
    );
}
