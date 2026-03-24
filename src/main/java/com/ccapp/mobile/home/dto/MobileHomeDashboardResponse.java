package com.ccapp.mobile.home.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record MobileHomeDashboardResponse(
        UserSection user,
        EngagementSection engagement,
        NextActionSection nextAction,
        CircleSection circle,
        List<QuickAction> quickActions,
        FinancingSection financing
) {
    public record UserSection(
            String firstName,
            String profilePicture,
            String membershipType
    ) {
    }

    public record EngagementSection(
            int streakDays,
            List<TodayTask> todayTasks,
            ProgressSection progress
    ) {
    }

    public record TodayTask(
            UUID id,
            String title,
            boolean completed,
            String type
    ) {
    }

    public record ProgressSection(
            int completed,
            int total
    ) {
    }

    public record NextActionSection(
            String type,
            String title,
            String description,
            String ctaLabel,
            String ctaAction
    ) {
    }

    public record CircleSection(
            boolean hasCircle,
            List<RecentActivity> recentActivities
    ) {
    }

    public record RecentActivity(
            String userName,
            String message,
            OffsetDateTime timestamp
    ) {
    }

    public record QuickAction(
            String type,
            String label
    ) {
    }

    public record FinancingSection(
            int progressPercent,
            BigDecimal remainingAmount,
            BigDecimal targetAmount
    ) {
    }
}
