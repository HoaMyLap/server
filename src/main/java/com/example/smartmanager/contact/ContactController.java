package com.example.smartmanager.contact;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/contact")
@RequiredArgsConstructor
@Slf4j
public class ContactController {

    @PostMapping
    public ResponseEntity<?> submitContact(@RequestBody Map<String, String> body) {
        String fullname = body.get("fullname");
        String email = body.get("email");
        String subject = body.get("subject");
        String message = body.get("message");

        log.info("Nhận tin nhắn liên hệ mới từ {}: {} - Chủ đề: {}", fullname, email, subject);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Cảm ơn bạn đã gửi liên hệ! Đội ngũ Homix v2.0 sẽ phản hồi trong vòng 24h qua email " + (email != null ? email : "")
        ));
    }
}
