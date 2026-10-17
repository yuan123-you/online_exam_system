import { afterEach, describe, expect, it, vi } from 'vitest'
import { aiChatStream, aiPracticeQuestionsStream, setCurrentAuthToken } from './client'

function response(parts: string[]) {
  return new Response(new ReadableStream({ start(controller) {
    for (const part of parts) controller.enqueue(new TextEncoder().encode(part))
    controller.close()
  } }), { headers: { 'Content-Type': 'text/event-stream' } })
}
async function run(parts: string[]) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(parts)))
  const chunks: unknown[] = [], completed: unknown[] = [], errors: string[] = []
  let finalized = 0
  await new Promise<void>(resolve => aiChatStream({ message: '解释微积分' },
    chunk => chunks.push(chunk), data => completed.push(data), error => errors.push(error),
    () => { finalized++; resolve() }))
  return { chunks, completed, errors, finalized }
}
afterEach(() => { setCurrentAuthToken(); vi.unstubAllGlobals(); vi.useRealTimers() })
describe('AI stream lifecycle', () => {
  it('reports truncated streams as errors rather than fabricated success', async () => {
    const result = await run(['event: chunk\ndata: {"type":"content","text":"部分回答"}\n\n'])
    expect(result.completed).toHaveLength(0)
    expect(result.errors).toHaveLength(1)
    expect(result.finalized).toBe(1)
  })
  it('parses CRLF frames split across packets and stops at terminal event', async () => {
    const result = await run(['event: chunk\r\ndata: {"type":"content","text":"你好"}\r',
      '\n\r\nevent: complete\r\ndata: {"content":"你好","reasoning":"","done":true}\r\n\r\n',
      'event: error\ndata: {"error":"late error"}\n\n'])
    expect(result.chunks).toHaveLength(1)
    expect(result.completed).toHaveLength(1)
    expect(result.errors).toEqual([])
  })
  it('dispatches the final event even without a trailing blank line', async () => {
    const result = await run(['event: complete\ndata: {"content":"完成","reasoning":"","done":true}\n'])
    expect(result.completed).toEqual([{ content: '完成', reasoning: '', done: true }])
    expect(result.errors).toEqual([])
  })
  it('does not wait for EOF after a complete frame', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(new ReadableStream({ start(c) {
      c.enqueue(new TextEncoder().encode('event: complete\ndata: {"content":"完成","reasoning":"","done":true}\n\n'))
    } }))))
    const complete = vi.fn(), error = vi.fn(), final = vi.fn()
    aiChatStream({ message: 'test' }, vi.fn(), complete, error, final)
    await vi.waitFor(() => expect(final).toHaveBeenCalledOnce())
    expect(complete).toHaveBeenCalledOnce()
    expect(error).not.toHaveBeenCalled()
  })
  it('cancels retry backoff promptly and finalizes once without another request', async () => {
    vi.useFakeTimers()
    const fetch = vi.fn().mockResolvedValue(new Response('{"error":"busy"}', { status: 429 }))
    vi.stubGlobal('fetch', fetch)
    const error = vi.fn(), final = vi.fn()
    const controller = aiChatStream({ message: 'test' }, vi.fn(), vi.fn(), error, final)
    await vi.advanceTimersByTimeAsync(0)
    controller.abort()
    await vi.advanceTimersByTimeAsync(0)
    expect(final).toHaveBeenCalledOnce()
    expect(error).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(60_000)
    expect(fetch).toHaveBeenCalledOnce()
  })
  it('surfaces heartbeat timeout instead of silently leaving the UI loading', async () => {
    vi.useFakeTimers()
    vi.stubGlobal('fetch', vi.fn((_url, options) => new Promise((_resolve, reject) => {
      options.signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
    })))
    const error = vi.fn(), final = vi.fn()
    aiChatStream({ message: 'test' }, vi.fn(), vi.fn(), error, final)
    await vi.advanceTimersByTimeAsync(300_000)
    expect(error).toHaveBeenCalledWith(expect.stringContaining('超时'))
    expect(final).toHaveBeenCalledOnce()
  })
  it('honors Retry-After and succeeds without duplicate completion', async () => {
    vi.useFakeTimers()
    const fetch = vi.fn().mockResolvedValueOnce(new Response('{"error":"busy"}', {
      status: 429, headers: { 'Retry-After': '2' },
    })).mockResolvedValueOnce(response(['event: complete\ndata: {"content":"完成","reasoning":"","done":true}\n\n']))
    vi.stubGlobal('fetch', fetch)
    const complete = vi.fn(), error = vi.fn(), final = vi.fn()
    aiChatStream({ message: 'test' }, vi.fn(), complete, error, final)
    await vi.advanceTimersByTimeAsync(1999)
    expect(fetch).toHaveBeenCalledOnce()
    await vi.advanceTimersByTimeAsync(1)
    expect(complete).toHaveBeenCalledOnce()
    expect(final).toHaveBeenCalledOnce()
    expect(error).not.toHaveBeenCalled()
  })
  it('treats failed batches as terminal instead of accepting a later success', async () => {
    const result = await run(['event: batch_error\ndata: {"error":"batch failed"}\n\n',
      'event: complete\ndata: {"content":"[]","reasoning":"","done":true}\n\n'])
    expect(result.errors).toEqual(['batch failed'])
    expect(result.completed).toEqual([])
  })

  it('preserves practice endpoint, authentication and batch progress callbacks', async () => {
    const fetch = vi.fn().mockResolvedValue(response([
      'event: batch_progress\ndata: {"batch":1,"totalBatches":2,"totalCount":21,"generated":20}\n\n',
      'event: complete\ndata: {"content":"[]","reasoning":"","done":true}\n\n',
    ]))
    vi.stubGlobal('fetch', fetch)
    setCurrentAuthToken('student-token', 'signed-session')
    const progress = vi.fn()
    await new Promise<void>(resolve => aiPracticeQuestionsStream({
      customPrompt: '出21道数学题', subject: '数学', type: 'single', difficulty: 'medium', count: 21,
    }, vi.fn(), vi.fn(), vi.fn(), resolve, progress))
    expect(progress).toHaveBeenCalledWith({ batch: 1, totalBatches: 2, totalCount: 21, generated: 20 })
    const [endpoint, options] = fetch.mock.calls[0]!
    expect(endpoint).toBe('/api/ai/practice-questions/stream')
    expect(options.headers['X-User-Id']).toBe('student-token')
    expect(options.headers['X-Session-Token']).toBe('signed-session')
    expect(JSON.parse(options.body)).toMatchObject({ count: 21, customPrompt: '出21道数学题' })
  })

})
