package com.fitmate.backend.inbody.application

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.inbody.api.InbodyAnalysisFailureResponse
import com.fitmate.backend.inbody.api.InbodyAnalysisFieldResponse
import com.fitmate.backend.inbody.api.InbodyAnalysisResponse
import com.fitmate.backend.inbody.domain.InbodyAiProvider
import com.fitmate.backend.inbody.domain.InbodyAnalysis
import com.fitmate.backend.inbody.domain.InbodyAnalysisStatus
import com.fitmate.backend.inbody.domain.InbodyExtractedField
import com.fitmate.backend.inbody.domain.InbodyFieldCode
import com.fitmate.backend.inbody.domain.InbodyFieldStatus
import com.fitmate.backend.inbody.infrastructure.ai.InbodyAnalyzerRouter
import com.fitmate.backend.inbody.repository.InbodyAnalysisRepository
import com.fitmate.backend.media.application.MediaAssetService
import com.fitmate.backend.media.domain.MediaPurpose
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class InbodyAnalysisRequested(val analysisId: UUID)

data class InbodyAnalysisWork(
    val analysisId: UUID,
    val userId: UUID,
    val mediaId: UUID,
    val provider: InbodyAiProvider,
)

@Service
class InbodyAnalysisService(
    private val analyses: InbodyAnalysisRepository,
    private val mediaAssets: MediaAssetService,
    private val analyzerRouter: InbodyAnalyzerRouter,
    private val events: ApplicationEventPublisher,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun create(userId: UUID, mediaId: UUID): InbodyAnalysisResponse {
        mediaAssets.requireOwnedActive(userId, mediaId, MediaPurpose.INBODY)
        val analyzer = analyzerRouter.current()
        val saved = analyses.saveAndFlush(InbodyAnalysis(
            id = UUID.randomUUID(),
            userId = userId,
            mediaId = mediaId,
            provider = analyzer.provider,
        ))
        events.publishEvent(InbodyAnalysisRequested(requireNotNull(saved.id)))
        return response(saved)
    }

    @Transactional(readOnly = true)
    fun get(userId: UUID, analysisId: UUID): InbodyAnalysisResponse = response(owned(userId, analysisId))

    @Transactional
    fun retry(userId: UUID, analysisId: UUID): InbodyAnalysisResponse {
        val analysis = owned(userId, analysisId)
        if (analysis.status != InbodyAnalysisStatus.FAILED) {
            throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION, "실패한 판독만 다시 시도할 수 있습니다.")
        }
        mediaAssets.requireOwnedActive(userId, requireNotNull(analysis.mediaId), MediaPurpose.INBODY)
        analysis.status = InbodyAnalysisStatus.UPLOADED
        analysis.failureCode = null
        analysis.failureMessage = null
        val saved = analyses.saveAndFlush(analysis)
        events.publishEvent(InbodyAnalysisRequested(requireNotNull(saved.id)))
        return response(saved)
    }

    @Transactional
    fun markProcessing(analysisId: UUID): InbodyAnalysisWork? {
        val analysis = analyses.findById(analysisId).orElse(null) ?: return null
        if (analysis.status != InbodyAnalysisStatus.UPLOADED) return null
        analysis.status = InbodyAnalysisStatus.PROCESSING
        analysis.attemptCount += 1
        analysis.failureCode = null
        analysis.failureMessage = null
        val saved = analyses.saveAndFlush(analysis)
        return InbodyAnalysisWork(
            analysisId = requireNotNull(saved.id),
            userId = requireNotNull(saved.userId),
            mediaId = requireNotNull(saved.mediaId),
            provider = saved.provider,
        )
    }

    @Transactional
    fun complete(analysisId: UUID, model: String, fields: List<InbodyExtractedField>) {
        val analysis = analyses.findById(analysisId).orElse(null) ?: return
        if (analysis.status != InbodyAnalysisStatus.PROCESSING) return
        analysis.providerModel = model.take(100)
        analysis.resultJson = objectMapper.writeValueAsString(fields)
        analysis.status = if (requiresReview(fields)) {
            InbodyAnalysisStatus.REVIEW_REQUIRED
        } else {
            InbodyAnalysisStatus.COMPLETED
        }
        analysis.completedAt = Instant.now()
        analyses.saveAndFlush(analysis)
    }

    @Transactional
    fun fail(analysisId: UUID, code: String, message: String) {
        val analysis = analyses.findById(analysisId).orElse(null) ?: return
        if (analysis.status != InbodyAnalysisStatus.PROCESSING) return
        analysis.status = InbodyAnalysisStatus.FAILED
        analysis.failureCode = code.take(64)
        analysis.failureMessage = message.take(500)
        analyses.saveAndFlush(analysis)
    }

    fun analyzer(provider: InbodyAiProvider) = analyzerRouter.forProvider(provider)

    private fun owned(userId: UUID, analysisId: UUID): InbodyAnalysis =
        analyses.findByIdAndUserId(analysisId, userId)
            ?: throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "인바디 판독 내역을 찾을 수 없습니다.")

    private fun response(analysis: InbodyAnalysis): InbodyAnalysisResponse {
        val fields = analysis.resultJson?.let {
            objectMapper.readValue(it, object : TypeReference<List<InbodyExtractedField>>() {})
        }.orEmpty()
        return InbodyAnalysisResponse(
            id = requireNotNull(analysis.id),
            mediaId = requireNotNull(analysis.mediaId),
            status = analysis.status,
            provider = analysis.provider,
            providerModel = analysis.providerModel,
            fields = fields.map { field -> InbodyAnalysisFieldResponse(
                code = field.code,
                value = field.value,
                unit = field.unit,
                rawText = field.rawText,
                status = field.status,
                issueCode = field.issueCode,
            ) },
            failure = analysis.failureCode?.let { InbodyAnalysisFailureResponse(it, analysis.failureMessage.orEmpty()) },
            attemptCount = analysis.attemptCount,
            completedAt = analysis.completedAt,
            version = analysis.version,
        )
    }

    private fun requiresReview(fields: List<InbodyExtractedField>): Boolean {
        val required = setOf(
            InbodyFieldCode.MEASUREMENT_DATE,
            InbodyFieldCode.WEIGHT_KG,
            InbodyFieldCode.SKELETAL_MUSCLE_MASS_KG,
            InbodyFieldCode.BODY_FAT_PERCENT,
        )
        return fields.any { it.status == InbodyFieldStatus.REVIEW_REQUIRED } ||
            fields.filter { it.code in required }.any { it.status != InbodyFieldStatus.RECOGNIZED }
    }
}
