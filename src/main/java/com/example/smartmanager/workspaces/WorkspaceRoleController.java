package com.example.smartmanager.workspaces;

import lombok.RequiredArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}")
@RequiredArgsConstructor
public class WorkspaceRoleController {

    private final WorkspaceRoleRepository roleRepository;
    private final WorkspaceRolePermissionRepository rolePermissionRepository;
    private final WorkspaceMemberPermissionRepository memberPermissionRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    @Data
    @NoArgsConstructor
    public static class CreateRoleRequest {
        private String name;
        private String description;
        private List<String> permissions;
    }

    @Data
    @NoArgsConstructor
    public static class UpdateMemberRoleRequest {
        private String role;
        private String roleId; // UUID as string, or null
    }

    @Data
    @NoArgsConstructor
    public static class PermissionOverrideDto {
        private String permission;
        private boolean isAllowed;
    }

    @GetMapping("/roles")
    @PreAuthorize("@securityService.hasWorkspaceRole(#workspaceId, 'VIEWER')")
    public ResponseEntity<?> getWorkspaceRoles(@PathVariable("workspaceId") String workspaceId) {
        UUID wsId = UUID.fromString(workspaceId);
        List<WorkspaceRoleEntity> roles = roleRepository.findByWorkspaceId(wsId);
        
        List<Map<String, Object>> response = roles.stream().map(role -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", role.getId().toString());
            map.put("workspaceId", role.getWorkspaceId().toString());
            map.put("name", role.getName());
            map.put("description", role.getDescription());
            map.put("createdAt", role.getCreatedAt());
            
            List<String> perms = rolePermissionRepository.findByIdRoleId(role.getId())
                    .stream()
                    .map(p -> p.getId().getPermission())
                    .collect(Collectors.toList());
            map.put("permissions", perms);
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/roles")
    @PreAuthorize("@securityService.hasWorkspaceRole(#workspaceId, 'ADMIN')")
    @Transactional
    public ResponseEntity<?> createRole(
            @PathVariable("workspaceId") String workspaceId,
            @RequestBody CreateRoleRequest req) {
        if (req.getName() == null || req.getName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tên vai trò không được để trống"));
        }

        UUID wsId = UUID.fromString(workspaceId);
        WorkspaceRoleEntity role = new WorkspaceRoleEntity();
        role.setWorkspaceId(wsId);
        role.setName(req.getName().trim());
        role.setDescription(req.getDescription() != null ? req.getDescription().trim() : "");
        role.setCreatedAt(java.time.LocalDateTime.now());
        WorkspaceRoleEntity savedRole = roleRepository.save(role);

        if (req.getPermissions() != null) {
            for (String p : req.getPermissions()) {
                if (p != null && !p.isBlank()) {
                    WorkspaceRolePermissionId id = new WorkspaceRolePermissionId(savedRole.getId(), p.trim());
                    rolePermissionRepository.save(new WorkspaceRolePermissionEntity(id));
                }
            }
        }

        return ResponseEntity.ok(savedRole);
    }

    @PutMapping("/roles/{roleId}")
    @PreAuthorize("@securityService.hasWorkspaceRole(#workspaceId, 'ADMIN')")
    @Transactional
    public ResponseEntity<?> updateRole(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("roleId") String roleId,
            @RequestBody CreateRoleRequest req) {
        UUID rId = UUID.fromString(roleId);
        WorkspaceRoleEntity role = roleRepository.findById(rId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vai trò"));

        if (req.getName() == null || req.getName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tên vai trò không được để trống"));
        }

        role.setName(req.getName().trim());
        role.setDescription(req.getDescription() != null ? req.getDescription().trim() : "");
        roleRepository.save(role);

        // Refresh permissions
        rolePermissionRepository.deleteByIdRoleId(rId);
        if (req.getPermissions() != null) {
            for (String p : req.getPermissions()) {
                if (p != null && !p.isBlank()) {
                    WorkspaceRolePermissionId id = new WorkspaceRolePermissionId(role.getId(), p.trim());
                    rolePermissionRepository.save(new WorkspaceRolePermissionEntity(id));
                }
            }
        }

        return ResponseEntity.ok(role);
    }

    @DeleteMapping("/roles/{roleId}")
    @PreAuthorize("@securityService.hasWorkspaceRole(#workspaceId, 'ADMIN')")
    @Transactional
    public ResponseEntity<?> deleteRole(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("roleId") String roleId) {
        UUID rId = UUID.fromString(roleId);
        
        // Remove mappings in workspace_members
        List<WorkspaceMemberEntity> members = workspaceMemberRepository.findByIdWorkspaceId(UUID.fromString(workspaceId));
        for (WorkspaceMemberEntity m : members) {
            if (rId.equals(m.getRoleId())) {
                m.setRoleId(null);
                workspaceMemberRepository.save(m);
            }
        }

        rolePermissionRepository.deleteByIdRoleId(rId);
        roleRepository.deleteById(rId);
        return ResponseEntity.ok(Map.of("message", "Xóa vai trò thành công"));
    }

    @GetMapping("/members/{userId}/permissions")
    @PreAuthorize("@securityService.hasWorkspaceRole(#workspaceId, 'ADMIN')")
    public ResponseEntity<?> getMemberPermissions(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("userId") String userId) {
        UUID wsId = UUID.fromString(workspaceId);
        UUID uId = UUID.fromString(userId);

        List<WorkspaceMemberPermissionEntity> overrides = memberPermissionRepository.findByIdWorkspaceIdAndIdUserId(wsId, uId);
        List<PermissionOverrideDto> response = overrides.stream().map(o -> {
            PermissionOverrideDto dto = new PermissionOverrideDto();
            dto.setPermission(o.getId().getPermission());
            dto.setAllowed(o.isAllowed());
            return dto;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/members/{userId}/permissions")
    @PreAuthorize("@securityService.hasWorkspaceRole(#workspaceId, 'ADMIN')")
    @Transactional
    public ResponseEntity<?> saveMemberPermissions(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("userId") String userId,
            @RequestBody List<PermissionOverrideDto> req) {
        UUID wsId = UUID.fromString(workspaceId);
        UUID uId = UUID.fromString(userId);

        memberPermissionRepository.deleteByIdWorkspaceIdAndIdUserId(wsId, uId);
        if (req != null) {
            for (PermissionOverrideDto dto : req) {
                if (dto.getPermission() != null && !dto.getPermission().isBlank()) {
                    WorkspaceMemberPermissionId id = new WorkspaceMemberPermissionId(wsId, uId, dto.getPermission().trim());
                    WorkspaceMemberPermissionEntity entity = new WorkspaceMemberPermissionEntity(id, dto.isAllowed());
                    memberPermissionRepository.save(entity);
                }
            }
        }

        return ResponseEntity.ok(Map.of("message", "Lưu quyền thành viên thành công"));
    }

    @PutMapping("/members/{userId}/role")
    @PreAuthorize("@securityService.hasWorkspaceRole(#workspaceId, 'ADMIN')")
    @Transactional
    public ResponseEntity<?> updateMemberRole(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("userId") String userId,
            @RequestBody UpdateMemberRoleRequest req) {
        UUID wsId = UUID.fromString(workspaceId);
        UUID uId = UUID.fromString(userId);

        WorkspaceMemberEntity member = workspaceMemberRepository.findById(new WorkspaceMemberId(wsId, uId))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thành viên trong Workspace"));

        if (req.getRole() != null && !req.getRole().isBlank()) {
            member.setRole(req.getRole().trim());
        }
        
        if (req.getRoleId() != null && !req.getRoleId().isBlank()) {
            member.setRoleId(UUID.fromString(req.getRoleId().trim()));
        } else {
            member.setRoleId(null);
        }

        workspaceMemberRepository.save(member);
        return ResponseEntity.ok(Map.of("message", "Cập nhật vai trò thành công"));
    }
}
