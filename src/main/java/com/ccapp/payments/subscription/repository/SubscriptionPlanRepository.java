package com.ccapp.payments.subscription.repository;

import com.ccapp.payments.subscription.domain.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {
    List<SubscriptionPlan> findByCountryAndActiveTrueOrderByPriceAsc(String country);

    Optional<SubscriptionPlan> findByCodeAndCountryAndActiveTrue(String code, String country);

    List<SubscriptionPlan> findAllByOrderByCreatedAtDesc();
}
