package com.fitmate.backend.admin.domain

import com.fitmate.backend.common.persistence.BaseTimeEntity
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class NoticeStatus { DRAFT, PUBLISHED, ENDED }

@Entity
@Table(name = "ops_notices")
class AdminNotice(
    @Id @Convert(converter = UuidBinaryConverter::class) @Column(columnDefinition = "BINARY(16)") var id: UUID? = null,
    @Column(nullable = false, length = 100) var title: String = "",
    @Column(nullable = false, columnDefinition = "TEXT") var body: String = "",
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) var status: NoticeStatus = NoticeStatus.DRAFT,
    @Column(name = "published_at") var publishedAt: Instant? = null,
    @Column(name = "ended_at") var endedAt: Instant? = null,
    @Column(name = "created_by", nullable = false, columnDefinition = "BINARY(16)") var createdBy: UUID? = null,
    @Column(name = "updated_by", nullable = false, columnDefinition = "BINARY(16)") var updatedBy: UUID? = null,
    @Version @Column(nullable = false) var version: Long = 0,
) : BaseTimeEntity()

@Entity
@Table(name = "ops_category_settings")
class AdminCategorySettings(@Id var id: Int = 1, @Column(nullable = false) var version: Long = 0)
