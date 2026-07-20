package com.bigdreams.ussd.service;

import com.bigdreams.ussd.config.UssdProperties;
import com.bigdreams.ussd.model.SessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds active USSD session state in memory, keyed by Arkesel's sessionID.
 *
 * NOTE: this is a single-instance in-memory store. If you deploy more than one
 * instance behind a load balancer, replace this with a shared store (Redis, etc.)
 * so all steps of a session hit the same state, or use sticky sessions upstream.
 */
@Service
public class UssdSessionService {

    private static final Logger log = LoggerFactory.getLogger(UssdSessionService.class);

    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();
    private final UssdProperties properties;

    public UssdSessionService(UssdProperties properties) {
        this.properties = properties;
    }

    public SessionState create(String sessionId, String msisdn) {
        SessionState state = new SessionState(sessionId, msisdn);
        sessions.put(sessionId, state);
        log.debug("Created session {} (active sessions: {})", sessionId, sessions.size());
        return state;
    }

    public SessionState get(String sessionId) {
        SessionState state = sessions.get(sessionId);
        if (state != null) {
            state.touch();
        } else {
            log.debug("Session {} not found (expired or invalid)", sessionId);
        }
        return state;
    }

    public void remove(String sessionId) {
        SessionState removed = sessions.remove(sessionId);
        if (removed != null) {
            log.debug("Removed session {} (active sessions: {})", sessionId, sessions.size());
        }
    }

    @Scheduled(fixedRate = 60_000)
    public void cleanupExpiredSessions() {
        long cutoff = System.currentTimeMillis() - (properties.getTimeoutMinutes() * 60_000L);
        int before = sessions.size();
        sessions.entrySet().removeIf(e -> e.getValue().getLastActivity() < cutoff);
        int removed = before - sessions.size();
        if (removed > 0) {
            log.debug("Cleaned up {} expired USSD session(s), {} remaining", removed, sessions.size());
        }
    }
}