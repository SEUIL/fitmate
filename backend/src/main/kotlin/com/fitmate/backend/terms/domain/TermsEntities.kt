package com.fitmate.backend.terms.domain

import com.fitmate.backend.common.persistence.BaseTimeEntity
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class TermsType { SERVICE_TERMS, PRIVACY }

@Entity
@Table(name = "core_terms_documents")
class TermsDocument(
    @Id @Convert(converter = UuidBinaryConverter::class) @Column(name = "id", columnDefinition = "BINARY(16)") var id: UUID? = null,
    @Enumerated(EnumType.STRING) @Column(name = "type", nullable = false, length = 32) var type: TermsType = TermsType.SERVICE_TERMS,
    @Column(name = "version", nullable = false, length = 32) var version: String = "",
    @Column(name = "content_uri", nullable = false, length = 512) var contentUri: String = "",
    @Column(name = "effective_at", nullable = false) var effectiveAt: Instant = Instant.EPOCH,
    @Column(name = "is_required", nullable = false) var isRequired: Boolean = false,
) : BaseTimeEntity()

@Entity
@Table(name = "core_user_consents")
class UserConsent(
    @Id @Convert(converter = UuidBinaryConverter::class) @Column(name = "id", columnDefinition = "BINARY(16)") var id: UUID? = null,
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)") var userId: UUID? = null,
    @Column(name = "terms_document_id", nullable = false, columnDefinition = "BINARY(16)") var termsDocumentId: UUID? = null,
    @Column(name = "consented_at", nullable = false) var consentedAt: Instant = Instant.EPOCH,
    @Column(name = "withdrawn_at") var withdrawnAt: Instant? = null,
) : BaseTimeEntity()
