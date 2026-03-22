package com.example.lifelink.service;

import com.example.lifelink.entity.HealthData;
import com.example.lifelink.repository.HealthDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class HealthDataService {
    @Autowired
    private HealthDataRepository healthDataRepository;

    @Autowired
    private AlertService alertService;

    public HealthData uploadData(HealthData data) {
        HealthData saved = healthDataRepository.save(data);
        alertService.checkHealth(saved);
        return saved;
    }

    public Optional<HealthData> getLatestData(Long userId) {
        return healthDataRepository.findTopByUserIdOrderByTimestampDesc(userId);
    }
}
