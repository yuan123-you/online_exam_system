# Department deletion integrity implementation plan

**Goal:** A cached zero-reference view cannot cause deletion to null a teacher/admin department or detach a newly bound account. Physical deletion and audit roll back together.
**Architecture:** Preserve current organization-blocker rules. Lock the current department, check current class/user references, and repeat both reference predicates in the final DELETE with affected-row1. Use the existing outer CRUD transaction and cache completion invalidation. No migration or guessed user rebindings.
**Tech Stack:** Java21/JDBC/Spring transaction proxies, isolated H2 with real SET NULL/RESTRICT FKs.
**Spec:** User continues three-platform backend/database/AI remediation, frontend unchanged. Existing schema user.department FK SET NULL; current generic delete relies only on cached blocker.

- [ ] Failing real SQL cases: current bound teacher, current class, late binding, missing parent, zero deletion, audit-row failure, valid deletion and cached blocker.
- [ ] Specialized department deletion authority with final predicates; preserve roles/organization references, no shared Store mutation.
- [ ] Focused/full exact baseline comparison, scoped review and report. Known4 policy failures unchanged, no productionDB/deployment.
