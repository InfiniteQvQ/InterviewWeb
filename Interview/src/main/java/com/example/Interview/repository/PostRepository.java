package com.example.Interview.repository;
import com.example.Interview.entity.Post;
import com.example.Interview.repository.PostRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
}
