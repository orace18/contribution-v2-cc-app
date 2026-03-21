package com.ccapp.progress.domain.enums;

import java.util.List;

public enum TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    EXPIRED,
    REJECTED;

    public boolean active() {
        return this == PENDING || this == IN_PROGRESS;
    }

    public boolean terminal() {
        return !active();
    }

    public static List<TaskStatus> activeStatuses() {
        return List.of(PENDING, IN_PROGRESS);
    }
}
