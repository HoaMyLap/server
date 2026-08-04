package com.example.smartmanager.tasks;

import com.example.smartmanager.notifications.NotificationService;
import com.example.smartmanager.ai.OpenRouterServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskLogRepository taskLogRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;
    private final OpenRouterServiceClient openRouterServiceClient;

    @Transactional
    public TaskEntity createTask(TaskEntity task, String creatorId) {
        if (creatorId != null) {
            task.setCreatorId(UUID.fromString(creatorId));
        }
        
        // Tính toán position cuối cùng cho cột
        Optional<TaskEntity> lastTaskOpt = taskRepository.findFirstByProjectIdAndStatusOrderByPositionDesc(
                task.getProjectId(), task.getStatus());
        if (lastTaskOpt.isPresent()) {
            task.setPosition(lastTaskOpt.get().getPosition() + 1.0);
        } else {
            task.setPosition(1.0);
        }

        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        TaskEntity saved = taskRepository.save(task);

        // Ghi nhật ký
        TaskLogEntity log = new TaskLogEntity(
                null,
                saved.getId(),
                creatorId != null ? UUID.fromString(creatorId) : null,
                "CREATE",
                null,
                "Đã tạo công việc: " + saved.getTitle(),
                LocalDateTime.now()
        );
        taskLogRepository.save(log);

        // Tạo thông báo nếu giao cho ai đó
        if (saved.getAssigneeId() != null && (creatorId == null || !creatorId.equals(saved.getAssigneeId().toString()))) {
            notificationService.createNotificationWithNav(
                    saved.getAssigneeId().toString(),
                    "Bạn được giao công việc mới",
                    "Bạn đã được giao công việc: \"" + saved.getTitle() + "\"",
                    "ASSIGNMENT",
                    null,
                    saved.getProjectId(),
                    saved.getId(),
                    null
            );
        }

        // Phát tin nhắn realtime qua WebSocket
        TaskMessage message = new TaskMessage(
                "CREATE",
                saved.getId().toString(),
                saved.getProjectId().toString(),
                saved,
                creatorId
        );
        messagingTemplate.convertAndSend("/topic/projects/" + saved.getProjectId().toString(), message);

        return saved;
    }

    public List<TaskEntity> getProjectTasks(String projectId) {
        return taskRepository.findByProjectId(UUID.fromString(projectId));
    }

    public List<TaskEntity> getTasksByStatus(String projectId, String status) {
        return taskRepository.findByProjectIdAndStatusOrderByPositionAsc(
                UUID.fromString(projectId), status);
    }

    public TaskEntity getTaskById(String taskId) {
        return taskRepository.findById(UUID.fromString(taskId))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy công việc với ID: " + taskId));
    }

    public List<TaskEntity> getSubtasks(String taskId) {
        return taskRepository.findByParentTaskId(UUID.fromString(taskId));
    }

    @Transactional
    public void deleteTask(String taskId, String userId) {
        TaskEntity task = getTaskById(taskId);
        taskRepository.delete(task);

        // Ghi nhật ký
        TaskLogEntity log = new TaskLogEntity(
                null,
                UUID.fromString(taskId),
                userId != null ? UUID.fromString(userId) : null,
                "DELETE",
                task.getTitle(),
                null,
                LocalDateTime.now()
        );
        taskLogRepository.save(log);

        // Phát tin nhắn realtime qua WebSocket
        TaskMessage message = new TaskMessage(
                "DELETE",
                task.getId().toString(),
                task.getProjectId().toString(),
                null,
                userId
            );
        messagingTemplate.convertAndSend("/topic/projects/" + task.getProjectId().toString(), message);
    }

    @Transactional
    public TaskEntity moveTask(String taskId, String newStatus, Double prevPosition, Double nextPosition, String userId) {
        TaskEntity task = getTaskById(taskId);
        String oldStatus = task.getStatus();
        
        // Tính toán vị trí mới
        double newPosition;
        if (prevPosition == null && nextPosition == null) {
            newPosition = 1.0;
        } else if (prevPosition == null) {
            newPosition = nextPosition / 2.0;
        } else if (nextPosition == null) {
            newPosition = prevPosition + 1.0;
        } else {
            newPosition = (prevPosition + nextPosition) / 2.0;
        }

        task.setStatus(newStatus);
        if ("DONE".equals(oldStatus) && !"DONE".equals(newStatus)) {
            task.setReminded(false);
        }
        task.setPosition(newPosition);
        task.setUpdatedAt(LocalDateTime.now());
        TaskEntity saved = taskRepository.save(task);

        // Ghi log hoạt động
        if (!oldStatus.equals(newStatus)) {
            TaskLogEntity log = new TaskLogEntity(
                    null,
                    saved.getId(),
                    userId != null ? UUID.fromString(userId) : null,
                    "UPDATE_STATUS",
                    oldStatus,
                    newStatus,
                    LocalDateTime.now()
            );
            taskLogRepository.save(log);

            // Gửi thông báo cho người thực hiện nếu họ không phải là người di chuyển
            if (saved.getAssigneeId() != null && (userId == null || !userId.equals(saved.getAssigneeId().toString()))) {
                notificationService.createNotificationWithNav(
                        saved.getAssigneeId().toString(),
                        "Công việc của bạn đã thay đổi trạng thái",
                        "Công việc \"" + saved.getTitle() + "\" được chuyển sang cột: " + newStatus,
                        "ASSIGNMENT",
                        null,
                        saved.getProjectId(),
                        saved.getId(),
                        null
                );
            }
        }

        // Phát tin nhắn realtime qua WebSocket
        TaskMessage message = new TaskMessage(
                "MOVE",
                saved.getId().toString(),
                saved.getProjectId().toString(),
                saved,
                userId
        );
        messagingTemplate.convertAndSend("/topic/projects/" + saved.getProjectId().toString(), message);

        return saved;
    }
    
    @Transactional
    public TaskEntity updateTask(TaskEntity taskDetails, String userId) {
        TaskEntity task = getTaskById(taskDetails.getId().toString());
        UUID oldAssignee = task.getAssigneeId();
        
        if (taskDetails.getDueDate() != null && !taskDetails.getDueDate().equals(task.getDueDate())) {
            task.setReminded(false);
        } else if (taskDetails.getDueDate() == null && task.getDueDate() != null) {
            task.setReminded(false);
        }
        
        task.setTitle(taskDetails.getTitle());
        task.setDescription(taskDetails.getDescription());
        task.setPriority(taskDetails.getPriority());
        task.setDueDate(taskDetails.getDueDate());
        task.setAssigneeId(taskDetails.getAssigneeId());
        task.setUpdatedAt(LocalDateTime.now());
        
        TaskEntity saved = taskRepository.save(task);
        
        // Ghi log hoạt động cập nhật
        TaskLogEntity log = new TaskLogEntity(
                null,
                saved.getId(),
                userId != null ? UUID.fromString(userId) : null,
                "UPDATE",
                null,
                "Đã cập nhật chi tiết công việc",
                LocalDateTime.now()
        );
        taskLogRepository.save(log);

        // Thông báo cho người được phân công mới
        if (saved.getAssigneeId() != null && !saved.getAssigneeId().equals(oldAssignee) && 
            (userId == null || !userId.equals(saved.getAssigneeId().toString()))) {
            notificationService.createNotificationWithNav(
                    saved.getAssigneeId().toString(),
                    "Bạn được phân công công việc mới",
                    "Bạn được phân công quản trị công việc: \"" + saved.getTitle() + "\"",
                    "ASSIGNMENT",
                    null,
                    saved.getProjectId(),
                    saved.getId(),
                    null
            );
        }

        // Phát tin nhắn realtime qua WebSocket
        TaskMessage message = new TaskMessage(
                "UPDATE",
                saved.getId().toString(),
                saved.getProjectId().toString(),
                saved,
                userId
        );
        messagingTemplate.convertAndSend("/topic/projects/" + saved.getProjectId().toString(), message);
        
        return saved;
    }

    public List<TaskLogEntity> getTaskLogs(String taskId) {
        return taskLogRepository.findByTaskIdOrderByCreatedAtDesc(UUID.fromString(taskId));
    }

    @Transactional
    public TaskEntity toggleTaskDone(String taskId, String userId) {
        TaskEntity task = getTaskById(taskId);
        String oldStatus = task.getStatus();
        String newStatus = "DONE".equals(oldStatus) ? "TODO" : "DONE";

        task.setStatus(newStatus);
        if ("DONE".equals(oldStatus) && !"DONE".equals(newStatus)) {
            task.setReminded(false);
        }
        task.setUpdatedAt(LocalDateTime.now());
        TaskEntity saved = taskRepository.save(task);

        // Ghi log hoạt động
        TaskLogEntity log = new TaskLogEntity(
                null,
                saved.getId(),
                userId != null ? UUID.fromString(userId) : null,
                "UPDATE_STATUS",
                oldStatus,
                newStatus,
                LocalDateTime.now()
        );
        taskLogRepository.save(log);

        // Phát tin nhắn realtime qua WebSocket
        TaskMessage message = new TaskMessage(
                "UPDATE",
                saved.getId().toString(),
                saved.getProjectId().toString(),
                saved,
                userId
        );
        messagingTemplate.convertAndSend("/topic/projects/" + saved.getProjectId().toString(), message);

        return saved;
    }

    public List<String> suggestAiSubtasks(String taskId) {
        TaskEntity task = getTaskById(taskId);

        String prompt = String.format(
                "Bạn là một kiến trúc sư phần mềm chuyên nghiệp. Nhiệm vụ của bạn là đọc tiêu đề và mô tả công việc sau, " +
                "rồi phân tách nó thành các bước nhỏ hơn (sub-tasks) để lập trình viên thực hiện.\n" +
                "Tiêu đề: %s\n" +
                "Mô tả: %s\n\n" +
                "Hãy trả về kết quả dưới dạng mảng JSON chứa các chuỗi, ví dụ: [\"Chuẩn bị tài liệu\", \"Code logic endpoint\"]. " +
                "CHỈ TRẢ VỀ JSON array thô, không giải thích gì thêm, không bọc ngoài bằng bất kỳ thẻ text nào khác.",
                task.getTitle(),
                task.getDescription() != null ? task.getDescription() : "Không có mô tả."
        );

        String rawResponse = openRouterServiceClient.generateContent(prompt);
        return openRouterServiceClient.parseSubtasks(rawResponse);
    }

    @Transactional
    public List<TaskEntity> addBatchSubtasks(String taskId, List<String> subtaskTitles, String userId) {
        if (subtaskTitles == null || subtaskTitles.isEmpty()) {
            return List.of();
        }

        TaskEntity task = getTaskById(taskId);
        List<TaskEntity> createdSubtasks = new ArrayList<>();
        double currentPosition = 1.0;

        for (String title : subtaskTitles) {
            if (title == null || title.trim().isEmpty()) continue;

            TaskEntity subtask = new TaskEntity();
            subtask.setTitle(title.trim());
            subtask.setStatus("TODO");
            subtask.setPriority("MEDIUM");
            subtask.setPosition(currentPosition++);
            subtask.setProjectId(task.getProjectId());
            subtask.setParentTaskId(task.getId());
            subtask.setCreatorId(userId != null ? UUID.fromString(userId) : null);
            subtask.setCreatedAt(LocalDateTime.now());
            subtask.setUpdatedAt(LocalDateTime.now());

            TaskEntity savedSub = taskRepository.save(subtask);
            createdSubtasks.add(savedSub);

            // Ghi nhật ký từng subtask
            TaskLogEntity log = new TaskLogEntity(
                    null,
                    savedSub.getId(),
                    userId != null ? UUID.fromString(userId) : null,
                    "CREATE",
                    null,
                    "Tạo subtask cho task \"" + task.getTitle() + "\": " + title.trim(),
                    LocalDateTime.now()
            );
            taskLogRepository.save(log);

            // Phát tin nhắn realtime qua WebSocket
            TaskMessage message = new TaskMessage(
                    "CREATE",
                    savedSub.getId().toString(),
                    savedSub.getProjectId().toString(),
                    savedSub,
                    userId
            );
            messagingTemplate.convertAndSend("/topic/projects/" + savedSub.getProjectId().toString(), message);
        }

        return createdSubtasks;
    }

    @Transactional
    public List<TaskEntity> generateAiSubtasks(String taskId, String userId) {
        List<String> titles = suggestAiSubtasks(taskId);
        return addBatchSubtasks(taskId, titles, userId);
    }

    @Transactional
    public List<TaskEntity> createBatchTasks(BatchCreateTasksRequest request, String userId) {
        if (request.getTasks() == null || request.getTasks().isEmpty()) {
            throw new IllegalArgumentException("Danh sách công việc không được để trống");
        }
        UUID projectUuid = UUID.fromString(request.getProjectId());
        List<TaskEntity> createdList = new java.util.ArrayList<>();

        for (TaskEntity task : request.getTasks()) {
            if (task.getTitle() != null && !task.getTitle().isBlank()) {
                task.setProjectId(projectUuid);
                if (task.getStatus() == null || task.getStatus().isBlank()) {
                    task.setStatus("TODO");
                }
                if (task.getPriority() == null || task.getPriority().isBlank()) {
                    task.setPriority("MEDIUM");
                }
                TaskEntity created = createTask(task, userId);
                createdList.add(created);
            }
        }
        return createdList;
    }

    private final TaskFileRepository taskFileRepository;

    public List<TaskFileEntity> getTaskFiles(String taskId) {
        return taskFileRepository.findByTaskIdOrderByUploadedAtDesc(UUID.fromString(taskId));
    }

    @Transactional
    public TaskFileEntity saveTaskFile(String taskId, TaskFileEntity file, String uploaderId) {
        file.setTaskId(UUID.fromString(taskId));
        if (uploaderId != null) {
            file.setUploaderId(UUID.fromString(uploaderId));
        }
        file.setUploadedAt(LocalDateTime.now());
        return taskFileRepository.save(file);
    }

    @Transactional
    public void deleteTaskFile(String fileId) {
        taskFileRepository.deleteById(UUID.fromString(fileId));
    }
}
