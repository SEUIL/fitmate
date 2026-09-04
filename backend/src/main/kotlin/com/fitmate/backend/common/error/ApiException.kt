package com.fitmate.backend.common.error

class ApiException(
    val errorCode: ApiErrorCode,
    override val message: String = errorCode.message,
) : RuntimeException(message)
