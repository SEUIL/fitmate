package com.fitmate.backend.matechat.api
import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.api.*
import com.fitmate.backend.matechat.application.MateChatService
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID
data class SendMateMessageRequest(val clientMessageId:UUID,@field:NotBlank @field:Size(max=2000) val body:String)
data class ReadMateRoomRequest(val lastReadMessageId:UUID)
data class MateUserSummary(val userId:UUID,val nickname:String?)
data class MateRoomResponse(val id:UUID,val postId:UUID,val author:MateUserSummary,val inquirer:MateUserSummary,val status:String,val writable:Boolean,val readOnlyReason:String?,val lastMessageSequence:Long,val hostLastReadSequence:Long,val inquirerLastReadSequence:Long,val myLastReadSequence:Long,val unreadCount:Long,val lastMessage:MateMessageResponse?)
data class MateMessageResponse(val id:UUID,val roomId:UUID,val senderId:UUID,val clientMessageId:UUID,val sequenceNo:Long,val body:String,val status:String="SENT",val createdAt:Instant)
@RestController class MateChatController(private val service:MateChatService){
 @PostMapping("/api/v1/mate-posts/{postId}/inquiry-room") fun room(@AuthenticationPrincipal u:AuthenticatedUser,@PathVariable postId:UUID):org.springframework.http.ResponseEntity<ApiResponse<MateRoomResponse>> {val r=service.room(u.userId,postId);return org.springframework.http.ResponseEntity.status(if(r.second)HttpStatus.CREATED else HttpStatus.OK).body(ApiResponse.success(r.first))}
 @GetMapping("/api/v1/mate-chat-rooms") fun rooms(@AuthenticationPrincipal u:AuthenticatedUser,@RequestParam(required=false) cursor:String?,@RequestParam(defaultValue="20") size:Int)=ApiResponse.success(service.rooms(u.userId,cursor,size))
 @GetMapping("/api/v1/mate-chat-rooms/{id}") fun get(@AuthenticationPrincipal u:AuthenticatedUser,@PathVariable id:UUID)=ApiResponse.success(service.get(u.userId,id))
 @GetMapping("/api/v1/mate-chat-rooms/{id}/messages") fun messages(@AuthenticationPrincipal u:AuthenticatedUser,@PathVariable id:UUID,@RequestParam(required=false) beforeSequence:Long?,@RequestParam(required=false) afterSequence:Long?,@RequestParam(defaultValue="20") size:Int)=ApiResponse.success(ItemListResponse(service.messages(u.userId,id,beforeSequence,afterSequence,size)))
 @PostMapping("/api/v1/mate-chat-rooms/{id}/messages") fun send(@AuthenticationPrincipal u:AuthenticatedUser,@PathVariable id:UUID,@Valid @RequestBody r:SendMateMessageRequest):org.springframework.http.ResponseEntity<ApiResponse<MateMessageResponse>> {val x=service.send(u.userId,id,r);return org.springframework.http.ResponseEntity.status(if(x.second)HttpStatus.CREATED else HttpStatus.OK).body(ApiResponse.success(x.first))}
 @PutMapping("/api/v1/mate-chat-rooms/{id}/read") fun read(@AuthenticationPrincipal u:AuthenticatedUser,@PathVariable id:UUID,@Valid @RequestBody r:ReadMateRoomRequest)=ApiResponse.success(service.read(u.userId,id,r.lastReadMessageId))
}
