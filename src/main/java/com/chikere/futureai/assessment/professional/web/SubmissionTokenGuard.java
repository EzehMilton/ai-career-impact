package com.chikere.futureai.assessment.professional.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import java.util.ArrayDeque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public final class SubmissionTokenGuard {

    private static final String TOKEN_MAP_ATTR = SubmissionTokenGuard.class.getName() + ".tokenMap";
    private static final String TOKEN_QUEUE_ATTR = SubmissionTokenGuard.class.getName() + ".tokenQueue";
    private static final int TOKEN_LIMIT = 20;

    public String issueToken(HttpSession session) {
        String token = UUID.randomUUID().toString();
        synchronized (WebUtils.getSessionMutex(session)) {
            var map = getOrCreateTokenMap(session);
            var queue = getOrCreateTokenQueue(session);
            map.put(token, new TokenStatus.Registered());
            queue.addLast(token);
            while (queue.size() > TOKEN_LIMIT) {
                map.remove(queue.removeFirst());
            }
        }
        return token;
    }

    /** Returns the state the token had before this call. */
    public TokenStatus claimToken(HttpSession session, String token) {
        if (token == null || token.isEmpty()) return null;
        var map = getOrCreateTokenMap(session);
        var captured = new TokenStatus[1];
        map.compute(token, (k, existing) -> {
            captured[0] = existing;
            if (existing instanceof TokenStatus.Registered) return new TokenStatus.InProgress();
            return existing;
        });
        return captured[0];
    }

    public void completeToken(HttpSession session, String token, UUID assessmentId) {
        getOrCreateTokenMap(session).put(token, new TokenStatus.Completed(assessmentId));
    }

    public void freeToken(HttpSession session, String token) {
        getOrCreateTokenMap(session).remove(token);
    }

    public TokenStatus statusOf(HttpSession session, String token) {
        if (token == null || token.isEmpty()) return null;
        return getOrCreateTokenMap(session).get(token);
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<String, TokenStatus> getOrCreateTokenMap(HttpSession session) {
        var map = (ConcurrentHashMap<String, TokenStatus>) session.getAttribute(TOKEN_MAP_ATTR);
        if (map == null) {
            synchronized (WebUtils.getSessionMutex(session)) {
                map = (ConcurrentHashMap<String, TokenStatus>) session.getAttribute(TOKEN_MAP_ATTR);
                if (map == null) {
                    map = new ConcurrentHashMap<>();
                    session.setAttribute(TOKEN_MAP_ATTR, map);
                }
            }
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private ArrayDeque<String> getOrCreateTokenQueue(HttpSession session) {
        // Always called under WebUtils.getSessionMutex(session)
        var queue = (ArrayDeque<String>) session.getAttribute(TOKEN_QUEUE_ATTR);
        if (queue == null) {
            queue = new ArrayDeque<>();
            session.setAttribute(TOKEN_QUEUE_ATTR, queue);
        }
        return queue;
    }
}
