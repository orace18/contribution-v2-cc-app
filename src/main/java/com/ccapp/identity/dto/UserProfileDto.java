package com.ccapp.identity.dto;

import java.util.UUID;

public record UserProfileDto(
        String firstName,
        String lastName,
        String country,
        String phone,
        UUID profilePhotoMediaId
) {
}
