package com.example.lifelink.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "relationships")
public class Relationship {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long elderlyId;

    private Long familyId;

    private String bindingCode;
}
