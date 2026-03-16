package com.ccapp.payments.commission.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CommissionEventRateLimiter {

    private final Map<String, Deque<Long>> requests = new ConcurrentHashMap<>();

    public boolean allow(String key, int maxPerMinute) {
        long now = Instant.now().toEpochMilli();
        long threshold = now - 60_000;

        Deque<Long> queue = requests.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && queue.peekFirst() < threshold) {
                queue.pollFirst();
            }
            if (queue.size() >= maxPerMinute) {
                return false;
            }
            queue.addLast(now);
            return true;
        }
    }
}
