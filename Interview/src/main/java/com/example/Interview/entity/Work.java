package com.example.Interview.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "work")
@Data
public class Work {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String companyName;
    private String position;

    @Column(columnDefinition = "TEXT")
    private String description;
}
