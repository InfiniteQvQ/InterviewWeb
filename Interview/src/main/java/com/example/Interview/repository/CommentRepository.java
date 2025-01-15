package com.example.Interview.repository;

import com.example.Interview.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    // 根据 Post 的 ID 查找评论
    public List<Comment> findByPost_Id(Long postId);
}
