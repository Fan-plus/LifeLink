package com.example.lifelink.repository;

import com.example.lifelink.entity.Relationship;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface RelationshipRepository extends JpaRepository<Relationship, Long> {
    Optional<Relationship> findByBindingCode(String bindingCode);
    List<Relationship> findByElderlyId(Long elderlyId);
    List<Relationship> findByFamilyId(Long familyId);
}
