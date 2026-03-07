package com.ccapp.admin.service;

import com.ccapp.admin.domain.entity.AdminUser;
import com.ccapp.admin.domain.enums.AdminRole;
import com.ccapp.admin.domain.enums.AdminUserStatus;
import com.ccapp.admin.repository.AdminUserRepository;
import com.ccapp.admin.security.AdminBootstrapProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapInitializer implements ApplicationRunner {

    private final AdminUserRepository adminUserRepository;
    private final AdminBootstrapProperties bootstrapProperties;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminUserRepository.existsByRole(AdminRole.ROLE_SUPER_ADMIN)) {
            return;
        }

        String email = bootstrapProperties.getEmail();
        String password = bootstrapProperties.getPassword();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.warn("Admin bootstrap skipped: missing admin.bootstrap.email/password");
            return;
        }

        AdminUser superAdmin = AdminUser.builder()
                .id(UUID.randomUUID())
                .email(email.trim().toLowerCase(Locale.ROOT))
                .passwordHash(passwordEncoder.encode(password))
                .role(AdminRole.ROLE_SUPER_ADMIN)
                .countryId(null)
                .status(AdminUserStatus.ACTIVE)
                .twoFactorEnabled(false)
                .build();

        adminUserRepository.save(superAdmin);
        log.info("Bootstrap SUPER_ADMIN created with email={}", superAdmin.getEmail());
    }
}
