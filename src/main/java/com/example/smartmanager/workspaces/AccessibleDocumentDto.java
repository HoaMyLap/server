package com.example.smartmanager.workspaces;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccessibleDocumentDto {
    private String id;
    private String name;
    private String url;
    private Long size;
    private String type;
    private LocalDateTime uploadedAt;
    private String source; // "Workspace", "Project: <Project Name>", "Task: <Task Name>"
    private String projectId; // null if workspace file
}
