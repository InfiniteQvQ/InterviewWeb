package com.example.Interview.controller;

import com.example.Interview.entity.Edu;
import com.example.Interview.entity.User;
import com.example.Interview.repository.EduRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/edu")
public class EduController {

    @Autowired
    private EduRepository eduRepository;

    @PostMapping("/add")
    public ResponseEntity<?> addEdu(@RequestBody Edu edu, HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            response.put("success", false);
            response.put("message", "未登录");
            return ResponseEntity.status(401).body(response);
        }
        edu.setUser(user);
        eduRepository.save(edu);
        response.put("success", true);
        response.put("message", "学历信息添加成功");
        response.put("edu", edu);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/update")
    public ResponseEntity<?> updateEdu(@RequestBody Edu edu, HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            response.put("success", false);
            response.put("message", "未登录");
            return ResponseEntity.status(401).body(response);
        }
        edu.setUser(user);
        eduRepository.save(edu);
        response.put("success", true);
        response.put("message", "学历信息更新成功");
        response.put("edu", edu);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteEdu(@PathVariable Long id, HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            response.put("success", false);
            response.put("message", "未登录");
            return ResponseEntity.status(401).body(response);
        }
        Edu edu = eduRepository.findById(id).orElse(null);
        if (edu == null) {
            response.put("success", false);
            response.put("message", "学历信息不存在");
            return ResponseEntity.status(404).body(response);
        }
        if (!edu.getUser().getId().equals(user.getId())) {
            response.put("success", false);
            response.put("message", "无权限删除他人的学历信息");
            return ResponseEntity.status(403).body(response);
        }
        eduRepository.deleteById(id);
        response.put("success", true);
        response.put("message", "学历信息删除成功");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/list")
    public ResponseEntity<?> listEdu(HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            response.put("success", false);
            response.put("message", "未登录");
            return ResponseEntity.status(401).body(response);
        }
        List<Edu> eduList = eduRepository.findByUser(user);
        response.put("success", true);
        response.put("message", "学历信息获取成功");
        response.put("eduList", eduList);
        return ResponseEntity.ok(response);
    }
}
