# Direct DeepSeek retries

This bundle enables the existing unbounded retry policy for the direct DeepSeek provider.

It relies on the standard dsh-base profile, which already mounts @deepseek-ai/dsh-llm-retry. The bundle only replaces the llm-deepseek configuration row; it does not implement another retry loop.

Install it into a profile with:

    dsh plugin --profile <profile> add ./plugin/retry-always

The policy retries until the request succeeds, the session is cancelled, or the owning runtime is disposed. Backoff remains bounded by the provider policy.

The Pi AI dynamic-provider default is intentionally not included. Its defaultRetryPolicy field is an extension in the current source tree rather than a master-compatible provider setting. Android notifications are a native Gradle application, not a Cordis bundle.
