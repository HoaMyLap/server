package com.example.smartmanager.workspaces;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface WorkspaceFolderRepository extends JpaRepository<WorkspaceFolderEntity, UUID> {
    List<WorkspaceFolderEntity> findByWorkspaceIdAndParentId(UUID workspaceId, UUID parentId);
    List<WorkspaceFolderEntity> findByWorkspaceIdAndParentIdIsNull(UUID workspaceId);
    List<WorkspaceFolderEntity> findByWorkspaceId(UUID workspaceId);
    void deleteByWorkspaceId(UUID workspaceId);
}
