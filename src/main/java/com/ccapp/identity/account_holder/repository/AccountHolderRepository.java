package com.ccapp.identity.account_holder.repository;

import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountHolderRepository extends JpaRepository<AccountHolder, UUID> {

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumberAndIdNot(String phoneNumber, UUID id);

    Optional<AccountHolder> findByPhoneNumber(String phoneNumber);

    @Query("select a.id from AccountHolder a")
    List<UUID> findAllIds();

    @Query("select a.id from AccountHolder a where upper(a.serviceCountryCode) = upper(:serviceCountryCode)")
    List<UUID> findAllIdsByServiceCountryCode(@Param("serviceCountryCode") String serviceCountryCode);
}
