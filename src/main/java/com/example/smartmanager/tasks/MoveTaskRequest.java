package com.example.smartmanager.tasks;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MoveTaskRequest {
    
    @NotBlank(message = "Trạng thái mới không được để trống")
    private String newStatus;
    
    private Double prevPosition;
    private Double nextPosition;
}
