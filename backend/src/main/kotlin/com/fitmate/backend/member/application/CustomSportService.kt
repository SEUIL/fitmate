package com.fitmate.backend.member.application

import com.fitmate.backend.common.error.ApiErrorCode
import com.fitmate.backend.common.error.ApiException
import com.fitmate.backend.member.api.CustomSportDto
import com.fitmate.backend.member.domain.UserCustomSport
import com.fitmate.backend.member.domain.UserCustomSportId
import com.fitmate.backend.member.repository.UserCustomSportRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.text.Normalizer
import java.time.Clock
import java.util.Locale
import java.util.UUID

@Service
class CustomSportService(
    private val repository: UserCustomSportRepository,
    private val clock: Clock,
) {
    fun validate(selections: List<CustomSportDto>) {
        normalizedSelections(selections)
    }

    @Transactional(readOnly = true)
    fun get(userId: UUID): List<CustomSportDto> = repository.findAllByIdUserIdOrderByIdNormalizedNameAsc(userId)
        .map { CustomSportDto(it.name) }

    // Callers hold the profile lock when editing an existing user's interests.
    @Transactional
    fun replace(userId: UUID, selections: List<CustomSportDto>): List<CustomSportDto> {
        val names = normalizedSelections(selections)
        val existing = repository.findAllByIdUserIdOrderByIdNormalizedNameAsc(userId)
        val byName = existing.associateBy { it.id.normalizedName }
        repository.deleteAll(existing.filter { it.id.normalizedName !in names })
        repository.saveAll(names.map { (normalizedName, name) ->
            byName[normalizedName]?.also { it.name = name }
                ?: UserCustomSport(UserCustomSportId(userId, normalizedName), name, clock.instant())
        })
        return names.toSortedMap().values.map(::CustomSportDto)
    }

    private fun normalizedSelections(selections: List<CustomSportDto?>): Map<String, String> {
        if (selections.size > 20) invalid("커스텀 관심 운동은 최대 20개까지 등록할 수 있습니다.")
        val names = linkedMapOf<String, String>()
        selections.forEach { selection ->
            if (selection == null) invalid("커스텀 관심 운동 이름을 입력해야 합니다.")
            val name = Normalizer.normalize(selection.name, Normalizer.Form.NFKC)
                .replace(Regex("[\\s\\p{Z}]+"), " ").trim()
            val normalizedName = name.lowercase(Locale.ROOT)
            if (selection.name.length > 50 || name.length !in 1..50 || normalizedName.length > 50 || name.any { Character.isISOControl(it) }) {
                invalid("커스텀 관심 운동 이름은 공백을 제외하고 1~50자여야 합니다.")
            }
            if (names.put(normalizedName, name) != null) invalid("같은 커스텀 관심 운동을 중복 등록할 수 없습니다.")
        }
        return names
    }

    private fun invalid(message: String): Nothing = throw ApiException(ApiErrorCode.VALIDATION_ERROR, message)
}
