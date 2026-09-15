package com.fitmate.backend.admin.application

import com.fitmate.backend.admin.api.*
import com.fitmate.backend.admin.domain.AdminNotice
import com.fitmate.backend.admin.domain.NoticeStatus
import com.fitmate.backend.admin.repository.AdminNoticeRepository
import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.Base64
import java.util.UUID

@Service
class AdminNoticeService(private val notices: AdminNoticeRepository, private val access: AdminToolAccess, private val clock: Clock) {
    @Transactional(readOnly = true)
    fun list(user: AuthenticatedUser, cursor: String?, size: Int): NoticePage {
        access.requireManager(user)
        return page(cursor, size, false)
    }
    @Transactional(readOnly = true)
    fun publicList(cursor: String?, size: Int) = page(cursor, size, true)
    private fun page(cursor: String?, size: Int, publicOnly: Boolean): NoticePage {
        val page = try { cursor?.let { String(Base64.getUrlDecoder().decode(it)).toInt().also { n -> require(n >= 0 && n <= 1000000) } } ?: 0 }
            catch (_: IllegalArgumentException) { throw ApiException(ApiErrorCode.VALIDATION_ERROR) }
        if (size !in 1..50) throw ApiException(ApiErrorCode.VALIDATION_ERROR)
        val request = PageRequest.of(page, size)
        val rows = if (publicOnly) notices.findAllByStatusOrderByPublishedAtDescIdDesc(NoticeStatus.PUBLISHED, request)
            else notices.findAllByOrderByUpdatedAtDescIdDesc(request)
        val hasNext = if (rows.size < size) false else {
            val next = PageRequest.of(page + 1, size)
            if (publicOnly) notices.findAllByStatusOrderByPublishedAtDescIdDesc(NoticeStatus.PUBLISHED, next).isNotEmpty()
            else notices.findAllByOrderByUpdatedAtDescIdDesc(next).isNotEmpty()
        }
        return NoticePage(rows.map { NoticeSummary(requireNotNull(it.id), it.title, it.status, it.publishedAt, it.version) },
            if (hasNext) Base64.getUrlEncoder().withoutPadding().encodeToString((page + 1).toString().toByteArray()) else null, hasNext)
    }
    @Transactional(readOnly = true)
    fun detail(user: AuthenticatedUser, id: UUID): NoticeDetail {
        access.requireManager(user)
        return dto(notices.findById(id).orElseThrow { ApiException(ApiErrorCode.RESOURCE_NOT_FOUND) })
    }
    @Transactional(readOnly = true)
    fun publicDetail(id: UUID) = dto(notices.findByIdAndStatus(id, NoticeStatus.PUBLISHED) ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND))
    fun create(user: AuthenticatedUser, key: UUID, request: NoticeEdit) =
        access.command(user, key, "notice:create", request, NoticeDetail::class.java) {
            validate(request)
            if (request.expectedVersion != 0L) throw ApiException(ApiErrorCode.VERSION_CONFLICT)
            val entity = notices.saveAndFlush(AdminNotice(UUID.randomUUID(), request.title.trim(), request.body.trim(), createdBy = user.userId, updatedBy = user.userId))
            access.audit(user, "NOTICE_CREATED", "NOTICE", requireNotNull(entity.id))
            dto(entity)
        }
    fun edit(user: AuthenticatedUser, id: UUID, key: UUID, request: NoticeEdit) =
        access.command(user, key, "notice:$id:edit", request, NoticeDetail::class.java) {
            validate(request)
            val entity = locked(id, request.expectedVersion)
            if (entity.status == NoticeStatus.ENDED) throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION)
            entity.title = request.title.trim(); entity.body = request.body.trim(); entity.updatedBy = user.userId
            val saved = notices.saveAndFlush(entity)
            access.audit(user, "NOTICE_EDITED", "NOTICE", id)
            dto(saved)
        }
    fun transition(user: AuthenticatedUser, id: UUID, key: UUID, request: NoticeVersion, next: NoticeStatus) =
        access.command(user, key, "notice:$id:$next", request, NoticeDetail::class.java) {
            val entity = locked(id, request.expectedVersion)
            if (!((entity.status == NoticeStatus.DRAFT && next == NoticeStatus.PUBLISHED) || (entity.status == NoticeStatus.PUBLISHED && next == NoticeStatus.ENDED))) {
                throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION)
            }
            entity.status = next; entity.updatedBy = user.userId
            if (next == NoticeStatus.PUBLISHED) entity.publishedAt = clock.instant() else entity.endedAt = clock.instant()
            val saved = notices.saveAndFlush(entity)
            access.audit(user, if (next == NoticeStatus.PUBLISHED) "NOTICE_PUBLISHED" else "NOTICE_ENDED", "NOTICE", id)
            dto(saved)
        }
    private fun validate(request: NoticeEdit) {
        if (request.title.isBlank() || request.title.length > 100 || request.body.isBlank() || request.body.length > 5000) throw ApiException(ApiErrorCode.VALIDATION_ERROR)
    }
    private fun locked(id: UUID, version: Long): AdminNotice {
        val entity = notices.findForUpdate(id) ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND)
        if (entity.version != version) throw ApiException(ApiErrorCode.VERSION_CONFLICT)
        return entity
    }
    private fun dto(n: AdminNotice) = NoticeDetail(requireNotNull(n.id), n.title, n.body, n.status, n.publishedAt, n.endedAt, n.version)
}
