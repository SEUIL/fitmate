package com.fitmate.backend.inbody.domain

import com.fitmate.backend.common.persistence.BaseTimeEntity
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class InbodyAnalysisStatus { UPLOADED, PROCESSING, COMPLETED, REVIEW_REQUIRED, FAILED }
enum class InbodyAiProvider { GEMINI, OPENAI, CLAUDE, MOCK }
enum class InbodyRecordSource { MANUAL, AI_OCR }

enum class InbodyFieldCode {
    MEASUREMENT_DATE,
    HEIGHT_CM,
    WEIGHT_KG,
    SKELETAL_MUSCLE_MASS_KG,
    BODY_FAT_MASS_KG,
    BODY_FAT_PERCENT,
    BMI,
    FAT_FREE_MASS_KG,
    BODY_WATER_L,
    PROTEIN_KG,
    MINERALS_KG,
    OBESITY_DEGREE_PERCENT,
    WAIST_HIP_RATIO,
    VISCERAL_FAT_LEVEL,
    BASAL_METABOLIC_RATE_KCAL,
    INBODY_SCORE,
    RECOMMENDED_WEIGHT_KG,
    WEIGHT_CONTROL_KG,
    LEFT_ARM_MUSCLE_KG,
    RIGHT_ARM_MUSCLE_KG,
    TRUNK_MUSCLE_KG,
    LEFT_LEG_MUSCLE_KG,
    RIGHT_LEG_MUSCLE_KG,
    LEFT_ARM_FAT_KG,
    RIGHT_ARM_FAT_KG,
    TRUNK_FAT_KG,
    LEFT_LEG_FAT_KG,
    RIGHT_LEG_FAT_KG,
}

enum class InbodyFieldStatus { RECOGNIZED, REVIEW_REQUIRED, NOT_RECOGNIZED }

data class InbodyExtractedField(
    val code: InbodyFieldCode,
    val value: String?,
    val unit: String?,
    val rawText: String?,
    val status: InbodyFieldStatus,
    val issueCode: String? = null,
)

@Entity
@Table(name = "health_inbody_analyses")
class InbodyAnalysis(
    @Id @Convert(converter = UuidBinaryConverter::class) @Column(columnDefinition = "BINARY(16)")
    var id: UUID? = null,
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    var userId: UUID? = null,
    @Column(name = "media_id", nullable = false, columnDefinition = "BINARY(16)")
    var mediaId: UUID? = null,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    var status: InbodyAnalysisStatus = InbodyAnalysisStatus.UPLOADED,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    var provider: InbodyAiProvider = InbodyAiProvider.MOCK,
    @Column(name = "provider_model", length = 100) var providerModel: String? = null,
    @Column(name = "result_json", columnDefinition = "MEDIUMTEXT") var resultJson: String? = null,
    @Column(name = "failure_code", length = 64) var failureCode: String? = null,
    @Column(name = "failure_message", length = 500) var failureMessage: String? = null,
    @Column(name = "attempt_count", nullable = false) var attemptCount: Int = 0,
    @Column(name = "completed_at") var completedAt: Instant? = null,
    @Version @Column(nullable = false) var version: Long = 0,
) : BaseTimeEntity()

@Entity
@Table(name = "health_inbody_records")
class InbodyRecord(
    @Id @Convert(converter = UuidBinaryConverter::class) @Column(columnDefinition = "BINARY(16)")
    var id: UUID? = null,
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    var userId: UUID? = null,
    @Column(name = "analysis_id", columnDefinition = "BINARY(16)") var analysisId: UUID? = null,
    @Enumerated(EnumType.STRING) @Column(name = "source_type", nullable = false, length = 32)
    var sourceType: InbodyRecordSource = InbodyRecordSource.MANUAL,
    @Column(name = "measured_date", nullable = false) var measuredDate: LocalDate = LocalDate.ofEpochDay(0),
    @Column(name = "weight_kg", nullable = false, precision = 6, scale = 2) var weightKg: BigDecimal = BigDecimal.ONE,
    @Column(name = "skeletal_muscle_mass_kg", nullable = false, precision = 6, scale = 2)
    var skeletalMuscleMassKg: BigDecimal = BigDecimal.ZERO,
    @Column(name = "body_fat_percentage", nullable = false, precision = 5, scale = 2)
    var bodyFatPercentage: BigDecimal = BigDecimal.ZERO,
    @Column(name = "optional_values_json", columnDefinition = "MEDIUMTEXT") var optionalValuesJson: String? = null,
    @Version @Column(nullable = false) var version: Long = 0,
) : BaseTimeEntity()
