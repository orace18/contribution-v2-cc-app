package com.ccapp.payments.subscription.repository;

import com.ccapp.payments.subscription.domain.entity.SubscriptionPlan;
import com.ccapp.payments.subscription.domain.enums.SubscriptionGrade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {
    List<SubscriptionPlan> findByCountryAndActiveTrueOrderByGradeAscDurationMonthsAscPriceAsc(String country);

    List<SubscriptionPlan> findAllByCountryOrderByGradeAscDurationMonthsAscPriceAsc(String country);

    Optional<SubscriptionPlan> findByCodeAndCountryAndActiveTrue(String code, String country);

    Optional<SubscriptionPlan> findByIdAndActiveTrue(UUID id);

    Optional<SubscriptionPlan> findByCountryAndGradeAndDurationMonthsAndCurrency(String country,
                                                                                 SubscriptionGrade grade,
                                                                                 Integer durationMonths,
                                                                                 String currency);

    boolean existsByCountryAndGradeAndDurationMonthsAndCurrency(String country,
                                                                SubscriptionGrade grade,
                                                                Integer durationMonths,
                                                                String currency);

    List<SubscriptionPlan> findAllByOrderByCreatedAtDesc();
}
