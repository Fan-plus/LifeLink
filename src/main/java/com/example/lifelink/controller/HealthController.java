package com.example.lifelink.controller;

import com.example.lifelink.entity.HealthData;
import com.example.lifelink.service.HealthDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    @Autowired
    private HealthDataService healthDataService;

    @PostMapping("/upload")
    public ResponseEntity<HealthData> upload(@RequestBody HealthData data) {
        return ResponseEntity.ok(healthDataService.uploadData(data));
    }

    @GetMapping("/latest")
    public ResponseEntity<HealthData> getLatest(@RequestParam Long userId) {
        return healthDataService.getLatestData(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
