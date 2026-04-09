package com.ccapp.financing.repository;

import com.ccapp.financing.domain.entity.FinancingOffer;
import com.ccapp.financing.domain.enums.FinancingOfferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface FinancingOfferRepositoryCustom {

    Page<FinancingOffer> search(UUID partnerId,
                                FinancingOfferStatus status,
                                String countryCode,
                                String search,
                                Pageable pageable);
}
