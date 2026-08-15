package ai.deepseek.dsh.android

import android.webkit.JavascriptInterface
import org.json.JSONObject

/** Minimal event payload sent by the Web client for a completed root turn. */
data class TaskCompletion(
    val sessionId: String,
    val turn: Int,
    val sequence: Long,
)

/** Receives optional Web client callbacks without exposing native controls to JavaScript. */
class TaskCompletionBridge(private val onTaskCompleted: (TaskCompletion) -> Unit) {
    @JavascriptInterface
    fun onTaskCompleted(payload: String) {
        parseTaskCompletion(payload)?.let(onTaskCompleted)
    }
}

internal fun parseTaskCompletion(payload: String): TaskCompletion? {
    return runCatching {
        val json = JSONObject(payload)
        val sessionId = json.optString("sessionId")
        val turn = json.optInt("turn", -1)
        val sequence = json.optLong("seq", -1L)
        if (sessionId.isBlank() || turn < 0 || sequence < 0) null
        else TaskCompletion(sessionId, turn, sequence)
    }.getOrNull()
}
