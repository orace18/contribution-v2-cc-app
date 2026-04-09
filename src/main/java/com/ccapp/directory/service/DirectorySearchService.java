package com.ccapp.directory.service;

import com.ccapp.circles.domain.entity.Circle;
import com.ccapp.circles.domain.entity.CircleMember;
import com.ccapp.circles.repository.CircleMemberRepository;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.directory.dto.DirectoryCircleResultResponse;
import com.ccapp.directory.dto.DirectoryMemberResultResponse;
import com.ccapp.directory.dto.DirectorySearchResponse;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DirectorySearchService {

    private final CircleRepository circleRepository;
    private final CircleMemberRepository circleMemberRepository;
    private final AccountHolderRepository accountHolderRepository;

    @Transactional(readOnly = true)
    public DirectorySearchResponse search(UUID requesterAccountHolderId,
                                          String query,
                                          String scopes) {
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery == null) {
            return new DirectorySearchResponse(List.of(), List.of());
        }

        EnumSet<SearchScope> requestedScopes = parseScopes(scopes);
        List<Circle> activeCircles = circleRepository.findActiveCirclesForUser(requesterAccountHolderId);

        List<DirectoryCircleResultResponse> circleResults = requestedScopes.contains(SearchScope.CIRCLES)
                ? findCircles(activeCircles, normalizedQuery)
                : List.of();
        List<DirectoryMemberResultResponse> memberResults = requestedScopes.contains(SearchScope.MEMBERS)
                ? findMembers(activeCircles, normalizedQuery)
                : List.of();

        return new DirectorySearchResponse(circleResults, memberResults);
    }

    private List<DirectoryCircleResultResponse> findCircles(List<Circle> circles, String normalizedQuery) {
        return circles.stream()
                .filter(circle -> contains(circle.getName(), normalizedQuery))
                .sorted(Comparator.comparing(circle -> safeText(circle.getName())))
                .map(circle -> new DirectoryCircleResultResponse(
                        circle.getId(),
                        circle.getName(),
                        circleMemberRepository.countByCircle_Id(circle.getId())
                ))
                .toList();
    }

    private List<DirectoryMemberResultResponse> findMembers(List<Circle> circles, String normalizedQuery) {
        List<UUID> circleIds = circles.stream()
                .map(Circle::getId)
                .toList();
        if (circleIds.isEmpty()) {
            return List.of();
        }

        List<CircleMember> memberships = circleMemberRepository.findByCircle_IdInOrderByJoinedAtAsc(circleIds);
        if (memberships.isEmpty()) {
            return List.of();
        }

        Set<UUID> memberIds = memberships.stream()
                .map(CircleMember::getAccountHolderId)
                .collect(java.util.stream.Collectors.toSet());
        Map<UUID, AccountHolder> accountHoldersById = accountHolderRepository.findAllById(memberIds).stream()
                .collect(java.util.stream.Collectors.toMap(AccountHolder::getId, accountHolder -> accountHolder));

        LinkedHashMap<UUID, DirectoryMemberAccumulator> membersById = new LinkedHashMap<>();
        for (CircleMember membership : memberships) {
            AccountHolder accountHolder = accountHoldersById.get(membership.getAccountHolderId());
            if (accountHolder == null) {
                continue;
            }
            String name = displayName(accountHolder);
            String activity = normalizeOptionalText(membership.getBusinessActivity());
            if (!contains(name, normalizedQuery) && !contains(activity, normalizedQuery)) {
                continue;
            }
            membersById.computeIfAbsent(membership.getAccountHolderId(), ignored ->
                    new DirectoryMemberAccumulator(membership.getAccountHolderId(), name))
                    .mergeActivity(activity);
        }

        return membersById.values().stream()
                .sorted(Comparator.comparing(DirectoryMemberAccumulator::name))
                .map(member -> new DirectoryMemberResultResponse(
                        member.accountHolderId(),
                        member.name(),
                        member.activity(),
                        null
                ))
                .toList();
    }

    private EnumSet<SearchScope> parseScopes(String scopes) {
        if (scopes == null || scopes.isBlank()) {
            return EnumSet.allOf(SearchScope.class);
        }
        EnumSet<SearchScope> parsed = EnumSet.noneOf(SearchScope.class);
        for (String rawScope : scopes.split(",")) {
            if (rawScope == null || rawScope.isBlank()) {
                continue;
            }
            String normalizedScope = rawScope.trim().toUpperCase(Locale.ROOT);
            try {
                parsed.add(SearchScope.valueOf(normalizedScope));
            } catch (IllegalArgumentException exception) {
                throw new BusinessException("Unsupported directory search scope: " + rawScope.trim());
            }
        }
        return parsed.isEmpty() ? EnumSet.allOf(SearchScope.class) : parsed;
    }

    private String normalizeQuery(String query) {
        if (query == null) {
            return null;
        }
        String normalized = query.trim();
        return normalized.isBlank() ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private boolean contains(String value, String normalizedQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    private String displayName(AccountHolder accountHolder) {
        String first = safeText(accountHolder.getFirstName());
        String last = safeText(accountHolder.getLastName());
        String combined = (first + " " + last).trim();
        return combined.isEmpty() ? "Unknown" : combined;
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeOptionalText(String value) {
        String normalized = safeText(value);
        return normalized.isBlank() ? null : normalized;
    }

    private enum SearchScope {
        CIRCLES,
        MEMBERS
    }

    private static final class DirectoryMemberAccumulator {
        private final UUID accountHolderId;
        private final String name;
        private String activity;

        private DirectoryMemberAccumulator(UUID accountHolderId, String name) {
            this.accountHolderId = accountHolderId;
            this.name = name == null || name.isBlank() ? "Unknown" : name.trim();
        }

        private void mergeActivity(String candidateActivity) {
            if (activity == null && candidateActivity != null && !candidateActivity.isBlank()) {
                activity = candidateActivity.trim();
            }
        }

        private UUID accountHolderId() {
            return accountHolderId;
        }

        private String name() {
            return name;
        }

        private String activity() {
            return activity;
        }
    }
}
