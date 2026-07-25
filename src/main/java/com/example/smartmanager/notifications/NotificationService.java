package com.example.smartmanager.notifications;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public List<NotificationEntity> getUserNotifications(String userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(UUID.fromString(userId));
    }

    @Transactional
    public NotificationEntity markAsRead(String notificationId) {
        NotificationEntity notification = notificationRepository.findById(UUID.fromString(notificationId))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông báo"));
        notification.setIsRead(true);
        return notificationRepository.save(notification);
    }

    @Transactional
    public NotificationEntity createNotification(String userId, String title, String content, String type) {
        NotificationEntity notification = new NotificationEntity(
                null,
                UUID.fromString(userId),
                title,
                content,
                false,
                type,
                LocalDateTime.now()
        );
        NotificationEntity saved = notificationRepository.save(notification);

        // Phát tin nhắn realtime qua WebSocket cho riêng user
        messagingTemplate.convertAndSend("/topic/notifications/" + userId, saved);

        return saved;
    }
}
