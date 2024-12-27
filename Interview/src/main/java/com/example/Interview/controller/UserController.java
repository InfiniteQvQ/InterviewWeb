package com.example.Interview.controller;

import com.example.Interview.entity.User;
import com.example.Interview.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        Map<String, Object> response = new HashMap<>();
        if (userRepository.findByUsername(user.getUsername()) != null) {
            response.put("success", false);
            response.put("message", "用户名已存在");
            return ResponseEntity.badRequest().body(response);
        }
        userRepository.save(user);
        response.put("success", true);
        response.put("message", "注册成功");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody User loginUser) {
        Map<String, Object> response = new HashMap<>();
        User user = userRepository.findByUsername(loginUser.getUsername());
        if (user == null || !user.getPwd().equals(loginUser.getPwd())) {
            response.put("success", false);
            response.put("message", "用户名或密码错误");
            return ResponseEntity.status(401).body(response);
        }
      
        response.put("success", true);
        response.put("message", "登录成功");
        response.put("user", user);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/logout")
    public ResponseEntity<?> logout(@RequestParam String username) {
        Map<String, Object> response = new HashMap<>();
        User user = userRepository.findByUsername(username);
        if (user == null) {
            response.put("success", false);
            response.put("message", "用户不存在或未登录");
            return ResponseEntity.status(404).body(response);
        }

        response.put("success", true);
        response.put("message", "登出成功");
        return ResponseEntity.ok(response);
    }
}
