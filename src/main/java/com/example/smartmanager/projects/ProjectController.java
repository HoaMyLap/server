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

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.hasProjectRole(#projectId, 'VIEWER')")
    public ResponseEntity<?> getProjectById(@PathVariable("id") String projectId) {
        try {
            ProjectEntity project = projectService.getProjectById(projectId);
            return ResponseEntity.ok(project);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("@securityService.hasProjectRole(#projectId, 'MEMBER')")
    public ResponseEntity<?> updateProject(
            @PathVariable("id") String projectId,
            @Valid @RequestBody ProjectEntity project) {
        try {
            project.setId(java.util.UUID.fromString(projectId));
            ProjectEntity updated = projectService.updateProject(project);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@securityService.hasProjectRole(#projectId, 'ADMIN')")
    public ResponseEntity<?> deleteProject(@PathVariable("id") String projectId) {
        try {
            projectService.deleteProject(projectId);
            return ResponseEntity.ok(Map.of("message", "Xóa dự án thành công"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
