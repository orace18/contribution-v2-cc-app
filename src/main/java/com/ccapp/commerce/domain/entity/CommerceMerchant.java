package com.ccapp.commerce.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import com.ccapp.commerce.domain.enums.CommerceMerchantStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "commerce_merchants", indexes = {
        @Index(name = "idx_commerce_merchants_account_holder", columnList = "account_holder_id")
})
public class CommerceMerchant extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "account_holder_id", nullable = false, unique = true)
    private UUID accountHolderId;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "country_code", length = 3)
    private String countryCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CommerceMerchantStatus status;

    @Column(name = "logo_url", length = 512)
    private String logoUrl;
}
