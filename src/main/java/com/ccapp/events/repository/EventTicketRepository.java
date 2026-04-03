package com.ccapp.events.repository;

import com.ccapp.events.domain.entity.EventTicket;
import com.ccapp.events.domain.enums.EventTicketStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventTicketRepository extends JpaRepository<EventTicket, UUID> {

    List<EventTicket> findByBuyerAccountHolderIdOrderByCreatedAtDesc(UUID buyerAccountHolderId);

    List<EventTicket> findByOrderId(UUID orderId);

    List<EventTicket> findByEventIdAndStatusIn(UUID eventId, Collection<EventTicketStatus> statuses);

    long countByEventIdAndStatusIn(UUID eventId, Collection<EventTicketStatus> statuses);

    @Query("""
            select distinct t.orderId
            from EventTicket t
            where t.event.id = :eventId
              and t.status in :statuses
            """)
    List<UUID> findDistinctOrderIdsByEventIdAndStatusIn(@Param("eventId") UUID eventId,
                                                        @Param("statuses") Collection<EventTicketStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from EventTicket t where t.id = :ticketId")
    Optional<EventTicket> lockById(@Param("ticketId") UUID ticketId);
}
