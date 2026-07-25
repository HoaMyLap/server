package com.example.smartmanager.workspaces;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddMemberRequest {
    
    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    private String email;

    @NotBlank(message = "Vai trò không được để trống")
    private String role = "MEMBER"; // ADMIN, MEMBER, VIEWER
}
