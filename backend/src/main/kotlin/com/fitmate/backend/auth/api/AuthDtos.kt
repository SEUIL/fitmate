package com.fitmate.backend.auth.api

import com.fitmate.backend.auth.domain.AccountStatus
import com.fitmate.backend.member.domain.Gender
import com.fitmate.backend.member.domain.GoalCode
import com.fitmate.backend.member.domain.ProfileVisibility
import com.fitmate.backend.member.domain.SkillLevel
import com.fitmate.backend.member.domain.WeightUnit
import com.fitmate.backend.member.api.CustomSportDto
import com.fitmate.backend.fitness.domain.SportCode
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class AvailabilityResponse(val value: String, val available: Boolean)

data class SignUpRequest(
    @field:Email @field:NotBlank @field:Size(max = 320) val email: String,
    @field:NotBlank val password: String,
    val displayName: String? = null,
    @field:NotBlank @field:Size(max = 30) val nickname: String,
    val birthDate: LocalDate? = null,
    val gender: Gender? = null,
    val visibility: ProfileVisibility = ProfileVisibility.PUBLIC,
    @field:Size(max = 500) val bio: String? = null,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val timezoneId: String = "Asia/Seoul",
    val activityRegionIds: List<UUID> = emptyList(),
    val facilityNames: List<@NotBlank @Size(max = 150) String> = emptyList(),
    val availableTimes: List<@Valid AvailableTimeRequest> = emptyList(),
    val sports: List<@Valid SportSelectionRequest> = emptyList(),
    @field:NotEmpty val goals: List<@Valid GoalSelectionRequest>,
    @field:NotEmpty val consentDocumentIds: List<UUID>,
    @field:Size(max = 128) val deviceId: String? = null,
    @field:Valid @field:Size(max = 20) val customSports: List<CustomSportDto> = emptyList(),
)

data class AvailableTimeRequest(
    val weekdayLocal: Int,
    val startLocalTime: LocalTime,
    val endLocalTime: LocalTime,
    val timezoneId: String = "Asia/Seoul",
)

data class SportSelectionRequest(val sportCode: SportCode, val skillLevel: SkillLevel)
data class GoalSelectionRequest(val goalCode: GoalCode, val primary: Boolean)

data class LoginRequest(@field:Email @field:NotBlank val email: String, @field:NotBlank val password: String, @field:Size(max = 128) val deviceId: String? = null)
data class RefreshRequest(@field:NotBlank val refreshToken: String, @field:Size(max = 128) val deviceId: String? = null)
data class LogoutRequest(@field:NotBlank val refreshToken: String)

data class TokenResponse(val accessToken: String, val accessTokenExpiresInSeconds: Long, val refreshToken: String)
data class SignUpProfileResponse(val nickname: String, val displayName: String, val visibility: ProfileVisibility, val weightUnit: WeightUnit, val timezoneId: String, val version: Long)
data class SignUpUserResponse(val id: UUID, val email: String, val accountStatus: AccountStatus, val profile: SignUpProfileResponse, val customSports: List<CustomSportDto> = emptyList())
data class SignUpResponse(val user: SignUpUserResponse, val tokens: TokenResponse)
data class LoginResponse(val userId: UUID, val accountStatus: AccountStatus, val tokens: TokenResponse)
