package com.fitmate.backend.common.api

data class ApiResponse<T>(
    val data: T,
) {
    companion object {
        fun <T> success(data: T): ApiResponse<T> = ApiResponse(data)
    }
}

data class ItemListResponse<T>(
    val items: List<T>,
)

data class CursorPageResponse<T>(
    val items: List<T>,
    val nextCursor: String?,
    val hasNext: Boolean,
)

data class ErrorResponse(
    val error: ApiErrorBody,
)

data class ApiErrorBody(
    val code: String,
    val message: String,
    val fields: List<ApiErrorField> = emptyList(),
)

data class ApiErrorField(
    val field: String,
    val reason: String,
)
