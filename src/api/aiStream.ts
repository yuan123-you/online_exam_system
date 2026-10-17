import type { SseBatchProgress, SseChunk, SseComplete } from './client'

/** One request owns one terminal event and one cleanup, including retries and cancellation. */
export function streamAiResponse(
  endpoint: string, body: Record<string, unknown>, headers: Record<string, string>,
  onChunk: (chunk: SseChunk) => void, onComplete: (data: SseComplete) => void,
  onError: (error: string) => void, onFinally?: () => void, maxRetries = 2,
  onBatchProgress?: (data: SseBatchProgress) => void,
): AbortController {
  const controller = new AbortController()
  const { signal } = controller
  let terminal = false, timedOut = false
  let timer: ReturnType<typeof setTimeout> | undefined
  const heartbeat = () => {
    clearTimeout(timer)
    timer = setTimeout(() => { timedOut = true; controller.abort() }, 300_000)
  }
  const fail = (message: string) => {
    if (terminal) return
    terminal = true
    onError(message.includes('熔断保护') ? 'AI 服务正在恢复中，请稍后重试' : message)
  }
  const wait = (ms: number) => new Promise<void>((resolve, reject) => {
    const abort = () => { clearTimeout(id); reject(new DOMException('Aborted', 'AbortError')) }
    const id = setTimeout(() => { signal.removeEventListener('abort', abort); resolve() }, ms)
    signal.addEventListener('abort', abort, { once: true })
    if (signal.aborted) abort()
  })
  void (async () => {
    let reader: ReadableStreamDefaultReader<Uint8Array> | undefined
    try {
      let response: Response | undefined
      for (let attempt = 0; attempt <= maxRetries; attempt++) {
        if (signal.aborted) return
        heartbeat()
        try {
          response = await fetch(endpoint, { method: 'POST', headers, body: JSON.stringify(body), signal })
        } catch (error) {
          if (signal.aborted || attempt === maxRetries) throw error
          clearTimeout(timer)
          await wait(1000 * (attempt + 1))
          continue
        }
        if ((response.status === 429 || response.status === 503) && attempt < maxRetries) {
          const retryAfter = response.headers.get('Retry-After')
          const seconds = retryAfter === null ? NaN : Number(retryAfter)
          const delay = Number.isFinite(seconds) ? seconds * 1000 : Date.parse(retryAfter || '') - Date.now()
          // Do not retry earlier than a long upstream cooldown; return its error instead.
          if (delay > 30_000) break
          await response.body?.cancel()
          clearTimeout(timer)
          await wait(Number.isFinite(delay) ? Math.max(0, delay) : 2000 * 2 ** attempt)
          continue
        }
        break
      }
      if (!response) throw new Error('网络连接失败，请检查网络后重试')
      if (!response.ok) {
        let message = `请求失败 (HTTP ${response.status})`
        try { const data = await response.json(); message = data.error || data.message || message } catch { /* non-JSON gateway */ }
        fail(message)
        return
      }
      reader = response.body?.getReader()
      if (!reader) throw new Error('响应数据不可读取')
      const decoder = new TextDecoder()
      let buffer = '', event = '', data: string[] = []
      const dispatch = () => {
        if (!data.length || terminal) { event = ''; data = []; return }
        const name = event, payload = data.join('\n')
        event = ''; data = []
        if (!['chunk', 'complete', 'error', 'batch_progress', 'batch_start', 'batch_error'].includes(name)) return
        let parsed: any
        try { parsed = JSON.parse(payload) } catch { throw new Error('AI 响应格式错误，请重试') }
        if (name === 'complete') {
          if (parsed.done !== true || typeof parsed.content !== 'string') throw new Error('AI 响应格式错误，请重试')
          terminal = true
          onComplete(parsed)
        } else if (name === 'error' || name === 'batch_error') {
          fail(parsed.error || 'AI 请求失败，请稍后重试')
        } else if (name === 'chunk') onChunk(parsed)
        else onBatchProgress?.(parsed)
      }
      const line = (value: string) => {
        value = value.replace(/\r$/, '')
        if (!value) { dispatch(); return }
        const colon = value.indexOf(':')
        const field = colon < 0 ? value : value.slice(0, colon)
        const text = colon < 0 ? '' : value.slice(colon + 1).replace(/^ /, '')
        if (field === 'event') event = text
        else if (field === 'data') data.push(text)
      }
      while (!terminal && !signal.aborted) {
        const { done, value } = await reader.read()
        buffer += done ? decoder.decode() : decoder.decode(value, { stream: true })
        if (!done) heartbeat()
        let newline: number
        while (!terminal && (newline = buffer.indexOf('\n')) >= 0) {
          line(buffer.slice(0, newline)); buffer = buffer.slice(newline + 1)
        }
        if (done) { if (buffer) line(buffer); dispatch(); break }
      }
      if (!terminal && !signal.aborted) fail('AI 响应意外中断，请重试')
    } catch (error) {
      if (timedOut) fail('AI 响应超时，请重试')
      else if (!signal.aborted) fail(error instanceof Error ? error.message : '网络连接失败，请重试')
    } finally {
      clearTimeout(timer)
      try { await reader?.cancel() } catch { /* disconnected */ }
      reader?.releaseLock()
      onFinally?.()
    }
  })()
  return controller
}
