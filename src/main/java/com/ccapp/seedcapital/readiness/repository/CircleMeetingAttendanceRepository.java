package com.ccapp.seedcapital.readiness.repository;

import com.ccapp.seedcapital.readiness.domain.entity.CircleMeetingAttendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CircleMeetingAttendanceRepository extends JpaRepository<CircleMeetingAttendance, UUID> {

    List<CircleMeetingAttendance> findByMeetingIdOrderByCreatedAtAsc(UUID meetingId);

    void deleteByMeetingId(UUID meetingId);
}
