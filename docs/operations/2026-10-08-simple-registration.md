# Simplified registration acceptance — 2026-10-08

- Database inspection before acceptance: class classe-1779258228737-4b1d9a / 计信 2310 had 50 accounts, department dept-1 / 计算机学院. The separate class-1 / 2310 had 17; it is not selected.
- Only login account and display username are entered. Backend defaults omitted password to 123456, hashes it with BCrypt, and uses the existing class and department. Existing explicit password and organization validation remains intact.
- Login and registration use the existing authentication card with accessible tabs. /register redirects to /login?tab=register.
- Form title, labels, placeholders and project introduction have explicit readable colors; no global application theme change.
- Frontend: 104 tests passed; includes 8 tab/form tests. Production Vite build succeeded (existing large-chunk warning).
- Backend: 16 authentication-focused tests passed on Java 21 in isolated /tmp/exam-registration-tabs on the server; new tests were first observed failing against old production source.
- Live API: simplified registration, default-password login, correct class/department, duplicate 409, immediate bootstrap, unsigned 401 and student administrator-access 403 verified. A controlled synthetic acceptance account is recorded only in ignored .deployment-private/simple-registration-acceptance.json.
- Browser: domain login/register tabs, legacy link redirect, no console errors; 375px mobile viewport has no horizontal overflow. Title and label computed colors rgb(32,61,50).
- Screenshots: D:/HBuilderPrograms/blo/artifacts/simple-registration/desktop.png and mobile.png.
- Deployment built from existing remote source plus targeted changes, not unrelated local uncommitted backend changes. Previous JAR/frontend/touched sources backed up in /opt/online-exam/registration-tabs-backup-20261008. Service active and health verified.
- Local full vue-tsc remains blocked by diagnostics in unchanged PaginationBar.test.ts, PaperPreviewModal.vue, useResponsiveLayout.ts and ClassAnalysisView.vue. No remaining diagnostics in this task's changed files. These unrelated issues were not repaired.
- Windows has Java 17; backend Java 21 verification was performed on the production host in an isolated build directory, without changing the project Java version.
