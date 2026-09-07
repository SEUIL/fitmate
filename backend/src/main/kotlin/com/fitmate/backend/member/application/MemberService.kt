package com.fitmate.backend.member.application

import com.fitmate.backend.auth.api.AvailableTimeRequest
import com.fitmate.backend.auth.api.GoalSelectionRequest
import com.fitmate.backend.auth.api.SportSelectionRequest
import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.auth.domain.AccountStatus
import com.fitmate.backend.auth.domain.AuthEvent
import com.fitmate.backend.auth.domain.AuthEventType
import com.fitmate.backend.auth.repository.AuthEventRepository
import com.fitmate.backend.auth.repository.AuthSessionRepository
import com.fitmate.backend.auth.repository.UserRepository
import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import com.fitmate.backend.fitness.domain.SportCode
import com.fitmate.backend.fitness.repository.SportRepository
import com.fitmate.backend.member.api.ActivityRegionResponse
import com.fitmate.backend.member.api.AvailableTimeResponse
import com.fitmate.backend.member.api.ChangePasswordRequest
import com.fitmate.backend.member.api.ConsentResponse
import com.fitmate.backend.member.api.FacilityResponse
import com.fitmate.backend.member.api.MeResponse
import com.fitmate.backend.member.api.NotificationSettingsResponse
import com.fitmate.backend.member.api.NotificationSettingsUpdateRequest
import com.fitmate.backend.member.api.ProfileResponse
import com.fitmate.backend.member.api.ProfileUpdateRequest
import com.fitmate.backend.member.api.RegionResponse
import com.fitmate.backend.member.api.TermsResponse
import com.fitmate.backend.member.api.UserFacilityResponse
import com.fitmate.backend.member.api.UserGoalResponse
import com.fitmate.backend.member.api.UserSportResponse
import com.fitmate.backend.member.api.WithdrawRequest
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
import com.fitmate.backend.member.repository.NotificationSettingRepository
import com.fitmate.backend.member.repository.RegionRepository
import com.fitmate.backend.member.repository.UserActivityRegionRepository
import com.fitmate.backend.member.repository.UserAvailableTimeRepository
import com.fitmate.backend.member.repository.UserFacilityRepository
import com.fitmate.backend.member.repository.UserGoalRepository
import com.fitmate.backend.member.repository.UserProfileRepository
import com.fitmate.backend.member.repository.UserSportRepository
import com.fitmate.backend.terms.domain.UserConsent
import com.fitmate.backend.terms.domain.TermsType
import com.fitmate.backend.terms.repository.TermsDocumentRepository
import com.fitmate.backend.terms.repository.UserConsentRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.ZoneId
import java.util.UUID

@Service
class MemberService(
    private val userRepository: UserRepository,
    private val userProfileRepository: UserProfileRepository,
    private val regionRepository: RegionRepository,
    private val facilityRepository: FacilityRepository,
    private val userActivityRegionRepository: UserActivityRegionRepository,
    private val userFacilityRepository: UserFacilityRepository,
    private val userAvailableTimeRepository: UserAvailableTimeRepository,
    private val userSportRepository: UserSportRepository,
    private val userGoalRepository: UserGoalRepository,
    private val notificationSettingRepository: NotificationSettingRepository,
    private val termsDocumentRepository: TermsDocumentRepository,
    private val userConsentRepository: UserConsentRepository,
    private val sportRepository: SportRepository,
    private val authSessionRepository: AuthSessionRepository,
    private val authEventRepository: AuthEventRepository,
    private val passwordEncoder: PasswordEncoder,
    private val clock: Clock,
) {
    fun getTerms(type: TermsType?, current: Boolean): List<TermsResponse> {
        val documents = termsDocumentRepository.findAllByOrderByEffectiveAtDesc().filter { type == null || it.type == type }
        val resolved = if (current) documents.groupBy { it.type }.values.mapNotNull { items -> items.maxByOrNull { it.effectiveAt } } else documents
        return resolved.map { TermsResponse(toApiReferenceId(requireNotNull(it.id)), it.type, it.version, it.contentUri, it.effectiveAt, it.isRequired) }
    }

    fun getRegions(parentId: UUID?): List<RegionResponse> = regionRepository.findAllByIsActiveTrueAndParentIdOrderByNameAsc(parentId?.let(::toStoredReferenceId)).map {
        RegionResponse(toApiReferenceId(requireNotNull(it.id)), it.parentId?.let(::toApiReferenceId), it.name, if (it.level == 1) "PROVINCE" else "CITY", it.code)
    }

    fun getFacilities(regionId: UUID?, name: String?): List<FacilityResponse> = facilityRepository.searchActive(regionId?.let(::toStoredReferenceId), name?.trim()?.takeIf { it.isNotEmpty() }).map {
        FacilityResponse(requireNotNull(it.id), toApiReferenceId(requireNotNull(it.regionId)), it.name, it.addressSummary)
    }

    @Transactional(readOnly = true)
    fun me(currentUser: AuthenticatedUser): MeResponse {
        val user = userRepository.findById(currentUser.userId).orElseThrow { ApiException(ApiErrorCode.RESOURCE_NOT_FOUND) }
        val profile = userProfileRepository.findById(currentUser.userId).orElseThrow { ApiException(ApiErrorCode.RESOURCE_NOT_FOUND) }
        val regions = userActivityRegionRepository.findAllByIdUserId(currentUser.userId)
        val regionById = regionRepository.findAllById(regions.mapNotNull { it.id.regionId }).associateBy { requireNotNull(it.id) }
        val facilities = userFacilityRepository.findAllByIdUserId(currentUser.userId)
        val facilityById = facilityRepository.findAllById(facilities.mapNotNull { it.id.facilityId }).associateBy { requireNotNull(it.id) }
        val sports = userSportRepository.findAllByIdUserId(currentUser.userId)
        val sportById = sportRepository.findAllById(sports.mapNotNull { it.id.sportId }).associateBy { requireNotNull(it.id) }
        val consents = userConsentRepository.findAllByUserIdOrderByConsentedAtDesc(currentUser.userId)
        val documentById = termsDocumentRepository.findAllById(consents.mapNotNull { it.termsDocumentId }).associateBy { requireNotNull(it.id) }
        return MeResponse(
            id = currentUser.userId,
            email = user.email,
            accountStatus = user.accountStatus.name,
            profile = ProfileResponse(profile.nickname, profile.displayName, profile.birthDate, profile.gender, profile.visibility, profile.bio, profile.weightUnit, profile.timezoneId, profile.version),
            activityRegions = regions.mapNotNull { relation -> regionById[relation.id.regionId]?.let { ActivityRegionResponse(toApiReferenceId(requireNotNull(it.id)), it.name, relation.isPrimary) } },
            facilities = facilities.mapNotNull { relation -> facilityById[relation.id.facilityId]?.let { UserFacilityResponse(requireNotNull(it.id), toApiReferenceId(requireNotNull(it.regionId)), it.name, relation.isPrimary) } },
            availableTimes = userAvailableTimeRepository.findAllByUserIdOrderByWeekdayLocalAscStartLocalTimeAsc(currentUser.userId).map { AvailableTimeResponse(requireNotNull(it.id), it.weekdayLocal, it.startLocalTime, it.endLocalTime, it.timezoneId) },
            sports = sports.mapNotNull { relation -> sportById[relation.id.sportId]?.let { UserSportResponse(it.code.name, relation.skillLevel.name) } },
            goals = userGoalRepository.findAllByIdUserId(currentUser.userId).map { UserGoalResponse(it.id.goalCode.name, it.isPrimary) },
            consents = consents.mapNotNull { consent -> documentById[consent.termsDocumentId]?.let { document -> ConsentResponse(toApiReferenceId(requireNotNull(document.id)), document.type, document.version, consent.consentedAt, consent.withdrawnAt) } },
        )
    }

    @Transactional
    fun updateProfile(currentUser: AuthenticatedUser, request: ProfileUpdateRequest): ProfileResponse {
        val profile = userProfileRepository.findById(currentUser.userId).orElseThrow { ApiException(ApiErrorCode.RESOURCE_NOT_FOUND) }
        if (profile.version != request.version) throw ApiException(ApiErrorCode.VERSION_CONFLICT)
        request.nickname?.trim()?.let { nickname ->
            val normalized = normalize(nickname)
            if (normalized != profile.nicknameNormalized && userProfileRepository.existsByNicknameNormalizedAndDeletedAtIsNull(normalized)) throw ApiException(ApiErrorCode.NICKNAME_ALREADY_EXISTS)
            if (nickname.isEmpty()) throw ApiException(ApiErrorCode.VALIDATION_ERROR)
            profile.nickname = nickname
            profile.nicknameNormalized = normalized
        }
        request.displayName?.trim()?.let { if (it.isEmpty()) throw ApiException(ApiErrorCode.VALIDATION_ERROR) else profile.displayName = it }
        request.birthDate?.let { profile.birthDate = it }
        request.gender?.let { profile.gender = it }
        request.visibility?.let { profile.visibility = it }
        request.bio?.let { profile.bio = it.trim().takeIf(String::isNotEmpty) }
        request.weightUnit?.let { profile.weightUnit = it }
        request.timezoneId?.let { profile.timezoneId = validZoneId(it) }
        if (request.activityRegionIds != null || request.facilityNames != null) replaceRegionsAndFacilities(currentUser.userId, request.activityRegionIds?.map(::toStoredReferenceId), request.facilityNames)
        request.availableTimes?.let { replaceAvailableTimes(currentUser.userId, it) }
        request.sports?.let { replaceSports(currentUser.userId, it) }
        request.goals?.let { replaceGoals(currentUser.userId, it) }
        val updated = userProfileRepository.saveAndFlush(profile)
        return ProfileResponse(updated.nickname, updated.displayName, updated.birthDate, updated.gender, updated.visibility, updated.bio, updated.weightUnit, updated.timezoneId, updated.version)
    }

    @Transactional
    fun changePassword(currentUser: AuthenticatedUser, request: ChangePasswordRequest) {
        val user = userRepository.findById(currentUser.userId).orElseThrow { ApiException(ApiErrorCode.RESOURCE_NOT_FOUND) }
        if (!passwordEncoder.matches(request.currentPassword, user.passwordHash)) throw ApiException(ApiErrorCode.INVALID_CREDENTIALS)
        validatePassword(request.newPassword)
        user.passwordHash = passwordEncoder.encode(request.newPassword)
        invalidateAllSessions(user, "PASSWORD_CHANGED")
        authEventRepository.save(AuthEvent(UUID.randomUUID(), currentUser.userId, currentUser.sessionId, AuthEventType.PASSWORD_CHANGED, null, clock.instant()))
    }

    @Transactional
    fun consent(currentUser: AuthenticatedUser, termsDocumentId: UUID) {
        val storedDocumentId = toStoredReferenceId(termsDocumentId)
        val document = termsDocumentRepository.findById(storedDocumentId).orElseThrow { ApiException(ApiErrorCode.RESOURCE_NOT_FOUND) }
        if (document.isRequired) throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION)
        val now = clock.instant()
        val existing = userConsentRepository.findByUserIdAndTermsDocumentId(currentUser.userId, storedDocumentId)
        if (existing == null) userConsentRepository.save(UserConsent(UUID.randomUUID(), currentUser.userId, storedDocumentId, now)) else existing.withdrawnAt = null
    }

    @Transactional
    fun withdrawConsent(currentUser: AuthenticatedUser, termsDocumentId: UUID) {
        val storedDocumentId = toStoredReferenceId(termsDocumentId)
        val document = termsDocumentRepository.findById(storedDocumentId).orElseThrow { ApiException(ApiErrorCode.RESOURCE_NOT_FOUND) }
        if (document.isRequired) throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION)
        val consent = userConsentRepository.findByUserIdAndTermsDocumentId(currentUser.userId, storedDocumentId) ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND)
        consent.withdrawnAt = clock.instant()
    }

    @Transactional
    fun withdraw(currentUser: AuthenticatedUser, request: WithdrawRequest) {
        val user = userRepository.findById(currentUser.userId).orElseThrow { ApiException(ApiErrorCode.RESOURCE_NOT_FOUND) }
        if (!passwordEncoder.matches(request.password, user.passwordHash)) throw ApiException(ApiErrorCode.INVALID_CREDENTIALS)
        val now = clock.instant()
        user.accountStatus = AccountStatus.WITHDRAWN
        user.withdrawnAt = now
        userProfileRepository.findById(currentUser.userId).ifPresent { it.deletedAt = now }
        invalidateAllSessions(user, "WITHDRAWAL")
        authEventRepository.save(AuthEvent(UUID.randomUUID(), currentUser.userId, currentUser.sessionId, AuthEventType.WITHDRAWAL, null, now))
    }

    @Transactional(readOnly = true)
    fun notificationSettings(currentUser: AuthenticatedUser): NotificationSettingsResponse {
        val setting = notificationSettingRepository.findById(currentUser.userId).orElseThrow { ApiException(ApiErrorCode.RESOURCE_NOT_FOUND) }
        return NotificationSettingsResponse(setting.inAppEnabled, setting.dmEnabled, setting.mateEnabled, setting.communityEnabled, setting.version)
    }

    @Transactional
    fun updateNotificationSettings(currentUser: AuthenticatedUser, request: NotificationSettingsUpdateRequest): NotificationSettingsResponse {
        val setting = notificationSettingRepository.findById(currentUser.userId).orElseThrow { ApiException(ApiErrorCode.RESOURCE_NOT_FOUND) }
        if (setting.version != request.version) throw ApiException(ApiErrorCode.VERSION_CONFLICT)
        setting.inAppEnabled = request.inAppEnabled
        setting.dmEnabled = request.dmEnabled
        setting.mateEnabled = request.mateEnabled
        setting.communityEnabled = request.communityEnabled
        val updated = notificationSettingRepository.saveAndFlush(setting)
        return NotificationSettingsResponse(updated.inAppEnabled, updated.dmEnabled, updated.mateEnabled, updated.communityEnabled, updated.version)
    }

    private fun replaceRegionsAndFacilities(userId: UUID, regionIds: List<UUID>?, facilityNames: List<String>?) {
        val resolvedRegions = regionIds?.distinct() ?: userActivityRegionRepository.findAllByIdUserId(userId).mapNotNull { it.id.regionId }
        if (facilityNames != null && facilityNames.isNotEmpty() && resolvedRegions.size != 1) throw ApiException(ApiErrorCode.VALIDATION_ERROR, "시설을 등록하려면 활동 지역을 하나 선택해야 합니다.")
        regionIds?.let {
            it.forEach { regionId -> if (!regionRepository.existsById(regionId)) throw ApiException(ApiErrorCode.VALIDATION_ERROR) }
            userActivityRegionRepository.deleteAllByIdUserId(userId)
            it.distinct().forEachIndexed { index, regionId -> userActivityRegionRepository.save(UserActivityRegion(UserRegionId(userId, regionId), index == 0)) }
        }
        facilityNames?.let { names ->
            userFacilityRepository.deleteAllByIdUserId(userId)
            val regionId = resolvedRegions.singleOrNull()
            names.distinct().forEachIndexed { index, name ->
                val normalized = normalizeFacility(name)
                val facility = facilityRepository.findByRegionIdAndNormalizedName(requireNotNull(regionId), normalized)
                    ?: facilityRepository.save(Facility(UUID.randomUUID(), name.trim(), normalized, regionId))
                userFacilityRepository.save(UserFacility(UserFacilityId(userId, requireNotNull(facility.id)), index == 0))
            }
        }
    }

    private fun replaceAvailableTimes(userId: UUID, values: List<AvailableTimeRequest>) {
        values.forEach { validateAvailableTime(it) }
        userAvailableTimeRepository.deleteAllByUserId(userId)
        values.forEach { value -> userAvailableTimeRepository.save(UserAvailableTime(UUID.randomUUID(), userId, value.weekdayLocal, value.startLocalTime, value.endLocalTime, validZoneId(value.timezoneId))) }
    }

    private fun replaceSports(userId: UUID, values: List<SportSelectionRequest>) {
        if (values.map { it.sportCode }.distinct().size != values.size || values.count { it.sportCode == SportCode.HEALTH } != 1) throw ApiException(ApiErrorCode.VALIDATION_ERROR)
        userSportRepository.deleteAllByIdUserId(userId)
        values.forEach { value ->
            val sport = sportRepository.findByCodeAndIsActiveTrue(value.sportCode) ?: throw ApiException(ApiErrorCode.VALIDATION_ERROR)
            userSportRepository.save(UserSport(UserSportId(userId, requireNotNull(sport.id)), value.skillLevel))
        }
    }

    private fun replaceGoals(userId: UUID, values: List<GoalSelectionRequest>) {
        if (values.map { it.goalCode }.distinct().size != values.size || values.count { it.primary } != 1) throw ApiException(ApiErrorCode.VALIDATION_ERROR)
        userGoalRepository.deleteAllByIdUserId(userId)
        values.forEach { value -> userGoalRepository.save(UserGoal(UserGoalId(userId, value.goalCode), value.primary)) }
    }

    private fun invalidateAllSessions(user: com.fitmate.backend.auth.domain.User, reason: String) {
        val now = clock.instant()
        authSessionRepository.findAllByUserIdAndRevokedAtIsNull(requireNotNull(user.id)).forEach { it.revokedAt = now; it.revokeReason = reason }
        user.authVersion += 1
    }
    private fun validatePassword(value: String) { if (value.length !in 8..64 || value.toByteArray(StandardCharsets.UTF_8).size > 72 || value.none { it.isLetter() } || value.none { it.isDigit() }) throw ApiException(ApiErrorCode.VALIDATION_ERROR, "비밀번호 형식이 올바르지 않습니다.") }
    private fun validateAvailableTime(value: AvailableTimeRequest) { if (value.weekdayLocal !in 0..6 || !value.startLocalTime.isBefore(value.endLocalTime)) throw ApiException(ApiErrorCode.VALIDATION_ERROR) }
    private fun validZoneId(value: String): String = try { ZoneId.of(value).id } catch (_: Exception) { throw ApiException(ApiErrorCode.VALIDATION_ERROR) }
    private fun normalize(value: String): String = value.trim().lowercase()
    private fun normalizeFacility(value: String): String = normalize(value).replace(Regex("\\s+"), " ")
    private fun toStoredReferenceId(value: UUID): UUID = UuidBinaryConverter.toMysqlOrderedUuid(value)
    private fun toApiReferenceId(value: UUID): UUID = UuidBinaryConverter.fromMysqlOrderedUuid(value)
}
