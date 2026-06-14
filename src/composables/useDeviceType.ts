import { ref, onMounted, onBeforeUnmount } from 'vue'

export function useDeviceType() {
  const isMobile = ref(false)
  const isTablet = ref(false)
  const isDesktop = ref(true)
  const isLandscape = ref(false)
  const isPortrait = ref(true)

  let rafId = 0

  function update() {
    const w = window.innerWidth
    isMobile.value = w < 768
    isTablet.value = w >= 768 && w <= 1024
    isDesktop.value = w > 1024
    isLandscape.value = window.innerWidth > window.innerHeight
    isPortrait.value = !isLandscape.value
  }

  function onResize() {
    // Debounce via requestAnimationFrame
    if (rafId) cancelAnimationFrame(rafId)
    rafId = requestAnimationFrame(update)
  }

  onMounted(() => {
    update()
    window.addEventListener('resize', onResize, { passive: true })
    window.addEventListener('orientationchange', () => {
      // orientationchange fires before layout recalc; delay slightly
      setTimeout(update, 100)
    })
  })

  onBeforeUnmount(() => {
    window.removeEventListener('resize', onResize)
    if (rafId) cancelAnimationFrame(rafId)
  })

  return { isMobile, isTablet, isDesktop, isLandscape, isPortrait }
}
