package com.ccapp.mlm.repository;

import com.ccapp.mlm.domain.entity.MlmMemberLink;
import com.ccapp.mlm.repository.projection.MlmTreeMemberProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface MlmMemberLinkRepository extends JpaRepository<MlmMemberLink, UUID> {

    @Query(value = """
            with recursive sponsor_tree as (
                select links.account_holder_id as member_id,
                       links.sponsor_account_holder_id as parent_id,
                       1 as generation,
                       links.created_at as linked_at
                from mlm_member_links links
                where links.sponsor_account_holder_id = :accountHolderId
                union all
                select child.account_holder_id as member_id,
                       child.sponsor_account_holder_id as parent_id,
                       sponsor_tree.generation + 1 as generation,
                       child.created_at as linked_at
                from mlm_member_links child
                join sponsor_tree on child.sponsor_account_holder_id = sponsor_tree.member_id
            )
            select cast(member_id as varchar) as memberId,
                   cast(parent_id as varchar) as parentId,
                   generation as generation,
                   linked_at as linkedAt
            from sponsor_tree
            order by generation asc, linked_at asc, member_id asc
            """, nativeQuery = true)
    List<MlmTreeMemberProjection> findSponsorTree(UUID accountHolderId);

    @Query(value = """
            with recursive placement_tree as (
                select links.account_holder_id as member_id,
                       links.placement_account_holder_id as parent_id,
                       1 as generation,
                       links.created_at as linked_at
                from mlm_member_links links
                where links.placement_account_holder_id = :accountHolderId
                  and links.account_holder_id <> :accountHolderId
                union all
                select child.account_holder_id as member_id,
                       child.placement_account_holder_id as parent_id,
                       placement_tree.generation + 1 as generation,
                       child.created_at as linked_at
                from mlm_member_links child
                join placement_tree on child.placement_account_holder_id = placement_tree.member_id
                where child.account_holder_id <> child.placement_account_holder_id
            )
            select cast(member_id as varchar) as memberId,
                   cast(parent_id as varchar) as parentId,
                   generation as generation,
                   linked_at as linkedAt
            from placement_tree
            order by generation asc, linked_at asc, member_id asc
            """, nativeQuery = true)
    List<MlmTreeMemberProjection> findPlacementTree(UUID accountHolderId);
}
