package ai.deepseek.dsh.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServiceUrlPolicyTest {
    @Test
    fun normalizesAnHttpsOrigin() {
        val result = ServiceUrlPolicy.validate(" https://dsh.example.ts.net/ ", allowHttp = false)

        assertEquals("https://dsh.example.ts.net", result.url)
        assertNull(result.error)
    }

    @Test
    fun preservesAnExplicitPort() {
        val result = ServiceUrlPolicy.validate("https://100.64.0.4:3080", allowHttp = false)

        assertEquals("https://100.64.0.4:3080", result.url)
    }

    @Test
    fun allowsHttpOnlyWhenDebugTransportIsEnabled() {
        val debugResult = ServiceUrlPolicy.validate("http://100.64.0.4:3080", allowHttp = true)
        val releaseResult = ServiceUrlPolicy.validate("http://100.64.0.4:3080", allowHttp = false)

        assertEquals("http://100.64.0.4:3080", debugResult.url)
        assertEquals(ServiceUrlPolicy.Error.HTTPS_REQUIRED, releaseResult.error)
    }

    @Test
    fun rejectsAPathQueryCredentialsAndMissingHost() {
        assertEquals(
            ServiceUrlPolicy.Error.ORIGIN_ONLY,
            ServiceUrlPolicy.validate("https://dsh.example.ts.net/app", allowHttp = false).error,
        )
        assertEquals(
            ServiceUrlPolicy.Error.INVALID_URL,
            ServiceUrlPolicy.validate("https://dsh.example.ts.net?token=secret", allowHttp = false).error,
        )
        assertEquals(
            ServiceUrlPolicy.Error.INVALID_URL,
            ServiceUrlPolicy.validate("https://user:pass@dsh.example.ts.net", allowHttp = false).error,
        )
        assertEquals(
            ServiceUrlPolicy.Error.INVALID_URL,
            ServiceUrlPolicy.validate("https://", allowHttp = false).error,
        )
    }
}
