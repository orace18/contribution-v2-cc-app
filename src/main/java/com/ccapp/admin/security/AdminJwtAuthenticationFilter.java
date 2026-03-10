package com.ccapp.admin.security;

import com.ccapp.admin.domain.entity.AdminUser;
import com.ccapp.admin.domain.enums.AdminUserStatus;
import com.ccapp.admin.repository.AdminUserRepository;
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
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getServletPath();
        if (!path.startsWith("/api/admin/") || path.startsWith("/api/admin/auth/")) {
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

            AdminPrincipal principal = AdminPrincipal.builder()
                    .adminUserId(adminUser.getId())
                    .email(adminUser.getEmail())
                    .role(adminUser.getRole())
                    .countryId(adminUser.getCountryId())
                    .build();

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority(adminUser.getRole().name()))
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (RuntimeException ex) {
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
