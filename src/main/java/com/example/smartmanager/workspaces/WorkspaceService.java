package com.example.smartmanager.workspaces;

import com.example.smartmanager.email.EmailJob;
import com.example.smartmanager.email.EmailWorker;
import com.example.smartmanager.notifications.NotificationService;
import com.example.smartmanager.users.UserEntity;
import com.example.smartmanager.users.UserRepository;
import com.example.smartmanager.projects.ProjectRepository;
import com.example.smartmanager.projects.ProjectMemberRepository;
import com.example.smartmanager.projects.ProjectMemberId;
import com.example.smartmanager.projects.ProjectMemberEntity;
import com.example.smartmanager.projects.ProjectEntity;
import com.example.smartmanager.projects.ProjectFileEntity;
import com.example.smartmanager.projects.ProjectFileRepository;
import com.example.smartmanager.tasks.TaskEntity;
import com.example.smartmanager.tasks.TaskFileEntity;
import com.example.smartmanager.tasks.TaskFileRepository;
import com.example.smartmanager.tasks.TaskRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final WorkspaceFolderRepository workspaceFolderRepository;
    private final WorkspaceFileRepository workspaceFileRepository;
    private final ProjectFileRepository projectFileRepository;
    private final TaskFileRepository taskFileRepository;
    private final TaskRepository taskRepository;

    public WorkspaceService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            @Qualifier("redisTemplate") RedisTemplate<String, Object> redisTemplate,
            SimpMessagingTemplate messagingTemplate,
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository,
            WorkspaceFolderRepository workspaceFolderRepository,
            WorkspaceFileRepository workspaceFileRepository,
            ProjectFileRepository projectFileRepository,
            TaskFileRepository taskFileRepository,
            TaskRepository taskRepository) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.workspaceFolderRepository = workspaceFolderRepository;
        this.workspaceFileRepository = workspaceFileRepository;
        this.projectFileRepository = projectFileRepository;
        this.taskFileRepository = taskFileRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional
    public WorkspaceEntity createWorkspace(WorkspaceEntity workspace, String ownerId) {
        workspace.setOwnerId(UUID.fromString(ownerId));
        WorkspaceEntity savedWorkspace = workspaceRepository.save(workspace);

        // Thêm người tạo làm ADMIN trong bảng workspace_members
        WorkspaceMemberId memberId = new WorkspaceMemberId(savedWorkspace.getId(), UUID.fromString(ownerId));
        WorkspaceMemberEntity member = new WorkspaceMemberEntity(memberId, "ADMIN", java.time.LocalDateTime.now());
        workspaceMemberRepository.save(member);

        return savedWorkspace;
    }

    @Transactional
    public void addMember(String workspaceId, String email, String role) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thành viên với email này"));

        WorkspaceMemberId memberId = new WorkspaceMemberId(UUID.fromString(workspaceId), user.getId());
        
        if (workspaceMemberRepository.existsById(memberId)) {
            throw new IllegalArgumentException("Thành viên đã ở trong không gian làm việc này rồi");
        }

        WorkspaceMemberEntity member = new WorkspaceMemberEntity(memberId, role, java.time.LocalDateTime.now());
        workspaceMemberRepository.save(member);

        // Lấy tên Workspace
        WorkspaceEntity ws = workspaceRepository.findById(UUID.fromString(workspaceId)).orElse(null);
        String wsName = ws != null ? ws.getName() : "Workspace";

        // Thêm người dùng vào tất cả dự án hiện có của Workspace
        try {
            UUID wsId = UUID.fromString(workspaceId);
            List<ProjectEntity> allProjects = projectRepository.findByWorkspaceId(wsId);
            for (ProjectEntity p : allProjects) {
                ProjectMemberId pmId = new ProjectMemberId(p.getId(), user.getId());
                projectMemberRepository.save(new ProjectMemberEntity(pmId, role, java.time.LocalDateTime.now()));
            }

            // Phát tin nhắn realtime ADD_MEMBER qua WebSocket
            java.util.Map<String, Object> socketMsg = new java.util.HashMap<>();
            socketMsg.put("action", "ADD_MEMBER");
            socketMsg.put("workspaceId", workspaceId);
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("userId", user.getId().toString());
            payload.put("email", user.getEmail());
            payload.put("fullname", user.getFullname());
            payload.put("avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "");
            payload.put("role", role);
            socketMsg.put("payload", payload);

            // Gửi tới workspace topic
            messagingTemplate.convertAndSend("/topic/workspaces/" + workspaceId, socketMsg);

            // Gửi tới từng dự án trong workspace
            for (ProjectEntity p : allProjects) {
                java.util.Map<String, Object> pSocketMsg = new java.util.HashMap<>();
                pSocketMsg.put("action", "ADD_MEMBER");
                pSocketMsg.put("projectId", p.getId().toString());
                pSocketMsg.put("payload", payload);
                messagingTemplate.convertAndSend("/topic/projects/" + p.getId().toString(), pSocketMsg);
            }
        } catch (Exception e) {
            System.err.println("Failed to sync new workspace member to projects or broadcast websocket: " + e.getMessage());
        }

        // 1. Tạo thông báo hệ thống
        notificationService.createNotification(
                user.getId().toString(),
                "Bạn được mời vào Workspace mới",
                "Bạn đã được thêm vào không gian làm việc \"" + wsName + "\" với vai trò: " + role,
                "WORKSPACE"
        );

        // 2. Đẩy Email Background Job vào Redis Queue
        EmailJob job = new EmailJob(
                email,
                workspaceId,
                wsName,
                role,
                "Admin",
                "Lời mời tham gia không gian làm việc " + wsName,
                "Bạn vừa được mời tham gia không gian làm việc " + wsName + " với vai trò " + role
        );
        redisTemplate.opsForList().leftPush(EmailWorker.QUEUE_NAME, job);
    }

    public List<WorkspaceEntity> getUserWorkspaces(String userId) {
        List<WorkspaceMemberEntity> memberships = workspaceMemberRepository.findByIdUserId(UUID.fromString(userId));
        List<UUID> workspaceIds = memberships.stream().map(m -> m.getId().getWorkspaceId()).toList();
        return workspaceRepository.findAllById(workspaceIds);
    }

    public List<WorkspaceMemberDto> getWorkspaceMembers(String workspaceId) {
        return workspaceMemberRepository.findMembersWithDetails(UUID.fromString(workspaceId));
    }

    @Transactional
    public void removeMember(String workspaceId, String userId) {
        WorkspaceMemberId memberId = new WorkspaceMemberId(UUID.fromString(workspaceId), UUID.fromString(userId));
        if (!workspaceMemberRepository.existsById(memberId)) {
            throw new IllegalArgumentException("Thành viên không tồn tại trong không gian làm việc này");
        }
        workspaceMemberRepository.deleteById(memberId);

        // Cũng xóa khỏi project_members của tất cả dự án thuộc Workspace
        try {
            UUID wsId = UUID.fromString(workspaceId);
            UUID uId = UUID.fromString(userId);
            List<ProjectEntity> allProjects = projectRepository.findByWorkspaceId(wsId);
            for (ProjectEntity p : allProjects) {
                projectMemberRepository.deleteById(new ProjectMemberId(p.getId(), uId));
            }

            // Phát tin nhắn realtime REMOVE_MEMBER qua WebSocket
            java.util.Map<String, Object> socketMsg = new java.util.HashMap<>();
            socketMsg.put("action", "REMOVE_MEMBER");
            socketMsg.put("workspaceId", workspaceId);
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("userId", userId);
            socketMsg.put("payload", payload);

            // Gửi tới workspace
            messagingTemplate.convertAndSend("/topic/workspaces/" + workspaceId, socketMsg);

            // Gửi tới từng dự án
            for (ProjectEntity p : allProjects) {
                java.util.Map<String, Object> pSocketMsg = new java.util.HashMap<>();
                pSocketMsg.put("action", "REMOVE_MEMBER");
                pSocketMsg.put("projectId", p.getId().toString());
                pSocketMsg.put("payload", payload);
                messagingTemplate.convertAndSend("/topic/projects/" + p.getId().toString(), pSocketMsg);
            }
        } catch (Exception e) {
            System.err.println("Failed to clean project members or broadcast remove member: " + e.getMessage());
        }
    }

    @Transactional
    public void updateMemberRole(String workspaceId, String userId, String newRole) {
        WorkspaceMemberId memberId = new WorkspaceMemberId(UUID.fromString(workspaceId), UUID.fromString(userId));
        WorkspaceMemberEntity member = workspaceMemberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Thành viên không tồn tại trong không gian làm việc này"));
        member.setRole(newRole);
        workspaceMemberRepository.save(member);

        // Cũng cập nhật vai trò trong project_members của tất cả các dự án thuộc Workspace
        try {
            UUID wsId = UUID.fromString(workspaceId);
            UUID uId = UUID.fromString(userId);
            List<ProjectEntity> allProjects = projectRepository.findByWorkspaceId(wsId);
            for (ProjectEntity p : allProjects) {
                ProjectMemberId pmId = new ProjectMemberId(p.getId(), uId);
                projectMemberRepository.findById(pmId).ifPresent(pm -> {
                    pm.setRole(newRole);
                    projectMemberRepository.save(pm);
                });
            }

            // Phát tin nhắn realtime UPDATE_MEMBER qua WebSocket
            java.util.Map<String, Object> socketMsg = new java.util.HashMap<>();
            socketMsg.put("action", "UPDATE_MEMBER");
            socketMsg.put("workspaceId", workspaceId);
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("userId", userId);
            payload.put("role", newRole);
            socketMsg.put("payload", payload);

            // Gửi tới workspace
            messagingTemplate.convertAndSend("/topic/workspaces/" + workspaceId, socketMsg);

            // Gửi tới từng dự án
            for (ProjectEntity p : allProjects) {
                java.util.Map<String, Object> pSocketMsg = new java.util.HashMap<>();
                pSocketMsg.put("action", "UPDATE_MEMBER");
                pSocketMsg.put("projectId", p.getId().toString());
                pSocketMsg.put("payload", payload);
                messagingTemplate.convertAndSend("/topic/projects/" + p.getId().toString(), pSocketMsg);
            }
        } catch (Exception e) {
            System.err.println("Failed to update project roles or broadcast update role: " + e.getMessage());
        }
    }

    public List<WorkspaceFolderEntity> getWorkspaceFolders(String workspaceId, String parentId) {
        UUID wsId = UUID.fromString(workspaceId);
        if (parentId == null || parentId.trim().isEmpty() || "null".equalsIgnoreCase(parentId)) {
            return workspaceFolderRepository.findByWorkspaceIdAndParentIdIsNull(wsId);
        } else {
            return workspaceFolderRepository.findByWorkspaceIdAndParentId(wsId, UUID.fromString(parentId));
        }
    }

    public List<WorkspaceFileEntity> getWorkspaceFiles(String workspaceId, String folderId) {
        UUID wsId = UUID.fromString(workspaceId);
        if (folderId == null || folderId.trim().isEmpty() || "null".equalsIgnoreCase(folderId)) {
            return workspaceFileRepository.findByWorkspaceIdAndFolderIdIsNull(wsId);
        } else {
            return workspaceFileRepository.findByWorkspaceIdAndFolderId(wsId, UUID.fromString(folderId));
        }
    }

    @Transactional
    public WorkspaceFolderEntity createWorkspaceFolder(String workspaceId, String name, String parentId, String creatorId) {
        WorkspaceFolderEntity folder = new WorkspaceFolderEntity();
        folder.setWorkspaceId(UUID.fromString(workspaceId));
        folder.setName(name);
        if (parentId != null && !parentId.trim().isEmpty() && !"null".equalsIgnoreCase(parentId)) {
            folder.setParentId(UUID.fromString(parentId));
        }
        if (creatorId != null && !creatorId.trim().isEmpty() && !"null".equalsIgnoreCase(creatorId)) {
            folder.setCreatedBy(UUID.fromString(creatorId));
        }
        folder.setCreatedAt(java.time.LocalDateTime.now());
        return workspaceFolderRepository.save(folder);
    }

    @Transactional
    public void deleteWorkspaceFolder(String folderId) {
        deleteWorkspaceFolderRecursive(UUID.fromString(folderId));
    }

    private void deleteWorkspaceFolderRecursive(UUID folderId) {
        java.util.Optional<WorkspaceFolderEntity> folderOpt = workspaceFolderRepository.findById(folderId);
        if (folderOpt.isEmpty()) return;
        UUID workspaceId = folderOpt.get().getWorkspaceId();
        
        List<WorkspaceFolderEntity> subfolders = workspaceFolderRepository.findByWorkspaceIdAndParentId(workspaceId, folderId);
        for (WorkspaceFolderEntity sub : subfolders) {
            deleteWorkspaceFolderRecursive(sub.getId());
        }
        
        List<WorkspaceFileEntity> files = workspaceFileRepository.findByFolderId(folderId);
        workspaceFileRepository.deleteAll(files);
        
        workspaceFolderRepository.deleteById(folderId);
    }

    @Transactional
    public WorkspaceFileEntity saveWorkspaceFile(String workspaceId, WorkspaceFileEntity file, String uploaderId) {
        file.setWorkspaceId(UUID.fromString(workspaceId));
        if (uploaderId != null) {
            file.setUploaderId(UUID.fromString(uploaderId));
        }
        file.setUploadedAt(java.time.LocalDateTime.now());
        return workspaceFileRepository.save(file);
    }

    @Transactional
    public void deleteWorkspaceFile(String fileId) {
        workspaceFileRepository.deleteById(UUID.fromString(fileId));
    }

    public List<AccessibleDocumentDto> getAllAccessibleDocuments(String workspaceId, String userId) {
        UUID wsId = UUID.fromString(workspaceId);
        UUID uId = UUID.fromString(userId);
        
        List<AccessibleDocumentDto> documents = new java.util.ArrayList<>();
        
        WorkspaceMemberEntity member = workspaceMemberRepository.findById(new WorkspaceMemberId(wsId, uId)).orElse(null);
        if (member == null) {
            return documents;
        }
        boolean isWorkspaceAdmin = "ADMIN".equalsIgnoreCase(member.getRole());
        
        List<WorkspaceFileEntity> wsFiles = workspaceFileRepository.findByWorkspaceId(wsId);
        for (WorkspaceFileEntity file : wsFiles) {
            documents.add(new AccessibleDocumentDto(
                    file.getId().toString(),
                    file.getName(),
                    file.getUrl(),
                    file.getSize(),
                    file.getType(),
                    file.getUploadedAt(),
                    "Workspace",
                    null
            ));
        }
        
        List<ProjectEntity> allProjects = projectRepository.findByWorkspaceId(wsId);
        for (ProjectEntity project : allProjects) {
            boolean hasProjectAccess = isWorkspaceAdmin;
            if (!hasProjectAccess) {
                hasProjectAccess = projectMemberRepository.findById(
                        new com.example.smartmanager.projects.ProjectMemberId(project.getId(), uId)).isPresent();
            }
            
            if (hasProjectAccess) {
                List<ProjectFileEntity> projectFiles = 
                        projectFileRepository.findByProjectIdOrderByUploadedAtDesc(project.getId());
                for (ProjectFileEntity file : projectFiles) {
                    documents.add(new AccessibleDocumentDto(
                            file.getId().toString(),
                            file.getName(),
                            file.getUrl(),
                            file.getSize(),
                            file.getType(),
                            file.getUploadedAt(),
                            "Dự án: " + project.getName(),
                            project.getId().toString()
                    ));
                }
                
                List<TaskEntity> tasks = taskRepository.findByProjectId(project.getId());
                for (TaskEntity task : tasks) {
                    List<TaskFileEntity> taskFiles = 
                            taskFileRepository.findByTaskIdOrderByUploadedAtDesc(task.getId());
                    for (TaskFileEntity file : taskFiles) {
                        documents.add(new AccessibleDocumentDto(
                                file.getId().toString(),
                                file.getName(),
                                file.getUrl(),
                                file.getSize(),
                                file.getType(),
                                file.getUploadedAt(),
                                "Công việc (" + task.getTitle() + ") trong dự án \"" + project.getName() + "\"",
                                project.getId().toString()
                        ));
                    }
                }
            }
        }
        
        documents.sort((d1, d2) -> d2.getUploadedAt().compareTo(d1.getUploadedAt()));
        return documents;
    }

    @Transactional
    public void deleteWorkspace(String workspaceId) {
        UUID wsId = UUID.fromString(workspaceId);
        List<ProjectEntity> projects = projectRepository.findByWorkspaceId(wsId);
        for (ProjectEntity project : projects) {
            projectRepository.delete(project);
        }
        workspaceMemberRepository.deleteAll(workspaceMemberRepository.findByIdWorkspaceId(wsId));
        workspaceFileRepository.deleteByWorkspaceId(wsId);
        workspaceFolderRepository.deleteByWorkspaceId(wsId);
        workspaceRepository.deleteById(wsId);
    }
}
