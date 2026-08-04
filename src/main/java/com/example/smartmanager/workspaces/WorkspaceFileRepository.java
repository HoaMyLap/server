package com.example.smartmanager.workspaces;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface WorkspaceFileRepository extends JpaRepository<WorkspaceFileEntity, UUID> {
    List<WorkspaceFileEntity> findByWorkspaceIdAndFolderId(UUID workspaceId, UUID folderId);
    List<WorkspaceFileEntity> findByWorkspaceIdAndFolderIdIsNull(UUID workspaceId);
    List<WorkspaceFileEntity> findByWorkspaceId(UUID workspaceId);
    List<WorkspaceFileEntity> findByFolderId(UUID folderId);
    void deleteByFolderId(UUID folderId);
    void deleteByWorkspaceId(UUID workspaceId);
}
