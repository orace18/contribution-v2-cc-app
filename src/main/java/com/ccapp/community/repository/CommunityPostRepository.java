package com.ccapp.community.repository;

import com.ccapp.community.domain.entity.CommunityPost;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @deprecated Legacy repository for the old community feed tables. Replaced by {@code com.ccapp.prayer.repository.PrayerPostRepository}.
 */
@Deprecated(since = "2026-04", forRemoval = false)
public interface CommunityPostRepository extends JpaRepository<CommunityPost, UUID> {

    @Query("""
            select p from CommunityPost p
            where p.active = true and p.featured = true
            order by coalesce(p.featuredPriority, 2147483647) asc, p.featuredAt desc, p.createdAt desc
            """)
    List<CommunityPost> findFeaturedForFeed();

    @Query("""
            select p from CommunityPost p
            where p.active = true and p.featured = true
            order by coalesce(p.featuredPriority, 2147483647) asc, p.featuredAt desc, p.createdAt desc
            """)
    List<CommunityPost> findFeaturedForFeed(Pageable pageable);

    List<CommunityPost> findByActiveTrueAndFeaturedFalseOrderByCreatedAtDesc();

    long countByActiveTrueAndFeaturedTrue();

    Optional<CommunityPost> findByIdAndActiveTrue(UUID id);
}
