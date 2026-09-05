package com.fitmate.backend.terms.repository

import com.fitmate.backend.terms.domain.TermsDocument
import com.fitmate.backend.terms.domain.UserConsent
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TermsDocumentRepository : JpaRepository<TermsDocument, UUID> {
    fun findAllByOrderByEffectiveAtDesc(): List<TermsDocument>
}

interface UserConsentRepository : JpaRepository<UserConsent, UUID> {
    fun findAllByUserIdOrderByConsentedAtDesc(userId: UUID): List<UserConsent>
    fun findByUserIdAndTermsDocumentId(userId: UUID, termsDocumentId: UUID): UserConsent?
}
