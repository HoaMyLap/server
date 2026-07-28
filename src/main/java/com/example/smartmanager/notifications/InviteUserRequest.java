package com.example.smartmanager.notifications;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InviteUserRequest {
    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String targetType; // WORKSPACE, PROJECT

    @NotBlank
    private String targetId;

    private String role = "MEMBER"; // ADMIN, MEMBER, VIEWER
}
