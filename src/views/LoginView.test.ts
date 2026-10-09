import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import LoginView from './LoginView.vue'
import { registerStudent } from '@/api/client'
vi.mock('@/stores/app', () => ({ useAppStore: () => ({ loginMessage: '', menuItems: [], login: vi.fn() }) }))
vi.mock('@/api/client', () => ({ getRegistrationOptions: vi.fn().mockResolvedValue({
  departments: [{ id: 'dept-1', name: '计算机学院' }],
  classes: [{ id: 'c50', name: '计信 2310', departmentId: 'dept-1', major: '计算机' }], defaultClassId: 'c50'
}), registerStudent: vi.fn() }))
async function create(path = '/login') {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/login', component: LoginView },
    { path: '/register', redirect: { path: '/login', query: { tab: 'register' } } }
  ] })
  await router.push(path); await router.isReady()
  const w = mount(LoginView, { global: { plugins: [router] } }); await flushPromises()
  return { w, router }
}
beforeEach(() => vi.mocked(registerStudent).mockResolvedValue({ user: { id: 'u1', role: 'student' } as any, message: '注册成功' }))
describe('authentication tabs', () => {
  it('switches tabs without opening a separate page and preserves login input', async () => {
    const { w, router } = await create()
    await w.get<HTMLInputElement>('input[name="login-id"]').setValue('saved_account')
    await w.get('#register-tab').trigger('click'); await flushPromises()
    expect(w.get('#register-tab').attributes('aria-selected')).toBe('true')
    expect(w.get('#register-panel').isVisible()).toBe(true)
    expect(router.currentRoute.value.path).toBe('/login')
    await w.get('#login-tab').trigger('click')
    expect(w.get<HTMLInputElement>('input[name="login-id"]').element.value).toBe('saved_account')
  })
  it('opens registration tab from a legacy link', async () => {
    const { w, router } = await create('/register')
    expect(router.currentRoute.value.path).toBe('/login')
    expect(w.get('#register-tab').attributes('aria-selected')).toBe('true')
  })
  it('returns to login with the new account filled in', async () => {
    const { w } = await create('/login?tab=register')
    await w.get('#register-username').setValue('student_new')
    await w.get('#register-name').setValue('测试用户')
    await w.get('#register-panel form').trigger('submit'); await flushPromises()
    await w.get('.register-login').trigger('click'); await flushPromises()
    expect(w.get('#login-tab').attributes('aria-selected')).toBe('true')
    expect(w.get<HTMLInputElement>('input[name="login-id"]').element.value).toBe('student_new')
  })
})
