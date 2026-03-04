package com.ccapp.accounting.ledger.domain.entity;

import com.ccapp.accounting.ledger.domain.enums.AccountingEventType;
import com.ccapp.accounting.operation.domain.entity.Operation;
import com.ccapp.common.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "accounting_events", uniqueConstraints = {
        @UniqueConstraint(name = "uk_accounting_events_operation_id", columnNames = "operation_id")
})
public class AccountingEvent extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operation_id", nullable = false, foreignKey = @ForeignKey(name = "fk_accounting_events_operation"))
    private Operation operation;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private AccountingEventType eventType;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "total_debit", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalDebit;

    @Column(name = "total_credit", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalCredit;

    @Column(name = "posted_at", nullable = false)
    private OffsetDateTime postedAt;
}
