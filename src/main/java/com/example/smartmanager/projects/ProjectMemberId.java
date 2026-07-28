package com.example.smartmanager.projects;

import jakarta.persistence.Embeddable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMemberId implements Serializable {
    private UUID projectId;
    private UUID userId;
}
