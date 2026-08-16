const SETTINGS_NAMESPACE = 'llm-pi-ai'

/** This plugin has no deployment-specific knobs; it accepts only an empty object. */
export const Config = {
  '~standard': {
    version: 1,
    vendor: 'dsh-pi-ai-retry-always',
    validate(value) {
      return value === undefined || isRecord(value)
        ? { value: value ?? {} }
        : { issues: [{ message: 'pi-ai-retry-always config must be an object' }] }
    },
  },
}

export const name = 'pi-ai-retry-always'
export const inject = ['settings']

function isRecord(value) {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function missingPolicyOperations(section) {
  if (!isRecord(section) || !isRecord(section.providers)) return []
  return Object.entries(section.providers)
    .filter(([, profile]) => isRecord(profile) && profile.retryPolicy === undefined)
    .map(([provider]) => ({
      op: 'set',
      path: ['providers', provider, 'retryPolicy'],
      value: { mode: 'always' },
    }))
}

/**
 * Apply an explicit always policy to settings-backed Pi AI routes that omit one.
 * @param ctx - the injected settings context.
 * @returns nothing; the settings provider owns persistence and validation.
 */
export function apply(ctx) {
  const current = ctx.settings.get(SETTINGS_NAMESPACE)
  if (current === undefined) {
    throw new Error('pi-ai-retry-always requires the llm-pi-ai settings namespace')
  }

  const state = { disposed: false, pending: false, queued: undefined }
  const reconcile = section => {
    if (state.disposed) return
    state.queued = section
    if (state.pending) return
    const next = state.queued
    state.queued = undefined
    const ops = missingPolicyOperations(next)
    if (ops.length === 0) return
    state.pending = true
    void ctx.settings.mutate(SETTINGS_NAMESPACE, ops).catch(error => {
      ctx.logger.warn('pi-ai-retry-always could not persist provider policies: %o', error)
    }).finally(() => {
      state.pending = false
      if (state.queued !== undefined) reconcile(state.queued)
    })
  }

  const disposeListener = ctx.on('settings/updated', (namespace, next) => {
    if (String(namespace) === SETTINGS_NAMESPACE) reconcile(next)
  })
  ctx.effect(() => () => {
    state.disposed = true
    disposeListener()
  }, 'pi-ai-retry-always: dispose settings listener')

  reconcile(current)
}
