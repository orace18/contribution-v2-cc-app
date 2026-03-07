package com.ccapp.admin.repository;

import com.ccapp.admin.domain.entity.AdminCountry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminCountryRepository extends JpaRepository<AdminCountry, UUID> {

    Optional<AdminCountry> findByCountryCode(String countryCode);

    List<AdminCountry> findAllByOrderByCountryCodeAsc();
}
