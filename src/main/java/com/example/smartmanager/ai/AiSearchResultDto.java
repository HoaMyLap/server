package com.example.smartmanager.ai;

import com.example.smartmanager.tasks.TaskEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiSearchResultDto {
    private TaskEntity task;
    private Double relevanceScore;
    private String reason;
}
