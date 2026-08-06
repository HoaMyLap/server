package com.example.smartmanager.projects;

import com.example.smartmanager.workspaces.WorkspaceMemberRepository;
import com.example.smartmanager.workspaces.WorkspaceMemberDto;
import com.example.smartmanager.workspaces.WorkspaceMemberEntity;
import com.example.smartmanager.notifications.NotificationService;
import com.example.smartmanager.users.UserRepository;
import com.example.smartmanager.users.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final ProjectDeletionRequestRepository projectDeletionRequestRepository;
    private final ProjectFolderRepository projectFolderRepository;

    @Transactional
    public ProjectEntity createProject(ProjectEntity project, String userId) {
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());
        ProjectEntity saved = projectRepository.save(project);

        // Thêm tất cả các thành viên hiện tại của Workspace vào dự án mới này
        try {
            List<WorkspaceMemberEntity> wsMembers = workspaceMemberRepository.findByIdWorkspaceId(saved.getWorkspaceId());
            for (WorkspaceMemberEntity wm : wsMembers) {
                ProjectMemberId pmId = new ProjectMemberId(saved.getId(), wm.getId().getUserId());
                projectMemberRepository.save(new ProjectMemberEntity(pmId, wm.getRole(), LocalDateTime.now()));
            }
        } catch (Exception e) {
            System.err.println("Failed to populate members for new project: " + e.getMessage());
        }

        // Đảm bảo người tạo dự án có vai trò ADMIN
        if (userId != null) {
            ProjectMemberId pmId = new ProjectMemberId(saved.getId(), UUID.fromString(userId));
            projectMemberRepository.save(new ProjectMemberEntity(pmId, "ADMIN", LocalDateTime.now()));
        }

        // Gửi thông báo đến tất cả các thành viên tham gia trong Workspace (ngoại trừ người tạo)
        try {
            String creatorName = "Admin";
            if (userId != null) {
                Optional<UserEntity> creatorOpt = userRepository.findById(UUID.fromString(userId));
                if (creatorOpt.isPresent()) {
                    creatorName = creatorOpt.get().getFullname();
                }
            }

            List<WorkspaceMemberEntity> wsMembers = workspaceMemberRepository.findByIdWorkspaceId(saved.getWorkspaceId());
            String title = "Dự án mới được tạo";
            String content = creatorName + " đã tạo dự án mới \"" + saved.getName() + "\" trong Workspace.";

            for (WorkspaceMemberEntity wm : wsMembers) {
                if (userId == null || !wm.getId().getUserId().toString().equals(userId)) {
                    notificationService.createNotification(
                        wm.getId().getUserId().toString(),
                        title,
                        content,
                        "PROJECT"
                    );
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to send project creation notifications: " + e.getMessage());
        }

        return saved;
    }

    public List<ProjectEntity> getWorkspaceProjects(String workspaceId, String userId) {
        UUID wsUuid = UUID.fromString(workspaceId);
        UUID userUuid = UUID.fromString(userId);

        Optional<String> roleOpt = workspaceMemberRepository.findRoleByWorkspaceIdAndUserId(wsUuid, userUuid);
        if (roleOpt.isPresent()) {
            String role = roleOpt.get();
            if ("ADMIN".equalsIgnoreCase(role) || "MEMBER".equalsIgnoreCase(role)) {
                // Workspace Admin/Member xem được tất cả các dự án trong workspace đó
                return projectRepository.findByWorkspaceId(wsUuid);
            } else {
                // Workspace Viewer (chỉ được mời vào dự án riêng lẻ) chỉ xem được các dự án mà họ thuộc project_members
                return projectMemberRepository.findUserProjectsInWorkspace(wsUuid, userUuid);
            }
        }

        return java.util.Collections.emptyList();
    }

    public ProjectEntity getProjectById(String projectId) {
        return projectRepository.findById(UUID.fromString(projectId))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy dự án với ID: " + projectId));
    }

    @Transactional
    public ProjectEntity updateProject(ProjectEntity projectDetails) {
        ProjectEntity project = getProjectById(projectDetails.getId().toString());
        project.setName(projectDetails.getName());
        project.setDescription(projectDetails.getDescription());
        if (projectDetails.getStatus() != null) {
            project.setStatus(projectDetails.getStatus());
        }
        project.setUpdatedAt(LocalDateTime.now());
        return projectRepository.save(project);
    }

    private final ProjectFileRepository projectFileRepository;

    public List<ProjectFileEntity> getProjectFiles(String projectId) {
        return projectFileRepository.findByProjectIdOrderByUploadedAtDesc(UUID.fromString(projectId));
    }

    public List<ProjectFileEntity> getProjectFilesFiltered(String projectId, String folderId) {
        UUID projId = UUID.fromString(projectId);
        if (folderId == null || folderId.trim().isEmpty() || "null".equalsIgnoreCase(folderId)) {
            return projectFileRepository.findByProjectIdAndFolderIdIsNullOrderByUploadedAtDesc(projId);
        } else {
            return projectFileRepository.findByProjectIdAndFolderIdOrderByUploadedAtDesc(projId, UUID.fromString(folderId));
        }
    }

    public List<ProjectFolderEntity> getProjectFolders(String projectId, String parentId) {
        UUID projId = UUID.fromString(projectId);
        if (parentId == null || parentId.trim().isEmpty() || "null".equalsIgnoreCase(parentId)) {
            return projectFolderRepository.findByProjectIdAndParentIdIsNull(projId);
        } else {
            return projectFolderRepository.findByProjectIdAndParentId(projId, UUID.fromString(parentId));
        }
    }

    @Transactional
    public ProjectFolderEntity createFolder(String projectId, String name, String parentId, String creatorId) {
        ProjectFolderEntity folder = new ProjectFolderEntity();
        folder.setProjectId(UUID.fromString(projectId));
        folder.setName(name);
        if (parentId != null && !parentId.trim().isEmpty() && !"null".equalsIgnoreCase(parentId)) {
            folder.setParentId(UUID.fromString(parentId));
        }
        if (creatorId != null && !creatorId.trim().isEmpty() && !"null".equalsIgnoreCase(creatorId)) {
            folder.setCreatedBy(UUID.fromString(creatorId));
        }
        folder.setCreatedAt(LocalDateTime.now());
        return projectFolderRepository.save(folder);
    }

    @Transactional
    public void deleteFolder(String folderId) {
        deleteFolderRecursive(UUID.fromString(folderId));
    }

    private void deleteFolderRecursive(UUID folderId) {
        Optional<ProjectFolderEntity> folderOpt = projectFolderRepository.findById(folderId);
        if (folderOpt.isEmpty()) return;
        UUID projectId = folderOpt.get().getProjectId();
        
        List<ProjectFolderEntity> subfolders = projectFolderRepository.findByProjectIdAndParentId(projectId, folderId);
        for (ProjectFolderEntity sub : subfolders) {
            deleteFolderRecursive(sub.getId());
        }
        
        List<ProjectFileEntity> files = projectFileRepository.findByFolderId(folderId);
        projectFileRepository.deleteAll(files);
        
        projectFolderRepository.deleteById(folderId);
    }

    @Transactional
    public ProjectFileEntity saveProjectFile(String projectId, ProjectFileEntity file, String uploaderId) {
        file.setProjectId(UUID.fromString(projectId));
        if (uploaderId != null) {
            file.setUploaderId(UUID.fromString(uploaderId));
        }
        file.setUploadedAt(LocalDateTime.now());
        return projectFileRepository.save(file);
    }

    @Transactional
    public void deleteProjectFile(String fileId) {
        projectFileRepository.deleteById(UUID.fromString(fileId));
    }

    @Transactional
    public void deleteProject(String projectId) {
        ProjectEntity project = getProjectById(projectId);
        projectRepository.delete(project);
    }

    @Transactional
    public List<WorkspaceMemberDto> getProjectMembers(String projectId) {
        UUID pId = UUID.fromString(projectId);
        ProjectEntity project = getProjectById(projectId);
        
        // Tự động đồng bộ tất cả thành viên trong Workspace (bao gồm Admin và các thành viên) vào dự án này nếu chưa có trong project_members
        try {
            List<WorkspaceMemberEntity> wsMembers = workspaceMemberRepository.findByIdWorkspaceId(project.getWorkspaceId());
            for (WorkspaceMemberEntity wm : wsMembers) {
                ProjectMemberId pmId = new ProjectMemberId(pId, wm.getId().getUserId());
                if (!projectMemberRepository.existsById(pmId)) {
                    String role = "ADMIN".equalsIgnoreCase(wm.getRole()) ? "ADMIN" : (wm.getRole() != null ? wm.getRole() : "MEMBER");
                    projectMemberRepository.save(new ProjectMemberEntity(pmId, role, LocalDateTime.now()));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to sync workspace members to project: " + e.getMessage());
        }

        return projectMemberRepository.findProjectMembersWithDetails(pId);
    }

    @Transactional
    public ProjectDeletionRequestEntity createDeletionRequest(String projectId, String userId, String reason) {
        ProjectEntity project = getProjectById(projectId);
        
        project.setStatus("DELETION_PENDING");
        project.setUpdatedAt(LocalDateTime.now());
        projectRepository.save(project);

        ProjectDeletionRequestEntity request = new ProjectDeletionRequestEntity();
        request.setProjectId(project.getId());
        request.setRequesterId(UUID.fromString(userId));
        request.setReason(reason);
        request.setStatus("PENDING");
        request.setCreatedAt(LocalDateTime.now());
        ProjectDeletionRequestEntity savedRequest = projectDeletionRequestRepository.save(request);

        try {
            String requesterName = userRepository.findById(UUID.fromString(userId))
                    .map(UserEntity::getFullname).orElse("Admin Dự án");

            List<WorkspaceMemberEntity> wsMembers = workspaceMemberRepository.findByIdWorkspaceId(project.getWorkspaceId());
            for (WorkspaceMemberEntity wm : wsMembers) {
                if ("ADMIN".equalsIgnoreCase(wm.getRole())) {
                    notificationService.createNotification(
                        wm.getId().getUserId().toString(),
                        "Yêu cầu xóa dự án cần phê duyệt",
                        requesterName + " đã gửi yêu cầu xóa dự án \"" + project.getName() + "\". Vui lòng phê duyệt.",
                        "PROJECT"
                    );
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to send deletion request notifications: " + e.getMessage());
        }

        return savedRequest;
    }

    public List<ProjectDeletionRequestEntity> getPendingDeletionRequests(String workspaceId) {
        return projectDeletionRequestRepository.findByWorkspaceIdAndStatus(UUID.fromString(workspaceId), "PENDING");
    }

    @Transactional
    public void approveDeletionRequest(String requestId, String adminId) {
        ProjectDeletionRequestEntity request = projectDeletionRequestRepository.findById(UUID.fromString(requestId))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu xóa dự án"));
        
        if (!"PENDING".equals(request.getStatus())) {
            throw new IllegalStateException("Yêu cầu này đã được xử lý");
        }

        ProjectEntity project = getProjectById(request.getProjectId().toString());
        
        // Xác minh người phê duyệt là Workspace Admin
        UUID wsId = project.getWorkspaceId();
        workspaceMemberRepository.findById(new com.example.smartmanager.workspaces.WorkspaceMemberId(wsId, UUID.fromString(adminId)))
                .filter(m -> "ADMIN".equalsIgnoreCase(m.getRole()))
                .orElseThrow(() -> new IllegalArgumentException("Chỉ Admin Workspace mới có quyền phê duyệt yêu cầu này"));

        request.setStatus("APPROVED");
        projectDeletionRequestRepository.save(request);

        projectRepository.delete(project);

        try {
            notificationService.createNotification(
                request.getRequesterId().toString(),
                "Yêu cầu xóa dự án đã được chấp thuận",
                "Yêu cầu xóa dự án của bạn đã được Admin Workspace phê duyệt và thực hiện.",
                "PROJECT"
            );
        } catch (Exception e) {
            System.err.println("Failed to send deletion approval notification: " + e.getMessage());
        }
    }

    @Transactional
    public void rejectDeletionRequest(String requestId, String adminId) {
        ProjectDeletionRequestEntity request = projectDeletionRequestRepository.findById(UUID.fromString(requestId))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu xóa dự án"));

        if (!"PENDING".equals(request.getStatus())) {
            throw new IllegalStateException("Yêu cầu này đã được xử lý");
        }

        ProjectEntity project = getProjectById(request.getProjectId().toString());

        // Xác minh người từ chối là Workspace Admin
        UUID wsId = project.getWorkspaceId();
        workspaceMemberRepository.findById(new com.example.smartmanager.workspaces.WorkspaceMemberId(wsId, UUID.fromString(adminId)))
                .filter(m -> "ADMIN".equalsIgnoreCase(m.getRole()))
                .orElseThrow(() -> new IllegalArgumentException("Chỉ Admin Workspace mới có quyền từ chối yêu cầu này"));

        request.setStatus("REJECTED");
        projectDeletionRequestRepository.save(request);

        project.setStatus("ACTIVE");
        project.setUpdatedAt(LocalDateTime.now());
        projectRepository.save(project);

        try {
            notificationService.createNotification(
                request.getRequesterId().toString(),
                "Yêu cầu xóa dự án bị từ chối",
                "Yêu cầu xóa dự án \"" + project.getName() + "\" của bạn đã bị Admin Workspace từ chối.",
                "PROJECT"
            );
        } catch (Exception e) {
            System.err.println("Failed to send deletion rejection notification: " + e.getMessage());
        }
    }

    public boolean isWorkspaceAdmin(String workspaceId, String userId) {
        return workspaceMemberRepository.findById(new com.example.smartmanager.workspaces.WorkspaceMemberId(UUID.fromString(workspaceId), UUID.fromString(userId)))
                .map(m -> "ADMIN".equalsIgnoreCase(m.getRole()))
                .orElse(false);
    }
}
