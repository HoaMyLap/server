package com.example.smartmanager.contact;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/contact")
@RequiredArgsConstructor
@Slf4j
public class ContactController {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:homixspace@gmail.com}")
    private String fromEmail;

    @PostMapping
    public ResponseEntity<?> submitContact(@RequestBody Map<String, String> body) {
        String fullname = body.get("fullname");
        String email = body.get("email");
        String phone = body.get("phone");
        String subject = body.get("subject");
        String message = body.get("message");

        log.info("Nhận tin nhắn liên hệ mới từ {}: {} ({}) - Chủ đề: {}", fullname, email, phone, subject);

        if (mailSender != null && email != null && !email.isBlank()) {
            try {
                // Send notification to Support Desk (homixspace@gmail.com)
                MimeMessage adminMsg = mailSender.createMimeMessage();
                MimeMessageHelper adminHelper = new MimeMessageHelper(adminMsg, true, "UTF-8");
                adminHelper.setFrom(fromEmail, "Homix Support Form");
                adminHelper.setTo(fromEmail);
                adminHelper.setSubject("[LIÊN HỆ MỚI] " + subject + " - " + fullname);
                adminHelper.setText(String.format("""
                    <h3>Tin nhắn liên hệ mới từ Website Homix v2.0</h3>
                    <p><strong>Họ tên:</strong> %s</p>
                    <p><strong>Email khách hàng:</strong> %s</p>
                    <p><strong>Số điện thoại:</strong> %s</p>
                    <p><strong>Chủ đề:</strong> %s</p>
                    <p><strong>Nội dung:</strong></p>
                    <blockquote style="background:#f4f4f5; padding:12px; border-left:4px solid #6366f1;">%s</blockquote>
                    """, fullname, email, phone != null ? phone : "N/A", subject, message), true);
                mailSender.send(adminMsg);

                // Send confirmation email to Customer
                MimeMessage custMsg = mailSender.createMimeMessage();
                MimeMessageHelper custHelper = new MimeMessageHelper(custMsg, true, "UTF-8");
                custHelper.setFrom(fromEmail, "Homix v2.0 Ecosystem");
                custHelper.setTo(email);
                custHelper.setSubject("Cảm ơn bạn đã liên hệ Homix v2.0 Ecosystem");
                custHelper.setText(String.format("""
                    <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 12px;">
                        <h2 style="color: #6366f1; text-align: center;">Homix v2.0 Support Team</h2>
                        <p>Xin chào <strong>%s</strong>,</p>
                        <p>Chúng tôi đã nhận được thông điệp của bạn về chủ đề: <strong>"%s"</strong>.</p>
                        <p>Đội ngũ kỹ thuật & CSKH Homix v2.0 sẽ phản hồi tới bạn qua email này trong thời gian sớm nhất (tối đa 24 giờ).</p>
                        <hr style="border: none; border-top: 1px solid #eee; margin: 20px 0;" />
                        <p style="font-size: 11px; color: #888; text-align: center;">Hotline hỗ trợ 24/7: 1900 6868 | Email: homixspace@gmail.com</p>
                    </div>
                    """, fullname, subject), true);
                mailSender.send(custMsg);

                log.info("✅ Gửi email thông báo liên hệ THÀNH CÔNG cho khách hàng {}", email);
            } catch (Exception e) {
                log.error("❌ Lỗi gửi email thông báo liên hệ qua Gmail: ", e);
                return ResponseEntity.badRequest().body(Map.of("error", "Lỗi gửi email: " + e.getMessage()));
            }
        } else if (mailSender == null) {
            log.warn("⚠️ JavaMailSender is NULL! Mail service is not auto-configured.");
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Cảm ơn bạn đã gửi liên hệ! Đội ngũ Homix v2.0 đã nhận thông tin và sẽ phản hồi trong vòng 24h qua email " + (email != null ? email : "")
        ));
    }
}
