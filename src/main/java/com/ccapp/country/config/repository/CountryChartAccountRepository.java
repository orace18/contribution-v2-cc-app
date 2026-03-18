package com.ccapp.country.config.repository;

import com.ccapp.country.config.domain.entity.CountryChartAccount;
import com.ccapp.country.config.domain.enums.CountryChartAccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CountryChartAccountRepository extends JpaRepository<CountryChartAccount, UUID> {

    List<CountryChartAccount> findByCountryCodeOrderByAccountCodeAsc(String countryCode);

    List<CountryChartAccount> findByCountryCodeAndStatusOrderByAccountCodeAsc(String countryCode,
                                                                               CountryChartAccountStatus status);

    Optional<CountryChartAccount> findByIdAndCountryCode(UUID id, String countryCode);

    boolean existsByCountryCodeAndAccountCode(String countryCode, String accountCode);

    boolean existsByCountryCodeAndAccountCodeAndStatus(String countryCode,
                                                       String accountCode,
                                                       CountryChartAccountStatus status);

    long countByCountryCodeAndStatus(String countryCode, CountryChartAccountStatus status);
}
