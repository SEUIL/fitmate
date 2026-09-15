package com.fitmate.backend.common.error

import com.fitmate.backend.common.api.ApiErrorField

class ApiException(
    val errorCode: ApiErrorCode,
    override val message: String = errorCode.message,
    val fields: List<ApiErrorField> = emptyList(),
) : RuntimeException(message)
