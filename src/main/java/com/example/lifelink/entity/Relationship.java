package com.example.lifelink.entity;

import jakarta.persistence.*;

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

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getElderlyId() {
        return elderlyId;
    }

    public void setElderlyId(Long elderlyId) {
        this.elderlyId = elderlyId;
    }

    public Long getFamilyId() {
        return familyId;
    }

    public void setFamilyId(Long familyId) {
        this.familyId = familyId;
    }

    public String getBindingCode() {
        return bindingCode;
    }

    public void setBindingCode(String bindingCode) {
        this.bindingCode = bindingCode;
    }
}
