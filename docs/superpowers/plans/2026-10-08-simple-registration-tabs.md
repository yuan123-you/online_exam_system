# Simplified Registration Implementation Plan

**Goal:** Register real students in the existing 50-person 计信 2310 / 计算机学院 class, with default password 123456 and login/register tabs.
**Architecture:** Preserve existing authentication layout and registration endpoint. Embed the simplified registration form in LoginView; redirect legacy /register links to the registration tab. The backend owns defaults and continues hashing passwords and enforcing student-only permissions.
**Tech Stack:** Vue 3, Vitest, Java/Spring, JUnit.
**Spec:** User request in this chat, confirmed class ID classe-1779258228737-4b1d9a, department dept-1.

## Constraints
- Preserve unrelated uncommitted changes.
- Keep login account plus display username; remove password and organization inputs.
- Do not create new colleges/classes or relax general password validation.

## Tasks
- [x] Add failing registration form, tab switching, and backend default-password/class tests.
- [x] Embed registration in existing auth card, add accessible tabs, redirect /register; set explicit readable form colors.
- [x] Backend default omitted registration fields; preserve existing explicit registration validation and hashed storage.
- [x] Run full frontend tests, backend authentication tests and production build.
- [x] Deploy only relevant code, inspect live desktop/mobile form and validate default-password login.
