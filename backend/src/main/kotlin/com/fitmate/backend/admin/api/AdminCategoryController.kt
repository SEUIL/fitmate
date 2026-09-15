package com.fitmate.backend.admin.api

import com.fitmate.backend.admin.application.AdminCategoryService
import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.api.ApiResponse
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class AdminCategory(val id: UUID, val code: String, val name: String, val active: Boolean, val displayOrder: Int)
data class OtherSportCount(val name: String, val memberCount: Long)
data class AdminCategoryCatalog(val version: Long, val items: List<AdminCategory>, val otherSports: List<OtherSportCount>, val otherSportsLimit: Int = 50)
data class CategoryOrder(@param:JsonProperty(required = true) @field:Min(0) val expectedVersion: Long, @field:Size(min = 1, max = 100) val ids: List<UUID>)

@RestController
@RequestMapping("/api/v1/admin/categories")
class AdminCategoryController(private val service: AdminCategoryService) {
    @GetMapping fun catalog(@AuthenticationPrincipal user: AuthenticatedUser) = ApiResponse.success(service.catalog(user))
    @PutMapping("/order") fun order(@AuthenticationPrincipal user: AuthenticatedUser,
        @RequestHeader("Idempotency-Key") key: UUID, @Valid @RequestBody request: CategoryOrder) = ApiResponse.success(service.reorder(user, key, request))
}
