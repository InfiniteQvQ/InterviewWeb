package com.example.Interview.controller;

import com.example.Interview.ocr.DocumentOCRService;
import com.example.Interview.repository.EduRepository;
import com.example.Interview.repository.ProfileRepository;
import com.example.Interview.repository.SkillRepository;
import com.example.Interview.repository.WorkRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    private final String OPENAI_API_KEY = System.getenv("INT_DP_RES_KEY");
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
}
