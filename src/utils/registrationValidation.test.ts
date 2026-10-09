import { describe,it,expect } from 'vitest'
import { validateUsername,validatePassword } from './validation'
describe('registration credentials remain valid at login',()=>{
 it('accepts safe student account separators and up to 32 characters',()=>{
  expect(validateUsername('student_new.2026').valid).toBe(true)
  expect(validateUsername('a'.repeat(32)).valid).toBe(true)
  expect(validateUsername("bad';--").valid).toBe(false)
 })
 it('supports strong passwords while retaining legacy six-character login',()=>{
  expect(validatePassword('StudentCheck123!').valid).toBe(true)
  expect(validatePassword('123456').valid).toBe(true)
  expect(validatePassword('密'.repeat(25)).valid).toBe(false)
 })
 it('uses a stricter minimum for new registrations',()=>{
  expect(validatePassword('123456',8).valid).toBe(false)
  expect(validatePassword('SafePass123!',8).valid).toBe(true)
 })
})
