package com.fitmate.backend.member.repository

import com.fitmate.backend.member.domain.Facility
import com.fitmate.backend.member.domain.NotificationSetting
import com.fitmate.backend.member.domain.Region
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
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface UserProfileRepository : JpaRepository<UserProfile, UUID> {
    fun existsByNicknameNormalizedAndDeletedAtIsNull(nicknameNormalized: String): Boolean
}

interface RegionRepository : JpaRepository<Region, UUID> {
    fun findAllByIsActiveTrueAndParentIdOrderByNameAsc(parentId: UUID?): List<Region>
}

interface FacilityRepository : JpaRepository<Facility, UUID> {
    fun findByRegionIdAndNormalizedName(regionId: UUID, normalizedName: String): Facility?

    @Query("select facility from Facility facility where facility.isActive = true and (:regionId is null or facility.regionId = :regionId) and (:name is null or lower(facility.name) like lower(concat('%', :name, '%'))) order by facility.name")
    fun searchActive(regionId: UUID?, name: String?): List<Facility>
}

interface UserActivityRegionRepository : JpaRepository<UserActivityRegion, UserRegionId> {
    fun findAllByIdUserId(userId: UUID): List<UserActivityRegion>
    fun deleteAllByIdUserId(userId: UUID)
}

interface UserFacilityRepository : JpaRepository<UserFacility, UserFacilityId> {
    fun findAllByIdUserId(userId: UUID): List<UserFacility>
    fun deleteAllByIdUserId(userId: UUID)
}

interface UserAvailableTimeRepository : JpaRepository<UserAvailableTime, UUID> {
    fun findAllByUserIdOrderByWeekdayLocalAscStartLocalTimeAsc(userId: UUID): List<UserAvailableTime>
    fun deleteAllByUserId(userId: UUID)
}

interface UserSportRepository : JpaRepository<UserSport, UserSportId> {
    fun findAllByIdUserId(userId: UUID): List<UserSport>
    fun deleteAllByIdUserId(userId: UUID)
}

interface UserGoalRepository : JpaRepository<UserGoal, UserGoalId> {
    fun findAllByIdUserId(userId: UUID): List<UserGoal>
    fun deleteAllByIdUserId(userId: UUID)
}

interface NotificationSettingRepository : JpaRepository<NotificationSetting, UUID>
