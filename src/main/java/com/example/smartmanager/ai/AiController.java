package com.example.smartmanager.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AiController {

    private final AiService aiService;

    @GetMapping("/project/{projectId}/summary")
    @PreAuthorize("@securityService.hasProjectRole(#projectId, 'VIEWER')")
    public ResponseEntity<?> getProjectSummary(@PathVariable("projectId") String projectId) {
        try {
            String summary = aiService.generateDailyProjectSummary(projectId);
            return ResponseEntity.ok(Map.of("summary", summary));
        } catch (Exception e) {
            log.error("Lỗi khi sinh Báo cáo AI cho dự án {}: ", projectId, e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Lỗi sinh báo cáo AI"));
        }
    }

    @PostMapping("/project/{projectId}/smart-search")
    @PreAuthorize("@securityService.hasProjectRole(#projectId, 'VIEWER')")
    public ResponseEntity<?> smartSearch(
            @PathVariable("projectId") String projectId,
            @RequestBody Map<String, String> body) {
        try {
            String query = body.get("query");
            if (query == null || query.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Từ khóa tìm kiếm không được để trống"));
            }
            java.util.List<AiSearchResultDto> results = aiService.smartSearchTasks(projectId, query);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
