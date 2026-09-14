package com.fitmate.backend.auth.application

import com.fitmate.backend.auth.api.AvailableTimeRequest
import com.fitmate.backend.auth.api.GoalSelectionRequest
import com.fitmate.backend.auth.api.LoginRequest
import com.fitmate.backend.auth.api.LoginResponse
import com.fitmate.backend.auth.api.LogoutRequest
import com.fitmate.backend.auth.api.RefreshRequest
import com.fitmate.backend.auth.api.SignUpProfileResponse
import com.fitmate.backend.auth.api.SignUpRequest
import com.fitmate.backend.auth.api.SignUpResponse
import com.fitmate.backend.auth.api.SignUpUserResponse
import com.fitmate.backend.auth.api.TokenResponse
import com.fitmate.backend.auth.domain.AccountStatus
import com.fitmate.backend.auth.domain.AuthEvent
import com.fitmate.backend.auth.domain.AuthEventType
import com.fitmate.backend.auth.domain.AuthSession
import com.fitmate.backend.auth.domain.RevokedAccessToken
import com.fitmate.backend.auth.domain.User
import com.fitmate.backend.auth.repository.AuthEventRepository
import com.fitmate.backend.auth.repository.AuthSessionRepository
import com.fitmate.backend.auth.repository.RevokedAccessTokenRepository
import com.fitmate.backend.auth.repository.UserRepository
import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.common.config.JwtProperties
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import com.fitmate.backend.fitness.domain.SportCode
import com.fitmate.backend.fitness.repository.SportRepository
import com.fitmate.backend.member.domain.Facility
import com.fitmate.backend.member.application.CustomSportService
import com.fitmate.backend.member.domain.NotificationSetting
import com.fitmate.backend.member.domain.UserActivityRegion
import com.fitmate.backend.member.domain.UserAvailableTime
import com.fitmate.backend.member.domain.UserFacility
import com.fitmate.backend.member.domain.UserFacilityId
import com.fitmate.backend.member.domain.UserGoal
import com.fitmate.backend.member.domain.UserGoalId
import com.fitmate.backend.member.domain.UserProfile
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
import com.fitmate.backend.terms.repository.TermsDocumentRepository
import com.fitmate.backend.terms.repository.UserConsentRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import java.util.UUID

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val authSessionRepository: AuthSessionRepository,
    private val revokedAccessTokenRepository: RevokedAccessTokenRepository,
    private val authEventRepository: AuthEventRepository,
    private val userProfileRepository: UserProfileRepository,
    private val termsDocumentRepository: TermsDocumentRepository,
    private val userConsentRepository: UserConsentRepository,
    private val regionRepository: RegionRepository,
    private val facilityRepository: FacilityRepository,
    private val userActivityRegionRepository: UserActivityRegionRepository,
    private val userFacilityRepository: UserFacilityRepository,
    private val userAvailableTimeRepository: UserAvailableTimeRepository,
    private val userSportRepository: UserSportRepository,
    private val userGoalRepository: UserGoalRepository,
    private val notificationSettingRepository: NotificationSettingRepository,
    private val sportRepository: SportRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val jwtProperties: JwtProperties,
    private val clock: Clock,
    private val customSportService: CustomSportService,
    private val accountSuspensionService: com.fitmate.backend.admin.application.AccountSuspensionService,
) {
    @Transactional
    fun signUp(request: SignUpRequest, userAgent: String?): SignUpResponse {
        val storedRequest = request.copy(
            activityRegionIds = request.activityRegionIds.map(UuidBinaryConverter::toMysqlOrderedUuid),
            consentDocumentIds = request.consentDocumentIds.map(UuidBinaryConverter::toMysqlOrderedUuid),
        )
        val email = storedRequest.email.trim()
        val emailNormalized = normalize(email)
        validatePassword(storedRequest.password)
        val nickname = storedRequest.nickname.trim()
        val nicknameNormalized = normalize(nickname)
        if (userRepository.existsByEmailNormalizedAndAccountStatusNot(emailNormalized, AccountStatus.WITHDRAWN)) {
            throw ApiException(ApiErrorCode.EMAIL_ALREADY_EXISTS)
        }
        if (userProfileRepository.existsByNicknameNormalizedAndDeletedAtIsNull(nicknameNormalized)) {
            throw ApiException(ApiErrorCode.NICKNAME_ALREADY_EXISTS)
        }
        validateRequestCollections(storedRequest)
        customSportService.validate(storedRequest.customSports)
        val now = clock.instant()
        // Child entities carry scalar UUID foreign keys instead of JPA associations.
        // Flush the parent first so MySQL can enforce those foreign keys safely.
        val user = userRepository.saveAndFlush(User(UUID.randomUUID(), email, emailNormalized, passwordEncoder.encode(storedRequest.password)))
        val userId = requireNotNull(user.id)
        val profile = userProfileRepository.save(
            UserProfile(
                userId = userId,
                nickname = nickname,
                nicknameNormalized = nicknameNormalized,
                displayName = storedRequest.displayName?.trim()?.takeIf { it.isNotEmpty() } ?: nickname,
                birthDate = storedRequest.birthDate,
                gender = storedRequest.gender,
                visibility = storedRequest.visibility,
                bio = storedRequest.bio?.trim()?.takeIf { it.isNotEmpty() },
                weightUnit = storedRequest.weightUnit,
                timezoneId = validZoneId(storedRequest.timezoneId),
                onboardingCompletedAt = now,
            ),
        )
        createSelections(userId, storedRequest, now)
        val customSports = customSportService.replace(userId, storedRequest.customSports)
        notificationSettingRepository.save(NotificationSetting(userId = userId))
        val tokens = createSessionAndTokens(user, storedRequest.deviceId, null, now)
        recordEvent(userId, tokens.sessionId, AuthEventType.SIGNUP, userAgent, now)
        return SignUpResponse(
            SignUpUserResponse(userId, user.email, user.accountStatus, SignUpProfileResponse(profile.nickname, profile.displayName, profile.visibility, profile.weightUnit, profile.timezoneId, profile.version), customSports),
            tokens.response,
        )
    }

    @Transactional
    fun login(request: LoginRequest, userAgent: String?): LoginResponse {
        val user = userRepository.findByEmailNormalizedAndAccountStatusNot(normalize(request.email), AccountStatus.WITHDRAWN)
            ?: throw ApiException(ApiErrorCode.INVALID_CREDENTIALS)
        ensureActive(user)
        if (!passwordEncoder.matches(request.password, user.passwordHash)) throw ApiException(ApiErrorCode.INVALID_CREDENTIALS)
        val now = clock.instant()
        user.lastLoginAt = now
        val tokens = createSessionAndTokens(user, request.deviceId, null, now)
        recordEvent(requireNotNull(user.id), tokens.sessionId, AuthEventType.LOGIN, userAgent, now)
        return LoginResponse(requireNotNull(user.id), user.accountStatus, tokens.response)
    }

    @Transactional(noRollbackFor = [ApiException::class])
    fun refresh(request: RefreshRequest, userAgent: String?): TokenResponse {
        val now = clock.instant()
        val hash = hashToken(request.refreshToken)
        val previous = authSessionRepository.findForUpdateByRefreshTokenHash(hash) ?: throw ApiException(ApiErrorCode.INVALID_REFRESH_TOKEN)
        val userId = requireNotNull(previous.userId)
        val user = userRepository.findById(userId).orElseThrow { ApiException(ApiErrorCode.INVALID_REFRESH_TOKEN) }
        if (previous.revokedAt != null) {
            revokeAllSessions(user, now, "REUSE_DETECTED")
            recordEvent(userId, previous.id, AuthEventType.TOKEN_REUSE, userAgent, now)
            throw ApiException(ApiErrorCode.REFRESH_TOKEN_REUSE_DETECTED)
        }
        if (!previous.expiresAt.isAfter(now)) throw ApiException(ApiErrorCode.INVALID_REFRESH_TOKEN)
        ensureActive(user)
        val tokens = createSessionAndTokens(user, request.deviceId ?: previous.deviceId, requireNotNull(previous.tokenFamilyId), now)
        previous.revokedAt = now
        previous.revokeReason = "ROTATED"
        previous.replacedBySessionId = tokens.sessionId
        previous.lastUsedAt = now
        authSessionRepository.save(previous)
        recordEvent(userId, tokens.sessionId, AuthEventType.REFRESH, userAgent, now)
        return tokens.response
    }

    @Transactional
    fun logout(currentUser: AuthenticatedUser, request: LogoutRequest, userAgent: String?) {
        val session = authSessionRepository.findForUpdateByRefreshTokenHash(hashToken(request.refreshToken))
            ?: throw ApiException(ApiErrorCode.INVALID_REFRESH_TOKEN)
        if (session.id != currentUser.sessionId || session.userId != currentUser.userId) throw ApiException(ApiErrorCode.FORBIDDEN_RESOURCE)
        val now = clock.instant()
        session.revokedAt = now
        session.revokeReason = "LOGOUT"
        session.lastUsedAt = now
        authSessionRepository.save(session)
        revokeAccessToken(currentUser, now)
        recordEvent(currentUser.userId, currentUser.sessionId, AuthEventType.LOGOUT, userAgent, now)
    }

    @Transactional
    fun logoutAll(currentUser: AuthenticatedUser, userAgent: String?) {
        val user = userRepository.findById(currentUser.userId).orElseThrow { ApiException(ApiErrorCode.INVALID_ACCESS_TOKEN) }
        val now = clock.instant()
        revokeAllSessions(user, now, "LOGOUT_ALL")
        revokeAccessToken(currentUser, now)
        recordEvent(currentUser.userId, currentUser.sessionId, AuthEventType.LOGOUT_ALL, userAgent, now)
    }

    fun isEmailAvailable(email: String): Boolean = !userRepository.existsByEmailNormalizedAndAccountStatusNot(normalize(email), AccountStatus.WITHDRAWN)
    fun isNicknameAvailable(nickname: String): Boolean = !userProfileRepository.existsByNicknameNormalizedAndDeletedAtIsNull(normalize(nickname))

    private fun createSelections(userId: UUID, request: SignUpRequest, now: Instant) {
        val regions = request.activityRegionIds.distinct()
        regions.forEachIndexed { index, regionId ->
            if (!regionRepository.existsById(regionId)) throw ApiException(ApiErrorCode.VALIDATION_ERROR, "활성 지역만 선택할 수 있습니다.")
            userActivityRegionRepository.save(UserActivityRegion(UserRegionId(userId, regionId), index == 0))
        }
        if (request.facilityNames.isNotEmpty()) {
            val regionId = regions.single()
            request.facilityNames.distinct().forEachIndexed { index, name ->
                val normalizedName = normalizeFacility(name)
                val facility = facilityRepository.findByRegionIdAndNormalizedName(regionId, normalizedName)
                    ?: facilityRepository.save(Facility(UUID.randomUUID(), name.trim(), normalizedName, regionId))
                userFacilityRepository.save(UserFacility(UserFacilityId(userId, requireNotNull(facility.id)), index == 0))
            }
        }
        request.availableTimes.forEach { time ->
            validateAvailableTime(time)
            userAvailableTimeRepository.save(UserAvailableTime(UUID.randomUUID(), userId, time.weekdayLocal, time.startLocalTime, time.endLocalTime, validZoneId(time.timezoneId)))
        }
        request.sports.forEach { selection ->
            val sport = sportRepository.findByCodeAndIsActiveTrue(selection.sportCode)
                ?: throw ApiException(ApiErrorCode.VALIDATION_ERROR, "활성 운동 종목만 선택할 수 있습니다.")
            userSportRepository.save(UserSport(UserSportId(userId, requireNotNull(sport.id)), selection.skillLevel))
        }
        request.goals.forEach { goal -> userGoalRepository.save(UserGoal(UserGoalId(userId, goal.goalCode), goal.primary)) }
        val documents = termsDocumentRepository.findAllById(request.consentDocumentIds.distinct())
        val requiredIds = termsDocumentRepository.findAllByOrderByEffectiveAtDesc().filter { it.isRequired }.mapNotNull { it.id }.toSet()
        if (!documents.mapNotNull { it.id }.containsAll(requiredIds)) throw ApiException(ApiErrorCode.VALIDATION_ERROR, "필수 약관에 동의해야 합니다.")
        documents.forEach { document -> userConsentRepository.save(UserConsent(UUID.randomUUID(), userId, requireNotNull(document.id), now)) }
    }

    private fun validateRequestCollections(request: SignUpRequest) {
        if (request.sports.map { it.sportCode }.distinct().size != request.sports.size || (request.sports.isEmpty() && request.customSports.isEmpty())) {
            throw ApiException(ApiErrorCode.VALIDATION_ERROR, "중복 없이 관심 운동을 하나 이상 선택해야 합니다.")
        }
        if (request.goals.map { it.goalCode }.distinct().size != request.goals.size || request.goals.count { it.primary } != 1) {
            throw ApiException(ApiErrorCode.VALIDATION_ERROR, "주 목표를 하나 선택해야 합니다.")
        }
        if (request.facilityNames.isNotEmpty() && request.activityRegionIds.distinct().size != 1) {
            throw ApiException(ApiErrorCode.VALIDATION_ERROR, "시설을 등록하려면 활동 지역을 하나 선택해야 합니다.")
        }
    }

    private fun createSessionAndTokens(user: User, deviceId: String?, tokenFamilyId: UUID?, now: Instant): SessionTokens {
        val rawRefreshToken = randomToken()
        val session = authSessionRepository.save(AuthSession(UUID.randomUUID(), requireNotNull(user.id), tokenFamilyId ?: UUID.randomUUID(), hashToken(rawRefreshToken), deviceId, now, now.plus(jwtProperties.refreshTokenTtl)))
        val access = jwtService.issueAccessToken(user, session)
        return SessionTokens(requireNotNull(session.id), TokenResponse(access.value, java.time.Duration.between(now, access.expiresAt).seconds, rawRefreshToken))
    }

    private fun revokeAllSessions(user: User, now: Instant, reason: String) {
        authSessionRepository.findAllByUserIdAndRevokedAtIsNull(requireNotNull(user.id)).forEach { session ->
            session.revokedAt = now
            session.revokeReason = reason
        }
        user.authVersion += 1
        userRepository.save(user)
    }

    private fun revokeAccessToken(currentUser: AuthenticatedUser, now: Instant) {
        if (currentUser.expiresAt.isAfter(now)) revokedAccessTokenRepository.save(RevokedAccessToken(currentUser.accessTokenJti, currentUser.userId, currentUser.sessionId, currentUser.expiresAt, now))
    }

    private fun recordEvent(userId: UUID, sessionId: UUID?, type: AuthEventType, userAgent: String?, now: Instant) {
        authEventRepository.save(AuthEvent(UUID.randomUUID(), userId, sessionId, type, userAgent?.take(255), now))
    }

    private fun ensureActive(user: User) = when (accountSuspensionService.restoreIfExpired(user).accountStatus) {
        AccountStatus.ACTIVE -> Unit
        AccountStatus.SUSPENDED -> throw ApiException(ApiErrorCode.ACCOUNT_SUSPENDED)
        AccountStatus.BANNED -> throw ApiException(ApiErrorCode.ACCOUNT_BANNED)
        AccountStatus.WITHDRAWN -> throw ApiException(ApiErrorCode.ACCOUNT_WITHDRAWN)
    }

    private fun validatePassword(password: String) {
        if (password.length !in 8..64 || password.toByteArray(StandardCharsets.UTF_8).size > 72 || password.none { it.isLetter() } || password.none { it.isDigit() }) {
            throw ApiException(ApiErrorCode.VALIDATION_ERROR, "비밀번호 형식이 올바르지 않습니다.")
        }
    }

    private fun validateAvailableTime(value: AvailableTimeRequest) {
        if (value.weekdayLocal !in 0..6 || !value.startLocalTime.isBefore(value.endLocalTime)) throw ApiException(ApiErrorCode.VALIDATION_ERROR, "운동 가능 시간이 올바르지 않습니다.")
    }

    private fun validZoneId(value: String): String = try { ZoneId.of(value).id } catch (_: Exception) { throw ApiException(ApiErrorCode.VALIDATION_ERROR, "timezoneId가 올바르지 않습니다.") }
    private fun normalize(value: String): String = value.trim().lowercase()
    private fun normalizeFacility(value: String): String = normalize(value).replace(Regex("\\s+"), " ")
    private fun hashToken(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun randomToken(): String = ByteArray(32).also { SecureRandom().nextBytes(it) }.let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    private data class SessionTokens(val sessionId: UUID, val response: TokenResponse)
}
