package com.example.Interview.controller;

import com.example.Interview.ocr.DocumentOCRService;
import com.example.Interview.repository.EduRepository;
import com.example.Interview.repository.ProfileRepository;
import com.example.Interview.repository.SkillRepository;
import com.example.Interview.repository.UserRepository;
import com.example.Interview.repository.WorkRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;


import org.springframework.transaction.annotation.Transactional;
import com.example.Interview.entity.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    private final String OPENAI_API_KEY = System.getenv("INT_DP_RES_KEY");
    private final String INT_DP_EVAL_KEY = System.getenv("INT_DP_EVAL_KEY");
    private final String CHATGPT_API_URL = "https://api.deepseek.com/chat/completions";

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private EduRepository eduRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private WorkRepository workRepository;

    @Autowired
    private DocumentOCRService documentOCRService; // 注入 OCR 服务

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ResumeController(ProfileRepository profileRepository, EduRepository eduRepository,
                            SkillRepository skillRepository, WorkRepository workRepository,
                            DocumentOCRService documentOCRService) {
        this.profileRepository = profileRepository;
        this.eduRepository = eduRepository;
        this.skillRepository = skillRepository;
        this.workRepository = workRepository;
        this.documentOCRService = documentOCRService;
    }

    /**
     * 统一的端点，用于处理和解析上传的简历文件。
     *
     * @param file     上传的简历文件。
     * @param username 与简历关联的用户名。
     * @return 包含解析结果的 ResponseEntity。
     */
    @PostMapping("/process")
    public ResponseEntity<?> processAndParseResume(@RequestParam("file") MultipartFile file,
                                                   @RequestParam String username) {
        System.out.println("开始简历处理与解析...");
        Map<String, Object> response = new HashMap<>();

        if (file.isEmpty()) {
            response.put("success", false);
            response.put("message", "文件不能为空！");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            // 步骤 1：执行 OCR，提取文本
            String ocrText = performOCR(file);
            if (ocrText == null || ocrText.isEmpty()) {
                response.put("success", false);
                response.put("message", "无法从文件中提取文本！");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }

            // 步骤 2：构建用于 ChatGPT 的提示语
            String prompt = buildFullResumeParsingPrompt(ocrText);

            // 步骤 3：调用 ChatGPT API 解析简历
            String gptResponse = callChatGPT(prompt);

            if (gptResponse.isEmpty()) {
                response.put("success", false);
                response.put("message", "ChatGPT 未返回有效内容！");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }

            // 步骤 4：解析 ChatGPT 的响应为 JSON
            Map<String, Object> parsedData = parseChatGPTResponse(gptResponse);

            if (parsedData.isEmpty()) {
                response.put("success", false);
                response.put("message", "解析后的数据为空！");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }

            // 步骤 5：可选 - 将解析的数据保存到仓库
            // saveParsedData(username, parsedData);

            // 步骤 6：准备并返回响应
            response.put("success", true);
            response.put("message", "简历处理与解析成功！");
            response.put("data", parsedData);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "简历处理与解析失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 执行 OCR 以从上传的文件中提取文本。
     *
     * @param file 上传的简历文件。
     * @return 提取的文本字符串。
     * @throws Exception 如果 OCR 处理失败。
     */
    private String performOCR(MultipartFile file) throws Exception {
        // 将文件保存到临时位置
        File tempFile = convertToFile(file);
        String outputDir = System.getProperty("java.io.tmpdir"); // 临时目录

        // 调用 OCR 服务处理文件
        String ocrResult = documentOCRService.processFile(tempFile, outputDir);

        // 可选：处理完成后删除临时文件
        if (tempFile.exists()) {
            boolean deleted = tempFile.delete();
            if (!deleted) {
                System.err.println("无法删除临时文件：" + tempFile.getAbsolutePath());
            }
        }

        System.out.println("OCR 提取的文本长度：" + (ocrResult != null ? ocrResult.length() : "null"));
        return ocrResult;
    }

    /**
     * 将 MultipartFile 转换为 File。
     *
     * @param file MultipartFile 对象。
     * @return 转换后的 File 对象。
     * @throws IOException 如果文件转换失败。
     */
    private File convertToFile(MultipartFile file) throws IOException {
        File convFile = new File(System.getProperty("java.io.tmpdir") + "/" + file.getOriginalFilename());
        try (FileOutputStream fos = new FileOutputStream(convFile)) {
            fos.write(file.getBytes());
        }
        return convFile;
    }

    /**
     * 构建用于解析整个简历的提示语。
     *
     * @param resumeContent 简历的全文文本。
     * @return 格式化的提示语字符串。
     */
    private String buildFullResumeParsingPrompt(String resumeContent) {
        String jsonTemplate = "{\n" +
                "  \"profile\": {\n" +
                "    \"phone\": \"\", // 联系电话\n" +
                "    \"gender\": \"\", // 性别，例如 男 或 女\n" +
                "    \"age\": , // 年龄，整数\n" +
                "    \"location\": \"\", // 居住地\n" +
                "    \"expected_salary\": \"\", // 期望薪资，例如 15k-20k\n" +
                "    \"description\": \"\", // 个人简介\n" +
                "    \"is_full_time\": true // 是否全职，布尔值\n" +
                "  },\n" +
                "  \"edu\": [\n" +
                "    { \"school_name\": \"\", \"degree\": \"\", \"major\": \"\", \"start_date\": \"\", \"end_date\": \"\", \"eval\": \"\" }\n" +
                "    // 可以有多个教育经历, 日期按照年月日的格式返回，中间是-分隔\n" +
                "  ],\n" +
                "  \"work\": [\n" +
                "    { \"company_name\": \"\", \"position\": \"\", \"description\": \"\", \"start_date\": \"\", \"end_date\": \"\", \"eval\": \"\" }\n" +
                "    // 可以有多个工作经历\n" +
                "  ],\n" +
                "  \"skills\": [\n" +
                "    { \"skill_name\": \"\" }\n" +
                "    // 可以有多个技能,只包含技能的信息,选择至多四个你认为他最擅长的\n" +
                "  ],\n" +
                "}";

        return "请解析以下简历内容，并返回如下格式的 JSON, edu和work的eval是你认为这个学校或公司的含金量，，按照杰出，优秀，良好这三个打分，profile当中的description是你结合简历内容对这个人的描述，不要包括名字，不要太长，没有主语:\n" +
                jsonTemplate + "\n" +
                "内容如下：\n" + resumeContent;
    }

    /**
     * 调用 ChatGPT API 发送提示语并获取响应。
     *
     * @param prompt 发送给 ChatGPT 的提示语。
     * @return ChatGPT 响应的内容。
     * @throws Exception 如果调用 API 失败。
     */
    private String callChatGPT(String prompt) throws Exception {
        if (prompt.isEmpty()) {
            return "";
        }
        System.out.println("Sending request to OpenAI API with prompt: " + prompt);
        // 构建消息体
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);

        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "deepseek-chat"); 
        requestBody.put("messages", List.of(message));
        requestBody.put("temperature", 1);
        requestBody.put("max_tokens", 4000);

        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + OPENAI_API_KEY);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 创建 RestTemplate 实例
        RestTemplate restTemplate = new RestTemplate();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        // 发送 POST 请求
        ResponseEntity<String> response = restTemplate.exchange(
                CHATGPT_API_URL, HttpMethod.POST, entity, String.class
        );

        // 检查响应状态码
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("调用 ChatGPT API 失败：HTTP " + response.getStatusCodeValue() + " - " + response.getBody());
        }

        // 解析响应内容
        JsonNode responseJson = objectMapper.readTree(response.getBody());
        String nestedJsonContent = responseJson
                .path("choices")
                .get(0)
                .path("message")
                .path("content")
                .asText()
                .trim();

        // 打印原始响应以供调试
        System.out.println("原始 ChatGPT 响应内容：\n" + nestedJsonContent);

        // 移除 ```json 和 ```
        if (nestedJsonContent.startsWith("```json")) {
            nestedJsonContent = nestedJsonContent.substring(7).trim();
        }
        if (nestedJsonContent.endsWith("```")) {
            nestedJsonContent = nestedJsonContent.substring(0, nestedJsonContent.length() - 3).trim();
        }

        // 打印处理后的响应以供调试
        System.out.println("处理后的 ChatGPT 响应内容：\n" + nestedJsonContent);
        return nestedJsonContent;
    }

    /**
     * 解析 ChatGPT 的响应并转换为 Map。
     *
     * @param gptResponse ChatGPT 返回的响应内容。
     * @return 解析后的数据映射。
     */
    private Map<String, Object> parseChatGPTResponse(String gptResponse) {
        Map<String, Object> parsedData = new HashMap<>();
        try {
            JsonNode jsonNode = objectMapper.readTree(gptResponse);
            parsedData = objectMapper.convertValue(jsonNode, Map.class);
        } catch (Exception e) {
            System.err.println("解析 ChatGPT 响应失败：" + e.getMessage());
            e.printStackTrace();
        }
        return parsedData;
    }

    /**
     * 根据用户名获取用户 ID 的占位方法。
     *
     * @param username 用户名。
     * @return 用户 ID。
     */
    private Long getUserIdByUsername(String username) {
        // 如果需要实现实际的用户检索逻辑，请在此处实现
        // 例如：
        // User user = userRepository.findByUsername(username)
        //         .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        // return user.getId();
        return 1L; // 占位符
    }

    /**
     * 可选：将解析的数据保存到仓库中。
     *
     * @param username   与简历关联的用户名。
     * @param parsedData 解析后的简历数据。
     */
    private void saveParsedData(String username, Map<String, Object> parsedData) {
        // 在此处实现保存逻辑
        // 例如：
        // Profile profile = objectMapper.convertValue(parsedData.get("profile"), Profile.class);
        // profile.setUserId(getUserIdByUsername(username));
        // profileRepository.save(profile);
        //
        // 同样适用于 edu、work、skills、projects
    }




    @PostMapping("/add")
    @Transactional
    public ResponseEntity<?> addResume(@RequestBody Map<String, Object> resumeData) {
        Map<String, Object> response = new HashMap<>();

        try {
            // 解析并获取 profile 数据
            Map<String, Object> profileMap = (Map<String, Object>) resumeData.get("profile");
            if (profileMap == null) {
                response.put("success", false);
                response.put("message", "Profile 信息不能为空！");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // 获取 user_id
            Object userIdObj = profileMap.get("user_id");
            if (userIdObj == null) {
                response.put("success", false);
                response.put("message", "user_id 不能为空！");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            Long userId = Long.parseLong(userIdObj.toString());

            // 查找 User 实体
            Optional<User> userOptional = userRepository.findById(userId);
            if (!userOptional.isPresent()) {
                response.put("success", false);
                response.put("message", "用户不存在！");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            User user = userOptional.get();

            // 保存 Profile
            Profile profile = new Profile();
            profile.setUser(user);
            profile.setPhone((String) profileMap.get("phone"));
            profile.setGender((String) profileMap.get("gender"));
            profile.setAge(profileMap.get("age") != null ? Integer.parseInt(profileMap.get("age").toString()) : null);
            profile.setLocation((String) profileMap.get("location"));
            profile.setExpectedSalary((String) profileMap.get("expected_salary"));
            profile.setDescription((String) profileMap.get("description"));
            profile.setIsFullTime(profileMap.get("is_full_time") != null && (Boolean) profileMap.get("is_full_time"));
            profileRepository.save(profile);

            // 解析并保存 Edu 数据
            List<Map<String, Object>> eduList = (List<Map<String, Object>>) resumeData.get("edu");
            if (eduList != null) {
                for (Map<String, Object> eduMap : eduList) {
                    Edu edu = new Edu();
                    edu.setUser(user);
                    edu.setSchoolName((String) eduMap.get("school_name"));
                    edu.setDegree((String) eduMap.get("degree"));
                    edu.setMajor((String) eduMap.get("major"));
                    edu.setStartDate(parseDate(eduMap.get("start_date")));
                    edu.setEndDate(parseDate(eduMap.get("end_date")));
                    edu.setEval((String) eduMap.get("eval"));
                    eduRepository.save(edu);
                }
            }

            // 解析并保存 Work 数据
            List<Map<String, Object>> workList = (List<Map<String, Object>>) resumeData.get("work");
            if (workList != null) {
                for (Map<String, Object> workMap : workList) {
                    Work work = new Work();
                    work.setUser(user);
                    work.setCompanyName((String) workMap.get("company_name"));
                    work.setPosition((String) workMap.get("position"));
                    work.setDescription((String) workMap.get("description"));
                    work.setStartDate(parseDate(workMap.get("start_date")));
                    work.setEndDate(parseDate(workMap.get("end_date")));
                    work.setEval((String) workMap.get("eval"));
                    workRepository.save(work);
                }
            }

            // 解析并保存 Skills 数据
            List<Map<String, Object>> skillsList = (List<Map<String, Object>>) resumeData.get("skills");
            if (skillsList != null) {
                for (Map<String, Object> skillMap : skillsList) {
                    Skill skill = new Skill();
                    skill.setUser(user);
                    skill.setSkillName((String) skillMap.get("skill_name"));
                    skillRepository.save(skill);
                }
            }

            response.put("success", true);
            response.put("message", "简历信息保存成功！");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "保存简历信息失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 辅助方法：将字符串日期转换为 Date 对象。
     *
     * @param dateObj 字符串日期，可能是 yyyy-MM 或 yyyy-MM-dd
     * @return Date 对象，如果输入为空或格式不正确，返回 null
     */
    private Date parseDate(Object dateObj) {
        if (dateObj == null) {
            return null;
        }
        String dateStr = dateObj.toString();
        if (dateStr.isEmpty()) {
            return null;
        }

        SimpleDateFormat sdf;
        if (dateStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
            sdf = new SimpleDateFormat("yyyy-MM-dd");
        } else if (dateStr.matches("\\d{4}-\\d{2}")) {
            sdf = new SimpleDateFormat("yyyy-MM");
        } else if (dateStr.matches("\\d{4}")) {
            sdf = new SimpleDateFormat("yyyy");
        } else {
            // 不支持的日期格式
            return null;
        }

        sdf.setLenient(false);
        try {
            return sdf.parse(dateStr);
        } catch (ParseException e) {
            // 日期格式不正确
            return null;
        }
    }

     @PostMapping("/check")
    public ResponseEntity<?> checkResumeData(@RequestBody Map<String, Object> requestBody) {
        Map<String, Object> response = new HashMap<>();

        try {
            String username = (String) requestBody.get("username");

            if (username == null || username.trim().isEmpty()) {
                response.put("success", false);
                response.put("hasData", false);
                response.put("message", "username 不能为空！");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // 查找 User 实体，返回 User 或 null
            User user = userRepository.findByUsername(username);
            if (user == null) {
                response.put("success", false);
                response.put("hasData", false);
                response.put("message", "用户不存在！");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // 查找 Profile 实体，返回 Profile 或 null
            Profile profile = profileRepository.findByUser(user);
            boolean hasData = profile != null;

            response.put("success", true);
            response.put("hasData", hasData);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("hasData", false);
            response.put("message", "检查简历数据失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/evaluate-resume")
    public ResponseEntity<Map<String, Object>> evaluateResume(@RequestBody Map<String, Object> requestData) {
        Map<String, Object> response = new HashMap<>();
        try {
            // Step 1: 构建评分的提示语
            String resumeContent = buildEvalPrompt(requestData);

            // Step 2: 调用 ChatGPT API 进行评分
            String gptResponse = callRESGPT(resumeContent);

            if (gptResponse.isEmpty()) {
                response.put("success", false);
                response.put("message", "ChatGPT 未返回有效内容！");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }

            // Step 3: 解析 ChatGPT 的响应为 JSON
            Map<String, Object> evaluationResult = parseRESResponse(gptResponse);

            if (evaluationResult.isEmpty()) {
                response.put("success", false);
                response.put("message", "解析后的评分数据为空！");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }

            // Step 4: 返回评分数据和亮点
            response.put("success", true);
            response.put("message", "简历评分成功！");
            response.put("data", evaluationResult);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "简历评分失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    private String buildEvalPrompt(Map<String, Object> requestData) {
        try {
            return "请根据以下简历内容，从语法、精确度、简历的影响力和结构四个角度分别进行评分（0-100分），并返回如下格式的 JSON 数据：\n" +
                    "{\n" +
                    "  \"grammar\": { \"score\": 分数, \"details\": \"语法得分的详细原因, 使用一些优秀简历里出现的词会更高分\" },\n" +
                    "  \"brevity\": { \"score\": 分数, \"details\": \"简明得分的详细原因，要看语言的质量是否能有效传达信息\" },\n" +
                    "  \"impact\": { \"score\": 分数, \"details\": \"简历的影响力得分的详细原因，把你想成面试官，根据这个简历给你的冲击力来打分\" },\n" +
                    "  \"sections\": { \"score\": 分数, \"details\": \"结构得分的详细原因,打分也要基于这个简历和大部分简历内容长度的比较\" },\n" +
                    "  \"overallEvaluation\": \"整体评估的详细描述\",\n" +
                    "  \"highlights\": [\n" +
                    "    {\"summary\": \"亮点概述\", \"details\": \"亮点的详细描述\"},\n" +
                    "    {\"summary\": \"亮点概述\", \"details\": \"亮点的详细描述\"},\n" +
                    "    {\"summary\": \"亮点概述\", \"details\": \"亮点的详细描述\"}\n" +
                    "  ]\n" +
                    "}\n" +
                    "其中 得分只有40 60 80 100，详细原因和描述不要带主语，内容详细一些， 简历内容如下：\n" +
                    objectMapper.writeValueAsString(requestData) ;
        } catch (Exception e) {
            // 记录错误日志
            System.err.println("构建评分提示语失败：" + e.getMessage());
            // 根据需求，可以选择返回一个默认值或者抛出一个运行时异常
            throw new RuntimeException("构建评分提示语失败", e);
        }
    }

    private String callRESGPT(String prompt) throws Exception {
        if (prompt.isEmpty()) {
            return "";
        }
        System.out.println("Sending request to OpenAI API with prompt: " + prompt);

        // 构建消息体
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);

        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "deepseek-chat");
        requestBody.put("messages", List.of(message));
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 1000);

        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + INT_DP_EVAL_KEY);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 创建 RestTemplate 实例
        RestTemplate restTemplate = new RestTemplate();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        // 发送 POST 请求
        ResponseEntity<String> response = restTemplate.exchange(
                CHATGPT_API_URL, HttpMethod.POST, entity, String.class
        );

        // 检查响应状态码
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("调用 EVAL API 失败：HTTP " + response.getStatusCodeValue() + " - " + response.getBody());
        }

        // 解析响应内容
        JsonNode responseJson = objectMapper.readTree(response.getBody());
        String nestedJsonContent = responseJson
                .path("choices")
                .get(0)
                .path("message")
                .path("content")
                .asText()
                .trim();

        // 处理 ChatGPT 响应内容
        if (nestedJsonContent.startsWith("```json")) {
            nestedJsonContent = nestedJsonContent.substring(7).trim();
        }
        if (nestedJsonContent.endsWith("```")) {
            nestedJsonContent = nestedJsonContent.substring(0, nestedJsonContent.length() - 3).trim();
        }

        System.out.println("处理后的 DP 响应内容：\n" + nestedJsonContent);
        return nestedJsonContent;
    }

    private Map<String, Object> parseRESResponse(String gptResponse) {
        Map<String, Object> parsedData = new HashMap<>();
        try {
            JsonNode jsonNode = objectMapper.readTree(gptResponse);
            parsedData = objectMapper.convertValue(jsonNode, Map.class);
        } catch (Exception e) {
            System.err.println("解析 EVAL 响应失败：" + e.getMessage());
            e.printStackTrace();
        }
        return parsedData;
    }


}
