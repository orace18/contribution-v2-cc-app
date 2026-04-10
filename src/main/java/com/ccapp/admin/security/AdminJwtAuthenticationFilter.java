package com.ccapp.admin.security;

import com.ccapp.admin.domain.entity.AdminUser;
import com.ccapp.admin.domain.enums.AdminUserStatus;
import com.ccapp.admin.repository.AdminUserRepository;
import com.ccapp.country.repository.CountryRepository;
import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.exception.UnauthorizedException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class AdminJwtAuthenticationFilter extends OncePerRequestFilter {

    private final AdminJwtService adminJwtService;
    private final AdminUserRepository adminUserRepository;
    private final CountryRepository countryRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getServletPath();
        boolean adminRoute = path.startsWith("/api/admin/")
                || path.startsWith("/admin/")
                || path.startsWith("/dev-tools/");
        boolean sharedEventRoute = path.startsWith("/api/events");
        boolean adminAuthRoute = path.startsWith("/api/admin/auth/") || path.startsWith("/admin/auth/");
        if ((!adminRoute && !sharedEventRoute) || adminAuthRoute) {
            filterChain.doFilter(request, response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = authHeader.substring(7);
            AdminJwtService.AdminTokenClaims claims = adminJwtService.verify(token, AdminTokenType.ACCESS);

            AdminUser adminUser = adminUserRepository.findById(claims.adminUserId())
                    .orElseThrow(() -> new UnauthorizedException("Admin user not found"));

            if (adminUser.getStatus() != AdminUserStatus.ACTIVE) {
                throw new UnauthorizedException("Admin account disabled");
            }
            if (!adminUser.isTwoFactorEnabled()) {
                throw new UnauthorizedException("2FA not activated");
            }

            String countryCode = adminUser.getCountryId() == null ? null
                    : countryRepository.findById(adminUser.getCountryId())
                            .map(c -> c.getCode())
                            .orElse(null);

            AdminPrincipal principal = AdminPrincipal.builder()
                    .adminUserId(adminUser.getId())
                    .email(adminUser.getEmail())
                    .role(adminUser.getRole())
                    .countryId(adminUser.getCountryId())
                    .countryCode(countryCode)
                    .build();

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority(adminUser.getRole().name()))
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (RuntimeException ex) {
            if (sharedEventRoute && !adminRoute) {
                filterChain.doFilter(request, response);
                return;
            }
            log.warn(
                    "Admin JWT auth failed path={} method={} reason={}",
                    path,
                    request.getMethod(),
                    ex.getMessage()
            );
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), CommonResponse.error("Unauthorized admin access"));
        }
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return true;
    }
}
