package com.ludoarena;

import com.ludoarena.config.AppProperties;

/** Builds AppProperties for unit tests without starting Spring. */
public final class TestProps {
    private TestProps() {
    }

    public static AppProperties props() {
        return props(15, true, 10);
    }

    public static AppProperties props(int accessMinutes, boolean rateLimitEnabled, int loginPerMinute) {
        return new AppProperties("http://localhost:5173", false,
                new AppProperties.Jwt("unit-test-secret-unit-test-secret-0123456789", accessMinutes, 14),
                new AppProperties.BootstrapAdmin("", ""),
                new AppProperties.Mail(false, "no-reply@test", false),
                new AppProperties.RateLimit(rateLimitEnabled, loginPerMinute, 5, 5, 5, 30),
                new AppProperties.Payments(false, "razorpay", "", "", "", "INR", "https://api.example.test/v1"));
    }
}
