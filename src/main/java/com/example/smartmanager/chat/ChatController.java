package com.example.smartmanager.chat;

import com.example.smartmanager.auth.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping("/workspace/{workspaceId}/history")
    @PreAuthorize("@securityService.hasWorkspaceRole(#workspaceId, 'VIEWER')")
    public ResponseEntity<List<ChatMessageDto>> getWorkspaceChatHistory(
            @PathVariable("workspaceId") String workspaceId) {
        List<ChatMessageDto> history = chatService.getMessageHistory("WORKSPACE", UUID.fromString(workspaceId));
        return ResponseEntity.ok(history);
    }

    @GetMapping("/project/{projectId}/history")
    @PreAuthorize("@securityService.hasProjectRole(#projectId, 'VIEWER')")
    public ResponseEntity<List<ChatMessageDto>> getProjectChatHistory(
            @PathVariable("projectId") String projectId) {
        List<ChatMessageDto> history = chatService.getMessageHistory("PROJECT", UUID.fromString(projectId));
        return ResponseEntity.ok(history);
    }

    @PostMapping("/workspace/{workspaceId}/send")
    @PreAuthorize("@securityService.hasWorkspaceRole(#workspaceId, 'VIEWER')")
    public ResponseEntity<?> sendWorkspaceMessage(
            @PathVariable("workspaceId") String workspaceId,
            @RequestBody Map<String, String> payload,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        String content = payload.get("content");
        if (content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Content cannot be empty"));
        }

        ChatMessageDto dto = new ChatMessageDto();
        dto.setSenderId(UUID.fromString(userPrincipal.getId()));
        dto.setTargetId(UUID.fromString(workspaceId));
        dto.setTargetType("WORKSPACE");
        dto.setContent(content.trim());

        ChatMessageDto saved = chatService.saveMessage(dto);

        // Broadcast realtime via WebSocket
        messagingTemplate.convertAndSend("/topic/chat/WORKSPACE/" + workspaceId, saved);

        return ResponseEntity.ok(saved);
    }

    @PostMapping("/project/{projectId}/send")
    @PreAuthorize("@securityService.hasProjectRole(#projectId, 'VIEWER')")
    public ResponseEntity<?> sendProjectMessage(
            @PathVariable("projectId") String projectId,
            @RequestBody Map<String, String> payload,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        String content = payload.get("content");
        if (content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Content cannot be empty"));
        }

        ChatMessageDto dto = new ChatMessageDto();
        dto.setSenderId(UUID.fromString(userPrincipal.getId()));
        dto.setTargetId(UUID.fromString(projectId));
        dto.setTargetType("PROJECT");
        dto.setContent(content.trim());

        ChatMessageDto saved = chatService.saveMessage(dto);

        // Broadcast realtime via WebSocket
        messagingTemplate.convertAndSend("/topic/chat/PROJECT/" + projectId, saved);

        return ResponseEntity.ok(saved);
    }
}
