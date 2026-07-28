package com.example.smartmanager.notifications;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvitationRepository extends JpaRepository<InvitationEntity, UUID> {
    Optional<InvitationEntity> findByTargetTypeAndTargetIdAndInviteeIdAndTypeAndStatus(
            String targetType, UUID targetId, UUID inviteeId, String type, String status);
}
