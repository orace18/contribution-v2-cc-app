package com.ccapp.commerce.repository;

import com.ccapp.commerce.domain.entity.CommerceProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CommerceProductRepository extends JpaRepository<CommerceProduct, UUID> {

    List<CommerceProduct> findByProductIdIn(Collection<UUID> productIds);
}
