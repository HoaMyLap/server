package com.example.smartmanager.tasks.schedulers;

import com.example.smartmanager.projects.ProjectEntity;
import com.example.smartmanager.projects.ProjectRepository;
import com.example.smartmanager.tasks.TaskEntity;
import com.example.smartmanager.tasks.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PositionReindexScheduler {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;

    /**
     * Tự động quét và re-index vị trí thẻ công việc định kỳ mỗi 10 phút
     * nhằm bảo toàn độ chính xác số thực Fractional Positioning (tránh Floating-point Underflow).
     */
    @Scheduled(fixedRate = 600000) // 10 phút một lần
    @Transactional
    public void reindexTaskPositions() {
        log.info("Bắt đầu tiến trình kiểm tra và re-index vị trí thẻ công việc (Fractional Positioning Cleanup)...");
        List<ProjectEntity> projects = projectRepository.findAll();
        String[] statuses = {"TODO", "IN_PROGRESS", "DONE"};
        int reindexedProjects = 0;

        for (ProjectEntity project : projects) {
            boolean projectModified = false;
            for (String status : statuses) {
                List<TaskEntity> tasks = taskRepository.findByProjectIdAndStatusOrderByPositionAsc(project.getId(), status);
                if (tasks.size() < 2) continue;

                // Kiểm tra xem khoảng cách giữa các vị trí kề nhau có bị tiệm cận 0 hay không (< 1e-6)
                boolean needsReindex = false;
                for (int i = 0; i < tasks.size() - 1; i++) {
                    double diff = tasks.get(i + 1).getPosition() - tasks.get(i).getPosition();
                    if (diff < 1e-6) {
                        needsReindex = true;
                        break;
                    }
                }

                if (needsReindex) {
                    log.info("Phát hiện tiệm cận số thực tại Project: {} - Cột: {}. Đang giãn cách lại vị trí...", project.getName(), status);
                    double newPos = 1000.0;
                    for (TaskEntity task : tasks) {
                        task.setPosition(newPos);
                        taskRepository.save(task);
                        newPos += 1000.0;
                    }
                    projectModified = true;
                }
            }
            if (projectModified) {
                reindexedProjects++;
            }
        }
        log.info("Hoàn tất tiến trình re-index vị trí. Đã chuẩn hóa lại {} dự án.", reindexedProjects);
    }
}
