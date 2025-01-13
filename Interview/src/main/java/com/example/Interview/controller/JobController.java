package com.example.Interview.controller;

import com.example.Interview.entity.Job;
import com.example.Interview.entity.User;
import com.example.Interview.repository.JobRepository;
import com.example.Interview.repository.UserRepository;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseExtractor;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.concurrent.CompletableFuture;

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
    //private final String CHAT_KEY = System.getenv("INT_CHAT_KEY");
    private final String CHAT_KEY = System.getenv("INT_FG_CHAT_KEY");
    private final String FASTGPT_CHATGPT_API_URL = "https://api.fastgpt.in/api/v1/chat/completions";
    

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
            
            Map<String, Object> job = new HashMap<>();
            job.put("title", jdData.get("title"));
            job.put("description", jdData.get("description"));
            job.put("requirement", jdData.get("requirement"));
            job.put("hourlyRate", hourlyRate);

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
            "返回内容只包含以下三个字段：\n" +
            "{\n" +
            "  \"title\": \"岗位名称\",\n" +
            "  \"description\": \"岗位描述\"\n" +
            "  \"requirement\": \"任职要求\"\n" +
            "}\n" +
            "不要返回任何其他内容，包括多余的文字或注释。岗位描述要考虑到这个岗位需要实现的内容，相当于Overview，任职要求要考虑到这个岗位需要的所有能力";
        }

    
    @PostMapping("/adjust")
    public ResponseEntity<?> adjustJobField(@RequestBody Map<String, String> request, @RequestParam String username) {
        Map<String, Object> response = new HashMap<>();
        User user = userRepository.findByUsername(username);

        if (user == null) {
            response.put("success", false);
            response.put("message", "未登录");
            return ResponseEntity.status(401).body(response);
        }

        String field = request.get("field");
        String originalContent = request.get("originalContent");
        String improvementPoints = request.get("improvementPoints");

        if (field == null || originalContent == null || improvementPoints == null || improvementPoints.isEmpty()) {
            response.put("success", false);
            response.put("message", "字段名、原始内容和改进点不能为空！");
            return ResponseEntity.badRequest().body(response);
        }

        if (!field.equals("description") && !field.equals("requirement")) {
            response.put("success", false);
            response.put("message", "仅支持调整 description 或 requirement 字段！");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            // 调用 ChatGPT 生成新的字段内容
            String adjustedContent = callChatGPTToAdjustField(field, originalContent, improvementPoints);

            response.put("success", true);
            response.put("message", "字段调整成功！");
            response.put("adjustedContent", adjustedContent);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "调用 ChatGPT 接口失败：" + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    private String callChatGPTToAdjustField(String field, String originalContent, String improvementPoints) throws Exception {
        // 准备请求体
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", buildFieldAdjustmentPrompt(field, originalContent, improvementPoints));

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
        if (content.startsWith("\"")) {
            content = content.substring(1).trim();
        }
        if (content.endsWith("\"")) {
            content = content.substring(0, content.length() - 1).trim();
        }

        return content;
    }

    private String buildFieldAdjustmentPrompt(String field, String originalContent, String improvementPoints) {
        String fieldDescription = field.equals("overview") 
            ? "这是岗位的概述内容，它需要描述岗位的主要职责和目标" 
            : "这是岗位的任职要求，它需要描述岗位所需的技能和资格";

        return "以下是岗位字段的原始内容和用户的改进点，请结合两者生成新的字段信息，主要侧重用户想更改的内容：" +
               "\n字段类型: " + fieldDescription +
               "\n原始内容: \"" + originalContent + "\"" +
               "\n改进点: \"" + improvementPoints + "\"" +
               "\n请根据上述内容生成更新后的字段信息，输出严格按照文本格式返回, 文字只包含新的字段类型所应该拥有的信息,不要包含任何额外注释或解释!";
    }

    

    @GetMapping(value = "/chatbot", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter queryChatbot(@RequestParam("query") String userQuery) {
        SseEmitter emitter = new SseEmitter();
        CompletableFuture.runAsync(() -> {
            try {
                callChatGPTBot(userQuery, emitter); // 调用 ChatGPT 流式方法
            } catch (Exception e) {
                emitter.completeWithError(e); // 异常处理
            }
        });
        return emitter;
    }


    private void callChatGPTBot(String userQuery , SseEmitter emitter)  throws Exception {
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", userQuery);

        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "你是一个智能职业规划和职业发展咨询助手，请针对用户提出的问题，作出符合职业规划以及发展的回答，回答在400个token左右，输出严格按照文本格式返回, 文字只包含回答信息,不要包含任何额外注释或解释!");
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(systemMessage);
        messages.add(userMessage);
        // 请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "");
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 600);
        requestBody.put("stream", true);

        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + CHAT_KEY);
        headers.set("Content-Type", "application/json");

        RestTemplate restTemplate = new RestTemplate();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseExtractor<Void> responseExtractor = (ClientHttpResponse response) -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                String line;
                StringBuilder contentBuilder = new StringBuilder();

                while ((line = reader.readLine()) != null) {
                    if (line.equals("data: [DONE]")) {
                        // 结束流式传输
                        emitter.send(SseEmitter.event().data("[DONE]"));
                        emitter.complete();
                        break; // 跳出循环，避免继续读取
                    }
                    if (line.startsWith("data: ") ) {
                        String data = line.substring(6).trim();

                        
                        JsonNode jsonNode = new ObjectMapper().readTree(data);

                        // 提取 delta.content 内容
                        JsonNode delta = jsonNode.path("choices").get(0).path("delta").path("content");
                        if (!delta.isMissingNode()) {
                            String content = delta.asText();
                       

                            contentBuilder.append(content);

                            // 将提取的文字发送给客户端
                            emitter.send(SseEmitter.event().data(content));
                        }
                    }
                }
                emitter.complete(); // 完成 SSe

            } catch (Exception e) {

                emitter.completeWithError(e); // 异常处理
            }
            return null;
        };

        restTemplate.execute(FASTGPT_CHATGPT_API_URL, HttpMethod.POST, request -> {
            Map<String, String> singleValueMap = headers.toSingleValueMap();
            singleValueMap.forEach(request.getHeaders()::add);
            new ObjectMapper().writeValue(request.getBody(), requestBody);
        }, responseExtractor);
    }

}
