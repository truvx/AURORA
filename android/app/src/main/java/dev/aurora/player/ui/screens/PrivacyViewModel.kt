package dev.aurora.player.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aurora.player.domain.library.LibraryOrganizationRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class PrivacyAction {
    object Idle : PrivacyAction()
    object Working : PrivacyAction()
    data class Exported(val path: String, val count: Int) : PrivacyAction()
    object Deleted : PrivacyAction()
    data class Failed(val reason: String) : PrivacyAction()
}

/**
 * Listening history is the user's own data: it must be exportable and deletable without
 * affecting playback or their curation. See docs/SECURITY.md.
 */
class PrivacyViewModel(
    private val repository: LibraryOrganizationRepository,
    private val exportDir: File
) : ViewModel() {

    private val _action = MutableStateFlow<PrivacyAction>(PrivacyAction.Idle)
    val action: StateFlow<PrivacyAction> = _action.asStateFlow()

    fun exportHistory() {
        if (_action.value == PrivacyAction.Working) return
        _action.value = PrivacyAction.Working
        viewModelScope.launch {
            _action.value = runCatching {
                val records = repository.exportHistory()
                val file = withContext(Dispatchers.IO) {
                    exportDir.mkdirs()
                    val target = File(exportDir, "aurora-listening-history.json")
                    target.writeText(toJson(records))
                    target
                }
                PrivacyAction.Exported(file.absolutePath, records.size)
            }.getOrElse { PrivacyAction.Failed(it.message ?: "Export failed") }
        }
    }

    fun deleteAllPrivateData() {
        if (_action.value == PrivacyAction.Working) return
        _action.value = PrivacyAction.Working
        viewModelScope.launch {
            _action.value = runCatching {
                repository.deleteAllPrivateData()
                PrivacyAction.Deleted
            }.getOrElse { PrivacyAction.Failed(it.message ?: "Delete failed") }
        }
    }

    fun acknowledge() {
        _action.value = PrivacyAction.Idle
    }

    /**
     * Hand-rolled rather than pulled through a serializer: the export contains only the
     * user's own event records, and keeping it dependency-free means the format is obvious
     * to anyone reading the file.
     */
    private fun toJson(
        records: List<dev.aurora.player.domain.library.ListeningHistoryRecord>
    ): String = buildString {
        append("[\n")
        records.forEachIndexed { index, record ->
            append("  {")
            append("\"mediaId\":\"").append(escape(record.mediaId)).append("\",")
            append("\"provider\":\"").append(escape(record.provider)).append("\",")
            append("\"timestamp\":").append(record.timestamp).append(',')
            append("\"kind\":\"").append(record.kind.name).append("\",")
            append("\"progressMs\":").append(record.progressMs)
            append("}")
            if (index != records.lastIndex) append(',')
            append('\n')
        }
        append("]\n")
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        fun exportDirFor(context: Context): File =
            File(context.getExternalFilesDir(null) ?: context.filesDir, "exports")
    }
}
