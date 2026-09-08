package com.fitmate.backend.mate.domain

import com.fitmate.backend.common.persistence.BaseTimeEntity
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import com.fitmate.backend.fitness.domain.Sport
import com.fitmate.backend.member.domain.Region
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class MatePostStatus { RECRUITING, FULL, CLOSED, CANCELED }
enum class MateParticipationStatus { PENDING, ACCEPTED, REJECTED, CANCELED, WITHDRAWN }
enum class MateParticipantRole { HOST, MEMBER }
enum class MateDifficulty { BEGINNER, INTERMEDIATE, ADVANCED }

@Entity @Table(name = "fitness_mate_posts")
class MatePost(
    @Id @Convert(converter = UuidBinaryConverter::class) @Column(columnDefinition = "BINARY(16)") var id: UUID? = null,
    @Column(name = "author_id", nullable = false, columnDefinition = "BINARY(16)") var authorId: UUID? = null,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "sport_id") var sport: Sport? = null,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "region_id") var region: Region? = null,
    @Column(nullable = false, length = 100) var title: String = "", @Column(nullable = false, columnDefinition = "TEXT") var body: String = "",
    @Column(name = "scheduled_start_at", nullable = false) var scheduledStartAt: Instant = Instant.EPOCH,
    @Column(name = "duration_seconds") var durationSeconds: Int? = null, @Column(name = "location_summary", nullable = false) var locationSummary: String = "",
    @Column(nullable = false) var capacity: Int = 2, @Column(name = "accepted_count", nullable = false) var acceptedCount: Int = 1,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var difficulty: MateDifficulty = MateDifficulty.BEGINNER,
    @Column(name = "estimated_cost_krw") var estimatedCostKrw: Int? = null,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: MatePostStatus = MatePostStatus.RECRUITING,
    @Version @Column(nullable = false) var version: Long = 0, @Column(name = "closed_at") var closedAt: Instant? = null, @Column(name = "canceled_at") var canceledAt: Instant? = null,
) : BaseTimeEntity()

@Entity @Table(name = "fitness_mate_participations")
class MateParticipation(
    @Id @Convert(converter = UuidBinaryConverter::class) @Column(columnDefinition = "BINARY(16)") var id: UUID? = null,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "mate_post_id") var post: MatePost? = null,
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)") var userId: UUID? = null,
    @Enumerated(EnumType.STRING) @Column(name = "participant_role", nullable = false) var role: MateParticipantRole = MateParticipantRole.MEMBER,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: MateParticipationStatus = MateParticipationStatus.PENDING,
    @Column(name = "status_reason") var statusReason: String? = null, @Column(name = "applied_at", nullable = false) var appliedAt: Instant = Instant.EPOCH,
    @Column(name = "responded_at") var respondedAt: Instant? = null, @Column(name = "canceled_at") var canceledAt: Instant? = null,
    @Version @Column(nullable = false) var version: Long = 0,
) : BaseTimeEntity()
