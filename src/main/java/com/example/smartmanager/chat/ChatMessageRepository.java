package com.example.smartmanager.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {
    List<ChatMessageEntity> findByTargetTypeAndTargetIdOrderByCreatedAtAsc(String targetType, UUID targetId);
}
