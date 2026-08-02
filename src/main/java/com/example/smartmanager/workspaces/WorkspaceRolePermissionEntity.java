package com.example.smartmanager.workspaces;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "workspace_role_permissions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceRolePermissionEntity {

    @EmbeddedId
    private WorkspaceRolePermissionId id;
}
