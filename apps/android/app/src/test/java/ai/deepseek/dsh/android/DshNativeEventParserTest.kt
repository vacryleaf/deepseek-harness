package ai.deepseek.dsh.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DshNativeEventParserTest {
    @Test
    fun sessionListKeepsOnlyRootSessions() {
        val body = """{"result":{"ok":true,"value":{"items":[{"sessionId":"root"},{"sessionId":"child","origin":"subagent"},{"sessionId":"parented","parentSessionId":"root"}]}}}"""

        assertEquals(setOf("root"), parseRootSessionIds(body))
    }

    @Test
    fun completedRootTurnProducesCompletion() {
        val frame = envelope("""{"type":"session/event","sessionId":"root","event":{"type":"turn/end","seq":12,"data":{"turn":3,"reason":{"kind":"completed"}}}}""")

        assertEquals(TaskCompletion("root", 3, 12L), parseNativeTaskCompletion(frame, setOf("root")))
    }

    @Test
    fun subagentAndAbortedTurnsAreIgnored() {
        val subagent = envelope("""{"type":"session/event","sessionId":"child","event":{"type":"turn/end","seq":12,"data":{"turn":3,"reason":{"kind":"completed"}}}}""")
        val aborted = envelope("""{"type":"session/event","sessionId":"root","event":{"type":"turn/end","seq":13,"data":{"turn":3,"reason":{"kind":"aborted"}}}}""")

        assertNull(parseNativeTaskCompletion(subagent, setOf("root")))
        assertNull(parseNativeTaskCompletion(aborted, setOf("root")))
    }

    @Test
    fun hostSessionChangesTrackOnlyRoots() {
        val added = envelope("""{"type":"host/session-added","sessionId":"new-root"}""")
        val child = envelope("""{"type":"host/session-added","sessionId":"new-child","origin":"subagent"}""")
        val removed = envelope("""{"type":"host/session-removed","sessionId":"new-root"}""")

        assertEquals(RootSessionChange("new-root", true), parseRootSessionChange(added))
        assertNull(parseRootSessionChange(child))
        assertEquals(RootSessionChange("new-root", false), parseRootSessionChange(removed))
    }

    @Test
    fun malformedFramesAreIgnored() {
        assertNull(parseRootSessionIds("not-json"))
        assertNull(parseNativeTaskCompletion("{}", setOf("root")))
        assertNull(parseRootSessionChange("{}"))
    }

    private fun envelope(payload: String): String {
        return """{"type":"server-request","rpcId":"r","payload":$payload}"""
    }
}
