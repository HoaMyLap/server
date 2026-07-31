package com.example.smartmanager.tasks;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "task_files")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskFileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(nullable = false)
    private Long size = 0L;

    private String type;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "uploader_id")
    private UUID uploaderId;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();
}
