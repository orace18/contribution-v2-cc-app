package com.ccapp.config.security;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.identity.service.UserProvisioningService;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.identity.app_user.domain.entity.AppUser;
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

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

    private final FirebaseTokenVerifier tokenVerifier;
    private final UserProvisioningService userProvisioningService;

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
        if (path.startsWith("/api/admin/") || path.startsWith("/admin/") || path.startsWith("/dev-tools/")) {
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

        AppUser appUser;
        try {
            appUser = userProvisioningService.resolveForClaims(claims);
        } catch (BusinessException ex) {
            log.warn("Firebase identity provisioning failed path={} method={} firebaseUid={} reason={}",
                    path, request.getMethod(), claims.uid(), ex.getMessage());
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"success\":false,\"message\":\"" + ex.getMessage() + "\",\"data\":null}");
            return;
        }

        AuthenticatedPrincipal principal = AuthenticatedPrincipal.builder()
                .firebaseUid(claims.uid())
                .email(claims.email())
                .phoneNumber(claims.phoneNumber())
                .userId(appUser.getId())
                .accountHolderId(appUser.getAccountHolderId())
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
