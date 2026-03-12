package com.ccapp.mlm.repository;

import com.ccapp.mlm.domain.entity.MlmRelationship;
import com.ccapp.mlm.domain.entity.MlmRelationshipId;
import com.ccapp.mlm.repository.projection.MlmHierarchyMemberProjection;
import com.ccapp.mlm.repository.projection.MlmLevelBreakdownProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MlmRelationshipRepository extends JpaRepository<MlmRelationship, MlmRelationshipId> {

    boolean existsByDescendantId(UUID descendantId);

    boolean existsByAncestorIdAndDescendantId(UUID ancestorId, UUID descendantId);

    long countByAncestorIdAndLevel(UUID ancestorId, Integer level);

    long countByDescendantIdAndLevelGreaterThan(UUID descendantId, Integer level);

    @Query("""
            select r.descendantId
            from MlmRelationship r
            where r.ancestorId = :ancestorId
              and r.level = 1
            order by r.createdAt asc, r.descendantId asc
            """)
    List<UUID> findDirectChildrenOrdered(UUID ancestorId);

    @Query("""
            select r from MlmRelationship r
            where r.descendantId = :descendantId
            order by r.level asc
            """)
    List<MlmRelationship> findAncestors(UUID descendantId);

    @Query("""
            select count(r) from MlmRelationship r
            where r.ancestorId = :ancestorId
              and r.level > 0
            """)
    long countTeamSize(UUID ancestorId);

    @Query(value = """
            select cast(r.ancestor_id as varchar) as memberId,
                   r.level as level,
                   ah.first_name as firstName,
                   ah.last_name as lastName,
                   r.created_at as linkedAt
            from mlm_relationships r
            join account_holders ah on ah.id = r.ancestor_id
            where r.descendant_id = :accountHolderId
              and r.level > 0
            order by r.level asc, r.created_at asc
            """, nativeQuery = true)
    List<MlmHierarchyMemberProjection> findUpline(UUID accountHolderId);

    @Query(value = """
            select cast(r.descendant_id as varchar) as memberId,
                   r.level as level,
                   ah.first_name as firstName,
                   ah.last_name as lastName,
                   r.created_at as linkedAt
            from mlm_relationships r
            join account_holders ah on ah.id = r.descendant_id
            where r.ancestor_id = :accountHolderId
              and r.level > 0
            order by r.level asc, r.created_at asc
            """, nativeQuery = true)
    List<MlmHierarchyMemberProjection> findDownline(UUID accountHolderId);

    @Query(value = """
            select cast(r.descendant_id as varchar) as memberId,
                   r.level as level,
                   ah.first_name as firstName,
                   ah.last_name as lastName,
                   r.created_at as linkedAt
            from mlm_relationships r
            join account_holders ah on ah.id = r.descendant_id
            where r.ancestor_id = :accountHolderId
              and r.level = :level
            order by r.created_at asc
            """, nativeQuery = true)
    List<MlmHierarchyMemberProjection> findMembersAtLevel(UUID accountHolderId, int level);

    @Query("""
            select r.level as level,
                   count(r.descendantId) as members
            from MlmRelationship r
            where r.ancestorId = :accountHolderId
              and r.level > 0
            group by r.level
            order by r.level asc
            """)
    List<MlmLevelBreakdownProjection> countLevelBreakdown(UUID accountHolderId);

    @Query(value = """
            select cast(candidates.descendant_id as varchar)
            from mlm_relationships candidates
            left join mlm_relationships direct_children
                on direct_children.ancestor_id = candidates.descendant_id
               and direct_children.level = 1
            where candidates.ancestor_id = :sponsorId
            group by candidates.descendant_id, candidates.level, candidates.created_at
            having count(direct_children.descendant_id) < :maxDirectChildren
            order by candidates.level asc, candidates.created_at asc
            limit 1
            """, nativeQuery = true)
    Optional<String> findPlacementParentCandidate(UUID sponsorId, int maxDirectChildren);
}
