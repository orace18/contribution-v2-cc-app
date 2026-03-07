package com.ccapp.circlesocial.repository;

import com.ccapp.circlesocial.domain.entity.CircleProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CircleProgressRepository extends JpaRepository<CircleProgress, UUID> {
}
