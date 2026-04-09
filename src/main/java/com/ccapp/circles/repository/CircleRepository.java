package com.ccapp.circles.repository;

import com.ccapp.circles.domain.entity.Circle;
import com.ccapp.circles.domain.enums.CircleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CircleRepository extends JpaRepository<Circle, UUID> {

    Optional<Circle> findByIdAndStatus(UUID id, CircleStatus status);

    Optional<Circle> findByIdAndCountryCode(UUID id, String countryCode);

    List<Circle> findAllByOrderByCreatedAtDesc();

    long countByStatus(CircleStatus status);

    @Query("""
            select (count(c) > 0) from Circle c
            where c.status = com.ccapp.circles.domain.enums.CircleStatus.ACTIVE
              and exists (
                  select 1 from CircleMember cm
                  where cm.circle = c
                    and cm.accountHolderId = :accountHolderId
              )
            """)
    boolean existsActiveCircleForUser(UUID accountHolderId);

    @Query("""
            select c from Circle c
            where c.status = com.ccapp.circles.domain.enums.CircleStatus.ACTIVE
              and exists (
                  select 1 from CircleMember cm
                  where cm.circle = c
                    and cm.accountHolderId = :accountHolderId
              )
            order by c.createdAt desc
            """)
    List<Circle> findActiveCirclesForUser(UUID accountHolderId);

    @Query(value = """
            SELECT CAST(c.id AS VARCHAR) AS circleId,
                   c.name AS name,
                   c.status AS status,
                   COALESCE(cp.progress_percentage, 0) AS progress,
                   (
                     SELECT COUNT(*)
                     FROM circle_members cm2
                     WHERE cm2.circle_id = c.id
                   ) AS membersCount,
                   cm.role AS role,
                   COALESCE((
                       SELECT COUNT(*)
                       FROM chat_messages m
                       WHERE m.recipient_account_holder_id = :accountHolderId
                         AND m.message_status IN ('SENT', 'DELIVERED')
                         AND EXISTS (
                             SELECT 1
                             FROM circle_members sender_cm
                             WHERE sender_cm.circle_id = c.id
                               AND sender_cm.account_holder_id = m.sender_account_holder_id
                         )
                   ), 0) AS unreadMessages,
                   COALESCE((
                       SELECT MAX(m2.created_at)
                       FROM chat_messages m2
                       WHERE EXISTS (
                           SELECT 1
                           FROM circle_members p1
                           WHERE p1.circle_id = c.id
                             AND p1.account_holder_id = m2.sender_account_holder_id
                       )
                         AND EXISTS (
                           SELECT 1
                           FROM circle_members p2
                           WHERE p2.circle_id = c.id
                             AND p2.account_holder_id = m2.recipient_account_holder_id
                       )
                   ), c.updated_at, c.created_at) AS lastActivity
            FROM circles c
            JOIN circle_members cm
              ON cm.circle_id = c.id
             AND cm.account_holder_id = :accountHolderId
            LEFT JOIN circle_progress cp
              ON cp.circle_id = c.id
            WHERE (:status IS NULL OR c.status = :status)
              AND (:search IS NULL OR :search = '' OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY lastActivity DESC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM circles c
                    JOIN circle_members cm
                      ON cm.circle_id = c.id
                     AND cm.account_holder_id = :accountHolderId
                    WHERE (:status IS NULL OR c.status = :status)
                      AND (:search IS NULL OR :search = '' OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')))
                    """,
            nativeQuery = true)
    Page<CircleListProjection> findCircleSummariesForAccountHolder(@Param("accountHolderId") UUID accountHolderId,
                                                                   @Param("status") String status,
                                                                   @Param("search") String search,
                                                                   Pageable pageable);

    interface CircleListProjection {
        String getCircleId();

        String getName();

        String getStatus();

        BigDecimal getProgress();

        long getMembersCount();

        String getRole();

        long getUnreadMessages();

        OffsetDateTime getLastActivity();
    }
}
