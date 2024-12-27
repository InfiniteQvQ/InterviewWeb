package com.example.Interview.controller;

import com.example.Interview.entity.Job;
import com.example.Interview.entity.User;
import com.example.Interview.repository.JobRepository;
import com.example.Interview.repository.UserRepository;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.core.type.TypeReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/job")
public class JobController {

    private final String OPENAI_API_KEY =  System.getenv("INT_JD_KEY");
    private final String CHATGPT_API_URL = "https://api.openai.com/v1/chat/completions";

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private UserRepository userRepository;
    /**
     * 添加工作
     */
    @PostMapping("/add")
    public ResponseEntity<?> addJob(@RequestBody Job job, @RequestParam String username) {
        Map<String, Object> response = new HashMap<>();
        User user = userRepository.findByUsername(username);
        if (user == null) {
            response.put("success", false);
            response.put("message", "未登录");
            return ResponseEntity.status(401).body(response);
        }
        job.setUser(user);
        jobRepository.save(job);
        response.put("success", true);
        response.put("message", "工作添加成功");
        response.put("job", job);
        return ResponseEntity.ok(response);
    }

    /**
     * 更新工作
     */
    @PutMapping("/update")
    public ResponseEntity<?> updateJob(@RequestBody Job job, @RequestParam String username) {
        Map<String, Object> response = new HashMap<>();
        User user = userRepository.findByUsername(username);
        if (user == null) {
            response.put("success", false);
            response.put("message", "未登录");
            return ResponseEntity.status(401).body(response);
        }

        // 确认工作是否存在
        Job existingJob = jobRepository.findById(job.getId()).orElse(null);
        if (existingJob == null) {
            response.put("success", false);
            response.put("message", "工作不存在");
            return ResponseEntity.status(404).body(response);
        }

        // 确保只能更新自己的工作
        if (!existingJob.getUser().getId().equals(user.getId())) {
            response.put("success", false);
            response.put("message", "无权限更新他人的工作");
            return ResponseEntity.status(403).body(response);
        }

        job.setUser(user);
        jobRepository.save(job);
        response.put("success", true);
        response.put("message", "工作更新成功");
        response.put("job", job);
        return ResponseEntity.ok(response);
    }

    /**
     * 删除工作
     */
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteJob(@PathVariable Long id, @RequestParam String username) {
        Map<String, Object> response = new HashMap<>();
        User user = userRepository.findByUsername(username);
        if (user == null) {
            response.put("success", false);
            response.put("message", "未登录");
            return ResponseEntity.status(401).body(response);
        }

        // 确认工作是否存在
        Job existingJob = jobRepository.findById(id).orElse(null);
        if (existingJob == null) {
            response.put("success", false);
            response.put("message", "工作不存在");
            return ResponseEntity.status(404).body(response);
        }

        // 确保只能删除自己的工作
        if (!existingJob.getUser().getId().equals(user.getId())) {
            response.put("success", false);
            response.put("message", "无权限删除他人的工作");
            return ResponseEntity.status(403).body(response);
        }

        jobRepository.deleteById(id);
        response.put("success", true);
        response.put("message", "工作删除成功");
        return ResponseEntity.ok(response);
    }

    /**
     * 列出当前用户的所有工作
     */
    @GetMapping("/list")
    public ResponseEntity<?> listJobs(@RequestParam String username) {
        Map<String, Object> response = new HashMap<>();
        User user = userRepository.findByUsername(username);
        if (user == null) {
            response.put("success", false);
            response.put("message", "未登录");
            return ResponseEntity.status(401).body(response);
        }
        List<Job> jobs = jobRepository.findByUser(user);
        response.put("success", true);
        response.put("message", "工作获取成功");
        response.put("jobs", jobs);
        return ResponseEntity.ok(response);
    }



    /**
     * 利用 ChatGPT 生成 JD
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generateJobDescription(@RequestBody Map<String, Object> request, @RequestParam String username) {
        Map<String, Object> response = new HashMap<>();
        User user = userRepository.findByUsername(username);

        if (user == null) {
            response.put("success", false);
            response.put("message", "未登录");
            return ResponseEntity.status(401).body(response);
        }

        String description = (String) request.get("description");
        Object hourlyRateObj = request.get("hourlyRate");
        Double hourlyRate;

        if (hourlyRateObj instanceof Integer) {
            hourlyRate = ((Integer) hourlyRateObj).doubleValue();
        } else if (hourlyRateObj instanceof Double) {
            hourlyRate = (Double) hourlyRateObj;
        } else {
            response.put("success", false);
            response.put("message", "描述和小时薪资不能为空或格式错误！");
            return ResponseEntity.badRequest().body(response);
        }
        if (description == null || description.isEmpty() || hourlyRate == null) {
            response.put("success", false);
            response.put("message", "描述和小时薪资不能为空！");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            Map<String, String> jdData = callChatGPTToGenerateJD(description);
            
            Job job = new Job();
            job.setUser(user);
            job.setTitle(jdData.get("title"));
            job.setDescription(jdData.get("description"));
            job.setHourlyRate(hourlyRate);
            job.setApplicantsCount(0); 
            jobRepository.save(job);

            response.put("success", true);
            response.put("message", "JD 生成成功！");
            response.put("job", job);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "调用 ChatGPT 接口失败：" + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 调用 ChatGPT API 生成 JD
     */
    private Map<String, String> callChatGPTToGenerateJD(String description) throws Exception {
        // 准备请求体
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", buildPrompt(description));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "chatgpt-4o-latest");
        requestBody.put("messages", List.of(message));
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 300);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + OPENAI_API_KEY);
        headers.set("Content-Type", "application/json");

        RestTemplate restTemplate = new RestTemplate();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.exchange(
            CHATGPT_API_URL, HttpMethod.POST, entity, String.class
        );

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode responseJson = objectMapper.readTree(response.getBody());

        String content = responseJson.get("choices").get(0).get("message").get("content").asText().trim();
        if (content.startsWith("```json")) {
            content = content.substring(7).trim();
        }
        if (content.endsWith("```")) {
            content = content.substring(0, content.length() - 3).trim();
        }
        return objectMapper.readValue(content, new TypeReference<Map<String, String>>() {});
    }

    /**
     * 构建 ChatGPT 的提示
     */
    private String buildPrompt(String description) {
        return "根据以下描述生成一份岗位信息，输出严格按照 JSON 格式返回：\n" +
            "描述: \"" + description + "\"。\n" +
            "返回内容只包含以下两个字段：\n" +
            "{\n" +
            "  \"title\": \"岗位名称\",\n" +
            "  \"description\": \"岗位描述\"\n" +
            "}\n" +
            "不要返回任何其他内容，包括多余的文字或注释。岗位描述要考虑到这个岗位需要的所有能力。";
        }

}
