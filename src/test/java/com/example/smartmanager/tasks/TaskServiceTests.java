package com.example.smartmanager.tasks;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class TaskServiceTests {

    @Autowired
    private TaskService taskService;

    @MockBean
    private TaskRepository taskRepository;

    @MockBean
    private TaskLogRepository taskLogRepository;

    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void testMoveTaskCalculations() {
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        TaskEntity mockTask = new TaskEntity(
                taskId,
                "Test Title",
                "Desc",
                "TODO",
                "MEDIUM",
                1.0,
                null,
                null,
                null,
                projectId,
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(mockTask));
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Case 1: di chuyển vào cột rỗng (prev = null, next = null)
        TaskEntity resultEmpty = taskService.moveTask(taskId.toString(), "IN_PROGRESS", null, null, null);
        assertEquals("IN_PROGRESS", resultEmpty.getStatus());
        assertEquals(1.0, resultEmpty.getPosition());

        // Case 2: di chuyển lên đầu cột (prev = null, next = 2.0)
        TaskEntity resultTop = taskService.moveTask(taskId.toString(), "IN_PROGRESS", null, 2.0, null);
        assertEquals(1.0, resultTop.getPosition());

        // Case 3: di chuyển xuống cuối cột (prev = 3.0, next = null)
        TaskEntity resultBottom = taskService.moveTask(taskId.toString(), "IN_PROGRESS", 3.0, null, null);
        assertEquals(4.0, resultBottom.getPosition());

        // Case 4: di chuyển xen giữa hai task (prev = 1.5, next = 2.5)
        TaskEntity resultMiddle = taskService.moveTask(taskId.toString(), "IN_PROGRESS", 1.5, 2.5, null);
        assertEquals(2.0, resultMiddle.getPosition());
    }
}
