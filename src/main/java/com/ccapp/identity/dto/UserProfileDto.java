package com.ccapp.identity.dto;

public record UserProfileDto(
        String firstName,
        String lastName,
        String country,
        String phone
) {
}
