package com.example.smartmanager.workspaces;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface WorkspaceRolePermissionRepository extends JpaRepository<WorkspaceRolePermissionEntity, WorkspaceRolePermissionId> {
    List<WorkspaceRolePermissionEntity> findByIdRoleId(UUID roleId);
    void deleteByIdRoleId(UUID roleId);
}
