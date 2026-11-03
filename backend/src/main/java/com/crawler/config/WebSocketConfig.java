package com.crawler.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // In-memory message broker prefix for client subscriptions
        registry.enableSimpleBroker("/topic");
        // Prefix for incoming messages sent from clients (if needed)
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Client connection endpoint with SockJS fallback and CORS allowance
        registry.addEndpoint("/ws/crawler")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}