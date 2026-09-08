package com.fitmate.backend.matechat.repository

import com.fitmate.backend.matechat.domain.*
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.*
import org.springframework.data.repository.query.Param
import org.springframework.data.domain.Pageable
import java.time.Instant
import java.util.UUID

interface MateChatRoomRepository : JpaRepository<MateChatRoom, UUID> {
    fun findByPostIdAndInquirerId(postId: UUID, inquirerId: UUID): MateChatRoom?
    fun findAllByPostId(postId: UUID): List<MateChatRoom>
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from MateChatRoom r where r.id = :id") fun findLocked(@Param("id") id: UUID): MateChatRoom?
    @Query("""select r from MateChatRoom r where (r.inquirerId = :userId or r.post.authorId = :userId)
        and (:cursorAt is null or r.updatedAt < :cursorAt or (r.updatedAt = :cursorAt and r.id < :cursorId))
        order by r.updatedAt desc, r.id desc""")
    fun findPageForUser(
        @Param("userId") userId: UUID, @Param("cursorAt") cursorAt: Instant?,
        @Param("cursorId") cursorId: UUID?, pageable: Pageable,
    ): List<MateChatRoom>
}
interface MateChatMessageRepository : JpaRepository<MateChatMessage, UUID> {
    fun findByRoomIdAndSenderIdAndClientMessageId(roomId: UUID, senderId: UUID, clientMessageId: UUID): MateChatMessage?
    fun findAllByRoomIdOrderBySequenceNoAsc(roomId: UUID, pageable: Pageable): List<MateChatMessage>
    fun findAllByRoomIdAndSequenceNoGreaterThanOrderBySequenceNoAsc(roomId: UUID, sequenceNo: Long, pageable: Pageable): List<MateChatMessage>
    fun findAllByRoomIdAndSequenceNoLessThanOrderBySequenceNoDesc(roomId: UUID, sequenceNo: Long, pageable: Pageable): List<MateChatMessage>
    fun findFirstByRoomIdOrderBySequenceNoDesc(roomId: UUID): MateChatMessage?
    fun findByIdAndRoomId(id: UUID, roomId: UUID): MateChatMessage?
}
