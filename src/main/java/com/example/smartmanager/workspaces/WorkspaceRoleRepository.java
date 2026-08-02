package com.example.smartmanager.workspaces;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface WorkspaceRoleRepository extends JpaRepository<WorkspaceRoleEntity, UUID> {
    List<WorkspaceRoleEntity> findByWorkspaceId(UUID workspaceId);
}
