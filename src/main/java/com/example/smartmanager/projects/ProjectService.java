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

    public List<WorkspaceMemberDto> getProjectMembers(String projectId) {
        return projectMemberRepository.findProjectMembersWithDetails(UUID.fromString(projectId));
    }
}
