package com.aeye.app.deploy.service;

import com.aeye.app.deploy.model.AppLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 日志WebSocket处理器
 * 管理客户端连接，按appCode订阅推送日志
 */
@Component
public class LogWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(LogWebSocketHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 按appCode分组的会话集合 */
    private final ConcurrentHashMap<String, Set<WebSocketSession>> appSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String appCode = getAppCodeFromSession(session);
        if (appCode != null) {
            appSessions.computeIfAbsent(appCode, k -> ConcurrentHashMap.newKeySet()).add(session);
            logger.info("WebSocket连接建立: appCode={}, sessionId={}", appCode, session.getId());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String appCode = getAppCodeFromSession(session);
        if (appCode != null) {
            Set<WebSocketSession> sessions = appSessions.get(appCode);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    appSessions.remove(appCode);
                }
            }
            logger.info("WebSocket连接关闭: appCode={}, sessionId={}", appCode, session.getId());
        }
    }

    /**
     * 推送日志给订阅该appCode的所有客户端
     */
    public void pushLog(AppLog log) {
        String appCode = log.getAppCode();
        Set<WebSocketSession> sessions = appSessions.get(appCode);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        try {
            String message = objectMapper.writeValueAsString(log);
            TextMessage textMessage = new TextMessage(message);
            
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        synchronized (session) {
                            session.sendMessage(textMessage);
                        }
                    } catch (IOException e) {
                        logger.error("推送日志失败: sessionId={}", session.getId(), e);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("序列化日志失败", e);
        }
    }

    private String getAppCodeFromSession(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri != null && uri.getQuery() != null) {
            for (String param : uri.getQuery().split("&")) {
                String[] kv = param.split("=");
                if (kv.length == 2 && "appCode".equals(kv[0])) {
                    return kv[1];
                }
            }
        }
        return null;
    }
}
