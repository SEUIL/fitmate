package com.fitmate.backend.routine.domain

import com.fitmate.backend.common.persistence.BaseTimeEntity
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import com.fitmate.backend.fitness.domain.Sport
import com.fitmate.backend.member.domain.GoalCode
import com.fitmate.backend.member.domain.SkillLevel
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.util.UUID

@Entity
@Table(name = "fitness_routines")
class Routine(
    @Id
    @Convert(converter = UuidBinaryConverter::class)
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    var id: UUID? = null,
    @Column(name = "owner_user_id", columnDefinition = "BINARY(16)")
    var ownerUserId: UUID? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sport_id", nullable = false)
    var sport: Sport? = null,
    @Column(name = "name", nullable = false, length = 100)
    var name: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    var sourceType: RoutineSourceType = RoutineSourceType.SYSTEM_TEMPLATE,
    @Enumerated(EnumType.STRING)
    @Column(name = "target_goal_code", length = 32)
    var targetGoalCode: GoalCode? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "target_level", length = 32)
    var targetLevel: SkillLevel? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false, length = 32)
    var difficulty: DifficultyLevel = DifficultyLevel.BEGINNER,
    @Column(name = "split_count", nullable = false)
    var splitCount: Int = 1,
    @Column(name = "is_template", nullable = false)
    var isTemplate: Boolean = false,
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0,
    @Column(name = "recommendation_json", columnDefinition = "LONGTEXT")
    var recommendationJson: String? = null,
) : BaseTimeEntity()
