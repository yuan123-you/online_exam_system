# User profile write integrity implementation plan

**Goal:** Admin profile updates work with the password-free Store, cannot reset credentials or overwrite unrequested concurrent profile edits, and cannot recreate deleted accounts. Creation must not overwrite existing IDs.
**Architecture:** Separate strict account creation from existing-account field updates in StoreService; update only fixed allowlisted fields explicitly supplied by the request. EntityCrudService validates the merged view but writes the patch, hashes only an explicitly supplied nonblank new password and never retrieves credentials from the shared Store. Password defaults/minimums/session revocation are not redefined here.
**Tech Stack:** Java21/JDBC, JUnit/Mockito and isolated H2.
**Spec:** User continuing all-platform backend/DB/AI remediation, no frontend. Root: readStore excludes passwords, but EntityCrudService rereads the same password-free Store hoping to find a password; validate still requires it for profile updates. Store user upsert can replace credentials/profile from old metadata or recreate a deleted account.

- [ ] Failing tests: password-free admin update; SQL preserves stored password and unrequested profile fields; missing/deleted target not recreated; duplicate account ID/username can't overwrite.
- [ ] Minimal Store create/update separation and EntityCrud update contract; preserve explicit password hashing and safe response.
- [ ] Correct only invalid positive CRUD fixture (class existence and obsolete dummy plaintext password), preserving status/name/privacy assertions.
- [ ] Focused regression, final full suite and bounds/remaining policy in report. No real database operation.


## Verified additions and integration boundaries
- Initial profile regressions:7 tests/7 failures, including password-free400 and destructive duplicate creation. Added username SQL clash regression:1test/1failure, then explicit409.
- Read-only review found stale role/binding validation could produce a teacher/admin with no department. Executed stale-student patch regression failed before correction. Existing-update interface now requires the server-read profile; atomic SQL guards role/class/department inputs used for validation.
- Zero changed rows are only accepted as no-op when the current validation tuple and all requested values match; a deleted account404, changed tuple409. Nonrequestedmajor/password edits are not guarded or overwritten.
- Latest core acceptance85tests0fail0error0skip at2026-10-08 22:02:41+08:00. Final fullsuite/recheck results follow in round6report.
- Existing password defaults, character-length policy and session revocation are not redefined here. Auth worker separately verifies hash byte bounds.
- Generic creation and imports use strict insert, generic updates use field-only guard. Existing unused UserRepository.save/upsert is not claimed remediated and needs future repository consolidation/consumer audit.


Final parent full verification after all changes is running in exec3777; log backend/target/round6-exam-final-verified.log. Do not mark the fullsuite passing without observing its terminal result. Progress/remaining broader work are recorded in round6-three-platforms report.
