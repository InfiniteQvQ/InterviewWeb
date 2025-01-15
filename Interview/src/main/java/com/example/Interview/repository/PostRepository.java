package com.example.Interview.repository;
import com.example.Interview.entity.Post;
import com.example.Interview.repository.PostRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.Interview.entity.Post;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
     Page<Post> findAll(Pageable pageable);
}
