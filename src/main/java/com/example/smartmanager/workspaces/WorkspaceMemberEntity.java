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
@AllArgsConstructor
public class WorkspaceMemberEntity {

    @EmbeddedId
    private WorkspaceMemberId id;

    @Column(nullable = false)
    private String role = "MEMBER"; // ADMIN, MEMBER, VIEWER

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
