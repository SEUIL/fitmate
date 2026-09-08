package com.fitmate.backend.inbody.infrastructure.ai

import com.fitmate.backend.inbody.domain.InbodyAiProvider

data class InbodyAnalysisInput(
    val contentType: String,
    val bytes: ByteArray,
)

data class ProviderExtractedField(
    val code: String,
    val value: String?,
    val unit: String?,
    val rawText: String?,
)

data class InbodyProviderResult(
    val model: String,
    val fields: List<ProviderExtractedField>,
)

class InbodyProviderException(
    val failureCode: String,
    message: String,
    val retryable: Boolean = false,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

interface InbodyAnalyzer {
    val provider: InbodyAiProvider
    fun analyze(input: InbodyAnalysisInput): InbodyProviderResult
}
