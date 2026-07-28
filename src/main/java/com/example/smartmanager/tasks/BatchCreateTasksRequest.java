package com.example.smartmanager.tasks;

import lombok.Data;
import java.util.List;

@Data
public class BatchCreateTasksRequest {
    private String projectId;
    private List<TaskEntity> tasks;
}
