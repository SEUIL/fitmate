package com.fitmate.backend.mate.api

import com.fitmate.backend.mate.domain.*
import jakarta.validation.constraints.*
import java.time.Instant
import java.util.UUID

data class CreateMatePostRequest(@field:NotBlank @field:Size(max=100) val title:String, @field:NotBlank @field:Size(max=5000) val body:String, val sportCode:String, val regionId:UUID, val scheduledStartAt:Instant, @field:Positive val durationSeconds:Int?=null, @field:NotBlank @field:Size(max=255) val locationSummary:String, @field:Min(2) @field:Max(100) val capacity:Int=2, val difficulty:MateDifficulty, @field:PositiveOrZero val estimatedCostKrw:Int?=null)
data class UpdateMatePostRequest(@field:Min(0) val version:Long, @field:Size(max=100) val title:String?=null, @field:Size(max=5000) val body:String?=null, val sportCode:String?=null, val regionId:UUID?=null, val scheduledStartAt:Instant?=null, @field:Positive val durationSeconds:Int?=null, @field:Size(max=255) val locationSummary:String?=null, @field:Min(2) @field:Max(100) val capacity:Int?=null, val difficulty:MateDifficulty?=null, @field:PositiveOrZero val estimatedCostKrw:Int?=null)
data class VersionRequest(@field:Min(0) val version:Long)
data class MateUserSummary(val userId:UUID,val nickname:String?)
data class MatePostResponse(val id:UUID,val authorId:UUID,val author:MateUserSummary,val sportCode:String,val regionId:UUID,val title:String,val body:String,val scheduledStartAt:Instant,val durationSeconds:Int?,val locationSummary:String,val capacity:Int,val difficulty:MateDifficulty,val estimatedCostKrw:Int?,val acceptedCount:Int,val remainingSlots:Int,val status:MatePostStatus,val version:Long,val createdAt:Instant,val updatedAt:Instant)
data class MatePostDetailResponse(val id:UUID,val authorId:UUID,val authorNickname:String?,val author:MateUserSummary,val sportCode:String,val regionId:UUID,val title:String,val body:String,val scheduledStartAt:Instant,val durationSeconds:Int?,val locationSummary:String,val capacity:Int,val difficulty:MateDifficulty,val estimatedCostKrw:Int?,val acceptedCount:Int,val remainingSlots:Int,val status:MatePostStatus,val version:Long,val createdAt:Instant,val updatedAt:Instant,val myApplication:MateApplicationResponse?,val myInquiryRoomId:UUID?,val allowedActions:Set<String>)
data class MateApplicationResponse(val id:UUID,val matePostId:UUID,val userId:UUID,val applicant:MateUserSummary,val status:MateParticipationStatus,val statusReason:String?,val postStatus:MatePostStatus,val acceptedCount:Int,val appliedAt:Instant,val respondedAt:Instant?,val canceledAt:Instant?,val version:Long)
