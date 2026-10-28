import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { saveSubmission, submitSubmission } from '@/api/client'
import type { ExamDetail, SubmissionReview } from '@/types'
import ExamSessionModal from './ExamSessionModal.vue'

vi.mock('@/api/client', () => ({ saveSubmission: vi.fn(), submitSubmission: vi.fn() }))
const start = new Date('2026-10-28T06:00:00Z').getTime()
const wrappers: ReturnType<typeof mount>[] = []
function exam(): ExamDetail {
  return {
    id: 'e1', teacherId: 't1', paperId: 'p1', name: 'Exam', targetClassIds: ['c1'],
    startTime: new Date(start - 60000).toISOString(), endTime: new Date(start + 1000).toISOString(),
    antiCheatLimit: 3, published: true,
    paper: { id: 'p1', teacherId: 't1', name: 'Paper', durationMinutes: 60, totalScore: 100, passScore: 60, questionIds: ['q1'] },
    questions: [{ id: 'q1', teacherId: 't1', subject: 'Math', knowledgePoint: 'Basic', difficulty: '易', type: 'single', title: 'Question', options: ['A', 'B'], answer: [], score: 100, order: 1 }],
    session: { submissionId: 's1', startedAt: new Date(start - 60000).toISOString(), deadlineAt: new Date(start + 1000).toISOString(), switchCount: 0, answers: [], remainingMs: 1000 },
  }
}
function create() {
  const wrapper = mount(ExamSessionModal, { props: { exam: exam() }, global: { stubs: { BaseModal: { template: '<div><slot name="header"/><slot/></div>' } } } })
  wrappers.push(wrapper)
  return wrapper
}
const finished: SubmissionReview = { id: 's1', examId: 'e1', studentId: 'u1', studentName: 'Student', answers: [], switchCount: 0, status: '已完成' }

beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(start)
  vi.resetAllMocks()
  vi.mocked(saveSubmission).mockResolvedValue({ success: true, submission: { deadlineAt: new Date(start + 1000).toISOString() } })
  vi.mocked(submitSubmission).mockResolvedValue({ submission: finished })
})
afterEach(() => { for (const wrapper of wrappers.splice(0)) wrapper.unmount(); vi.useRealTimers() })

describe('confirmed expiry policy in the exam modal', () => {
  it('identifies automatic timeout requests and freezes expired answer inputs', async () => {
    const wrapper = create()
    await vi.advanceTimersByTimeAsync(1000); await flushPromises()
    expect(submitSubmission).toHaveBeenCalledWith('e1', [{ questionId: 'q1', answer: [] }], 0, true)
    expect(wrapper.get('input[type="radio"]').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('已保存')
  })

  it('uses the active deadline response instead of prematurely completing a teacher extension', async () => {
    vi.mocked(submitSubmission).mockResolvedValue({ sessionState: 'active', session: { ...exam().session!, deadlineAt: new Date(start + 600000).toISOString(), remainingMs: 599000 } })
    const wrapper = create()
    await vi.advanceTimersByTimeAsync(1000); await flushPromises()
    expect(wrapper.emitted('submitted')).toBeUndefined()
    expect(wrapper.get('input[type="radio"]').attributes('disabled')).toBeUndefined()
    await vi.advanceTimersByTimeAsync(2000)
    expect(submitSubmission).toHaveBeenCalledTimes(1)
  })

  it('uses server remaining time even when the browser clock is far ahead', async () => {
    vi.setSystemTime(start + 3600000)
    vi.mocked(submitSubmission).mockResolvedValue({ sessionState: 'active', session: { ...exam().session!, deadlineAt: new Date(start + 600000).toISOString(), remainingMs: 599000 } })
    const wrapper = create()
    expect(wrapper.get('input[type="radio"]').attributes('disabled')).toBeUndefined()
    await vi.advanceTimersByTimeAsync(1000); await flushPromises()
    expect(wrapper.emitted('submitted')).toBeUndefined()
    expect(wrapper.get('input[type="radio"]').attributes('disabled')).toBeUndefined()
    await vi.advanceTimersByTimeAsync(2000)
    expect(submitSubmission).toHaveBeenCalledTimes(1)
  })

  it('preserves intentional manual submission without treating the click event as a timeout flag', async () => {
    const wrapper = create()
    await wrapper.get('input[value="B"]').setValue()
    await wrapper.get('.primary-btn').trigger('click'); await flushPromises()
    expect(submitSubmission).toHaveBeenCalledWith('e1', [{ questionId: 'q1', answer: ['B'] }], 0, false)
    expect(wrapper.emitted('submitted')).toHaveLength(1)
  })

  it('does not autosave expired answers or repeatedly emit completion after settlement', async () => {
    const wrapper = create()
    await vi.advanceTimersByTimeAsync(31000); await flushPromises()
    expect(saveSubmission).not.toHaveBeenCalled()
    expect(submitSubmission).toHaveBeenCalledTimes(1)
    expect(wrapper.emitted('submitted')).toHaveLength(1)
  })

  it('shows timeout settlement errors without re-enabling expired answers', async () => {
    vi.mocked(submitSubmission).mockRejectedValue(new Error('网络连接失败，请稍后重试'))
    const wrapper = create()
    await vi.advanceTimersByTimeAsync(1000); await flushPromises()
    expect(wrapper.text()).toContain('网络连接失败')
    expect(wrapper.get('input[type="radio"]').attributes('disabled')).toBeDefined()
    expect(wrapper.emitted('submitted')).toBeUndefined()
  })
})
