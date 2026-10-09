import { beforeEach, describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import RegisterView from './RegisterView.vue'
import { getRegistrationOptions, registerStudent } from '@/api/client'
vi.mock('@/api/client', () => ({ getRegistrationOptions: vi.fn(), registerStudent: vi.fn() }))
const options = { departments: [{ id: 'dept-1', name: '计算机学院' }], classes: [
  { id: 'class-1', name: '2310', departmentId: 'dept-1', major: '软件工程' },
  { id: 'classe-1779258228737-4b1d9a', name: '计信 2310', departmentId: 'dept-1', major: '计算机科学与技术（专升本）' }
], defaultClassId: 'classe-1779258228737-4b1d9a' }
const create = () => mount(RegisterView)
async function fill(w: ReturnType<typeof create>) {
  await flushPromises()
  await w.get('#register-username').setValue('student_new')
  await w.get('#register-name').setValue('测试用户')
}
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(getRegistrationOptions).mockResolvedValue(options)
  vi.mocked(registerStudent).mockResolvedValue({ user: { id: 'u1', role: 'student' } as any, message: '注册成功' })
})
describe('simplified student registration', () => {
  it('shows only account and display username and the correct automatic organization', async () => {
    const w = create(); await flushPromises()
    expect(w.findAll('input')).toHaveLength(2)
    expect(w.findAll('select')).toHaveLength(0)
    expect(w.get('label[for="register-name"]').text()).toBe('用户名')
    expect(w.text()).toContain('计算机学院'); expect(w.text()).toContain('计信 2310')
    expect(w.text()).toContain('123456')
  })
  it('submits without user-supplied password or organization and offers in-place login', async () => {
    const w = create(); await fill(w); await w.get('form').trigger('submit'); await flushPromises()
    expect(registerStudent).toHaveBeenCalledWith({ username: 'student_new', name: '测试用户' })
    expect(w.text()).toContain('注册成功'); expect(w.text()).toContain('123456')
    await w.get('.register-login').trigger('click')
    expect(w.emitted('login')).toEqual([['student_new']])
  })
  it('blocks registration when the configured class is missing instead of choosing the other 2310', async () => {
    vi.mocked(getRegistrationOptions).mockResolvedValue({ ...options, classes: options.classes.slice(0, 1) })
    const w = create(); await fill(w); await w.get('form').trigger('submit')
    expect(registerStudent).not.toHaveBeenCalled()
    expect(w.get('[role="alert"]').text()).toContain('默认班级')
  })
  it('shows duplicate-account errors and allows correction', async () => {
    vi.mocked(registerStudent).mockRejectedValue(new Error('该账号已注册'))
    const w = create(); await fill(w); await w.get('form').trigger('submit'); await flushPromises()
    expect(w.get('[role="alert"]').text()).toContain('已注册')
    expect(w.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
  })
  it('can retry organization loading', async () => {
    vi.mocked(getRegistrationOptions).mockRejectedValueOnce(new Error('无法加载院系'))
    const w = create(); await flushPromises()
    await w.get('.retry-button').trigger('click'); await flushPromises()
    expect(w.text()).toContain('计信 2310')
    expect(w.find('[role="alert"]').exists()).toBe(false)
  })
})
