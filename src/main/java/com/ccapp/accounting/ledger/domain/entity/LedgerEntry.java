package com.ccapp.accounting.ledger.domain.entity;

import com.ccapp.accounting.ledger.domain.enums.LedgerEntryType;
import com.ccapp.common.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ledger_entries", indexes = {
        @Index(name = "idx_ledger_entries_account_currency", columnList = "account_code, currency")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_ledger_entries_event_line", columnNames = {"event_id", "line_no"})
})
public class LedgerEntry extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ledger_entries_event"))
    private AccountingEvent event;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "account_code", nullable = false, length = 64)
    private String accountCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 16)
    private LedgerEntryType entryType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "description", length = 255)
    private String description;
}
