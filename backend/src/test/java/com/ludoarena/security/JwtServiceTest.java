package com.ludoarena.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ludoarena.TestProps;
import com.ludoarena.config.AppProperties;
import com.ludoarena.model.Enums.Role;
import com.ludoarena.model.User;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
    private static User user() {
        User u = new User();
        u.id = "user-1";
        u.role = Role.USER;
        return u;
    }

    @Test
    void validTokenRoundTripsToTheUserId() {
        JwtService jwt = new JwtService(TestProps.props());
        String token = jwt.createAccessToken(user());
        assertEquals("user-1", jwt.validateAndGetUserId(token).orElseThrow());
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService jwt = new JwtService(TestProps.props());
        String token = jwt.createAccessToken(user());
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");
        assertFalse(jwt.validateAndGetUserId(tampered).isPresent());
        assertFalse(jwt.validateAndGetUserId("not-a-jwt").isPresent());
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtService a = new JwtService(TestProps.props());
        AppProperties other = new AppProperties("http://x", false,
                new AppProperties.Jwt("a-completely-different-secret-0123456789abcdef", 15, 14),
                null, TestProps.props().mail(), TestProps.props().rateLimit(), TestProps.props().payments());
        JwtService b = new JwtService(other);
        assertFalse(a.validateAndGetUserId(b.createAccessToken(user())).isPresent());
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService jwt = new JwtService(TestProps.props(-1, true, 10));
        assertFalse(jwt.validateAndGetUserId(jwt.createAccessToken(user())).isPresent());
    }

    @Test
    void shortSecretFailsFast() {
        AppProperties weak = new AppProperties("http://x", false, new AppProperties.Jwt("short", 15, 14),
                null, TestProps.props().mail(), TestProps.props().rateLimit(), TestProps.props().payments());
        assertThrows(IllegalStateException.class, () -> new JwtService(weak));
        assertTrue(true);
    }
}
