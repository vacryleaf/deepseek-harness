# Agent Note: Android remote client over Tailscale

Status: implemented

English | [中文](2026-08-15-android-tailscale-remote-client.zh.md)

> Scope: the native Android shell in plugin/android-remote, its remote-origin policy, and the private-network deployment path for a DSH Web host. This decision does not move DSH execution, credentials, or tool providers onto Android.

## Problem

The DSH Web UI needs a phone client for a host that remains responsible for the Agent, sessions, Shell, filesystem, subprocess, and LSP capabilities. A mobile package that embeds the Node runtime would duplicate host lifecycle and platform-specific tool implementations. A browser shell loaded from a Tailnet host can reuse the existing boot manifest, HTTP RPC, WebSocket streams, uploads, and reconnect behavior without introducing a second client protocol.

## Decision

**Android is a native WebView remote client.** plugin/android-remote collects and stores multiple validated DSH origins, remembers the last selected origin, and loads the complete remote Web application in a locked-down WebView. The app does not execute Node, Shell, subprocess, filesystem, or LSP code.

**Tailscale Serve is the deployment path.** DSH remains bound to 127.0.0.1; Tailscale Serve publishes that local listener to the Tailnet over HTTPS. The Android WebView loads the Tailscale HTTPS origin directly, so the page, /api requests, and WebSocket event paths remain same-origin. The DSH host name is added to --trusted-host because the API trust fence evaluates the forwarded authority.

**The app accepts an origin, not an arbitrary URL.** ServiceUrlPolicy rejects paths, queries, fragments, user information, missing hosts, and release HTTP URLs. Debug builds allow HTTP for local-network testing; release manifests set usesCleartextTraffic to false. TLS failures are cancelled instead of bypassed, and the WebView disables file access and mixed content.

**Tailscale access is not an application authentication protocol.** Tailnet membership and ACLs control network reachability. The Android client does not invent a bearer-token or login protocol; additional application authentication belongs to the remote DSH deployment or its authenticated proxy. The existing trustedHosts check remains a browser trust fence, not an identity mechanism.

## Alternatives considered

**Run DSH locally inside Android.** Rejected because the current runtime depends on Node, subprocesses, local filesystem semantics, and host-side plugins that Android does not provide as the same execution environment.

**Bundle a second React or Capacitor transport client.** Rejected for the first release because loading the remote Web origin preserves the existing boot injection and connection implementation, while a local bundle would require a new boot-config and cross-origin protocol path before it adds user capability.

**Bind DSH to all interfaces and connect to the LAN address.** Rejected as the default deployment because keeping DSH on loopback and using Tailscale Serve limits the listener and supplies the private encrypted network path without a public port.

## Consequences

A phone must run Tailscale, belong to the same Tailnet as the DSH host, and be allowed by the Tailnet ACL. The host operator runs dsh web with the Tailscale HTTPS hostname in its trusted-host list and points tailscale serve at the loopback Web port. The Android app can then be built and installed independently of the host runtime.

The WebView persists the service list and last selected origin and provides a native setup screen, service switching, browser navigation, external-link handling, file selection, reconnect behavior, TLS failure handling, and local notifications for completed root turns. Android 13 and newer requires notification permission. While a service is loaded, plugin/android-remote starts a low-priority data-sync foreground service that calls session.list, listens to the existing events.mux and events.host downlinks, filters root sessions, reconnects with backoff, and deduplicates completion notifications. The WebView callback remains a fallback for a loaded page. This reduces routine background reclamation but cannot override force-stop, OEM battery policy, or system-level termination, and it is not a replacement for FCM. A future native RPC carrier remains possible, but it must preserve the remote execution boundary and the host-owned capability model.

The client module loader derives each plugin revision from a 12-character SHA-1 content hash and appends it as `?rev=...`; local runtime bundle updates therefore require rebuilding and restarting the host artifact server, not a manual revision edit. Publishing a package additionally requires a package-version and lockfile update. The implementation and deployment procedure live in [the Android README](../../../../plugin/android-remote/README.md); the transport and trust semantics remain owned by [the WebSocket downlink note](../architecture/2026-08-04-websocket-downlink-carrier.md) and the host webserver package.
