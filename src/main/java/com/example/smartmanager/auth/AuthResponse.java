package com.example.smartmanager.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String type = "Bearer";
    private String email;
    private String fullname;
    private String userId;

    public AuthResponse(String token, String email, String fullname, String userId) {
        this.token = token;
        this.email = email;
        this.fullname = fullname;
        this.userId = userId;
    }
}
