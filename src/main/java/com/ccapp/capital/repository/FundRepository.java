package com.ccapp.capital.repository;

import com.ccapp.capital.domain.entity.Fund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface FundRepository extends JpaRepository<Fund, UUID> {

    List<Fund> findByCurrencyOrderByCreatedAtDesc(String currency);

    @Query("select coalesce(sum(f.availableCapital), 0) from Fund f")
    BigDecimal sumAvailableCapital();
}
