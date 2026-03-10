package com.ccapp.country.repository;

import com.ccapp.country.domain.entity.Country;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CountryRepository extends JpaRepository<Country, UUID> {

    Optional<Country> findByCode(String code);

    Optional<Country> findByFallbackTrue();

    List<Country> findByActiveTrueOrderByNameAsc();

    List<Country> findAllByOrderByCodeAsc();
}
