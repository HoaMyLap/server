package com.example.smartmanager.auth;

import com.example.smartmanager.workspaces.*;
import com.example.smartmanager.projects.ProjectRepository;
import com.example.smartmanager.projects.ProjectMemberRepository;
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
    private final ProjectMemberRepository projectMemberRepository;
    private final TaskRepository taskRepository;
    private final WorkspaceRolePermissionRepository rolePermissionRepository;
    private final WorkspaceMemberPermissionRepository memberPermissionRepository;

    /**
     * Checks if a user has a specific granular permission in a workspace.
     * Evaluates member overrides, custom role permissions, or system defaults.
     */
    public boolean hasPermission(UUID workspaceId, UUID userId, String requiredPermission) {
        // 1. Check direct member permission overrides
        Optional<WorkspaceMemberPermissionEntity> overrideOpt = memberPermissionRepository.findById(
                new WorkspaceMemberPermissionId(workspaceId, userId, requiredPermission)
        );
        if (overrideOpt.isPresent()) {
            return overrideOpt.get().isAllowed();
        }

        // 2. Retrieve member record
        Optional<WorkspaceMemberEntity> memberOpt = workspaceMemberRepository.findById(
                new WorkspaceMemberId(workspaceId, userId)
        );
        if (memberOpt.isEmpty()) {
            return false;
        }

        WorkspaceMemberEntity member = memberOpt.get();

        // 3. Admin default bypass
        if ("ADMIN".equals(member.getRole())) {
            return true;
        }

        // 4. Custom role check
        if (member.getRoleId() != null) {
            Optional<WorkspaceRolePermissionEntity> rolePermOpt = rolePermissionRepository.findById(
                    new WorkspaceRolePermissionId(member.getRoleId(), requiredPermission)
            );
            return rolePermOpt.isPresent();
        }

        // 5. Default roles fallback mapping
        String userRole = member.getRole();
        if ("MEMBER".equals(userRole)) {
            // MEMBERS can do everything except administrative tasks
            return !"WORKSPACE_UPDATE".equals(requiredPermission) &&
                   !"WORKSPACE_DELETE".equals(requiredPermission) &&
                   !"WORKSPACE_ROLE_MANAGE".equals(requiredPermission) &&
                   !"WORKSPACE_MEMBER_REMOVE".equals(requiredPermission);
        }

        if ("VIEWER".equals(userRole)) {
            // VIEWERS can only view
            return "WORKSPACE_VIEW".equals(requiredPermission) ||
                   "PROJECT_VIEW".equals(requiredPermission) ||
                   "TASK_VIEW".equals(requiredPermission);
        }

        return false;
    }

    /**
     * Checks workspace role constraints mapped to permissions.
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

        UUID uId = UUID.fromString(userPrincipal.getId());
        UUID wsId = UUID.fromString(workspaceId);

        if ("ADMIN".equals(requiredRole)) {
            return hasPermission(wsId, uId, "WORKSPACE_ROLE_MANAGE") || 
                   hasPermission(wsId, uId, "WORKSPACE_UPDATE");
        }
        if ("MEMBER".equals(requiredRole)) {
            return hasPermission(wsId, uId, "PROJECT_CREATE") || 
                   hasPermission(wsId, uId, "TASK_CREATE") ||
                   hasPermission(wsId, uId, "WORKSPACE_MEMBER_INVITE");
        }
        return hasPermission(wsId, uId, "WORKSPACE_VIEW");
    }

    /**
     * Checks project role constraints mapped to permissions.
     */
    public boolean hasProjectRole(String projectId, String requiredRole) {
        try {
            UUID projId = UUID.fromString(projectId);
            Optional<ProjectEntity> projectOpt = projectRepository.findById(projId);
            if (projectOpt.isEmpty()) {
                return false;
            }

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal userPrincipal) {
                UUID userId = UUID.fromString(userPrincipal.getId());
                UUID wsId = projectOpt.get().getWorkspaceId();

                // 1. Check workspace level permissions override/inheritance
                if ("ADMIN".equals(requiredRole)) {
                    if (hasPermission(wsId, userId, "PROJECT_DELETE") || hasPermission(wsId, userId, "WORKSPACE_ROLE_MANAGE")) {
                        return true;
                    }
                } else if ("MEMBER".equals(requiredRole)) {
                    if (hasPermission(wsId, userId, "TASK_CREATE") || hasPermission(wsId, userId, "TASK_UPDATE")) {
                        return true;
                    }
                } else { // VIEWER
                    if (hasPermission(wsId, userId, "PROJECT_VIEW")) {
                        return true;
                    }
                }

                // 2. Check project members level
                Optional<com.example.smartmanager.projects.ProjectMemberEntity> pmOpt = projectMemberRepository.findById(
                        new com.example.smartmanager.projects.ProjectMemberId(projId, userId)
                );
                if (pmOpt.isPresent()) {
                    String pmRole = pmOpt.get().getRole();
                    if ("ADMIN".equals(pmRole)) {
                        return true;
                    }
                    if ("MEMBER".equals(pmRole)) {
                        return "MEMBER".equals(requiredRole) || "VIEWER".equals(requiredRole);
                    }
                    return "VIEWER".equals(requiredRole);
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks task role constraints mapped to permissions.
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
