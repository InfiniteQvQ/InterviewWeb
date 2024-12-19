package com.example.Interview.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.sql.Timestamp;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String pwd;

    @Column(unique = true, nullable = false)
    private String email;

    private Timestamp createdAt = new Timestamp(System.currentTimeMillis());
}
