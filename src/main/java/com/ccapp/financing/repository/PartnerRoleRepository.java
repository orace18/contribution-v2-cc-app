package com.ccapp.financing.repository;

import com.ccapp.financing.domain.entity.PartnerRole;
import com.ccapp.financing.domain.enums.PartnerBackofficeRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PartnerRoleRepository extends JpaRepository<PartnerRole, UUID> {

    List<PartnerRole> findByPartnerUserId(UUID partnerUserId);

    boolean existsByPartnerUserIdAndRoleIn(UUID partnerUserId, Collection<PartnerBackofficeRole> roles);
}
