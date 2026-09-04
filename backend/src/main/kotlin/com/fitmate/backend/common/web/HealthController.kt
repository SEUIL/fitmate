package com.fitmate.backend.common.web

import com.fitmate.backend.common.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/v1/health")
class HealthController {

    @GetMapping
    fun health(): ApiResponse<HealthResponse> {
        return ApiResponse.success(
            HealthResponse(
                status = "UP",
                timestamp = Instant.now(),
            ),
        )
    }
}

data class HealthResponse(
    val status: String,
    val timestamp: Instant,
)
