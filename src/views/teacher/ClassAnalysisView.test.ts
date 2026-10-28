import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { classAnalysis } from '@/api/client'
import type { ClassAnalysisResult } from '@/api/client'
import ClassAnalysisView from './ClassAnalysisView.vue'

const { showToast } = vi.hoisted(() => ({ showToast: vi.fn() }))
vi.mock('@/stores/app', () => ({
  useAppStore: () => ({ myExams: [{ id: 'e1', name: 'First exam' }, { id: 'e2', name: 'Second exam' }], showToast }),
}))
vi.mock('@/api/client', () => ({ classAnalysis: vi.fn() }))

function deferred() {
  let resolve!: (value: ClassAnalysisResult) => void
  let reject!: (reason: Error) => void
  const promise = new Promise<ClassAnalysisResult>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

function analysis(name: string): ClassAnalysisResult {
  return {
    examName: name, totalScore: 100, passScore: 60,
    classes: [{
      classId: name, className: name, totalStudents: 1, submittedCount: 1,
      notStartedCount: 0, runningCount: 0, avgScore: 80, maxScore: 80, minScore: 80,
      passRate: 100, distribution: { fail: 0, d60: 0, d70: 0, d80: 1, d90: 0 }, questionStats: [],
    }],
  }
}

beforeEach(() => vi.resetAllMocks())

describe('class analysis selection consistency', () => {
  it('keeps the latest selected exam when requests finish out of order', async () => {
    const first = deferred(), second = deferred()
    vi.mocked(classAnalysis).mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    const wrapper = shallowMount(ClassAnalysisView)
    await wrapper.get('select').setValue('e1')
    await wrapper.get('select').setValue('e2')
    second.resolve(analysis('Latest class')); await flushPromises()
    first.resolve(analysis('Stale class')); await flushPromises()
    expect(wrapper.text()).toContain('Latest class')
    expect(wrapper.text()).not.toContain('Stale class')
    wrapper.unmount()
  })

  it('does not restore analysis after clearing the exam selection', async () => {
    const pending = deferred()
    vi.mocked(classAnalysis).mockReturnValue(pending.promise)
    const wrapper = shallowMount(ClassAnalysisView)
    await wrapper.get('select').setValue('e1')
    await wrapper.get('select').setValue('')
    expect(wrapper.text()).not.toContain('加载中...')
    pending.resolve(analysis('Stale class')); await flushPromises()
    expect(wrapper.findAll('.class-card')).toHaveLength(0)
    wrapper.unmount()
  })

  it('ignores stale errors and leaves the current request loading', async () => {
    const first = deferred(), second = deferred()
    vi.mocked(classAnalysis).mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    const wrapper = shallowMount(ClassAnalysisView)
    await wrapper.get('select').setValue('e1')
    await wrapper.get('select').setValue('e2')
    first.reject(new Error('Old failure')); await flushPromises()
    expect(showToast).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('加载中...')
    second.resolve(analysis('Latest class')); await flushPromises()
    expect(wrapper.text()).toContain('Latest class')
    wrapper.unmount()
  })

  it('does not show previous exam results when the new exam fails to load', async () => {
    vi.mocked(classAnalysis).mockResolvedValueOnce(analysis('Previous class'))
      .mockRejectedValueOnce(new Error('Current failure'))
    const wrapper = shallowMount(ClassAnalysisView)
    await wrapper.get('select').setValue('e1'); await flushPromises()
    await wrapper.get('select').setValue('e2'); await flushPromises()
    expect(wrapper.findAll('.class-card')).toHaveLength(0)
    expect(showToast).toHaveBeenCalledWith('Current failure', 'error')
    wrapper.unmount()
  })
})
