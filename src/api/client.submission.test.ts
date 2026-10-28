import { afterEach, expect, it, vi } from 'vitest'
import { setCurrentAuthToken, submitSubmission } from './client'

afterEach(() => { vi.unstubAllGlobals(); setCurrentAuthToken('') })

it('sends an explicit automatic-timeout flag to let the server recheck an extension', async () => {
  const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ sessionState: 'active', session: { deadlineAt: '2026-10-28T07:00:00Z', remainingMs: 60000 } }), { status: 200 }))
  vi.stubGlobal('fetch', fetchMock)
  const result = await submitSubmission('e1', [], 0, true)
  expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ examId: 'e1', answers: [], switchCount: 0, autoSubmit: true })
  expect(result.sessionState).toBe('active')
})

it('does not turn intentional manual submission into an automatic timeout probe', async () => {
  const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ submission: { status: '已完成' } }), { status: 200 }))
  vi.stubGlobal('fetch', fetchMock)
  await submitSubmission('e1', [], 0)
  expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ examId: 'e1', answers: [], switchCount: 0 })
})
