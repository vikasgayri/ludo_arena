package com.ludoarena.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ludoarena.TestProps;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {
    private int call(RateLimitFilter filter, String method, String path, String ip) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(method, path);
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res.getStatus();
    }

    @Test
    void blocksLoginAfterTheConfiguredNumberOfAttempts() throws Exception {
        RateLimitFilter f = new RateLimitFilter(TestProps.props(15, true, 3), new ObjectMapper());
        for (int i = 0; i < 3; i++) assertEquals(200, call(f, "POST", "/api/v1/auth/login", "10.0.0.1"));
        assertEquals(429, call(f, "POST", "/api/v1/auth/login", "10.0.0.1"));
    }

    @Test
    void limitsArePerClientIp() throws Exception {
        RateLimitFilter f = new RateLimitFilter(TestProps.props(15, true, 1), new ObjectMapper());
        assertEquals(200, call(f, "POST", "/api/v1/auth/login", "10.0.0.1"));
        assertEquals(429, call(f, "POST", "/api/v1/auth/login", "10.0.0.1"));
        assertEquals(200, call(f, "POST", "/api/v1/auth/login", "10.0.0.2"));
    }

    @Test
    void retryAfterHeaderIsSetWhenLimited() throws Exception {
        RateLimitFilter f = new RateLimitFilter(TestProps.props(15, true, 1), new ObjectMapper());
        call(f, "POST", "/api/v1/auth/login", "10.0.0.9");
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        req.setRemoteAddr("10.0.0.9");
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, new MockFilterChain());
        assertEquals(429, res.getStatus());
        assertNotNull(res.getHeader("Retry-After"));
    }

    @Test
    void otherEndpointsAndMethodsAreNotLimited() throws Exception {
        RateLimitFilter f = new RateLimitFilter(TestProps.props(15, true, 1), new ObjectMapper());
        for (int i = 0; i < 20; i++) {
            assertEquals(200, call(f, "GET", "/api/v1/auth/login", "10.0.0.1"));
            assertEquals(200, call(f, "GET", "/api/v1/wallet", "10.0.0.1"));
        }
    }

    @Test
    void canBeDisabled() throws Exception {
        RateLimitFilter f = new RateLimitFilter(TestProps.props(15, false, 1), new ObjectMapper());
        for (int i = 0; i < 10; i++) assertEquals(200, call(f, "POST", "/api/v1/auth/login", "10.0.0.1"));
    }
}
