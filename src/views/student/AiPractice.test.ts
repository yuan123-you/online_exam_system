import { describe, expect, it, vi, beforeEach } from 'vitest'
import { logBehavior } from '@/api/client'

describe('logBehavior robustness', () => {
  it('does not reject when fetch fails with network or resource error', async () => {
    // Mock global fetch to throw a network error
    const originalFetch = globalThis.fetch
    globalThis.fetch = vi.fn().mockRejectedValue(new Error('net::ERR_INSUFFICIENT_RESOURCES'))
    try {
      const result = await logBehavior({ action: 'test', targetType: 'test' })
      expect(result).toHaveProperty('logged', false)
    } finally {
      globalThis.fetch = originalFetch
    }
  })
})
