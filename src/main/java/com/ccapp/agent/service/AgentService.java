package com.ccapp.agent.service;

import com.ccapp.agent.domain.entity.AgentPermission;
import com.ccapp.agent.domain.entity.AgentProfile;
import com.ccapp.agent.dto.AgentProfileResponse;
import com.ccapp.agent.dto.CreateAgentRequest;
import com.ccapp.agent.repository.AgentPermissionRepository;
import com.ccapp.agent.repository.AgentProfileRepository;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentProfileRepository agentProfileRepository;
    private final AgentPermissionRepository agentPermissionRepository;
    private final AppUserRepository appUserRepository;
    private final CircleRepository circleRepository;

    @Transactional
    public AgentProfileResponse createAgent(CreateAgentRequest request) {
        appUserRepository.findById(request.appUserId())
                .orElseThrow(() -> new NotFoundException("App user not found"));

        if (request.circleId() != null && !circleRepository.existsById(request.circleId())) {
            throw new NotFoundException("Circle not found");
        }

        AgentProfile profile = agentProfileRepository.save(AgentProfile.builder()
                .appUserId(request.appUserId())
                .agentType(request.agentType())
                .circleId(request.circleId())
                .status(normalizeStatus(request.status()))
                .build());

        List<AgentPermission> permissions = savePermissions(profile, request.permissions());
        return toResponse(profile, permissions);
    }

    @Transactional(readOnly = true)
    public List<AgentProfileResponse> listAgents(UUID actorUserId) {
        return agentProfileRepository.findByAppUserIdOrderByCreatedAtDesc(actorUserId).stream()
                .map(profile -> toResponse(profile, agentPermissionRepository.findByAgentProfile_IdOrderByCreatedAtAsc(profile.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public void assertActiveAgent(UUID actorUserId) {
        agentProfileRepository.findFirstByAppUserIdAndStatusOrderByCreatedAtDesc(actorUserId, "ACTIVE")
                .orElseThrow(() -> new BusinessException("Active agent profile required"));
    }

    private List<AgentPermission> savePermissions(AgentProfile profile, List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return List.of();
        }

        Set<String> unique = new LinkedHashSet<>();
        for (String value : permissions) {
            if (value != null && !value.isBlank()) {
                unique.add(value.trim().toUpperCase(Locale.ROOT));
            }
        }

        List<AgentPermission> rows = new ArrayList<>();
        for (String permission : unique) {
            rows.add(AgentPermission.builder()
                    .agentProfile(profile)
                    .permission(permission)
                    .build());
        }
        if (rows.isEmpty()) {
            return List.of();
        }
        return agentPermissionRepository.saveAll(rows);
    }

    private String normalizeStatus(String status) {
        String normalized = status == null || status.isBlank()
                ? "ACTIVE"
                : status.trim().toUpperCase(Locale.ROOT);

        if (!normalized.equals("ACTIVE") && !normalized.equals("INACTIVE") && !normalized.equals("SUSPENDED")) {
            throw new BusinessException("Invalid agent status");
        }
        return normalized;
    }

    private AgentProfileResponse toResponse(AgentProfile profile, List<AgentPermission> permissions) {
        return new AgentProfileResponse(
                profile.getId(),
                profile.getAppUserId(),
                profile.getAgentType(),
                profile.getCircleId(),
                profile.getStatus(),
                profile.getCreatedAt(),
                permissions.stream().map(AgentPermission::getPermission).toList()
        );
    }
}
