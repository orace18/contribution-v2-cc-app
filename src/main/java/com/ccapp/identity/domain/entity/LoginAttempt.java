package com.ccapp.identity.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "login_attempts", indexes = {
        @Index(name = "idx_login_attempts_ip_created_at", columnList = "ip_address, created_at"),
        @Index(name = "idx_login_attempts_device_created_at", columnList = "device_identifier, created_at")
})
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "device_identifier", nullable = false, length = 128)
    private String deviceIdentifier;

    @Column(name = "ip_address", nullable = false, length = 64)
    private String ipAddress;

    @Column(name = "succeeded", nullable = false)
    private boolean succeeded;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
