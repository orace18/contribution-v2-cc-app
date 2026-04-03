package com.ccapp.events.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.events.domain.enums.EventProductSyncStatus;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "event_ticket_types", uniqueConstraints = {
        @UniqueConstraint(name = "uk_event_ticket_types_event_name", columnNames = {"event_id", "name"}),
        @UniqueConstraint(name = "uk_event_ticket_types_shop_product", columnNames = "shop_product_id")
}, indexes = {
        @Index(name = "idx_event_ticket_types_event_created", columnList = "event_id, created_at"),
        @Index(name = "idx_event_ticket_types_stock", columnList = "stock")
})
public class EventTicketType extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, foreignKey = @ForeignKey(name = "fk_event_ticket_types_event"))
    private Event event;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "price", nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "stock", nullable = false)
    private Integer stock;

    @Column(name = "cv", nullable = false, precision = 19, scale = 4)
    private BigDecimal cv;

    @Column(name = "platform_commission_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal platformCommissionRate;

    @Column(name = "dsb_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal dsbAmount;

    @Column(name = "shop_product_id")
    private UUID shopProductId;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_sync_status", nullable = false, length = 16)
    @Builder.Default
    private EventProductSyncStatus productSyncStatus = EventProductSyncStatus.PENDING;

    @Column(name = "product_sync_error", length = 255)
    private String productSyncError;

    @Column(name = "product_synced_at")
    private OffsetDateTime productSyncedAt;
}
