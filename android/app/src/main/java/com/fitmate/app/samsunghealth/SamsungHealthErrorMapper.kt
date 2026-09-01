package com.fitmate.app.samsunghealth

import com.samsung.android.sdk.health.data.error.AuthorizationException
import com.samsung.android.sdk.health.data.error.ErrorCode
import com.samsung.android.sdk.health.data.error.HealthDataException
import com.samsung.android.sdk.health.data.error.ResolvablePlatformException

object SamsungHealthErrorMapper {
    fun map(error: Throwable): Pair<SamsungHealthStatus, String> = when (error) {
        is AuthorizationException -> SamsungHealthStatus.AUTHORIZATION_ERROR to "앱 인증 또는 권한 오류입니다. Samsung Health Data SDK 개발 모드와 Data Read 설정, package/signature 등록 상태를 확인하세요."
        is HealthDataException -> when (error.errorCode) {
            ErrorCode.ERR_PLATFORM_NOT_INSTALLED -> SamsungHealthStatus.PLATFORM_ERROR to "Samsung Health가 설치되지 않았습니다."
            ErrorCode.ERR_OLD_VERSION_PLATFORM -> SamsungHealthStatus.PLATFORM_ERROR to "Samsung Health 버전이 낮습니다. 6.30.2 이상으로 업데이트하세요."
            ErrorCode.ERR_PLATFORM_DISABLED -> SamsungHealthStatus.PLATFORM_ERROR to "Samsung Health가 비활성화되어 있습니다."
            ErrorCode.ERR_PLATFORM_NOT_INITIALIZED -> SamsungHealthStatus.PLATFORM_ERROR to "Samsung Health 초기 설정을 완료하세요."
            ErrorCode.ERR_NO_USER_PERMISSION -> SamsungHealthStatus.PERMISSION_MISSING to "Samsung Health 읽기 권한이 필요합니다."
            else -> SamsungHealthStatus.UNKNOWN_ERROR to "Samsung Health SDK 오류가 발생했습니다."
        }
        else -> SamsungHealthStatus.UNKNOWN_ERROR to "Samsung Health SDK를 사용할 수 없습니다."
    }

    fun isResolvable(error: Throwable) = error is ResolvablePlatformException && error.hasResolution
}
