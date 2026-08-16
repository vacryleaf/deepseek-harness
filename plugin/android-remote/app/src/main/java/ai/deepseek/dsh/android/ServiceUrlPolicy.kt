package ai.deepseek.dsh.android

import java.net.URI

/** Validates and normalizes the origin used to load a remote DSH server. */
internal object ServiceUrlPolicy {
    enum class Error {
        INVALID_URL,
        HTTPS_REQUIRED,
        ORIGIN_ONLY,
    }

    data class Result(
        val url: String?,
        val error: Error? = null,
    )

    /**
     * Accepts an HTTPS origin in every build and HTTP only when the caller explicitly allows debug transport.
     */
    fun validate(raw: String, allowHttp: Boolean): Result {
        val value = raw.trim().removeSuffix("/")
        return try {
            val uri = URI(value)
            val scheme = uri.scheme?.lowercase()
            if (uri.host.isNullOrBlank() || uri.userInfo != null || uri.query != null || uri.fragment != null) {
                return Result(null, Error.INVALID_URL)
            }
            if (uri.path != null && uri.path.isNotEmpty() && uri.path != "/") {
                return Result(null, Error.ORIGIN_ONLY)
            }
            if (scheme != "https" && !(allowHttp && scheme == "http")) {
                return Result(null, Error.HTTPS_REQUIRED)
            }
            Result(URI(scheme, null, uri.host, uri.port, null, null, null).toString().removeSuffix("/"))
        } catch (_: Exception) {
            Result(null, Error.INVALID_URL)
        }
    }
}
