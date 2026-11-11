package io.androllm.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.androllm.core.tools.audit.AuditEntry
import io.androllm.core.tools.audit.AuditLog
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Backs the "Dnevnik akcija" screen: shows and clears the persistent audit log. */
@HiltViewModel
class AuditLogViewModel @Inject constructor(
    private val auditLog: AuditLog
) : ViewModel() {

    val entries: StateFlow<List<AuditEntry>> = auditLog.entries

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch { auditLog.load() }
    }

    fun clear() {
        viewModelScope.launch { auditLog.clear() }
    }
}
