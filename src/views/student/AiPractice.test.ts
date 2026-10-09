import { describe, expect, it, vi, beforeEach } from 'vitest'
import { logBehavior } from '@/api/client'

describe('logBehavior robustness', () => {
  it('does not reject when fetch fails with network or resource error', async () => {
    // Test that logBehavior returns a safe resolved object even if network fails
    const result = await logBehavior({ action: 'test', targetType: 'test' }).catch(err => ({ error: err }))
    expect(result).toHaveProperty('logged')
  })
})
