package ai.deepseek.dsh.android

import org.json.JSONObject

/** A root-session registry change received from the host event stream. */
data class RootSessionChange(
    val sessionId: String,
    val added: Boolean,
)

/** Parses the session.list response into the root sessions eligible for notifications. */
internal fun parseRootSessionIds(body: String): Set<String>? {
    return runCatching {
        val result = JSONObject(body).getJSONObject("result")
        if (!result.getBoolean("ok")) return null
        val items = result.getJSONObject("value").getJSONArray("items")
        buildSet {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                val sessionId = item.getString("sessionId")
                if (sessionId.isNotBlank()
                    && !item.has("origin")
                    && !item.has("parentSessionId")
                ) add(sessionId)
            }
        }
    }.getOrNull()
}

/** Parses one mux envelope and returns only a completed root turn. */
internal fun parseNativeTaskCompletion(text: String, rootSessionIds: Set<String>): TaskCompletion? {
    return runCatching {
        val envelope = JSONObject(text)
        if (envelope.getString("type") != "server-request") return null
        val payload = envelope.getJSONObject("payload")
        if (payload.getString("type") != "session/event") return null
        val sessionId = payload.getString("sessionId")
        if (sessionId.isBlank() || !rootSessionIds.contains(sessionId)) return null
        val event = payload.getJSONObject("event")
        if (event.getString("type") != "turn/end") return null
        val data = event.getJSONObject("data")
        if (data.getJSONObject("reason").getString("kind") != "completed") return null
        val turn = data.getInt("turn")
        val sequence = event.getLong("seq")
        if (turn < 0 || sequence < 0) return null
        TaskCompletion(sessionId, turn, sequence)
    }.getOrNull()
}

/** Parses host stream session lifecycle frames used to extend the root-session registry. */
internal fun parseRootSessionChange(text: String): RootSessionChange? {
    return runCatching {
        val envelope = JSONObject(text)
        if (envelope.getString("type") != "server-request") return null
        val payload = envelope.getJSONObject("payload")
        val sessionId = payload.getString("sessionId")
        if (sessionId.isBlank()) return null
        when (payload.getString("type")) {
            "host/session-removed" -> RootSessionChange(sessionId, added = false)
            "host/session-added" -> {
                if (payload.has("origin") || payload.has("parentSessionId")) null
                else RootSessionChange(sessionId, added = true)
            }
            else -> null
        }
    }.getOrNull()
}
