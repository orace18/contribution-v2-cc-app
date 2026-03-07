package com.ccapp.notifications.domain.entity;

import com.ccapp.notifications.domain.enums.NotificationChannel;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "notification_templates", uniqueConstraints = {
        @UniqueConstraint(name = "uk_notification_templates_code_channel", columnNames = {"code", "channel"})
})
public class NotificationTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16)
    private NotificationChannel channel;

    @Column(name = "subject", length = 255)
    private String subject;

    @Column(name = "body_template", nullable = false, length = 4000)
    private String bodyTemplate;

    @Column(name = "active", nullable = false)
    private boolean active;
}
