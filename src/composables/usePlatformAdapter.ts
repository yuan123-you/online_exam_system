import { ref } from 'vue'
import router from '@/router'

/** Supported runtime platforms. */
export type Platform = 'web' | 'miniprogram' | 'native'

/** Navigation result descriptor. */
export interface NavResult {
  type: 'router' | 'miniprogram' | 'unknown'
  path: string
}

/**
 * Platform adaptation layer for cross-platform compatibility.
 *
 * Currently targets web (Vue Router + browser APIs). Designed to be
 * extended for WeChat mini-program (wx.*) or native shells in the future.
 *
 * Usage:
 *   const { platform, navigateTo, getStorage, setStorage, getBaseUrl } = usePlatformAdapter()
 */
export function usePlatformAdapter() {
  /** Current runtime platform (detected once at init). */
  const platform = ref<Platform>(detectPlatform())

  // -- In-memory storage fallback (avoids localStorage per project rules) --
  const memoryStore = new Map<string, string>()

  /**
   * Navigate to a path using the platform-appropriate mechanism.
   * On web this delegates to vue-router; on mini-program it would call
   * wx.navigateTo / wx.switchTab, etc.
   */
  function navigateTo(path: string): NavResult {
    if (platform.value === 'web') {
      router.push(path)
      return { type: 'router', path }
    }
    // Future: wx.navigateTo({ url: path })
    return { type: 'unknown', path }
  }

  /**
   * Read a value from platform storage.
   * On web we use an in-memory Map (no persistence across reloads).
   */
  function getStorage(key: string): string | null {
    if (platform.value === 'web') {
      return memoryStore.get(key) ?? null
    }
    // Future: wx.getStorageSync(key)
    return null
  }

  /**
   * Write a value to platform storage.
   */
  function setStorage(key: string, value: string): void {
    if (platform.value === 'web') {
      memoryStore.set(key, value)
      return
    }
    // Future: wx.setStorageSync(key, value)
  }

  /** Remove a value from platform storage. */
  function removeStorage(key: string): void {
    memoryStore.delete(key)
  }

  /** Clear all values from platform storage. */
  function clearStorage(): void {
    memoryStore.clear()
  }

  /**
   * Return the base URL for API calls. All platforms currently share
   * the same Vite dev-server proxy, but this can diverge later.
   */
  function getBaseUrl(): string {
    return '/api'
  }

  return {
    platform,
    navigateTo,
    getStorage,
    setStorage,
    removeStorage,
    clearStorage,
    getBaseUrl,
  }
}

// ---------- helpers ----------

function detectPlatform(): Platform {
  // Simple UA-based detection; extend for real mini-program environments.
  if (typeof navigator !== 'undefined' && /MicroMessenger/i.test(navigator.userAgent)) {
    // Running inside WeChat browser – could be miniprogram webview or regular browser.
    // For now treat as web; real detection requires wx SDK bridge.
  }
  return 'web'
}
