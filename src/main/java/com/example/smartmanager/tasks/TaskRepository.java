package com.example.smartmanager.tasks;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<TaskEntity, UUID> {
    
    List<TaskEntity> findByProjectId(UUID projectId);
    
    List<TaskEntity> findByProjectIdAndStatusOrderByPositionAsc(UUID projectId, String status);
    
    List<TaskEntity> findByParentTaskId(UUID parentTaskId);
    
    Optional<TaskEntity> findFirstByProjectIdAndStatusOrderByPositionDesc(UUID projectId, String status);
}
