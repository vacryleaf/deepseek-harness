package ai.deepseek.dsh.android

/** Completed root turn carried by the native host event stream. */
data class TaskCompletion(
    val sessionId: String,
    val turn: Int,
    val sequence: Long,
)
