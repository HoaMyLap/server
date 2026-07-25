package com.example.smartmanager.tasks;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskMessage {
    private String action; // CREATE, UPDATE, DELETE, MOVE, ADD_COMMENT
    private String taskId;
    private String projectId;
    private Object payload; // Chứa dữ liệu thay đổi (TaskEntity, CommentEntity, v.v.)
    private String userId; // ID người thực hiện
}
