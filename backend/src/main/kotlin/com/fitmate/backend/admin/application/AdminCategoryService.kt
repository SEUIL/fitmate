package com.fitmate.backend.admin.application

import com.fitmate.backend.admin.api.*
import com.fitmate.backend.admin.repository.AdminCategorySettingsRepository
import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.fitness.repository.SportRepository
import com.fitmate.backend.member.repository.UserCustomSportRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AdminCategoryService(private val sports: SportRepository, private val custom: UserCustomSportRepository,
    private val settings: AdminCategorySettingsRepository, private val access: AdminToolAccess) {
    @Transactional(readOnly = true)
    fun catalog(user: AuthenticatedUser): AdminCategoryCatalog {
        access.requireManager(user)
        return snapshot(settings.findById(1).orElseThrow { ApiException(ApiErrorCode.RESOURCE_NOT_FOUND) }.version)
    }
    fun reorder(user: AuthenticatedUser, key: UUID, request: CategoryOrder) =
        access.command(user, key, "category:order", request, AdminCategoryCatalog::class.java) {
            val state = settings.lockSettings() ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND)
            if (state.version != request.expectedVersion) throw ApiException(ApiErrorCode.VERSION_CONFLICT)
            val current = sports.findAll().associateBy { requireNotNull(it.id) }
            if (request.ids.isEmpty() || request.ids.size != current.size || request.ids.toSet() != current.keys) {
                throw ApiException(ApiErrorCode.VALIDATION_ERROR, "전체 종목을 중복이나 누락 없이 전달해 주세요.")
            }
            // MySQL checks the unique display_order index per row, even inside one transaction.
            // Move to unused values first so swapping two existing positions cannot collide.
            val occupied = current.values.map { it.displayOrder }.toSet() + (0 until current.size)
            val temporary = (65535 downTo 0).asSequence().filterNot { it in occupied }.take(current.size).toList()
            if (temporary.size != current.size) throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION)
            request.ids.forEachIndexed { index, id -> current.getValue(id).displayOrder = temporary[index] }
            sports.saveAllAndFlush(current.values)
            request.ids.forEachIndexed { index, id -> current.getValue(id).displayOrder = index }
            sports.saveAllAndFlush(current.values)
            state.version += 1
            settings.saveAndFlush(state)
            access.audit(user, "CATEGORY_ORDER_CHANGED", "CATEGORY_CATALOG", UUID(0L, 1L))
            snapshot(state.version)
        }
    private fun snapshot(version: Long) = AdminCategoryCatalog(version,
        sports.findAll().sortedWith(compareBy({ it.displayOrder }, { it.code.name })).map {
            AdminCategory(requireNotNull(it.id), it.code.name, it.name, it.isActive, it.displayOrder)
        }, custom.aggregateNames(PageRequest.of(0, 50)).map { OtherSportCount(it.name, it.total) })
}
