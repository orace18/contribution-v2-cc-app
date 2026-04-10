package com.ccapp.devtools.dto;

import com.ccapp.common.exception.BusinessException;

import java.util.UUID;

public record DeleteUserRequest(
        String email,
        UUID userId
) {
    public void validate() {
        boolean hasEmail = normalizedEmail() != null;
        boolean hasUserId = userId != null;
        if (hasEmail == hasUserId) {
            throw new BusinessException("Provide exactly one of email or userId");
        }
    }

    public String normalizedEmail() {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim();
    }
}
