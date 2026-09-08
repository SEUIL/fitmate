package com.fitmate.backend.inbody.application

import com.fitmate.backend.inbody.infrastructure.ai.InbodyAnalysisInput
import com.fitmate.backend.inbody.infrastructure.ai.InbodyProviderException
import com.fitmate.backend.media.application.MediaAssetService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class InbodyAnalysisProcessor(
    private val analysisService: InbodyAnalysisService,
    private val mediaAssets: MediaAssetService,
    private val normalizer: InbodyFieldNormalizer,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun process(event: InbodyAnalysisRequested) {
        val work = analysisService.markProcessing(event.analysisId) ?: return
        try {
            val media = mediaAssets.openForOwner(work.userId, work.mediaId)
            val bytes = media.resource.inputStream.use { it.readBytes() }
            val result = analysisService.analyzer(work.provider).analyze(InbodyAnalysisInput(media.contentType, bytes))
            analysisService.complete(work.analysisId, result.model, normalizer.normalize(result.fields))
            runCatching { mediaAssets.delete(work.userId, work.mediaId) }
                .onFailure { logger.warn("Completed InBody analysis but failed to delete source image: {}", work.analysisId, it) }
        } catch (exception: InbodyProviderException) {
            logger.warn("InBody provider failed: analysisId={}, code={}", work.analysisId, exception.failureCode)
            analysisService.fail(work.analysisId, exception.failureCode, exception.message ?: "AI 판독에 실패했습니다.")
        } catch (exception: Exception) {
            logger.error("Unexpected InBody analysis failure: analysisId={}", work.analysisId, exception)
            analysisService.fail(work.analysisId, "ANALYSIS_FAILED", "인바디 이미지를 판독하지 못했습니다.")
        }
    }
}
