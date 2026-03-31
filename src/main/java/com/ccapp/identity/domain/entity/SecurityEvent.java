package com.ccapp.identity.domain.entity;

import com.ccapp.identity.domain.enums.SecurityEventType;
import com.ccapp.identity.domain.enums.SecurityRiskLevel;
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
@Table(name = "security_events", indexes = {
        @Index(name = "idx_security_events_user_id_created_at", columnList = "user_id, created_at"),
        @Index(name = "idx_security_events_event_type_created_at", columnList = "event_type, created_at")
})
public class SecurityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private SecurityEventType eventType;

    @Column(name = "device_identifier", length = 128)
    private String deviceIdentifier;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 16)
    private SecurityRiskLevel riskLevel;

    @Column(name = "country_code", length = 8)
    private String countryCode;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
