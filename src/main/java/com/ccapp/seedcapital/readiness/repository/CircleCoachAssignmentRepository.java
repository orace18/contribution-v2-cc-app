package com.ccapp.seedcapital.readiness.repository;

import com.ccapp.seedcapital.readiness.domain.entity.CircleCoachAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CircleCoachAssignmentRepository extends JpaRepository<CircleCoachAssignment, UUID> {
}
