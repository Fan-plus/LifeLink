package com.example.lifelink.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    private String phone;

    private String openId;
    private String deviceToken;

    @Enumerated(EnumType.STRING)
    private Role role; // ELDERLY, FAMILY

    public enum Role {
        ELDERLY, FAMILY
    }
}
