package com.fitmate.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException

/** UI-thread state holder; a failed request never substitutes fake data for server data. */
internal class AdminUiState(private val repository: AdminRepository) {
    var snapshot: AdminSnapshot? by mutableStateOf(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var error: AdminError? by mutableStateOf(null)
        private set
    var requiresRefresh by mutableStateOf(false)
        private set
    var accessBlocked by mutableStateOf(false)
        private set

    suspend fun load(): Boolean = execute(mutation = false) { repository.fetch() }
    suspend fun claim(report: AdminReport): Boolean = execute(mutation = true) { repository.claim(report.id, report.version) }
    suspend fun submit(command: AdminDecisionCommand): Boolean = execute(mutation = true) { repository.submit(command) }

    private suspend fun execute(mutation: Boolean, request: suspend () -> AdminSnapshot): Boolean {
        if (busy || accessBlocked || (mutation && requiresRefresh)) return false
        busy = true
        error = null
        try {
            snapshot = request()
            requiresRefresh = false
            return true
        } catch (cancelled: CancellationException) {
            if (mutation) requiresRefresh = true
            throw cancelled
        } catch (failure: Exception) {
            error = (failure as? AdminFailure)?.code ?: AdminError.UNKNOWN
            if (error == AdminError.SESSION_EXPIRED || error == AdminError.FORBIDDEN) {
                snapshot = null
                accessBlocked = true
            }
            if (mutation) requiresRefresh = true
            return false
        } finally {
            busy = false
        }
    }
}
