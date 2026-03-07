package com.ccapp.payments.psp.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SimpleRateLimiter {

    private final Map<String, Deque<Long>> requests = new ConcurrentHashMap<>();

    public boolean allow(String key, int maxRequestsPerMinute) {
        long now = Instant.now().toEpochMilli();
        long threshold = now - 60_000;

        Deque<Long> queue = requests.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && queue.peekFirst() < threshold) {
                queue.pollFirst();
            }
            if (queue.size() >= maxRequestsPerMinute) {
                return false;
            }
            queue.addLast(now);
            return true;
        }
    }
}
