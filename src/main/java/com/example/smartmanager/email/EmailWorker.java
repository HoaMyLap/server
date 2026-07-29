package com.example.smartmanager.email;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class EmailWorker {

    public static final String QUEUE_NAME = "email-invitations-queue";
    private final RedisTemplate<String, Object> redisTemplate;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:homixspace@gmail.com}")
    private String fromEmail;

    private ExecutorService executorService;
    private volatile boolean running = true;

    public EmailWorker(@Qualifier("redisTemplate") RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void startWorker() {
        executorService = Executors.newSingleThreadExecutor();
        executorService.submit(this::listenToQueue);
        log.info("Email Background Queue Worker đã được khởi chạy với tài khoản từ: {}", fromEmail);
    }

    private void listenToQueue() {
        while (running) {
            try {
                Object rawJob = redisTemplate.opsForList().rightPop(QUEUE_NAME, 5, TimeUnit.SECONDS);
                if (rawJob != null) {
                    EmailJob job = null;
                    if (rawJob instanceof EmailJob ej) {
                        job = ej;
                    } else if (rawJob instanceof java.util.Map map) {
                        job = new EmailJob(
                            (String) map.get("recipientEmail"),
                            (String) map.get("workspaceId"),
                            (String) map.get("workspaceName"),
                            (String) map.get("role"),
                            (String) map.get("inviterName"),
                            (String) map.get("subject"),
                            (String) map.get("content")
                        );
                    }
                    if (job != null && job.getRecipientEmail() != null) {
                        processEmailJob(job);
                    }
                }
            } catch (Exception e) {
                log.error("Lỗi xảy ra trong Email Queue Worker: {}", e.getMessage());
                try { Thread.sleep(2000); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            }
        }
    }

    @Async
    public void processEmailJob(EmailJob job) {
        log.info("📧 [Email Background Job] Đang thực thi gửi mail mời từ {} tới: {} | Workspace: {} | Role: {}",
                fromEmail, job.getRecipientEmail(), job.getWorkspaceName(), job.getRole());
        
        if (mailSender != null) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(fromEmail, "Homix v2.0 Ecosystem");
                helper.setTo(job.getRecipientEmail());
                helper.setSubject("Lời mời tham gia không gian làm việc: " + job.getWorkspaceName());

                String htmlContent = String.format("""
                    <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; rounded: 12px;">
                        <h2 style="color: #6366f1; text-align: center;">Lời Mời Tham Gia Workspace - Homix v2.0</h2>
                        <p>Xin chào <strong>%s</strong>,</p>
                        <p>Bạn đã nhận được lời mời tham gia không gian làm việc <strong>"%s"</strong> với vai trò <strong>%s</strong> trên hệ thống Homix v2.0.</p>
                        <div style="text-align: center; margin: 30px 0;">
                            <a href="http://localhost:3000/login" style="background-color: #6366f1; color: white; padding: 12px 24px; text-decoration: none; border-radius: 8px; font-weight: bold;">Đăng Nhập Trải Nghiệm Ngay</a>
                        </div>
                        <hr style="border: none; border-top: 1px solid #eee;" />
                        <p style="font-size: 11px; color: #888; text-align: center;">Email này được gửi tự động từ hệ thống Homix v2.0 Ecosystem (%s).</p>
                    </div>
                    """, job.getRecipientEmail(), job.getWorkspaceName(), job.getRole(), fromEmail);

                helper.setText(htmlContent, true);
                mailSender.send(message);
                log.info("✅ [Email Background Job] Gửi THÀNH CÔNG email thực tế tới {} qua {}", job.getRecipientEmail(), fromEmail);
            } catch (Exception e) {
                log.warn("⚠️ Gửi email thực tế không thành công (kiểm tra GMAIL_APP_PASSWORD): {}", e.getMessage());
            }
        } else {
            try {
                Thread.sleep(1000);
                log.info("✅ [Email Background Job Simulation] Gửi thành công email mời tham gia không gian làm việc \"{}\" tới {}",
                        job.getWorkspaceName(), job.getRecipientEmail());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @PreDestroy
    public void stopWorker() {
        running = false;
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
