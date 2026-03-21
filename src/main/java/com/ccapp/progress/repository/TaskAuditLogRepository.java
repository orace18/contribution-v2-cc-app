package com.ccapp.progress.repository;

import com.ccapp.progress.domain.entity.TaskAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TaskAuditLogRepository extends JpaRepository<TaskAuditLog, UUID> {
}
