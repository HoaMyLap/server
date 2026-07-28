package com.example.smartmanager.tasks;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tasks")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String status = "TODO"; // TODO, IN_PROGRESS, DONE

    @Column(nullable = false)
    private String priority = "MEDIUM"; // LOW, MEDIUM, HIGH, URGENT

    @Column(nullable = false)
    private Double position = 0.0;

    @Column(name = "due_date")
    private LocalDateTime dueDate;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "creator_id")
    private UUID creatorId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "parent_task_id")
    private UUID parentTaskId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
