package com.fitmate.backend.media.application

import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.community.repository.CommunityPostMediaRepository
import com.fitmate.backend.dm.repository.DirectMessageRepository
import com.fitmate.backend.media.api.MediaUploadResponse
import com.fitmate.backend.media.domain.MediaAsset
import com.fitmate.backend.media.domain.MediaPurpose
import com.fitmate.backend.media.domain.MediaStatus
import com.fitmate.backend.media.repository.MediaAssetRepository
import com.fitmate.backend.member.repository.UserProfileRepository
import org.springframework.core.io.FileSystemResource
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

data class MediaDownload(val contentType: String, val resource: FileSystemResource)

@Service
class MediaAssetService(
    private val assets: MediaAssetRepository,
    private val communityPostMedia: CommunityPostMediaRepository,
    private val directMessages: DirectMessageRepository,
    private val profiles: UserProfileRepository,
    private val properties: MediaStorageProperties,
) {
    @Transactional
    fun upload(ownerUserId: UUID, purpose: MediaPurpose, file: MultipartFile): MediaUploadResponse {
        val contentType = file.contentType?.lowercase() ?: invalid("이미지 형식을 확인할 수 없습니다.")
        if (contentType !in allowedContentTypes) invalid("JPEG, PNG, WEBP 이미지만 업로드할 수 있습니다.")
        if (file.isEmpty || file.size !in 1..properties.maxImageBytes) invalid("이미지 크기는 1바이트 이상 ${properties.maxImageBytes}바이트 이하여야 합니다.")

        val mediaId = UUID.randomUUID()
        val key = "${ownerUserId}/$mediaId.${extensionFor(contentType)}"
        val destination = resolve(key)
        try {
            Files.createDirectories(destination.parent)
            file.inputStream.use { input -> Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING) }
            val saved = assets.saveAndFlush(MediaAsset(
                id = mediaId,
                ownerUserId = ownerUserId,
                purpose = purpose,
                originalFilename = file.originalFilename?.take(255),
                contentType = contentType,
                byteSize = file.size,
                storageKey = key,
            ))
            return response(requireNotNull(saved.id), saved.purpose, saved.contentType, saved.byteSize)
        } catch (exception: ApiException) {
            throw exception
        } catch (exception: Exception) {
            Files.deleteIfExists(destination)
            throw ApiException(ApiErrorCode.INTERNAL_SERVER_ERROR, "이미지를 저장하지 못했습니다.")
        }
    }

    @Transactional(readOnly = true)
    fun openForOwner(ownerUserId: UUID, mediaId: UUID): MediaDownload {
        val asset = assets.findByIdAndOwnerUserIdAndStatus(mediaId, ownerUserId, MediaStatus.ACTIVE)
            ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "이미지를 찾을 수 없습니다.")
        val file = resolve(asset.storageKey).toFile()
        if (!file.isFile) throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "이미지를 찾을 수 없습니다.")
        return MediaDownload(asset.contentType, FileSystemResource(file))
    }

    @Transactional(readOnly = true)
    fun openForAuthorizedReader(mediaId: UUID): MediaDownload {
        val asset = assets.findByIdAndStatus(mediaId, MediaStatus.ACTIVE)
            ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "이미지를 찾을 수 없습니다.")
        val file = resolve(asset.storageKey).toFile()
        if (!file.isFile) throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "이미지를 찾을 수 없습니다.")
        return MediaDownload(asset.contentType, FileSystemResource(file))
    }

    @Transactional(readOnly = true)
    fun requireOwnedActive(ownerUserId: UUID, mediaId: UUID, purpose: MediaPurpose): MediaAsset =
        assets.findByIdAndOwnerUserIdAndStatus(mediaId, ownerUserId, MediaStatus.ACTIVE)
            ?.takeIf { it.purpose == purpose }
            ?: throw ApiException(ApiErrorCode.VALIDATION_ERROR, "사용할 수 없는 이미지입니다.")

    @Transactional
    fun delete(ownerUserId: UUID, mediaId: UUID) {
        val asset = assets.findByIdAndOwnerUserIdAndStatus(mediaId, ownerUserId, MediaStatus.ACTIVE)
            ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "이미지를 찾을 수 없습니다.")
        if (communityPostMedia.existsByMediaId(mediaId)) {
            throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION, "게시물에 사용 중인 이미지는 게시물을 수정하거나 삭제한 뒤 삭제할 수 있습니다.")
        }
        if (directMessages.existsByMediaIdAndDeletedAtIsNull(mediaId)) {
            throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION, "대화에 사용 중인 이미지는 삭제할 수 없습니다.")
        }
        if (profiles.existsByProfileImageMediaId(mediaId)) {
            throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION, "프로필에 사용 중인 이미지는 새 프로필 사진으로 변경한 뒤 삭제할 수 있습니다.")
        }
        asset.status = MediaStatus.DELETED
        assets.saveAndFlush(asset)
        Files.deleteIfExists(resolve(asset.storageKey))
    }

    private fun response(id: UUID, purpose: MediaPurpose, contentType: String, byteSize: Long) =
        MediaUploadResponse(id, purpose, contentType, byteSize, "/api/v1/media/$id")

    private fun resolve(key: String): Path {
        val root = Path.of(properties.storageDir).toAbsolutePath().normalize()
        val resolved = root.resolve(key).normalize()
        if (!resolved.startsWith(root)) invalid("잘못된 저장 경로입니다.")
        return resolved
    }

    private fun extensionFor(contentType: String): String = when (contentType) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> invalid("지원하지 않는 이미지 형식입니다.")
    }

    private fun invalid(message: String): Nothing = throw ApiException(ApiErrorCode.VALIDATION_ERROR, message)

    private companion object {
        val allowedContentTypes = setOf("image/jpeg", "image/png", "image/webp")
    }
}
