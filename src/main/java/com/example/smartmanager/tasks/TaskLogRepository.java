package com.example.smartmanager.tasks;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface TaskLogRepository extends JpaRepository<TaskLogEntity, UUID> {
    List<TaskLogEntity> findByTaskIdOrderByCreatedAtDesc(UUID taskId);
}
