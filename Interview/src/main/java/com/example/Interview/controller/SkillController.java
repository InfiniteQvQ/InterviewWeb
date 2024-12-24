package com.example.Interview.controller;

import com.example.Interview.entity.Skill;
import com.example.Interview.entity.User;
import com.example.Interview.repository.SkillRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/skills")
public class SkillController {

    @Autowired
    private SkillRepository skillRepository;

    /**
     * 添加技能
     */
    @PostMapping("/add")
    public ResponseEntity<?> addSkill(@RequestBody Skill skill, HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            response.put("success", false);
            response.put("message", "未登录");
            return ResponseEntity.status(401).body(response);
        }
        skill.setUser(user);
        skillRepository.save(skill);
        response.put("success", true);
        response.put("message", "技能添加成功");
        response.put("skill", skill);
        return ResponseEntity.ok(response);
    }

    /**
     * 更新技能
     */
    @PutMapping("/update")
    public ResponseEntity<?> updateSkill(@RequestBody Skill skill, HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            response.put("success", false);
            response.put("message", "未登录");
            return ResponseEntity.status(401).body(response);
        }

        // 确认技能是否存在
        Skill existingSkill = skillRepository.findById(skill.getId()).orElse(null);
        if (existingSkill == null) {
            response.put("success", false);
            response.put("message", "技能不存在");
            return ResponseEntity.status(404).body(response);
        }

        // 确保只能更新自己的技能
        if (!existingSkill.getUser().getId().equals(user.getId())) {
            response.put("success", false);
            response.put("message", "无权限更新他人的技能");
            return ResponseEntity.status(403).body(response);
        }

        skill.setUser(user);
        skillRepository.save(skill);
        response.put("success", true);
        response.put("message", "技能更新成功");
        response.put("skill", skill);
        return ResponseEntity.ok(response);
    }

    /**
     * 删除技能
     */
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteSkill(@PathVariable Long id, HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            response.put("success", false);
            response.put("message", "未登录");
            return ResponseEntity.status(401).body(response);
        }

        // 确认技能是否存在
        Skill existingSkill = skillRepository.findById(id).orElse(null);
        if (existingSkill == null) {
            response.put("success", false);
            response.put("message", "技能不存在");
            return ResponseEntity.status(404).body(response);
        }

        // 确保只能删除自己的技能
        if (!existingSkill.getUser().getId().equals(user.getId())) {
            response.put("success", false);
            response.put("message", "无权限删除他人的技能");
            return ResponseEntity.status(403).body(response);
        }

        skillRepository.deleteById(id);
        response.put("success", true);
        response.put("message", "技能删除成功");
        return ResponseEntity.ok(response);
    }

    /**
     * 列出当前用户的所有技能
     */
    @GetMapping("/list")
    public ResponseEntity<?> listSkills(HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            response.put("success", false);
            response.put("message", "未登录");
            return ResponseEntity.status(401).body(response);
        }
        List<Skill> skills = skillRepository.findByUserId(user.getId());
        response.put("success", true);
        response.put("message", "技能获取成功");
        response.put("skills", skills);
        return ResponseEntity.ok(response);
    }
}
