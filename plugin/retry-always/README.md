# Retry policies

English | [中文](README.zh.md)

This bundle provides the two retry policy features that can run independently on the master source tree: direct DeepSeek unbounded retries and settings-backed Pi AI defaults.

## Direct DeepSeek

The bundle replaces the llm-deepseek configuration row with retryPolicy mode always. It relies on the standard dsh-base profile, which already mounts dsh-llm-retry. It does not implement another retry loop.

## Pi AI defaults

The bundle mounts a settings consumer that reads the llm-pi-ai namespace and adds retryPolicy mode always to every provider profile that does not already declare a policy. It repeats reconciliation when settings change, so providers added from the Models page receive the same default. Existing explicit policies are preserved.

The policy is validated and persisted by the normal settings provider. The existing dsh-llm-retry plugin continues to own backoff, cancellation, session events, and disposal. This avoids the leaf-only defaultRetryPolicy configuration field and works with master.

## Install

Install it into a profile that mounts llm-pi-ai and llm-retry:

    dsh plugin --profile <profile> add ./plugin/retry-always

A provider configured only in a static cordis entry must declare its own retryPolicy. Removing the bundle does not remove policies it already persisted; remove those settings explicitly when reverting the behavior.
