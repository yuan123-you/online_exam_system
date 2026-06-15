import { ref, onMounted, onBeforeUnmount, type Ref } from 'vue'

/** 可配置的自动滚动选项 */
export interface AutoScrollOptions {
  /** 距底部视窗高度比例，低于此值视为"在底部附近"，默认 0.2 (20%) */
  nearBottomRatio?: number
  /** 平滑滚动动画时长 (ms)，默认 300 */
  scrollDuration?: number
  /** 流式内容更新时的即时滚动时长 (ms)，默认 0（立即跳转） */
  streamingScrollDuration?: number
}

/**
 * 智能自动滚动 composable
 *
 * 功能：
 * 1. 用户手动上滚 → 暂停自动跟随
 * 2. 用户滚回底部附近 → 自动恢复跟随
 * 3. 提供 showScrollToBottom 响应式状态，控制"返回底部"按钮显隐
 * 4. scrollToBottom() 平滑滚动到底部
 * 5. 阈值可配置
 */
export function useAutoScroll(
  getContainer: () => HTMLElement | null,
  options: AutoScrollOptions = {}
) {
  const nearBottomRatio = options.nearBottomRatio ?? 0.2
  const scrollDuration = options.scrollDuration ?? 300
  const streamingScrollDuration = options.streamingScrollDuration ?? 0

  const userScrolledUp = ref(false)
  const showScrollToBottom = ref(false)
  let scrollRafId = 0

  /** 判断当前是否在底部附近 */
  function isNearBottom(): boolean {
    const el = getContainer()
    if (!el) return true
    const distFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight
    const threshold = el.clientHeight * nearBottomRatio
    return distFromBottom <= threshold
  }

  /** 滚动事件处理 */
  function onScroll() {
    const nearBottom = isNearBottom()
    userScrolledUp.value = !nearBottom
    showScrollToBottom.value = !nearBottom
  }

  /** 平滑滚动到底部 */
  function scrollToBottom(duration?: number) {
    const el = getContainer()
    if (!el) return
    if (scrollRafId) cancelAnimationFrame(scrollRafId)

    const dur = duration ?? scrollDuration

    if (dur <= 0) {
      el.scrollTop = el.scrollHeight
      userScrolledUp.value = false
      showScrollToBottom.value = false
      return
    }

    const startTop = el.scrollTop
    const targetTop = el.scrollHeight - el.clientHeight
    const distance = targetTop - startTop
    if (distance <= 0) {
      userScrolledUp.value = false
      showScrollToBottom.value = false
      return
    }

    const startTime = performance.now()
    function animate(now: number) {
      const elapsed = now - startTime
      const progress = Math.min(elapsed / dur, 1)
      // ease-out cubic
      const eased = 1 - Math.pow(1 - progress, 3)
      if (el) el.scrollTop = startTop + distance * eased
      if (progress < 1) {
        scrollRafId = requestAnimationFrame(animate)
      } else {
        scrollRafId = 0
        userScrolledUp.value = false
        showScrollToBottom.value = false
      }
    }
    scrollRafId = requestAnimationFrame(animate)
  }

  /** 流式内容更新时调用：仅当用户未上滚时自动跟随 */
  function onStreamingUpdate() {
    if (!userScrolledUp.value) {
      scrollToBottom(streamingScrollDuration)
    }
  }

  /** 新消息到达时调用：重置跟随状态并滚动 */
  function onNewMessage() {
    userScrolledUp.value = false
    showScrollToBottom.value = false
    scrollToBottom(scrollDuration)
  }

  /** 点击"返回底部"按钮 */
  function handleScrollToBottom() {
    scrollToBottom(scrollDuration)
  }

  onMounted(() => {
    const el = getContainer()
    if (el) {
      el.addEventListener('scroll', onScroll, { passive: true })
    }
  })

  onBeforeUnmount(() => {
    const el = getContainer()
    if (el) {
      el.removeEventListener('scroll', onScroll)
    }
    if (scrollRafId) cancelAnimationFrame(scrollRafId)
  })

  return {
    userScrolledUp,
    showScrollToBottom,
    scrollToBottom,
    onStreamingUpdate,
    onNewMessage,
    handleScrollToBottom,
    onScroll, // 暴露以便手动绑定/解绑
  }
}
