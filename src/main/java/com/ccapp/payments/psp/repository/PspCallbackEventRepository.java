package com.ccapp.payments.psp.repository;

import com.ccapp.payments.psp.domain.entity.PspCallbackEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PspCallbackEventRepository extends JpaRepository<PspCallbackEvent, UUID> {
    boolean existsByNonce(String nonce);
}
