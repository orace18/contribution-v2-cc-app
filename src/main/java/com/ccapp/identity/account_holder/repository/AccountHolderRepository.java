package com.ccapp.identity.account_holder.repository;

import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountHolderRepository extends JpaRepository<AccountHolder, UUID> {
}
