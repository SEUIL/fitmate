package com.fitmate.backend.mate.api

import com.fitmate.backend.auth.application.AuthenticatedUser
import com.fitmate.backend.common.api.*
import com.fitmate.backend.mate.application.MateService
import com.fitmate.backend.mate.application.IdempotencyCreateRaceException
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController @RequestMapping("/api/v1/mate-posts")
class MatePostController(private val service: MateService) {
 @GetMapping fun list(@AuthenticationPrincipal u:AuthenticatedUser,@RequestParam(required=false) sportCode:String?,@RequestParam(required=false) regionId:UUID?,@RequestParam(required=false) from:java.time.Instant?,@RequestParam(required=false) to:java.time.Instant?,@RequestParam(required=false) difficulty:com.fitmate.backend.mate.domain.MateDifficulty?,@RequestParam(required=false) status:com.fitmate.backend.mate.domain.MatePostStatus?,@RequestParam(required=false) cursor:String?,@RequestParam(defaultValue="20") size:Int): ApiResponse<CursorPageResponse<MatePostResponse>> = ApiResponse.success(service.list(u.userId,sportCode,regionId,from,to,difficulty,status,cursor,size))
 @GetMapping("/{postId}") fun get(@AuthenticationPrincipal u:AuthenticatedUser,@PathVariable postId:UUID):ApiResponse<MatePostDetailResponse> = ApiResponse.success(service.detail(u.userId,postId))
 @PostMapping fun create(@AuthenticationPrincipal u:AuthenticatedUser,@RequestHeader("Idempotency-Key") key:UUID,@Valid @RequestBody r:CreateMatePostRequest):org.springframework.http.ResponseEntity<ApiResponse<MatePostResponse>> { val response=try{service.create(u.userId,key,r)}catch(_:IdempotencyCreateRaceException){service.replayCreate(u.userId,key,r)};return org.springframework.http.ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response)) }
 @PatchMapping("/{postId}") fun update(@AuthenticationPrincipal u:AuthenticatedUser,@PathVariable postId:UUID,@Valid @RequestBody r:UpdateMatePostRequest):ApiResponse<MatePostResponse> = ApiResponse.success(service.update(u.userId,postId,r))
 @PostMapping("/{postId}/close") fun close(@AuthenticationPrincipal u:AuthenticatedUser,@PathVariable postId:UUID,@Valid @RequestBody r:VersionRequest):ApiResponse<MatePostResponse> = ApiResponse.success(service.close(u.userId,postId,r.version))
 @PostMapping("/{postId}/cancel") fun cancel(@AuthenticationPrincipal u:AuthenticatedUser,@PathVariable postId:UUID,@Valid @RequestBody r:VersionRequest):ApiResponse<MatePostResponse> = ApiResponse.success(service.cancel(u.userId,postId,r.version))
 @PostMapping("/{postId}/applications") @ResponseStatus(HttpStatus.CREATED) fun apply(@AuthenticationPrincipal u:AuthenticatedUser,@PathVariable postId:UUID):ApiResponse<MateApplicationResponse> = ApiResponse.success(service.apply(u.userId,postId))
 @GetMapping("/{postId}/applications/me") fun myApplication(@AuthenticationPrincipal u:AuthenticatedUser,@PathVariable postId:UUID):ApiResponse<MateApplicationResponse?> = ApiResponse.success(service.myApplication(u.userId,postId))
 @GetMapping("/{postId}/applications") fun applications(@AuthenticationPrincipal u:AuthenticatedUser,@PathVariable postId:UUID,@RequestParam(required=false) status:com.fitmate.backend.mate.domain.MateParticipationStatus?,@RequestParam(required=false) cursor:String?,@RequestParam(defaultValue="20") size:Int):ApiResponse<CursorPageResponse<MateApplicationResponse>> = ApiResponse.success(service.applications(u.userId,postId,status,cursor,size))
 @PostMapping("/{postId}/applications/{id}/accept") fun accept(@AuthenticationPrincipal u:AuthenticatedUser,@PathVariable postId:UUID,@PathVariable id:UUID,@Valid @RequestBody r:VersionRequest):ApiResponse<MateApplicationResponse> = ApiResponse.success(service.decide(u.userId,postId,id,r.version,true))
 @PostMapping("/{postId}/applications/{id}/reject") fun reject(@AuthenticationPrincipal u:AuthenticatedUser,@PathVariable postId:UUID,@PathVariable id:UUID,@Valid @RequestBody r:VersionRequest):ApiResponse<MateApplicationResponse> = ApiResponse.success(service.decide(u.userId,postId,id,r.version,false))
 @PostMapping("/{postId}/applications/{id}/cancel") fun withdraw(@AuthenticationPrincipal u:AuthenticatedUser,@PathVariable postId:UUID,@PathVariable id:UUID,@Valid @RequestBody r:VersionRequest):ApiResponse<MateApplicationResponse> = ApiResponse.success(service.withdraw(u.userId,postId,id,r.version))
}
