package com.example.smartmanager.notifications;

import com.example.smartmanager.auth.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<NotificationEntity>> getUserNotifications(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        List<NotificationEntity> list = notificationService.getUserNotifications(userPrincipal.getId());
        return ResponseEntity.ok(list);
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(
            @PathVariable("id") String notificationId) {
        try {
            NotificationEntity updated = notificationService.markAsRead(notificationId);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/read-all")
    public ResponseEntity<?> markAllAsRead(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            notificationService.markAllAsRead(userPrincipal.getId());
            return ResponseEntity.ok(Map.of("message", "Đã đánh dấu tất cả là đã đọc"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/invite")
    @PreAuthorize("('WORKSPACE'.equals(#req.targetType) and @securityService.hasWorkspaceRole(#req.targetId, 'ADMIN')) or ('PROJECT'.equals(#req.targetType) and @securityService.hasProjectRole(#req.targetId, 'ADMIN'))")
    public ResponseEntity<?> inviteUser(
            @Valid @RequestBody InviteUserRequest req,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            notificationService.inviteUser(req, userPrincipal.getId());
            return ResponseEntity.ok(Map.of("message", "Gửi lời mời thành công"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/invite-batch")
    @PreAuthorize("('WORKSPACE'.equals(#req.targetType) and @securityService.hasWorkspaceRole(#req.targetId, 'ADMIN')) or ('PROJECT'.equals(#req.targetType) and @securityService.hasProjectRole(#req.targetId, 'ADMIN'))")
    public ResponseEntity<?> inviteUsersBatch(
            @Valid @RequestBody BatchInviteUserRequest req,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            notificationService.inviteUsersBatch(req, userPrincipal.getId());
            return ResponseEntity.ok(Map.of("message", "Gửi lời mời hàng loạt thành công"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/respond-invitation")
    public ResponseEntity<?> respondInvitation(
            @PathVariable("id") String notificationId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            String action = body.get("action"); // ACCEPT or DECLINE
            NotificationEntity updated = notificationService.respondInvitation(notificationId, action, userPrincipal.getId());
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/request-leave")
    public ResponseEntity<?> requestLeave(
            @Valid @RequestBody LeaveRequest req,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            notificationService.requestLeave(req, userPrincipal.getId());
            return ResponseEntity.ok(Map.of("message", "Yêu cầu rời đã được gửi tới Admin"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/respond-leave")
    public ResponseEntity<?> respondLeaveRequest(
            @PathVariable("id") String notificationId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            String action = body.get("action"); // APPROVE or REJECT
            NotificationEntity updated = notificationService.respondLeaveRequest(notificationId, action, userPrincipal.getId());
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
