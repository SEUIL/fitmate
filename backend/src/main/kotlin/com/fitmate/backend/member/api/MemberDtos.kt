package com.fitmate.backend.member.api

import com.fitmate.backend.auth.api.AvailableTimeRequest
import com.fitmate.backend.auth.api.GoalSelectionRequest
import com.fitmate.backend.auth.api.SportSelectionRequest
import com.fitmate.backend.member.domain.Gender
import com.fitmate.backend.member.domain.ProfileVisibility
import com.fitmate.backend.member.domain.WeightUnit
import com.fitmate.backend.terms.domain.TermsType
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class TermsResponse(val id: UUID, val type: TermsType, val version: String, val contentUri: String, val effectiveAt: Instant, val required: Boolean)
data class RegionResponse(val id: UUID, val parentId: UUID?, val name: String, val level: String, val code: String)
data class FacilityResponse(val id: UUID, val regionId: UUID, val name: String, val addressSummary: String?)
data class ActivityRegionResponse(val id: UUID, val name: String, val primary: Boolean)
data class UserFacilityResponse(val id: UUID, val regionId: UUID, val name: String, val primary: Boolean)
data class AvailableTimeResponse(val id: UUID, val weekdayLocal: Int, val startLocalTime: LocalTime, val endLocalTime: LocalTime, val timezoneId: String)
data class UserSportResponse(val sportCode: String, val skillLevel: String)
data class UserGoalResponse(val goalCode: String, val primary: Boolean)
data class ConsentResponse(val termsDocumentId: UUID, val type: TermsType, val version: String, val consentedAt: Instant, val withdrawnAt: Instant?)
data class MemberProfileResponse(val nickname: String, val displayName: String, val birthDate: LocalDate?, val gender: Gender?, val visibility: ProfileVisibility, val bio: String?, val weightUnit: WeightUnit, val timezoneId: String, val version: Long)
data class MeResponse(val id: UUID, val email: String, val accountStatus: String, val profile: MemberProfileResponse, val activityRegions: List<ActivityRegionResponse>, val facilities: List<UserFacilityResponse>, val availableTimes: List<AvailableTimeResponse>, val sports: List<UserSportResponse>, val goals: List<UserGoalResponse>, val consents: List<ConsentResponse>)

data class ProfileUpdateRequest(
    @field:Size(max = 30) val nickname: String? = null,
    @field:Size(max = 50) val displayName: String? = null,
    val birthDate: LocalDate? = null,
    val gender: Gender? = null,
    val visibility: ProfileVisibility? = null,
    @field:Size(max = 500) val bio: String? = null,
    val weightUnit: WeightUnit? = null,
    val timezoneId: String? = null,
    val activityRegionIds: List<UUID>? = null,
    val facilityNames: List<@NotBlank @Size(max = 150) String>? = null,
    val availableTimes: List<@Valid AvailableTimeRequest>? = null,
    val sports: List<@Valid SportSelectionRequest>? = null,
    val goals: List<@Valid GoalSelectionRequest>? = null,
    @field:NotNull val version: Long,
)

data class ChangePasswordRequest(@field:NotBlank val currentPassword: String, @field:NotBlank val newPassword: String)
data class WithdrawRequest(@field:NotBlank val password: String)
data class NotificationSettingsResponse(val inAppEnabled: Boolean, val dmEnabled: Boolean, val mateEnabled: Boolean, val communityEnabled: Boolean, val version: Long)
data class NotificationSettingsUpdateRequest(@field:NotNull val inAppEnabled: Boolean, @field:NotNull val dmEnabled: Boolean, @field:NotNull val mateEnabled: Boolean, @field:NotNull val communityEnabled: Boolean, @field:NotNull val version: Long)
