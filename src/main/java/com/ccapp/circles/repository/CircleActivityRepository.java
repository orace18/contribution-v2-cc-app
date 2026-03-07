package com.ccapp.circles.repository;

import com.ccapp.circles.domain.entity.CircleActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CircleActivityRepository extends JpaRepository<CircleActivity, UUID> {
}
