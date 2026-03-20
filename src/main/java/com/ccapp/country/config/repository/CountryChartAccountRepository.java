package com.ccapp.country.config.repository;

import com.ccapp.country.config.domain.entity.CountryChartAccount;
import com.ccapp.country.config.domain.enums.CountryAccountGroup;
import com.ccapp.country.config.domain.enums.CountryChartAccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CountryChartAccountRepository extends JpaRepository<CountryChartAccount, UUID> {

    List<CountryChartAccount> findByCountryCodeOrderByAccountCodeAsc(String countryCode);

    List<CountryChartAccount> findByCountryCodeAndStatusOrderByAccountCodeAsc(String countryCode,
                                                                               CountryChartAccountStatus status);

    Optional<CountryChartAccount> findByIdAndCountryCode(UUID id, String countryCode);

    Optional<CountryChartAccount> findFirstByCountryCodeAndAccountGroupOrderByCreatedAtAsc(String countryCode,
                                                                                            CountryAccountGroup accountGroup);

    Optional<CountryChartAccount> findFirstByCountryCodeAndAccountGroupAndStatusOrderByCreatedAtAsc(String countryCode,
                                                                                                      CountryAccountGroup accountGroup,
                                                                                                      CountryChartAccountStatus status);

    Optional<CountryChartAccount> findByAccountHolderIdAndCountryCode(UUID accountHolderId, String countryCode);

    Optional<CountryChartAccount> findByAccountHolderIdAndCountryCodeAndStatus(UUID accountHolderId,
                                                                                String countryCode,
                                                                                CountryChartAccountStatus status);

    boolean existsByAccountHolderIdAndCountryCode(UUID accountHolderId, String countryCode);

    boolean existsByCountryCodeAndAccountCode(String countryCode, String accountCode);

    boolean existsByCountryCodeAndAccountCodeAndStatus(String countryCode,
                                                       String accountCode,
                                                       CountryChartAccountStatus status);

    long countByCountryCodeAndStatus(String countryCode, CountryChartAccountStatus status);

    @Query(value = """
            SELECT COALESCE(MAX(CAST(RIGHT(account_code, 6) AS INTEGER)), 0)
            FROM country_chart_accounts
            WHERE account_code LIKE CONCAT(:prefix, '%')
              AND account_code ~ '^[0-9]+$'
              AND LENGTH(account_code) = :totalLength
            """, nativeQuery = true)
    int findMaxGeneratedSequenceForPrefix(@Param("prefix") String prefix, @Param("totalLength") int totalLength);
}
