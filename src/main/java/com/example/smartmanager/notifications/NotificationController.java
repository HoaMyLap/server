package com.example.smartmanager.notifications;

import com.example.smartmanager.auth.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
}
