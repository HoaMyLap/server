package com.example.smartmanager.workspaces;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
public class WorkspaceMemberDto {
    private UUID userId;
    private String email;
    private String fullname;
    private String avatarUrl;
    private String role;
    private UUID roleId;
    private String customRoleName;

    public WorkspaceMemberDto(UUID userId, String email, String fullname, String avatarUrl, String role) {
        this.userId = userId;
        this.email = email;
        this.fullname = fullname;
        this.avatarUrl = avatarUrl;
        this.role = role;
    }

    public WorkspaceMemberDto(UUID userId, String email, String fullname, String avatarUrl, String role, UUID roleId, String customRoleName) {
        this.userId = userId;
        this.email = email;
        this.fullname = fullname;
        this.avatarUrl = avatarUrl;
        this.role = role;
        this.roleId = roleId;
        this.customRoleName = customRoleName;
    }
}
