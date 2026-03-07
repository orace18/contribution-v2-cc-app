package com.ccapp.progress.repository;

import com.ccapp.progress.domain.entity.UserProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserProgressRepository extends JpaRepository<UserProgress, UUID> {
}
