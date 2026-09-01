package com.fitmate.app.samsunghealth

data class SamsungDeviceUiModel(
    val maskedId: String?,
    val type: String?,
    val manufacturer: String?,
    val model: String?,
    val name: String?,
    val label: String
)

object SamsungDeviceFormatter {
    fun maskId(value: String?): String? = value?.takeIf { it.isNotBlank() }?.let { if (it.length <= 4) "••••" else "${it.take(2)}••••${it.takeLast(2)}" }
    fun label(type: String?, model: String?, name: String?): String {
        val combined = listOfNotNull(type, model, name).joinToString(" ").lowercase()
        return when {
            "fit2" in combined || "fit 2" in combined -> "Galaxy Fit2 추정"
            "phone" in combined || "mobile" in combined -> "휴대전화"
            else -> "기기 확인 불가"
        }
    }
}
