package com.example.smartmanager.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenRouterServiceClient {

    @Value("${app.ai.openrouter.api-key:your_openrouter_api_key_here}")
    private String apiKey;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String OPENROUTER_API_URL = "https://openrouter.ai/api/v1/chat/completions";

    /**
     * Gọi OpenRouter API (tương thích OpenAI format) để nhận văn bản phản hồi thô.
     */
    public String generateContent(String prompt) {
        if (apiKey == null || 
            "your_openrouter_api_key_here".equals(apiKey) || 
            apiKey.trim().isEmpty() || 
            apiKey.startsWith("sk-or-v1-placeholder")) {
            throw new IllegalStateException("AI không thể sử dụng do thiếu hoặc sai API Key. Vui lòng cấu hình OPENROUTER_API_KEY trong hệ thống.");
        }

        try {
            // Xây dựng request body chuẩn Chat Completions
            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "google/gemini-2.5-flash"); // Model hiệu năng cao của Gemini trên OpenRouter
            requestBody.put("messages", Collections.singletonList(message));
            requestBody.put("max_tokens", 1500);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            headers.set("HTTP-Referer", "http://localhost:3000"); // OpenRouter yêu cầu header này
            headers.set("X-Title", "Smart Task Manager");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(OPENROUTER_API_URL, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode textNode = root.path("choices")
                        .path(0)
                        .path("message")
                        .path("content");
                return textNode.asText();
            } else {
                throw new RuntimeException("Phản hồi từ OpenRouter API không thành công.");
            }
        } catch (HttpClientErrorException e) {
            log.error("Lỗi xác thực/HTTP khi gọi OpenRouter API: {}", e.getMessage());
            throw new RuntimeException("AI không thể sử dụng do API Key sai hoặc không có quyền truy cập.");
        } catch (Exception e) {
            log.error("Lỗi khi kết nối với OpenRouter API", e);
            throw new RuntimeException("AI không thể sử dụng do lỗi kết nối: " + e.getMessage());
        }
    }

    /**
     * Parse danh sách các subtask từ phản hồi của AI.
     */
    public List<String> parseSubtasks(String rawAiResponse) {
        if (rawAiResponse == null || rawAiResponse.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String cleaned = rawAiResponse.trim();
        if (cleaned.startsWith("```")) {
            int firstLineBreak = cleaned.indexOf("\n");
            int lastBackticks = cleaned.lastIndexOf("```");
            if (firstLineBreak != -1 && lastBackticks > firstLineBreak) {
                cleaned = cleaned.substring(firstLineBreak + 1, lastBackticks).trim();
            }
        }

        try {
            JsonNode node = objectMapper.readTree(cleaned);
            List<String> subtasks = new ArrayList<>();
            if (node.isArray()) {
                for (JsonNode item : node) {
                    if (item.isTextual()) {
                        subtasks.add(item.asText());
                    } else if (item.isObject() && item.has("title")) {
                        subtasks.add(item.get("title").asText());
                    }
                }
            }
            return subtasks;
        } catch (Exception e) {
            log.error("Không thể phân tích định dạng JSON subtask từ AI: " + rawAiResponse, e);
            String[] lines = rawAiResponse.split("\\r?\\n");
            List<String> fallbackTasks = new ArrayList<>();
            for (String line : lines) {
                String cleanLine = line.replaceAll("^(\\d+[.)]\\s*|[-*+]\\s*)", "").trim();
                if (!cleanLine.isEmpty() && !cleanLine.startsWith("```") && cleanLine.length() < 150) {
                    fallbackTasks.add(cleanLine);
                }
            }
            return fallbackTasks;
        }
    }
}
