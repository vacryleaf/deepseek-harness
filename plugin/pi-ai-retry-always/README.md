# Pi AI retry defaults

This bundle keeps the Pi AI default retry behavior compatible with the master source tree. It does not use the leaf-only defaultRetryPolicy configuration field.

When the plugin activates, it reads the llm-pi-ai settings namespace and adds retryPolicy: { mode: always } to every provider profile that does not already declare a policy. It repeats the reconciliation when the settings namespace changes, so providers added from the Models page receive the same default.

Existing explicit policies are preserved. The resulting policy is validated and persisted by the normal settings provider, then the existing dsh-llm-retry plugin handles backoff, cancellation, session events, and disposal.

Install it into a profile that already mounts llm-pi-ai and llm-retry:

    dsh plugin --profile <profile> add ./plugin/pi-ai-retry-always

This plugin applies to settings-backed Pi AI routes. A provider configured only in a static cordis entry must declare its own retryPolicy. Removing the plugin does not remove policies it already persisted; remove those settings explicitly when reverting the behavior.
