package com.ccapp.country.config.repository;

import com.ccapp.country.config.domain.entity.CountryConfigVersion;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface CountryConfigVersionRepository extends JpaRepository<CountryConfigVersion, UUID> {

    boolean existsByCountryCode(String countryCode);

    Optional<CountryConfigVersion> findFirstByCountryCodeAndConfigTypeOrderByVersionDesc(String countryCode,
                                                                                          CountryConfigType configType);

    Optional<CountryConfigVersion> findFirstByCountryCodeAndConfigTypeAndEffectiveDateLessThanEqualOrderByEffectiveDateDescVersionDesc(
            String countryCode,
            CountryConfigType configType,
            OffsetDateTime asOf
    );

    List<CountryConfigVersion> findByCountryCodeAndConfigTypeOrderByVersionDesc(String countryCode,
                                                                                 CountryConfigType configType);

    Optional<CountryConfigVersion> findByCountryCodeAndConfigTypeAndVersion(String countryCode,
                                                                             CountryConfigType configType,
                                                                             Integer version);

    @Query("select distinct c.countryCode from CountryConfigVersion c")
    Set<String> findDistinctCountryCodes();
}
