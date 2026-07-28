package com.example.smartmanager.projects;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectMemberRepository extends JpaRepository<ProjectMemberEntity, ProjectMemberId> {
    boolean existsByIdProjectIdAndIdUserId(UUID projectId, UUID userId);

    @Query("SELECT p FROM ProjectEntity p WHERE p.workspaceId = :workspaceId AND p.id IN (SELECT pm.id.projectId FROM ProjectMemberEntity pm WHERE pm.id.userId = :userId)")
    List<ProjectEntity> findUserProjectsInWorkspace(@Param("workspaceId") UUID workspaceId, @Param("userId") UUID userId);
}
