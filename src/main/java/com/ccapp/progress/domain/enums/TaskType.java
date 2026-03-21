package com.ccapp.progress.domain.enums;

import java.util.Arrays;
import java.util.List;

public enum TaskType {
    COMPLETE_KYC(TaskCategory.ONBOARDING, false, true, 700, false),
    JOIN_CIRCLE(TaskCategory.ENGAGEMENT, false, true, 600, false),
    MAKE_FIRST_CONTRIBUTION(TaskCategory.FINANCIAL, false, true, 650, false),
    INVITE_MEMBER(TaskCategory.ENGAGEMENT, false, true, 350, false),
    APPLY_LOAN(TaskCategory.FINANCIAL, false, true, 800, false),
    REPAY_INSTALLMENT(TaskCategory.FINANCIAL, true, false, 950, false),
    COMPLETE_PROFILE(TaskCategory.ONBOARDING, false, true, 500, false),
    VERIFY_PHONE(TaskCategory.ONBOARDING, false, true, 550, false),

    PHONE_VERIFIED(TaskCategory.LEGACY, false, false, 0, true),
    KYC_COMPLETED(TaskCategory.LEGACY, false, false, 0, true),
    CIRCLE_JOINED(TaskCategory.LEGACY, false, false, 0, true),
    TRAINING_COMPLETED(TaskCategory.LEGACY, false, false, 0, true),
    PROJECT_SUBMITTED(TaskCategory.LEGACY, false, false, 0, true),
    PERSONAL_CONTRIBUTION_PAID(TaskCategory.LEGACY, false, false, 0, true);

    private final TaskCategory category;
    private final boolean repeatable;
    private final boolean progressTracked;
    private final int defaultPriority;
    private final boolean legacy;

    TaskType(TaskCategory category,
             boolean repeatable,
             boolean progressTracked,
             int defaultPriority,
             boolean legacy) {
        this.category = category;
        this.repeatable = repeatable;
        this.progressTracked = progressTracked;
        this.defaultPriority = defaultPriority;
        this.legacy = legacy;
    }

    public TaskCategory category() {
        return category;
    }

    public boolean repeatable() {
        return repeatable;
    }

    public boolean progressTracked() {
        return progressTracked;
    }

    public int defaultPriority() {
        return defaultPriority;
    }

    public boolean legacy() {
        return legacy;
    }

    public boolean financial() {
        return category == TaskCategory.FINANCIAL;
    }

    public static List<TaskType> engagementTypes() {
        return Arrays.stream(values())
                .filter(taskType -> !taskType.legacy)
                .toList();
    }

    public static List<TaskType> progressTypes() {
        return engagementTypes().stream()
                .filter(TaskType::progressTracked)
                .toList();
    }

    public enum TaskCategory {
        ONBOARDING,
        ENGAGEMENT,
        FINANCIAL,
        LEGACY
    }
}
