package com.ccapp.financing.repository;

import com.ccapp.financing.domain.entity.PartnerReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnerReportRepository extends JpaRepository<PartnerReport, UUID> {

    List<PartnerReport> findByPartnerIdOrderByCreatedAtDesc(UUID partnerId);

    Optional<PartnerReport> findFirstByPartnerIdAndReportTypeAndPeriodStartAndPeriodEnd(UUID partnerId,
                                                                                         String reportType,
                                                                                         LocalDate periodStart,
                                                                                         LocalDate periodEnd);
}
