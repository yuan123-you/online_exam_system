# Resource creation and class deletion integrity implementation plan

**Goal:** Create cannot use a client-supplied existing ID to overwrite another resource; rejected/failed class deletion cannot change exam rosters or cached rows; class deletion cannot detach newly referenced students.
**Architecture:** Server-generated IDs for create entry points; creation/update remain distinct. For class deletion, validate blockers before writes, lock current class and check actual bound users (schema FK currently SET NULL, not RESTRICT), mutate only current active exam target-class JSON under row locks and delete atomically. Keep frozen exam contents and unrelated metadata unchanged; never edit the shared Store rows. Existing deletion policies for questions/papers/exams are NOT changed without decisions/history evidence.
**Tech Stack:** Java21, Spring/JDBC transaction proxies, JUnit/Mockito/H2.
**Spec:** User continues three-platform backend/DB/AI remediation, no frontend/deployment/realDB/provider calls. Source confirms caller ID retained then upsert; class references cleared before blocker; FK class deletion SET NULL means stale cached blocker alone unsafe.

- [ ] Observe last parent full result and save487/4failure baseline.
- [ ] Failing creation tests for6entities(existing/null/blank/clientID), teacher cannot replace another teacher'squestion/paper/exam; valid controls.
- [ ] Failing rejected-deletion cache/persistence tests; real isolated SQL failure/rollback, fresh reference check, current metadata/duplicate roster IDs and frozen-version unchanged cases.
- [ ] Minimal generation and DB-authoritative class deletion module; audit joins deletion transaction; cached maps untouched; no migration.
- [ ] Existing positive fixtures adapted at new DB authority without weaker assertions; focused/full/review and report evidence.

## Follow-up domains (not silently decided)
Teacher-owned references at paper/exam construction, soft-deleted exam history, quota concurrency, password reset defaults, targetMySQL and no-key explicit cart checkout semantics remain separately tracked.


## Execution evidence (local logs span October8–9 +08:00)
- Baseline487tests4fail0error; savedfailureidentities. Initial19UI/classcases17fail1error; strictSQL12cases5fail; orphanSnapshot1fail. Initial31green.
- Reference/typebounds11cases8fail; publicationalreadyhadownerchecks andwasretainedpositivecontrol. Owner-reference145passed.
- Realclassjoin race: onefailure showingnewbounduserbecameNULL viaSETNULL; finaldeleteNOTEXISTSguardfixedit. ActualauditINSERTverifiedrollbackwithdelete+roster.
- Cross-teacherdelete6cases6fail, fixedcachedgate+ownedactiveSQLpredicate. RawHTTPtiny-fraction numericbinding7cases1fail, fixedlossless untypedBigDecimal config; typedDTOcoercionisnotclaimedsolvedbythisconfiguration.
- Finalrelatedacceptance163tests0fail0error0skip at2026-10-09T00:53:00+08:00.
- Finalparentfull545tests4fail0error0skip at2026-10-09T00:57:50+08:00; exactfailureidentitysetequalsbaseline. Full remainsred (blankresetpolicy+3softdeletecontract cases), no discardedassertions.
- Existingfixtureadaptations change authoritytoexplicitcreation/currentDBcleanup andretainsuccess/privacy assertions; invalidcreationmustnotcallcreateRecord ORsaveRecord.
- No schema migration/liveDB/provider/deploy/frontendchanges. TargetMySQLlocking/performance/realAPIend-to-end stillnotcertified.
