import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import PaginationBar from '../common/PaginationBar.vue'

// ================================================================
// PaginationBar 组件测试
// ================================================================
describe('PaginationBar', () => {
  function mountBar(props = {}) {
    return mount(PaginationBar, {
      props: {
        total: 100,
        currentPage: 1,
        pageSize: 10,
        ...props,
      },
    })
  }

  // ---- 渲染 ----
  describe('渲染', () => {
    it('有数据时渲染分页导航', () => {
      const wrapper = mountBar({ total: 100, currentPage: 1, pageSize: 10 })
      expect(wrapper.find('nav.pagination-bar').exists()).toBe(true)
    })

    it('无数据时不渲染', () => {
      const wrapper = mountBar({ total: 0, currentPage: 1, pageSize: 10 })
      expect(wrapper.find('nav.pagination-bar').exists()).toBe(false)
    })

    it('显示总记录数', () => {
      const wrapper = mountBar({ total: 100, currentPage: 1, pageSize: 10 })
      expect(wrapper.find('.pagination-info').text()).toContain('100')
    })

    it('多页时显示当前页/总页数', () => {
      const wrapper = mountBar({ total: 100, currentPage: 3, pageSize: 10 })
      expect(wrapper.find('.pagination-info').text()).toContain('3')
      expect(wrapper.find('.pagination-info').text()).toContain('10')
    })

    it('只有一页时不显示页码导航按钮', () => {
      const wrapper = mountBar({ total: 5, currentPage: 1, pageSize: 10 })
      expect(wrapper.find('.pagination-btns').exists()).toBe(false)
    })

    it('只有一页时不显示跳转输入', () => {
      const wrapper = mountBar({ total: 5, currentPage: 1, pageSize: 10 })
      expect(wrapper.find('.page-jump').exists()).toBe(false)
    })
  })

  // ---- 页码导航 ----
  describe('页码导航', () => {
    it('渲染上一页和下一页按钮', () => {
      const wrapper = mountBar({ total: 100, currentPage: 1, pageSize: 10 })
      const buttons = wrapper.findAll('.pagination-btn')
      const texts = buttons.map(b => b.text())
      expect(texts.some(t => t.includes('上一页'))).toBe(true)
      expect(texts.some(t => t.includes('下一页'))).toBe(true)
    })

    it('第一页时上一页按钮禁用', () => {
      const wrapper = mountBar({ total: 100, currentPage: 1, pageSize: 10 })
      const prevBtn = wrapper.findAll('.pagination-btn').find(b => b.text().includes('上一页'))
      expect(prevBtn?.attributes('disabled')).toBeDefined()
    })

    it('最后一页时下一页按钮禁用', () => {
      const wrapper = mountBar({ total: 100, currentPage: 10, pageSize: 10 })
      const nextBtn = wrapper.findAll('.pagination-btn').find(b => b.text().includes('下一页'))
      expect(nextBtn?.attributes('disabled')).toBeDefined()
    })

    it('中间页时上一页和下一页都可用', () => {
      const wrapper = mountBar({ total: 100, currentPage: 5, pageSize: 10 })
      const prevBtn = wrapper.findAll('.pagination-btn').find(b => b.text().includes('上一页'))
      const nextBtn = wrapper.findAll('.pagination-btn').find(b => b.text().includes('下一页'))
      expect(prevBtn?.attributes('disabled')).toBeUndefined()
      expect(nextBtn?.attributes('disabled')).toBeUndefined()
    })

    it('当前页高亮显示', () => {
      const wrapper = mountBar({ total: 100, currentPage: 3, pageSize: 10 })
      const activeBtn = wrapper.find('.pagination-btn.active')
      expect(activeBtn.exists()).toBe(true)
      expect(activeBtn.text()).toBe('3')
    })

    it('点击页码按钮触发 page-change 事件', async () => {
      const wrapper = mountBar({ total: 100, currentPage: 1, pageSize: 10 })
      const pageBtn = wrapper.findAll('.pagination-btn').find(b => b.text() === '2')
      await pageBtn?.trigger('click')
      expect(wrapper.emitted('page-change')).toBeTruthy()
      expect(wrapper.emitted('page-change')![0]).toEqual([2])
    })

    it('点击下一页触发 page-change', async () => {
      const wrapper = mountBar({ total: 100, currentPage: 1, pageSize: 10 })
      const nextBtn = wrapper.findAll('.pagination-btn').find(b => b.text().includes('下一页'))
      await nextBtn?.trigger('click')
      expect(wrapper.emitted('page-change')![0]).toEqual([2])
    })

    it('点击上一页触发 page-change', async () => {
      const wrapper = mountBar({ total: 100, currentPage: 3, pageSize: 10 })
      const prevBtn = wrapper.findAll('.pagination-btn').find(b => b.text().includes('上一页'))
      await prevBtn?.trigger('click')
      expect(wrapper.emitted('page-change')![0]).toEqual([2])
    })

    it('点击当前页不触发事件', async () => {
      const wrapper = mountBar({ total: 100, currentPage: 3, pageSize: 10 })
      const activeBtn = wrapper.find('.pagination-btn.active')
      await activeBtn.trigger('click')
      expect(wrapper.emitted('page-change')).toBeFalsy()
    })
  })

  // ---- 省略号 ----
  describe('省略号', () => {
    it('页数较多时显示省略号', () => {
      const wrapper = mountBar({ total: 200, currentPage: 10, pageSize: 10, maxVisiblePages: 5 })
      const ellipsisBtns = wrapper.findAll('.pagination-btn.ellipsis')
      expect(ellipsisBtns.length).toBeGreaterThanOrEqual(1)
    })

    it('省略号按钮被禁用', () => {
      const wrapper = mountBar({ total: 200, currentPage: 10, pageSize: 10, maxVisiblePages: 5 })
      const ellipsisBtn = wrapper.findAll('.pagination-btn.ellipsis').at(0)
      expect(ellipsisBtn?.attributes('disabled')).toBeDefined()
    })

    it('页数较少时不显示省略号', () => {
      const wrapper = mountBar({ total: 50, currentPage: 1, pageSize: 10, maxVisiblePages: 5 })
      const ellipsisBtns = wrapper.findAll('.pagination-btn.ellipsis')
      expect(ellipsisBtns.length).toBe(0)
    })
  })

  // ---- 每页条数选择器 ----
  describe('每页条数选择器', () => {
    it('默认显示每页条数选择器', () => {
      const wrapper = mountBar({ total: 100, currentPage: 1, pageSize: 10 })
      expect(wrapper.find('.page-size-selector').exists()).toBe(true)
    })

    it('showPageSize 为 false 时不显示', () => {
      const wrapper = mountBar({ total: 100, currentPage: 1, pageSize: 10, showPageSize: false })
      expect(wrapper.find('.page-size-selector').exists()).toBe(false)
    })

    it('选择每页条数触发 page-size-change 事件', async () => {
      const wrapper = mountBar({ total: 100, currentPage: 1, pageSize: 10 })
      const select = wrapper.find('.page-size-select')
      await select.setValue('20')
      expect(wrapper.emitted('page-size-change')).toBeTruthy()
      expect(wrapper.emitted('page-size-change')![0]).toEqual([20])
    })

    it('自定义 pageSizeOptions', () => {
      const wrapper = mountBar({
        total: 100, currentPage: 1, pageSize: 5,
        pageSizeOptions: [5, 15, 30],
      })
      const options = wrapper.findAll('.page-size-select option')
      expect(options.length).toBe(3)
      expect(options[0].attributes('value')).toBe('5')
      expect(options[1].attributes('value')).toBe('15')
      expect(options[2].attributes('value')).toBe('30')
    })
  })

  // ---- 跳转输入 ----
  describe('跳转输入', () => {
    it('默认显示跳转输入', () => {
      const wrapper = mountBar({ total: 100, currentPage: 1, pageSize: 10 })
      expect(wrapper.find('.page-jump').exists()).toBe(true)
    })

    it('showPageJump 为 false 时不显示', () => {
      const wrapper = mountBar({ total: 100, currentPage: 1, pageSize: 10, showPageJump: false })
      expect(wrapper.find('.page-jump').exists()).toBe(false)
    })

    it('输入有效页码并点击前往按钮触发 page-change', async () => {
      const wrapper = mountBar({ total: 100, currentPage: 1, pageSize: 10 })
      const input = wrapper.find('.page-jump-input')
      await input.setValue('5')
      const btn = wrapper.find('.page-jump-btn')
      await btn.trigger('click')
      expect(wrapper.emitted('page-change')).toBeTruthy()
      expect(wrapper.emitted('page-change')![0]).toEqual([5])
    })

    it('按回车键触发跳转', async () => {
      const wrapper = mountBar({ total: 100, currentPage: 1, pageSize: 10 })
      const input = wrapper.find('.page-jump-input')
      await input.setValue('3')
      await input.trigger('keyup.enter')
      expect(wrapper.emitted('page-change')![0]).toEqual([3])
    })
  })

  // ---- 输入验证 ----
  describe('输入验证', () => {
    it('空输入显示"请输入页码"', async () => {
      const wrapper = mountBar({ total: 100, currentPage: 1, pageSize: 10 })
      const btn = wrapper.find('.page-jump-btn')
      await btn.trigger('click')
      expect(wrapper.find('.jump-error').text()).toBe('请输入页码')
    })

    it('非数字输入显示"请输入有效的数字"', async () => {
      const wrapper = mountBar({ total: 100, currentPage: 1, pageSize: 10 })
      const input = wrapper.find('.page-jump-input')
      await input.setValue('abc')
      const btn = wrapper.find('.page-jump-btn')
      await btn.trigger('click')
      expect(wrapper.find('.jump-error').text()).toBe('请输入有效的数字')
    })

    it('输入0显示"页码不能小于1"', async () => {
      const wrapper = mountBar({ total: 100, currentPage: 1, pageSize: 10 })
      const input = wrapper.find('.page-jump-input')
      await input.setValue('0')
      const btn = wrapper.find('.page-jump-btn')
      await btn.trigger('click')
      expect(wrapper.find('.jump-error').text()).toBe('页码不能小于1')
    })

    it('输入负数显示"页码不能小于1"', async () => {
      const wrapper = mountBar({ total: 100, currentPage: 1, pageSize: 10 })
      const input = wrapper.find('.page-jump-input')
      await input.setValue('-5')
      const btn = wrapper.find('.page-jump-btn')
      await btn.trigger('click')
      expect(wrapper.find('.jump-error').text()).toBe('请输入有效的数字')
    })

    it('超出总页数显示"页码不能超过总页数"', async () => {
      const wrapper = mountBar({ total: 100, currentPage: 1, pageSize: 10 })
      const input = wrapper.find('.page-jump-input')
      await input.setValue('20')
      const btn = wrapper.find('.page-jump-btn')
      await btn.trigger('click')
      expect(wrapper.find('.jump-error').text()).toContain('页码不能超过总页数')
    })

    it('有效输入后错误信息消失', async () => {
      const wrapper = mountBar({ total: 100, currentPage: 1, pageSize: 10 })
      // 先触发一个错误
      const btn = wrapper.find('.page-jump-btn')
      await btn.trigger('click')
      expect(wrapper.find('.jump-error').exists()).toBe(true)

      // 然后输入有效页码
      const input = wrapper.find('.page-jump-input')
      await input.setValue('5')
      await btn.trigger('click')
      expect(wrapper.find('.jump-error').exists()).toBe(false)
    })

    it('输入错误时输入框显示错误样式', async () => {
      const wrapper = mountBar({ total: 100, currentPage: 1, pageSize: 10 })
      const btn = wrapper.find('.page-jump-btn')
      await btn.trigger('click')
      const input = wrapper.find('.page-jump-input')
      expect(input.classes()).toContain('input-error')
    })
  })

  // ---- ARIA 无障碍 ----
  describe('ARIA 无障碍', () => {
    it('nav 元素有 role="navigation"', () => {
      const wrapper = mountBar({ total: 100, currentPage: 1, pageSize: 10 })
      expect(wrapper.find('nav').attributes('role')).toBe('navigation')
    })

    it('nav 元素有 aria-label', () => {
      const wrapper = mountBar({ total: 100, currentPage: 1, pageSize: 10 })
      expect(wrapper.find('nav').attributes('aria-label')).toBe('分页导航')
    })

    it('自定义 aria-label', () => {
      const wrapper = mountBar({ total: 100, currentPage: 1, pageSize: 10, ariaLabel: '用户列表分页' })
      expect(wrapper.find('nav').attributes('aria-label')).toBe('用户列表分页')
    })

    it('当前页有 aria-current="page"', () => {
      const wrapper = mountBar({ total: 100, currentPage: 3, pageSize: 10 })
      const activeBtn = wrapper.find('.pagination-btn.active')
      expect(activeBtn.attributes('aria-current')).toBe('page')
    })

    it('非当前页没有 aria-current', () => {
      const wrapper = mountBar({ total: 100, currentPage: 3, pageSize: 10 })
      const buttons = wrapper.findAll('.pagination-btn:not(.active):not(.ellipsis)')
      buttons.forEach(btn => {
        expect(btn.attributes('aria-current')).toBeUndefined()
      })
    })

    it('错误信息有 role="alert"', async () => {
      const wrapper = mountBar({ total: 100, currentPage: 1, pageSize: 10 })
      const btn = wrapper.find('.page-jump-btn')
      await btn.trigger('click')
      const errorEl = wrapper.find('.jump-error')
      expect(errorEl.attributes('role')).toBe('alert')
    })

    it('输入错误时 aria-invalid 为 true', async () => {
      const wrapper = mountBar({ total: 100, currentPage: 1, pageSize: 10 })
      const btn = wrapper.find('.page-jump-btn')
      await btn.trigger('click')
      const input = wrapper.find('.page-jump-input')
      expect(input.attributes('aria-invalid')).toBe('true')
    })

    it('页码按钮组有 aria-label', () => {
      const wrapper = mountBar({ total: 100, currentPage: 1, pageSize: 10 })
      const btns = wrapper.find('.pagination-btns')
      expect(btns.attributes('aria-label')).toBe('页码导航')
    })

    it('每页条数选择器有 sr-only label', () => {
      const wrapper = mountBar({ total: 100, currentPage: 1, pageSize: 10 })
      const label = wrapper.find('.sr-only')
      expect(label.text()).toBe('每页显示条数')
    })
  })

  // ---- 边界情况 ----
  describe('边界情况', () => {
    it('数据不足一页时只显示信息不显示导航', () => {
      const wrapper = mountBar({ total: 5, currentPage: 1, pageSize: 10 })
      expect(wrapper.find('.pagination-btns').exists()).toBe(false)
      expect(wrapper.find('.pagination-info').text()).toContain('5')
    })

    it('恰好一页数据时不显示页码导航', () => {
      const wrapper = mountBar({ total: 10, currentPage: 1, pageSize: 10 })
      expect(wrapper.find('.pagination-btns').exists()).toBe(false)
    })

    it('数据比 pageSize 多1条时显示2页', () => {
      const wrapper = mountBar({ total: 11, currentPage: 1, pageSize: 10 })
      expect(wrapper.find('.pagination-btns').exists()).toBe(true)
      const info = wrapper.find('.pagination-info').text()
      expect(info).toContain('2')
    })

    it('大数据量时正常渲染', () => {
      const wrapper = mountBar({ total: 10000, currentPage: 500, pageSize: 10 })
      expect(wrapper.find('nav.pagination-bar').exists()).toBe(true)
      expect(wrapper.find('.pagination-btn.active').text()).toBe('500')
    })

    it('currentPage 变化时清空跳转输入和错误', async () => {
      const wrapper = mountBar({ total: 100, currentPage: 1, pageSize: 10 })
      // 触发错误
      const btn = wrapper.find('.page-jump-btn')
      await btn.trigger('click')
      expect(wrapper.find('.jump-error').exists()).toBe(true)

      // 更新 currentPage
      await wrapper.setProps({ currentPage: 2 })
      expect(wrapper.find('.jump-error').exists()).toBe(false)
    })
  })
})
