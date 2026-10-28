# Runbook: client-id-invalid

## When this fires

CI logs matching any of the following patterns trigger this runbook:

- `GITHUB_APP_CLIENT_ID is blank`
- `GITHUB_APP_CLIENT_ID has characters`

## Steps

1. Find where `GITHUB_APP_CLIENT_ID` is set for the release build (a Gradle property or an environment variable in `release.yml`).
2. Remove the empty or wrong value, or set the real client ID (see `docs/github-app-setup.md`).
3. Re-run the failed release run: `gh run rerun <run-id>`.

## Notes

_Seeded from ship plan `recovery-playbooks[client-id-invalid]`. Update this file as the playbook evolves._
_Last synced from plan version: 1_
