package com.ccapp.progress.dto;

public record TaskCompletionResponse(
        UserTaskResponse task,
        UserProgressResponse progress
) {
}
