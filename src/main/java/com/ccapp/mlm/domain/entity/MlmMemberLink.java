package com.ccapp.mlm.domain.entity;

import com.ccapp.common.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "mlm_member_links", indexes = {
        @Index(name = "idx_mlm_member_links_sponsor", columnList = "sponsor_account_holder_id"),
        @Index(name = "idx_mlm_member_links_placement", columnList = "placement_account_holder_id")
})
public class MlmMemberLink extends AuditableEntity {

    @Id
    @Column(name = "account_holder_id", nullable = false)
    private UUID accountHolderId;

    @Column(name = "sponsor_account_holder_id")
    private UUID sponsorAccountHolderId;

    @Column(name = "placement_account_holder_id", nullable = false)
    private UUID placementAccountHolderId;
}
