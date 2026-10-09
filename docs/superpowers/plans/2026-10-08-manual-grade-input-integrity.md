# Manual-grade input integrity plan

**Goal:** Invalid or unknown score input cannot silently become0/clamped score or completed grading; corrupted saved detail bounds cannot be used to authorize a final score.
**Architecture:** A focused pure grading policy validates explicit score object, knownquestionkeys, exact nonnegative integers within server-storedfullscore, saved detail integrity and integer sum. Produce detached result only after validation. Existing explicitpartial/empty updates and completed regrade remain supported; completing every subjective question is not silently redesigned.
**Tech Stack:** Java21/JUnit/Mockito and configured HTTP Jackson mapper already lossless.
**Spec:** Fullthree-platform backend/DB/AI remediation, nofrontend orliveDB/provider/deploy. Current manualGrade coerces malformedMapto{}, numericinputtoint, clampsbounds andignor esunknownquestionkeys.

- [ ] Failing score-root/value/key andstored-detail corruption regressions.
- [ ] Minimal policy/controller integration with400requesterrors/409stored-dataerrors, no writes onrejection.
- [ ] Positivepartial/regrade/zero/full/integralcompat andprivacy/ownership/version regressions.
- [ ] Focused/fullverification andreport; overallgoalincomplete.
