package com.ccapp.events.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.events.domain.enums.EventTicketStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "event_tickets", indexes = {
        @Index(name = "idx_event_tickets_buyer_created", columnList = "buyer_account_holder_id, created_at"),
        @Index(name = "idx_event_tickets_event_status", columnList = "event_id, status"),
        @Index(name = "idx_event_tickets_order", columnList = "order_id")
})
public class EventTicket extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, foreignKey = @ForeignKey(name = "fk_event_tickets_event"))
    private Event event;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "buyer_account_holder_id", nullable = false)
    private UUID buyerAccountHolderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_type_id", nullable = false, foreignKey = @ForeignKey(name = "fk_event_tickets_ticket_type"))
    private EventTicketType ticketType;

    @Column(name = "qr_code", nullable = false, columnDefinition = "TEXT", unique = true)
    private String qrCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private EventTicketStatus status;

    @Column(name = "checkin_timestamp")
    private OffsetDateTime checkinTimestamp;
}
