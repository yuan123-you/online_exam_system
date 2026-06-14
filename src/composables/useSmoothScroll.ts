import { onMounted, onBeforeUnmount } from 'vue'

/**
 * Normalizes wheel scroll speed across scrollable containers.
 * Caps deltaY to prevent excessively large scroll jumps (especially on
 * high-resolution mice / trackpads) and adds smooth interpolation.
 */
const MAX_WHEEL_DELTA = 120   // maximum pixels per wheel tick
const SCROLL_MULTIPLIER = 1.0 // overall sensitivity (1.0 = default)

export function useSmoothScroll(containerSelector: string = '.content-panel, .sidebar-body, .table-wrap, .modal-card, .chat-main') {
  let rafId = 0

  function normalizeWheel(e: WheelEvent) {
    const el = e.currentTarget as HTMLElement
    if (!el) return

    // Only handle vertical scroll
    if (e.deltaY === 0) return

    // Cap the delta to prevent huge jumps
    const rawDelta = e.deltaY
    const cappedDelta = Math.sign(rawDelta) * Math.min(Math.abs(rawDelta), MAX_WHEEL_DELTA)
    const finalDelta = cappedDelta * SCROLL_MULTIPLIER

    // If the browser reports line-mode or page-mode delta, convert to pixel
    if (e.deltaMode === 1) {
      // line mode: ~40px per line
      e.preventDefault()
      el.scrollTop += finalDelta * 40
      return
    }
    if (e.deltaMode === 2) {
      // page mode
      e.preventDefault()
      el.scrollTop += finalDelta * el.clientHeight
      return
    }

    // pixel mode (default for most modern browsers)
    // Only intercept if the raw delta is abnormally large
    if (Math.abs(rawDelta) > MAX_WHEEL_DELTA) {
      e.preventDefault()
      el.scrollTop += finalDelta
    }
  }

  function attach() {
    const containers = document.querySelectorAll<HTMLElement>(containerSelector)
    containers.forEach((el) => {
      el.addEventListener('wheel', normalizeWheel, { passive: false })
    })
  }

  function detach() {
    const containers = document.querySelectorAll<HTMLElement>(containerSelector)
    containers.forEach((el) => {
      el.removeEventListener('wheel', normalizeWheel)
    })
    if (rafId) cancelAnimationFrame(rafId)
  }

  onMounted(() => {
    // Delay slightly so DOM is fully rendered
    rafId = requestAnimationFrame(attach)
  })
  onBeforeUnmount(detach)

  return { attach, detach }
}
