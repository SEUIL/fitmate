package com.fitmate.backend.member.api

import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.media.application.MediaAssetService
import com.fitmate.backend.member.application.ProfileService
import com.fitmate.backend.social.application.SocialRelationshipService
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users")
class ProfileImageController(
    private val relationships: SocialRelationshipService,
    private val profileService: ProfileService,
    private val mediaAssets: MediaAssetService,
) {
    @GetMapping("/{userId}/profile-image")
    fun open(@AuthenticationPrincipal currentUser: AuthenticatedUser, @PathVariable userId: UUID): ResponseEntity<Resource> {
        val preview = relationships.profile(currentUser.userId, userId)
        if (!preview.canViewProfile) throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND)
        val mediaId = profileService.profileImageMediaId(userId)
            ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND)
        val download = mediaAssets.openForAuthorizedReader(mediaId)
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(download.contentType)).body(download.resource)
    }
}
