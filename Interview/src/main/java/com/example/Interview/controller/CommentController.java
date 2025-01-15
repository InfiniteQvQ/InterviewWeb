package com.example.Interview.controller;

import com.example.Interview.entity.Comment;
import com.example.Interview.repository.CommentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/comments")
public class CommentController {

    @Autowired
    private CommentRepository commentRepository;

    // 获取某个帖子的所有评论
    @GetMapping("/post/{postId}")
    public List<Comment> getCommentsByPostId(@PathVariable Long postId) {
        return commentRepository.findByPost_PostId(postId);
    }

    // 创建评论
   @PostMapping
    public Comment createComment(@RequestBody Comment comment) {
        // Find the user by username
        User user = userRepository.findByUsername(comment.getUsername());
        if (user == null) {
            throw new RuntimeException("User not found with username: " + comment.getUsername());
        }

        // Set the user and post for the comment
        comment.setUser(user);
        comment.setPost(postRepository.findById(comment.getPost().getPostId())
                            .orElseThrow(() -> new RuntimeException("Post not found")));

        return commentRepository.save(comment);
    }

    // 更新评论
    @PutMapping("/{id}")
    public Comment updateComment(@PathVariable Long id, @RequestBody Comment commentDetails) {
        return commentRepository.findById(id)
                .map(comment -> {
                    comment.setContent(commentDetails.getContent());
                    return commentRepository.save(comment);
                })
                .orElseThrow(() -> new RuntimeException("Comment not found with id " + id));
    }

    // 删除评论
    @DeleteMapping("/{id}")
    public void deleteComment(@PathVariable Long id) {
        commentRepository.deleteById(id);
    }
}
