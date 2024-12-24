package com.example.Interview.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.util.Date;
@Entity
@Table(name = "edu")
@Data
public class Edu {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String schoolName;
    private String degree;
    private String major;

    @Column(name = "start_date", nullable = false)
    private Date startDate;

    @Column(name = "end_date")
    private Date endDate;
}
