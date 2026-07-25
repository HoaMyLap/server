package com.example.smartmanager.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class JwtTokenProviderTests {

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Test
    void testTokenGenerationAndValidation() {
        String email = "test@example.com";
        String token = tokenProvider.generateToken(email);
        
        assertNotNull(token);
        assertTrue(tokenProvider.validateToken(token));
        assertEquals(email, tokenProvider.getEmailFromJWT(token));
    }
}
