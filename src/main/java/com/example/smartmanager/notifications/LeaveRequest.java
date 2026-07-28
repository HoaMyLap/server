package com.example.smartmanager.notifications;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LeaveRequest {
    @NotBlank
    private String targetType; // WORKSPACE, PROJECT

    @NotBlank
    private String targetId;
}
