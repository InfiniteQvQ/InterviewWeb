package com.example.Interview.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;


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
    
    private final String OPENAI_API_KEY = System.getenv("INT_Search_KEY");
    private final String CHATGPT_API_URL = "https://api.openai.com/v1/chat/completions";
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


    @PostMapping("/filter")
    public ResponseEntity<?> filterCandidates(@RequestBody Map<String, Object> filters, @RequestParam(defaultValue = "false") boolean includeDescription) {
        Map<String, String> degreeMapping = new HashMap<>();
        degreeMapping.put("bachelors", "学士");
        degreeMapping.put("bachelor of science", "学士");
        degreeMapping.put("bachelor of arts", "学士");
        degreeMapping.put("学士", "学士");
        degreeMapping.put("本科", "学士");
        System.out.println("Received filters: " + filters);
        
        String skills = (String) filters.get("skills");
        String company = filters.get("company") != null ? filters.get("company").toString() : null;
        String position = (String) filters.get("position");
        String field = (String) filters.get("field");
        String degree = (String) filters.get("degree");
        String university = (String) filters.get("university");
        String workType = (String) filters.getOrDefault("workType", "不限");
        String startDate = (String) filters.get("startDate");
        Integer minSalary = 0;
        Integer maxSalary = Integer.MAX_VALUE;
        String standardizedDegree = degree != null 
            ? degreeMapping.getOrDefault(degree.toLowerCase(), degree.toLowerCase()) 
            : null;
        try {
            if (filters.get("minSalary") != null) {
                minSalary = Integer.parseInt(filters.get("minSalary").toString());
            }
            if (filters.get("maxSalary") != null) {
                maxSalary = Integer.parseInt(filters.get("maxSalary").toString());
            }
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body("薪资过滤器的格式不正确！");
        }

        final Integer finalMinSalary = minSalary;
        final Integer finalMaxSalary = maxSalary;

        List<User> filteredUsers = userRepository.findAll().stream()
            .filter(user -> {
                Profile profile = profileRepository.findByUser(user);
                if (profile == null) return false;

                if (includeDescription && field != null && !field.isEmpty()) {
                    String description = profile.getDescription();
                    if (description != null && description.toLowerCase().contains(field.toLowerCase())) {
                        return true; 
                    }
                }

                // 筛选工作性质
                if (workType != null && !workType.isEmpty() && !"不限".equalsIgnoreCase(workType)) {
                    if (profile.getIsFullTime() && !"Full-time or Part-time".equalsIgnoreCase(workType)) {
                        return false;
                    }
                    if (!profile.getIsFullTime() && "Full-time or Part-time".equalsIgnoreCase(workType)) {
                        return false;
                    }
                }

                // 筛选薪资
                try {
                    String salaryStr = profile.getExpectedSalary();
                    int expectedSalary = salaryStr != null ? Integer.parseInt(salaryStr) : 0;
                    expectedSalary = expectedSalary/12;
                    if (expectedSalary < finalMinSalary || expectedSalary > finalMaxSalary) {
                        return false;
                    }
                } catch (NumberFormatException e) {
        
                    return false;
                }

                // 筛选技能
                if (skills != null && !skills.isEmpty()) {
                    List<Skill> userSkills = skillRepository.findByUser(user);
                    if (userSkills == null || userSkills.stream().noneMatch(skill ->
                        skills.toLowerCase().contains(skill.getSkillName().toLowerCase()) ||
                        skill.getSkillName().toLowerCase().contains(skills.toLowerCase()))) {
                        return false;
                    }
                }

                // 筛选教育
                if (standardizedDegree != null && !standardizedDegree.isEmpty()) {
                    List<Edu> education = eduRepository.findByUser(user);
                    if (education == null || education.stream().noneMatch(edu -> {
                        String dbDegree = degreeMapping.getOrDefault(edu.getDegree().toLowerCase(), edu.getDegree().toLowerCase());
                        return dbDegree.equals(standardizedDegree);
                    })) {
                        return false;
                    }
                }

                // 筛选公司和职位
                if (company != null && !company.isEmpty()) {
                    List<Work> workExperience = workRepository.findByUser(user);
                    if (workExperience == null || workExperience.stream().noneMatch(work ->
                        work.getCompanyName().toLowerCase().contains(company.toLowerCase()) || 
                        company.toLowerCase().contains(work.getCompanyName().toLowerCase())
                    )) {
                        return false; 
                    }
                }

                if (position != null && !position.isEmpty()) {
                    List<Work> workExperience = workRepository.findByUser(user);
                    if (workExperience == null || workExperience.stream().noneMatch(work ->
                        work.getPosition().toLowerCase().contains(position.toLowerCase()) || 
                        position.toLowerCase().contains(work.getPosition().toLowerCase())
                    )) {
                        return false; 
                    }
                }

                

                return true;
            })
            .collect(Collectors.toList());

        // 构造结果
        List<Map<String, Object>> result = filteredUsers.stream()
            .map(this::constructUserInfo)
            .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }


    @PostMapping("/searchai")
    public ResponseEntity<?> searchCandidates(@RequestBody Map<String, Object> request) {
        String query = (String) request.get("query");
        if (query == null || query.isEmpty()) {
            return ResponseEntity.badRequest().body("查询内容不能为空！");
        }

        try {
      
            Map<String, Object> filters = callChatGPTToExtractRelevantData(query);
            return filterCandidates(filters, true);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("调用 ChatGPT 接口失败：" + e.getMessage());
        }

    }

    public Map<String, Object> callChatGPTToExtractRelevantData(String query) throws Exception{
        
        Map<String, Object> message = new HashMap<>();
        message.put("role", "system");
        message.put("content", buildPrompt(query));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "chatgpt-4o-latest");
        requestBody.put("messages", List.of(message));
        requestBody.put("temperature", 0.0); 
        requestBody.put("max_tokens", 150);

         HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + OPENAI_API_KEY);
        headers.set("Content-Type", "application/json");

        // 发送请求
        RestTemplate restTemplate = new RestTemplate();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.exchange(
            CHATGPT_API_URL, HttpMethod.POST, entity, String.class
        );
        System.out.println("API Response: " + response.getBody());
        // 解析响应
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode responseJson = objectMapper.readTree(response.getBody());

        // 获取嵌套的 JSON 字符串
        String nestedJsonContent = responseJson.get("choices").get(0).get("message").get("content").asText().trim();

        // 去掉 ```json 和 ```
        if (nestedJsonContent.startsWith("```json")) {
            nestedJsonContent = nestedJsonContent.substring(7).trim();
        }
        if (nestedJsonContent.endsWith("```")) {
            nestedJsonContent = nestedJsonContent.substring(0, nestedJsonContent.length() - 3).trim();
        }

        // 将嵌套 JSON 转换为 Map
        return objectMapper.readValue(nestedJsonContent, Map.class);
    }

    private String buildPrompt(String query) {
        return "Analyze the following job search query and organize it into JSON format. " +
           "Do not infer or assume any information beyond what is explicitly mentioned in the query. " +
           "Input: \"" + query + "\". " +
           "The output must strictly include the following keys: " +
           "skills, company, position, field, degree, university, workType, minSalary, maxSalary. " +
           "For the 'workType' key, use only one of these values: '不限', 'Full-time or Part-time', 'Part-time Only'. " +
           "For the 'degree' key, use only one of these values: '不限', '本科', '硕士', '博士'. " +
           "Leave any key as null if it is not explicitly mentioned in the query.";
    }
}
