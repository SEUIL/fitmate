package com.fitmate.backend.mate.repository

import com.fitmate.backend.mate.domain.*
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.*
import org.springframework.data.repository.query.Param
import org.springframework.data.domain.Pageable
import java.time.Instant
import java.util.UUID

interface MatePostRepository : JpaRepository<MatePost, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from MatePost p where p.id = :id") fun findLocked(@Param("id") id: UUID): MatePost?
    @EntityGraph(attributePaths = ["sport", "region"])
    @Query("""select p from MatePost p
        where (:sportCode is null or p.sport.code = :sportCode)
          and (:regionId is null or p.region.id = :regionId)
          and (:fromAt is null or p.scheduledStartAt >= :fromAt)
          and (:toAt is null or p.scheduledStartAt <= :toAt)
          and (:difficulty is null or p.difficulty = :difficulty)
          and (:status is null or p.status = :status)
          and (:cursorAt is null or p.createdAt < :cursorAt or (p.createdAt = :cursorAt and p.id < :cursorId))
        order by p.createdAt desc, p.id desc""")
    fun findPage(
        @Param("sportCode") sportCode: String?, @Param("regionId") regionId: UUID?,
        @Param("fromAt") fromAt: Instant?, @Param("toAt") toAt: Instant?,
        @Param("difficulty") difficulty: MateDifficulty?, @Param("status") status: MatePostStatus?,
        @Param("cursorAt") cursorAt: Instant?, @Param("cursorId") cursorId: UUID?, pageable: Pageable,
    ): List<MatePost>
}
interface MateParticipationRepository : JpaRepository<MateParticipation, UUID> {
    fun findByPostIdAndUserId(postId: UUID, userId: UUID): MateParticipation?
    fun findAllByPostIdAndStatus(postId: UUID, status: MateParticipationStatus): List<MateParticipation>
    fun findAllByPostId(postId: UUID): List<MateParticipation>
    @Query("""select a from MateParticipation a
        where a.post.id = :postId and (:status is null or a.status = :status)
          and (:cursorAt is null or a.createdAt < :cursorAt or (a.createdAt = :cursorAt and a.id < :cursorId))
        order by a.createdAt desc, a.id desc""")
    fun findPageByPostId(
        @Param("postId") postId: UUID, @Param("status") status: MateParticipationStatus?,
        @Param("cursorAt") cursorAt: Instant?, @Param("cursorId") cursorId: UUID?, pageable: Pageable,
    ): List<MateParticipation>
}
