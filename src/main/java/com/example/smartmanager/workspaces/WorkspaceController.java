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
}
