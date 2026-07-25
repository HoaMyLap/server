package com.example.smartmanager.comments;

import com.example.smartmanager.tasks.TaskEntity;
import com.example.smartmanager.tasks.TaskLogEntity;
import com.example.smartmanager.tasks.TaskLogRepository;
import com.example.smartmanager.tasks.TaskRepository;
import com.example.smartmanager.tasks.TaskMessage;
import com.example.smartmanager.notifications.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final TaskLogRepository taskLogRepository;
    private final TaskRepository taskRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;

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

        // Tạo thông báo cho người được giao (Assignee) nếu không phải là người viết bình luận
        if (task.getAssigneeId() != null && !task.getAssigneeId().toString().equals(userId)) {
            notificationService.createNotification(
                    task.getAssigneeId().toString(),
                    "Bình luận mới trong công việc của bạn",
                    "Thành viên khác vừa bình luận vào công việc \"" + task.getTitle() + "\"",
                    "COMMENT"
            );
        }

        // Tạo thông báo cho người tạo công việc (Creator) nếu họ không phải là người viết bình luận và không phải là người được giao
        if (task.getCreatorId() != null && !task.getCreatorId().toString().equals(userId) && 
            (task.getAssigneeId() == null || !task.getAssigneeId().equals(task.getCreatorId()))) {
            notificationService.createNotification(
                    task.getCreatorId().toString(),
                    "Bình luận mới trong công việc bạn tạo",
                    "Thành viên khác vừa bình luận vào công việc \"" + task.getTitle() + "\"",
                    "COMMENT"
            );
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
}
