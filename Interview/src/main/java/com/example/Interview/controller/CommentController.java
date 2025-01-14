
package com.example.Interview.controller;
import com.example.Interview.entity.*;
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

    @GetMapping
    public List<Comment> getAllComments() {
        return commentRepository.findAll();
    }

    @PostMapping
    public Comment createComment(@RequestBody Comment comment) {
        return commentRepository.save(comment);
    }

    @PutMapping("/{id}")
    public Comment updateComment(@PathVariable Long id, @RequestBody Comment commentDetails) {
        return commentRepository.findById(id)
                .map(comment -> {
                    comment.setContent(commentDetails.getContent());
                    return commentRepository.save(comment);
                })
                .orElseThrow(() -> new RuntimeException("Comment not found with id " + id));
    }

    @DeleteMapping("/{id}")
    public void deleteComment(@PathVariable Long id) {
        commentRepository.deleteById(id);
    }
}
