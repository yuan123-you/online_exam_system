import { ref, watch, type Ref } from 'vue'

/**
 * 防抖 ref — 输入时延迟更新，减少 API 请求和计算频率
 * @param delay 防抖延迟（毫秒），默认 300ms
 * @returns [debouncedValue, inputValue, setInput]
 *   - debouncedValue: 防抖后的值（用于 API 调用 / computed 过滤）
 *   - inputValue: 即时值（用于 v-model 绑定输入框）
 *   - setInput: 手动设置值（跳过防抖，如清空搜索时）
 */
export function useDebouncedRef<T = string>(initialValue: T, delay = 300) {
  const inputValue = ref<T>(initialValue)
  const debouncedValue = ref<T>(initialValue)
  let timer: ReturnType<typeof setTimeout> | null = null

  watch(inputValue, (val) => {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      debouncedValue.value = val
    }, delay)
  })

  /** 手动设置值（立即同步，跳过防抖） */
  function setValue(val: T) {
    if (timer) clearTimeout(timer)
    inputValue.value = val
    debouncedValue.value = val
  }

  return { debouncedValue, inputValue, setValue }
}
