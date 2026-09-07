package com.fitmate.backend.member.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import com.fitmate.backend.auth.api.AvailableTimeRequest
import com.fitmate.backend.auth.api.GoalSelectionRequest
import com.fitmate.backend.auth.api.SportSelectionRequest
import com.fitmate.backend.auth.domain.AccountStatus
import com.fitmate.backend.member.domain.Gender
import com.fitmate.backend.member.domain.ProfileVisibility
import com.fitmate.backend.member.domain.WeightUnit
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class UpdateProfileRequest(
    @param:JsonProperty(required = true) @param:JsonSetter(nulls = Nulls.FAIL) @field:Min(0) val version: Long,
    @field:Size(min = 1, max = 30) val nickname: String? = null,
    @field:Size(min = 1, max = 50) val displayName: String? = null,
    val birthDate: LocalDate? = null,
    val gender: Gender? = null,
    val visibility: ProfileVisibility? = null,
    @field:Size(max = 500) val bio: String? = null,
    val profileImageMediaId: UUID? = null,
    val weightUnit: WeightUnit? = null,
    val timezoneId: String? = null,
    val activityRegionIds: List<UUID>? = null,
    val facilityNames: List<String>? = null,
    @field:Valid val availableTimes: List<AvailableTimeRequest>? = null,
    @field:Valid val sports: List<SportSelectionRequest>? = null,
    @field:Valid @field:Size(max = 20) val customSports: List<CustomSportDto>? = null,
    @field:Valid val goals: List<GoalSelectionRequest>? = null,
)

data class ProfileResponse(
    val nickname: String,
    val displayName: String,
    val birthDate: LocalDate?,
    val gender: Gender?,
    val visibility: ProfileVisibility,
    val bio: String?,
    val profileImageUrl: String?,
    val weightUnit: WeightUnit,
    val timezoneId: String,
    val version: Long,
)

data class ProfilePlaceResponse(val id: UUID, val name: String, val primary: Boolean)
data class ProfileConsentResponse(val termsDocumentId: UUID, val consentedAt: Instant, val withdrawnAt: Instant?)

data class MyProfileResponse(
    val id: UUID,
    val email: String,
    val accountStatus: AccountStatus,
    val profile: ProfileResponse,
    val activityRegions: List<ProfilePlaceResponse>,
    val facilities: List<ProfilePlaceResponse>,
    val availableTimes: List<AvailableTimeRequest>,
    val sports: List<SportSelectionRequest>,
    val customSports: List<CustomSportDto>,
    val goals: List<GoalSelectionRequest>,
    val consents: List<ProfileConsentResponse>,
)
