package com.fitmate.backend.recommendation.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.common.persistence.UuidBinaryConverter
import com.fitmate.backend.fitness.domain.SportCode
import com.fitmate.backend.recommendation.api.*
import com.fitmate.backend.recommendation.infrastructure.*
import com.fitmate.backend.routine.api.CreateRoutineRequest
import com.fitmate.backend.routine.api.RoutineItemWriteRequest
import com.fitmate.backend.routine.application.RoutineCommandService
import com.fitmate.backend.routine.application.RoutineQueryService
import com.fitmate.backend.routine.domain.RoutineSourceType
import com.fitmate.backend.routine.repository.RoutineRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

@Service
class WorkoutPlanService(
    private val plans: WorkoutPlanRepository,
    private val reader: WorkoutPlanContextReader,
    private val rules: WorkoutPlanRules,
    private val proposer: WorkoutPlanProposer,
    private val mapper: ObjectMapper,
    transactionManager: PlatformTransactionManager,
    @Qualifier("workoutPlanExecutor") private val executor: Executor,
    private val commands: RoutineCommandService,
    private val queries: RoutineQueryService,
    private val routines: RoutineRepository,
) {
    private val tx = TransactionTemplate(transactionManager)

    fun options(userId: UUID): PlanOptions {
        val context = reader.read(userId, CreateWorkoutPlanRequest(UUID.randomUUID(), 1, 20))
        return PlanOptions(context.goal, context.skillLevel, context.catalog, proposer.available(),
            OpenAiWorkoutPlanProposer.CONSENT_VERSION, OpenAiWorkoutPlanProposer.CONSENT_NOTICE)
    }

    fun create(userId: UUID, request: CreateWorkoutPlanRequest): WorkoutPlanResponse {
        if (request.externalAiConsent && request.consentVersion != OpenAiWorkoutPlanProposer.CONSENT_VERSION)
            throw ApiException(ApiErrorCode.VALIDATION_ERROR, "현재 AI 전송 안내를 확인하고 다시 동의해 주세요.")
        val context = reader.read(userId, request)
        rules.checkConditions(context)
        var token: String? = null
        val response = try {
            requireNotNull(tx.execute {
                val generation = UUID.randomUUID().toString()
                // Insert without merging: even a racing request ID cannot overwrite another owner's row.
                plans.insertIfAbsent(request.requestId.toString(), userId, mapper.writeValueAsString(request), generation)
                val entity = owned(userId, request.requestId)
                if (entity.generationToken == generation) {
                    token = generation
                } else {
                    if (mapper.readValue<CreateWorkoutPlanRequest>(entity.requestJson) != request) throw ApiException(ApiErrorCode.IDEMPOTENCY_KEY_CONFLICT)
                    expire(entity)
                    if (entity.status == "FAILED") {
                        entity.status = "GENERATING"; entity.message = null; entity.generationToken = UUID.randomUUID().toString()
                        entity.updatedAt = Instant.now(); token = entity.generationToken
                    }
                }
                plans.flush()
                response(entity)
            })
        } catch (_: DataIntegrityViolationException) {
            // A concurrent request won the unique request ID. Never launch a second provider call.
            val existing = plans.findByIdAndOwnerUserId(request.requestId.toString(), userId) ?: notFound()
            if (mapper.readValue<CreateWorkoutPlanRequest>(existing.requestJson) != request) throw ApiException(ApiErrorCode.IDEMPOTENCY_KEY_CONFLICT)
            return response(existing)
        }
        token?.let { generation ->
            try { executor.execute { generate(userId, request.requestId, generation, context) } }
            catch (_: RejectedExecutionException) { finish(userId, request.requestId, generation, null, "추천 요청이 많습니다. 잠시 후 같은 조건으로 재시도해 주세요.") }
        }
        return response
    }

    private fun generate(userId: UUID, id: UUID, token: String, context: WorkoutPlanContext) {
        try {
            // No transaction or JPA entity crosses the external request boundary.
            val active = plans.findByIdAndOwnerUserId(id.toString(), userId) ?: return
            if (active.status != "GENERATING" || active.generationToken != token) return
            val proposal = if (context.conditions.externalAiConsent) {
                try { proposer.propose(context) } catch (_: Exception) { null }
            } else null
            val accepted = proposal?.let {
                try { rules.result(context, it, "AI") }
                catch (_: ApiException) { null }
            }
            val result = accepted ?: rules.result(context, rules.fallback(context), "RULE_BASED",
                if (context.conditions.externalAiConsent) "AI 구성을 사용할 수 없어 동일 조건을 검증한 기본 추천으로 대체했습니다."
                else "외부 AI 전송 없이 기본 추천을 구성했습니다.")
            finish(userId, id, token, result, null)
        } catch (failure: ApiException) {
            finish(userId, id, token, null, failure.message)
        } catch (_: Exception) {
            finish(userId, id, token, null, "추천을 완료하지 못했습니다. 조건은 유지되며 다시 시도할 수 있습니다.")
        }
    }

    private fun finish(userId: UUID, id: UUID, token: String, result: WorkoutPlanResult?, message: String?) {
        tx.executeWithoutResult {
            val entity = plans.lockOwned(id.toString(), userId) ?: return@executeWithoutResult
            expire(entity)
            if (entity.status != "GENERATING" || entity.generationToken != token) return@executeWithoutResult
            entity.status = if (result == null) "FAILED" else "READY"
            entity.resultJson = result?.let(mapper::writeValueAsString)
            entity.message = message; entity.updatedAt = Instant.now()
        }
    }

    fun get(userId: UUID, id: UUID): WorkoutPlanResponse = requireNotNull(tx.execute {
        val entity = owned(userId, id); expire(entity); plans.flush(); response(entity)
    })

    fun cancel(userId: UUID, id: UUID): WorkoutPlanResponse = requireNotNull(tx.execute {
        val entity = owned(userId, id)
        if (entity.status == "SAVED") throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION, "이미 저장한 추천은 취소할 수 없습니다.")
        entity.status = "CANCELED"; entity.resultJson = null; entity.message = "추천을 취소했습니다."
        entity.updatedAt = Instant.now(); plans.flush(); response(entity)
    })

    fun alternatives(userId: UUID, id: UUID, day: Int, exerciseId: UUID): List<PlanAlternative> {
        val snapshot = get(userId, id)
        if (snapshot.status != "READY") throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION)
        val result = snapshot.result ?: notFound()
        val old = result.days.firstOrNull { it.day == day }?.exercises?.firstOrNull { it.exerciseId == exerciseId } ?: notFound()
        val context = currentContext(userId, result)
        return rules.eligible(context).filter { it.id != old.exerciseId && it.bodyPart == old.bodyPart }.mapNotNull { candidate ->
            val proposal = replace(rules.proposal(result), day, exerciseId, candidate.id)
            try {
                val revised = rules.validate(context, proposal).first { it.day == day }
                PlanAlternative(candidate, revised.estimatedSeconds, revised.estimatedSeconds - result.days.first { it.day == day }.estimatedSeconds)
            } catch (_: ApiException) { null }
        }
    }

    fun replace(userId: UUID, id: UUID, request: ReplacePlanExerciseRequest): WorkoutPlanResponse = requireNotNull(tx.execute {
        val entity = owned(userId, id)
        checkVersion(entity, request.version)
        if (entity.status != "READY") throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION)
        val original = result(entity)
        val context = currentContext(userId, original)
        val old = original.days.firstOrNull { it.day == request.day }?.exercises?.firstOrNull { it.exerciseId == request.exerciseId } ?: notFound()
        val candidate = rules.eligible(context).firstOrNull { it.id == request.replacementId && it.bodyPart == old.bodyPart && it.id != old.exerciseId }
            ?: throw ApiException(ApiErrorCode.VALIDATION_ERROR, "같은 운동 부위와 허용 기구를 만족하는 대체 운동을 선택해 주세요.")
        val proposal = replace(rules.proposal(original), request.day, request.exerciseId, candidate.id)
        val updated = original.copy(days = rules.validate(context, proposal), reasons = (original.reasons + "${request.day}일차 운동을 사용자가 교체하고 서버가 다시 검증했습니다.").distinct())
        entity.resultJson = mapper.writeValueAsString(updated); entity.updatedAt = Instant.now(); plans.flush(); response(entity)
    })

    fun save(userId: UUID, id: UUID, version: Long): WorkoutPlanResponse = requireNotNull(tx.execute {
        val entity = owned(userId, id)
        if (entity.status == "SAVED") {
            queries.getDetail(userId, requireNotNull(entity.routineId))
            return@execute response(entity)
        }
        checkVersion(entity, version)
        if (entity.status != "READY") throw ApiException(ApiErrorCode.INVALID_STATE_TRANSITION)
        val result = result(entity)
        val context = currentContext(userId, result)
        rules.validate(context, rules.proposal(result))
        var sequence = 0
        val detail = commands.create(userId, CreateRoutineRequest(result.name, RoutineSourceType.MANUAL, SportCode.HEALTH,
            splitCount = result.days.size, items = result.days.flatMap { day -> day.exercises.map {
                RoutineItemWriteRequest(it.exerciseId, ++sequence, day.day, it.sets, it.reps, it.reps, null,
                    if (rules.beginner(context)) 6 else 7, it.restSeconds)
            } }))
        val routine = routines.findByIdAndIsActiveTrue(UuidBinaryConverter.toMysqlOrderedUuid(detail.id)) ?: notFound()
        routine.recommendationJson = entity.resultJson
        routine.targetGoalCode = result.goal
        routine.targetLevel = result.skillLevel
        routine.difficulty = when {
            rules.beginner(context) -> com.fitmate.backend.routine.domain.DifficultyLevel.BEGINNER
            result.skillLevel == com.fitmate.backend.member.domain.SkillLevel.SIX_MONTHS_TO_ONE_YEAR -> com.fitmate.backend.routine.domain.DifficultyLevel.INTERMEDIATE
            else -> com.fitmate.backend.routine.domain.DifficultyLevel.ADVANCED
        }
        routines.flush()
        entity.routineId = detail.id; entity.status = "SAVED"; entity.updatedAt = Instant.now(); plans.flush(); response(entity)
    })

    private fun currentContext(userId: UUID, result: WorkoutPlanResult): WorkoutPlanContext = reader.read(userId, result.conditions)
        .copy(goal = result.goal, skillLevel = result.skillLevel, measuredDate = result.inbodyMeasuredDate)
    private fun replace(proposal: ProposedWorkoutPlan, day: Int, old: UUID, replacement: UUID) = ProposedWorkoutPlan(proposal.days.map {
        if (it.day != day) it else it.copy(exercises = it.exercises.map { item -> if (item.exerciseId != old) item else item.copy(exerciseId = replacement) })
    })
    private fun expire(entity: WorkoutPlanEntity) {
        if (entity.status == "GENERATING" && entity.updatedAt.isBefore(Instant.now().minusSeconds(120))) {
            entity.status = "FAILED"; entity.generationToken = UUID.randomUUID().toString()
            entity.message = "추천 처리 시간이 초과됐습니다. 같은 조건으로 다시 시도해 주세요."; entity.updatedAt = Instant.now()
        }
    }
    private fun owned(userId: UUID, id: UUID) = plans.lockOwned(id.toString(), userId) ?: notFound()
    private fun result(entity: WorkoutPlanEntity) = mapper.readValue<WorkoutPlanResult>(entity.resultJson ?: notFound())
    private fun response(entity: WorkoutPlanEntity) = WorkoutPlanResponse(UUID.fromString(entity.id), entity.status, entity.version,
        entity.resultJson?.let { mapper.readValue<WorkoutPlanResult>(it) }, entity.routineId, entity.message)
    private fun checkVersion(entity: WorkoutPlanEntity, version: Long) { if (entity.version != version) throw ApiException(ApiErrorCode.VERSION_CONFLICT) }
    private fun notFound(): Nothing = throw ApiException(ApiErrorCode.RESOURCE_NOT_FOUND, "추천 또는 루틴을 찾을 수 없습니다.")
}
