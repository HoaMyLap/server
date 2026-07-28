package com.example.smartmanager.projects;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "project_members")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMemberEntity {

    @EmbeddedId
    private ProjectMemberId id;

    @Column(nullable = false)
    private String role = "MEMBER";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
