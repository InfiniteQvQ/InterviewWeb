package com.example.Interview.repository;

import com.example.Interview.entity.Work;
import com.example.Interview.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;


public interface WorkRepository extends JpaRepository<Work, Long> {
    List<Work> findByUser(User user);
}