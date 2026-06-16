import { onMounted, onBeforeUnmount } from 'vue'

/**
 * 平滑滚动 composable
 *
 * 功能：
 * 1. 限制单次滚轮事件最大滚动距离，防止高分辨率鼠标/触控板产生过大跳跃
 * 2. 使用 requestAnimationFrame + 缓动函数实现平滑插值滚动
 * 3. 支持惯性滚动，让滚动更自然流畅
 * 4. 可配置选择器、最大滚动距离、动画时长等参数
 * 5. 自动处理 line-mode / page-mode delta 转换
 */

export interface SmoothScrollOptions {
  /** 要绑定平滑滚动的容器选择器，默认覆盖主要滚动区域 */
  selector?: string
  /** 单次滚轮事件最大滚动像素，默认 150 */
  maxDelta?: number
  /** 滚动灵敏度倍率，默认 1.0 */
  multiplier?: number
  /** 平滑动画时长 (ms)，默认 180 */
  duration?: number
  /** 缓动函数，默认 ease-out cubic */
  easing?: (t: number) => number
  /** 是否启用惯性效果，默认 true */
  inertia?: boolean
  /** 惯性摩擦系数 (0~1)，越小减速越快，默认 0.92 */
  friction?: number
}

/** ease-out cubic 缓动 */
function easeOutCubic(t: number): number {
  return 1 - Math.pow(1 - t, 3)
}

interface ScrollState {
  /** 当前动画帧 ID */
  rafId: number
  /** 目标滚动位置 */
  targetTop: number
  /** 动画起始位置 */
  startTop: number
  /** 动画开始时间 */
  startTime: number
  /** 是否正在动画中 */
  animating: boolean
  /** 惯性速度 */
  velocity: number
  /** 惯性动画帧 ID */
  inertiaRafId: number
  /** 上次滚轮事件时间 */
  lastWheelTime: number
}

export function useSmoothScroll(options: SmoothScrollOptions = {}) {
  const {
    selector = '.content-panel, .sidebar-body, .table-wrap, .modal-card, .chat-main, .exam-question-body, .grading-question-body',
    maxDelta = 150,
    multiplier = 1.0,
    duration = 180,
    easing = easeOutCubic,
    inertia = true,
    friction = 0.92,
  } = options

  const scrollStates = new WeakMap<HTMLElement, ScrollState>()

  function getState(el: HTMLElement): ScrollState {
    if (!scrollStates.has(el)) {
      scrollStates.set(el, {
        rafId: 0,
        targetTop: el.scrollTop,
        startTop: 0,
        startTime: 0,
        animating: false,
        velocity: 0,
        inertiaRafId: 0,
        lastWheelTime: 0,
      })
    }
    return scrollStates.get(el)!
  }

  /** 取消正在进行的动画 */
  function cancelAnimation(state: ScrollState) {
    if (state.rafId) {
      cancelAnimationFrame(state.rafId)
      state.rafId = 0
    }
    if (state.inertiaRafId) {
      cancelAnimationFrame(state.inertiaRafId)
      state.inertiaRafId = 0
    }
    state.animating = false
  }

  /** 执行平滑滚动动画 */
  function animateScroll(el: HTMLElement, state: ScrollState) {
    const now = performance.now()
    const elapsed = now - state.startTime
    const progress = Math.min(elapsed / duration, 1)
    const easedProgress = easing(progress)

    el.scrollTop = state.startTop + (state.targetTop - state.startTop) * easedProgress

    if (progress < 1) {
      state.rafId = requestAnimationFrame(() => animateScroll(el, state))
    } else {
      el.scrollTop = state.targetTop
      state.animating = false
      state.rafId = 0

      // 动画结束后启动惯性
      if (inertia && Math.abs(state.velocity) > 0.5) {
        startInertia(el, state)
      } else {
        state.velocity = 0
      }
    }
  }

  /** 惯性滚动 */
  function startInertia(el: HTMLElement, state: ScrollState) {
    function inertiaStep() {
      state.velocity *= friction

      if (Math.abs(state.velocity) < 0.5) {
        state.velocity = 0
        state.inertiaRafId = 0
        return
      }

      const newTop = el.scrollTop + state.velocity
      // 边界检测
      if (newTop <= 0 || newTop >= el.scrollHeight - el.clientHeight) {
        state.velocity = 0
        state.inertiaRafId = 0
        return
      }

      el.scrollTop = newTop
      state.inertiaRafId = requestAnimationFrame(inertiaStep)
    }

    state.inertiaRafId = requestAnimationFrame(inertiaStep)
  }

  /** 滚轮事件处理 */
  function normalizeWheel(e: WheelEvent) {
    const el = e.currentTarget as HTMLElement
    if (!el) return

    // 只处理纵向滚动
    if (e.deltaY === 0) return

    const state = getState(el)
    const now = performance.now()

    // 计算实际 delta
    let rawDelta = e.deltaY

    // line-mode / page-mode 转换
    if (e.deltaMode === 1) {
      // line mode: ~40px per line
      rawDelta *= 40
    } else if (e.deltaMode === 2) {
      // page mode
      rawDelta *= el.clientHeight
    }

    // 限制单次最大滚动距离
    const cappedDelta = Math.sign(rawDelta) * Math.min(Math.abs(rawDelta), maxDelta)
    const finalDelta = cappedDelta * multiplier

    // 阻止浏览器默认滚动行为，由我们控制
    e.preventDefault()

    // 取消惯性
    if (state.inertiaRafId) {
      cancelAnimationFrame(state.inertiaRafId)
      state.inertiaRafId = 0
    }

    // 计算速度（用于惯性）
    const timeDelta = now - state.lastWheelTime
    if (timeDelta > 0 && timeDelta < 100) {
      state.velocity = state.velocity * 0.6 + (finalDelta / timeDelta) * 16 * 0.4
    } else {
      state.velocity = finalDelta / Math.max(duration, 1) * 16
    }
    state.lastWheelTime = now

    // 如果正在动画中，从当前位置继续
    if (state.animating) {
      state.targetTop += finalDelta
    } else {
      state.startTop = el.scrollTop
      state.targetTop = el.scrollTop + finalDelta
      state.startTime = now
      state.animating = true
      state.rafId = requestAnimationFrame(() => animateScroll(el, state))
    }

    // 边界限制
    const maxTop = el.scrollHeight - el.clientHeight
    state.targetTop = Math.max(0, Math.min(state.targetTop, maxTop))
  }

  let containers: HTMLElement[] = []

  function attach() {
    containers = Array.from(document.querySelectorAll<HTMLElement>(selector))
    containers.forEach((el) => {
      el.addEventListener('wheel', normalizeWheel, { passive: false })
    })
  }

  function detach() {
    containers.forEach((el) => {
      el.removeEventListener('wheel', normalizeWheel)
      const state = scrollStates.get(el)
      if (state) cancelAnimation(state)
    })
    containers = []
  }

  onMounted(() => {
    // 延迟一帧确保 DOM 渲染完成
    requestAnimationFrame(attach)
  })

  onBeforeUnmount(detach)

  return { attach, detach }
}
