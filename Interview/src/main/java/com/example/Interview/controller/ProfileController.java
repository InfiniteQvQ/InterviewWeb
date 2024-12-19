package com.example.Interview.controller;

import com.example.Interview.entity.Profile;
import com.example.Interview.entity.User;
import com.example.Interview.repository.ProfileRepository;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/profiles")
public class ProfileController {

    @Autowired
    private ProfileRepository profileRepository;

    /**
     * 新增或更新用户的个人信息
     */
    @PostMapping("/save")
    @Transactional
    public ResponseEntity<?> saveProfile(@RequestBody @Valid Profile profile, HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            response.put("success", false);
            response.put("message", "未登录");
            return ResponseEntity.status(401).body(response);
        }

        Profile existingProfile = profileRepository.findByUser(user);
        if (existingProfile != null) {
            // 更新操作
            if (profile.getPhone() != null) {
                existingProfile.setPhone(profile.getPhone());
            }
            if (profile.getGender() != null) {
                existingProfile.setGender(profile.getGender());
            }
            if (profile.getAge() != null) {
                existingProfile.setAge(profile.getAge());
            }
            if (profile.getLocation() != null) {
                existingProfile.setLocation(profile.getLocation());
            }
            if (profile.getExpectedSalary() != null) {
                existingProfile.setExpectedSalary(profile.getExpectedSalary());
            }
            profileRepository.save(existingProfile);
            response.put("success", true);
            response.put("message", "个人信息更新成功");
            response.put("profile", existingProfile);
            return ResponseEntity.ok(response);
        } else {
            // 新增操作
            profile.setUser(user);
            profileRepository.save(profile);
            response.put("success", true);
            response.put("message", "个人信息保存成功");
            response.put("profile", profile);
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 获取当前用户的个人信息
     */
    @GetMapping("/get")
    public ResponseEntity<?> getProfile(HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            response.put("success", false);
            response.put("message", "未登录");
            return ResponseEntity.status(401).body(response);
        }

        Profile profile = profileRepository.findByUser(user);
        if (profile == null) {
            response.put("success", false);
            response.put("message", "未找到个人信息");
            return ResponseEntity.status(404).body(response);
        }

        response.put("success", true);
        response.put("message", "个人信息获取成功");
        response.put("profile", profile);
        return ResponseEntity.ok(response);
    }

    /**
     * 删除当前用户的 Profile 信息
     */
    @DeleteMapping("/delete")
    @Transactional
    public ResponseEntity<?> deleteProfile(HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        User user = (User) session.getAttribute("user");
        if (user == null) {
            response.put("success", false);
            response.put("message", "未登录");
            return ResponseEntity.status(401).body(response);
        }

        Profile profile = profileRepository.findByUser(user);
        if (profile == null) {
            response.put("success", false);
            response.put("message", "未找到个人信息");
            return ResponseEntity.status(404).body(response);
        }

        profileRepository.delete(profile);
        response.put("success", true);
        response.put("message", "个人信息删除成功");
        return ResponseEntity.ok(response);
    }
}
