package com.example.smartmanager.tasks.schedulers;

import com.example.smartmanager.tasks.TaskEntity;
import com.example.smartmanager.tasks.TaskRepository;
import com.example.smartmanager.users.UserEntity;
import com.example.smartmanager.users.UserRepository;
import com.example.smartmanager.projects.ProjectRepository;
import com.example.smartmanager.projects.ProjectEntity;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
public class TaskDeadlineReminderScheduler {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:homixspace@gmail.com}")
    private String fromEmail;

    // Run every 1 minute for demo and verification
    @Scheduled(cron = "0 */1 * * * *")
    @Transactional
    public void sendTaskDeadlineReminders() {
        log.info("⏰ [Scheduler] Bắt đầu chạy quét công việc sắp đến hạn...");
        
        // Find tasks due within the next 24 hours that haven't been completed and haven't been reminded
        LocalDateTime tomorrow = LocalDateTime.now().plusDays(1);
        List<TaskEntity> nearDeadlineTasks = taskRepository.findByStatusNotAndDueDateIsNotNullAndDueDateBeforeAndRemindedFalse(
                "DONE", tomorrow);

        if (nearDeadlineTasks.isEmpty()) {
            log.info("⏰ [Scheduler] Không tìm thấy công việc nào sắp đến hạn cần nhắc nhở.");
            return;
        }

        log.info("⏰ [Scheduler] Phát hiện {} công việc sắp đến hạn cần gửi nhắc nhở.", nearDeadlineTasks.size());

        for (TaskEntity task : nearDeadlineTasks) {
            if (task.getAssigneeId() == null) {
                continue;
            }

            Optional<UserEntity> assigneeOpt = userRepository.findById(task.getAssigneeId());
            if (assigneeOpt.isEmpty()) {
                continue;
            }

            UserEntity assignee = assigneeOpt.get();
            String recipientEmail = assignee.getEmail();
            if (recipientEmail == null || recipientEmail.trim().isEmpty()) {
                continue;
            }

            String projectName = "Dự án";
            Optional<ProjectEntity> projectOpt = projectRepository.findById(task.getProjectId());
            if (projectOpt.isPresent()) {
                projectName = projectOpt.get().getName();
            }

            try {
                sendEmailReminder(recipientEmail, assignee.getFullname(), task.getTitle(), task.getDueDate(), projectName);
                
                // Mark as reminded so we don't send again
                task.setReminded(true);
                taskRepository.save(task);
            } catch (Exception e) {
                log.error("Lỗi khi gửi email nhắc nhở cho task ID {}: {}", task.getId(), e.getMessage());
            }
        }
    }

    private void sendEmailReminder(String recipientEmail, String recipientName, String taskTitle, LocalDateTime dueDate, String projectName) {
        log.info("📧 [Deadline Reminder Email] Đang gửi mail tới: {} | Task: \"{}\" | DueDate: {}", 
                recipientEmail, taskTitle, dueDate);

        if (mailSender != null) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(fromEmail, "Homix v2.0 Ecosystem");
                helper.setTo(recipientEmail);
                helper.setSubject("⏰ Nhắc nhở: Công việc sắp đến hạn - " + taskTitle);

                String htmlContent = String.format("""
                    <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 12px;">
                        <h2 style="color: #ef4444; text-align: center;">⏰ Nhắc Nhở Công Việc Sắp Đến Hạn</h2>
                        <p>Xin chào <strong>%s</strong>,</p>
                        <p>Chúng tôi xin nhắc nhở bạn có một công việc được phân công sắp đến hạn hoàn thành trong dự án <strong>"%s"</strong>:</p>
                        <div style="background-color: #fef2f2; padding: 15px; border-left: 4px solid #ef4444; border-radius: 6px; margin: 20px 0;">
                            <h3 style="margin: 0 0 10px 0; color: #991b1b;">%s</h3>
                            <p style="margin: 0; font-size: 14px; color: #7f1d1d;"><strong>Hạn chót:</strong> %s</p>
                        </div>
                        <p>Vui lòng cập nhật tiến độ công việc trên hệ thống Homix v2.0.</p>
                        <div style="text-align: center; margin: 30px 0;">
                            <a href="http://localhost:3000" style="background-color: #6366f1; color: white; padding: 12px 24px; text-decoration: none; border-radius: 8px; font-weight: bold;">Truy Cập Hệ Thống</a>
                        </div>
                        <hr style="border: none; border-top: 1px solid #eee;" />
                        <p style="font-size: 11px; color: #888; text-align: center;">Email này được gửi tự động từ hệ thống Homix v2.0 Ecosystem (%s).</p>
                    </div>
                    """, recipientName, projectName, taskTitle, dueDate.toString(), fromEmail);

                helper.setText(htmlContent, true);
                mailSender.send(message);
                log.info("✅ [Deadline Reminder Email] Gửi THÀNH CÔNG email nhắc nhở tới {} qua {}", recipientEmail, fromEmail);
            } catch (Exception e) {
                log.warn("⚠️ Gửi email nhắc nhở không thành công (kiểm tra GMAIL_APP_PASSWORD): {}", e.getMessage());
            }
        } else {
            log.info("✅ [Deadline Reminder Email Simulation] Gửi thành công email nhắc nhở công việc \"{}\" (Hạn chót: {}) tới {}",
                    taskTitle, dueDate, recipientEmail);
        }
    }
}
