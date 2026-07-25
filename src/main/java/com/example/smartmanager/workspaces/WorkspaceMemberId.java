package com.example.smartmanager.workspaces;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.io.Serializable;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceMemberId implements Serializable {
    
    @Column(name = "workspace_id")
    private java.util.UUID workspaceId;

    @Column(name = "user_id")
    private java.util.UUID userId;
}
