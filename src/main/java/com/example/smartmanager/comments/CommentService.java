package com.example.smartmanager.comments;

import com.example.smartmanager.tasks.TaskEntity;
import com.example.smartmanager.tasks.TaskLogEntity;
import com.example.smartmanager.tasks.TaskLogRepository;
import com.example.smartmanager.tasks.TaskRepository;
import com.example.smartmanager.tasks.TaskMessage;
import com.example.smartmanager.notifications.NotificationService;
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

    public CommentService(
            CommentRepository commentRepository,
            TaskLogRepository taskLogRepository,
            TaskRepository taskRepository,
            SimpMessagingTemplate messagingTemplate,
            NotificationService notificationService,
            @Qualifier("redisTemplate") RedisTemplate<String, Object> redisTemplate) {
        this.commentRepository = commentRepository;
        this.taskLogRepository = taskLogRepository;
        this.taskRepository = taskRepository;
        this.messagingTemplate = messagingTemplate;
        this.notificationService = notificationService;
        this.redisTemplate = redisTemplate;
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

                        notificationService.createNotification(
                                rId.toString(),
                                title,
                                content,
                                "COMMENT"
                        );
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to send comment notifications: " + e.getMessage());
        }

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
        return commentRepository.findByTaskIdOrderByCreatedAtAsc(UUID.fromString(taskId));
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
        if (task != null) {
            // Gửi thông báo cho tác giả nếu được thích và họ không đang xem thảo luận
            if (isLikedNow && saved.getUserId() != null && !saved.getUserId().toString().equals(userId)) {
                if (!isUserViewingDiscussion(task.getId().toString(), saved.getUserId().toString())) {
                    notificationService.createNotification(
                            saved.getUserId().toString(),
                            "Bình luận của bạn được thích",
                            "Thành viên khác vừa thích bình luận của bạn trong công việc \"" + task.getTitle() + "\"",
                            "COMMENT"
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
}
