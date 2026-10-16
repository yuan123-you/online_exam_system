import { onMounted, onBeforeUnmount } from 'vue'

export interface SmoothScrollOptions {
  selector?: string
  maxDelta?: number
  multiplier?: number
  duration?: number
  easing?: (t: number) => number
  inertia?: boolean
  friction?: number
}

/**
 * 平滑滚动 composable
 * 采用浏览器硬件加速的原生平滑滚动机制，避免 JS RAF 动画循环与浏览器排版引擎冲突导致卡顿或滑动受阻。
 */
export function useSmoothScroll(options: SmoothScrollOptions = {}) {
  const {
    selector = '.content-panel, .sidebar-body, .table-wrap, .modal-card, .chat-main, .exam-question-body, .grading-question-body',
  } = options

  let containers: HTMLElement[] = []

  function attach() {
    containers = Array.from(document.querySelectorAll<HTMLElement>(selector))
    containers.forEach((el) => {
      el.style.scrollBehavior = 'smooth'
      ;(el.style as any).webkitOverflowScrolling = 'touch'
    })
  }

  function detach() {
    containers.forEach((el) => {
      el.style.scrollBehavior = ''
    })
    containers = []
  }

  function scrollToTop(el?: HTMLElement) {
    const target = el || document.querySelector<HTMLElement>('.content-panel')
    if (target) {
      target.scrollTo({ top: 0, behavior: 'smooth' })
    }
  }

  function scrollToElement(targetEl: HTMLElement, containerEl?: HTMLElement) {
    const container = containerEl || document.querySelector<HTMLElement>('.content-panel')
    if (container && targetEl) {
      const offsetTop = targetEl.offsetTop - container.offsetTop
      container.scrollTo({ top: Math.max(0, offsetTop), behavior: 'smooth' })
    }
  }

  onMounted(() => {
    requestAnimationFrame(attach)
  })

  onBeforeUnmount(detach)

  return { attach, detach, scrollToTop, scrollToElement }
}
