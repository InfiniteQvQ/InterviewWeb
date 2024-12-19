package com.example.Interview.controller;

import com.example.Interview.entity.Work;
import com.example.Interview.entity.User;
import com.example.Interview.repository.WorkRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/work")
public class WorkController {

    @Autowired
    private WorkRepository workRepository;

    /**
     * 添加工作经历
     */
    @PostMapping("/add")
    public ResponseEntity<?> addWork(@RequestBody Work work, HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            response.put("success", false);
            response.put("message", "未登录");
            return ResponseEntity.status(401).body(response);
        }
        work.setUser(user);
        workRepository.save(work);
        response.put("success", true);
        response.put("message", "工作经历添加成功");
        response.put("work", work);
        return ResponseEntity.ok(response);
    }

    /**
     * 更新工作经历
     */
    @PutMapping("/update")
    public ResponseEntity<?> updateWork(@RequestBody Work work, HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            response.put("success", false);
            response.put("message", "未登录");
            return ResponseEntity.status(401).body(response);
        }

        // 确认工作经历是否存在
        Work existingWork = workRepository.findById(work.getId()).orElse(null);
        if (existingWork == null) {
            response.put("success", false);
            response.put("message", "工作经历不存在");
            return ResponseEntity.status(404).body(response);
        }

        // 确保只能更新自己的工作经历
        if (!existingWork.getUser().getId().equals(user.getId())) {
            response.put("success", false);
            response.put("message", "无权限更新他人的工作经历");
            return ResponseEntity.status(403).body(response);
        }

        work.setUser(user);
        workRepository.save(work);
        response.put("success", true);
        response.put("message", "工作经历更新成功");
        response.put("work", work);
        return ResponseEntity.ok(response);
    }

    /**
     * 删除工作经历
     */
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteWork(@PathVariable Long id, HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            response.put("success", false);
            response.put("message", "未登录");
            return ResponseEntity.status(401).body(response);
        }

        // 确认工作经历是否存在
        Work existingWork = workRepository.findById(id).orElse(null);
        if (existingWork == null) {
            response.put("success", false);
            response.put("message", "工作经历不存在");
            return ResponseEntity.status(404).body(response);
        }

        // 确保只能删除自己的工作经历
        if (!existingWork.getUser().getId().equals(user.getId())) {
            response.put("success", false);
            response.put("message", "无权限删除他人的工作经历");
            return ResponseEntity.status(403).body(response);
        }

        workRepository.deleteById(id);
        response.put("success", true);
        response.put("message", "工作经历删除成功");
        return ResponseEntity.ok(response);
    }

    /**
     * 列出当前用户的所有工作经历
     */
    @GetMapping("/list")
    public ResponseEntity<?> listWork(HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            response.put("success", false);
            response.put("message", "未登录");
            return ResponseEntity.status(401).body(response);
        }
        List<Work> works = workRepository.findByUser(user);
        response.put("success", true);
        response.put("message", "工作经历获取成功");
        response.put("works", works);
        return ResponseEntity.ok(response);
    }
}
