package com.ccapp.financing.repository;

import com.ccapp.financing.domain.entity.LoanApplication;
import com.ccapp.financing.domain.enums.LoanApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface LoanApplicationRepositoryCustom {

    Page<LoanApplication> searchForAdmin(UUID partnerId,
                                         LoanApplicationStatus status,
                                         String countryCode,
                                         Pageable pageable);
}
