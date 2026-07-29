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

    @Value("${app.ai.gemini.api-key:${GEMINI_API_KEY:}}")
    private String geminiApiKey;

    @Value("${app.ai.openrouter.api-key:${OPENROUTER_API_KEY:}}")
    private String openrouterApiKey;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String OPENROUTER_API_URL = "https://openrouter.ai/api/v1/chat/completions";

    /**
     * Gọi Google Gemini Direct API hoặc OpenRouter API để nhận văn bản phản hồi thô.
     */
    public String generateContent(String prompt) {
        // 1. Ưu tiên thử gọi trực tiếp Google Gemini REST API nếu có Gemini Key
        if (geminiApiKey != null && !geminiApiKey.trim().isEmpty()) {
            try {
                return callGeminiDirect(geminiApiKey.trim(), prompt);
            } catch (Exception e) {
                log.warn("Gọi Google Gemini Direct API không thành công ({}), đang chuyển hướng sang OpenRouter API...", e.getMessage());
            }
        }

        // 2. Dự phòng bằng OpenRouter API
        if (openrouterApiKey != null && !openrouterApiKey.trim().isEmpty()) {
            return callOpenRouter(openrouterApiKey.trim(), prompt);
        }

        throw new IllegalStateException("AI không thể sử dụng do chưa được cấu hình API Key hợp lệ.");
    }

    private String callGeminiDirect(String key, String prompt) {
        String[] geminiModels = new String[] {
            "gemini-2.0-flash",
            "gemini-2.0-flash-exp",
            "gemini-1.5-flash-latest",
            "gemini-1.5-pro-latest"
        };

        Exception lastException = null;

        for (String model : geminiModels) {
            try {
                String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + key;

                Map<String, Object> textPart = Map.of("text", prompt);
                Map<String, Object> partsObj = Map.of("parts", Collections.singletonList(textPart));
                Map<String, Object> requestBody = Map.of("contents", Collections.singletonList(partsObj));

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
                ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    JsonNode textNode = root.path("candidates")
                            .path(0)
                            .path("content")
                            .path("parts")
                            .path(0)
                            .path("text");
                    if (!textNode.isMissingNode() && !textNode.asText().isBlank()) {
                        log.info("Gọi thành công Google Gemini Direct API (model: {})!", model);
                        return textNode.asText();
                    }
                }
            } catch (Exception e) {
                log.warn("Gọi Google Gemini Direct API với model {} thất bại: {}", model, e.getMessage());
                lastException = e;
            }
        }
        throw new RuntimeException("Tất cả model Direct Gemini API đều thất bại: " + (lastException != null ? lastException.getMessage() : "Unknown error"));
    }

    private String callOpenRouter(String key, String prompt) {
        String[] models = new String[] {
            "google/gemini-2.0-flash-001",
            "deepseek/deepseek-r1:free",
            "deepseek/deepseek-chat:free",
            "qwen/qwen-2.5-coder-32b-instruct:free",
            "meta-llama/llama-3.3-70b-instruct"
        };

        Exception lastException = null;

        for (String model : models) {
            try {
                Map<String, Object> message = new HashMap<>();
                message.put("role", "user");
                message.put("content", prompt);

                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("model", model);
                requestBody.put("messages", Collections.singletonList(message));
                requestBody.put("max_tokens", 450);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Authorization", "Bearer " + key);
                headers.set("HTTP-Referer", "http://localhost:3000");
                headers.set("X-Title", "Homix Smart Task Manager");

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
                ResponseEntity<String> response = restTemplate.postForEntity(OPENROUTER_API_URL, entity, String.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    JsonNode textNode = root.path("choices")
                            .path(0)
                            .path("message")
                            .path("content");
                    if (!textNode.isMissingNode() && !textNode.asText().isBlank()) {
                        log.info("Gọi thành công OpenRouter API với model {}!", model);
                        return textNode.asText();
                    }
                }
            } catch (Exception e) {
                log.warn("Gọi OpenRouter API với model {} thất bại: {}. Thử model tiếp theo...", model, e.getMessage());
                lastException = e;
            }
        }

        log.error("Tất cả các model OpenRouter AI đều không khả dụng", lastException);
        throw new RuntimeException("AI không thể phản hồi do giới hạn API Key / Token từ nhà cung cấp.");
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
