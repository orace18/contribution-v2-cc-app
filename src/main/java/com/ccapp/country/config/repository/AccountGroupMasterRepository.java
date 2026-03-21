package com.ccapp.country.config.repository;

import com.ccapp.country.config.domain.entity.AccountGroupMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountGroupMasterRepository extends JpaRepository<AccountGroupMaster, Integer> {

    List<AccountGroupMaster> findAllByOrderByGroupIdAsc();
}
