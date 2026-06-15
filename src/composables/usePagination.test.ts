import { describe, it, expect, vi } from 'vitest'
import { ref, computed, nextTick } from 'vue'
import { useClientPagination, useServerPagination } from './usePagination'

// ================================================================
// useClientPagination 测试
// ================================================================
describe('useClientPagination', () => {
  function createPagination(data: any[], options = {}) {
    const dataRef = ref(data) as any
    return { dataRef, ...useClientPagination(dataRef, options) }
  }

  // ---- 基本功能 ----
  describe('基本功能', () => {
    it('正确计算 total 和 totalPages', () => {
      const p = createPagination([1, 2, 3, 4, 5], { defaultPageSize: 2 })
      expect(p.total.value).toBe(5)
      expect(p.totalPages.value).toBe(3) // ceil(5/2) = 3
    })

    it('空数据时 totalPages 为 1，hasData 为 false', () => {
      const p = createPagination([])
      expect(p.total.value).toBe(0)
      expect(p.totalPages.value).toBe(1)
      expect(p.hasData.value).toBe(false)
    })

    it('数据不足一页时 totalPages 为 1', () => {
      const p = createPagination([1, 2, 3], { defaultPageSize: 10 })
      expect(p.totalPages.value).toBe(1)
      expect(p.hasData.value).toBe(true)
    })

    it('数据恰好整除时 totalPages 正确', () => {
      const p = createPagination([1, 2, 3, 4], { defaultPageSize: 2 })
      expect(p.totalPages.value).toBe(2)
    })

    it('默认每页条数为 20', () => {
      const p = createPagination(Array.from({ length: 50 }, (_, i) => i))
      expect(p.pageSize.value).toBe(20)
      expect(p.totalPages.value).toBe(3) // ceil(50/20) = 3
    })

    it('自定义默认每页条数', () => {
      const p = createPagination(Array.from({ length: 50 }, (_, i) => i), { defaultPageSize: 15 })
      expect(p.pageSize.value).toBe(15)
      expect(p.totalPages.value).toBe(4) // ceil(50/15) = 4
    })
  })

  // ---- 数据切片 ----
  describe('数据切片', () => {
    it('第一页返回正确的数据切片', () => {
      const p = createPagination([1, 2, 3, 4, 5], { defaultPageSize: 2 })
      expect(p.paginatedData.value).toEqual([1, 2])
    })

    it('最后一页可能不满 pageSize', () => {
      const p = createPagination([1, 2, 3, 4, 5], { defaultPageSize: 2 })
      p.goToPage(3)
      expect(p.paginatedData.value).toEqual([5])
    })

    it('空数据时 paginatedData 为空数组', () => {
      const p = createPagination([])
      expect(p.paginatedData.value).toEqual([])
    })

    it('数据恰好一页时返回全部数据', () => {
      const data = [1, 2, 3]
      const p = createPagination(data, { defaultPageSize: 10 })
      expect(p.paginatedData.value).toEqual(data)
    })
  })

  // ---- goToPage ----
  describe('goToPage', () => {
    it('跳转到指定页', () => {
      const p = createPagination([1, 2, 3, 4, 5], { defaultPageSize: 2 })
      p.goToPage(2)
      expect(p.currentPage.value).toBe(2)
      expect(p.paginatedData.value).toEqual([3, 4])
    })

    it('跳转到第一页', () => {
      const p = createPagination([1, 2, 3, 4, 5], { defaultPageSize: 2 })
      p.goToPage(3)
      p.goToPage(1)
      expect(p.currentPage.value).toBe(1)
    })

    it('跳转到最后一页', () => {
      const p = createPagination([1, 2, 3, 4, 5], { defaultPageSize: 2 })
      p.goToPage(3)
      expect(p.currentPage.value).toBe(3)
    })

    it('小于 1 的页码被忽略', () => {
      const p = createPagination([1, 2, 3], { defaultPageSize: 2 })
      p.goToPage(0)
      expect(p.currentPage.value).toBe(1)
      p.goToPage(-1)
      expect(p.currentPage.value).toBe(1)
    })

    it('超过总页数的页码被忽略', () => {
      const p = createPagination([1, 2, 3], { defaultPageSize: 2 })
      p.goToPage(100)
      expect(p.currentPage.value).toBe(1)
    })
  })

  // ---- changePageSize ----
  describe('changePageSize', () => {
    it('修改每页条数后 totalPages 更新', () => {
      const p = createPagination([1, 2, 3, 4, 5], { defaultPageSize: 2 })
      p.changePageSize(5)
      expect(p.pageSize.value).toBe(5)
      expect(p.totalPages.value).toBe(1)
    })

    it('修改每页条数时尽量保持当前数据可见', () => {
      const p = createPagination(Array.from({ length: 100 }, (_, i) => i), { defaultPageSize: 10 })
      p.goToPage(5) // 第5页，显示索引40-49
      p.changePageSize(20)
      // 索引40在新的每页20条下，应该在第 ceil(41/20)=3 页
      expect(p.currentPage.value).toBe(3)
    })

    it('相同的 pageSize 不触发变更', () => {
      const p = createPagination([1, 2, 3], { defaultPageSize: 10 })
      p.changePageSize(10)
      expect(p.pageSize.value).toBe(10)
    })

    it('修改 pageSize 后数据切片正确', () => {
      const p = createPagination([1, 2, 3, 4, 5, 6], { defaultPageSize: 2 })
      p.changePageSize(3)
      expect(p.paginatedData.value).toEqual([1, 2, 3])
    })
  })

  // ---- resetPage ----
  describe('resetPage', () => {
    it('重置到第一页', () => {
      const p = createPagination([1, 2, 3, 4, 5], { defaultPageSize: 2 })
      p.goToPage(3)
      p.resetPage()
      expect(p.currentPage.value).toBe(1)
    })
  })

  // ---- 数据源变化时自动修正 ----
  describe('数据源变化时自动修正', () => {
    it('当前页超出范围时自动修正到最后有效页', async () => {
      const data = ref([1, 2, 3, 4, 5, 6, 7, 8, 9, 10])
      const p = useClientPagination(data, { defaultPageSize: 2 })
      p.goToPage(5) // 第5页
      expect(p.currentPage.value).toBe(5)

      // 缩减数据到只有3条，总页数变为2
      data.value = [1, 2, 3]
      await nextTick()
      expect(p.totalPages.value).toBe(2)
      expect(p.currentPage.value).toBe(2) // 自动修正到第2页
    })

    it('数据清空时自动修正到第1页', async () => {
      const data = ref([1, 2, 3, 4, 5])
      const p = useClientPagination(data, { defaultPageSize: 2 })
      p.goToPage(3)
      data.value = []
      await nextTick()
      expect(p.currentPage.value).toBe(1)
    })
  })

  // ---- 配置选项 ----
  describe('配置选项', () => {
    it('自定义 pageSizeOptions', () => {
      const p = createPagination([], { pageSizeOptions: [5, 15, 30] })
      expect(p.pageSizeOptions).toEqual([5, 15, 30])
    })

    it('默认 pageSizeOptions 为 [10, 20, 50, 100]', () => {
      const p = createPagination([])
      expect(p.pageSizeOptions).toEqual([10, 20, 50, 100])
    })

    it('showPageSize 默认为 true', () => {
      const p = createPagination([])
      expect(p.showPageSize).toBe(true)
    })

    it('showPageSize 可设为 false', () => {
      const p = createPagination([], { showPageSize: false })
      expect(p.showPageSize).toBe(false)
    })
  })
})

// ================================================================
// useServerPagination 测试
// ================================================================
describe('useServerPagination', () => {
  function createServerPagination(options = {}) {
    return useServerPagination(options)
  }

  // ---- 初始状态 ----
  describe('初始状态', () => {
    it('默认初始状态', () => {
      const p = createServerPagination()
      expect(p.currentPage.value).toBe(1)
      expect(p.pageSize.value).toBe(20)
      expect(p.total.value).toBe(0)
      expect(p.totalPages.value).toBe(1)
      expect(p.hasData.value).toBe(false)
      expect(p.loading.value).toBe(false)
      expect(p.error.value).toBeNull()
    })

    it('自定义默认每页条数', () => {
      const p = createServerPagination({ defaultPageSize: 15 })
      expect(p.pageSize.value).toBe(15)
    })
  })

  // ---- setTotal ----
  describe('setTotal', () => {
    it('设置总数后 totalPages 和 hasData 更新', () => {
      const p = createServerPagination({ defaultPageSize: 10 })
      p.setTotal(25)
      expect(p.total.value).toBe(25)
      expect(p.totalPages.value).toBe(3) // ceil(25/10) = 3
      expect(p.hasData.value).toBe(true)
    })

    it('设置总数为 0 时 hasData 为 false', () => {
      const p = createServerPagination()
      p.setTotal(0)
      expect(p.hasData.value).toBe(false)
      expect(p.totalPages.value).toBe(1)
    })

    it('当前页超出范围时自动修正', () => {
      const p = createServerPagination({ defaultPageSize: 10 })
      p.setTotal(50) // 5页
      p.goToPage(5)
      expect(p.currentPage.value).toBe(5)
      p.setTotal(10) // 缩减到1页
      expect(p.currentPage.value).toBe(1)
    })

    it('当前页在范围内时保持不变', () => {
      const p = createServerPagination({ defaultPageSize: 10 })
      p.setTotal(50) // 5页
      p.goToPage(3)
      p.setTotal(40) // 4页
      expect(p.currentPage.value).toBe(3)
    })
  })

  // ---- goToPage ----
  describe('goToPage', () => {
    it('跳转到指定页', () => {
      const p = createServerPagination({ defaultPageSize: 10 })
      p.setTotal(50)
      p.goToPage(3)
      expect(p.currentPage.value).toBe(3)
    })

    it('小于 1 被忽略', () => {
      const p = createServerPagination()
      p.setTotal(50)
      p.goToPage(0)
      expect(p.currentPage.value).toBe(1)
    })

    it('超过总页数被忽略', () => {
      const p = createServerPagination()
      p.setTotal(50)
      p.goToPage(100)
      expect(p.currentPage.value).toBe(1)
    })
  })

  // ---- changePageSize ----
  describe('changePageSize', () => {
    it('修改每页条数后 totalPages 更新', () => {
      const p = createServerPagination({ defaultPageSize: 10 })
      p.setTotal(50)
      p.changePageSize(25)
      expect(p.pageSize.value).toBe(25)
      expect(p.totalPages.value).toBe(2)
    })

    it('修改每页条数时尽量保持当前数据可见', () => {
      const p = createServerPagination({ defaultPageSize: 10 })
      p.setTotal(100)
      p.goToPage(5) // 第5页，索引40-49
      p.changePageSize(20)
      // 索引40在新的每页20条下，第 ceil(41/20)=3 页
      expect(p.currentPage.value).toBe(3)
    })

    it('相同的 pageSize 不触发变更', () => {
      const p = createServerPagination({ defaultPageSize: 10 })
      p.setTotal(50)
      p.changePageSize(10)
      expect(p.pageSize.value).toBe(10)
    })
  })

  // ---- resetPage ----
  describe('resetPage', () => {
    it('重置到第一页', () => {
      const p = createServerPagination()
      p.setTotal(50)
      p.goToPage(3)
      p.resetPage()
      expect(p.currentPage.value).toBe(1)
    })
  })

  // ---- loading 和 error ----
  describe('loading 和 error 状态', () => {
    it('可以设置 loading 状态', () => {
      const p = createServerPagination()
      p.loading.value = true
      expect(p.loading.value).toBe(true)
    })

    it('可以设置 error 状态', () => {
      const p = createServerPagination()
      p.error.value = '网络错误'
      expect(p.error.value).toBe('网络错误')
    })
  })

  // ---- 配置选项 ----
  describe('配置选项', () => {
    it('自定义 pageSizeOptions', () => {
      const p = createServerPagination({ pageSizeOptions: [5, 15, 30] })
      expect(p.pageSizeOptions).toEqual([5, 15, 30])
    })

    it('showPageSize 默认为 true', () => {
      const p = createServerPagination()
      expect(p.showPageSize).toBe(true)
    })
  })

  // ---- 边界情况 ----
  describe('边界情况', () => {
    it('total 为 1 时 totalPages 为 1', () => {
      const p = createServerPagination({ defaultPageSize: 10 })
      p.setTotal(1)
      expect(p.totalPages.value).toBe(1)
    })

    it('total 恰好整除 pageSize', () => {
      const p = createServerPagination({ defaultPageSize: 10 })
      p.setTotal(30)
      expect(p.totalPages.value).toBe(3)
    })

    it('changePageSize 后当前页超出范围时修正', () => {
      const p = createServerPagination({ defaultPageSize: 10 })
      p.setTotal(50) // 5页
      p.goToPage(5)
      p.changePageSize(50) // 变为1页
      expect(p.currentPage.value).toBe(1)
    })
  })
})
