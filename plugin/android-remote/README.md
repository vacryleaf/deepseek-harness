# DSH Android Remote Client

English | [中文](README.zh.md)

This module is a native Android WebView shell for a remotely hosted DSH Web application. The Android process does not run Node, Shell, subprocess, filesystem, or LSP plugins. Those capabilities remain on the remote DSH host.

## Build

Open plugin/android-remote in Android Studio or run gradlew.bat :app:assembleDebug after the Gradle wrapper is generated. The debug APK is written to app/build/outputs/apk/debug/app-debug.apk. The debug build accepts HTTP URLs for local-network testing. Release builds accept HTTPS URLs only; the current release output is unsigned and needs a signing configuration before distribution.

## App behavior

The setup screen accepts and saves multiple validated service origins. Selecting a saved service makes it the last service, and the next app launch opens that origin automatically. The old single-service `service_url` preference is migrated into the service list.

Android 13 and newer requires the notification permission. While a service is loaded, the app starts a low-priority `dataSync` foreground service. The service calls `/api/session.list`, listens to `/api/events.mux` and `/api/events.host`, filters root sessions, reconnects with backoff, and posts a native notification for each completed root turn. The WebView bridge remains a fallback for a loaded page and uses the same completion key for deduplication. This is best-effort retention, not an Android guarantee: force-stop, OEM battery policies, and system-level process termination can still stop delivery. This is not FCM. The remote host must expose the existing HTTP API and both WebSocket paths; the optional `window.DshAndroidBridge.onTaskCompleted` callback is only needed for the WebView fallback.

## Updating the Web runtime

Run `pnpm run build` from the repository root. The build refreshes `packages/client/runtime/lib/client.js` and the other client bundles. The Web module loader computes a 12-character SHA-1 content revision and adds it as `?rev=...`, so a local deployment does not require a manually edited runtime version. Restart the DSH Web installation that serves the rebuilt artifacts; a Windows task pointing at an older global `dsh.cmd` must be updated or reinstalled before restart.

Only bump `packages/client/runtime/package.json` and refresh `pnpm-lock.yaml` when publishing a new npm package version. A local Web deployment needs the rebuilt client bundle and a restarted host, not a package-version-only change.

## Tailscale deployment

Keep DSH on loopback and publish it to the Tailnet with Tailscale Serve. For example, start DSH with the Tailscale HTTPS hostname in its API trust list, then proxy the local Web server:

    pnpm dsh web --port 3080 --trusted-host <machine>.<tailnet>.ts.net
    tailscale serve --bg http://127.0.0.1:3080

Open the HTTPS hostname shown by tailscale serve in the Android app. The phone and the DSH host must belong to the same Tailnet, and the Tailnet ACL must permit the phone to reach the host. Tailscale provides the private encrypted network path; the DSH trustedHosts setting only protects the browser trust fence and is not authentication.

## Remote host requirements

The configured address must serve the complete DSH Web application, including /api and the two WebSocket event paths. Use HTTPS/WSS. The first implementation intentionally loads the remote Web origin directly, so page requests remain same-origin and keep the existing DSH Web boot manifest, HTTP RPC, WebSocket streams, uploads, and reconnect behavior unchanged.

For Android completion notifications, the native service uses the existing DSH HTTP and WebSocket event paths, so a runtime bundle version bump is not required for the native path. Deploying a Web build containing the optional runtime callback preserves the WebView fallback, and the same Web build continues to work in ordinary browsers.

Do not expose the loopback service directly to the public Internet. If the service is deployed outside a private Tailnet, put an authenticated HTTPS reverse proxy in front of it before allowing access.
