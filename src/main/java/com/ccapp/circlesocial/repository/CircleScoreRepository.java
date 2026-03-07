package com.ccapp.circlesocial.repository;

import com.ccapp.circlesocial.domain.entity.CircleScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CircleScoreRepository extends JpaRepository<CircleScore, UUID> {
}
