package com.example.smartmanager.notifications;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "invitations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvitationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "target_type", nullable = false)
    private String targetType; // WORKSPACE, PROJECT

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "inviter_id", nullable = false)
    private UUID inviterId;

    @Column(name = "invitee_id", nullable = false)
    private UUID inviteeId;

    @Column(nullable = false)
    private String role; // ADMIN, MEMBER, VIEWER

    @Column(nullable = false)
    private String type = "INVITATION"; // INVITATION, LEAVE_REQUEST

    @Column(nullable = false)
    private String status = "PENDING"; // PENDING, ACCEPTED, REJECTED, APPROVED, REJECTED_LEAVE

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
