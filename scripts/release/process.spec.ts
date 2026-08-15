/** Tests for shell-free package-manager invocation in release scripts. */

import { describe, expect, it } from 'vitest'
import { pnpmInvocation } from './process.ts'

describe('pnpmInvocation', () => {
  it('uses Node to invoke the pnpm lifecycle entrypoint', () => {
    expect(pnpmInvocation(['--dir', 'packages/sandbox/sandbox', 'pack'], 'C:/tools/pnpm.cjs')).toEqual({
      command: process.execPath,
      args: ['C:/tools/pnpm.cjs', '--dir', 'packages/sandbox/sandbox', 'pack'],
    })
  })

  it('fails before a release step when pnpm did not launch it', () => {
    expect(() => { pnpmInvocation(['pack'], '') }).toThrow(
      'release: npm_execpath is unavailable; invoke the release script through a pnpm package script',
    )
  })
})
