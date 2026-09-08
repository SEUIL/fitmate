package com.fitmate.backend.matechat.domain

import com.fitmate.backend.common.persistence.BaseTimeEntity
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import com.fitmate.backend.mate.domain.MatePost
import jakarta.persistence.*
import java.util.UUID

@Entity @Table(name = "fitness_mate_chat_rooms")
class MateChatRoom(
    @Id @Convert(converter = UuidBinaryConverter::class) @Column(columnDefinition = "BINARY(16)") var id: UUID? = null,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "mate_post_id") var post: MatePost? = null,
    @Column(name = "inquirer_id", nullable = false, columnDefinition = "BINARY(16)") var inquirerId: UUID? = null,
    @Column(name = "last_message_sequence", nullable = false) var lastMessageSequence: Long = 0,
    @Column(name = "host_last_read_sequence", nullable = false) var hostLastReadSequence: Long = 0,
    @Column(name = "inquirer_last_read_sequence", nullable = false) var inquirerLastReadSequence: Long = 0,
    @Version @Column(nullable = false) var version: Long = 0,
) : BaseTimeEntity()

@Entity @Table(name = "fitness_mate_chat_messages")
class MateChatMessage(
    @Id @Convert(converter = UuidBinaryConverter::class) @Column(columnDefinition = "BINARY(16)") var id: UUID? = null,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "room_id") var room: MateChatRoom? = null,
    @Column(name = "sender_id", nullable = false, columnDefinition = "BINARY(16)") var senderId: UUID? = null,
    @Convert(converter = UuidBinaryConverter::class) @Column(name = "client_message_id", nullable = false, columnDefinition = "BINARY(16)") var clientMessageId: UUID? = null,
    @Column(name = "sequence_no", nullable = false) var sequenceNo: Long = 0, @Column(nullable = false, length = 2000) var body: String = "",
) : BaseTimeEntity()
