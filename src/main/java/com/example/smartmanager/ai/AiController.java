package com.example.smartmanager.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @GetMapping("/project/{projectId}/summary")
    @PreAuthorize("@securityService.hasProjectRole(#projectId, 'VIEWER')")
    public ResponseEntity<?> getProjectSummary(@PathVariable("projectId") String projectId) {
        try {
            String summary = aiService.generateDailyProjectSummary(projectId);
            return ResponseEntity.ok(Map.of("summary", summary));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
