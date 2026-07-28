package com.example.smartmanager.tasks;

import com.example.smartmanager.auth.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    @PreAuthorize("@securityService.hasProjectRole(#task.projectId.toString(), 'MEMBER')")
    public ResponseEntity<?> createTask(
            @Valid @RequestBody TaskEntity task,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            TaskEntity created = taskService.createTask(task, userPrincipal.getId());
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/project/{projectId}")
    @PreAuthorize("@securityService.hasProjectRole(#projectId, 'VIEWER')")
    public ResponseEntity<List<TaskEntity>> getProjectTasks(@PathVariable("projectId") String projectId) {
        List<TaskEntity> tasks = taskService.getProjectTasks(projectId);
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/project/{projectId}/status/{status}")
    @PreAuthorize("@securityService.hasProjectRole(#projectId, 'VIEWER')")
    public ResponseEntity<List<TaskEntity>> getTasksByStatus(
            @PathVariable("projectId") String projectId,
            @PathVariable("status") String status) {
        List<TaskEntity> tasks = taskService.getTasksByStatus(projectId, status);
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.hasTaskRole(#taskId, 'VIEWER')")
    public ResponseEntity<?> getTaskById(@PathVariable("id") String taskId) {
        try {
            TaskEntity task = taskService.getTaskById(taskId);
            return ResponseEntity.ok(task);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/subtasks")
    @PreAuthorize("@securityService.hasTaskRole(#taskId, 'VIEWER')")
    public ResponseEntity<List<TaskEntity>> getSubtasks(@PathVariable("id") String taskId) {
        List<TaskEntity> subtasks = taskService.getSubtasks(taskId);
        return ResponseEntity.ok(subtasks);
    }

    @PutMapping
    @PreAuthorize("@securityService.hasTaskRole(#task.id.toString(), 'MEMBER')")
    public ResponseEntity<?> updateTask(
            @Valid @RequestBody TaskEntity task,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            TaskEntity updated = taskService.updateTask(task, userPrincipal.getId());
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@securityService.hasTaskRole(#taskId, 'MEMBER')")
    public ResponseEntity<?> deleteTask(
            @PathVariable("id") String taskId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            taskService.deleteTask(taskId, userPrincipal.getId());
            return ResponseEntity.ok(Map.of("message", "Xóa công việc thành công"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/move")
    @PreAuthorize("@securityService.hasTaskRole(#taskId, 'MEMBER')")
    public ResponseEntity<?> moveTask(
            @PathVariable("id") String taskId,
            @Valid @RequestBody MoveTaskRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            TaskEntity moved = taskService.moveTask(
                    taskId,
                    request.getNewStatus(),
                    request.getPrevPosition(),
                    request.getNextPosition(),
                    userPrincipal.getId()
            );
            return ResponseEntity.ok(moved);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/ai-subtasks")
    @PreAuthorize("@securityService.hasTaskRole(#taskId, 'MEMBER')")
    public ResponseEntity<?> generateAiSubtasks(
            @PathVariable("id") String taskId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            List<TaskEntity> subtasks = taskService.generateAiSubtasks(taskId, userPrincipal.getId());
            return ResponseEntity.ok(subtasks);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/suggest-subtasks")
    @PreAuthorize("@securityService.hasTaskRole(#taskId, 'VIEWER')")
    public ResponseEntity<?> suggestAiSubtasks(@PathVariable("id") String taskId) {
        try {
            List<String> suggestions = taskService.suggestAiSubtasks(taskId);
            return ResponseEntity.ok(Map.of("suggestions", suggestions));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/batch-subtasks")
    @PreAuthorize("@securityService.hasTaskRole(#taskId, 'MEMBER')")
    public ResponseEntity<?> addBatchSubtasks(
            @PathVariable("id") String taskId,
            @RequestBody Map<String, List<String>> body,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            List<String> subtasks = body.get("subtasks");
            List<TaskEntity> created = taskService.addBatchSubtasks(taskId, subtasks, userPrincipal.getId());
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/logs")
    @PreAuthorize("@securityService.hasTaskRole(#taskId, 'VIEWER')")
    public ResponseEntity<List<TaskLogEntity>> getTaskLogs(@PathVariable("id") String taskId) {
        List<TaskLogEntity> logs = taskService.getTaskLogs(taskId);
        return ResponseEntity.ok(logs);
    }

    @PatchMapping("/{id}/toggle-done")
    @PreAuthorize("@securityService.hasTaskRole(#taskId, 'MEMBER')")
    public ResponseEntity<?> toggleSubtaskDone(
            @PathVariable("id") String taskId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            TaskEntity updated = taskService.toggleTaskDone(taskId, userPrincipal.getId());
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
