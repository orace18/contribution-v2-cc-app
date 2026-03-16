package com.ccapp.chat.dto;

public record ChatRealtimeEvent(
        String eventType,
        ChatMessageResponse payload
) {
}
