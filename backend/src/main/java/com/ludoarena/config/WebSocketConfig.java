package com.ludoarena.config;

import com.ludoarena.websocket.StompAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Native Spring WebSocket + STOMP (no Socket.IO, no SockJS).
 * Client -> server:  /app/room.join, /app/room.ready, /app/room.leave, /app/match.rollDice,
 *                    /app/match.moveToken, /app/match.requestState
 * Server -> client:  /topic/room/{id}, /topic/match/{id}, /user/queue/errors, /user/queue/state,
 *                    /user/queue/notifications
 * The simple in-memory broker is used; a multi-instance deployment needs a relay (RabbitMQ/Redis) - see README.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final AppProperties props;
    private final StompAuthInterceptor authInterceptor;

    // @Lazy breaks the cycle: broker messaging template -> this configurer -> interceptor -> services -> messaging template
    public WebSocketConfig(AppProperties props, @Lazy StompAuthInterceptor authInterceptor) {
        this.props = props;
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(props.allowedOrigins().toArray(new String[0]));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        ThreadPoolTaskScheduler heartbeat = new ThreadPoolTaskScheduler();
        heartbeat.setPoolSize(1);
        heartbeat.setThreadNamePrefix("ws-heartbeat-");
        heartbeat.initialize();
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[] {10_000, 10_000})
                .setTaskScheduler(heartbeat);
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
