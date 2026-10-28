# 2026-10-28 Reviewed Release Deployment

## Authorization and target

User explicitly requested pushing all changes and deployment after confirming the saved-server-answers-only expiry policy. Deploy the reviewed current master to the existing Oracle origin: ubuntu@129.151.25.15, /opt/online-exam, online-exam.service, port 8085. Preserve the Tencent relay and existing domains; no DNS or unrelated site changes.

## Safeguards

- Commit project source, regression tests and reports; exclude credentials, private operational artifacts, dependencies, build output and Python bytecode.
- Fetch origin and require normal fast-forward push; no force push or overwrite of unrelated changes.
- Re-run the full validation. Build an immutable release from a Git archive of the exact pushed commit; verify frontend/static consistency and the packaged JAR.
- Keep generated assets out of the source commit, following the existing deployment convention. Build them only in an ignored staging directory.
- Preserve unexpected remote source changes by refusing deployment. Known generated static differences and untracked private files are left intact.
- Back up the current JAR, frontend, environment and database to a protected server directory before restart. Do not restore database backups automatically over new business writes.
- Disable startup seed SQL through SPRING_SQL_INIT_MODE=never; no other environment values are changed. No schema migration is introduced by this release.
- Stop the service, atomically activate the prebuilt JAR/frontend, restart, and verify local API health, expected artifact checksum, source commit and release marker through the public relay.
- If activation/health verification fails, restore the previous runtime artifacts/environment and restart the old service; retain all evidence for diagnosis.
- The newly approved scheduler may settle already-expired RUNNING submissions after startup, using only saved answers. The pre-deployment database backup preserves the preceding state.

## Evidence

Exact commit, artifact hashes, protected backup path and production health results will be recorded in the ignored local operational receipt `.deployment-private/reviewed-release-20261028.json` and summarized in the chat. Do not commit secret values, database backups or private deployment logs.
