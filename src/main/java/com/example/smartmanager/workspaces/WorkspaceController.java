package com.example.smartmanager.workspaces;

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
@RequestMapping("/api/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @PostMapping
    public ResponseEntity<?> createWorkspace(
            @Valid @RequestBody WorkspaceEntity workspace,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            WorkspaceEntity created = workspaceService.createWorkspace(workspace, userPrincipal.getId());
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<List<WorkspaceEntity>> getUserWorkspaces(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        List<WorkspaceEntity> workspaces = workspaceService.getUserWorkspaces(userPrincipal.getId());
        return ResponseEntity.ok(workspaces);
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("@securityService.hasWorkspaceRole(#workspaceId, 'ADMIN')")
    public ResponseEntity<?> addMember(
            @PathVariable("id") String workspaceId,
            @Valid @RequestBody AddMemberRequest request) {
        try {
            workspaceService.addMember(workspaceId, request.getEmail(), request.getRole());
            return ResponseEntity.ok(Map.of("message", "Thêm thành viên thành công"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("@securityService.hasWorkspaceRole(#workspaceId, 'VIEWER')")
    public ResponseEntity<List<WorkspaceMemberDto>> getWorkspaceMembers(
            @PathVariable("id") String workspaceId) {
        List<WorkspaceMemberDto> members = workspaceService.getWorkspaceMembers(workspaceId);
        return ResponseEntity.ok(members);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("@securityService.hasWorkspaceRole(#workspaceId, 'ADMIN')")
    public ResponseEntity<?> removeMember(
            @PathVariable("id") String workspaceId,
            @PathVariable("userId") String userId) {
        try {
            workspaceService.removeMember(workspaceId, userId);
            return ResponseEntity.ok(Map.of("message", "Xóa thành viên thành công"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/members/{userId}/role")
    @PreAuthorize("@securityService.hasWorkspaceRole(#workspaceId, 'ADMIN')")
    public ResponseEntity<?> updateMemberRole(
            @PathVariable("id") String workspaceId,
            @PathVariable("userId") String userId,
            @RequestBody Map<String, String> body) {
        try {
            String role = body.get("role");
            if (role == null || role.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Role không được để trống"));
            }
            workspaceService.updateMemberRole(workspaceId, userId, role);
            return ResponseEntity.ok(Map.of("message", "Cập nhật vai trò thành công"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/folders")
    @PreAuthorize("@securityService.hasWorkspaceRole(#workspaceId, 'VIEWER')")
    public ResponseEntity<List<WorkspaceFolderEntity>> getWorkspaceFolders(
            @PathVariable("id") String workspaceId,
            @RequestParam(value = "parentId", required = false) String parentId) {
        return ResponseEntity.ok(workspaceService.getWorkspaceFolders(workspaceId, parentId));
    }

    @PostMapping("/{id}/folders")
    @PreAuthorize("@securityService.hasWorkspaceRole(#workspaceId, 'VIEWER')")
    public ResponseEntity<?> createWorkspaceFolder(
            @PathVariable("id") String workspaceId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            String name = body.get("name");
            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Tên thư mục không được để trống"));
            }
            String parentId = body.get("parentId");
            String creatorId = userPrincipal != null ? userPrincipal.getId() : null;
            WorkspaceFolderEntity folder = workspaceService.createWorkspaceFolder(workspaceId, name, parentId, creatorId);
            return ResponseEntity.ok(folder);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}/folders/{folderId}")
    @PreAuthorize("@securityService.hasWorkspaceRole(#workspaceId, 'VIEWER')")
    public ResponseEntity<?> deleteWorkspaceFolder(
            @PathVariable("id") String workspaceId,
            @PathVariable("folderId") String folderId) {
        try {
            workspaceService.deleteWorkspaceFolder(folderId);
            return ResponseEntity.ok(Map.of("message", "Xóa thư mục thành công"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/files")
    @PreAuthorize("@securityService.hasWorkspaceRole(#workspaceId, 'VIEWER')")
    public ResponseEntity<List<WorkspaceFileEntity>> getWorkspaceFiles(
            @PathVariable("id") String workspaceId,
            @RequestParam(value = "folderId", required = false) String folderId) {
        return ResponseEntity.ok(workspaceService.getWorkspaceFiles(workspaceId, folderId));
    }

    @PostMapping("/{id}/files")
    @PreAuthorize("@securityService.hasWorkspaceRole(#workspaceId, 'VIEWER')")
    public ResponseEntity<?> addWorkspaceFile(
            @PathVariable("id") String workspaceId,
            @RequestBody WorkspaceFileEntity file,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            WorkspaceFileEntity saved = workspaceService.saveWorkspaceFile(workspaceId, file, userPrincipal != null ? userPrincipal.getId() : null);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}/files/{fileId}")
    @PreAuthorize("@securityService.hasWorkspaceRole(#workspaceId, 'VIEWER')")
    public ResponseEntity<?> deleteWorkspaceFile(
            @PathVariable("id") String workspaceId,
            @PathVariable("fileId") String fileId) {
        try {
            workspaceService.deleteWorkspaceFile(fileId);
            return ResponseEntity.ok(Map.of("message", "Xóa tệp tin thành công"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/all-accessible-documents")
    @PreAuthorize("@securityService.hasWorkspaceRole(#workspaceId, 'VIEWER')")
    public ResponseEntity<List<AccessibleDocumentDto>> getAllAccessibleDocuments(
            @PathVariable("id") String workspaceId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(workspaceService.getAllAccessibleDocuments(workspaceId, userPrincipal.getId()));
    }
}
