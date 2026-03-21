package com.ccapp.country.config.repository;

import com.ccapp.country.config.domain.entity.CountryAccountSystemMapping;
import com.ccapp.country.config.domain.entity.CountryAccountSystemMappingId;
import com.ccapp.country.config.domain.enums.CountryAccountSystemTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CountryAccountSystemMappingRepository extends JpaRepository<CountryAccountSystemMapping, CountryAccountSystemMappingId> {

    List<CountryAccountSystemMapping> findByCountryCodeOrderBySystemTagAsc(String countryCode);

    Optional<CountryAccountSystemMapping> findByCountryCodeAndSystemTag(String countryCode, CountryAccountSystemTag systemTag);

    boolean existsByCountryCodeAndSystemTag(String countryCode, CountryAccountSystemTag systemTag);
}
