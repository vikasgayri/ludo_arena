package com.ludoarena.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String frontendUrl, boolean trustProxy, Jwt jwt, BootstrapAdmin bootstrapAdmin, Mail mail,
                            RateLimit rateLimit, Payments payments) {

    public record Jwt(String secret, int accessTokenMinutes, int refreshTokenDays) {
    }

    public record BootstrapAdmin(String email, String password) {
    }

    public record Mail(boolean enabled, String from, boolean logLinks) {
    }

    public record RateLimit(boolean enabled, int loginPerMinute, int registerPerMinute, int forgotPasswordPerMinute,
                            int resetPasswordPerMinute, int refreshPerMinute) {
    }

    public record Payments(boolean enabled, String provider, String keyId, String keySecret, String webhookSecret,
                           String currency, String apiBaseUrl) {
    }

    /**
     * Browser origins allowed for REST (CORS) and the WebSocket handshake, from FRONTEND_URL (comma separated).
     * Trailing slashes are removed because a browser Origin never has one ("https://x.vercel.app/" would never match).
     * Entries may be patterns such as "https://*.vercel.app"; the first entry should be the real site, it is also used
     * as the base URL in password-reset emails.
     */
    public java.util.List<String> allowedOrigins() {
        return java.util.Arrays.stream(frontendUrl.split(","))
                .map(String::trim)
                .map(s -> s.replaceAll("/+$", ""))
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
