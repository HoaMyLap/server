package com.example.smartmanager.projects;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectDeletionRequestRepository extends JpaRepository<ProjectDeletionRequestEntity, UUID> {
    
    List<ProjectDeletionRequestEntity> findByProjectIdAndStatus(UUID projectId, String status);

    @Query("SELECT r FROM ProjectDeletionRequestEntity r JOIN ProjectEntity p ON r.projectId = p.id WHERE p.workspaceId = :workspaceId AND r.status = :status ORDER BY r.createdAt DESC")
    List<ProjectDeletionRequestEntity> findByWorkspaceIdAndStatus(@Param("workspaceId") UUID workspaceId, @Param("status") String status);
}
