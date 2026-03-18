package com.ccapp.admin.repository;

import com.ccapp.admin.domain.entity.AdminUser;
import com.ccapp.admin.domain.enums.AdminRole;
import com.ccapp.admin.domain.enums.AdminUserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {

    Optional<AdminUser> findByEmailIgnoreCase(String email);

    boolean existsByRole(AdminRole role);

    List<AdminUser> findByStatusOrderByCreatedAtDesc(AdminUserStatus status);

    List<AdminUser> findAllByOrderByCreatedAtDesc();

    boolean existsByCountryIdAndStatusAndRoleIn(UUID countryId,
                                                AdminUserStatus status,
                                                List<AdminRole> roles);
}
