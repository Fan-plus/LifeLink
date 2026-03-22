package com.example.lifelink.repository;

import com.example.lifelink.entity.Checkin;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CheckinRepository extends JpaRepository<Checkin, Long> {
    List<Checkin> findByUserIdOrderByTimestampDesc(Long userId);
}
