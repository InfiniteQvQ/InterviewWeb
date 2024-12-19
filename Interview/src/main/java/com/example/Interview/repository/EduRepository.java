package com.example.Interview.repository;

import com.example.Interview.entity.Edu;
import com.example.Interview.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;


public interface EduRepository extends JpaRepository<Edu, Long> {
    List<Edu> findByUser(User user);
}