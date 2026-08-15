import { afterEach, describe, expect, it, vi } from 'vitest'
import { publishNativeTaskCompleted } from '../src/client/native-task-notifications.ts'

const completion = { sessionId: 'session-1', turn: 2, seq: 17 }

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('publishNativeTaskCompleted', () => {
  it('does nothing when the page has no window', () => {
    vi.stubGlobal('window', undefined)
    expect(() => publishNativeTaskCompleted(completion)).not.toThrow()
  })

  it('does nothing when the Android bridge is unavailable', () => {
    vi.stubGlobal('window', {})
    expect(() => publishNativeTaskCompleted(completion)).not.toThrow()
  })

  it('serializes the completion payload for the Android bridge', () => {
    const onTaskCompleted = vi.fn()
    vi.stubGlobal('window', { DshAndroidBridge: { onTaskCompleted } })

    publishNativeTaskCompleted(completion)

    expect(onTaskCompleted).toHaveBeenCalledWith(JSON.stringify(completion))
  })

  it('contains a bridge failure so session delivery can continue', () => {
    const onTaskCompleted = vi.fn(() => { throw new Error('destroyed WebView') })
    vi.stubGlobal('window', { DshAndroidBridge: { onTaskCompleted } })

    expect(() => publishNativeTaskCompleted(completion)).not.toThrow()
    expect(onTaskCompleted).toHaveBeenCalledOnce()
  })
})
