package com.fitmate.backend.member.application

import com.fitmate.backend.auth.api.AvailableTimeRequest
import com.fitmate.backend.auth.api.GoalSelectionRequest
import com.fitmate.backend.auth.api.SportSelectionRequest
import com.fitmate.backend.auth.repository.UserRepository
import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import com.fitmate.backend.fitness.domain.SportCode
import com.fitmate.backend.fitness.repository.SportRepository
import com.fitmate.backend.member.api.MyProfileResponse
import com.fitmate.backend.member.api.ProfileConsentResponse
import com.fitmate.backend.member.api.ProfilePlaceResponse
import com.fitmate.backend.member.api.ProfileResponse
import com.fitmate.backend.member.api.UpdateProfileRequest
import com.fitmate.backend.member.domain.Facility
import com.fitmate.backend.member.domain.UserActivityRegion
import com.fitmate.backend.member.domain.UserAvailableTime
import com.fitmate.backend.member.domain.UserFacility
import com.fitmate.backend.member.domain.UserFacilityId
import com.fitmate.backend.member.domain.UserGoal
import com.fitmate.backend.member.domain.UserGoalId
import com.fitmate.backend.member.domain.UserRegionId
import com.fitmate.backend.member.domain.UserSport
import com.fitmate.backend.member.domain.UserSportId
import com.fitmate.backend.member.repository.FacilityRepository
import com.fitmate.backend.member.repository.RegionRepository
import com.fitmate.backend.member.repository.UserActivityRegionRepository
import com.fitmate.backend.member.repository.UserAvailableTimeRepository
import com.fitmate.backend.member.repository.UserFacilityRepository
import com.fitmate.backend.member.repository.UserGoalRepository
import com.fitmate.backend.member.repository.UserProfileRepository
import com.fitmate.backend.member.repository.UserSportRepository
import com.fitmate.backend.media.application.MediaAssetService
import com.fitmate.backend.media.domain.MediaPurpose
import com.fitmate.backend.terms.repository.UserConsentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.ZoneId
import java.util.UUID

@Service
class ProfileService(
    private val userRepository: UserRepository,
    private val profileRepository: UserProfileRepository,
    private val regionRepository: RegionRepository,
    private val facilityRepository: FacilityRepository,
    private val activityRegionRepository: UserActivityRegionRepository,
    private val userFacilityRepository: UserFacilityRepository,
    private val availableTimeRepository: UserAvailableTimeRepository,
    private val userSportRepository: UserSportRepository,
    private val sportRepository: SportRepository,
    private val goalRepository: UserGoalRepository,
    private val consentRepository: UserConsentRepository,
    private val customSportService: CustomSportService,
    private val mediaAssets: MediaAssetService,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun get(userId: UUID): MyProfileResponse {
        val user = userRepository.findById(userId).orElseThrow { ApiException(ApiErrorCode.RESOURCE_NOT_FOUND) }
        val profile = profileRepository.findById(userId).orElseThrow { ApiException(ApiErrorCode.RESOURCE_NOT_FOUND) }
        if (profile.deletedAt != null) throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND)
        return MyProfileResponse(
            userId, user.email, user.accountStatus,
            ProfileResponse(profile.nickname, profile.displayName, profile.birthDate, profile.gender, profile.visibility,
                profile.bio, profile.profileImageMediaId?.let { "/api/v1/users/$userId/profile-image" }, profile.weightUnit, profile.timezoneId, profile.version),
            activityRegionRepository.findAllByIdUserId(userId).map { selection ->
                val region = regionRepository.findById(requireNotNull(selection.id.regionId)).orElseThrow()
                ProfilePlaceResponse(UuidBinaryConverter.fromMysqlOrderedUuid(requireNotNull(region.id)), region.name, selection.isPrimary)
            },
            userFacilityRepository.findAllByIdUserId(userId).map { selection ->
                val facility = facilityRepository.findById(requireNotNull(selection.id.facilityId)).orElseThrow()
                ProfilePlaceResponse(UuidBinaryConverter.fromMysqlOrderedUuid(requireNotNull(facility.id)), facility.name, selection.isPrimary)
            },
            availableTimeRepository.findAllByUserIdOrderByWeekdayLocalAscStartLocalTimeAsc(userId)
                .map { AvailableTimeRequest(it.weekdayLocal, it.startLocalTime, it.endLocalTime, it.timezoneId) },
            userSportRepository.findAllByIdUserId(userId).map { selection ->
                val sport = sportRepository.findById(requireNotNull(selection.id.sportId)).orElseThrow()
                SportSelectionRequest(sport.code, selection.skillLevel)
            }.sortedBy { it.sportCode.name },
            customSportService.get(userId),
            goalRepository.findAllByIdUserId(userId).map { GoalSelectionRequest(it.id.goalCode, it.isPrimary) },
            consentRepository.findAllByUserIdOrderByConsentedAtDesc(userId).map {
                ProfileConsentResponse(UuidBinaryConverter.fromMysqlOrderedUuid(requireNotNull(it.termsDocumentId)), it.consentedAt, it.withdrawnAt)
            },
        )
    }

    @Transactional(readOnly = true)
    fun profileImageMediaId(userId: UUID): UUID? = profileRepository.findById(userId).orElseThrow { ApiException(ApiErrorCode.RESOURCE_NOT_FOUND) }
        .takeIf { it.deletedAt == null }?.profileImageMediaId

    @Transactional
    fun update(userId: UUID, request: UpdateProfileRequest): MyProfileResponse {
        val profile = profileRepository.findForUpdateByUserId(userId) ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND)
        if (request.version != profile.version) throw ApiException(ApiErrorCode.VERSION_CONFLICT)
        request.customSports?.let(customSportService::validate)
        if (request.sports != null || request.customSports != null) {
            val effectiveSports = request.sports?.isNotEmpty() ?: userSportRepository.findAllByIdUserId(userId).isNotEmpty()
            val effectiveCustomSports = request.customSports?.isNotEmpty() ?: customSportService.get(userId).isNotEmpty()
            if (!effectiveSports && !effectiveCustomSports) invalid("관심 운동을 하나 이상 선택해야 합니다.")
        }
        request.nickname?.let {
            val nickname = it.trim()
            if (nickname.isEmpty()) invalid("닉네임을 입력해야 합니다.")
            val normalized = nickname.lowercase()
            if (normalized != profile.nicknameNormalized && profileRepository.existsByNicknameNormalizedAndDeletedAtIsNull(normalized)) {
                throw ApiException(ApiErrorCode.NICKNAME_ALREADY_EXISTS)
            }
            profile.nickname = nickname
            profile.nicknameNormalized = normalized
        }
        request.displayName?.let {
            profile.displayName = it.trim().takeIf(String::isNotEmpty) ?: invalid("이름을 입력해야 합니다.")
        }
        request.birthDate?.let { profile.birthDate = it }
        request.gender?.let { profile.gender = it }
        request.visibility?.let { profile.visibility = it }
        request.bio?.let { profile.bio = it.trim().takeIf(String::isNotEmpty) }
        request.profileImageMediaId?.let { profile.profileImageMediaId = mediaAssets.requireOwnedActive(userId, it, MediaPurpose.PROFILE).id }
        request.weightUnit?.let { profile.weightUnit = it }
        request.timezoneId?.let { profile.timezoneId = validZoneId(it) }
        updatePlaces(userId, request)
        request.availableTimes?.let { times ->
            times.forEach {
                if (it.weekdayLocal !in 0..6 || !it.startLocalTime.isBefore(it.endLocalTime)) invalid("운동 가능 시간이 올바르지 않습니다.")
                validZoneId(it.timezoneId)
            }
            availableTimeRepository.deleteAllByUserId(userId)
            availableTimeRepository.flush()
            availableTimeRepository.saveAll(times.map { UserAvailableTime(UUID.randomUUID(), userId, it.weekdayLocal, it.startLocalTime, it.endLocalTime, it.timezoneId) })
        }
        request.sports?.let { selections ->
            if (selections.map { it.sportCode }.distinct().size != selections.size) {
                invalid("관심 운동은 중복 선택할 수 없습니다.")
            }
            val sports = selections.map { selection ->
                val sport = sportRepository.findByCodeAndIsActiveTrue(selection.sportCode) ?: invalid("활성 운동 종목만 선택할 수 있습니다.")
                UserSport(UserSportId(userId, requireNotNull(sport.id)), selection.skillLevel)
            }
            userSportRepository.deleteAllByIdUserId(userId)
            userSportRepository.flush()
            userSportRepository.saveAll(sports)
        }
        request.customSports?.let { customSportService.replace(userId, it) }
        request.goals?.let { goals ->
            if (goals.map { it.goalCode }.distinct().size != goals.size || goals.count { it.primary } != 1) invalid("주 목표를 하나 선택해야 합니다.")
            goalRepository.deleteAllByIdUserId(userId)
            goalRepository.flush()
            goalRepository.saveAll(goals.map { UserGoal(UserGoalId(userId, it.goalCode), it.primary) })
        }
        // Child collection changes must also advance the profile's optimistic version.
        profile.updatedAt = maxOf(clock.instant(), profile.updatedAt.plusMillis(1))
        profileRepository.saveAndFlush(profile)
        return get(userId)
    }

    private fun updatePlaces(userId: UUID, request: UpdateProfileRequest) {
        if (request.activityRegionIds == null && request.facilityNames == null) return
        val regionIds = request.activityRegionIds?.map(UuidBinaryConverter::toMysqlOrderedUuid)?.distinct()
            ?: activityRegionRepository.findAllByIdUserId(userId).map { requireNotNull(it.id.regionId) }
        regionIds.forEach {
            if (regionRepository.findById(it).orElse(null)?.isActive != true) invalid("활성 지역만 선택할 수 있습니다.")
        }
        val names = request.facilityNames?.map { it.trim().replace(Regex("\\s+"), " ") }
        if (names != null && names.any { it.isEmpty() || it.length > 150 }) invalid("시설 이름은 1~150자여야 합니다.")
        if (!names.isNullOrEmpty() && regionIds.size != 1) invalid("시설을 등록하려면 활동 지역을 하나 선택해야 합니다.")
        if (names == null) {
            val retainedFacilities = userFacilityRepository.findAllByIdUserId(userId)
            if (retainedFacilities.any { facilityRepository.findById(requireNotNull(it.id.facilityId)).orElseThrow().regionId !in regionIds }) {
                invalid("활동 지역을 변경하려면 기존 시설도 함께 수정해야 합니다.")
            }
        }
        request.activityRegionIds?.let {
            activityRegionRepository.deleteAllByIdUserId(userId)
            activityRegionRepository.flush()
            activityRegionRepository.saveAll(regionIds.mapIndexed { index, id -> UserActivityRegion(UserRegionId(userId, id), index == 0) })
        }
        names?.let {
            val facilities = names.distinctBy { it.lowercase() }.mapIndexed { index, name ->
                val regionId = regionIds.single()
                val facility = facilityRepository.findByRegionIdAndNormalizedName(regionId, name.lowercase())
                    ?: facilityRepository.saveAndFlush(Facility(UUID.randomUUID(), name, name.lowercase(), regionId))
                UserFacility(UserFacilityId(userId, requireNotNull(facility.id)), index == 0)
            }
            userFacilityRepository.deleteAllByIdUserId(userId)
            userFacilityRepository.flush()
            userFacilityRepository.saveAll(facilities)
        }
    }

    private fun validZoneId(value: String): String = try { ZoneId.of(value).id } catch (_: Exception) { invalid("timezoneId가 올바르지 않습니다.") }
    private fun invalid(message: String): Nothing = throw ApiException(ApiErrorCode.VALIDATION_ERROR, message)
}
