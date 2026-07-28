package com.example.smartmanager.notifications;

import lombok.Data;
import java.util.List;

@Data
public class BatchInviteUserRequest {
    private String targetType; // WORKSPACE or PROJECT
    private String targetId;
    private String role; // MEMBER or VIEWER
    private List<String> emails;
}
