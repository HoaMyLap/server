package com.example.smartmanager.projects;

import com.example.smartmanager.workspaces.WorkspaceMemberRepository;
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

    @Transactional
    public ProjectEntity createProject(ProjectEntity project, String userId) {
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());
        ProjectEntity saved = projectRepository.save(project);

        // Thêm người tạo vào project_members
        if (userId != null) {
            ProjectMemberId pmId = new ProjectMemberId(saved.getId(), UUID.fromString(userId));
            projectMemberRepository.save(new ProjectMemberEntity(pmId, "ADMIN", LocalDateTime.now()));
        }

        return saved;
    }

    public List<ProjectEntity> getWorkspaceProjects(String workspaceId, String userId) {
        UUID wsUuid = UUID.fromString(workspaceId);
        UUID userUuid = UUID.fromString(userId);

        // Kiểm tra xem user có phải Admin của Workspace không
        Optional<String> roleOpt = workspaceMemberRepository.findRoleByWorkspaceIdAndUserId(wsUuid, userUuid);
        if (roleOpt.isPresent() && "ADMIN".equalsIgnoreCase(roleOpt.get())) {
            // Workspace Admin xem được tất cả các dự án trong workspace
            return projectRepository.findByWorkspaceId(wsUuid);
        }

        // Người dùng thông thường chỉ xem được các dự án mà họ thuộc danh sách project_members
        return projectMemberRepository.findUserProjectsInWorkspace(wsUuid, userUuid);
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
}
