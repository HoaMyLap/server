package com.example.smartmanager.projects;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectFolderRepository extends JpaRepository<ProjectFolderEntity, UUID> {
    List<ProjectFolderEntity> findByProjectIdAndParentId(UUID projectId, UUID parentId);
    List<ProjectFolderEntity> findByProjectIdAndParentIdIsNull(UUID projectId);
    List<ProjectFolderEntity> findByProjectId(UUID projectId);
    void deleteByProjectId(UUID projectId);
}
