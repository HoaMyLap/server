package com.example.smartmanager.email;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailJob implements Serializable {
    private String recipientEmail;
    private String workspaceId;
    private String workspaceName;
    private String role;
    private String inviterEmail;
    private String subject;
    private String content;
}
