package com.example.Interview.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.data.domain.Pageable;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import com.example.Interview.entity.Post;
import com.example.Interview.entity.User;
import com.example.Interview.repository.PostRepository;
import com.example.Interview.repository.UserRepository;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository; // 添加 UserRepository

   


    @GetMapping
    public List<Post> getAllPosts(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        // 创建分页请求并按时间排序
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt")));
        return postRepository.findAll(pageable).getContent();
    }

    @PostMapping(consumes = {"multipart/form-data"})
    public Post createPost(
        @RequestParam("username") String username,
        @RequestParam("title") String title,
        @RequestParam("content") String content,
        @RequestParam(value = "image", required = false) MultipartFile image,
        @RequestParam(value = "com", defaultValue = "all") String com
    ) throws IOException {
        // 1. 根据 username 查找用户
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new RuntimeException("User not found with username: " + username);
        }

        // 2. 构建用户目录（~/images/<username>/）
        String rootDirectory = System.getProperty("user.home") + "/images/";
        String userDirectory = rootDirectory + user.getUsername() + "/";
        File uploadDir = new File(userDirectory);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        // 3. 如果有上传文件，则保存文件，并生成可访问的URL
        String imageUrl = null;
        if (image != null && !image.isEmpty()) {
            String originalFilename = image.getOriginalFilename();
            String extension = "";
            int dotIndex = originalFilename.lastIndexOf(".");
            if (dotIndex != -1) {
                extension = originalFilename.substring(dotIndex);
            }

            // 生成唯一文件名(时间戳 + 随机数 + 后缀)
            String uniqueFileName = System.currentTimeMillis() 
                    + "_" + (int)(Math.random()*10000) 
                    + extension;

            // 完整的本地磁盘路径
            String filePath = userDirectory + uniqueFileName;
            // 写入磁盘
            image.transferTo(new File(filePath));

        
            imageUrl = "/images/" + user.getUsername() + "/" + uniqueFileName;
        }

        // 4. 创建帖子对象
        Post post = new Post();
        post.setUser(user); 
        post.setUsername(user.getUsername());
        post.setTitle(title);
        post.setContent(content);
        post.setImagePath(imageUrl); 
        post.setCom(com);
        post.setLikes(0);

        // 5. 保存数据库
        return postRepository.save(post);
    }


    @PutMapping("/{id}")
    public Post updatePost(@PathVariable Long id, @RequestBody Post postDetails) {
        return postRepository.findById(id)
                .map(post -> {
                    post.setTitle(postDetails.getTitle());
                    post.setContent(postDetails.getContent());
                    post.setImagePath(postDetails.getImagePath());
                    post.setLikes(postDetails.getLikes());
                    return postRepository.save(post);
                })
                .orElseThrow(() -> new RuntimeException("Post not found with id " + id));
    }

    @DeleteMapping("/{id}")
    public void deletePost(@PathVariable Long id) {
        postRepository.deleteById(id);
    }
}
