package com.ccapp.accounting.reconciliation.domain.entity;

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
@Table(name = "reconciliation_records", indexes = {
        @Index(name = "idx_recon_batch_id", columnList = "batch_id"),
        @Index(name = "idx_recon_psp_reference", columnList = "psp_reference")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_recon_batch_ref", columnNames = {"batch_id", "psp_reference"})
})
public class ReconciliationRecord extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "psp_reference", nullable = false, length = 128)
    private String pspReference;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "matched", nullable = false)
    private boolean matched;

    @Column(name = "adjustment_posted", nullable = false)
    private boolean adjustmentPosted;

    @Column(name = "notes", length = 255)
    private String notes;
}
