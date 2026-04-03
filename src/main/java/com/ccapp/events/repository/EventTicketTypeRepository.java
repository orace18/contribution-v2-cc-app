package com.ccapp.events.repository;

import com.ccapp.events.domain.entity.EventTicketType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventTicketTypeRepository extends JpaRepository<EventTicketType, UUID> {

    List<EventTicketType> findByEventIdOrderByCreatedAtAsc(UUID eventId);

    List<EventTicketType> findByEventIdIn(Collection<UUID> eventIds);

    List<EventTicketType> findByShopProductIdIn(Collection<UUID> shopProductIds);

    List<EventTicketType> findByStockLessThanEqualAndShopProductIdIsNotNull(Integer stock);

    Optional<EventTicketType> findByShopProductId(UUID shopProductId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from EventTicketType t where t.id = :ticketTypeId")
    Optional<EventTicketType> lockById(@Param("ticketTypeId") UUID ticketTypeId);
}
