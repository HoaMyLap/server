package com.example.smartmanager.projects;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectFileRepository extends JpaRepository<ProjectFileEntity, UUID> {
    List<ProjectFileEntity> findByProjectIdOrderByUploadedAtDesc(UUID projectId);
}
