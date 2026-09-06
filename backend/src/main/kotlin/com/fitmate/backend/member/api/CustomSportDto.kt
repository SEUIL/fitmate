package com.fitmate.backend.member.api

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CustomSportDto(@field:NotBlank @field:Size(max = 50) val name: String)
