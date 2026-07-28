package com.example.smartmanager.projects;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;

    @Transactional
    public ProjectEntity createProject(ProjectEntity project) {
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());
        return projectRepository.save(project);
    }

    public List<ProjectEntity> getWorkspaceProjects(String workspaceId) {
        return projectRepository.findByWorkspaceId(UUID.fromString(workspaceId));
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

    @Transactional
    public void deleteProject(String projectId) {
        ProjectEntity project = getProjectById(projectId);
        projectRepository.delete(project);
    }
}
