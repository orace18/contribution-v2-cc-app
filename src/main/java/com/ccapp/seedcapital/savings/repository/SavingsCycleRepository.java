package com.ccapp.seedcapital.savings.repository;

import com.ccapp.seedcapital.savings.domain.entity.SavingsCycle;
import com.ccapp.seedcapital.savings.domain.enums.SavingsCycleStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SavingsCycleRepository extends JpaRepository<SavingsCycle, UUID> {

    List<SavingsCycle> findByCircleIdOrderByWindowStartDesc(UUID circleId);

    long countByCircleId(UUID circleId);

    List<SavingsCycle> findByCircleIdAndStatusInAndWindowStartAfterOrderByWindowStartDesc(UUID circleId,
                                                                                           Collection<SavingsCycleStatus> statuses,
                                                                                           OffsetDateTime windowStart);
}
