package com.example.smartmanager.workspaces;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "workspace_member_permissions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceMemberPermissionEntity {

    @EmbeddedId
    private WorkspaceMemberPermissionId id;

    @Column(name = "is_allowed", nullable = false)
    private boolean isAllowed = true;
}
