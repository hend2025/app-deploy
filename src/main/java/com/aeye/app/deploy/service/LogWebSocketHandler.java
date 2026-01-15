package com.aeye.app.deploy.service;

import com.aeye.app.deploy.model.AppLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 日志WebSocket处理器
 * 管理客户端连接，按appCode订阅推送日志
 */
@Component
public class LogWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(LogWebSocketHandler.class);
    
    /** JSON序列化器（线程安全，复用实例） */
    private final ObjectMapper objectMapper;
    
    /** 最大WebSocket连接数 */
    @Value("${app.websocket.max-connections:100}")
    private int maxConnections;

    /** 按appCode分组的会话集合 */
    private final ConcurrentHashMap<String, Set<WebSocketSession>> appSessions = new ConcurrentHashMap<>();
    
    /** 异步消息发送队列 */
    private final BlockingQueue<LogMessage> messageQueue = new LinkedBlockingQueue<>(10000);
    
    /** 消息发送线程池 */
    private ExecutorService senderExecutor;
    
    /** 当前连接总数 */
    private final java.util.concurrent.atomic.AtomicInteger connectionCount = new java.util.concurrent.atomic.AtomicInteger(0);
    
    /** 内部消息封装类 */
    /** 内部消息封装类 */
    private static class LogMessage {
        final String appCode;
        final AppLog log;
        
        LogMessage(String appCode, AppLog log) {
            this.appCode = appCode;
            this.log = log;
        }
    }
    
    public LogWebSocketHandler() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        this.objectMapper.setDateFormat(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS"));
    }
    
    @PostConstruct
    public void init() {
        // 启动消息发送线程
        senderExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "ws-sender");
            t.setDaemon(true);
            return t;
        });
        
        for (int i = 0; i < 2; i++) {
            senderExecutor.submit(this::processSendQueue);
        }
        logger.info("WebSocket消息发送线程已启动，最大连接数: {}", maxConnections);
    }
    
    @PreDestroy
    public void shutdown() {
        if (senderExecutor != null) {
            senderExecutor.shutdown();
            try {
                if (!senderExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    senderExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                senderExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * 处理发送队列
     */
    /**
     * 处理发送队列（批量发送）
     */
    private void processSendQueue() {
        List<LogMessage> batchBuffer = new ArrayList<>(500);
        
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // 阻塞等待第一条消息
                LogMessage first = messageQueue.poll(1, TimeUnit.SECONDS);
                if (first == null) {
                    continue;
                }
                
                batchBuffer.add(first);
                // 立即获取队列中剩余的消息（最多500条），不阻塞
                messageQueue.drainTo(batchBuffer, 499);
                
                // 按AppCode分组
                Map<String, List<AppLog>> logGroups = batchBuffer.stream()
                    .collect(Collectors.groupingBy(
                        msg -> msg.appCode,
                        Collectors.mapping(msg -> msg.log, Collectors.toList())
                    ));
                
                // 发送各组消息
                for (Map.Entry<String, List<AppLog>> entry : logGroups.entrySet()) {
                    try {
                        // 序列化为JSON数组发送
                        String message = objectMapper.writeValueAsString(entry.getValue());
                        doSendMessage(entry.getKey(), message);
                    } catch (Exception e) {
                        logger.error("序列化日志批次失败", e);
                    }
                }
                
                batchBuffer.clear();
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("处理WebSocket消息队列异常", e);
                // 避免异常循环过快
                try { Thread.sleep(100); } catch (InterruptedException ex) {}
            }
        }
    }
    
    /**
     * 实际发送消息
     */
    private void doSendMessage(String appCode, String message) {
        Set<WebSocketSession> sessions = appSessions.get(appCode);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        
        TextMessage textMessage = new TextMessage(message);
        List<WebSocketSession> deadSessions = new ArrayList<>();
        
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(textMessage);
                    }
                } catch (IOException e) {
                    logger.warn("推送日志失败，移除会话: sessionId={}", session.getId());
                    deadSessions.add(session);
                }
            } else {
                deadSessions.add(session);
            }
        }
        
        // 清理已关闭的会话
        for (WebSocketSession deadSession : deadSessions) {
            sessions.remove(deadSession);
            connectionCount.decrementAndGet();
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 检查连接数限制
        if (connectionCount.get() >= maxConnections) {
            logger.warn("WebSocket连接数已达上限，拒绝新连接: {}", session.getId());
            try {
                session.close(CloseStatus.SERVICE_OVERLOAD);
            } catch (IOException e) {
                // ignore
            }
            return;
        }
        
        String appCode = getAppCodeFromSession(session);
        if (appCode != null) {
            appSessions.computeIfAbsent(appCode, k -> ConcurrentHashMap.newKeySet()).add(session);
            connectionCount.incrementAndGet();
            logger.info("WebSocket连接建立: appCode={}, sessionId={}, 当前连接数: {}", 
                appCode, session.getId(), connectionCount.get());
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
            connectionCount.decrementAndGet();
            logger.info("WebSocket连接关闭: appCode={}, sessionId={}, 当前连接数: {}", 
                appCode, session.getId(), connectionCount.get());
        }
    }

    /**
     * 推送日志给订阅该appCode的所有客户端（异步）
     */
    public void pushLog(AppLog log) {
        String appCode = log.getAppCode();
        Set<WebSocketSession> sessions = appSessions.get(appCode);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        try {
            // 放入队列异步发送（不再预先序列化，改为批量处理时序列化）
            if (!messageQueue.offer(new LogMessage(appCode, log))) {
                logger.warn("WebSocket消息队列已满，丢弃消息: appCode={}", appCode);
            }
        } catch (Exception e) {
            logger.error("入队日志失败", e);
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
