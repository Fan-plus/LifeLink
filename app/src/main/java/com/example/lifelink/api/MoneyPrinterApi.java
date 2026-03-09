package com.example.lifelink.api;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface MoneyPrinterApi {

    @POST("/api/v1/videos")
    Call<TaskResponse> generateVideo(@Body VideoRequest request);

    @GET("/api/v1/tasks/{taskId}")
    Call<TaskStatusResponse> getTaskStatus(@Path("taskId") String taskId);
}
