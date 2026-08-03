package com.example.smartmanager.chat;

import com.example.smartmanager.users.UserEntity;
import com.example.smartmanager.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;

    @Transactional
    public ChatMessageDto saveMessage(ChatMessageDto dto) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setSenderId(dto.getSenderId());
        entity.setTargetId(dto.getTargetId());
        entity.setTargetType(dto.getTargetType());
        entity.setContent(dto.getContent());
        entity.setCreatedAt(LocalDateTime.now());

        ChatMessageEntity saved = chatMessageRepository.save(entity);

        dto.setId(saved.getId());
        dto.setCreatedAt(saved.getCreatedAt());

        // Enrich sender info
        userRepository.findById(dto.getSenderId()).ifPresent(user -> {
            dto.setSenderName(user.getFullname());
            dto.setSenderAvatarUrl(user.getAvatarUrl());
        });

        return dto;
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getMessageHistory(String targetType, UUID targetId) {
        List<ChatMessageEntity> messages = chatMessageRepository.findByTargetTypeAndTargetIdOrderByCreatedAtAsc(targetType, targetId);
        return messages.stream().map(msg -> {
            ChatMessageDto dto = new ChatMessageDto();
            dto.setId(msg.getId());
            dto.setSenderId(msg.getSenderId());
            dto.setTargetId(msg.getTargetId());
            dto.setTargetType(msg.getTargetType());
            dto.setContent(msg.getContent());
            dto.setCreatedAt(msg.getCreatedAt());

            userRepository.findById(msg.getSenderId()).ifPresent(user -> {
                dto.setSenderName(user.getFullname());
                dto.setSenderAvatarUrl(user.getAvatarUrl());
            });
            return dto;
        }).collect(Collectors.toList());
    }
}
