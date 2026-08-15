/** Optional browser-to-native callback used by the Android WebView shell. */

interface DshAndroidBridge {
  onTaskCompleted(payload: string): void
}

export interface NativeTaskCompletion {
  readonly sessionId: string
  readonly turn: number
  readonly seq: number
}

/** Publishes one completed root turn when the host page exposes the Android bridge. */
export function publishNativeTaskCompleted(completion: NativeTaskCompletion): void {
  if (typeof window === 'undefined') return
  const bridge = (window as Window & { DshAndroidBridge?: DshAndroidBridge }).DshAndroidBridge
  if (bridge === undefined) return
  try {
    bridge.onTaskCompleted(JSON.stringify(completion))
  } catch (error) {
    // A destroyed WebView bridge must not interrupt the session event stream.
    void error
  }
}
