package com.example.smartmanager.auth;

import com.example.smartmanager.workspaces.WorkspaceMemberRepository;
import com.example.smartmanager.projects.ProjectRepository;
import com.example.smartmanager.projects.ProjectEntity;
import com.example.smartmanager.tasks.TaskRepository;
import com.example.smartmanager.tasks.TaskEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.util.Optional;
import java.util.UUID;

@Service("securityService")
@RequiredArgsConstructor
public class SecurityService {

    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;

    /**
     * Kiểm tra xem người dùng hiện tại có vai trò tương ứng (hoặc cao hơn) trong Workspace không.
     * Thừa kế quyền: ADMIN > MEMBER > VIEWER.
     */
    public boolean hasWorkspaceRole(String workspaceId, String requiredRole) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            return false;
        }

        String userId = userPrincipal.getId();
        Optional<String> roleOpt = workspaceMemberRepository.findRoleByWorkspaceIdAndUserId(
                UUID.fromString(workspaceId),
                UUID.fromString(userId)
        );
        
        if (roleOpt.isEmpty()) {
            return false;
        }

        String userRole = roleOpt.get();
        
        if ("ADMIN".equals(userRole)) {
            return true;
        }
        
        if ("MEMBER".equals(userRole)) {
            return "MEMBER".equals(requiredRole) || "VIEWER".equals(requiredRole);
        }
        
        return "VIEWER".equals(userRole) && "VIEWER".equals(requiredRole);
    }

    /**
     * Kiểm tra xem người dùng hiện tại có vai trò trong Workspace chứa Project hay không.
     */
    public boolean hasProjectRole(String projectId, String requiredRole) {
        try {
            UUID projId = UUID.fromString(projectId);
            Optional<ProjectEntity> projectOpt = projectRepository.findById(projId);
            if (projectOpt.isEmpty()) {
                return false;
            }
            return hasWorkspaceRole(projectOpt.get().getWorkspaceId().toString(), requiredRole);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Kiểm tra xem người dùng hiện tại có vai trò trong Workspace chứa Task hay không.
     */
    public boolean hasTaskRole(String taskId, String requiredRole) {
        try {
            UUID tId = UUID.fromString(taskId);
            Optional<TaskEntity> taskOpt = taskRepository.findById(tId);
            if (taskOpt.isEmpty()) {
                return false;
            }
            return hasProjectRole(taskOpt.get().getProjectId().toString(), requiredRole);
        } catch (Exception e) {
            return false;
        }
    }
}
