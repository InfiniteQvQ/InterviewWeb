package com.example.Interview.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.example.Interview.entity.Edu;
import com.example.Interview.entity.User;
import com.example.Interview.entity.Profile;
import com.example.Interview.entity.Work;
import com.example.Interview.entity.Skill;

import com.example.Interview.repository.EduRepository;
import com.example.Interview.repository.UserRepository;
import com.example.Interview.repository.ProfileRepository;
import com.example.Interview.repository.WorkRepository;
import com.example.Interview.repository.SkillRepository;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private WorkRepository workRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private EduRepository eduRepository;
    
    /**
     * 获取初始的用户列表（仅包含有 Profile 数据的用户）
     */
    @GetMapping("/list")
    public ResponseEntity<?> getInitialUsers() {
        System.out.println("Starting /api/search/list");
        List<User> usersWithProfile = userRepository.findAll().stream()
                .filter(user -> profileRepository.findByUser(user) != null)
                .collect(Collectors.toList());

        // 构造用户信息列表
        List<Map<String, Object>> usersList = usersWithProfile.stream()
                .map(this::constructUserInfo) 
                .collect(Collectors.toList());

        return ResponseEntity.ok(usersList);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getUserDetails(@PathVariable Long id) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body("用户未找到");
        }

        Profile profile = profileRepository.findByUser(user);
        List<Work> workExperience = workRepository.findByUser(user);
        List<Skill> skills = skillRepository.findByUser(user);
        List<Edu> education = eduRepository.findByUser(user);
        int exp = calculateTotalExperience(user);
        Map<String, Object> userDetails = new HashMap<>();
        userDetails.put("profile", profile);
        userDetails.put("workExperience", workExperience);
        userDetails.put("skills", skills);
        userDetails.put("education", education);
        userDetails.put("exp", exp);
        return ResponseEntity.ok(userDetails);
    }

    /**
     * 构造单个用户的信息
     */
    private Map<String, Object> constructUserInfo(User user) {
        Map<String, Object> userInfo = new HashMap<>();

        // 获取用户的 Profile
        Profile profile = profileRepository.findByUser(user);

        int totalExperience = calculateTotalExperience(user);

        List<Skill> skills = skillRepository.findByUser(user);

        userInfo.put("id", user.getId());
        userInfo.put("name", user.getUsername());
        userInfo.put("experience", totalExperience);
        userInfo.put("description", profile.getDescription());
        userInfo.put("skills", skills.stream().map(Skill::getSkillName).collect(Collectors.toList()));
        userInfo.put("isFullTime", profile.getIsFullTime());
        userInfo.put("imageUrl", profile.getImageUrl());
        

        return userInfo;
    }

    /**
     * 计算用户的工作总经验
     */
    private int calculateTotalExperience(User user) {
        List<Work> workExperience = workRepository.findByUser(user);

        return workExperience.stream()
                .filter(work -> work.getStartDate() != null && work.getEndDate() != null)
                .mapToInt(work -> {
                    long durationInMillis = work.getEndDate().getTime() - work.getStartDate().getTime();
                    return (int) (durationInMillis / (1000L * 60 * 60 * 24 * 365));
                })
                .sum();
    }
}
