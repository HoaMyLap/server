package com.example.smartmanager.workspaces;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceMemberDto {
    private UUID userId;
    private String email;
    private String fullname;
    private String avatarUrl;
    private String role;
}
