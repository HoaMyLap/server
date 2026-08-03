package com.example.smartmanager.chat;

import com.example.smartmanager.users.UserEntity;
import com.example.smartmanager.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ChatServiceTests {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ChatService chatService;

    private UUID userId;
    private UUID targetId;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userId = UUID.randomUUID();
        targetId = UUID.randomUUID();

        user = new UserEntity();
        user.setId(userId);
        user.setFullname("Test User");
        user.setEmail("test@example.com");
        user.setAvatarUrl("http://avatar.url");
    }

    @Test
    void testSaveMessage() {
        ChatMessageDto dto = new ChatMessageDto();
        dto.setSenderId(userId);
        dto.setTargetId(targetId);
        dto.setTargetType("WORKSPACE");
        dto.setContent("Hello World");

        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setId(UUID.randomUUID());
        entity.setSenderId(userId);
        entity.setTargetId(targetId);
        entity.setTargetType("WORKSPACE");
        entity.setContent("Hello World");
        entity.setCreatedAt(LocalDateTime.now());

        when(chatMessageRepository.save(any(ChatMessageEntity.class))).thenReturn(entity);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        ChatMessageDto result = chatService.saveMessage(dto);

        assertNotNull(result.getId());
        assertEquals("Test User", result.getSenderName());
        assertEquals("http://avatar.url", result.getSenderAvatarUrl());
        assertEquals("Hello World", result.getContent());
    }

    @Test
    void testGetMessageHistory() {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setId(UUID.randomUUID());
        entity.setSenderId(userId);
        entity.setTargetId(targetId);
        entity.setTargetType("WORKSPACE");
        entity.setContent("Hello World");
        entity.setCreatedAt(LocalDateTime.now());

        when(chatMessageRepository.findByTargetTypeAndTargetIdOrderByCreatedAtAsc("WORKSPACE", targetId))
                .thenReturn(Collections.singletonList(entity));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        List<ChatMessageDto> history = chatService.getMessageHistory("WORKSPACE", targetId);

        assertEquals(1, history.size());
        assertEquals("Hello World", history.get(0).getContent());
        assertEquals("Test User", history.get(0).getSenderName());
    }
}
