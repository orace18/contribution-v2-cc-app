package com.ccapp.commerce.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "commerce_products")
public class CommerceProduct extends AuditableEntity {

    @Id
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "dsb_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal dsbAmount;
}
