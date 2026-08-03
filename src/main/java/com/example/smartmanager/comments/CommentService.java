package com.example.smartmanager.comments;

import com.example.smartmanager.tasks.TaskEntity;
import com.example.smartmanager.tasks.TaskLogEntity;
import com.example.smartmanager.tasks.TaskLogRepository;
import com.example.smartmanager.tasks.TaskRepository;
import com.example.smartmanager.tasks.TaskMessage;
import com.example.smartmanager.notifications.NotificationService;
import com.example.smartmanager.users.UserRepository;
import com.example.smartmanager.projects.ProjectRepository;
import com.example.smartmanager.projects.ProjectMemberRepository;
import com.example.smartmanager.projects.ProjectMemberId;
import com.example.smartmanager.workspaces.WorkspaceMemberRepository;
import com.example.smartmanager.workspaces.WorkspaceMemberId;
import com.example.smartmanager.workspaces.WorkspaceRoleRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final TaskLogRepository taskLogRepository;
    private final TaskRepository taskRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;
    private final RedisTemplate<String, Object> redisTemplate;
    
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceRoleRepository workspaceRoleRepository;
    private final ProjectMemberRepository projectMemberRepository;

    public CommentService(
            CommentRepository commentRepository,
            TaskLogRepository taskLogRepository,
            TaskRepository taskRepository,
            SimpMessagingTemplate messagingTemplate,
            NotificationService notificationService,
            @Qualifier("redisTemplate") RedisTemplate<String, Object> redisTemplate,
            UserRepository userRepository,
            ProjectRepository projectRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            WorkspaceRoleRepository workspaceRoleRepository,
            ProjectMemberRepository projectMemberRepository) {
        this.commentRepository = commentRepository;
        this.taskLogRepository = taskLogRepository;
        this.taskRepository = taskRepository;
        this.messagingTemplate = messagingTemplate;
        this.notificationService = notificationService;
        this.redisTemplate = redisTemplate;
        
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceRoleRepository = workspaceRoleRepository;
        this.projectMemberRepository = projectMemberRepository;
    }

    public void setViewingDiscussion(String taskId, String userId, boolean viewing) {
        String key = "task:viewers:" + taskId;
        if (viewing) {
            redisTemplate.opsForSet().add(key, userId);
            redisTemplate.expire(key, 15, TimeUnit.SECONDS);
        } else {
            redisTemplate.opsForSet().remove(key, userId);
        }
    }

    public boolean isUserViewingDiscussion(String taskId, String userId) {
        String key = "task:viewers:" + taskId;
        Boolean isMember = redisTemplate.opsForSet().isMember(key, userId);
        return Boolean.TRUE.equals(isMember);
    }

    @Transactional
    public CommentEntity createComment(CommentEntity comment, String userId) {
        comment.setUserId(UUID.fromString(userId));
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());
        CommentEntity saved = commentRepository.save(comment);

        // Lấy thông tin dự án để xác định kênh WebSocket tương ứng
        TaskEntity task = taskRepository.findById(saved.getTaskId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy công việc tương ứng để bình luận"));

        // Ghi nhật ký hoạt động
        TaskLogEntity log = new TaskLogEntity(
                null,
                saved.getTaskId(),
                UUID.fromString(userId),
                "ADD_COMMENT",
                null,
                "Đã thêm bình luận mới: " + (saved.getContent().length() > 50 ? saved.getContent().substring(0, 50) + "..." : saved.getContent()),
                LocalDateTime.now()
        );
        taskLogRepository.save(log);

        // Gửi thông báo đến những người nhận (loại trừ chính người viết bình luận và những người đang mở tab thảo luận)
        try {
            java.util.Set<UUID> recipients = new java.util.HashSet<>();
            
            // 1. Assignee
            if (task.getAssigneeId() != null) {
                recipients.add(task.getAssigneeId());
            }
            // 2. Creator
            if (task.getCreatorId() != null) {
                recipients.add(task.getCreatorId());
            }
            // 3. Parent comment author (nếu là câu trả lời)
            if (saved.getParentCommentId() != null) {
                commentRepository.findById(saved.getParentCommentId()).ifPresent(parent -> {
                    if (parent.getUserId() != null) {
                        recipients.add(parent.getUserId());
                    }
                });
            }
            // 4. Các thành viên đã từng bình luận trước đó
            List<CommentEntity> previousComments = commentRepository.findByTaskIdOrderByCreatedAtAsc(saved.getTaskId());
            for (CommentEntity c : previousComments) {
                if (c.getUserId() != null) {
                    recipients.add(c.getUserId());
                }
            }

            for (UUID rId : recipients) {
                if (!rId.toString().equals(userId)) {
                    if (!isUserViewingDiscussion(task.getId().toString(), rId.toString())) {
                        String title = "Thảo luận mới trong công việc";
                        String content = "Có phản hồi mới trong cuộc thảo luận công việc \"" + task.getTitle() + "\"";
                        
                        if (rId.equals(task.getAssigneeId())) {
                            title = "Bình luận mới trong công việc của bạn";
                            content = "Thành viên khác vừa bình luận vào công việc \"" + task.getTitle() + "\"";
                        } else if (rId.equals(task.getCreatorId())) {
                            title = "Bình luận mới trong công việc bạn tạo";
                            content = "Thành viên khác vừa bình luận vào công việc \"" + task.getTitle() + "\"";
                        }

                        notificationService.createNotificationWithNav(
                                rId.toString(),
                                title,
                                content,
                                "COMMENT",
                                null,
                                task.getProjectId(),
                                task.getId(),
                                saved.getId()
                        );
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to send comment notifications: " + e.getMessage());
        }

        // Enrich comment metadata (author name, role badges)
        enrichComment(saved, task);

        // Phát tin nhắn realtime qua WebSocket
        TaskMessage message = new TaskMessage(
                "ADD_COMMENT",
                saved.getTaskId().toString(),
                task.getProjectId().toString(),
                saved,
                userId
        );
        messagingTemplate.convertAndSend("/topic/projects/" + task.getProjectId().toString(), message);

        return saved;
    }

    public List<CommentEntity> getTaskComments(String taskId) {
        List<CommentEntity> all = commentRepository.findByTaskIdOrderByCreatedAtAsc(UUID.fromString(taskId));
        List<CommentEntity> roots = all.stream()
                .filter(c -> c.getParentCommentId() == null)
                .sorted((c1, c2) -> c2.getCreatedAt().compareTo(c1.getCreatedAt()))
                .toList();

        List<CommentEntity> replies = all.stream()
                .filter(c -> c.getParentCommentId() != null)
                .sorted((c1, c2) -> c1.getCreatedAt().compareTo(c2.getCreatedAt()))
                .toList();

        java.util.List<CommentEntity> sortedList = new java.util.ArrayList<>();
        for (CommentEntity root : roots) {
            sortedList.add(root);
            for (CommentEntity reply : replies) {
                if (reply.getParentCommentId().equals(root.getId())) {
                    sortedList.add(reply);
                }
            }
        }
        
        TaskEntity task = taskRepository.findById(UUID.fromString(taskId)).orElse(null);
        for (CommentEntity c : sortedList) {
            enrichComment(c, task);
        }
        
        return sortedList;
    }

    @Transactional
    public CommentEntity updateComment(String commentId, String newContent, String userId) {
        CommentEntity comment = commentRepository.findById(UUID.fromString(commentId))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bình luận"));

        if (!comment.getUserId().toString().equals(userId)) {
            throw new IllegalArgumentException("Bạn không có quyền chỉnh sửa bình luận này");
        }

        comment.setContent(newContent);
        comment.setUpdatedAt(LocalDateTime.now());
        CommentEntity saved = commentRepository.save(comment);

        TaskEntity task = taskRepository.findById(saved.getTaskId()).orElse(null);
        enrichComment(saved, task);
        if (task != null) {
            TaskMessage message = new TaskMessage(
                    "UPDATE_COMMENT",
                    saved.getTaskId().toString(),
                    task.getProjectId().toString(),
                    saved,
                    userId
            );
            messagingTemplate.convertAndSend("/topic/projects/" + task.getProjectId().toString(), message);
        }

        return saved;
    }

    @Transactional
    public void deleteComment(String commentId, String userId) {
        CommentEntity comment = commentRepository.findById(UUID.fromString(commentId))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bình luận"));

        if (!comment.getUserId().toString().equals(userId)) {
            throw new IllegalArgumentException("Bạn không có quyền xóa bình luận này");
        }

        commentRepository.delete(comment);

        TaskEntity task = taskRepository.findById(comment.getTaskId()).orElse(null);
        if (task != null) {
            TaskMessage message = new TaskMessage(
                    "DELETE_COMMENT",
                    comment.getTaskId().toString(),
                    task.getProjectId().toString(),
                    comment,
                    userId
            );
            messagingTemplate.convertAndSend("/topic/projects/" + task.getProjectId().toString(), message);
        }
    }

    @Transactional
    public CommentEntity toggleLike(String commentId, String userId) {
        CommentEntity comment = commentRepository.findById(UUID.fromString(commentId))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bình luận"));

        UUID userUuid = UUID.fromString(userId);
        boolean isLikedNow = !comment.getLikedUserIds().contains(userUuid);
        if (comment.getLikedUserIds().contains(userUuid)) {
            comment.getLikedUserIds().remove(userUuid);
        } else {
            comment.getLikedUserIds().add(userUuid);
        }

        CommentEntity saved = commentRepository.save(comment);

        TaskEntity task = taskRepository.findById(saved.getTaskId()).orElse(null);
        enrichComment(saved, task);
        if (task != null) {
            // Gửi thông báo cho tác giả nếu được thích và họ không đang xem thảo luận
            if (isLikedNow && saved.getUserId() != null && !saved.getUserId().toString().equals(userId)) {
                if (!isUserViewingDiscussion(task.getId().toString(), saved.getUserId().toString())) {
                    notificationService.createNotificationWithNav(
                            saved.getUserId().toString(),
                            "Bình luận của bạn được thích",
                            "Thành viên khác vừa thích bình luận của bạn trong công việc \"" + task.getTitle() + "\"",
                            "COMMENT",
                            null,
                            task.getProjectId(),
                            task.getId(),
                            saved.getId()
                    );
                }
            }

            TaskMessage message = new TaskMessage(
                    "LIKE_COMMENT",
                    saved.getTaskId().toString(),
                    task.getProjectId().toString(),
                    saved,
                    userId
            );
            messagingTemplate.convertAndSend("/topic/projects/" + task.getProjectId().toString(), message);
        }

        return saved;
    }

    private void enrichComment(CommentEntity comment, TaskEntity task) {
        if (comment == null) return;

        userRepository.findById(comment.getUserId()).ifPresent(user -> {
            comment.setAuthorName(user.getFullname());
            comment.setAuthorAvatarUrl(user.getAvatarUrl());
        });

        if (task == null) {
            task = taskRepository.findById(comment.getTaskId()).orElse(null);
        }

        if (task != null) {
            if (task.getProjectId() != null) {
                projectRepository.findById(task.getProjectId()).ifPresent(proj -> {
                    UUID wsId = proj.getWorkspaceId();
                    if (wsId != null) {
                        workspaceMemberRepository.findById(new WorkspaceMemberId(wsId, comment.getUserId())).ifPresent(member -> {
                            if (member.getRoleId() != null) {
                                workspaceRoleRepository.findById(member.getRoleId()).ifPresent(r -> {
                                    comment.setWorkspaceRole(r.getName());
                                });
                            } else {
                                comment.setWorkspaceRole(member.getRole());
                            }
                        });
                    }
                });

                projectMemberRepository.findById(new ProjectMemberId(task.getProjectId(), comment.getUserId())).ifPresent(member -> {
                    comment.setProjectRole(member.getRole());
                });
            }

            comment.setIsTaskAssignee(task.getAssigneeId() != null && task.getAssigneeId().equals(comment.getUserId()));
        }
    }

    public List<com.example.smartmanager.users.UserEntity> getCommentLikes(String commentId) {
        CommentEntity comment = commentRepository.findById(UUID.fromString(commentId))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bình luận"));
        if (comment.getLikedUserIds().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return userRepository.findAllById(comment.getLikedUserIds());
    }
}
