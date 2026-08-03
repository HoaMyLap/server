package com.example.smartmanager.projects;

import com.example.smartmanager.auth.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    @PreAuthorize("@securityService.hasWorkspaceRole(#project.workspaceId.toString(), 'MEMBER')")
    public ResponseEntity<?> createProject(
            @Valid @RequestBody ProjectEntity project,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            ProjectEntity created = projectService.createProject(project, userPrincipal.getId());
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/workspace/{workspaceId}")
    @PreAuthorize("@securityService.hasWorkspaceRole(#workspaceId, 'VIEWER')")
    public ResponseEntity<List<ProjectEntity>> getWorkspaceProjects(
            @PathVariable("workspaceId") String workspaceId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        List<ProjectEntity> projects = projectService.getWorkspaceProjects(workspaceId, userPrincipal.getId());
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/{projectId}")
    @PreAuthorize("@securityService.hasProjectRole(#projectId, 'VIEWER')")
    public ResponseEntity<?> getProjectById(@PathVariable("projectId") String projectId) {
        try {
            ProjectEntity project = projectService.getProjectById(projectId);
            return ResponseEntity.ok(project);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{projectId}")
    @PreAuthorize("@securityService.hasProjectRole(#projectId, 'MEMBER')")
    public ResponseEntity<?> updateProject(
            @PathVariable("projectId") String projectId,
            @Valid @RequestBody ProjectEntity project) {
        try {
            project.setId(java.util.UUID.fromString(projectId));
            ProjectEntity updated = projectService.updateProject(project);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{projectId}")
    @PreAuthorize("@securityService.hasProjectRole(#projectId, 'ADMIN')")
    public ResponseEntity<?> deleteProject(
            @PathVariable("projectId") String projectId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            ProjectEntity project = projectService.getProjectById(projectId);
            boolean isWsAdmin = projectService.isWorkspaceAdmin(project.getWorkspaceId().toString(), userPrincipal.getId());
            if (!isWsAdmin) {
                return ResponseEntity.status(403).body(Map.of("error", "Chỉ Admin Workspace mới có quyền xóa dự án trực tiếp. Admin Dự án cần gửi yêu cầu xóa để được phê duyệt."));
            }
            projectService.deleteProject(projectId);
            return ResponseEntity.ok(Map.of("message", "Xóa dự án thành công"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{projectId}/files")
    @PreAuthorize("@securityService.hasProjectRole(#projectId, 'VIEWER')")
    public ResponseEntity<List<ProjectFileEntity>> getProjectFiles(@PathVariable("projectId") String projectId) {
        return ResponseEntity.ok(projectService.getProjectFiles(projectId));
    }

    @PostMapping("/{projectId}/files")
    @PreAuthorize("@securityService.hasProjectRole(#projectId, 'VIEWER')")
    public ResponseEntity<?> addProjectFile(
            @PathVariable("projectId") String projectId,
            @RequestBody ProjectFileEntity file,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            ProjectFileEntity saved = projectService.saveProjectFile(projectId, file, userPrincipal != null ? userPrincipal.getId() : null);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{projectId}/files/{fileId}")
    @PreAuthorize("@securityService.hasProjectRole(#projectId, 'VIEWER')")
    public ResponseEntity<?> deleteProjectFile(@PathVariable("projectId") String projectId, @PathVariable("fileId") String fileId) {
        try {
            projectService.deleteProjectFile(fileId);
            return ResponseEntity.ok(Map.of("message", "Xóa tệp tin thành công"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{projectId}/members")
    @PreAuthorize("@securityService.hasProjectRole(#projectId, 'VIEWER')")
    public ResponseEntity<List<com.example.smartmanager.workspaces.WorkspaceMemberDto>> getProjectMembers(@PathVariable("projectId") String projectId) {
        return ResponseEntity.ok(projectService.getProjectMembers(projectId));
    }

    @PostMapping("/{projectId}/deletion-request")
    @PreAuthorize("@securityService.hasProjectRole(#projectId, 'ADMIN')")
    public ResponseEntity<?> createDeletionRequest(
            @PathVariable("projectId") String projectId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            String reason = body.get("reason");
            ProjectDeletionRequestEntity request = projectService.createDeletionRequest(projectId, userPrincipal.getId(), reason);
            return ResponseEntity.ok(request);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/workspace/{workspaceId}/deletion-requests")
    @PreAuthorize("@securityService.hasWorkspaceRole(#workspaceId, 'ADMIN')")
    public ResponseEntity<?> getPendingDeletionRequests(@PathVariable("workspaceId") String workspaceId) {
        try {
            return ResponseEntity.ok(projectService.getPendingDeletionRequests(workspaceId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/deletion-requests/{requestId}/approve")
    public ResponseEntity<?> approveDeletionRequest(
            @PathVariable("requestId") String requestId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            projectService.approveDeletionRequest(requestId, userPrincipal.getId());
            return ResponseEntity.ok(Map.of("message", "Đã phê duyệt xóa dự án thành công"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/deletion-requests/{requestId}/reject")
    public ResponseEntity<?> rejectDeletionRequest(
            @PathVariable("requestId") String requestId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            projectService.rejectDeletionRequest(requestId, userPrincipal.getId());
            return ResponseEntity.ok(Map.of("message", "Đã từ chối yêu cầu xóa dự án"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
