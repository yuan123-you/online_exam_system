import { ref } from 'vue'

export interface Toast {
  id: number
  message: string
  type: 'success' | 'error' | 'warning' | 'info'
  duration: number
}

const toasts = ref<Toast[]>([])
let nextId = 0
// 记录每个 toast 的持续时间，便于在模板中驱动进度条动画
const durations = new Map<number, number>()

function remove(id: number) {
  toasts.value = toasts.value.filter(t => t.id !== id)
  durations.delete(id)
}

function show(message: string, type: Toast['type'] = 'info', duration = 3000) {
  const id = nextId++
  const safeDuration = duration > 0 ? duration : 3000
  durations.set(id, safeDuration)
  toasts.value.push({ id, message, type, duration: safeDuration })
  if (duration > 0) {
    setTimeout(() => remove(id), duration)
  }
}

function success(message: string) { show(message, 'success') }
function error(message: string) { show(message, 'error', 5000) }
function warning(message: string) { show(message, 'warning') }
function info(message: string) { show(message, 'info') }

function dismiss(id: number) {
  remove(id)
}

export function useToast() {
  return {
    toasts,
    show,
    success,
    error,
    warning,
    info,
    dismiss,
    getDuration: (id: number) => durations.get(id) ?? 3000,
  }
}
