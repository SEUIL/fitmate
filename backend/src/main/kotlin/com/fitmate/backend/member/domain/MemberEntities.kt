package com.fitmate.backend.member.domain

import com.fitmate.backend.common.persistence.BaseTimeEntity
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.io.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

enum class Gender { FEMALE, MALE, NONE }
enum class ProfileVisibility { PUBLIC, PRIVATE }
enum class WeightUnit { KG, LB }
enum class SkillLevel { BEGINNER, UNDER_6_MONTHS, SIX_MONTHS_TO_ONE_YEAR, ONE_TO_THREE_YEARS, OVER_THREE_YEARS }
enum class GoalCode { DIET, MUSCLE_GAIN, FITNESS, RECORD_IMPROVEMENT, HABIT }

@Entity
@Table(name = "core_user_profiles")
class UserProfile(
    @Id @Convert(converter = UuidBinaryConverter::class) @Column(name = "user_id", columnDefinition = "BINARY(16)") var userId: UUID? = null,
    @Column(name = "nickname", nullable = false, length = 30) var nickname: String = "",
    @Column(name = "nickname_normalized", nullable = false, length = 30) var nicknameNormalized: String = "",
    @Column(name = "deleted_at") var deletedAt: Instant? = null,
    @Column(name = "display_name", nullable = false, length = 50) var displayName: String = "",
    @Column(name = "birth_date") var birthDate: LocalDate? = null,
    @Enumerated(EnumType.STRING) @Column(name = "gender", length = 16) var gender: Gender? = null,
    @Enumerated(EnumType.STRING) @Column(name = "visibility", nullable = false, length = 16) var visibility: ProfileVisibility = ProfileVisibility.PUBLIC,
    @Column(name = "bio", length = 500) var bio: String? = null,
    @Enumerated(EnumType.STRING) @Column(name = "weight_unit", nullable = false, length = 4) var weightUnit: WeightUnit = WeightUnit.KG,
    @Column(name = "timezone_id", nullable = false, length = 64) var timezoneId: String = "Asia/Seoul",
    @Column(name = "onboarding_completed_at") var onboardingCompletedAt: Instant? = null,
    @Version @Column(name = "version", nullable = false) var version: Long = 0,
) : BaseTimeEntity()

@Entity
@Table(name = "core_regions")
class Region(
    @Id @Convert(converter = UuidBinaryConverter::class) @Column(name = "id", columnDefinition = "BINARY(16)") var id: UUID? = null,
    @Convert(converter = UuidBinaryConverter::class) @Column(name = "parent_id", columnDefinition = "BINARY(16)") var parentId: UUID? = null,
    @Column(name = "name", nullable = false, length = 100) var name: String = "",
    @Column(name = "level", nullable = false) var level: Int = 1,
    @Column(name = "code", nullable = false, length = 32) var code: String = "",
    @Column(name = "is_active", nullable = false) var isActive: Boolean = true,
) : BaseTimeEntity()

@Entity
@Table(name = "core_facilities")
class Facility(
    @Id @Convert(converter = UuidBinaryConverter::class) @Column(name = "id", columnDefinition = "BINARY(16)") var id: UUID? = null,
    @Column(name = "name", nullable = false, length = 150) var name: String = "",
    @Column(name = "normalized_name", nullable = false, length = 150) var normalizedName: String = "",
    @Column(name = "region_id", nullable = false, columnDefinition = "BINARY(16)") var regionId: UUID? = null,
    @Column(name = "address_summary", length = 255) var addressSummary: String? = null,
    @Column(name = "is_active", nullable = false) var isActive: Boolean = true,
) : BaseTimeEntity()

@Embeddable
data class UserRegionId(
    @Column(name = "user_id", columnDefinition = "BINARY(16)") var userId: UUID? = null,
    @Column(name = "region_id", columnDefinition = "BINARY(16)") var regionId: UUID? = null,
) : Serializable

@Entity
@Table(name = "core_user_activity_regions")
class UserActivityRegion(
    @EmbeddedId var id: UserRegionId = UserRegionId(),
    @Column(name = "is_primary", nullable = false) var isPrimary: Boolean = false,
) : BaseTimeEntity()

@Embeddable
data class UserFacilityId(
    @Column(name = "user_id", columnDefinition = "BINARY(16)") var userId: UUID? = null,
    @Column(name = "facility_id", columnDefinition = "BINARY(16)") var facilityId: UUID? = null,
) : Serializable

@Entity
@Table(name = "core_user_facilities")
class UserFacility(
    @EmbeddedId var id: UserFacilityId = UserFacilityId(),
    @Column(name = "is_primary", nullable = false) var isPrimary: Boolean = false,
) : BaseTimeEntity()

@Entity
@Table(name = "core_user_available_times")
class UserAvailableTime(
    @Id @Convert(converter = UuidBinaryConverter::class) @Column(name = "id", columnDefinition = "BINARY(16)") var id: UUID? = null,
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)") var userId: UUID? = null,
    @Column(name = "weekday_local", nullable = false) var weekdayLocal: Int = 0,
    @Column(name = "start_local_time", nullable = false) var startLocalTime: LocalTime = LocalTime.MIDNIGHT,
    @Column(name = "end_local_time", nullable = false) var endLocalTime: LocalTime = LocalTime.NOON,
    @Column(name = "timezone_id", nullable = false, length = 64) var timezoneId: String = "Asia/Seoul",
) : BaseTimeEntity()

@Embeddable
data class UserSportId(
    @Column(name = "user_id", columnDefinition = "BINARY(16)") var userId: UUID? = null,
    @Column(name = "sport_id", columnDefinition = "BINARY(16)") var sportId: UUID? = null,
) : Serializable

@Entity
@Table(name = "core_user_sports")
class UserSport(
    @EmbeddedId var id: UserSportId = UserSportId(),
    @Enumerated(EnumType.STRING) @Column(name = "skill_level", nullable = false, length = 32) var skillLevel: SkillLevel = SkillLevel.BEGINNER,
) : BaseTimeEntity()

@Embeddable
data class UserGoalId(
    @Column(name = "user_id", columnDefinition = "BINARY(16)") var userId: UUID? = null,
    @Enumerated(EnumType.STRING) @Column(name = "goal_code", length = 32) var goalCode: GoalCode = GoalCode.HABIT,
) : Serializable

@Entity
@Table(name = "core_user_goals")
class UserGoal(
    @EmbeddedId var id: UserGoalId = UserGoalId(),
    @Column(name = "is_primary", nullable = false) var isPrimary: Boolean = false,
) : BaseTimeEntity()

@Entity
@Table(name = "core_notification_settings")
class NotificationSetting(
    @Id @Convert(converter = UuidBinaryConverter::class) @Column(name = "user_id", columnDefinition = "BINARY(16)") var userId: UUID? = null,
    @Column(name = "in_app_enabled", nullable = false) var inAppEnabled: Boolean = true,
    @Column(name = "dm_enabled", nullable = false) var dmEnabled: Boolean = true,
    @Column(name = "mate_enabled", nullable = false) var mateEnabled: Boolean = true,
    @Column(name = "community_enabled", nullable = false) var communityEnabled: Boolean = true,
    @Version @Column(name = "version", nullable = false) var version: Long = 0,
) : BaseTimeEntity()
