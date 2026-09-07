package com.fitmate.backend.media.api

import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.api.ApiResponse
import com.fitmate.backend.media.application.MediaAssetService
import com.fitmate.backend.media.domain.MediaPurpose
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/v1/media")
class MediaAssetController(private val mediaAssetService: MediaAssetService) {
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @AuthenticationPrincipal currentUser: AuthenticatedUser,
        @RequestParam purpose: MediaPurpose,
        @RequestPart("file") file: MultipartFile,
    ): ApiResponse<MediaUploadResponse> = ApiResponse.success(mediaAssetService.upload(currentUser.userId, purpose, file))

    @GetMapping("/{mediaId}")
    fun open(@AuthenticationPrincipal currentUser: AuthenticatedUser, @PathVariable mediaId: UUID): ResponseEntity<Resource> {
        val download = mediaAssetService.openForOwner(currentUser.userId, mediaId)
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(download.contentType)).body(download.resource)
    }

    @DeleteMapping("/{mediaId}")
    fun delete(@AuthenticationPrincipal currentUser: AuthenticatedUser, @PathVariable mediaId: UUID): ResponseEntity<Void> {
        mediaAssetService.delete(currentUser.userId, mediaId)
        return ResponseEntity.noContent().build()
    }
}
