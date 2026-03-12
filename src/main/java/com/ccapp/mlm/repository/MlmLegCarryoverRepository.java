package com.ccapp.mlm.repository;

import com.ccapp.mlm.domain.entity.MlmLegCarryover;
import com.ccapp.mlm.domain.entity.MlmLegCarryoverId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MlmLegCarryoverRepository extends JpaRepository<MlmLegCarryover, MlmLegCarryoverId> {
    List<MlmLegCarryover> findByWeekId(Integer weekId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from MlmLegCarryover c
            where c.weekId = :weekId
            """)
    int deleteByWeekId(@Param("weekId") int weekId);
}
