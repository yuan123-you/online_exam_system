# Archived exam history read integrity implementation plan

**Goal:** Soft-deleted exams do not erase the saved student's score or frozen paper metadata and cannot imply pass with an unknown zero threshold. History remains role-scoped and cannot re-enable active exams or new mutation.
**Architecture:** A private archive metadata map, separately loaded for deleted exams having persisted submissions. ExamContent owns read-only historical lookup; review, score trend and teacher submission scopes use it. Active entity lists, session/delivery/mutation lookup remain unchanged. No historical snapshot fabrication.
**Tech Stack:** Java21/Spring/JDBC/Jackson, JUnit/Mockito and isolated H2.
**Spec:** User full backend/DB/AI remediation, frontend unchanged. Current Store filters deleted exams and review treats null exam as non-missing, returning passed against0; teacher bootstrap filters history away.

- [ ] Failing known-frozen archive receipt/trend/teacher scope and unknown-header fail-closed cases.
- [ ] Separate metadata loader, private serialization and historical read helpers; preserve savedscores and current active permissions.
- [ ] Positive active behavior, no raw version leak and unrelated teacher/student cannot see history; no new grading of archived records.
- [ ] Full regression/review/report; deletion-rule conflict remains unresolved, no liveDB/migration/deployment.
