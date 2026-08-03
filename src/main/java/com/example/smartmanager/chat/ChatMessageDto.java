package com.example.smartmanager.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {
    private UUID id;
    private UUID senderId;
    private String senderName;
    private String senderAvatarUrl;
    private UUID targetId;
    private String targetType; // WORKSPACE or PROJECT
    private String content;
    private LocalDateTime createdAt;
}
