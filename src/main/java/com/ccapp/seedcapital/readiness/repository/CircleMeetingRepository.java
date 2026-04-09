package com.ccapp.seedcapital.readiness.repository;

import com.ccapp.seedcapital.readiness.domain.entity.CircleMeeting;
import com.ccapp.seedcapital.readiness.domain.enums.CircleMeetingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface CircleMeetingRepository extends JpaRepository<CircleMeeting, UUID> {

    List<CircleMeeting> findByCircleIdOrderByMeetingAtDesc(UUID circleId);

    long countByCircleIdAndStatusAndQuorumReachedTrueAndMeetingAtAfter(UUID circleId,
                                                                       CircleMeetingStatus status,
                                                                       OffsetDateTime meetingAt);
}
