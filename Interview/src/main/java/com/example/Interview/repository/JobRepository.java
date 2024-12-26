package com.example.Interview.repository;

import com.example.Interview.entity.Job;
import com.example.Interview.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobRepository extends JpaRepository<Job, Long> {
    List<Job> findByUser(User user);
}
