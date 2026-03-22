package com.example.lifelink.repository;

import com.example.lifelink.entity.HealthData;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface HealthDataRepository extends JpaRepository<HealthData, Long> {
    Optional<HealthData> findTopByUserIdOrderByTimestampDesc(Long userId);
    List<HealthData> findByUserIdOrderByTimestampDesc(Long userId);
}
