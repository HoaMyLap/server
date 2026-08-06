package com.example.smartmanager.users;

import com.example.smartmanager.auth.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @GetMapping("/me")
    public ResponseEntity<?> getMyProfile(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        return userRepository.findById(UUID.fromString(userPrincipal.getId()))
                .map(user -> ResponseEntity.ok(Map.of(
                        "id", user.getId().toString(),
                        "email", user.getEmail(),
                        "fullname", user.getFullname(),
                        "avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "",
                        "subscriptionPlan", user.getSubscriptionPlan() != null ? user.getSubscriptionPlan() : "FREE",
                        "subscriptionExpiresAt", user.getSubscriptionExpiresAt() != null ? user.getSubscriptionExpiresAt().toString() : ""
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateProfile(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        String fullname = body.get("fullname");
        String avatarUrl = body.get("avatarUrl");

        if (fullname == null || fullname.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Họ tên không được để trống"));
        }

        return userRepository.findById(UUID.fromString(userPrincipal.getId()))
                .map(user -> {
                    user.setFullname(fullname);
                    if (avatarUrl != null) {
                        user.setAvatarUrl(avatarUrl);
                    }
                    UserEntity saved = userRepository.save(user);
                    return ResponseEntity.ok(Map.of(
                            "id", saved.getId().toString(),
                            "email", saved.getEmail(),
                            "fullname", saved.getFullname(),
                            "avatarUrl", saved.getAvatarUrl() != null ? saved.getAvatarUrl() : "",
                            "subscriptionPlan", saved.getSubscriptionPlan() != null ? saved.getSubscriptionPlan() : "FREE",
                            "subscriptionExpiresAt", saved.getSubscriptionExpiresAt() != null ? saved.getSubscriptionExpiresAt().toString() : ""
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
