# Paper/exam configuration input integrity plan

**Goal:** Invalid integer configuration and date intervals fail before writes rather than truncating/coercing into0 or causingSQLerrors.
**Architecture:** Validate the existing schema'spositiveINTduration/nonnegativeINTpassscore and anti-cheat limit; validoptional omitted0 defaults remain. Parse time with existingwriter semantics then normalize toDATETIME(3) precision before ordering/persistence. Do not inventduration480 cap or pass-score<=total rule (schema doesnotdeclaresuchlimit).
**Tech Stack:** Java21/JUnit/Mockito, nofrontend/liveDB/provider/deploy.
**Spec:** User fullbackend/DB/AI remediation. Currentgenericvalidate ignoresconfigbounds andtimeordering despite schemaCHECKs.

- [ ] Failingexactduration/passscore/antiCheat/date input andno-write cases, validboundaries.
- [ ] Minimal configuration validator at samecreate/updateentry, preservingownership/frozenmetadata rules andinputdetachment.
- [ ] Adapt validpositivefixtures toactualconfigshape, no weakerassertions; focused/full/reviewevidence.
- [ ] Passlineovermaximum, exam-endextension/score-release policies remainexplicit decisions, not silentlychanged.


## Execution evidence
- Initial28cases24failures0errors beforeimplementation: invalidINTbounds andtimeintervals reachedcreation.
- Additionaltimezone2cases2failures: embeddedZwasstrippedintoalocaltime; minuteZlost itsUTCmeaning.
- Finalfocused98tests0fail0error0skip at2026-10-09T02:45:47+08:00.
- Configdatesnormalizedondetachedrecord tomillisecondUTCISO; validoffsetandlocaldatetime parsing supportedwithoutembedded-markerrepair.
- Positiveidentity/referencefixturesnowincluderealpaperduration/examstart/end, preservingowner/IDs/privacyassertions. Negativeforeign-paper tests alsohavevalidtimes soauthorization remains the tested cause.
- No fullsuite result is claimed; prior old fullrun remains unconfirmedandisnotrestarted/monitoredafterobservationbudgetexhaustion.
- Nofrontend/livebusinessDB/provider/deploy/schemachange. ExistingpassScoreaboveDerivedTotal remainsacceptedpendingbusinessdecision; optionalomittedzero defaults preserved butexplicitmalformed/nullrejected.
