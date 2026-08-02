package com.example.smartmanager.workspaces;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "workspace_members")
@Data
@NoArgsConstructor
public class WorkspaceMemberEntity {

    public WorkspaceMemberEntity(WorkspaceMemberId id, String role, LocalDateTime createdAt) {
        this.id = id;
        this.role = role;
        this.createdAt = createdAt;
        this.roleId = null;
    }

    public WorkspaceMemberEntity(WorkspaceMemberId id, String role, java.util.UUID roleId, LocalDateTime createdAt) {
        this.id = id;
        this.role = role;
        this.roleId = roleId;
        this.createdAt = createdAt;
    }

    @EmbeddedId
    private WorkspaceMemberId id;

    @Column(nullable = false)
    private String role = "MEMBER"; // ADMIN, MEMBER, VIEWER

    @Column(name = "role_id")
    private java.util.UUID roleId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
