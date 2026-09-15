package com.fitmate.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException

/** UI-thread state holder; a failed request never substitutes fake data for server data. */
internal class AdminUiState(private val repository: AdminRepository) {
    var selected: AdminReport? by mutableStateOf(null)
        private set
    private var query = ""
    private var status: AdminReportStatus? = null
    suspend fun search(value: String, filter: AdminReportStatus?): Boolean {
        if (busy || accessBlocked) return false
        query = value; status = filter
        return execute(false) { repository.search(value, filter) }
    }
    suspend fun more(): Boolean = execute(false) {
        val current = requireNotNull(snapshot)
        val cursor = current.nextCursor ?: return@execute current
        val next = repository.search(query, status, cursor)
        next.copy(reports = (current.reports + next.reports).distinctBy { it.id })
    }
    suspend fun open(id: String): Boolean {
        if (busy || accessBlocked) return false
        selected = null
        return execute(false, refreshesDetail = true) { selected = repository.detail(id); requireNotNull(snapshot) }
    }
    fun closeDetail() { selected = null }
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

    suspend fun load(): Boolean = execute(mutation = false, refreshesDetail = true) {
        val fresh = repository.fetch()
        selected = selected?.let { repository.detail(it.id) }
        fresh
    }
    suspend fun claim(report: AdminReport): Boolean = execute(mutation = true) { repository.claim(report.id, report.version).also { selected = repository.detail(report.id) } }
    suspend fun submit(command: AdminDecisionCommand): Boolean = execute(mutation = true) { repository.submit(command).also { selected = repository.detail(command.reportId) } }

    private suspend fun execute(mutation: Boolean, refreshesDetail: Boolean = false, request: suspend () -> AdminSnapshot): Boolean {
        if (busy || accessBlocked || (mutation && requiresRefresh)) return false
        busy = true
        error = null
        try {
            snapshot = request()
            if (mutation || refreshesDetail || selected == null) requiresRefresh = false
            return true
        } catch (cancelled: CancellationException) {
            if (mutation) requiresRefresh = true
            throw cancelled
        } catch (failure: Exception) {
            error = (failure as? AdminFailure)?.code ?: AdminError.UNKNOWN
            if (error == AdminError.SESSION_EXPIRED || error == AdminError.FORBIDDEN) {
                snapshot = null
                selected = null
                accessBlocked = true
            }
            if (mutation) requiresRefresh = true
            return false
        } finally {
            busy = false
        }
    }
}
