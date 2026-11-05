package com.ludoarena.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WebRTC voice chat. Audio flows peer-to-peer; the server only relays signalling and hands out ICE servers.
 * STUN is enough on most Wi-Fi networks; mobile data / strict NATs need a TURN relay:
 *  - coturn with "use-auth-secret": set turnUrls + turnSecret (short-lived HMAC credentials per user), or
 *  - a managed TURN service with fixed credentials: set turnUrls + turnUsername + turnCredential.
 */
@ConfigurationProperties(prefix = "app.voice")
public record VoiceProperties(boolean enabled, String stunUrls, String turnUrls, String turnSecret,
                              String turnUsername, String turnCredential, long turnTtlSeconds) {

    public VoiceProperties {
        if (turnTtlSeconds <= 0) turnTtlSeconds = 6 * 3600;
    }

    public static List<String> split(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
