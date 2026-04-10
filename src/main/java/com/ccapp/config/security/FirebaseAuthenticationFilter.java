package com.ccapp.config.security;

import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountCreatedVia;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.identity.domain.enums.KycStatus;
import com.ccapp.identity.domain.enums.UserRole;
import com.ccapp.identity.repository.UserRepository;
import com.ccapp.identity.service.FirebaseTokenVerifier;
import com.ccapp.identity.service.FirebaseUserClaims;
import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

    private final FirebaseTokenVerifier tokenVerifier;
    private final AppUserRepository appUserRepository;
    private final UserRepository userRepository;
    private final AccountHolderRepository accountHolderRepository;

    @Value("${firebase.auth.enabled:false}")
    private boolean firebaseAuthEnabled;

    @Value("${firebase.dev-bypass.mock-user.enabled:false}")
    private boolean devBypassMockUserEnabled;

    @Value("${firebase.dev-bypass.uid:}")
    private String devBypassUid;

    @Value("${firebase.dev-bypass.email:}")
    private String devBypassEmail;

    @Value("${firebase.dev-bypass.user-id:}")
    private String devBypassUserId;

    @Value("${firebase.dev-bypass.account-holder-id:}")
    private String devBypassAccountHolderId;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getServletPath();
        if (path.startsWith("/api/admin/") || path.startsWith("/admin/")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            if (!firebaseAuthEnabled) {
                maybeApplyDevMockAuthentication(path);
            }
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        FirebaseUserClaims claims;
        try {
            claims = tokenVerifier.verify(token);
        } catch (RuntimeException ex) {
            log.warn(
                    "Firebase auth failed path={} method={} reason={} token={}",
                    path,
                    request.getMethod(),
                    ex.getMessage(),
                    token
            );
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"success\":false,\"message\":\"Invalid or expired token\",\"data\":null}");
            return;
        }

        AppUser appUser = appUserRepository.findByFirebaseUid(claims.uid()).orElse(null);
        UUID legacyUserId = userRepository.findByFirebaseUid(claims.uid()).map(user -> user.getId()).orElse(null);
        if (appUser == null && legacyUserId == null) {
                log.warn("No local user mapping → creating user uid={}", claims.uid());

                UUID id = UUID.randomUUID();

            AccountHolder accountHolder = AccountHolder.builder()
                    .id(id)
                    .firstName("UNKNOWN")
                    .lastName("UNKNOWN")
                    .countryCode("BJ")
                    .serviceCountryCode("BJ")
                    .accountType(AccountType.STANDARD)
                    .createdVia(AccountCreatedVia.SELF)
                    .kycStatus(KycStatus.NOT_STARTED)
                    .build();

                accountHolderRepository.save(accountHolder);

                AppUser newUser = AppUser.builder()
                        .id(id)
                        .firebaseUid(claims.uid())
                        .accountHolderId(accountHolder.getId())
                        .role(UserRole.USER)
                        .build();

                appUser = appUserRepository.save(newUser);

        }

        AuthenticatedPrincipal principal = AuthenticatedPrincipal.builder()
                .firebaseUid(claims.uid())
                .email(claims.email())
                .userId(appUser != null ? appUser.getId() : legacyUserId)
                .accountHolderId(appUser != null ? appUser.getAccountHolderId() : legacyUserId)
                .internal(false)
                .build();

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private void maybeApplyDevMockAuthentication(String path) {
        if (!devBypassMockUserEnabled) {
            return;
        }

        String firebaseUid = normalizeOptional(devBypassUid);
        UUID userId = parseOptionalUuid(devBypassUserId);
        UUID accountHolderId = parseOptionalUuid(devBypassAccountHolderId);
        if (firebaseUid == null || (userId == null && accountHolderId == null)) {
            log.warn("Firebase dev bypass mock user ignored path={} reason=missing-config", path);
            return;
        }

        AuthenticatedPrincipal principal = AuthenticatedPrincipal.builder()
                .firebaseUid(firebaseUid)
                .email(normalizeOptional(devBypassEmail))
                .userId(userId)
                .accountHolderId(accountHolderId)
                .internal(false)
                .build();

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.warn("Firebase auth DISABLED - running in dev bypass mode source=mock-user path={}", path);
    }

    private UUID parseOptionalUuid(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }
        try {
            return UUID.fromString(normalized);
        } catch (IllegalArgumentException ex) {
            log.warn("Firebase dev bypass mock user ignored invalid uuid value");
            return null;
        }
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
