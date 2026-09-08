package com.fitmate.backend.notification.api
import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.api.*
import com.fitmate.backend.common.error.*
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import com.fitmate.backend.notification.repository.NotificationRepository
import com.fitmate.backend.notification.domain.Notification
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.data.domain.PageRequest
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID
data class NotificationTargetResponse(val type:String?,val id:UUID?)
data class NotificationResponse(val id:UUID,val category:String,val type:String,val message:String,val target:NotificationTargetResponse?,val status:String,val readAt:Instant?,val createdAt:Instant)
@RestController @RequestMapping("/api/v1/notifications") class NotificationController(private val repo:NotificationRepository,private val objectMapper:ObjectMapper){
 @GetMapping fun list(@AuthenticationPrincipal u:AuthenticatedUser,@RequestParam(required=false,defaultValue="false") unreadOnly:Boolean,@RequestParam(required=false) cursor:String?,@RequestParam(defaultValue="20") size:Int):ApiResponse<CursorPageResponse<NotificationResponse>> {val boundary=decodeCursor(cursor);val limit=size.coerceIn(1,100);val selected=repo.findPage(u.userId,unreadOnly,boundary?.first,boundary?.second?.let(UuidBinaryConverter::toMysqlOrderedUuid),PageRequest.of(0,limit+1));val hasNext=selected.size>limit;val page=selected.take(limit);return ApiResponse.success(CursorPageResponse(page.map(::response),if(hasNext)encodeCursor(page.last().createdAt,UuidBinaryConverter.fromMysqlOrderedUuid(requireNotNull(page.last().id)))else null,hasNext))}
 @GetMapping("/unread-count") fun unread(@AuthenticationPrincipal u:AuthenticatedUser)=ApiResponse.success(mapOf("unreadCount" to repo.countByUserIdAndReadAtIsNull(u.userId)))
 @PatchMapping("/{id}/read") @Transactional fun read(@AuthenticationPrincipal u:AuthenticatedUser,@PathVariable id:UUID):ApiResponse<NotificationResponse>{val n=repo.findById(UuidBinaryConverter.toMysqlOrderedUuid(id)).orElseThrow{ApiException(ApiErrorCode.RESOURCE_NOT_FOUND)};if(n.userId!=u.userId)throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND);if(n.readAt==null)n.readAt=Instant.now();return ApiResponse.success(response(n))};private fun response(n:Notification):NotificationResponse{val payload=runCatching{objectMapper.readTree(n.payloadJson)}.getOrNull();val target=sequenceOf("postId" to "MATE_POST","roomId" to "MATE_CHAT_ROOM","messageId" to "MATE_CHAT_MESSAGE","applicationId" to "MATE_APPLICATION").mapNotNull{(field,type)->payload?.get(field)?.asText()?.let{runCatching{UUID.fromString(it)}.getOrNull()?.let{uuid->NotificationTargetResponse(type,uuid)}}}.firstOrNull();return NotificationResponse(UuidBinaryConverter.fromMysqlOrderedUuid(requireNotNull(n.id)),"MATE",n.type,message(n.type),target,if(n.readAt==null)"UNREAD" else "READ",n.readAt,n.createdAt)};private fun message(type:String)=when(type){"MATE_INQUIRY_RECEIVED"->"새 문의가 도착했습니다.";"MATE_MESSAGE_RECEIVED"->"새 메시지가 도착했습니다.";"MATE_APPLICATION_RECEIVED"->"새 참가 신청이 도착했습니다.";"MATE_APPLICATION_ACCEPTED"->"참가 신청이 수락되었습니다.";"MATE_APPLICATION_REJECTED"->"참가 신청이 거절되었습니다.";"MATE_APPLICATION_CANCELED"->"참가 신청이 취소되었습니다.";"MATE_PARTICIPATION_WITHDRAWN"->"참가자가 철회했습니다.";"MATE_POST_CLOSED"->"모집이 마감되었습니다.";"MATE_POST_CANCELED"->"모집이 취소되었습니다.";else->"메이트 활동 알림입니다."};private fun decodeCursor(value:String?):Pair<Instant,UUID>?=value?.let{runCatching{String(java.util.Base64.getUrlDecoder().decode(it)).split('|').let{parts->Instant.ofEpochMilli(parts[0].toLong()) to UUID.fromString(parts[1])}}.getOrElse{throw ApiException(ApiErrorCode.VALIDATION_ERROR,"cursor가 올바르지 않습니다.")}};private fun encodeCursor(at:Instant,id:UUID)=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("${at.toEpochMilli()}|$id".toByteArray())
}
