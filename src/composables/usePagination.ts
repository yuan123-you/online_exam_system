import { computed, ref, watch, type Ref, type ComputedRef } from 'vue'

/**
 * 通用分页配置选项
 */
export interface PaginationOptions {
  /** 默认每页条数，默认 20 */
  defaultPageSize?: number
  /** 可选的每页条数列表，默认 [10, 20, 50, 100] */
  pageSizeOptions?: number[]
  /** 是否显示每页条数选择器，默认 true */
  showPageSize?: boolean
}

/**
 * 通用分页返回值
 */
export interface PaginationReturn<T> {
  /** 当前页数据 */
  paginatedData: ComputedRef<T[]>
  /** 当前页码（从 1 开始） */
  currentPage: Ref<number>
  /** 每页条数 */
  pageSize: Ref<number>
  /** 总记录数 */
  total: ComputedRef<number>
  /** 总页数 */
  totalPages: ComputedRef<number>
  /** 是否有数据 */
  hasData: ComputedRef<boolean>
  /** 跳转到指定页 */
  goToPage: (page: number) => void
  /** 修改每页条数 */
  changePageSize: (size: number) => void
  /** 重置到第一页 */
  resetPage: () => void
  /** 可选的每页条数列表 */
  pageSizeOptions: number[]
  /** 是否显示每页条数选择器 */
  showPageSize: boolean
}

/**
 * 客户端分页 composable
 *
 * 对已有的全量数据进行前端切片分页，适用于数据量不大、
 * 已通过 bootstrap 或其他方式获取了全量数据的场景。
 *
 * @param dataRef 响应式的全量数据源（computed 或 ref）
 * @param options 分页配置
 */
export function useClientPagination<T>(
  dataRef: Ref<T[]> | ComputedRef<T[]>,
  options: PaginationOptions = {}
): PaginationReturn<T> {
  const defaultPageSize = options.defaultPageSize ?? 20
  const pageSizeOptions = options.pageSizeOptions ?? [10, 20, 50, 100]
  const showPageSize = options.showPageSize ?? true

  const currentPage = ref(1)
  const pageSize = ref(defaultPageSize)

  const total = computed(() => dataRef.value.length)
  const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
  const hasData = computed(() => total.value > 0)

  const paginatedData = computed(() => {
    const start = (currentPage.value - 1) * pageSize.value
    return dataRef.value.slice(start, start + pageSize.value)
  })

  // 当数据源变化导致当前页超出范围时，自动修正
  watch(totalPages, (newTotal) => {
    if (currentPage.value > newTotal) {
      currentPage.value = Math.max(1, newTotal)
    }
  })

  function goToPage(page: number) {
    if (page < 1 || page > totalPages.value) return
    currentPage.value = page
  }

  function changePageSize(size: number) {
    if (size === pageSize.value) return
    // 修改每页条数时，尽量保持当前数据可见
    const firstItemIndex = (currentPage.value - 1) * pageSize.value
    pageSize.value = size
    currentPage.value = Math.max(1, Math.floor(firstItemIndex / size) + 1)
  }

  function resetPage() {
    currentPage.value = 1
  }

  return {
    paginatedData,
    currentPage,
    pageSize,
    total,
    totalPages,
    hasData,
    goToPage,
    changePageSize,
    resetPage,
    pageSizeOptions,
    showPageSize,
  }
}

/**
 * 服务端分页状态管理 composable
 *
 * 仅管理分页状态（页码、每页条数），实际数据获取由外部处理。
 * 适用于需要从服务端获取分页数据的场景。
 */
export function useServerPagination(options: PaginationOptions = {}) {
  const defaultPageSize = options.defaultPageSize ?? 20
  const pageSizeOptions = options.pageSizeOptions ?? [10, 20, 50, 100]
  const showPageSize = options.showPageSize ?? true

  const currentPage = ref(1)
  const pageSize = ref(defaultPageSize)
  const total = ref(0)
  const loading = ref(false)
  const error = ref<string | null>(null)

  const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
  const hasData = computed(() => total.value > 0)

  function goToPage(page: number) {
    if (page < 1 || page > totalPages.value) return
    currentPage.value = page
  }

  function changePageSize(size: number) {
    if (size === pageSize.value) return
    const firstItemIndex = (currentPage.value - 1) * pageSize.value
    pageSize.value = size
    currentPage.value = Math.max(1, Math.floor(firstItemIndex / size) + 1)
  }

  function resetPage() {
    currentPage.value = 1
  }

  function setTotal(count: number) {
    total.value = count
    // 修正当前页
    const pages = Math.max(1, Math.ceil(count / pageSize.value))
    if (currentPage.value > pages) {
      currentPage.value = Math.max(1, pages)
    }
  }

  return {
    currentPage,
    pageSize,
    total,
    totalPages,
    hasData,
    loading,
    error,
    goToPage,
    changePageSize,
    resetPage,
    setTotal,
    pageSizeOptions,
    showPageSize,
  }
}
