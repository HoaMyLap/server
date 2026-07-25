package com.example.smartmanager.tasks;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "task_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "action_type", nullable = false)
    private String actionType; // CREATE, UPDATE_STATUS, UPDATE_ASSIGNEE, ADD_COMMENT, AI_SUBTASKS_GEN

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
