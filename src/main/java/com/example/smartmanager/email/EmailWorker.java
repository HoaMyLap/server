package com.example.smartmanager.email;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailWorker {

    public static final String QUEUE_NAME = "email-invitations-queue";
    private final RedisTemplate<String, Object> redisTemplate;
    
    private ExecutorService executorService;
    private volatile boolean running = true;

    @PostConstruct
    public void startWorker() {
        executorService = Executors.newSingleThreadExecutor();
        executorService.submit(this::listenToQueue);
        log.info("Email Background Queue Worker đã được khởi chạy thành công.");
    }

    private void listenToQueue() {
        while (running) {
            try {
                Object rawJob = redisTemplate.opsForList().rightPop(QUEUE_NAME, 5, TimeUnit.SECONDS);
                if (rawJob instanceof EmailJob job) {
                    processEmailJob(job);
                }
            } catch (Exception e) {
                log.error("Lỗi xảy ra trong Email Queue Worker: {}", e.getMessage());
                try { Thread.sleep(2000); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            }
        }
    }

    @Async
    public void processEmailJob(EmailJob job) {
        log.info("📧 [Email Background Job] Đang thực thi gửi mail mời tới: {} | Workspace: {} | Role: {}",
                job.getRecipientEmail(), job.getWorkspaceName(), job.getRole());
        
        // Giả lập tiến trình gửi mail bất đồng bộ (2 giây)
        try {
            Thread.sleep(1000);
            log.info("✅ [Email Background Job] Gửi thành công email mời tham gia không gian làm việc \"{}\" tới {}",
                    job.getWorkspaceName(), job.getRecipientEmail());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
