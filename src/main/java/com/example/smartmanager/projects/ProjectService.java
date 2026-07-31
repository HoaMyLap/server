package com.example.smartmanager.projects;

import com.example.smartmanager.workspaces.WorkspaceMemberRepository;
import com.example.smartmanager.workspaces.WorkspaceMemberDto;
import com.example.smartmanager.workspaces.WorkspaceMemberEntity;
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

        return saved;
    }

    public List<ProjectEntity> getWorkspaceProjects(String workspaceId, String userId) {
        UUID wsUuid = UUID.fromString(workspaceId);
        UUID userUuid = UUID.fromString(userId);

        // Bất kỳ ai là thành viên của Workspace đều thấy và tham gia tất cả các dự án trong workspace đó
        Optional<String> roleOpt = workspaceMemberRepository.findRoleByWorkspaceIdAndUserId(wsUuid, userUuid);
        if (roleOpt.isPresent()) {
            return projectRepository.findByWorkspaceId(wsUuid);
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
