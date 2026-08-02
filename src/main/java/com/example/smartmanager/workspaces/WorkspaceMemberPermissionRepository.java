package com.example.smartmanager.workspaces;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface WorkspaceMemberPermissionRepository extends JpaRepository<WorkspaceMemberPermissionEntity, WorkspaceMemberPermissionId> {
    List<WorkspaceMemberPermissionEntity> findByIdWorkspaceIdAndIdUserId(UUID workspaceId, UUID userId);
    void deleteByIdWorkspaceIdAndIdUserId(UUID workspaceId, UUID userId);
}
