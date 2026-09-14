package com.fitmate.backend.report.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import com.fitmate.backend.fitness.domain.SportCode
import com.fitmate.backend.report.api.*
import com.fitmate.backend.report.infrastructure.*
import com.fitmate.backend.workout.domain.WorkoutSessionStatus
import com.fitmate.backend.workout.repository.WorkoutSessionRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

@Service
class WorkoutInsightService(
    private val insights: WorkoutInsightRepository,
    private val sessions: WorkoutSessionRepository,
    private val reader: WorkoutInsightReader,
    private val rules: WorkoutInsightRules,
    private val ai: WorkoutInsightAi,
    private val mapper: ObjectMapper,
    transactionManager: PlatformTransactionManager,
    @Qualifier("workoutInsightExecutor") private val executor: Executor,
) {
    private val tx = TransactionTemplate(transactionManager)
    fun options(user: UUID, session: UUID): Map<String, Any> = requireNotNull(tx.execute {
        checkSession(user, session)
        mapOf("aiAvailable" to ai.available(), "consentVersion" to WorkoutInsightConsent.VERSION,
            "consentNotice" to WorkoutInsightConsent.NOTICE, "samsungHealthAvailable" to reader.hasSamsungSummary(user, session))
    })

    fun create(user: UUID, session: UUID, request: CreateWorkoutInsightRequest): WorkoutInsightResponse {
        if (request.externalAiConsent && request.consentVersion != WorkoutInsightConsent.VERSION)
            throw ApiException(ApiErrorCode.VALIDATION_ERROR, "리포트 전송 안내를 확인하고 다시 동의해 주세요.")
        var launch: WorkoutInsightEntity? = null
        val response = requireNotNull(tx.execute {
            checkSession(user, session)
            val previous = insights.findById(request.requestId.toString()).orElse(null)
            if (previous != null) {
                if (previous.ownerUserId != user || previous.sessionId != dbId(session)) notFound()
                if (mapper.readValue<CreateWorkoutInsightRequest>(previous.requestJson) != request) throw ApiException(ApiErrorCode.IDEMPOTENCY_KEY_CONFLICT)
                expire(previous)
                return@execute response(previous)
            }
            val latest = insights.findFirstByOwnerUserIdAndSessionIdOrderByCreatedAtDescIdDesc(user, dbId(session))
            latest?.let { expire(it) }
            if (latest?.status == "GENERATING") throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION, "이미 이 운동을 분석 중입니다. 현재 리포트를 확인해 주세요.")
            val snapshot = reader.read(user, session, request)
            val token = UUID.randomUUID().toString()
            insights.insertIfAbsent(request.requestId.toString(), user, dbId(session), mapper.writeValueAsString(request), mapper.writeValueAsString(snapshot), token)
            val entity = owned(user, session, request.requestId)
            if (entity.generationToken != token) throw ApiException(ApiErrorCode.IDEMPOTENCY_KEY_CONFLICT)
            launch = entity
            response(entity)
        })
        launch?.let { entity ->
            val token = entity.generationToken
            try { executor.execute { generate(user, session, request.requestId, token) } }
            catch (_: RejectedExecutionException) { finish(user, session, request.requestId, token, null, "요청이 많아 분석을 시작하지 못했어요. 기본 기록은 그대로 확인할 수 있어요.") }
        }
        return response
    }

    fun latest(user: UUID, session: UUID): WorkoutInsightResponse? = tx.execute {
        checkSession(user, session)
        insights.findFirstByOwnerUserIdAndSessionIdOrderByCreatedAtDescIdDesc(user, dbId(session))?.let { expire(it); response(it) }
    }

    fun get(user: UUID, session: UUID, id: UUID): WorkoutInsightResponse = requireNotNull(tx.execute {
        checkSession(user, session)
        val entity = owned(user, session, id); expire(entity); response(entity)
    })

    fun cancel(user: UUID, session: UUID, id: UUID): WorkoutInsightResponse = requireNotNull(tx.execute {
        checkSession(user, session)
        val entity = owned(user, session, id)
        if (entity.status == "GENERATING") {
            entity.status = "CANCELED"; entity.message = "분석을 취소했어요. 이미 전송한 요청은 회수할 수 없습니다."
            entity.updatedAt = Instant.now()
        }
        response(entity)
    })

    private fun generate(user: UUID, session: UUID, id: UUID, token: String) {
        try {
            // Only detached immutable DTOs cross the provider boundary; no open transaction during HTTP.
            val entity = insights.findByIdAndOwnerUserIdAndSessionId(id.toString(), user, dbId(session)) ?: return
            if (entity.status != "GENERATING" || entity.generationToken != token) return
            val snapshot = mapper.readValue<WorkoutInsightSnapshot>(entity.snapshotJson)
            val request = mapper.readValue<CreateWorkoutInsightRequest>(entity.requestJson)
            val catalog = rules.catalog(snapshot)
            val selected = if (request.externalAiConsent && ai.available()) {
                try { ai.select(snapshot, catalog, request) } catch (_: Exception) { null }
            } else null
            val accepted = selected?.let { try { rules.result(snapshot, catalog, it, "AI") } catch (_: IllegalArgumentException) { null } }
            val result = accepted ?: rules.result(snapshot, catalog, rules.fallback(catalog), "RULE_BASED")
            finish(user, session, id, token, result, if (accepted == null && request.externalAiConsent) "AI 분석을 사용할 수 없어 기록 기반 기본 해석을 제공해요. 다시 요청할 수 있어요." else null)
        } catch (_: Exception) {
            finish(user, session, id, token, null, "분석을 완료하지 못했어요. 기본 기록은 유지되며 다시 요청할 수 있어요.")
        }
    }

    private fun finish(user: UUID, session: UUID, id: UUID, token: String, result: WorkoutInsightResult?, message: String?) {
        tx.executeWithoutResult {
            checkSession(user, session)
            val entity = insights.lockOwned(id.toString(), user, dbId(session)) ?: return@executeWithoutResult
            expire(entity)
            if (entity.status != "GENERATING" || entity.generationToken != token) return@executeWithoutResult
            entity.status = if (result == null) "FAILED" else "READY"
            entity.resultJson = result?.let(mapper::writeValueAsString); entity.message = message; entity.updatedAt = Instant.now()
        }
    }

    private fun checkSession(user: UUID, session: UUID) {
        val entity = sessions.findByIdAndUserIdForUpdate(dbId(session), user) ?: notFound()
        if (entity.status != WorkoutSessionStatus.COMPLETED || entity.sportCode != SportCode.HEALTH)
            throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION, "완료한 헬스 운동만 분석할 수 있습니다.")
    }
    private fun owned(user: UUID, session: UUID, id: UUID) = insights.lockOwned(id.toString(), user, dbId(session)) ?: notFound()
    private fun expire(entity: WorkoutInsightEntity) {
        if (entity.status == "GENERATING" && entity.updatedAt.isBefore(Instant.now().minusSeconds(120))) {
            entity.status = "FAILED"; entity.message = "분석 대기 시간이 초과됐어요. 다시 요청해 주세요."; entity.updatedAt = Instant.now()
        }
    }
    private fun response(entity: WorkoutInsightEntity) = WorkoutInsightResponse(UUID.fromString(entity.id), entity.status, entity.createdAt,
        entity.resultJson?.let { mapper.readValue<WorkoutInsightResult>(it) }, entity.message)
    private fun dbId(id: UUID) = UuidBinaryConverter.toMysqlOrderedUuid(id)
    private fun notFound(): Nothing = throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "내 운동 리포트를 찾을 수 없습니다.")
}
